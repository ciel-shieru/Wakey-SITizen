package com.cielshieru.wakey.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::saveConfig) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Alarm Offsets", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = uiState.onlineOffset.toString(),
                onValueChange = { viewModel.updateOnlineOffset(it.toIntOrNull() ?: 60) },
                label = { Text("Online offset (minutes)") },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.campusOffset.toString(),
                onValueChange = { viewModel.updateCampusOffset(it.toIntOrNull() ?: 90) },
                label = { Text("Campus offset (minutes)") },
                singleLine = true
            )

            Text("Repeat Pattern", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = uiState.repeatInterval.toString(),
                onValueChange = { viewModel.updateRepeatInterval(it.toIntOrNull() ?: 5) },
                label = { Text("Repeat interval (minutes, 1-30)") },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.maxRepeats.toString(),
                onValueChange = { viewModel.updateMaxRepeats(it.toIntOrNull() ?: 6) },
                label = { Text("Max repeats (1-20)") },
                singleLine = true
            )

            Text("Bedtime Reminder", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = uiState.bedtimeEnabled,
                    onCheckedChange = viewModel::toggleBedtimeEnabled
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable bedtime reminders")
            }

            if (uiState.bedtimeEnabled) {
                OutlinedTextField(
                    value = uiState.sleepDuration.toString(),
                    onValueChange = { viewModel.updateSleepDuration(it.toIntOrNull() ?: 8) },
                    label = { Text("Target sleep (hours, 4-12)") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.bedtimeOffset.toString(),
                    onValueChange = { viewModel.updateBedtimeOffset(it.toIntOrNull() ?: 30) },
                    label = { Text("Bedtime reminder offset (minutes, 5-60)") },
                    singleLine = true
                )
            }

            Text("External Events", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = uiState.externalEnabled,
                    onCheckedChange = viewModel::toggleExternalEvents
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable external event adjustment")
            }

            if (uiState.externalEnabled) {
                OutlinedTextField(
                    value = uiState.externalBuffer.toString(),
                    onValueChange = { viewModel.updateExternalBuffer(it.toIntOrNull() ?: 30) },
                    label = { Text("Buffer minutes (5-120)") },
                    singleLine = true
                )
            }
        }
    }
}
