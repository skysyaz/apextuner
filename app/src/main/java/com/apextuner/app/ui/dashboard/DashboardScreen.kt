package com.apextuner.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val listState = rememberLazyListState()

    GradientBackground(
        top = ApexPurple.copy(alpha = 0.18f),
        bottom = ApexBgDark
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Bolt, null, tint = ApexPurple, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ApexTuner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            when {
                                state.caps.hasRoot -> "Root access · ${state.activeProfileName}"
                                state.caps.hasShizuku -> "Shizuku · ${state.activeProfileName}"
                                else -> "Standard · ${state.activeProfileName}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(onClick = { haptics.tap(); onNavigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Tune, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Settings")
                    }
                }
            }

            item(key = "gaming") {
                AccentGlassCard(accent = if (state.gamingModeActive) ApexMint else ApexPurple) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.VideogameAsset, null,
                            tint = if (state.gamingModeActive) ApexMint else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Gaming Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
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
                            Text(
                                if (state.gamingModeActive) "ON" else "OFF",
                                color = if (state.gamingModeActive) ApexBgDark else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item(key = "stats") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatCardWithChart(
                            icon = Icons.Filled.Thermostat,
                            title = "CPU Temp",
                            value = formatTemp(state.cpuTempC),
                            history = state.thermalHistory,
                            minVal = 20f, maxVal = 100f,
                            accent = thermalColor(state.cpuTempC, 75, 85),
                            modifier = Modifier.weight(1f)
                        )
                        StatCardWithChart(
                            icon = Icons.Filled.Memory,
                            title = "CPU Load",
                            value = "${state.cpuLoadPercent.toInt()}%",
                            history = state.cpuHistory,
                            minVal = 0f, maxVal = 100f,
                            accent = ApexPurple,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatCard(
                            icon = Icons.Filled.Whatshot,
                            title = "GPU",
                            value = formatTemp(state.gpuTempC),
                            subtitle = gpuSubtitle(state),
                            accent = thermalColor(state.gpuTempC, 85, 95),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Filled.Refresh,
                            title = "Refresh Rate",
                            value = "${state.refreshRateHz.toInt()}Hz",
                            subtitle = if (state.settings?.forcePeakHz == true) "Forced peak" else "Live display",
                            accent = ApexMint,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item(key = "profile") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Active Profile",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.activeProfileName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptics.tap()
                                    vm.applyPreset(Profile.ThermalPolicy.MAX_PERFORMANCE)
                                    onNavigate(Routes.CPU)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp)); Text("Max")
                            }
                            OutlinedButton(
                                onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.BALANCED) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AcUnit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp)); Text("Balanced")
                            }
                            OutlinedButton(
                                onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.POWER_SAVE) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.BatteryFull, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp)); Text("Save")
                            }
                        }
                        if (!state.caps.hasRoot) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "CPU/GPU writes need root. VPN, DNS, and live readings work without it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }

            item(key = "nav") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavCard("CPU Tuning", "Governor, freq, cores", Icons.Filled.Memory, ApexPurple, Modifier.weight(1f)) {
                            onNavigate(Routes.CPU)
                        }
                        NavCard("GPU Tuning", "Clock, governor", Icons.Filled.Whatshot, ApexRed, Modifier.weight(1f)) {
                            onNavigate(Routes.GPU)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavCard("Display", "Refresh rate", Icons.Filled.Refresh, ApexMint, Modifier.weight(1f)) {
                            onNavigate(Routes.DISPLAY)
                        }
                        NavCard("VPN & DNS", "Tunnel, DoH, kill switch", Icons.Filled.Security, ApexPurple, Modifier.weight(1f)) {
                            onNavigate(Routes.NETWORK)
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavCard("Game Library", "Per-game profiles", Icons.Filled.SportsEsports, ApexMint, Modifier.weight(1f)) {
                            onNavigate(Routes.GAMES)
                        }
                        NavCard("Profiles", "Save, import, export", Icons.Filled.Tune, ApexPurple, Modifier.weight(1f)) {
                            onNavigate(Routes.PROFILES)
                        }
                    }
                }
            }

            item(key = "vpn") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Router, null, tint = ApexMint, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "VPN: ${state.vpnMode.name.replace('_', ' ')}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "DNS: ${state.dnsProvider.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        OutlinedButton(onClick = { haptics.tap(); onNavigate(Routes.NETWORK) }) {
                            Text("Manage")
                        }
                    }
                }
            }

            item(key = "thermal") {
                SwitchCard(
                    label = "Thermal auto-revert",
                    description = "Revert to Balanced when CPU > ${state.settings?.cpuTempThresholdC ?: 75}°C or GPU > ${state.settings?.gpuTempThresholdC ?: 85}°C",
                    checked = state.settings?.autoRevertOnThermal ?: true,
                    onCheckedChange = { /* delegated to Settings */ }
                )
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun NavCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTemp(tempC: Float): String =
    if (tempC <= 0f) "—" else "${tempC.toInt()}°C"

private fun gpuSubtitle(state: DashboardState): String {
    val gov = state.gpuState.governor
    val mhz = state.gpuState.curClockMhz
    return when {
        mhz > 0L && gov.isNotBlank() -> "${mhz} MHz · $gov"
        mhz > 0L -> "${mhz} MHz"
        gov.isNotBlank() -> gov
        else -> "Live read"
    }
}

private fun thermalColor(tempC: Float, warm: Int, hot: Int): Color = when {
    tempC <= 0f -> Color.White.copy(alpha = 0.5f)
    tempC >= hot -> ThermalHot
    tempC >= warm -> Color(0xFFFFC107)
    else -> ThermalCool
}
