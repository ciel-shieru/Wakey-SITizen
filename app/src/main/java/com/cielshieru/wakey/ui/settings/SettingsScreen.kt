package com.cielshieru.wakey.ui.settings

import android.Manifest.permission.READ_CALENDAR
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cielshieru.wakey.data.calendar.CalendarEntry
import com.cielshieru.wakey.data.calendar.getReadableCalendars
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus

@Composable
private fun ValidationSupportingText(
    value: String,
    min: Int,
    max: Int,
    label: String
) {
    val intValue = value.toIntOrNull()
    if (intValue != null && (intValue < min || intValue > max)) {
        Text(
            text = "$label must be $min-$max",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun CalendarPicker(
    calendars: List<CalendarEntry>,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(calendars, key = { it.id }) { calendar ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSelection(calendar.id) }
                    .toggleable(
                        value = calendar.id in selectedIds,
                        onValueChange = { onToggleSelection(calendar.id) }
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = calendar.id in selectedIds,
                    onCheckedChange = { onToggleSelection(calendar.id) }
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = calendar.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = calendar.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCalendarPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.configSaved.collect {
            onShowSnackbar("Settings saved")
        }
    }

    val postNotificationsGranted = uiState.permissionStatus
        .firstOrNull { it.permission == "POST_NOTIFICATIONS" }
        ?.status == PermissionStatus.Granted

    LaunchedEffect(uiState.externalEnabled) {
        if (uiState.externalEnabled && !showCalendarPicker) {
            showCalendarPicker = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setPermissionGranted(granted)
    }

    LaunchedEffect(uiState.discoveryMode) {
        if (uiState.discoveryMode == DiscoveryMode.BaseUrl) {
            viewModel.setPermissionGranted(false)
        }
    }

    LaunchedEffect(uiState.calendarPicker.permissionGranted, uiState.externalEnabled) {
        if (uiState.calendarPicker.permissionGranted && uiState.discoveryMode == DiscoveryMode.BaseUrl && uiState.externalEnabled) {
            val calendars = getReadableCalendars(context)
            viewModel.loadCalendars(calendars)
        }
    }

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
                    TextButton(onClick = {
                        if (uiState.externalEnabled && uiState.calendarPicker.selectedIds.isEmpty()) {
                            onShowSnackbar("Select at least one calendar for external events")
                            return@TextButton
                        }
                        viewModel.saveConfig()
                    }) {
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

            Text("SIT-ICS-Go Discovery", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = uiState.discoveryMode == DiscoveryMode.BaseUrl,
                    onCheckedChange = { viewModel.toggleDiscoveryMode() }
                )
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.discoveryMode == DiscoveryMode.BaseUrl) "Base URL mode" else "Device calendar mode")
            }

            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("SIT-ICS-Go base URL") },
                placeholder = { Text("http://localhost:42748") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (uiState.discoveryMode == DiscoveryMode.DeviceCalendar) {
                        Text("Only used in Base URL mode")
                    }
                }
            )

            Text("Repeat Pattern", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = uiState.repeatInterval.toString(),
                onValueChange = { viewModel.updateRepeatInterval(it.toIntOrNull() ?: 5) },
                label = { Text("Repeat interval (minutes, 1-30)") },
                singleLine = true,
                supportingText = { ValidationSupportingText(uiState.repeatInterval.toString(), 1, 30, "Repeat interval") }
            )

            OutlinedTextField(
                value = uiState.maxRepeats.toString(),
                onValueChange = { viewModel.updateMaxRepeats(it.toIntOrNull() ?: 6) },
                label = { Text("Max repeats (1-20)") },
                singleLine = true,
                supportingText = { ValidationSupportingText(uiState.maxRepeats.toString(), 1, 20, "Max repeats") }
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
                    singleLine = true,
                    supportingText = { ValidationSupportingText(uiState.sleepDuration.toString(), 4, 12, "Target sleep") }
                )

                OutlinedTextField(
                    value = uiState.bedtimeOffset.toString(),
                    onValueChange = { viewModel.updateBedtimeOffset(it.toIntOrNull() ?: 30) },
                    label = { Text("Bedtime reminder offset (minutes, 5-60)") },
                    singleLine = true,
                    supportingText = { ValidationSupportingText(uiState.bedtimeOffset.toString(), 5, 60, "Bedtime offset") }
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
                    singleLine = true,
                    supportingText = { ValidationSupportingText(uiState.externalBuffer.toString(), 5, 120, "Buffer") }
                )

                if (uiState.discoveryMode == DiscoveryMode.BaseUrl && !uiState.calendarPicker.permissionGranted) {
                    Button(
                        onClick = { permissionLauncher.launch(READ_CALENDAR) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Calendar Permission")
                    }
                } else if (uiState.discoveryMode == DiscoveryMode.BaseUrl && uiState.calendarPicker.permissionGranted && !uiState.calendarPicker.isLoading && uiState.calendarPicker.calendars.isEmpty()) {
                    Text("No readable calendars found", style = MaterialTheme.typography.bodyMedium)
                } else if (uiState.calendarPicker.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                } else if (showCalendarPicker) {
                    Text("Select Calendars", style = MaterialTheme.typography.titleMedium)
                    CalendarPicker(
                        calendars = uiState.calendarPicker.calendars,
                        selectedIds = uiState.calendarPicker.selectedIds,
                        onToggleSelection = viewModel::toggleCalendarSelection,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text("Permissions", style = MaterialTheme.typography.titleLarge)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.permissionStatus, key = { it.permission }) { entry ->
                    PermissionCard(entry) { viewModel.openAppSettings(context) }
                }
            }

            if (uiState.bedtimeEnabled && !postNotificationsGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Bedtime reminders require notification permission.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.openAppSettings(context) }) {
                            Text("Open App Settings")
                        }
                    }
                }
            }
        }

        if (uiState.showModeSwitchDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelModeSwitch() },
                title = { Text("Switch Discovery Mode") },
                text = {
                    Text(
                        if (uiState.discoveryMode == DiscoveryMode.BaseUrl) {
                            "Switching to Device Calendar mode will stop using your SIT-ICS-Go base URL. Timetable data will be read from your device calendar instead."
                        } else {
                            "Switching to Base URL mode will stop using your device calendar. Timetable data will be fetched from your SIT-ICS-Go server."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmModeSwitch() }) {
                        Text("Switch")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelModeSwitch() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PermissionCard(
    entry: PermissionEntry,
    onOpenSettings: () -> Unit
) {
    val icon = when (entry.status) {
        PermissionStatus.Granted -> Icons.Default.CheckCircle
        else -> Icons.Default.Warning
    }

    val statusColor = if (entry.status == PermissionStatus.Granted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    val statusText = when (entry.status) {
        PermissionStatus.Granted -> "Granted"
        PermissionStatus.Denied -> "Denied"
        PermissionStatus.Required -> "Required"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = entry.permission,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = entry.justification,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entry.status == PermissionStatus.Denied || entry.status == PermissionStatus.Required) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSettings) {
                    Text("Open App Settings")
                }
            }
        }
    }
}
