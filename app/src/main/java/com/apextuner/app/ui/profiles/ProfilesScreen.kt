package com.apextuner.app.ui.profiles

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.apextuner.app.ui.components.rememberHaptics
import com.apextuner.data.model.Profile

@Composable
fun ProfilesScreen(vm: ProfilesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val haptics = rememberHaptics()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var importPayload by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profiles", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { showImport = true }) {
                Icon(Icons.Filled.Upload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp)); Text("Import")
            }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = { haptics.tap(); state.profiles.let { /* export to file in real build */ } }) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp)); Text("Export")
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp)); Text("New")
            }
        }

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(4.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.profiles, key = { it.id }) { p ->
                ProfileRow(
                    profile = p,
                    isActive = p.id == state.activeId,
                    onApply = { haptics.confirm(); vm.apply(p.id) },
                    onDuplicate = { haptics.tap(); vm.duplicate(p.id, p.name + " copy") },
                    onDelete = { haptics.confirm(); vm.delete(p.id) }
                )
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New profile") },
            text = {
                Column {
                    OutlinedTextField(value = newName, onValueChange = { newName = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newDesc, onValueChange = { newDesc = it },
                        label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) vm.create(newName, newDesc)
                    newName = ""; newDesc = ""; showCreate = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import profiles JSON") },
            text = {
                OutlinedTextField(value = importPayload, onValueChange = { importPayload = it },
                    label = { Text("Paste JSON") },
                    modifier = Modifier.fillMaxWidth().height(180.dp))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importPayload.isNotBlank()) vm.import(importPayload)
                    importPayload = ""; showImport = false
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    isActive: Boolean,
    onApply: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        border = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                 else Color.White.copy(alpha = 0.18f)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
                if (profile.description.isNotBlank()) {
                    Text(profile.description, style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f))
                }
                Text("Policy: ${profile.thermalPolicy.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f))
            }
            IconButton(onClick = onApply) {
                Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Filled.ContentCopy, null, tint = Color.White.copy(alpha = 0.75f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
