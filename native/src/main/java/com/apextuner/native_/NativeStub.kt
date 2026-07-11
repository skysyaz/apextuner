package com.apextuner.native_

import com.apextuner.engine.root.ShellExecutor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub for the :native module.
 *
 * The original spec called for a Rust/C++ binary exposing a JNI surface for
 * high-performance sysfs polling and thermal monitoring. Per the user's
 * "Kotlin only" decision, we instead implement all polling in pure Kotlin via
 * libsu + coroutines (see [com.apextuner.engine.cpu.CpuMonitor] and
 * [com.apextuner.engine.thermal.ThermalMonitor]).
 *
 * This module exists purely to preserve the 5-module Gradle structure from
 * section 7 of the spec. It exposes a single [NativePoller] facade that
 * delegates to the Kotlin monitors, so any future C++/Rust implementation can
 * drop in here without changing the rest of the codebase.
 */
interface NativePoller {
    /** Returns the current CPU temperature in °C, or null if unavailable. */
    suspend fun cpuTempC(): Float?

    /** Returns the current GPU temperature in °C, or null if unavailable. */
    suspend fun gpuTempC(): Float?

    /** Returns the current per-core frequencies in kHz, indexed by cpu id. */
    suspend fun coreFrequenciesKHz(): Map<Int, Long>
}

@Singleton
class KotlinNativePoller @Inject constructor(
    private val shell: com.apextuner.engine.root.ShellSelector
) : NativePoller {

    override suspend fun cpuTempC(): Float? {
        val s = shell.best()
        val raw = s.readFile("/sys/class/thermal/thermal_zone0/temp") ?: return null
        return raw.trim().toLongOrNull()?.let { it / 1000f }
    }

    override suspend fun gpuTempC(): Float? {
        val s = shell.best()
        // Probe a few common GPU thermal zones.
        for (id in 0 until 16) {
            val type = s.readFile("/sys/class/thermal/thermal_zone$id/type") ?: continue
            if (type.contains("gpu", ignoreCase = true)) {
                val t = s.readFile("/sys/class/thermal/thermal_zone$id/temp") ?: continue
                return t.trim().toLongOrNull()?.let { it / 1000f }
            }
        }
        return null
    }

    override suspend fun coreFrequenciesKHz(): Map<Int, Long> {
        val s = shell.best()
        val out = HashMap<Int, Long>()
        for (cpu in 0 until 16) {
            val f = s.readFile("/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_cur_freq")
                ?: continue
            f.trim().toLongOrNull()?.let { out[cpu] = it }
        }
        return out
    }

    @Suppress("unused")
    private val shellMarker: ShellExecutor? = null
}
