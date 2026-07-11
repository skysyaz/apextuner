package com.apextuner.data.model

import kotlinx.serialization.Serializable

/**
 * Per-cluster CPU configuration. A modern SoC typically exposes 2-3 clusters
 * (little / big / prime). Each cluster shares a governor and frequency range.
 */
@Serializable
data class CpuClusterConfig(
    val clusterId: Int,
    val label: String,                   // e.g. "Little", "Big", "Prime"
    val cores: List<Int>,                // cpu0, cpu4, cpu7 ...
    val governor: String,                // performance, schedutil, conservative...
    val minFreqKHz: Long,
    val maxFreqKHz: Long,
    val onlineMask: List<Boolean>,       // per-core online state, aligned with [cores]
    val availableGovernors: List<String> = emptyList(),
    val availableFrequencies: List<Long> = emptyList()
) {
    val onlineCoreCount: Int get() = onlineMask.count { it }
}
