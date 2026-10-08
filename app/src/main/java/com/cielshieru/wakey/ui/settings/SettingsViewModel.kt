package com.cielshieru.wakey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.data.calendar.CalendarEntry
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.port.AlarmRepository
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
    val calendarPicker: CalendarPickerUiState = CalendarPickerUiState()
)

data class CalendarPickerUiState(
    val calendars: List<CalendarEntry> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false
)

sealed interface CalendarPermissionEvent {
    object Granted : CalendarPermissionEvent
    object Denied : CalendarPermissionEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _permissionEvent = MutableSharedFlow<CalendarPermissionEvent>(extraBufferCapacity = 1)
    val permissionEvent: SharedFlow<CalendarPermissionEvent> = _permissionEvent

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
        _uiState.update {
            it.copy(
                discoveryMode = if (it.discoveryMode == DiscoveryMode.BaseUrl) {
                    DiscoveryMode.DeviceCalendar
                } else {
                    DiscoveryMode.BaseUrl
                }
            )
        }
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update {
            it.copy(
                calendarPicker = it.calendarPicker.copy(permissionGranted = granted)
            )
        }
        viewModelScope.launch {
            if (granted) {
                _permissionEvent.emit(CalendarPermissionEvent.Granted)
            } else {
                _permissionEvent.emit(CalendarPermissionEvent.Denied)
            }
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
        }
    }
}
