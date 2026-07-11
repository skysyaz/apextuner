package com.apextuner.app.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apextuner.app.ui.components.GlassCard
import com.apextuner.app.ui.components.SwitchCard
import com.apextuner.app.ui.components.rememberHaptics
import com.apextuner.data.model.NetworkConfig

@Composable
fun VpnDnsScreen(vm: VpnDnsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val haptics = rememberHaptics()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text("VPN & DNS", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = Color.White)
        }

        // VPN mode chips
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("VPN Mode", style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NetworkConfig.VpnMode.values().forEach { m ->
                        FilterChip(
                            selected = state.cfg.vpnMode == m,
                            onClick = { haptics.tap(); vm.setVpnMode(m) },
                            label = { Text(m.name.replace('_', ' ')) }
                        )
                    }
                }
                if (state.cfg.vpnMode == NetworkConfig.VpnMode.FULL_TUNNEL) {
                    Spacer(Modifier.height(12.dp))
                    WireGuardConfigEditor(state.cfg.wireGuardConfig, vm::setWireGuardConfig)
                }
            }
        }

        // DNS provider picker
        DnsProviderPicker(state, vm)

        if (state.cfg.dnsProvider == NetworkConfig.DnsProvider.CUSTOM) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Custom DoH URL", style = MaterialTheme.typography.titleMedium,
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.cfg.customDohUrl,
                        onValueChange = vm::setCustomDohUrl,
                        label = { Text("https://example.com/dns-query") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        SwitchCard(
            label = "Kill Switch",
            description = "Block all internet traffic if the VPN tunnel drops unexpectedly. Requires root for the hard (iptables) layer; the soft layer (allowBypass=false) is always applied.",
            checked = state.cfg.killSwitch,
            enabled = state.caps.hasRoot,
            onCheckedChange = { haptics.tap(); vm.setKillSwitch(it) }
        )

        // Private DNS
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Dns, null, tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Private DNS (system-wide)", style = MaterialTheme.typography.titleMedium,
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Text("Current: ${state.privateDns.mapped} (${state.privateDns.specifier.ifBlank { "—" }})",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NetworkConfig.PrivateDnsMode.values().forEach { m ->
                        FilterChip(
                            selected = state.cfg.privateDnsMode == m,
                            onClick = { haptics.tap(); vm.setPrivateDnsMode(m) },
                            label = { Text(m.name) }
                        )
                    }
                }
                if (state.cfg.privateDnsMode == NetworkConfig.PrivateDnsMode.STRICT ||
                    state.cfg.privateDnsMode == NetworkConfig.PrivateDnsMode.HOSTNAME) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.cfg.privateDnsSpecifier,
                        onValueChange = vm::setPrivateDnsSpecifier,
                        label = { Text("DNS hostname (e.g. cloudflare-dns.com)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!state.caps.canWriteSecureSettings) {
                    Spacer(Modifier.height(10.dp))
                    Text("Requires WRITE_SECURE_SETTINGS via root or Shizuku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Button(
            onClick = { haptics.confirm(); vm.apply() },
            enabled = !state.applying,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.applying) "Applying…" else "Apply VPN + DNS") }

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(4.dp))
        }

        Spacer(Modifier.height(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsProviderPicker(state: NetworkUiState, vm: VpnDnsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = state.cfg.dnsProvider.name, onValueChange = {},
                    readOnly = true, label = { Text("DNS provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    NetworkConfig.DnsProvider.values().forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.setDnsProvider(p); expanded = false })
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val preset = com.apextuner.vpn.dns.DnsProviderPreset.fromProvider(state.cfg.dnsProvider)
            if (preset != null) {
                Text("DoH: ${preset.dohUrl}", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f))
                Text("Plain: ${preset.plainServers.joinToString()}", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun WireGuardConfigEditor(raw: String, onUpdate: (String) -> Unit) {
    var text by remember(raw) { mutableStateOf(raw) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VpnKey, null, tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text("WireGuard config", style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it; onUpdate(it) },
                label = { Text("Paste wg-quick config here") },
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
        }
    }
}
