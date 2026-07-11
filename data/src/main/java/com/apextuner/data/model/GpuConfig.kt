package com.apextuner.data.model

import kotlinx.serialization.Serializable

/**
 * GPU configuration. Paths are SoC-specific (Adreno vs Mali vs PowerVR).
 * The active path is resolved at runtime by [com.apextuner.engine.gpu.GpuPaths].
 */
@Serializable
data class GpuConfig(
    val socFamily: String,               // "adreno" | "mali" | "powervr" | "unknown"
    val sysfsRoot: String,               // e.g. /sys/class/kgsl/kgsl-3d0
    val governor: String,                // msm-adreno-tz, simple_ondemand, performance...
    val minClockMhz: Long,
    val maxClockMhz: Long,
    val availableGovernors: List<String> = emptyList(),
    val availableClocks: List<Long> = emptyList(),
    val busLevel: Int = -1,              // optional Adreno bus level
    val idleLevel: Int = -1              // optional Adreno idle level
)
