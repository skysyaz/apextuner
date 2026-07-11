package com.apextuner.app.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.engine.gaming.GamingModeController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile that toggles Gaming Mode. On tap, flips the
 * [GamingModeController] and updates the tile's state (ACTIVE when gaming
 * mode is on, INACTIVE otherwise).
 *
 * The tile also subscribes to [GamingModeController.isActive] so it reflects
 * external state changes (e.g. auto-activation when a game launches).
 */
@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class GamingModeTileService : TileService() {

    @Inject lateinit var gamingMode: GamingModeController
    @Inject lateinit var settings: SettingsDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            gamingMode.isActive.collectLatest { active ->
                qsTile?.apply {
                    state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    label = "Gaming Mode"
                    updateTile()
                }
            }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val currentlyActive = gamingMode.isActive.value
            gamingMode.toggle(!currentlyActive)
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}
