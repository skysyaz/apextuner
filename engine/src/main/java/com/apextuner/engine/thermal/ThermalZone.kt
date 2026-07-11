package com.apextuner.engine.thermal

/**
 * One thermal sensor reading. [tempC] is in degrees Celsius. The kernel
 * reports millidegrees; [ThermalMonitor] divides by 1000.
 */
data class ThermalZone(
    val zoneId: Int,
    val type: String,
    val tempC: Float
) {
    val isCpu: Boolean get() = type.lowercase() in ThermalPaths.cpuTypes()
    val isGpu: Boolean get() = type.lowercase() in ThermalPaths.gpuTypes()
    val isBattery: Boolean get() = type.lowercase() in ThermalPaths.batteryTypes()
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
