package com.cielshieru.wakey.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.cielshieru.wakey.data.calendar.getReadableCalendars
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdownCalendarPicker(
    state: CalendarDropdownUiState,
    label: String,
    onSelectCalendar: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = state.calendars.find { it.id == state.selectedId }?.displayName ?: "Select a calendar",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = state.permissionGranted,
            supportingText = {
                if (state.permissionDenied) {
                    Text("Permission denied — grant in app settings")
                } else if (!state.permissionGranted) {
                    Text("Permission required")
                } else if (state.isLoading) {
                    Text("Loading calendars...")
                }
            }
        )

        ExposedDropdownMenu(
            expanded = expanded && state.permissionGranted && !state.isLoading,
            onDismissRequest = { expanded = false }
        ) {
            if (state.calendars.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No calendars available") },
                    onClick = { expanded = false }
                )
            } else {
                state.calendars.forEach { calendar ->
                    DropdownMenuItem(
                        text = { Text(calendar.displayName) },
                        onClick = {
                            onSelectCalendar(calendar.id)
                            expanded = false
                        },
                        leadingIcon = {
                            if (calendar.id == state.selectedId) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectCalendarPicker(
    state: MultiSelectCalendarPickerUiState,
    label: String,
    onCalendarToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedDisplayText = when {
        state.selectedIds.isEmpty() -> "Select calendars"
        state.selectedIds.size <= 2 -> {
            state.calendars
                .filter { it.id in state.selectedIds }
                .joinToString(", ") { it.displayName }
        }
        else -> {
            val count = state.selectedIds.size
            "$count calendars selected"
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedDisplayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = state.permissionGranted,
            supportingText = {
                if (state.permissionDenied) {
                    Text("Permission denied — grant in app settings")
                } else if (!state.permissionGranted) {
                    Text("Permission required")
                } else if (state.isLoading) {
                    Text("Loading calendars...")
                }
            }
        )

        ExposedDropdownMenu(
            expanded = expanded && state.permissionGranted && !state.isLoading,
            onDismissRequest = { expanded = false }
        ) {
            if (state.calendars.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No calendars available") },
                    onClick = { expanded = false }
                )
            } else {
                state.calendars.forEach { calendar ->
                    val isSelected = calendar.id in state.selectedIds
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onCalendarToggle(calendar.id) }
                                )
                                Text(calendar.displayName)
                            }
                        },
                        onClick = {
                            onCalendarToggle(calendar.id)
                        }
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
    onNavigateToRingtonePicker: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.configSaved.collect {
            onShowSnackbar("Settings saved")
        }
    }

    val postNotificationsGranted = uiState.permissionStatus
        .firstOrNull { it.permission == "POST_NOTIFICATIONS" }
        ?.status == PermissionStatus.Granted

    LaunchedEffect(uiState.onlineCalendarPicker.permissionGranted, uiState.discoveryMode) {
        val calendarsNeedLoading =
            uiState.onlineCalendarPicker.calendars.isEmpty() ||
                uiState.campusCalendarPicker.calendars.isEmpty() ||
                uiState.externalEventCalendarPicker.calendars.isEmpty()
        val anyPickerLoading =
            uiState.onlineCalendarPicker.isLoading ||
                uiState.campusCalendarPicker.isLoading ||
                uiState.externalEventCalendarPicker.isLoading

        if (uiState.discoveryMode == DiscoveryMode.DeviceCalendar &&
            uiState.onlineCalendarPicker.permissionGranted &&
            calendarsNeedLoading &&
            !anyPickerLoading
        ) {
            viewModel.setDeviceCalendarLoading(true)
            try {
                viewModel.loadDeviceCalendars(getReadableCalendars(context))
            } catch (e: kotlinx.coroutines.CancellationException) {
                viewModel.setDeviceCalendarLoading(false)
                throw e
            } catch (e: Exception) {
                viewModel.setDeviceCalendarLoading(false)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setPermissionGranted("READ_CALENDAR", true)
        } else {
            viewModel.setPermissionGranted("READ_CALENDAR", false)
        }
    }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setPermissionGranted("POST_NOTIFICATIONS", granted)
    }

    val permissionLauncherMap = remember {
        mapOf(
            "POST_NOTIFICATIONS" to { postNotificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
            "READ_CALENDAR" to { permissionLauncher.launch(android.Manifest.permission.READ_CALENDAR) }
        )
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
                        if (uiState.externalEnabled &&
                            uiState.externalCalendarIds.isEmpty()
                        ) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Text("Alarm Offsets", style = MaterialTheme.typography.titleLarge)
            }

            item {
                OutlinedTextField(
                    value = uiState.onlineOffset.toString(),
                    onValueChange = { viewModel.updateOnlineOffset(it.toIntOrNull() ?: 60) },
                    label = { Text("Online offset (minutes)") },
                    singleLine = true,
                    supportingText = { ValidationSupportingText(uiState.onlineOffset.toString(), 1, 720, "Online offset") }
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.campusOffset.toString(),
                    onValueChange = { viewModel.updateCampusOffset(it.toIntOrNull() ?: 90) },
                    label = { Text("Campus offset (minutes)") },
                    singleLine = true,
                    supportingText = { ValidationSupportingText(uiState.campusOffset.toString(), 1, 720, "Campus offset") }
                )
            }

            item {
                Text("SIT-ICS-Go Discovery", style = MaterialTheme.typography.titleLarge)
            }

            item {
                DiscoveryModeSegmentedButton(
                    selectedMode = uiState.discoveryMode,
                    onModeSelected = viewModel::selectDiscoveryMode
                )
            }

            if (uiState.discoveryMode == DiscoveryMode.RemoteServer) {
                item {
                    OutlinedTextField(
                        value = uiState.remoteServerUrl,
                        onValueChange = viewModel::updateRemoteServerUrl,
                        label = { Text("Remote Server URL") },
                        placeholder = { Text("http://localhost:42748") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("URL of your SIT-ICS-Go server running wakey-sitizen plugin")
                        }
                    )
                }
            }

            if (uiState.discoveryMode == DiscoveryMode.DeviceCalendar) {
                item {
                    Text("Online Timetable Calendar", style = MaterialTheme.typography.titleMedium)
                }

                item {
                    ExposedDropdownCalendarPicker(
                        state = uiState.onlineCalendarPicker,
                        label = "Online Timetable Calendar",
                                                onSelectCalendar = viewModel::selectOnlineCalendar
                    )
                }

                item {
                    Text("In-Campus Timetable Calendar", style = MaterialTheme.typography.titleMedium)
                }

                item {
                    ExposedDropdownCalendarPicker(
                        state = uiState.campusCalendarPicker,
                        label = "In-Campus Timetable Calendar",
                                                onSelectCalendar = viewModel::selectCampusCalendar
                    )
                }

                item {
                    if (uiState.onlineCalendarPicker.permissionGranted &&
                        uiState.onlineCalendarPicker.calendars.isEmpty() &&
                        !uiState.onlineCalendarPicker.isLoading
                    ) {
                        Text(
                            "No readable calendars found on this device",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Text("Repeat Pattern", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Button(
                    onClick = { onNavigateToRingtonePicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Ringtones")
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.repeatInterval.toString(),
                    onValueChange = { viewModel.updateRepeatInterval(it.toIntOrNull() ?: 5) },
                    label = { Text("Repeat interval (minutes, 1-30)") },
                    singleLine = true,
                    supportingText = { ValidationSupportingText(uiState.repeatInterval.toString(), 1, 30, "Repeat interval") }
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.maxRepeats.toString(),
                    onValueChange = { viewModel.updateMaxRepeats(it.toIntOrNull() ?: 6) },
                    label = { Text("Max repeats (1-20)") },
                    singleLine = true,
                    supportingText = { ValidationSupportingText(uiState.maxRepeats.toString(), 1, 20, "Max repeats") }
                )
            }

            item {
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
            }

            if (uiState.bedtimeEnabled) {
                item {
                    OutlinedTextField(
                        value = uiState.sleepDuration.toString(),
                        onValueChange = { viewModel.updateSleepDuration(it.toIntOrNull() ?: 8) },
                        label = { Text("Target sleep (hours, 4-12)") },
                        singleLine = true,
                        supportingText = { ValidationSupportingText(uiState.sleepDuration.toString(), 4, 12, "Target sleep") }
                    )
                }

                item {
                    OutlinedTextField(
                        value = uiState.bedtimeOffset.toString(),
                        onValueChange = { viewModel.updateBedtimeOffset(it.toIntOrNull() ?: 30) },
                        label = { Text("Bedtime reminder offset (minutes, 5-60)") },
                        singleLine = true,
                        supportingText = { ValidationSupportingText(uiState.bedtimeOffset.toString(), 5, 60, "Bedtime offset") }
                    )
                }
            }

            if (uiState.bedtimeEnabled && !postNotificationsGranted) {
                item {
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
                            Button(onClick = { postNotificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("Grant")
                            }
                        }
                    }
                }
            }

            item {
                Text("External Events", style = MaterialTheme.typography.titleLarge)
            }

            item {
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
            }

            if (uiState.externalEnabled) {
                item {
                    OutlinedTextField(
                        value = uiState.externalBuffer.toString(),
                        onValueChange = { viewModel.updateExternalBuffer(it.toIntOrNull() ?: 30) },
                        label = { Text("Buffer minutes (5-120)") },
                        singleLine = true,
                        supportingText = { ValidationSupportingText(uiState.externalBuffer.toString(), 5, 120, "Buffer") }
                    )
                }

                if (uiState.discoveryMode == DiscoveryMode.DeviceCalendar) {
                    item {
                        MultiSelectCalendarPicker(
                            state = uiState.externalEventCalendarPicker,
                                                        onCalendarToggle = viewModel::toggleExternalEventCalendar,
                            label = "External Event Calendars"
                        )
                    }

                    item {
                        if (uiState.externalEventCalendarPicker.permissionGranted &&
                            uiState.externalEventCalendarPicker.calendars.isEmpty() &&
                            !uiState.externalEventCalendarPicker.isLoading
                        ) {
                            Text(
                                "No readable calendars found on this device",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                }
            }

            item {
                Text("Debug", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = uiState.debugModeEnabled,
                        onCheckedChange = viewModel::toggleDebugMode
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Enable debug mode")
                }
            }

            if (uiState.debugModeEnabled) {
                item {
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
                                    text = "Debug mode overrides may produce unexpected alarm behavior. Do not use in production.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                item {
                    val context = LocalContext.current
                    var showDatePicker by remember { mutableStateOf(false) }
                    val selectedDateText = uiState.targetDateMillis?.let { millis ->
                        val instant = Instant.ofEpochMilli(millis)
                        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(java.util.Date(millis))
                    } ?: "Select target date"

                    Button(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedDateText)
                    }

                    if (showDatePicker) {
                        var selectedInstant by remember { mutableStateOf<Instant?>(null) }
                        val initialDate = uiState.targetDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDate = initialDate
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        viewModel.setTargetDate(millis)
                                    }
                                    showDatePicker = false
                                }) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDatePicker = false
                                }) {
                                    Text("Cancel")
                                }
                            }
                        ) {
                            DatePicker(
                                state = datePickerState,
                                showModeToggle = true
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            viewModel.setTargetDate(null)
                        }) {
                            Text("Clear date")
                        }
                    }
                }
            }

            item {
                Text("Permissions", style = MaterialTheme.typography.titleLarge)
            }

            items(uiState.permissionStatus, key = { it.permission }) { entry ->
                PermissionCard(
                    entry = entry,
                    onRequestPermission = { permissionLauncherMap[entry.permission]?.invoke() },
                    onOpenSettings = { viewModel.openAppSettings(context) }
                )
            }
        }

    }
}

@Composable
private fun DiscoveryModeSegmentedButton(
    selectedMode: DiscoveryMode,
    onModeSelected: (DiscoveryMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            selected = selectedMode == DiscoveryMode.DeviceCalendar,
            onClick = { onModeSelected(DiscoveryMode.DeviceCalendar) },
            icon = {
                if (selectedMode == DiscoveryMode.DeviceCalendar) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                }
            }
        ) {
            Text("Device Calendar")
        }

        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            selected = selectedMode == DiscoveryMode.RemoteServer,
            onClick = { onModeSelected(DiscoveryMode.RemoteServer) },
            icon = {
                if (selectedMode == DiscoveryMode.RemoteServer) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                }
            }
        ) {
            Text("Remote Server")
        }
    }
}

@Composable
fun PermissionCard(
    entry: PermissionEntry,
    onRequestPermission: () -> Unit,
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
                        text = entry.featureName,
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

            if (entry.isRuntimePermission && entry.status != PermissionStatus.Granted) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRequestPermission) {
                    Text("Grant")
                }
            }
        }
    }
}
