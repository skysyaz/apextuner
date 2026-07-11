package com.apextuner.engine.cpu

import com.apextuner.data.model.CpuClusterConfig

/**
 * Live snapshot of one CPU cluster — what the dashboard binds to.
 */
data class CpuClusterState(
    val clusterId: Int,
    val label: String,
    val cores: List<Int>,
    val onlineMask: List<Boolean>,
    val governor: String,
    val minFreqKHz: Long,
    val maxFreqKHz: Long,
    val curFreqsKHz: List<Long>,        // per-core, aligned with [cores]
    val loadsPercent: List<Float>,      // per-core, aligned with [cores]
    val temperatureC: Float,
    val availableGovernors: List<String>,
    val availableFrequencies: List<Long>
) {
    val onlineCoreCount: Int get() = onlineMask.count { it }

    fun toConfig(): CpuClusterConfig = CpuClusterConfig(
        clusterId = clusterId, label = label, cores = cores, governor = governor,
        minFreqKHz = minFreqKHz, maxFreqKHz = maxFreqKHz, onlineMask = onlineMask,
        availableGovernors = availableGovernors, availableFrequencies = availableFrequencies
    )

    companion object {
        val EMPTY = CpuClusterState(
            clusterId = -1, label = "Unknown", cores = emptyList(),
            onlineMask = emptyList(), governor = "", minFreqKHz = 0, maxFreqKHz = 0,
            curFreqsKHz = emptyList(), loadsPercent = emptyList(), temperatureC = 0f,
            availableGovernors = emptyList(), availableFrequencies = emptyList()
        )
    }
}

/**
 * Whole-CPU snapshot — list of cluster states plus aggregate metrics.
 */
data class CpuSnapshot(
    val clusters: List<CpuClusterState>,
    val totalLoadPercent: Float,
    val averageTemperatureC: Float,
    val timestampMs: Long
) {
    val onlineCoreCount: Int get() = clusters.sumOf { it.onlineCoreCount }
    val totalCoreCount: Int get() = clusters.sumOf { it.cores.size }

    companion object {
        val EMPTY = CpuSnapshot(emptyList(), 0f, 0f, 0L)
    }
}
