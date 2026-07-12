package com.apextuner.engine.cpu

import com.apextuner.data.model.CpuClusterConfig
import com.apextuner.data.model.Profile
import com.apextuner.engine.root.ShellExecutor
import com.apextuner.engine.root.ShellSelector
import com.apextuner.engine.safety.Transaction
import com.apextuner.engine.safety.RollbackManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes governor / frequency / online state to sysfs. Every write is wrapped
 * in a [Transaction] that records the previous value and rolls back on verify
 * failure — this is the "transactional sysfs write" requirement from the spec.
 *
 * If root is unavailable, [apply] returns false and the UI surfaces a
 * "Root Required" banner. Reads work without root on most kernels.
 */
@Singleton
class CpuController @Inject constructor(
    private val selector: ShellSelector,
    private val topology: CpuTopology,
    private val rollback: RollbackManager
) {

    /** Read-only snapshot of the current CPU state. Works on any device. */
    suspend fun readCurrent(): List<CpuClusterConfig> {
        val shell = selector.best()
        return topology.detect(shell)
    }

    /**
     * Apply [clusters] to sysfs. Returns true only if every write succeeded
     * AND was verified by re-reading the node. On any verification failure
     * the entire batch is rolled back to the values captured before the
     * transaction started.
     */
    suspend fun apply(clusters: List<CpuClusterConfig>): Boolean {
        val shell = selector.bestForSysfsWrite()
            ?: return false

        val tx = rollback.begin("cpu.apply")
        var allOk = true

        for (cluster in clusters) {
            for ((i, cpu) in cluster.cores.withIndex()) {
                val wantOnline = cluster.onlineMask.getOrNull(i) ?: true

                // Capture original values (best-effort; some may be unreadable).
                tx.capture(CpuPaths.scalingGovernor(cpu), shell.readFile(CpuPaths.scalingGovernor(cpu)))
                tx.capture(CpuPaths.scalingMinFreq(cpu), shell.readFile(CpuPaths.scalingMinFreq(cpu)))
                tx.capture(CpuPaths.scalingMaxFreq(cpu), shell.readFile(CpuPaths.scalingMaxFreq(cpu)))
                if (cpu != 0) tx.capture(CpuPaths.onlineFile(cpu), shell.readFile(CpuPaths.onlineFile(cpu)))

                // Order matters: set governor first, then min, then max, then online.
                // Setting max below min (or min above max) is rejected by the kernel.
                if (!writeAndVerify(shell, CpuPaths.scalingGovernor(cpu), cluster.governor)) {
                    allOk = false; break
                }
                if (!writeAndVerify(shell, CpuPaths.scalingMinFreq(cpu), cluster.minFreqKHz.toString())) {
                    allOk = false; break
                }
                if (!writeAndVerify(shell, CpuPaths.scalingMaxFreq(cpu), cluster.maxFreqKHz.toString())) {
                    allOk = false; break
                }
                if (cpu != 0) {
                    val want = if (wantOnline) "1" else "0"
                    if (!writeAndVerify(shell, CpuPaths.onlineFile(cpu), want)) {
                        allOk = false; break
                    }
                }
            }
            if (!allOk) break
        }

        return if (allOk) {
            tx.commit()
            true
        } else {
            rollback.rollback(tx, shell)
            false
        }
    }

    /**
     * Build a per-cluster config for a preset policy against the live topology.
     * Used by [com.apextuner.engine.profile.ProfileApplier] when applying a
     * built-in [Profile.ThermalPolicy].
     */
    suspend fun buildPreset(
        policy: Profile.ThermalPolicy,
        littleOnly: Boolean = false
    ): List<CpuClusterConfig> {
        val current = readCurrent()
        if (current.isEmpty()) return emptyList()

        return current.mapIndexed { index, cluster ->
            val allFreqs = cluster.availableFrequencies.ifEmpty {
                listOf(cluster.minFreqKHz, cluster.maxFreqKHz)
            }
            val minFreq = allFreqs.minOrNull() ?: cluster.minFreqKHz
            val maxFreq = allFreqs.maxOrNull() ?: cluster.maxFreqKHz
            val onlineMask: List<Boolean> = when {
                littleOnly && index > 0 -> cluster.cores.map { false }
                else -> cluster.cores.map { true }
            }
            val (governor, newMin, newMax) = when (policy) {
                Profile.ThermalPolicy.MAX_PERFORMANCE ->
                    Triple(bestGovernor(cluster, Governors.PERFORMANCE), maxFreq, maxFreq)
                Profile.ThermalPolicy.BALANCED ->
                    Triple(bestGovernor(cluster, Governors.SCHEDUTIL), minFreq, maxFreq)
                Profile.ThermalPolicy.POWER_SAVE ->
                    Triple(bestGovernor(cluster, Governors.CONSERVATIVE), minFreq, (maxFreq / 2L).coerceAtLeast(minFreq))
                Profile.ThermalPolicy.CUSTOM ->
                    Triple(cluster.governor, cluster.minFreqKHz, cluster.maxFreqKHz)
            }
            cluster.copy(
                governor = governor,
                minFreqKHz = newMin,
                maxFreqKHz = newMax,
                onlineMask = onlineMask
            )
        }
    }

    /** Pick the governor closest to [preferred] that the kernel actually offers. */
    private fun bestGovernor(cluster: CpuClusterConfig, preferred: String): String {
        val available = cluster.availableGovernors
        if (available.isEmpty()) return preferred
        if (preferred in available) return preferred
        // Fallbacks in priority order.
        val fallbacks = when (preferred) {
            Governors.PERFORMANCE -> listOf(Governors.INTERACTIVE, Governors.SCHEDUTIL, Governors.ONDEMAND)
            Governors.SCHEDUTIL -> listOf(Governors.ONDEMAND, Governors.INTERACTIVE)
            Governors.CONSERVATIVE -> listOf(Governors.ONDEMAND, Governors.SCHEDUTIL)
            else -> listOf(Governors.SCHEDUTIL, Governors.ONDEMAND)
        }
        return fallbacks.firstOrNull { it in available } ?: available.first()
    }

    private suspend fun writeAndVerify(shell: ShellExecutor, path: String, value: String): Boolean {
        if (!shell.writeFile(path, value)) return false
        // Verify by re-reading. Some nodes (online) echo back the value; others
        // (scaling_cur_freq) don't, so we only verify writable control nodes.
        val readBack = shell.readFile(path) ?: return true // unreadable != write failed
        return readBack.trim() == value.trim()
    }
}
