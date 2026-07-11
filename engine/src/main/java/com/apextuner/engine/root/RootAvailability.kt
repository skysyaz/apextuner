package com.apextuner.engine.root

import com.apextuner.data.datastore.SettingsDataStore
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probes the device for the three escalation paths ApexTuner supports:
 *  1. Magisk/KernelSU root (via libsu)
 *  2. Shizuku running (via Shizuku binder)
 *  3. Neither (graceful degradation to non-root features only)
 *
 * Results are persisted into DataStore so the UI can render the onboarding
 * wizard's permission state without re-probing on every recomposition.
 */
@Singleton
class RootAvailability @Inject constructor(
    private val settings: SettingsDataStore
) {
    /**
     * Synchronously probe root + Shizuku. Does NOT block on the network.
     * Safe to call from a background coroutine.
     */
    suspend fun probe(): RootCapabilities = withContext(Dispatchers.IO) {
        val hasRoot = try {
            // libsu caches the shell; the first call auto-detects root.
            Shell.getShell().isRoot
        } catch (t: Throwable) {
            false
        }

        val (hasShizuku, needsActivation) = probeShizuku()

        settings.setRootGranted(hasRoot)
        settings.setShizukuGranted(hasShizuku && !needsActivation)

        RootCapabilities(
            hasRoot = hasRoot,
            hasShizuku = hasShizuku && !needsActivation,
            shizukuRequiresActivation = needsActivation
        )
    }

    private fun probeShizuku(): Pair<Boolean, Boolean> {
        return try {
            if (!Shizuku.pingBinder()) {
                // Shizuku is installed (the class loaded) but the service is not running.
                false to true
            } else {
                val granted = if (Shizuku.isPreV11()) {
                    true
                } else {
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                granted to !granted
            }
        } catch (t: Throwable) {
            // Shizuku not installed at all.
            false to false
        }
    }

    /** Cached snapshot of the persisted flags. Cheap to call. */
    suspend fun cached(): RootCapabilities {
        val s = settings.snapshot.first()
        return RootCapabilities(
            hasRoot = s.rootGranted,
            hasShizuku = s.shizukuGranted,
            shizukuRequiresActivation = false
        )
    }
}
