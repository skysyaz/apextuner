package com.apextuner.app.ui.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.model.CpuClusterConfig
import com.apextuner.data.model.Profile
import com.apextuner.engine.cpu.CpuController
import com.apextuner.engine.cpu.CpuSnapshot
import com.apextuner.engine.profile.ProfileApplier
import com.apextuner.engine.root.RootAvailability
import com.apextuner.engine.root.RootCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CpuUiState(
    val caps: RootCapabilities = RootCapabilities(false, false, false),
    val clusters: List<CpuClusterConfig> = emptyList(),
    val liveSnapshot: CpuSnapshot = CpuSnapshot.EMPTY,
    val applying: Boolean = false,
    val lastMessage: String? = null
)

@HiltViewModel
class CpuViewModel @Inject constructor(
    private val controller: CpuController,
    private val profileApplier: ProfileApplier,
    private val rootAvailability: RootAvailability
) : ViewModel() {

    private val _state = MutableStateFlow(CpuUiState())
    val state: StateFlow<CpuUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val caps = rootAvailability.probe()
            _state.value = _state.value.copy(caps = caps)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val clusters = controller.readCurrent()
            _state.value = _state.value.copy(clusters = clusters, lastMessage = null)
        }
    }

    fun updateCluster(updated: CpuClusterConfig) {
        _state.value = _state.value.copy(
            clusters = _state.value.clusters.map { if (it.clusterId == updated.clusterId) updated else it }
        )
    }

    fun applyAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(applying = true)
            val ok = controller.apply(_state.value.clusters)
            _state.value = _state.value.copy(
                applying = false,
                lastMessage = if (ok) "Applied (verified)" else "Apply failed — rolled back"
            )
        }
    }

    fun applyPreset(policy: Profile.ThermalPolicy) {
        viewModelScope.launch {
            _state.value = _state.value.copy(applying = true)
            val ok = profileApplier.applyBuiltIn(policy)
            _state.value = _state.value.copy(
                applying = false,
                lastMessage = if (ok) "Preset $policy applied" else "Preset apply failed"
            )
            refresh()
        }
    }
}
