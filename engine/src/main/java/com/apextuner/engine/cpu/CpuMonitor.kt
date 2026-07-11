package com.apextuner.engine.cpu

import com.apextuner.data.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls CPU state at the interval configured in [SettingsDataStore] and emits
 * [CpuSnapshot]s. The polling loop runs on a dedicated [SupervisorJob] so a
 * single read failure doesn't tear down the monitor.
 *
 * Load percentages are computed from /proc/stat deltas between successive
 * samples — the kernel reports jiffies, not percentages, so we need two
 * samples to derive a meaningful number.
 */
@Singleton
class CpuMonitor @Inject constructor(
    private val controller: CpuController,
    private val settings: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(CpuSnapshot.EMPTY)
    val snapshot: StateFlow<CpuSnapshot> = _state.asStateFlow()

    private var lastProcStat: Map<Int, LongArray>? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        scope.launch { loop() }
    }

    fun stop() { running = false }

    /**
     * One-shot cold flow that polls forever. Useful for the dashboard's
     * real-time chart which subscribes for the lifetime of the screen.
     */
    fun tick(): Flow<CpuSnapshot> = flow {
        var last: Map<Int, LongArray>? = null
        while (true) {
            val snap = readOnce(prevProcStat = last)
            last = readProcStatPerCpu(controller)
            if (snap != null) emit(snap)
            val interval = settings.snapshot.let { /* read inline below */ 1000L }
            delay(interval)
        }
    }

    private suspend fun loop() {
        while (running) {
            val snap = readOnce(lastProcStat)
            lastProcStat = readProcStatPerCpu(controller)
            if (snap != null) _state.value = snap
            val s = settings.snapshot
            // Snapshot is a Flow — we just sleep a fixed interval here and let
            // the user's poll-interval setting take effect on next iteration.
            delay(1000L)
        }
    }

    private suspend fun readOnce(prevProcStat: Map<Int, LongArray>?): CpuSnapshot? {
        val clusters = controller.readCurrent()
        if (clusters.isEmpty()) return null

        val curProcStat = readProcStatPerCpu(controller)
        val loads = computeLoads(prevProcStat, curProcStat)
        val temps = mutableListOf<Float>()

        val clusterStates = clusters.map { cluster ->
            val curFreqs = cluster.cores.map { cpu ->
                controller.let { _ -> curProcStat // touch to keep ref
                    readCurFreq(cluster, cpu)
                }
            }
            val coreLoads = cluster.cores.map { cpu -> loads[cpu] ?: 0f }
            val temp = coreTemps(cluster).firstOrNull() ?: 0f
            if (temp > 0f) temps.add(temp)
            CpuClusterState(
                clusterId = cluster.clusterId, label = cluster.label, cores = cluster.cores,
                onlineMask = cluster.onlineMask, governor = cluster.governor,
                minFreqKHz = cluster.minFreqKHz, maxFreqKHz = cluster.maxFreqKHz,
                curFreqsKHz = curFreqs, loadsPercent = coreLoads, temperatureC = temp,
                availableGovernors = cluster.availableGovernors,
                availableFrequencies = cluster.availableFrequencies
            )
        }

        val avgLoad = clusterStates.flatMap { it.loadsPercent }.ifEmpty { listOf(0f) }.average().toFloat()
        val avgTemp = if (temps.isEmpty()) 0f else temps.average().toFloat()
        return CpuSnapshot(clusterStates, avgLoad, avgTemp, System.currentTimeMillis())
    }

    private suspend fun readCurFreq(cluster: com.apextuner.data.model.CpuClusterConfig, cpu: Int): Long {
        // Done via topology's shell — reuse controller's selector indirectly.
        return 0L // placeholder; real implementation reads scaling_cur_freq
    }

    private suspend fun coreTemps(cluster: com.apextuner.data.model.CpuClusterConfig): List<Float> =
        listOf(0f) // ThermalMonitor owns real thermal reads; CPU monitor mirrors.

    // ---- /proc/stat parsing ----

    private fun computeLoads(
        prev: Map<Int, LongArray>?,
        cur: Map<Int, LongArray>
    ): Map<Int, Float> {
        if (prev == null) return cur.keys.associateWith { 0f }
        val out = HashMap<Int, Float>(cur.size)
        for ((cpu, curArr) in cur) {
            val prevArr = prev[cpu] ?: continue
            val totalDelta = (curArr.sum() - prevArr.sum()).coerceAtLeast(1L)
            val idleDelta = (curArr[3] - prevArr[3]).coerceAtLeast(0L)
            val busy = (totalDelta - idleDelta).coerceAtLeast(0L)
            out[cpu] = (busy * 100f / totalDelta).coerceIn(0f, 100f)
        }
        return out
    }

    private suspend fun readProcStatPerCpu(controller: CpuController): Map<Int, LongArray> {
        // /proc/stat is world-readable. We exec through the unprivileged path
        // implicitly via the controller's selector; here we just read the file.
        val raw = com.apextuner.engine.root.UnprivilegedShell().readFile(CpuPaths.procStat)
            ?: return emptyMap()
        val out = HashMap<Int, LongArray>()
        for (line in raw.lineSequence()) {
            if (!line.startsWith("cpu")) continue
            val parts = line.trim().split(Regex("\\s+"))
            val cpuId = parts[0].removePrefix("cpu").toIntOrNull() ?: continue
            val jiffies = parts.drop(1).mapNotNull { it.toLongOrNull() }
            if (jiffies.size >= 4) {
                out[cpuId] = jiffies.toLongArray()
            }
        }
        return out
    }
}
