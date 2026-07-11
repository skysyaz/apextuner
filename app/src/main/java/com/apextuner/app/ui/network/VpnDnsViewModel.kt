package com.apextuner.app.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.NetworkConfig
import com.apextuner.engine.root.RootAvailability
import com.apextuner.engine.root.RootCapabilities
import com.apextuner.vpn.PrivateDnsController
import com.apextuner.vpn.PrivateDnsState
import com.apextuner.vpn.VpnController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetworkUiState(
    val caps: RootCapabilities = RootCapabilities(false, false, false),
    val cfg: NetworkConfig = NetworkConfig(),
    val privateDns: PrivateDnsState = PrivateDnsState("auto", "", NetworkConfig.PrivateDnsMode.AUTO),
    val applying: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class VpnDnsViewModel @Inject constructor(
    private val vpnController: VpnController,
    private val privateDnsController: PrivateDnsController,
    private val settings: SettingsDataStore,
    private val rootAvailability: RootAvailability
) : ViewModel() {
    private val _state = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    init { viewModelScope.launch { refresh() } }

    fun refresh() = viewModelScope.launch {
        val caps = rootAvailability.probe()
        val cfg = vpnController.snapshot()
        val pdns = privateDnsController.readCurrent()
        _state.value = NetworkUiState(caps = caps, cfg = cfg, privateDns = pdns)
    }

    fun setVpnMode(mode: NetworkConfig.VpnMode) {
        _state.value = _state.value.copy(cfg = _state.value.cfg.copy(vpnMode = mode))
    }
    fun setDnsProvider(p: NetworkConfig.DnsProvider) {
        _state.value = _state.value.copy(cfg = _state.value.cfg.copy(dnsProvider = p))
    }
    fun setCustomDohUrl(url: String) {
        _state.value = _state.value.copy(cfg = _state.value.cfg.copy(customDohUrl = url))
    }
    fun setKillSwitch(v: Boolean) {
        _state.value = _state.value.copy(cfg = _state.value.cfg.copy(killSwitch = v))
    }
    fun setPrivateDnsMode(m: NetworkConfig.PrivateDnsMode) {
        _state.value = _state.value.copy(cfg = _state.value.cfg.copy(privateDnsMode = m))
    }
    fun setPrivateDnsSpecifier(s: String) {
        _state.value = _state.value.copy(cfg = _state.value.cfg.copy(privateDnsSpecifier = s))
    }
    fun setWireGuardConfig(raw: String) {
        _state.value = _state.value.copy(cfg = _state.value.cfg.copy(wireGuardConfig = raw))
    }

    fun apply() = viewModelScope.launch {
        _state.value = _state.value.copy(applying = true)
        vpnController.apply(_state.value.cfg)
        _state.value = _state.value.copy(applying = false,
            message = "Applied. VPN mode = ${_state.value.cfg.vpnMode}, DNS = ${_state.value.cfg.dnsProvider}")
        refresh()
    }
}
