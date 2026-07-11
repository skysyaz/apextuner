package com.apextuner.engine.root

/**
 * Capability flags reported by [RootAvailability]. The UI uses these to decide
 * which surfaces are shown and which feature banners ("Root Required") appear.
 */
data class RootCapabilities(
    val hasRoot: Boolean,
    val hasShizuku: Boolean,
    val shizukuRequiresActivation: Boolean
) {
    /**
     * Whether privileged sysfs writes (CPU/GPU governor + frequency) are
     * possible. These paths are only writable by root on production kernels.
     */
    val canWriteSysfs: Boolean get() = hasRoot

    /**
     * Whether [android.provider.Settings.Global] strings such as
     * PRIVATE_DNS_MODE can be written. Requires WRITE_SECURE_SETTINGS which
     * is obtainable either via root or via Shizuku/ADB.
     */
    val canWriteSecureSettings: Boolean get() = hasRoot || hasShizuku

    /**
     * Whether hidden display / thermal APIs are reachable. Requires Shizuku
     * bound to a privileged process (or root).
     */
    val canUseHiddenApis: Boolean get() = hasRoot || hasShizuku
}
