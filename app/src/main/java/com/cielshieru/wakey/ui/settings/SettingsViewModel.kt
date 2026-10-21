package com.cielshieru.wakey.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.data.calendar.CalendarEntry
import com.cielshieru.wakey.data.model.ServerStatusResponse
import com.cielshieru.wakey.data.network.ServerStatusClient
import com.cielshieru.wakey.data.store.PermissionStore
import com.cielshieru.wakey.data.store.AlarmConfigStore.explicitNumericConfigFieldsFlow
import com.cielshieru.wakey.domain.model.NumericConfigField
import com.cielshieru.wakey.android.AlarmService
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.BedtimeReminderMode
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.DebugConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus
import com.cielshieru.wakey.domain.port.AlarmRepository
import com.cielshieru.wakey.domain.port.Clock
import com.cielshieru.wakey.domain.port.ZoneProvider
import com.cielshieru.wakey.domain.port.PermissionChecker
import androidx.work.ExistingPeriodicWorkPolicy
import com.cielshieru.wakey.usecase.SyncOperations
import com.cielshieru.wakey.usecase.SyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val onlineOffset: String = "",
    val explicitNumericFields: Set<NumericConfigField> = emptySet(),
    val campusOffset: String = "",
    val repeatInterval: String = "",
    val maxRepeats: String = "",
    val sleepDuration: String = "",
    val bedtimeOffset: String = "",
    val bedtimeReminderMode: BedtimeReminderMode = BedtimeReminderMode.Absolute,
    val bedtimeAbsoluteTime: String = "19:00",
    val externalBuffer: String = "",
    val externalEnabled: Boolean = false,
    val externalCalendarIds: List<String> = emptyList(),
    val bedtimeEnabled: Boolean = false,
    val remoteServerUrl: String = "",
    val discoveryMode: DiscoveryMode = DiscoveryMode.DeviceCalendar,
    val onlineCalendarPicker: CalendarDropdownUiState = CalendarDropdownUiState(),
    val campusCalendarPicker: CalendarDropdownUiState = CalendarDropdownUiState(),
    val externalEventCalendarPicker: MultiSelectCalendarPickerUiState = MultiSelectCalendarPickerUiState(),
    val permissionStatus: List<PermissionEntry> = emptyList(),
    val debugModeEnabled: Boolean = false,
    val targetDateMillis: Long? = null,
    val serverStatus: ServerStatusResponse? = null,
    val serverStatusError: String? = null,
    val serverStatusLoading: Boolean = false,
    val serverRefreshInProgress: Boolean = false,
    val serverRefreshMessage: String? = null
)

private fun SettingsUiState.withNumericInput(field: NumericConfigField, value: String): SettingsUiState =
    copy(
        explicitNumericFields = if (value.toIntOrNull() != null) {
            explicitNumericFields + field
        } else {
            explicitNumericFields - field
        }
    )

data class SavedServerSettings(
    val discoveryMode: DiscoveryMode = DiscoveryMode.DeviceCalendar,
    val remoteServerUrl: String = AlarmConfig.DEFAULT_REMOTE_SERVER_URL
)

data class CalendarDropdownUiState(
    val calendars: List<CalendarEntry> = emptyList(),
    val selectedId: String? = null,
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val permissionDenied: Boolean = false,
)

data class MultiSelectCalendarPickerUiState(
    val calendars: List<CalendarEntry> = emptyList(),
    val selectedIds: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val permissionDenied: Boolean = false,
)

