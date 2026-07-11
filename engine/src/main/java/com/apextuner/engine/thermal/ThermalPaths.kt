package com.apextuner.engine.thermal

/**
 * Canonical thermal subsystem paths. Linux exposes one
 * `thermal_zoneN/{temp,type}` directory per sensor; the type string tells us
 * whether it's CPU, GPU, battery, skin, etc.
 */
object ThermalPaths {
    const val THERMAL_ROOT = "/sys/class/thermal"

    fun zoneDir(zoneId: Int) = "$THERMAL_ROOT/thermal_zone$zoneId"

    fun temp(zoneId: Int) = "${zoneDir(zoneId)}/temp"

    fun type(zoneId: Int) = "${zoneDir(zoneId)}/type"

    fun coolingDeviceDir(deviceId: Int) = "$THERMAL_ROOT/cooling_device$deviceId"

    fun curState(deviceId: Int) = "${coolingDeviceDir(deviceId)}/cur_state"

    /** CPU skin sensor typically named "soc-thermal" or "cpu-thermal-0". */
    fun cpuTypes() = listOf("cpu", "soc", "apc1", "apc2", "silver", "gold", "prime")
    fun gpuTypes() = listOf("gpu", "gpuss", "tsens_tz_sensor11")
    fun batteryTypes() = listOf("battery")
}
