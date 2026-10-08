package com.cielshieru.wakey.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HomeUiState(
    val nextAlarmText: String = "No alarms set",
    val overrideText: String? = null,
    val isSyncing: Boolean = false
) {
    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
    }

    fun nextAlarmFormatted(zone: ZoneId): String {
        if (nextAlarmText == "No alarms set") return nextAlarmText
        val localTime = nextAlarmText.substringBefore(", ")
        val datePart = nextAlarmText.substringAfter(", ")
        try {
            val date = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
            val parts = localTime.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val instant = date.atTime(hour, minute).atZone(zone).toInstant()
            return TIME_FORMATTER.format(instant.atZone(zone))
        } catch (e: Exception) {
            return nextAlarmText
        }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            alarmRepository.schedules
                .combine(alarmRepository.overrides) { schedules: List<AlarmSchedule>?, overrides: List<ManualOverride> ->
                    Pair(schedules, overrides)
                }
                .combine(alarmRepository.config) { pair: Pair<List<AlarmSchedule>?, List<ManualOverride>>, config: AlarmConfig ->
                    Triple(pair.first, pair.second, config)
                }
                .collect { (schedules, overrides, _) ->
                    val activeOverride = overrides.filter { it.expiresAt > Instant.now() }
                        .maxByOrNull { it.expiresAt.toEpochMilli() }

                    val overrideText = when (activeOverride?.type) {
                        OverrideType.SNOOZE -> "Alarms snoozed"
                        OverrideType.SKIP_TOMORROW -> "Tomorrow's alarms skipped"
                        OverrideType.CUSTOM_TIME -> "Custom alarm time active"
                        null -> null
                    }

                    val nextAlarmText = if (schedules.isNullOrEmpty()) {
                        "No alarms set"
                    } else {
                        val now = Instant.now()
                        val zone = ZoneId.systemDefault()
                        val firstUpcoming = schedules
                            .flatMap { it.alarms }
                            .filter { it.triggerAt.isAfter(now) }
                            .minByOrNull { it.triggerAt.toEpochMilli() }

                        if (firstUpcoming != null) {
                            val alarmDate = firstUpcoming.triggerAt.atZone(zone).toLocalDate()
                            val alarmLocalTime = firstUpcoming.triggerAt.atZone(zone).toLocalTime()
                            val period = if (alarmLocalTime.hour < 12) "AM" else "PM"
                            "Next alarm: ${alarmLocalTime.hour}:${alarmLocalTime.minute.toString().padStart(2, '0')} $period on $alarmDate"
                        } else {
                            "No alarms set"
                        }
                    }

                    _uiState.value = HomeUiState(
                        nextAlarmText = nextAlarmText,
                        overrideText = overrideText
                    )
                }
        }

        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow("sync_work")
                .map { workInfos ->
                    workInfos.any {
                        it.state == WorkInfo.State.RUNNING ||
                                it.state == WorkInfo.State.BLOCKED
                    }
                }
                .collect { syncing ->
                    _uiState.value = _uiState.value.copy(isSyncing = syncing)
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
