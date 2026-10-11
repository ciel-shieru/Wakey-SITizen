package com.cielshieru.wakey.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Wakey-SITizen") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.nextAlarmText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            uiState.overrideText?.let { text ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (uiState.isSyncing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Syncing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            uiState.staleDataWarning?.let { text ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Text("Quick Actions", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.snooze(15) },
                    icon = { Icon(Icons.Default.Schedule, "Snooze 15 min") },
                    text = { Text("15m") }
                )
                ExtendedFloatingActionButton(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.snooze(30) },
                    icon = { Icon(Icons.Default.Schedule, "Snooze 30 min") },
                    text = { Text("30m") }
                )
                ExtendedFloatingActionButton(
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.snooze(60) },
                    icon = { Icon(Icons.Default.Schedule, "Snooze 60 min") },
                    text = { Text("60m") }
                )
                ExtendedFloatingActionButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showTimePicker = true },
                    icon = { Icon(Icons.Default.Alarm, "Set custom time") },
                    text = { Text("Set Time") }
                )
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::skipTomorrow
            ) {
                Icon(Icons.Default.Cancel, "Skip tomorrow")
                Spacer(Modifier.width(8.dp))
                Text("Skip Tomorrow")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::resetOverrides,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Cancel, "Reset skip and custom time")
                Spacer(Modifier.width(8.dp))
                Text("Reset Skip & Custom Time")
            }

            if (uiState.scheduledAlarms.isNotEmpty()) {
                Text("Scheduled Alarms", style = MaterialTheme.typography.titleMedium)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.scheduledAlarms, key = { it.requestCode }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onSkip = { viewModel.skipAlarm(alarm) }
                        )
                    }
                }
            }

            if (showTimePicker) {
                val timePickerState = rememberTimePickerState()
                ModalBottomSheet(
                    onDismissRequest = { showTimePicker = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TimePicker(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                clockDialSelectedContentColor = MaterialTheme.colorScheme.onSurface,
                                clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showTimePicker = false }) {
                                Text("Cancel")
                            }
                            TextButton(onClick = {
                                viewModel.setCustomTime(timePickerState.hour, timePickerState.minute)
                                showTimePicker = false
                            }) {
                                Text("Set")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: HomeUiState.ScheduledAlarm,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = alarm.formattedTime,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (alarm.isUpcoming) "Upcoming" else "Past",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (alarm.isUpcoming) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (alarm.isUpcoming) {
                TextButton(onClick = onSkip) {
                    Text("Skip")
                }
            } else {
                Text(
                    text = "Past",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
