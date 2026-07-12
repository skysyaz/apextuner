package com.apextuner.engine.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.engine.profile.ProfileApplier
import com.apextuner.engine.root.ShellSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls every thermal zone every second, exposes a [ThermalSnapshot] state
 * flow, and triggers auto-revert via [ProfileApplier] when a user-configured
 * threshold is breached.
 *
 * On devices where sysfs thermal zones are unreadable (common without root),
 * falls back to [BatteryManager] EXTRA_TEMPERATURE so the dashboard still
 * shows a live temperature instead of 0.
 */
@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selector: ShellSelector,
    private val settings: SettingsDataStore,
    private val logs: LogRepository,
    private val profileApplier: ProfileApplier
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ThermalSnapshot.EMPTY)
    val snapshot: StateFlow<ThermalSnapshot> = _state.asStateFlow()
    private var loopJob: Job? = null
    private var lastBreachMs = 0L

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { loop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    suspend fun readOnce(): ThermalSnapshot {
        val shell = selector.best()
        val zones = mutableListOf<ThermalZone>()

        for (id in 0 until 32) {
            val type = shell.readFile(ThermalPaths.type(id)) ?: continue
            val tempRaw = shell.readFile(ThermalPaths.temp(id)) ?: continue
            val milli = tempRaw.trim().toLongOrNull() ?: continue
            // Some kernels report deci-C or C instead of milli-C.
            val c = when {
                milli > 200_000 -> milli / 1000f
                milli > 200 -> milli / 10f
                else -> milli.toFloat()
            }
            if (c in 1f..120f) zones.add(ThermalZone(id, type.trim(), c))
        }

        val batteryFallback = readBatteryTempC()

        val cpu = zones.filter { it.isCpu }.maxOfOrNull { it.tempC }
            // Unclassified SoC sensors are a better CPU proxy than leaving 0.
            ?: zones.filter { !it.isBattery && !it.isGpu }.maxOfOrNull { it.tempC }
            ?: batteryFallback
        val gpu = zones.filter { it.isGpu }.maxOfOrNull { it.tempC } ?: 0f
        val batt = zones.filter { it.isBattery }.maxOfOrNull { it.tempC } ?: batteryFallback
        val maxT = listOfNotNull(
            zones.maxOfOrNull { it.tempC },
            batteryFallback.takeIf { it > 0f }
        ).maxOrNull() ?: 0f

        return ThermalSnapshot(zones, cpu, gpu, batt, maxT, System.currentTimeMillis())
    }

    private fun readBatteryTempC(): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (tenths > 0) tenths / 10f else 0f
        } catch (_: Throwable) {
            0f
        }
    }

    private suspend fun loop() {
        while (isActive) {
            val snap = runCatching { readOnce() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrDefault(ThermalSnapshot.EMPTY)
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

        val now = System.currentTimeMillis()
        if (now - lastBreachMs < 5_000L) return
        lastBreachMs = now

        logs.log(
            level = TunerLog.Level.WARN,
            category = TunerLog.Category.THERMAL,
            message = "Thermal threshold breached (CPU=${snap.cpuTempC}°C, GPU=${snap.gpuTempC}°C). Auto-reverting to Balanced.",
            detail = "cpuThreshold=${s.cpuTempThresholdC}, gpuThreshold=${s.gpuTempThresholdC}"
        )
        runCatching { profileApplier.applyBuiltIn(com.apextuner.data.model.Profile.ThermalPolicy.BALANCED) }
            .onFailure { if (it is CancellationException) throw it }
    }

    /** Pure-Kotlin helper for tests — no shell required. */
    fun parseTempMilli(raw: String): Float? =
        raw.trim().toLongOrNull()?.let { it / 1000f }
}
