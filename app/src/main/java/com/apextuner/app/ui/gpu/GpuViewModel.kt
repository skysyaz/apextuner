package com.apextuner.app.ui.gpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.model.GpuConfig
import com.apextuner.data.model.Profile
import com.apextuner.engine.gpu.GpuController
import com.apextuner.engine.gpu.GpuState
import com.apextuner.engine.profile.ProfileApplier
import com.apextuner.engine.root.RootAvailability
import com.apextuner.engine.root.RootCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GpuUiState(
    val caps: RootCapabilities = RootCapabilities(false, false, false),
    val live: GpuState = GpuState.EMPTY,
    val applying: Boolean = false,
    val lastMessage: String? = null
) {
    val isSupported: Boolean get() = live != GpuState.EMPTY
}

@HiltViewModel
class GpuViewModel @Inject constructor(
    private val controller: GpuController,
    private val profileApplier: ProfileApplier,
    private val rootAvailability: RootAvailability
) : ViewModel() {
    private val _state = MutableStateFlow(GpuUiState())
    val state: StateFlow<GpuUiState> = _state.asStateFlow()

    init { viewModelScope.launch { _state.value = _state.value.copy(caps = rootAvailability.probe()); refresh() } }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(live = controller.readCurrent())
    }

    fun updateGovernor(g: String) {
        _state.value = _state.value.copy(live = _state.value.live.copy(governor = g))
    }
    fun updateMin(mhz: Long) {
        _state.value = _state.value.copy(live = _state.value.live.copy(minClockMhz = mhz))
    }
    fun updateMax(mhz: Long) {
        _state.value = _state.value.copy(live = _state.value.live.copy(maxClockMhz = mhz))
    }

    fun apply() = viewModelScope.launch {
        val cfg = GpuConfig(
            socFamily = _state.value.live.socFamily,
            sysfsRoot = _state.value.live.sysfsRoot,
            governor = _state.value.live.governor,
            minClockMhz = _state.value.live.minClockMhz,
            maxClockMhz = _state.value.live.maxClockMhz,
            availableGovernors = _state.value.live.availableGovernors,
            availableClocks = _state.value.live.availableClocks
        )
        _state.value = _state.value.copy(applying = true)
        val ok = controller.apply(cfg)
        _state.value = _state.value.copy(applying = false,
            lastMessage = if (ok) "GPU applied (verified)" else "GPU apply failed — rolled back")
    }

    fun applyPreset(policy: Profile.ThermalPolicy) = viewModelScope.launch {
        _state.value = _state.value.copy(applying = true)
        val ok = profileApplier.applyBuiltIn(policy)
        _state.value = _state.value.copy(applying = false,
            lastMessage = if (ok) "Preset $policy applied" else "Preset apply failed")
        refresh()
    }
}
