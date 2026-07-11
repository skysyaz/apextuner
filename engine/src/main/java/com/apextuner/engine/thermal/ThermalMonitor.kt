package com.apextuner.engine.thermal

import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.engine.profile.ProfileApplier
import com.apextuner.engine.root.ShellSelector
import com.apextuner.engine.root.UnprivilegedShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls every thermal zone every second, exposes a [ThermalSnapshot] state
 * flow, and triggers auto-revert via [ProfileApplier] when a user-configured
 * threshold is breached.
 *
 * The watchdog loop is the single most safety-critical component in the app:
 * if a Max Performance profile cooks the SoC, this is what pulls it back to
 * Balanced. The loop must therefore NEVER crash — every read is wrapped in
 * runCatching and a single bad zone does not abort the whole pass.
 */
@Singleton
class ThermalMonitor @Inject constructor(
    private val selector: ShellSelector,
    private val settings: SettingsDataStore,
    private val logs: LogRepository,
    private val profileApplier: ProfileApplier
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ThermalSnapshot.EMPTY)
    val snapshot: StateFlow<ThermalSnapshot> = _state.asStateFlow()
    private var running = false
    private var lastBreachMs = 0L

    fun start() {
        if (running) return
        running = true
        scope.launch { loop() }
    }

    fun stop() { running = false }

    suspend fun readOnce(): ThermalSnapshot {
        val shell = selector.best()
        val zones = mutableListOf<ThermalZone>()

        // Probe thermal_zone0 .. thermal_zone15 (typical max on modern SoCs).
        for (id in 0 until 16) {
            val type = shell.readFile(ThermalPaths.type(id)) ?: continue
            val tempRaw = shell.readFile(ThermalPaths.temp(id)) ?: continue
            val milli = tempRaw.trim().toLongOrNull() ?: continue
            val c = milli / 1000f
            zones.add(ThermalZone(id, type.trim(), c))
        }

        val cpu = zones.filter { it.isCpu }.maxOfOrNull { it.tempC } ?: 0f
        val gpu = zones.filter { it.isGpu }.maxOfOrNull { it.tempC } ?: 0f
        val batt = zones.filter { it.isBattery }.maxOfOrNull { it.tempC } ?: 0f
        val maxT = zones.maxOfOrNull { it.tempC } ?: 0f
        return ThermalSnapshot(zones, cpu, gpu, batt, maxT, System.currentTimeMillis())
    }

    private suspend fun loop() {
        while (running) {
            val snap = runCatching { readOnce() }.getOrDefault(ThermalSnapshot.EMPTY)
            _state.value = snap
            evaluateThresholds(snap)
            delay(1000L)
        }
    }

    private suspend fun evaluateThresholds(snap: ThermalSnapshot) {
        val s = settings.snapshot.first()
        if (!s.watchdogEnabled || !s.autoRevertOnThermal) return

        val cpuBreach = snap.cpuTempC > 0f && snap.cpuTempC >= s.cpuTempThresholdC
        val gpuBreach = snap.gpuTempC > 0f && snap.gpuTempC >= s.gpuTempThresholdC
        if (!cpuBreach && !gpuBreach) return

        // Debounce: don't revert more than once per 30 seconds.
        val now = System.currentTimeMillis()
        if (now - lastBreachMs < 30_000L) return
        lastBreachMs = now

        logs.log(
            level = TunerLog.Level.WARN,
            category = TunerLog.Category.THERMAL,
            message = "Thermal threshold breached (CPU=${snap.cpuTempC}°C, GPU=${snap.gpuTempC}°C). Auto-reverting to Balanced.",
            detail = "cpuThreshold=${s.cpuTempThresholdC}, gpuThreshold=${s.gpuTempThresholdC}"
        )
        runCatching { profileApplier.applyBuiltIn(com.apextuner.data.model.Profile.ThermalPolicy.BALANCED) }
    }

    /** Pure-Kotlin helper for tests — no shell required. */
    fun parseTempMilli(raw: String): Float? =
        raw.trim().toLongOrNull()?.let { it / 1000f }
}
