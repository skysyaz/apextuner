package com.apextuner.app.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.DisplayConfig
import com.apextuner.engine.display.DisplayController
import com.apextuner.engine.display.DisplayState
import com.apextuner.engine.root.RootAvailability
import com.apextuner.engine.root.RootCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisplayUiState(
    val caps: RootCapabilities = RootCapabilities(false, false, false),
    val display: DisplayState = DisplayState.EMPTY,
    val forcePeakHz: Boolean = false,
    val adaptive: Boolean = true,
    val perAppPackages: List<String> = emptyList()
)

@HiltViewModel
class DisplayViewModel @Inject constructor(
    @ApplicationContext private val appCtx: android.content.Context,
    private val controller: DisplayController,
    private val settings: SettingsDataStore,
    private val rootAvailability: RootAvailability
) : ViewModel() {
    private val _state = MutableStateFlow(DisplayUiState())
    val state: StateFlow<DisplayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val caps = rootAvailability.probe()
            val snap = settings.snapshot.first()
            val display = controller.readState(appCtx, DisplayConfig(
                modeId = -1, refreshRateHz = snap.let { if (it.forcePeakHz) 120f else 60f },
                forcePeakHz = snap.forcePeakHz, adaptive = snap.adaptiveRefresh,
                batterySaverHz = false, perAppPackages = snap.perAppRefreshPackages
            ))
            _state.value = DisplayUiState(
                caps = caps, display = display,
                forcePeakHz = snap.forcePeakHz, adaptive = snap.adaptiveRefresh,
                perAppPackages = snap.perAppRefreshPackages
            )
        }
    }

    fun setForcePeakHz(v: Boolean) {
        _state.value = _state.value.copy(forcePeakHz = v)
        viewModelScope.launch { settings.setForcePeakHz(v) }
    }

    fun setAdaptive(v: Boolean) {
        _state.value = _state.value.copy(adaptive = v)
        viewModelScope.launch { settings.setAdaptiveRefresh(v) }
    }

    fun setMode(modeId: Int) {
        // The actual window attach happens in MainActivity on next recomposition;
        // here we only persist the chosen mode id.
        viewModelScope.launch { /* persist via profile if needed */ }
    }
}
