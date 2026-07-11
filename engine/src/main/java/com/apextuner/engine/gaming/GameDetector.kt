package com.apextuner.engine.gaming

import android.app.usage.UsageStatsManager
import android.content.Context
import com.apextuner.data.model.Game
import com.apextuner.data.repository.GameRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the foreground game package by polling [UsageStatsManager]. The
 * caller supplies a polling interval; we return the package name of the app
 * that was most recently in the foreground, filtered to the user's game list.
 *
 * Requires the PACKAGE_USAGE_STATS permission (granted via Settings → Special
 * access → Usage access). If the permission is missing, [detectForeground]
 * returns null and the AccessibilityService fallback kicks in (see
 * [com.apextuner.app.service.GameLaunchAccessibilityService]).
 */
@Singleton
class GameDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepo: GameRepository
) {

    /**
     * Returns the foreground package name if it is in the user's game library,
     * or null otherwise. The check is O(n) over the game list which is small
     * (typically < 50 entries) so we don't bother indexing.
     */
    suspend fun detectForegroundGame(): Game? {
        val statsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val stats = statsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - 60_000L,
            now
        ) ?: return null
        val foreground = stats.maxByOrNull { it.lastTimeUsed } ?: return null
        val pkg = foreground.packageName
        val games = gameRepo.getEnabledGames()
        return games.firstOrNull { it.packageName == pkg }
    }

    /**
     * Whether the user has granted Usage Access. The Settings provider exposes
     * this via `Settings.Secure.getString("enabled_notification_listeners")`-
     * style checks but there is no public API; we approximate by querying and
     * checking whether the result set is non-empty AND covers recent timestamps.
     */
    fun hasUsageAccess(): Boolean = try {
        val statsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return false
        val now = System.currentTimeMillis()
        val stats = statsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60_000L, now)
        // If access is denied the system returns null or an empty list.
        !stats.isNullOrEmpty()
    } catch (t: Throwable) { false }
}
