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
import com.cielshieru.wakey.data.store.PermissionStore
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DebugConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus
import com.cielshieru.wakey.domain.port.AlarmRepository
import com.cielshieru.wakey.domain.port.PermissionChecker
import androidx.work.ExistingPeriodicWorkPolicy
import com.cielshieru.wakey.usecase.SyncOperations
import com.cielshieru.wakey.usecase.SyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val onlineOffset: Int = 60,
    val campusOffset: Int = 90,
    val repeatInterval: Int = 5,
    val maxRepeats: Int = 6,
    val sleepDuration: Int = 8,
    val bedtimeOffset: Int = 30,
    val externalBuffer: Int = 30,
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
    val targetDateMillis: Long? = null
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
    private val syncUseCase: SyncOperations
) : ViewModel() {

    private var hasLoadedInitialConfig = false

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _configSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val configSaved: SharedFlow<Unit> = _configSaved

    private val _syncEvent = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1)
    val syncEvent: SharedFlow<SyncEvent> = _syncEvent

    init {
        viewModelScope.launch {
            combine(
                alarmRepository.config,
                alarmRepository.debugConfig
            ) { config, debug ->
                val calendarIds = config.selectedCalendarIds
                val onlineId = calendarIds.getOrNull(0)
                val campusId = if (calendarIds.size > 1) calendarIds[1] else null

                SettingsUiState(
                    onlineOffset = config.onlineOffsetMinutes,
                    campusOffset = config.campusOffsetMinutes,
                    repeatInterval = config.repeatIntervalMinutes,
                    maxRepeats = config.maxRepeats,
                    sleepDuration = config.targetSleepDurationHours,
                    bedtimeOffset = config.bedtimeReminderOffsetMinutes,
                    externalBuffer = config.externalEventBufferMinutes,
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

    fun updateOnlineOffset(value: Int) {
        _uiState.update { it.copy(onlineOffset = value) }
    }

    fun updateCampusOffset(value: Int) {
        _uiState.update { it.copy(campusOffset = value) }
    }

    fun updateRepeatInterval(value: Int) {
        _uiState.update { it.copy(repeatInterval = value) }
    }

    fun updateMaxRepeats(value: Int) {
        _uiState.update { it.copy(maxRepeats = value) }
    }

    fun updateSleepDuration(value: Int) {
        _uiState.update { it.copy(sleepDuration = value) }
    }

    fun updateBedtimeOffset(value: Int) {
        _uiState.update { it.copy(bedtimeOffset = value) }
    }

    fun updateExternalBuffer(value: Int) {
        _uiState.update { it.copy(externalBuffer = value) }
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

    fun saveDebugConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            val config = DebugConfig(
                debugModeEnabled = state.debugModeEnabled,
                targetDateMillis = state.targetDateMillis
            )
            alarmRepository.saveDebugConfig(config)
        }
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
            val selectedCalendarIds = buildList {
                state.onlineCalendarPicker.selectedId?.let { add(it) }
                state.campusCalendarPicker.selectedId?.let { add(it) }
            }.distinct()

            val config = AlarmConfig(
                onlineOffsetMinutes = state.onlineOffset,
                campusOffsetMinutes = state.campusOffset,
                repeatIntervalMinutes = state.repeatInterval,
                maxRepeats = state.maxRepeats,
                targetSleepDurationHours = state.sleepDuration,
                bedtimeReminderOffsetMinutes = state.bedtimeOffset,
                externalEventBufferMinutes = state.externalBuffer,
                externalEventEnabled = state.externalEnabled,
                externalEventCalendarIds = state.externalCalendarIds,
                selectedCalendarIds = selectedCalendarIds,
                bedtimeEnabled = state.bedtimeEnabled,
                remoteServerUrl = state.remoteServerUrl,
                discoveryMode = state.discoveryMode
            )
            alarmRepository.saveConfig(config)
            saveDebugConfig()

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
