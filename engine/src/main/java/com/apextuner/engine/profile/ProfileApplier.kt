package com.apextuner.engine.profile

import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.Profile
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.data.repository.ProfileRepository
import com.apextuner.engine.cpu.CpuController
import com.apextuner.engine.display.DisplayController
import com.apextuner.engine.gpu.GpuController
import com.apextuner.engine.root.ShellSelector
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates a [Profile] into the concrete sysfs / display / VPN writes that
 * realize it. This is the single funnel through which every profile apply
 * passes — built-in presets, user profiles, Gaming Mode, boot-apply, and
 * WorkManager restore all call [apply] / [applyById] / [applyBuiltIn].
 *
 * The applier is intentionally defensive: each subsystem apply is wrapped in
 * runCatching and the outcome is logged. A failure in one subsystem (e.g.
 * GPU unsupported on this SoC) does NOT abort the others.
 */
@Singleton
class ProfileApplier @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val cpu: CpuController,
    private val gpu: GpuController,
    private val display: DisplayController,
    private val selector: ShellSelector,
    private val settings: SettingsDataStore,
    private val logs: LogRepository
) {

    /** Apply a fully-resolved [Profile]. Returns true if every subsystem succeeded. */
    suspend fun apply(profile: Profile): Boolean {
        var ok = true

        // CPU
        if (profile.cpu.isNotEmpty()) {
            val cpuOk = runCatching { cpu.apply(profile.cpu) }
                .onFailure { logs.log(TunerLog.Level.ERROR, TunerLog.Category.CPU, "CPU apply failed", it.message) }
                .getOrDefault(false)
            ok = ok && cpuOk
        } else if (profile.thermalPolicy != Profile.ThermalPolicy.CUSTOM) {
            // Built-in preset: synthesize CPU config from live topology.
            val built = runCatching { cpu.buildPreset(profile.thermalPolicy) }
                .getOrDefault(emptyList())
            if (built.isNotEmpty()) {
                val cpuOk = runCatching { cpu.apply(built) }.getOrDefault(false)
                ok = ok && cpuOk
            }
        }

        // GPU
        val gpuCfg = profile.gpu
        if (gpuCfg != null) {
            val gpuOk = runCatching { gpu.apply(gpuCfg) }
                .onFailure { logs.log(TunerLog.Level.ERROR, TunerLog.Category.GPU, "GPU apply failed", it.message) }
                .getOrDefault(false)
            ok = ok && gpuOk
        } else if (profile.thermalPolicy != Profile.ThermalPolicy.CUSTOM) {
            val current = runCatching { gpu.readCurrent() }.getOrDefault(com.apextuner.engine.gpu.GpuState.EMPTY)
            val built = runCatching { gpu.buildPreset(profile.thermalPolicy, current) }.getOrNull()
            if (built != null) {
                val gpuOk = runCatching { gpu.apply(built) }.getOrDefault(false)
                ok = ok && gpuOk
            }
        }

        // Display — global force peak Hz requires root; otherwise only the
        // in-app window is affected. The actual global setting write is done
        // via the ShellSelector when root is available.
        val displayCfg = profile.display
        if (displayCfg != null && displayCfg.forcePeakHz) {
            val shell = selector.bestForSysfsWrite()
            if (shell != null) {
                val hz = displayCfg.refreshRateHz.coerceAtLeast(60f)
                shell.exec(display.globalForcePeakHzCommand(hz))
            }
        }

        // Network (VPN/DNS) is applied by VpnController in the :vpn module
        // — :engine does not depend on :vpn to avoid a circular module graph.
        // The :app orchestration layer calls both ProfileApplier and
        // VpnController when applying a profile that has a network config.

        settings.setActiveProfileId(profile.id)
        settings.setLastSafeProfileId(profile.id)
        logs.log(
            level = TunerLog.Level.INFO,
            category = TunerLog.Category.PROFILE,
            message = "Applied profile '${profile.name}' (id=${profile.id})",
            detail = "ok=$ok policy=${profile.thermalPolicy}"
        )
        return ok
    }

    /** Convenience: look up a profile by id and apply it. */
    suspend fun applyById(id: Long): Boolean {
        val profile = profileRepo.getById(id) ?: run {
            logs.log(TunerLog.Level.WARN, TunerLog.Category.PROFILE, "Profile $id not found; defaulting to Balanced")
            return applyBuiltIn(Profile.ThermalPolicy.BALANCED)
        }
        return apply(profile)
    }

    /** Apply one of the three built-in presets without needing a stored row. */
    suspend fun applyBuiltIn(policy: Profile.ThermalPolicy): Boolean {
        val id = when (policy) {
            Profile.ThermalPolicy.MAX_PERFORMANCE -> Profile.DEFAULT_ID_MAX_PERFORMANCE
            Profile.ThermalPolicy.BALANCED -> Profile.DEFAULT_ID_BALANCED
            Profile.ThermalPolicy.POWER_SAVE -> Profile.DEFAULT_ID_POWER_SAVE
            Profile.ThermalPolicy.CUSTOM -> return false
        }
        val stored = profileRepo.getById(id)
        if (stored != null) return apply(stored)
        // Not yet seeded — synthesize on the fly.
        return apply(
            Profile(
                id = id,
                name = policy.name,
                thermalPolicy = policy,
                display = if (policy == Profile.ThermalPolicy.MAX_PERFORMANCE)
                    com.apextuner.data.model.DisplayConfig(
                        modeId = -1, refreshRateHz = 120f, forcePeakHz = true,
                        adaptive = false, batterySaverHz = false
                    ) else null
            )
        )
    }
}
