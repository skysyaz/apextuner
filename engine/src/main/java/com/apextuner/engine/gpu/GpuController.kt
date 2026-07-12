package com.apextuner.engine.gpu

import com.apextuner.data.model.GpuConfig
import com.apextuner.data.model.Profile
import com.apextuner.engine.root.ShellExecutor
import com.apextuner.engine.root.ShellSelector
import com.apextuner.engine.safety.RollbackManager
import com.apextuner.engine.safety.Transaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads + writes GPU governor / clock state via sysfs. Same transactional
 * apply/verify/rollback contract as [com.apextuner.engine.cpu.CpuController].
 *
 * Returns false from [apply] when root is unavailable or the GPU sysfs root
 * cannot be detected. The GPU tab renders an "Unsupported on this device"
 * banner in the latter case.
 */
@Singleton
class GpuController @Inject constructor(
    private val selector: ShellSelector,
    private val rollback: RollbackManager
) {

    suspend fun readCurrent(): GpuState {
        val shell = selector.best()
        val (family, root) = detectGpuRoot(shell) ?: return GpuState.EMPTY

        val governor = shell.readFile(GpuPaths.governor(root)).orEmpty()
        val minClock = shell.readFile(GpuPaths.minClock(root))?.toLongOrNull() ?: 0L
        val maxClock = shell.readFile(GpuPaths.maxClock(root))?.toLongOrNull() ?: 0L
        val curClock = shell.readFile(GpuPaths.curClock(root))?.toLongOrNull() ?: 0L
        val availGovs = shell.readFile(GpuPaths.availableGovernors(root))
            ?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
        val availClocks = shell.readFile(GpuPaths.availableFrequencies(root))
            ?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() } ?: emptyList()
        val temp = shell.readFile(GpuPaths.temp(root))?.toFloatOrNull() ?: 0f

        return GpuState(
            socFamily = family, sysfsRoot = root, governor = governor,
            minClockMhz = minClock, maxClockMhz = maxClock, curClockMhz = curClock,
            loadPercent = 0f, temperatureC = temp,
            availableGovernors = availGovs, availableClocks = availClocks
        )
    }

    suspend fun apply(config: GpuConfig): Boolean {
        val shell = selector.bestForSysfsWrite() ?: return false
        val root = config.sysfsRoot.takeIf { it.isNotBlank() } ?: return false

        val tx = rollback.begin("gpu.apply")
        var ok = true

        tx.capture(GpuPaths.governor(root), shell.readFile(GpuPaths.governor(root)))
        tx.capture(GpuPaths.minClock(root), shell.readFile(GpuPaths.minClock(root)))
        tx.capture(GpuPaths.maxClock(root), shell.readFile(GpuPaths.maxClock(root)))

        if (!writeAndVerify(shell, GpuPaths.governor(root), config.governor)) ok = false
        if (ok && !writeAndVerify(shell, GpuPaths.minClock(root), config.minClockMhz.toString())) ok = false
        if (ok && !writeAndVerify(shell, GpuPaths.maxClock(root), config.maxClockMhz.toString())) ok = false

        return if (ok) { tx.commit(); true } else { rollback.rollback(tx, shell); false }
    }

    suspend fun buildPreset(policy: Profile.ThermalPolicy, current: GpuState): GpuConfig? {
        if (current == GpuState.EMPTY) return null
        val allClocks = current.availableClocks.ifEmpty {
            listOf(current.minClockMhz, current.maxClockMhz)
        }
        val min = allClocks.minOrNull() ?: current.minClockMhz
        val max = allClocks.maxOrNull() ?: current.maxClockMhz
        val (gov, newMin, newMax) = when (policy) {
            Profile.ThermalPolicy.MAX_PERFORMANCE ->
                Triple(pickGovernor(current, "performance"), max, max)
            Profile.ThermalPolicy.BALANCED ->
                Triple(pickGovernor(current, "msm-adreno-tz"), min, max)
            Profile.ThermalPolicy.POWER_SAVE ->
                Triple(pickGovernor(current, "simple_ondemand"), min, (max / 2L).coerceAtLeast(min))
            Profile.ThermalPolicy.CUSTOM ->
                Triple(current.governor, current.minClockMhz, current.maxClockMhz)
        }
        return current.toConfig().copy(governor = gov, minClockMhz = newMin, maxClockMhz = newMax)
    }

    private fun pickGovernor(state: GpuState, preferred: String): String {
        val avail = state.availableGovernors
        if (avail.isEmpty()) return preferred
        if (preferred in avail) return preferred
        return avail.first()
    }

    private suspend fun writeAndVerify(shell: ShellExecutor, path: String, value: String): Boolean {
        if (!shell.writeFile(path, value)) return false
        val readBack = shell.readFile(path) ?: return true
        return readBack.trim() == value.trim()
    }
}
