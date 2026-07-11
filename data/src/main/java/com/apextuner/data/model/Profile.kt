package com.apextuner.data.model

import kotlinx.serialization.Serializable

/**
 * A named tuning profile. This is the single source of truth that
 * [com.apextuner.engine.profile.ProfileApplier] translates into sysfs/VPN
 * writes. Profiles are stored as JSON in Room (see [ProfileEntity]) and can
 * be imported/exported by [ProfileSerializer].
 */
@Serializable
data class Profile(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val iconKey: String = "default",     // resolved client-side to a Material icon
    val cpu: List<CpuClusterConfig> = emptyList(),
    val gpu: GpuConfig? = null,
    val display: DisplayConfig? = null,
    val network: NetworkConfig = NetworkConfig(),
    val thermalPolicy: ThermalPolicy = ThermalPolicy.BALANCED,
    val triggerPackages: List<String> = emptyList(), // games that auto-activate this profile
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    enum class ThermalPolicy {
        /** Maximum performance; thermal watchdog will throttle to [BALANCED] on breach. */
        MAX_PERFORMANCE,

        /** Schedutil governor, sane freq limits, no forced peak Hz. */
        BALANCED,

        /** Conservative governor, little-core-only option, low max freq. */
        POWER_SAVE,

        /** User-defined; thermal watchdog still runs but only warns. */
        CUSTOM
    }

    companion object {
        const val DEFAULT_ID_MAX_PERFORMANCE = -1L
        const val DEFAULT_ID_BALANCED = -2L
        const val DEFAULT_ID_POWER_SAVE = -3L
    }
}
