package com.apextuner.engine.gpu

import com.apextuner.data.model.GpuConfig
import com.apextuner.engine.root.ShellExecutor

/**
 * Live GPU state — what the dashboard binds to.
 */
data class GpuState(
    val socFamily: String,
    val sysfsRoot: String,
    val governor: String,
    val minClockMhz: Long,
    val maxClockMhz: Long,
    val curClockMhz: Long,
    val loadPercent: Float,
    val temperatureC: Float,
    val availableGovernors: List<String>,
    val availableClocks: List<Long>
) {
    fun toConfig(): GpuConfig = GpuConfig(
        socFamily = socFamily, sysfsRoot = sysfsRoot, governor = governor,
        minClockMhz = minClockMhz, maxClockMhz = maxClockMhz,
        availableGovernors = availableGovernors, availableClocks = availableClocks
    )

    companion object {
        val EMPTY = GpuState(
            socFamily = "unknown", sysfsRoot = "", governor = "",
            minClockMhz = 0, maxClockMhz = 0, curClockMhz = 0,
            loadPercent = 0f, temperatureC = 0f,
            availableGovernors = emptyList(), availableClocks = emptyList()
        )
    }
}

/** Resolve which candidate GPU sysfs root actually exists on this device. */
suspend fun detectGpuRoot(shell: ShellExecutor): Pair<String, String>? {
    for ((family, root) in GpuPaths.candidateRoots) {
        val exists = shell.readFile("$root/governor") != null ||
            shell.readFile("$root/devfreq/cur_freq") != null
        if (exists) return family to root
    }
    return null
}
