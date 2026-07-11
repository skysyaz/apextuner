package com.apextuner.app.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.apextuner.app.ui.components.rememberHaptics

@Composable
fun GameLibraryScreen(vm: GameLibraryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val haptics = rememberHaptics()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SportsEsports, null, tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text("Game Library", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.weight(1f))
            Button(onClick = { haptics.tap(); vm.scanInstalledGames() }) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp)); Text("Scan")
            }
        }

        if (!state.hasUsageAccess) {
            GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)) {
                Text(
                    "Usage Access not granted. Auto-detection of game launches won't work — " +
                        "you can still assign profiles here, but Gaming Mode won't activate automatically. " +
                        "Grant Usage Access in Settings → Apps → Special access → Usage access.",
                    Modifier.padding(14.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.games.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "No games added yet. Tap Scan to detect installed games " +
                        "(heuristic: FLAG_IS_GAME or common game publisher packages).",
                    Modifier.padding(18.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.games, key = { it.packageName }) { game ->
                GameRow(
                    game = game,
                    profiles = state.profiles,
                    onSetProfile = { p -> haptics.tap(); vm.setProfile(game.packageName, p) },
                    onToggle = { v -> haptics.tap(); vm.toggleEnabled(game.packageName, v) },
                    onDelete = { haptics.confirm(); vm.remove(game.packageName) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameRow(
    game: com.apextuner.data.model.Game,
    profiles: List<com.apextuner.data.model.Profile>,
    onSetProfile: (Long) -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeProfile = profiles.firstOrNull { it.id == game.profileId }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(game.label, style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(game.packageName, style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = activeProfile?.name ?: "Default",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth(0.7f).menuAnchor()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        profiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = { onSetProfile(p.id); expanded = false }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Switch(checked = game.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
