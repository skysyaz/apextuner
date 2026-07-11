package com.apextuner.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apextuner.app.ui.components.AccentGlassCard
import com.apextuner.app.ui.components.GlassCard
import com.apextuner.app.ui.components.GradientBackground
import com.apextuner.app.ui.components.StatCard
import com.apextuner.app.ui.components.StatCardWithChart
import com.apextuner.app.ui.components.SwitchCard
import com.apextuner.app.ui.components.rememberHaptics
import com.apextuner.app.ui.navigation.Routes
import com.apextuner.app.ui.theme.ApexBgDark
import com.apextuner.app.ui.theme.ApexMint
import com.apextuner.app.ui.theme.ApexPurple
import com.apextuner.app.ui.theme.ApexRed
import com.apextuner.app.ui.theme.ThermalCool
import com.apextuner.app.ui.theme.ThermalHot
import com.apextuner.data.model.Profile

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val haptics = rememberHaptics(state.settings?.hapticsEnabled ?: true)

    GradientBackground(
        top = ApexPurple.copy(alpha = 0.18f),
        bottom = ApexBgDark
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = ApexPurple, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("ApexTuner", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        if (state.caps.hasRoot) "Root access · ${state.activeProfileName}"
                        else if (state.caps.hasShizuku) "Shizuku · ${state.activeProfileName}"
                        else "Limited · ${state.activeProfileName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { haptics.tap(); onNavigate(Routes.SETTINGS) }) {
                    Icon(Icons.Filled.Tune, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Settings")
                }
            }

            // Gaming Mode hero card
            AccentGlassCard(accent = if (state.gamingModeActive) ApexMint else ApexPurple) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.VideogameAsset, null,
                        tint = if (state.gamingModeActive) ApexMint else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Gaming Mode", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(
                            if (state.gamingModeActive) "Auto-optimizing for the active game"
                            else "Tap to enable one-tap optimization",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Button(
                        onClick = { haptics.confirm(); vm.toggleGamingMode(!state.gamingModeActive) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.gamingModeActive) ApexMint else ApexPurple
                        )
                    ) {
                        Text(if (state.gamingModeActive) "ON" else "OFF",
                            color = if (state.gamingModeActive) ApexBgDark else Color.White,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Stat grid: CPU temp, GPU temp, refresh rate, VPN status
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(280.dp)
            ) {
                item {
                    StatCardWithChart(
                        icon = Icons.Filled.Thermostat,
                        title = "CPU Temp",
                        value = "${state.cpuTempC.toInt()}°C",
                        history = state.thermalHistory,
                        minVal = 30f, maxVal = 100f,
                        accent = thermalColor(state.cpuTempC, 75, 85)
                    )
                }
                item {
                    StatCardWithChart(
                        icon = Icons.Filled.Memory,
                        title = "CPU Load",
                        value = "${state.cpuLoadPercent.toInt()}%",
                        history = state.cpuHistory,
                        minVal = 0f, maxVal = 100f,
                        accent = ApexPurple
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Filled.Whatshot,
                        title = "GPU Temp",
                        value = "${state.gpuTempC.toInt()}°C",
                        subtitle = state.gpuState.governor.ifBlank { "—" },
                        accent = thermalColor(state.gpuTempC, 85, 95)
                    )
                }
                item {
                    StatCard(
                        icon = Icons.Filled.Refresh,
                        title = "Refresh Rate",
                        value = "${state.refreshRateHz.toInt()}Hz",
                        subtitle = if (state.settings?.forcePeakHz == true) "Forced peak" else "Adaptive",
                        accent = ApexMint
                    )
                }
            }

            // Active profile quick-switch
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Active Profile", style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                    Text(state.activeProfileName, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.MAX_PERFORMANCE); onNavigate(Routes.CPU) }) {
                            Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp)); Text("Max")
                        }
                        OutlinedButton(onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.BALANCED) }) {
                            Icon(Icons.Filled.AcUnit, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp)); Text("Balanced")
                        }
                        OutlinedButton(onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.POWER_SAVE) }) {
                            Icon(Icons.Filled.BatteryFull, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp)); Text("Save")
                        }
                    }
                }
            }

            // Navigation grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(290.dp)
            ) {
                item { NavCard("CPU Tuning", "Governor, freq, cores", Icons.Filled.Memory, ApexPurple) { onNavigate(Routes.CPU) } }
                item { NavCard("GPU Tuning", "Clock, governor", Icons.Filled.Whatshot, ApexRed) { onNavigate(Routes.GPU) } }
                item { NavCard("Display", "Refresh rate", Icons.Filled.Refresh, ApexMint) { onNavigate(Routes.DISPLAY) } }
                item { NavCard("VPN & DNS", "Tunnel, DoH, kill switch", Icons.Filled.Security, ApexPurple) { onNavigate(Routes.NETWORK) } }
                item { NavCard("Game Library", "Per-game profiles", Icons.Filled.SportsEsports, ApexMint) { onNavigate(Routes.GAMES) } }
                item { NavCard("Profiles", "Save, import, export", Icons.Filled.Tune, ApexPurple) { onNavigate(Routes.PROFILES) } }
            }

            // VPN status card
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Router, null, tint = ApexMint, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("VPN: ${state.vpnMode.name}", style = MaterialTheme.typography.titleMedium,
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("DNS: ${state.dnsProvider.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f))
                    }
                    OutlinedButton(onClick = { haptics.tap(); onNavigate(Routes.NETWORK) }) { Text("Manage") }
                }
            }

            // Thermal safety card
            SwitchCard(
                label = "Thermal auto-revert",
                description = "Revert to Balanced when CPU > ${state.settings?.cpuTempThresholdC ?: 75}°C or GPU > ${state.settings?.gpuTempThresholdC ?: 85}°C",
                checked = state.settings?.autoRevertOnThermal ?: true,
                onCheckedChange = { /* delegated to Settings */ }
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun NavCard(
    title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color, onClick: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(16.dp)
                .clickable(onClick = onClick)
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White,
                fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f))
        }
    }
}

private fun thermalColor(tempC: Float, warm: Int, hot: Int): Color = when {
    tempC >= hot -> ThermalHot
    tempC >= warm -> androidx.compose.ui.graphics.Color(0xFFFFC107)
    else -> ThermalCool
}

@Suppress("unused")
private val powerIcon = Icons.Filled.PowerSettingsNew
