package com.apextuner.engine.cpu

import com.apextuner.data.model.CpuClusterConfig
import com.apextuner.engine.root.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the CPU cluster topology by parsing `related_cpus` for every online
 * CPU. Cores that share `related_cpus` belong to the same cluster and share a
 * governor / frequency range. Returns clusters in little→big→prime order
 * inferred from each cluster's lowest available frequency.
 *
 * On devices where `related_cpus` is unreadable (no permission) we fall back
 * to a single-cluster model containing all online CPUs — this keeps the UI
 * functional without crashing on unsupported kernels.
 */
@Singleton
class CpuTopology @Inject constructor() {

    suspend fun detect(shell: ShellExecutor): List<CpuClusterConfig> {
        val cpuCount = readCpuCount(shell)
        if (cpuCount == 0) return emptyList()

        val clusterMap = LinkedHashMap<List<Int>, MutableList<Int>>()
        for (cpu in 0 until cpuCount) {
            val related = readRelatedCpus(shell, cpu) ?: listOf(cpu)
            val key = related.sorted()
            clusterMap.getOrPut(key) { mutableListOf() }.add(cpu)
        }

        val clusters = clusterMap.values.mapIndexed { index, cores ->
            val firstCore = cores.first()
            val governor = shell.readFile(CpuPaths.scalingGovernor(firstCore)).orEmpty()
            val minFreq = shell.readFile(CpuPaths.scalingMinFreq(firstCore))?.toLongOrNull() ?: 0L
            val maxFreq = shell.readFile(CpuPaths.scalingMaxFreq(firstCore))?.toLongOrNull() ?: 0L
            val availableGovs = shell.readFile(CpuPaths.availableGovernors(firstCore))
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val availableFreqs = shell.readFile(CpuPaths.availableFrequencies(firstCore))
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()
            val onlineMask = cores.map { isOnline(shell, it) }

            CpuClusterConfig(
                clusterId = index,
                label = labelFor(index, clusterMap.size),
                cores = cores,
                governor = governor,
                minFreqKHz = minFreq,
                maxFreqKHz = maxFreq,
                onlineMask = onlineMask,
                availableGovernors = availableGovs,
                availableFrequencies = availableFreqs
            )
        }

        // Sort little → big → prime by lowest available frequency.
        return clusters.sortedBy { it.minFreqKHz }
    }

    private fun labelFor(index: Int, total: Int): String = when {
        total <= 1 -> "All"
        total == 2 -> if (index == 0) "Little" else "Big"
        else -> when (index) { 0 -> "Little"; 1 -> "Big"; else -> "Prime" }
    }

    private suspend fun readCpuCount(shell: ShellExecutor): Int {
        val online = shell.readFile(CpuPaths.onlineFile())
        if (online != null) {
            // /sys/devices/system/cpu/online looks like "0-7" or "0-3,5-7"
            val fromOnline = parseCpuRange(online).maxOrNull()?.let { it + 1 } ?: 0
            if (fromOnline > 0) return fromOnline
        }
        // Unprivileged fallback: count cpuN lines in /proc/stat, then Runtime.
        val fromStat = countCpusFromProcStat(shell)
        if (fromStat > 0) return fromStat
        return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }

    private suspend fun countCpusFromProcStat(shell: ShellExecutor): Int {
        val raw = shell.readFile(CpuPaths.procStat) ?: return 0
        var max = -1
        for (line in raw.lineSequence()) {
            if (!line.startsWith("cpu")) continue
            val id = line.trim().split(Regex("\\s+"))[0].removePrefix("cpu").toIntOrNull() ?: continue
            if (id > max) max = id
        }
        return if (max >= 0) max + 1 else 0
    }

    private suspend fun readRelatedCpus(shell: ShellExecutor, cpu: Int): List<Int>? {
        val raw = shell.readFile(CpuPaths.relatedCpus(cpu)) ?: return null
        return parseCpuRange(raw).ifEmpty { null }
    }

    private suspend fun isOnline(shell: ShellExecutor, cpu: Int): Boolean {
        // cpu0 is always online (cannot be offlined on most kernels).
        if (cpu == 0) return true
        return shell.readFile(CpuPaths.onlineFile(cpu))?.trim() == "1"
    }

    /** Parses Linux CPU range strings: "0-3", "0-3,5-7", "0,2,4". */
    fun parseCpuRange(raw: String): List<Int> {
        val out = mutableListOf<Int>()
        for (part in raw.trim().split(",")) {
            val token = part.trim()
            if (token.isEmpty()) continue
            if ("-" in token) {
                val (a, b) = token.split("-").mapNotNull { it.toIntOrNull() }
                if (a != null && b != null) for (i in a..b) out.add(i)
            } else {
                token.toIntOrNull()?.let { out.add(it) }
            }
        }
        return out.distinct().sorted()
    }
}
