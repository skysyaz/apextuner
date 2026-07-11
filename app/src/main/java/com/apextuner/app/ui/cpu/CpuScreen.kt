package com.apextuner.app.ui.cpu

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
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.apextuner.app.ui.components.SwitchCard
import com.apextuner.app.ui.components.rememberHaptics
import com.apextuner.data.model.CpuClusterConfig
import com.apextuner.data.model.Profile

@Composable
fun CpuScreen(vm: CpuViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val haptics = rememberHaptics()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Memory, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text("CPU Tuning", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = Color.White)
        }

        if (!state.caps.hasRoot) {
            GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("Read-only without root — live freqs/governors still show when the kernel allows. Writing governors needs root.",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        // Preset chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.MAX_PERFORMANCE) },
                label = { Text("Max Performance") },
                leadingIcon = { Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(16.dp)) })
            AssistChip(onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.BALANCED) },
                label = { Text("Balanced") },
                leadingIcon = { Icon(Icons.Filled.AcUnit, null, modifier = Modifier.size(16.dp)) })
            AssistChip(onClick = { haptics.tap(); vm.applyPreset(Profile.ThermalPolicy.POWER_SAVE) },
                label = { Text("Power Save") },
                leadingIcon = { Icon(Icons.Filled.BatteryFull, null, modifier = Modifier.size(16.dp)) })
        }

        state.clusters.forEach { cluster -> ClusterEditor(cluster, state.caps.hasRoot, vm) }

        Button(
            onClick = { haptics.confirm(); vm.applyAll() },
            enabled = state.caps.hasRoot && !state.applying,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.applying) "Applying…" else "Apply & Verify") }

        state.lastMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = if (it.contains("failed")) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(4.dp))
        }

        Spacer(Modifier.height(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClusterEditor(cluster: CpuClusterConfig, canWrite: Boolean, vm: CpuViewModel) {
    var governorExpanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(cluster.label + " cluster", style = MaterialTheme.typography.titleMedium,
                color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Cores: ${cluster.cores.joinToString()}  ·  Online: ${cluster.onlineCoreCount}/${cluster.cores.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f))

            Spacer(Modifier.height(14.dp))

            ExposedDropdownMenuBox(expanded = governorExpanded, onExpandedChange = { governorExpanded = it }) {
                OutlinedTextField(
                    value = cluster.governor, onValueChange = {},
                    readOnly = true,
                    label = { Text("Governor") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(governorExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                DropdownMenu(expanded = governorExpanded, onDismissRequest = { governorExpanded = false }) {
                    cluster.availableGovernors.forEach { g ->
                        DropdownMenuItem(text = { Text(g) }, onClick = {
                            vm.updateCluster(cluster.copy(governor = g))
                            governorExpanded = false
                        })
                    }
                    if (cluster.availableGovernors.isEmpty()) {
                        DropdownMenuItem(text = { Text("(no governors detected)") }, onClick = {})
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            val freqs = cluster.availableFrequencies.ifEmpty { listOf(cluster.minFreqKHz, cluster.maxFreqKHz) }
            val min = freqs.minOrNull()?.toFloat() ?: 0f
            val max = freqs.maxOrNull()?.toFloat() ?: 1f

            SliderCard(
                label = "Min frequency",
                value = cluster.minFreqKHz.toFloat(),
                valueRange = min..max,
                valueFormatter = { "${(it / 1000).toInt()} MHz" },
                onValueChange = { v -> vm.updateCluster(cluster.copy(minFreqKHz = v.toLong())) },
                enabled = canWrite
            )
            Spacer(Modifier.height(8.dp))
            SliderCard(
                label = "Max frequency",
                value = cluster.maxFreqKHz.toFloat(),
                valueRange = min..max,
                valueFormatter = { "${(it / 1000).toInt()} MHz" },
                onValueChange = { v -> vm.updateCluster(cluster.copy(maxFreqKHz = v.toLong())) },
                enabled = canWrite
            )

            Spacer(Modifier.height(10.dp))
            cluster.cores.forEachIndexed { i, cpu ->
                SwitchCard(
                    label = "Core $cpu",
                    description = if (cluster.onlineMask.getOrNull(i) == true) "Online" else "Offline",
                    checked = cluster.onlineMask.getOrNull(i) ?: true,
                    enabled = canWrite && cpu != 0,
                    onCheckedChange = { v ->
                        val newMask = cluster.onlineMask.toMutableList().also { it[i] = v }
                        vm.updateCluster(cluster.copy(onlineMask = newMask))
                    }
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
