package com.apextuner.app.ui.settings

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apextuner.app.ui.components.GlassCard
import com.apextuner.app.ui.components.SliderCard
import com.apextuner.app.ui.components.SwitchCard
import com.apextuner.app.ui.components.rememberHaptics

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val s = state.snapshot
    val haptics = rememberHaptics()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = Color.White)
        }

        // Thermal safety
        SectionHeader("Thermal Safety", Icons.Filled.Bolt)
        SwitchCard(
            label = "Thermal auto-revert",
            description = "Revert to Balanced when thresholds are breached.",
            checked = s?.autoRevertOnThermal ?: true,
            onCheckedChange = { haptics.tap(); vm.setAutoRevert(it) }
        )
        SwitchCard(
            label = "Watchdog enabled",
            description = "Run the thermal monitor foreground service.",
            checked = s?.watchdogEnabled ?: true,
            onCheckedChange = { haptics.tap(); vm.setWatchdog(it) }
        )
        SliderCard(
            label = "CPU threshold",
            value = (s?.cpuTempThresholdC ?: 75).toFloat(),
            valueRange = 40f..95f,
            valueFormatter = { "${it.toInt()}°C" },
            onValueChange = { vm.setCpuThreshold(it.toInt()) },
            onValueChangeFinished = {}
        )
        SliderCard(
            label = "GPU threshold",
            value = (s?.gpuTempThresholdC ?: 85).toFloat(),
            valueRange = 50f..100f,
            valueFormatter = { "${it.toInt()}°C" },
            onValueChange = { vm.setGpuThreshold(it.toInt()) },
            onValueChangeFinished = {}
        )

        // Boot behavior
        SectionHeader("Boot Behavior", Icons.Filled.Schedule)
        SwitchCard(
            label = "Apply profile on boot",
            description = "Restore the last safe profile after a reboot.",
            checked = s?.applyOnBoot ?: false,
            onCheckedChange = { haptics.tap(); vm.setApplyOnBoot(it) }
        )

        // Appearance
        SectionHeader("Appearance", Icons.Filled.Palette)
        SwitchCard(
            label = "Dynamic color (Material You)",
            description = "Use wallpaper-derived colors on Android 12+.",
            checked = s?.dynamicColor ?: true,
            onCheckedChange = { haptics.tap(); vm.setDynamicColor(it) }
        )
        SwitchCard(
            label = "Haptic feedback",
            description = "Vibrate on every toggle and slider change.",
            checked = s?.hapticsEnabled ?: true,
            onCheckedChange = { haptics.tap(); vm.setHaptics(it) }
        )
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Theme", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.weight(1f))
                listOf("system", "dark", "oled", "light").forEach { m ->
                    AssistChip(
                        onClick = { haptics.tap(); vm.setTheme(m) },
                        label = { Text(m) },
                        leadingIcon = if (s?.themeMode == m) { { Icon(Icons.Filled.Bolt, null, Modifier.size(14.dp)) } } else null
                    )
                    Spacer(Modifier.size(4.dp))
                }
            }
        }

        // Polling
        SectionHeader("Polling", Icons.Filled.Schedule)
        SliderCard(
            label = "Poll interval",
            value = (s?.pollIntervalMs ?: 1000).toFloat(),
            valueRange = 250f..5000f,
            valueFormatter = { "${it.toInt()} ms" },
            onValueChange = { vm.setPollInterval(it.toInt()) },
            onValueChangeFinished = {}
        )
        SliderCard(
            label = "Chart history points",
            value = (s?.chartHistoryPoints ?: 60).toFloat(),
            valueRange = 20f..180f,
            valueFormatter = { it.toInt().toString() },
            onValueChange = { vm.setChartPoints(it.toInt()) },
            onValueChangeFinished = {}
        )

        // Logging
        SectionHeader("Logging", Icons.Filled.Tune)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("${state.logCount} recent log rows", style = MaterialTheme.typography.bodyLarge,
                    color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Retention: ${s?.logRetentionDays ?: 14} days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f))
                Spacer(Modifier.height(10.dp))
                Button(onClick = { haptics.confirm(); vm.clearLogs() }) { Text("Clear logs now") }
            }
        }
        SliderCard(
            label = "Log retention",
            value = (s?.logRetentionDays ?: 14).toFloat(),
            valueRange = 1f..90f,
            valueFormatter = { "${it.toInt()} days" },
            onValueChange = { vm.setLogRetention(it.toInt()) },
            onValueChangeFinished = {}
        )

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(4.dp))
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White,
            fontWeight = FontWeight.SemiBold)
    }
}
