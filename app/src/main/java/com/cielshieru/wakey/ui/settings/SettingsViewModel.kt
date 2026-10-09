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
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus
import com.cielshieru.wakey.domain.port.AlarmRepository
import com.cielshieru.wakey.domain.port.PermissionChecker
import com.cielshieru.wakey.usecase.SyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val bedtimeEnabled: Boolean = false,
    val baseUrl: String = "",
    val discoveryMode: DiscoveryMode = DiscoveryMode.BaseUrl,
    val calendarPicker: CalendarPickerUiState = CalendarPickerUiState(),
    val permissionStatus: List<PermissionEntry> = emptyList(),
    val showModeSwitchDialog: Boolean = false
)

data class CalendarPickerUiState(
    val calendars: List<CalendarEntry> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val permissionDenied: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val permissionChecker: PermissionChecker,
    private val datastore: DataStore<Preferences>,
    private val syncUseCase: SyncUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _configSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val configSaved: SharedFlow<Unit> = _configSaved

    init {
        viewModelScope.launch {
            alarmRepository.config.collect { config ->
                _uiState.value = SettingsUiState(
                    onlineOffset = config.onlineOffsetMinutes,
                    campusOffset = config.campusOffsetMinutes,
                    repeatInterval = config.repeatIntervalMinutes,
                    maxRepeats = config.maxRepeats,
                    sleepDuration = config.targetSleepDurationHours,
                    bedtimeOffset = config.bedtimeReminderOffsetMinutes,
                    externalBuffer = config.externalEventBufferMinutes,
                    externalEnabled = config.externalEventEnabled,
                    bedtimeEnabled = config.bedtimeEnabled,
                    baseUrl = config.baseUrl,
                    discoveryMode = config.discoveryMode,
                    calendarPicker = _uiState.value.calendarPicker.copy(
                        selectedIds = config.selectedCalendarIds.toSet()
                    )
                )
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
        if (!enabled) {
            _uiState.update {
                it.copy(
                    calendarPicker = it.calendarPicker.copy(
                        isLoading = false,
                        permissionGranted = false
                    )
                )
            }
        }
    }

    fun toggleBedtimeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(bedtimeEnabled = enabled) }
    }

    fun toggleDiscoveryMode() {
        _uiState.update { it.copy(showModeSwitchDialog = true) }
    }

    fun confirmModeSwitch() {
        val switchingToBaseUrl = _uiState.value.discoveryMode == DiscoveryMode.DeviceCalendar
        _uiState.update {
            it.copy(
                discoveryMode = if (switchingToBaseUrl) {
                    DiscoveryMode.BaseUrl
                } else {
                    DiscoveryMode.DeviceCalendar
                },
                showModeSwitchDialog = false,
                calendarPicker = if (switchingToBaseUrl) {
                    it.calendarPicker.copy(
                        selectedIds = emptySet(),
                        permissionGranted = false
                    )
                } else {
                    it.calendarPicker
                }
            )
        }
    }

    fun cancelModeSwitch() {
        _uiState.update { it.copy(showModeSwitchDialog = false) }
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
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
            val updatedCalendarPicker = if (permissionName == "READ_CALENDAR") {
                it.calendarPicker.copy(
                    permissionGranted = granted,
                    permissionDenied = !granted
                )
            } else {
                it.calendarPicker
            }
            val updatedPermissionStatus = it.permissionStatus.map { entry ->
                if (entry.permission == permissionName) {
                    entry.copy(status = if (granted) PermissionStatus.Granted else PermissionStatus.Denied)
                } else {
                    entry
                }
            }
            it.copy(
                calendarPicker = updatedCalendarPicker,
                permissionStatus = updatedPermissionStatus
            )
        }
    }

    fun loadCalendars(calendars: List<CalendarEntry>) {
        _uiState.update {
            it.copy(
                calendarPicker = it.calendarPicker.copy(
                    calendars = calendars,
                    isLoading = false
                )
            )
        }
    }

    fun toggleCalendarSelection(id: String) {
        _uiState.update { state ->
            val current = state.calendarPicker.selectedIds.toMutableSet()
            if (current.contains(id)) {
                if (current.size > 1) {
                    current.remove(id)
                }
            } else {
                current.add(id)
            }
            state.copy(
                calendarPicker = state.calendarPicker.copy(selectedIds = current)
            )
        }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            val config = AlarmConfig(
                onlineOffsetMinutes = state.onlineOffset,
                campusOffsetMinutes = state.campusOffset,
                repeatIntervalMinutes = state.repeatInterval,
                maxRepeats = state.maxRepeats,
                targetSleepDurationHours = state.sleepDuration,
                bedtimeReminderOffsetMinutes = state.bedtimeOffset,
                externalEventBufferMinutes = state.externalBuffer,
                externalEventEnabled = state.externalEnabled,
                selectedCalendarIds = state.calendarPicker.selectedIds.toList(),
                bedtimeEnabled = state.bedtimeEnabled,
                baseUrl = state.baseUrl,
                discoveryMode = state.discoveryMode
            )
            alarmRepository.saveConfig(config)
            syncUseCase.cancelSync()
            syncUseCase.schedulePeriodicSync()
            syncUseCase.syncImmediately()
            _configSaved.emit(Unit)
        }
    }

    fun refreshPermissionState(context: Context) {
        viewModelScope.launch {
            val entries = permissionChecker.getAllPermissionEntries(context)
            _uiState.update { it.copy(permissionStatus = entries) }
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
