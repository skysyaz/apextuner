package com.apextuner.app.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.model.Game
import com.apextuner.data.model.Profile
import com.apextuner.data.repository.GameRepository
import com.apextuner.data.repository.ProfileRepository
import com.apextuner.engine.gaming.GameDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GameLibraryState(
    val games: List<Game> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val hasUsageAccess: Boolean = false
)

@HiltViewModel
class GameLibraryViewModel @Inject constructor(
    @ApplicationContext private val ctx: android.content.Context,
    private val gameRepo: GameRepository,
    private val profileRepo: ProfileRepository,
    private val detector: GameDetector
) : ViewModel() {
    private val _state = MutableStateFlow(GameLibraryState())
    val state: StateFlow<GameLibraryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepo.seedBuiltIns()
            gameRepo.observeAll().collect { games ->
                _state.value = _state.value.copy(games = games)
            }
        }
        viewModelScope.launch {
            profileRepo.observeAll().collect { profiles ->
                _state.value = _state.value.copy(profiles = profiles)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(hasUsageAccess = detector.hasUsageAccess())
        }
    }

    fun setProfile(pkg: String, profileId: Long) = viewModelScope.launch {
        gameRepo.setProfile(pkg, profileId)
    }
    fun toggleEnabled(pkg: String, enabled: Boolean) = viewModelScope.launch {
        gameRepo.setEnabled(pkg, enabled)
    }
    fun remove(pkg: String) = viewModelScope.launch { gameRepo.delete(pkg) }

    /**
     * Scan installed packages and add any with a launcher intent category
     * that match a heuristic "game" check (uses `FLAG_IS_GAME` if available,
     * else falls back to package-name hints).
     */
    fun scanInstalledGames() = viewModelScope.launch {
        val pm = ctx.packageManager
        val flags = android.content.pm.PackageManager.GET_META_DATA
        val candidates = pm.getInstalledApplications(flags)
            .filter { app ->
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName) != null
                if (!launchIntent) return@filter false
                // Heuristic 1: FLAG_IS_GAME was set by the developer.
                if ((app.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0) return@filter true
                // Heuristic 2: package name contains common game markers.
                val pn = app.packageName.lowercase()
                return@filter pn.contains("game") || pn.contains(".gaming.") ||
                    pn.contains("com.garena.") || pn.contains("com.tencent.") ||
                    pn.contains("com.miHoYo.") || pn.contains("com.ea.") ||
                    pn.contains("com.gameloft.") || pn.contains("com.riot.")
            }
            .map { app ->
                Game(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    profileId = Profile.DEFAULT_ID_MAX_PERFORMANCE,
                    enabled = true,
                    installedAt = app.firstInstallTime,
                    lastLaunchedAt = 0L
                )
            }
        gameRepo.insertAll(candidates)
    }
}
