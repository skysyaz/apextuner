package com.apextuner.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.datastore.SettingsSnapshot
import com.apextuner.data.model.NetworkConfig
import com.apextuner.data.model.Profile
import com.apextuner.data.repository.ProfileRepository
import com.apextuner.data.repository.SettingsRepository
import com.apextuner.engine.cpu.CpuMonitor
import com.apextuner.engine.gpu.GpuController
import com.apextuner.engine.gpu.GpuState
import com.apextuner.engine.gaming.GamingModeController
import com.apextuner.engine.profile.ProfileApplier
import com.apextuner.engine.root.RootAvailability
import com.apextuner.engine.root.RootCapabilities
import com.apextuner.engine.thermal.ThermalMonitor
import com.apextuner.vpn.VpnController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val caps: RootCapabilities = RootCapabilities(false, false, false),
    val settings: SettingsSnapshot? = null,
    val cpuTempC: Float = 0f,
    val cpuLoadPercent: Float = 0f,
    val gpuState: GpuState = GpuState.EMPTY,
    val gpuTempC: Float = 0f,
    val refreshRateHz: Float = 60f,
    val activeProfileId: Long = 0L,
    val activeProfileName: String = "—",
    val gamingModeActive: Boolean = false,
    val vpnMode: NetworkConfig.VpnMode = NetworkConfig.VpnMode.OFF,
    val dnsProvider: NetworkConfig.DnsProvider = NetworkConfig.DnsProvider.NONE,
    val cpuHistory: List<Float> = emptyList(),
    val gpuHistory: List<Float> = emptyList(),
    val thermalHistory: List<Float> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val rootAvailability: RootAvailability,
    private val settingsRepo: SettingsRepository,
    private val profileRepo: ProfileRepository,
    private val profileApplier: ProfileApplier,
    private val cpuMonitor: CpuMonitor,
    private val thermalMonitor: ThermalMonitor,
    private val gpuController: GpuController,
    private val gamingMode: GamingModeController,
    private val vpnController: VpnController
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            rootAvailability.probe()
            cpuMonitor.start()
            thermalMonitor.start()
            gamingMode.start()
            collectSettings()
            collectCpu()
            collectThermal()
            collectGaming()
        }
    }

    private suspend fun collectSettings() = viewModelScope.launch {
        settingsRepo.snapshot.collectLatest { snap ->
            val active = profileRepo.getById(snap.activeProfileId)
            val net = runCatching { vpnController.snapshot() }.getOrDefault(NetworkConfig())
            _state.value = _state.value.copy(
                caps = RootCapabilities(snap.rootGranted, snap.shizukuGranted, false),
                settings = snap,
                activeProfileId = snap.activeProfileId,
                activeProfileName = active?.name ?: "—",
                gamingModeActive = snap.gamingModeActive,
                refreshRateHz = if (snap.forcePeakHz) 120f else 60f,
                vpnMode = net.vpnMode,
                dnsProvider = net.dnsProvider
            )
        }
    }.let { Unit }

    private suspend fun collectCpu() = viewModelScope.launch {
        cpuMonitor.snapshot.collectLatest { cpu ->
            _state.value = _state.value.copy(
                cpuLoadPercent = cpu.totalLoadPercent,
                cpuHistory = (_state.value.cpuHistory + cpu.totalLoadPercent).takeLast(60)
            )
        }
    }.let { Unit }

    private suspend fun collectThermal() = viewModelScope.launch {
        thermalMonitor.snapshot.collectLatest { t ->
            _state.value = _state.value.copy(
                cpuTempC = t.cpuTempC,
                gpuTempC = t.gpuTempC,
                thermalHistory = (_state.value.thermalHistory + t.maxTempC).takeLast(60)
            )
        }
    }.let { Unit }

    private suspend fun collectGaming() = viewModelScope.launch {
        gamingMode.isActive.collectLatest { active ->
            _state.value = _state.value.copy(gamingModeActive = active)
        }
    }.let { Unit }

    fun applyPreset(policy: Profile.ThermalPolicy) {
        viewModelScope.launch { profileApplier.applyBuiltIn(policy) }
    }

    fun toggleGamingMode(on: Boolean) {
        viewModelScope.launch { gamingMode.toggle(on) }
    }
}