sealed interface SyncEvent {
    data class SyncTriggered(
        val source: String,
        val mode: String,
        val nextSyncDue: String? = null
    ) : SyncEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val permissionChecker: PermissionChecker,
    private val datastore: DataStore<Preferences>,
    private val syncUseCase: SyncOperations,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val serverStatusClient: ServerStatusClient = ServerStatusClient()
) : ViewModel() {

    private var hasLoadedInitialConfig = false

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _savedServerSettings = MutableStateFlow(SavedServerSettings())
    val savedServerSettings: StateFlow<SavedServerSettings> = _savedServerSettings.asStateFlow()

    private val _configSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val configSaved: SharedFlow<Unit> = _configSaved

    private val _syncEvent = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1)
    val syncEvent: SharedFlow<SyncEvent> = _syncEvent

    init {
        // Track persisted settings independently of editable form state so remote-only
        // actions never appear until Remote Server mode has actually been saved.
        viewModelScope.launch {
            alarmRepository.config.collect { config ->
                _savedServerSettings.value = SavedServerSettings(
                    discoveryMode = config.discoveryMode,
                    remoteServerUrl = config.remoteServerUrl
                )
            }
        }

        viewModelScope.launch {
            combine(
                alarmRepository.config,
                alarmRepository.debugConfig,
                datastore.data.explicitNumericConfigFieldsFlow().take(1)
            ) { config, debug, explicitNumericFields ->
                val onlineId = config.onlineCalendarId
                val campusId = config.campusCalendarId

                SettingsUiState(
                    onlineOffset = if (NumericConfigField.ONLINE_OFFSET in explicitNumericFields) config.onlineOffsetMinutes.toString() else "",
                    campusOffset = if (NumericConfigField.CAMPUS_OFFSET in explicitNumericFields) config.campusOffsetMinutes.toString() else "",
                    repeatInterval = if (NumericConfigField.REPEAT_INTERVAL in explicitNumericFields) config.repeatIntervalMinutes.toString() else "",
                    maxRepeats = if (NumericConfigField.MAX_REPEATS in explicitNumericFields) config.maxRepeats.toString() else "",
                    sleepDuration = if (NumericConfigField.SLEEP_DURATION in explicitNumericFields) config.targetSleepDurationHours.toString() else "",
                    bedtimeOffset = if (NumericConfigField.BEDTIME_OFFSET in explicitNumericFields) config.bedtimeReminderOffsetMinutes.toString() else "",
                    bedtimeReminderMode = config.bedtimeReminderMode,
                    bedtimeAbsoluteTime = config.bedtimeReminderAbsoluteTime.toString(),
                    externalBuffer = if (NumericConfigField.EXTERNAL_BUFFER in explicitNumericFields) config.externalEventBufferMinutes.toString() else "",
                    explicitNumericFields = explicitNumericFields,
                    externalEnabled = config.externalEventEnabled,
                    externalCalendarIds = config.externalEventCalendarIds,
                    bedtimeEnabled = config.bedtimeEnabled,
                    remoteServerUrl = config.remoteServerUrl,
                    discoveryMode = config.discoveryMode,
                    onlineCalendarPicker = _uiState.value.onlineCalendarPicker.copy(selectedId = onlineId),
                    campusCalendarPicker = _uiState.value.campusCalendarPicker.copy(selectedId = campusId),
                    externalEventCalendarPicker = _uiState.value.externalEventCalendarPicker.copy(
                        selectedIds = config.externalEventCalendarIds
                    ),
                    debugModeEnabled = debug.debugModeEnabled,
                    targetDateMillis = debug.targetDateMillis
                )
            }.collect { state ->
                if (!hasLoadedInitialConfig) {
                    _uiState.value = state
                    hasLoadedInitialConfig = true
                }
            }
        }
    }

    fun updateOnlineOffset(value: String) {
        _uiState.update { it.withNumericInput(NumericConfigField.ONLINE_OFFSET, value).copy(onlineOffset = value) }
    }

    fun updateCampusOffset(value: String) {
        _uiState.update { it.withNumericInput(NumericConfigField.CAMPUS_OFFSET, value).copy(campusOffset = value) }
    }

    fun updateRepeatInterval(value: String) {
        _uiState.update { it.withNumericInput(NumericConfigField.REPEAT_INTERVAL, value).copy(repeatInterval = value) }
    }

    fun updateMaxRepeats(value: String) {
        _uiState.update { it.withNumericInput(NumericConfigField.MAX_REPEATS, value).copy(maxRepeats = value) }
    }

    fun updateSleepDuration(value: String) {
        _uiState.update { it.withNumericInput(NumericConfigField.SLEEP_DURATION, value).copy(sleepDuration = value) }
    }

    fun updateBedtimeOffset(value: String) {
        _uiState.update { it.withNumericInput(NumericConfigField.BEDTIME_OFFSET, value).copy(bedtimeOffset = value) }
    }

    fun selectBedtimeReminderMode(mode: BedtimeReminderMode) {
        _uiState.update { it.copy(bedtimeReminderMode = mode) }
    }

    fun updateBedtimeAbsoluteTime(value: String) {
        _uiState.update { it.copy(bedtimeAbsoluteTime = value) }
    }

    fun updateExternalBuffer(value: String) {
        _uiState.update { it.withNumericInput(NumericConfigField.EXTERNAL_BUFFER, value).copy(externalBuffer = value) }
    }

    fun toggleExternalEvents(enabled: Boolean) {
        _uiState.update { it.copy(externalEnabled = enabled) }
    }

    fun toggleBedtimeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(bedtimeEnabled = enabled) }
    }

    fun toggleDebugMode(enabled: Boolean) {
        if (!enabled) {
            _uiState.update { it.copy(debugModeEnabled = false, targetDateMillis = null) }
        } else {
            _uiState.update { it.copy(debugModeEnabled = true) }
        }
    }

    fun setTargetDate(millis: Long?) {
        _uiState.update { it.copy(targetDateMillis = millis) }
    }

    fun testAlarmNow() {
        if (!_uiState.value.debugModeEnabled) return

        // Capture the target before launching the coroutine so dispatch latency
        // does not shift the meaning of "the next second from button press".
        val triggerAt = clock.now().plusSeconds(1)
        val day = triggerAt.atZone(zoneProvider.zone).toLocalDate()

        viewModelScope.launch {
            val config = alarmRepository.config.first()
            // Use the ordinary schedule model and AlarmService so the test alarm
            // traverses the usual AlarmReceiver and playback-service path.
            alarmRepository.scheduleAlarm(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = triggerAt,
                    requestCode = AlarmService.DEBUG_TEST_ALARM_REQUEST_CODE,
                    ringtoneUri = config.ringtonePlaylist.ringtoneForRepeatIndex(0)
                ),
                day
            )
        }
    }

    fun saveDebugConfig() {
        viewModelScope.launch {
            persistDebugConfig(_uiState.value)
        }
    }

    private suspend fun persistDebugConfig(state: SettingsUiState) {
        alarmRepository.saveDebugConfig(
            DebugConfig(
                debugModeEnabled = state.debugModeEnabled,
                targetDateMillis = state.targetDateMillis
            )
        )
    }

    fun selectDiscoveryMode(mode: DiscoveryMode) {
        // Keep device-calendar selections and READ_CALENDAR permission state when the
        // user temporarily switches to Remote Server and back.
        _uiState.update { it.copy(discoveryMode = mode) }
    }

    fun updateRemoteServerUrl(value: String) {
        _uiState.update { it.copy(remoteServerUrl = value) }
    }

    fun setPermissionGranted(permissionName: String, granted: Boolean) {
        viewModelScope.launch {
            datastore.edit { prefs ->
                if (permissionName == "READ_CALENDAR") {
                    prefs[PermissionStore.CALENDAR_PERMISSION_DENIED_KEY] = !granted
                } else if (permissionName == "POST_NOTIFICATIONS") {
                    prefs[PermissionStore.POST_NOTIFICATIONS_DENIED_KEY] = !granted
                }
            }
        }
        _uiState.update {
            val updatedOnlinePicker = if (permissionName == "READ_CALENDAR") {
                it.onlineCalendarPicker.copy(
                    permissionGranted = granted,
                    permissionDenied = !granted
                )
            } else {
                it.onlineCalendarPicker
            }
            val updatedCampusPicker = if (permissionName == "READ_CALENDAR") {
                it.campusCalendarPicker.copy(
                    permissionGranted = granted,
                    permissionDenied = !granted
                )
            } else {
                it.campusCalendarPicker
            }
            val updatedPermissionStatus = it.permissionStatus.map { entry ->
                if (entry.permission == permissionName) {
                    entry.copy(status = if (granted) PermissionStatus.Granted else PermissionStatus.Denied)
                } else {
                    entry
                }
            }
            val updatedExternalPicker = if (permissionName == "READ_CALENDAR") {
                it.externalEventCalendarPicker.copy(
                    permissionGranted = granted,
                    permissionDenied = !granted
                )
            } else {
                it.externalEventCalendarPicker
            }
            it.copy(
                onlineCalendarPicker = updatedOnlinePicker,
                campusCalendarPicker = updatedCampusPicker,
                externalEventCalendarPicker = updatedExternalPicker,
                permissionStatus = updatedPermissionStatus
            )
        }
    }

    fun setDeviceCalendarLoading(isLoading: Boolean) {
        _uiState.update { state ->
            state.copy(
                onlineCalendarPicker = state.onlineCalendarPicker.copy(isLoading = isLoading),
                campusCalendarPicker = state.campusCalendarPicker.copy(isLoading = isLoading),
                externalEventCalendarPicker = state.externalEventCalendarPicker.copy(isLoading = isLoading)
            )
        }
    }

    fun loadDeviceCalendars(calendars: List<CalendarEntry>) {
        _uiState.update { state ->
            state.copy(
                onlineCalendarPicker = state.onlineCalendarPicker.copy(
                    calendars = calendars,
                    isLoading = false
                ),
                campusCalendarPicker = state.campusCalendarPicker.copy(
                    calendars = calendars,
                    isLoading = false
                ),
                externalEventCalendarPicker = state.externalEventCalendarPicker.copy(
                    calendars = calendars,
                    isLoading = false
                )
            )
        }
    }

    fun selectOnlineCalendar(id: String?) {
        _uiState.update {
            it.copy(
                onlineCalendarPicker = it.onlineCalendarPicker.copy(selectedId = id)
            )
        }
    }

    fun selectCampusCalendar(id: String?) {
        _uiState.update {
            it.copy(
                campusCalendarPicker = it.campusCalendarPicker.copy(selectedId = id)
            )
        }
    }

    fun toggleExternalEventCalendar(calendarId: String) {
        _uiState.update { state ->
            val currentIds = state.externalCalendarIds.toMutableSet()
            if (calendarId in currentIds) {
                currentIds.remove(calendarId)
            } else {
                currentIds.add(calendarId)
            }
            state.copy(
                externalCalendarIds = currentIds.toList(),
                externalEventCalendarPicker = state.externalEventCalendarPicker.copy(
                    selectedIds = currentIds.toList()
                )
            )
        }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            val currentConfig = alarmRepository.config.first()
            val selectedCalendarIds = buildList {
                state.onlineCalendarPicker.selectedId?.let { add(it) }
                state.campusCalendarPicker.selectedId?.let { add(it) }
            }.distinct()

            val config = AlarmConfig(
                onlineOffsetMinutes = state.onlineOffset.toIntOrNull() ?: AlarmConfig.DEFAULT_ONLINE_OFFSET,
                campusOffsetMinutes = state.campusOffset.toIntOrNull() ?: AlarmConfig.DEFAULT_CAMPUS_OFFSET,
                repeatIntervalMinutes = state.repeatInterval.toIntOrNull() ?: AlarmConfig.DEFAULT_REPEAT_INTERVAL,
                maxRepeats = state.maxRepeats.toIntOrNull() ?: AlarmConfig.DEFAULT_MAX_REPEATS,
                targetSleepDurationHours = state.sleepDuration.toIntOrNull() ?: AlarmConfig.DEFAULT_SLEEP_DURATION,
                bedtimeReminderOffsetMinutes = state.bedtimeOffset.toIntOrNull() ?: AlarmConfig.DEFAULT_BEDTIME_OFFSET,
                bedtimeReminderMode = state.bedtimeReminderMode,
                bedtimeReminderAbsoluteTime = runCatching { java.time.LocalTime.parse(state.bedtimeAbsoluteTime) }.getOrDefault(AlarmConfig.DEFAULT_BEDTIME_ABSOLUTE_TIME),
                externalEventBufferMinutes = state.externalBuffer.toIntOrNull() ?: AlarmConfig.DEFAULT_BUFFER_MINUTES,
                externalEventEnabled = state.externalEnabled,
                externalEventCalendarIds = state.externalCalendarIds,
                selectedCalendarIds = selectedCalendarIds,
                onlineCalendarId = state.onlineCalendarPicker.selectedId,
                campusCalendarId = state.campusCalendarPicker.selectedId,
                bedtimeEnabled = state.bedtimeEnabled,
                remoteServerUrl = state.remoteServerUrl,
                discoveryMode = state.discoveryMode,
                ringtonePlaylist = currentConfig.ringtonePlaylist
            )
            alarmRepository.saveConfig(config, state.explicitNumericFields)
            persistDebugConfig(state)

            syncUseCase.cancelSync()
            syncUseCase.schedulePeriodicSync(ExistingPeriodicWorkPolicy.KEEP)
            syncUseCase.syncImmediately(SyncUseCase.UNIQUE_IMMEDIATE_SYNC)
            _syncEvent.emit(
                SyncEvent.SyncTriggered(
                    source = "Settings save",
                    mode = "immediate"
                )
            )
            _configSaved.emit(Unit)
        }
    }

    fun fetchServerStatus() {
        val saved = _savedServerSettings.value
        if (saved.discoveryMode != DiscoveryMode.RemoteServer) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    serverStatus = null,
                    serverStatusLoading = true,
                    serverStatusError = null,
                    serverRefreshMessage = null
                )
            }
            try {
                val status = serverStatusClient.fetchStatus(saved.remoteServerUrl)
                _uiState.update {
                    it.copy(serverStatus = status, serverStatusLoading = false, serverStatusError = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        serverStatusLoading = false,
                        serverStatusError = e.message ?: "Unable to fetch server status"
                    )
                }
            }
        }
    }

    fun requestServerRefresh() {
        val saved = _savedServerSettings.value
        if (saved.discoveryMode != DiscoveryMode.RemoteServer) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    serverRefreshInProgress = true,
                    serverStatusError = null,
                    serverRefreshMessage = null
                )
            }
            try {
                serverStatusClient.requestRefresh(saved.remoteServerUrl)
                _uiState.update {
                    it.copy(serverRefreshMessage = "Refresh job scheduled.")
                }
                // The server acknowledges scheduling before the job finishes. A short
                // delay lets the in-memory job start timestamp become visible on GET.
                delay(300)
                val status = serverStatusClient.fetchStatus(saved.remoteServerUrl)
                _uiState.update {
                    it.copy(
                        serverStatus = status,
                        serverStatusLoading = false,
                        serverRefreshInProgress = false,
                        serverStatusError = null,
                        serverRefreshMessage = "Refresh job scheduled."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        serverRefreshInProgress = false,
                        serverStatusError = e.message ?: "Unable to request server refresh"
                    )
                }
            }
        }
    }

    fun refreshPermissionState(context: Context) {
        viewModelScope.launch {
            val entries = permissionChecker.getAllPermissionEntries(context)
            val calendarGranted = permissionChecker.checkCalendarPermission(context)
            _uiState.update {
                it.copy(
                    permissionStatus = entries,
                    onlineCalendarPicker = it.onlineCalendarPicker.copy(
                        permissionGranted = calendarGranted,
                        isLoading = false
                    ),
                    campusCalendarPicker = it.campusCalendarPicker.copy(
                        permissionGranted = calendarGranted,
                        isLoading = false
                    ),
                    externalEventCalendarPicker = it.externalEventCalendarPicker.copy(
                        permissionGranted = calendarGranted,
                        isLoading = false
                    )
                )
            }
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
