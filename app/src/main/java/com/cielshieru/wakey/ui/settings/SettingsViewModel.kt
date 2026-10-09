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
    val remoteServerUrl: String = "",
    val discoveryMode: DiscoveryMode = DiscoveryMode.DeviceCalendar,
    val onlineCalendarPicker: CalendarDropdownUiState = CalendarDropdownUiState(),
    val campusCalendarPicker: CalendarDropdownUiState = CalendarDropdownUiState(),
    val permissionStatus: List<PermissionEntry> = emptyList()
)

data class CalendarDropdownUiState(
    val calendars: List<CalendarEntry> = emptyList(),
    val selectedId: String? = null,
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val permissionDenied: Boolean = false,
    val showDropdown: Boolean = false
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
    private val syncUseCase: SyncUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _configSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val configSaved: SharedFlow<Unit> = _configSaved

    private val _syncEvent = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1)
    val syncEvent: SharedFlow<SyncEvent> = _syncEvent

    init {
        viewModelScope.launch {
            alarmRepository.config.collect { config ->
                val calendarIds = config.selectedCalendarIds
                val onlineId = calendarIds.getOrNull(0)
                val campusId = if (calendarIds.size > 1) calendarIds[1] else null

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
                    remoteServerUrl = config.remoteServerUrl,
                    discoveryMode = config.discoveryMode,
                    onlineCalendarPicker = _uiState.value.onlineCalendarPicker.copy(selectedId = onlineId),
                    campusCalendarPicker = _uiState.value.campusCalendarPicker.copy(selectedId = campusId)
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
                    onlineCalendarPicker = it.onlineCalendarPicker.copy(
                        isLoading = false,
                        permissionGranted = false
                    ),
                    campusCalendarPicker = it.campusCalendarPicker.copy(
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

    fun selectDiscoveryMode(mode: DiscoveryMode) {
        _uiState.update {
            it.copy(
                discoveryMode = mode,
                onlineCalendarPicker = if (mode == DiscoveryMode.RemoteServer) {
                    it.onlineCalendarPicker.copy(
                        selectedId = null,
                        permissionGranted = false
                    )
                } else {
                    it.onlineCalendarPicker
                },
                campusCalendarPicker = if (mode == DiscoveryMode.RemoteServer) {
                    it.campusCalendarPicker.copy(
                        selectedId = null,
                        permissionGranted = false
                    )
                } else {
                    it.campusCalendarPicker
                }
            )
        }
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
            it.copy(
                onlineCalendarPicker = updatedOnlinePicker,
                campusCalendarPicker = updatedCampusPicker,
                permissionStatus = updatedPermissionStatus
            )
        }
    }

    fun loadOnlineCalendars(calendars: List<CalendarEntry>) {
        _uiState.update {
            it.copy(
                onlineCalendarPicker = it.onlineCalendarPicker.copy(
                    calendars = calendars,
                    isLoading = false
                )
            )
        }
    }

    fun loadCampusCalendars(calendars: List<CalendarEntry>) {
        _uiState.update {
            it.copy(
                campusCalendarPicker = it.campusCalendarPicker.copy(
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

    fun toggleOnlineDropdown() {
        _uiState.update { state ->
            val picker = state.onlineCalendarPicker
            if (picker.permissionGranted && !picker.isLoading) {
                state.copy(
                    onlineCalendarPicker = picker.copy(showDropdown = !picker.showDropdown)
                )
            } else {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            onlineCalendarPicker = it.onlineCalendarPicker.copy(
                                isLoading = true,
                                showDropdown = false
                            )
                        )
                    }
                }
                state
            }
        }
    }

    fun toggleCampusDropdown() {
        _uiState.update { state ->
            val picker = state.campusCalendarPicker
            if (picker.permissionGranted && !picker.isLoading) {
                state.copy(
                    campusCalendarPicker = picker.copy(showDropdown = !picker.showDropdown)
                )
            } else {
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            campusCalendarPicker = it.campusCalendarPicker.copy(
                                isLoading = true,
                                showDropdown = false
                            )
                        )
                    }
                }
                state
            }
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
                selectedCalendarIds = selectedCalendarIds,
                bedtimeEnabled = state.bedtimeEnabled,
                remoteServerUrl = state.remoteServerUrl,
                discoveryMode = state.discoveryMode
            )
            alarmRepository.saveConfig(config)
            syncUseCase.cancelSync()
            syncUseCase.schedulePeriodicSync()
            syncUseCase.syncImmediately()
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
