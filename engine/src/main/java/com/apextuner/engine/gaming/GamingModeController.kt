package com.apextuner.engine.gaming

import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.Game
import com.apextuner.data.model.Profile
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.data.repository.ProfileRepository
import com.apextuner.engine.profile.ProfileApplier
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
 * Orchestrates Gaming Mode: polls [GameDetector] every 2 s, and when a
 * tracked game enters the foreground, applies its bound profile. When the
 * game exits, reverts to the previous profile (or Balanced if none).
 *
 * "Gaming Mode active" is exposed as a [StateFlow] so the QS Tile and the
 * dashboard card both reflect the same source of truth.
 */
@Singleton
class GamingModeController @Inject constructor(
    private val detector: GameDetector,
    private val gameRepo: com.apextuner.data.repository.GameRepository,
    private val profileRepo: ProfileRepository,
    private val profileApplier: ProfileApplier,
    private val settings: SettingsDataStore,
    private val logs: LogRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _activeGame = MutableStateFlow<Game?>(null)
    val activeGame: StateFlow<Game?> = _activeGame.asStateFlow()

    private val _active = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _active.asStateFlow()

    private var running = false
    private var previousProfileId: Long? = null

    fun start() {
        if (running) return
        running = true
        scope.launch { loop() }
    }

    fun stop() { running = false }

    /**
     * Manually toggle Gaming Mode (from the QS Tile). When turning on, we
     * apply the user's chosen default gaming profile (stored as the
     * `last.safe.profile.id` setting, or Balanced if unset).
     */
    suspend fun toggle(on: Boolean) {
        settings.setGamingModeActive(on)
        _active.value = on
        if (!on) {
            previousProfileId?.let { profileApplier.applyById(it) }
            _activeGame.value = null
        }
    }

    private suspend fun loop() {
        while (running) {
            val game = runCatching { detector.detectForegroundGame() }
                .getOrDefault(null)
            val current = _activeGame.value

            if (game != null && game.packageName != current?.packageName) {
                // Game launched → apply its bound profile.
                previousProfileId = settings.snapshot.first().activeProfileId
                val profile = profileRepo.getById(game.profileId)
                    ?: profileRepo.getById(Profile.DEFAULT_ID_MAX_PERFORMANCE)
                if (profile != null) {
                    profileApplier.apply(profile)
                    _activeGame.value = game
                    _active.value = true
                    settings.setGamingModeActive(true)
                    gameRepo.markLaunched(game.packageName)
                    logs.log(
                        level = TunerLog.Level.INFO,
                        category = TunerLog.Category.PROFILE,
                        message = "Gaming Mode auto-activated for ${game.label}",
                        detail = "package=${game.packageName} profile=${profile.name}"
                    )
                }
            } else if (game == null && current != null) {
                // Game exited → revert.
                val revertTo = previousProfileId ?: Profile.DEFAULT_ID_BALANCED
                profileApplier.applyById(revertTo)
                _activeGame.value = null
                _active.value = false
                settings.setGamingModeActive(false)
                logs.log(
                    level = TunerLog.Level.INFO,
                    category = TunerLog.Category.PROFILE,
                    message = "Gaming Mode deactivated (game exit); reverted to profile $revertTo"
                )
            }

            delay(2000L)
        }
    }
}
