package com.cielshieru.wakey.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.model.OverrideType
import com.cielshieru.wakey.domain.port.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HomeUiState(
    val nextAlarmText: String = "No alarms set",
    val overrideText: String? = null,
    val isSyncing: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            alarmRepository.overrides
                .combine(alarmRepository.config) { overrides, config ->
                    overrides to config
                }
                .collect { (overrides, config) ->
                    val activeOverride = overrides.filter { it.expiresAt > Instant.now() }
                        .maxByOrNull { it.expiresAt.toEpochMilli() }

                    val overrideText = when (activeOverride?.type) {
                        OverrideType.SNOOZE -> "Alarms snoozed"
                        OverrideType.SKIP_TOMORROW -> "Tomorrow's alarms skipped"
                        OverrideType.CUSTOM_TIME -> "Custom alarm time active"
                        null -> null
                    }

                    _uiState.value = HomeUiState(
                        nextAlarmText = "First class: 9:00 AM",
                        overrideText = overrideText
                    )
                }
        }
    }

    fun snooze(minutes: Int) {
        viewModelScope.launch {
            val override = ManualOverride(
                type = OverrideType.SNOOZE,
                value = minutes.toString(),
                expiresAt = Instant.now().plusSeconds(86400)
            )
            alarmRepository.saveOverride(override)
        }
    }

    fun skipTomorrow() {
        viewModelScope.launch {
            val nextDay = LocalDate.now(ZoneId.systemDefault()).plusDays(1)
            val override = ManualOverride(
                type = OverrideType.SKIP_TOMORROW,
                value = nextDay.toString(),
                expiresAt = Instant.now().plusSeconds(86400)
            )
            alarmRepository.saveOverride(override)
        }
    }

    fun setCustomTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val override = ManualOverride(
                type = OverrideType.CUSTOM_TIME,
                value = "$hour:$minute",
                expiresAt = Instant.now().plusSeconds(86400)
            )
            alarmRepository.saveOverride(override)
        }
    }
}
