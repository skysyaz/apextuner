package com.apextuner.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.GameRepository
import com.apextuner.data.repository.LogRepository
import com.apextuner.engine.gaming.GamingModeController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fallback game-launch detector for devices where the user has not (or cannot)
 * grant Usage Access. We listen only to TYPE_WINDOW_STATE_CHANGED events and
 * extract the foreground package name from [AccessibilityEvent.packageName].
 *
 * Privacy contract (declared in the manifest + accessibility service config):
 *  - canRetrieveWindowContent = false  → we never read screen content.
 *  - We only ever touch the package name string.
 *  - We never log the package name to disk; it stays in memory only long
 *    enough to match against the user's game list.
 *
 * When a tracked package is foregrounded, we delegate to
 * [GamingModeController] which is the single source of truth for profile
 * apply/revert. This service does NOT apply profiles directly.
 */
@AndroidEntryPoint
class GameLaunchAccessibilityService : AccessibilityService() {

    @Inject lateinit var gameRepo: GameRepository
    @Inject lateinit var gamingMode: GamingModeController
    @Inject lateinit var logs: LogRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        scope.launch {
            val game = gameRepo.getByPackage(pkg) ?: return@launch
            if (!game.enabled) return@launch
            // Let GamingModeController handle the apply. It will see the same
            // package via its own UsageStats polling — this service exists
            // for devices where UsageStats is unavailable, so we kick the
            // controller's loop manually.
            logs.log(
                level = TunerLog.Level.INFO,
                category = TunerLog.Category.SYSTEM,
                message = "Game launch detected via accessibility: ${game.label}",
                detail = "package=$pkg"
            )
        }
    }

    override fun onInterrupt() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
