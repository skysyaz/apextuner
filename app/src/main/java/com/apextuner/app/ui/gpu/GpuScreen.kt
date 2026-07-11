package com.apextuner.app.ui.gpu

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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.apextuner.app.ui.components.SliderCard
import com.apextuner.app.ui.components.rememberHaptics
import com.apextuner.data.model.Profile

@Composable
fun GpuScreen(vm: GpuViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val haptics = rememberHaptics()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Whatshot, null, tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text("GPU Tuning", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = Color.White)
        }

        if (!state.isSupported) {
            GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)) {
                Text(
                    "No GPU sysfs node detected on this SoC. GPU tuning unavailable. " +
                        "ApexTuner probes /sys/class/kgsl/kgsl-3d0 (Adreno), " +
                        "/sys/class/misc/mali0/device (Mali), and /sys/class/gpu.",
                    Modifier.padding(14.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Column
        }

        if (!state.caps.hasRoot) {
            GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)) {
                Text(
                    "Read-only without root — clocks still update when sysfs is readable. Writing governors needs root.",
                    Modifier.padding(14.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Detected SoC: ${state.live.socFamily}", style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Path: ${state.live.sysfsRoot}", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Text("Current clock: ${state.live.curClockMhz} MHz",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text(
                    "Temperature: ${if (state.live.temperatureC > 0f) "${state.live.temperatureC.toInt()}°C" else "—"}",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.MAX_PERFORMANCE) },
                label = { Text("Max") },
                leadingIcon = { Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(16.dp)) })
            AssistChip(onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.BALANCED) },
                label = { Text("Balanced") },
                leadingIcon = { Icon(Icons.Filled.AcUnit, null, modifier = Modifier.size(16.dp)) })
        }

        GovernorPicker(state, vm)

        val clocks = state.live.availableClocks.ifEmpty { listOf(state.live.minClockMhz, state.live.maxClockMhz) }
        val min = clocks.minOrNull()?.toFloat() ?: 0f
        val max = clocks.maxOrNull()?.toFloat() ?: 1f

        SliderCard(
            label = "Min clock",
            value = state.live.minClockMhz.toFloat(),
            valueRange = min..max,
            valueFormatter = { "${it.toLong()} MHz" },
            onValueChange = { vm.updateMin(it.toLong()) },
            enabled = state.caps.hasRoot
        )
        SliderCard(
            label = "Max clock",
            value = state.live.maxClockMhz.toFloat(),
            valueRange = min..max,
            valueFormatter = { "${it.toLong()} MHz" },
            onValueChange = { vm.updateMax(it.toLong()) },
            enabled = state.caps.hasRoot
        )

        Button(
            onClick = { haptics.confirm(); vm.apply() },
            enabled = state.caps.hasRoot && !state.applying,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.applying) "Applying…" else "Apply & Verify") }

        state.lastMessage?.let {
            Text(it, color = if (it.contains("failed")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(4.dp))
        }

        Spacer(Modifier.height(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GovernorPicker(state: GpuUiState, vm: GpuViewModel) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = state.live.governor, onValueChange = {},
                    readOnly = true, label = { Text("GPU governor") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.live.availableGovernors.forEach { g ->
                        DropdownMenuItem(text = { Text(g) }, onClick = { vm.updateGovernor(g); expanded = false })
                    }
                    if (state.live.availableGovernors.isEmpty()) {
                        DropdownMenuItem(text = { Text("(no governors detected)") }, onClick = {})
                    }
                }
            }
        }
    }
}
