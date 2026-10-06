package com.cielshieru.wakey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.port.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    val bedtimeEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
                    bedtimeEnabled = config.bedtimeEnabled
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
    }

    fun toggleBedtimeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(bedtimeEnabled = enabled) }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            alarmRepository.saveConfig(
                AlarmConfig(
                    onlineOffsetMinutes = state.onlineOffset,
                    campusOffsetMinutes = state.campusOffset,
                    repeatIntervalMinutes = state.repeatInterval,
                    maxRepeats = state.maxRepeats,
                    targetSleepDurationHours = state.sleepDuration,
                    bedtimeReminderOffsetMinutes = state.bedtimeOffset,
                    externalEventBufferMinutes = state.externalBuffer,
                    externalEventEnabled = state.externalEnabled,
                    bedtimeEnabled = state.bedtimeEnabled
                )
            )
        }
    }
}
