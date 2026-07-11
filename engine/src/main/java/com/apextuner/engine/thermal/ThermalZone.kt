package com.apextuner.engine.thermal

/**
 * One thermal sensor reading. [tempC] is in degrees Celsius. The kernel
 * reports millidegrees; [ThermalMonitor] divides by 1000.
 *
 * Type matching uses substring contains — real zone names are like
 * `cpu-thermal`, `soc-thermal`, `gpu-thermal-0`, not bare `cpu`/`gpu`.
 */
data class ThermalZone(
    val zoneId: Int,
    val type: String,
    val tempC: Float
) {
    private val typeLower: String get() = type.lowercase()

    val isCpu: Boolean get() = ThermalPaths.cpuTypes().any { typeLower.contains(it) }
    val isGpu: Boolean get() = ThermalPaths.gpuTypes().any { typeLower.contains(it) }
    val isBattery: Boolean get() = ThermalPaths.batteryTypes().any { typeLower.contains(it) }
}

/**
 * Aggregate thermal snapshot — what the dashboard binds to.
 */
data class ThermalSnapshot(
    val zones: List<ThermalZone>,
    val cpuTempC: Float,
    val gpuTempC: Float,
    val batteryTempC: Float,
    val maxTempC: Float,
    val timestampMs: Long
) {
    companion object {
        val EMPTY = ThermalSnapshot(emptyList(), 0f, 0f, 0f, 0f, 0L)
    }
}
