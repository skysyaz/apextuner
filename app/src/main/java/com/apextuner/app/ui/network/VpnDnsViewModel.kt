package com.apextuner.app.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val rootAvailability: RootAvailability
) : ViewModel() {
    private val _state = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    init { viewModelScope.launch { refresh() } }

    fun refresh() = viewModelScope.launch {
        val caps = rootAvailability.probe()
        val cfg = vpnController.snapshot()
        val pdns = privateDnsController.readCurrent()
        _state.value = _state.value.copy(caps = caps, cfg = cfg, privateDns = pdns)
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

    fun openSystemDnsSettings() {
        val ok = privateDnsController.openSystemPrivateDnsSettings()
        _state.value = _state.value.copy(
            message = if (ok) "Opened system settings — set Private DNS there."
            else "Could not open system DNS settings."
        )
    }

    fun onVpnPermissionDenied() {
        _state.value = _state.value.copy(
            applying = false,
            message = "VPN permission denied. Allow ApexTuner VPN to change DNS without root."
        )
    }

    fun applyAfterVpnConsent() = viewModelScope.launch {
        _state.value = _state.value.copy(applying = true, message = null)
        val cfg = _state.value.cfg
        // Prefer a real DNS provider when enabling a tunnel.
        val effective = if (
            (cfg.vpnMode == NetworkConfig.VpnMode.DNS_ONLY || cfg.vpnMode == NetworkConfig.VpnMode.FULL_TUNNEL) &&
            cfg.dnsProvider == NetworkConfig.DnsProvider.NONE
        ) {
            cfg.copy(dnsProvider = NetworkConfig.DnsProvider.CLOUDFLARE)
        } else cfg

        vpnController.apply(effective)
        _state.value = _state.value.copy(
            applying = false,
            cfg = effective,
            message = when (effective.vpnMode) {
                NetworkConfig.VpnMode.OFF -> "VPN stopped."
                NetworkConfig.VpnMode.DNS_ONLY ->
                    "DNS-only VPN applied with ${effective.dnsProvider.name}. No root required."
                NetworkConfig.VpnMode.FULL_TUNNEL ->
                    if (effective.wireGuardConfig.isBlank())
                        "Full tunnel without WireGuard config — running as DNS-only (no root)."
                    else "Full tunnel applied."
            }
        )
        refresh()
    }
}
