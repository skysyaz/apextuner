package com.apextuner.engine.root

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the highest-capability [ShellExecutor] available at call time. The
 * selection is re-evaluated on every call so that granting Shizuku mid-session
 * (or revoking root) immediately takes effect.
 *
 * Resolution order: root → Shizuku → unprivileged. The engine layer always
 * holds a reference to this selector rather than to a concrete backend.
 */
@Singleton
class ShellSelector @Inject constructor(
    private val suShell: SuShell,
    private val shizukuShell: ShizukuShell,
    private val unprivileged: UnprivilegedShell,
    private val rootAvailability: RootAvailability
) {

    /**
     * The best executor available right now. Callers that need a specific
     * capability should check [bestFor] instead.
     */
    suspend fun best(): ShellExecutor {
        val caps = rootAvailability.cached()
        return when {
            caps.hasRoot -> suShell
            caps.hasShizuku -> shizukuShell
            else -> unprivileged
        }
    }

    /**
     * Returns the best executor that can actually write sysfs, or null if
     * none can. CPU/GPU controllers call this and surface a "Root Required"
     * banner when it returns null.
     */
    suspend fun bestForSysfsWrite(): ShellExecutor? {
        val caps = rootAvailability.cached()
        return if (caps.hasRoot) suShell else null
    }

    /**
     * Returns the best executor that can write secure settings. Either root
     * or Shizuku qualifies.
     */
    suspend fun bestForSecureSettings(): ShellExecutor? {
        val caps = rootAvailability.cached()
        return when {
            caps.hasRoot -> suShell
            caps.hasShizuku -> shizukuShell
            else -> null
        }
    }
}
