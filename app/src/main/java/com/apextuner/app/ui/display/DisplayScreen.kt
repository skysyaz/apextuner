package com.apextuner.app.ui.display

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
import androidx.compose.material.icons.filled.Refresh
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
import com.apextuner.app.ui.components.SwitchCard
import com.apextuner.app.ui.components.rememberHaptics

@Composable
fun DisplayScreen(vm: DisplayViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val haptics = rememberHaptics()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text("Display & Refresh Rate", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = Color.White)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Active mode", style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f))
                Text("${state.display.activeRefreshRateHz.toInt()} Hz",
                    style = MaterialTheme.typography.displaySmall, color = Color.White,
                    fontWeight = FontWeight.SemiBold)
                Text("Peak available: ${state.display.peakHz.toInt()} Hz",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f))
            }
        }

        SwitchCard(
            label = "Force Peak Hz",
            description = "Globally force the highest supported refresh rate. Requires root for system-wide effect; otherwise affects ApexTuner's own window only.",
            checked = state.forcePeakHz,
            enabled = true,
            onCheckedChange = { haptics.tap(); vm.setForcePeakHz(it) }
        )

        SwitchCard(
            label = "Adaptive",
            description = "Let the system lower the refresh rate to save battery when the screen content is static.",
            checked = state.adaptive,
            onCheckedChange = { haptics.tap(); vm.setAdaptive(it) }
        )

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Supported display modes", style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                state.display.supportedModes.forEach { m ->
                    Text("· ${m.label}  (mode id ${m.modeId})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f))
                }
                if (state.display.supportedModes.isEmpty()) {
                    Text("(no modes reported)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f))
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Per-app refresh rate", style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Games added here will auto-switch to peak Hz when they enter the foreground. " +
                    "Managed from the Game Library screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f))
                Spacer(Modifier.height(8.dp))
                state.perAppPackages.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f))
                }
                if (state.perAppPackages.isEmpty()) {
                    Text("(none)", style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
