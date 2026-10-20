package com.cielshieru.wakey.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.domain.BedtimeComputer
import com.cielshieru.wakey.domain.DebugLogger
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.DebugConfig
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.model.OverrideType
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.AlarmRepository
import com.cielshieru.wakey.domain.port.Clock
import com.cielshieru.wakey.domain.port.SyncStatus
import com.cielshieru.wakey.domain.port.TimetableRepository
import com.cielshieru.wakey.domain.port.ZoneProvider
import com.cielshieru.wakey.usecase.SyncOperations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import javax.inject.Inject

private data class quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

data class HomeUiState(
    val nextAlarmText: String = "No alarms set",
    val bedtimeText: String? = null,
    val nextEvent: NextEventUiState? = null,
    val overrideText: String? = null,
    val isSyncing: Boolean = false,
    val staleDataWarning: String? = null,
    val scheduledAlarms: List<ScheduledAlarm> = emptyList(),
    val currentDay: LocalDate = LocalDate.now()
) {
    companion object {
        internal val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
    }

    data class ScheduledAlarm(
        val requestCode: Int,
        val formattedTime: String,
        val day: LocalDate = LocalDate.MIN,
        val ringtoneUri: String?,
        val isUpcoming: Boolean
    )

    data class NextEventUiState(
        val courseCode: String,
        val title: String,
        val dtstartFormatted: String,
        val location: String
    )

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
    private val syncStatus: SyncStatus,
    private val clock: Clock,
    private val syncOperations: SyncOperations,
    private val debugLogger: DebugLogger,
    private val timetableRepository: TimetableRepository,
    private val zoneProvider: ZoneProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private fun computeNextEvent(
        events: List<TimetableEvent>,
        now: Instant,
        zone: ZoneId,
        today: LocalDate,
        tomorrow: LocalDate
    ): HomeUiState.NextEventUiState? {
        val nowLocal = now.atZone(zone).toLocalDateTime()
        val todayEvents = events.filter { it.dtstart.toLocalDate() == today }
        val tomorrowEvents = events.filter { it.dtstart.toLocalDate() == tomorrow }
        val todayFirstEvent = todayEvents.minByOrNull { it.dtstart }
        val tomorrowFirstEvent = tomorrowEvents.minByOrNull { it.dtstart }

        val selectedEvent: TimetableEvent? = if (todayFirstEvent != null && todayFirstEvent.dtstart.isAfter(nowLocal)) {
            todayFirstEvent
        } else {
            tomorrowFirstEvent
        }

        return selectedEvent?.let { event ->
            HomeUiState.NextEventUiState(
                courseCode = event.courseCode,
                title = event.title,
                dtstartFormatted = event.dtstart.format(HomeUiState.TIME_FORMATTER),
                location = event.location
            )
        }
    }

    init {
        viewModelScope.launch {
            alarmRepository.schedules
                .combine(alarmRepository.overrides) { schedules: List<AlarmSchedule>?, overrides: List<ManualOverride> ->
                    Pair(schedules, overrides)
                }
                .combine(alarmRepository.config) { pair: Pair<List<AlarmSchedule>?, List<ManualOverride>>, config: AlarmConfig ->
                    Triple(pair.first, pair.second, config)
                }
                .combine(alarmRepository.debugConfig) { triple: Triple<List<AlarmSchedule>?, List<ManualOverride>, AlarmConfig>, debugConfig: DebugConfig ->
                    quadruple(triple.first, triple.second, triple.third, debugConfig)
                }
                .combine(timetableRepository.eventsFlow) { quad: quadruple<List<AlarmSchedule>?, List<ManualOverride>, AlarmConfig, DebugConfig>, events: List<TimetableEvent> ->
                    quad to events
                }
                .collect { (quad, events) ->
                    val (schedules, overrides, config, debugConfig) = quad

                    // Log full config when UI becomes visible (foreground app open)
                    if (debugConfig.debugModeEnabled) {
                        debugLogger.logFullConfig(config, debugConfig)
                    }

                    val activeOverride = overrides.filter { it.expiresAt > Instant.now() }
                        .maxByOrNull { it.expiresAt.toEpochMilli() }

                    val overrideText = when (activeOverride?.type) {
                        OverrideType.SNOOZE -> {
                            val minutes = activeOverride.value.trim().toLongOrNull() ?: 30L
                            "Alarms snoozed $minutes min"
                        }
                        OverrideType.SKIP_TOMORROW -> "Tomorrow's alarms skipped"
                        OverrideType.SKIP_ALARM -> "One alarm skipped"
                        OverrideType.CUSTOM_TIME -> {
                            val parts = activeOverride.value.split(":")
                            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
                            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            "Custom alarm time at ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                        }
                        null -> null
                    }

                    val now = if (debugConfig.debugModeEnabled && debugConfig.targetDateMillis != null) {
                        val targetDateLocal = Instant.ofEpochMilli(debugConfig.targetDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        targetDateLocal.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    } else {
                        clock.now()
                    }

                    val bedtimeNow = clock.now()

                    val zone = zoneProvider.zone
                    val nowLocal = now.atZone(zone).toLocalDateTime()
                    val today = nowLocal.toLocalDate()
                    val tomorrow = today.plusDays(1)

                    val nextEvent = computeNextEvent(events, now, zone, today, tomorrow)

                    val nextAlarmText = if (schedules.isNullOrEmpty()) {
                        "No alarms set"
                    } else {
                        val alarmZone = ZoneId.systemDefault()
                        val firstUpcoming = schedules
                            .flatMap { it.alarms }
                            .filter { it.triggerAt.isAfter(now) }
                            .minByOrNull { it.triggerAt.toEpochMilli() }

                        if (firstUpcoming != null) {
                            val alarmDate = firstUpcoming.triggerAt.atZone(alarmZone).toLocalDate()
                            val alarmLocalTime = firstUpcoming.triggerAt.atZone(alarmZone).toLocalTime()
                            val period = if (alarmLocalTime.hour < 12) "AM" else "PM"
                            "Next alarm: ${alarmLocalTime.hour}:${alarmLocalTime.minute.toString().padStart(2, '0')} $period on $alarmDate"
                        } else {
                            "No alarms set"
                        }
                    }

                    val bedtimeText = if (!schedules.isNullOrEmpty() && config.bedtimeEnabled) {
                        BedtimeComputer.compute(schedules, config, bedtimeNow)?.let { reminder ->
                            "Bedtime: ${reminder.triggerAt.atZone(ZoneId.systemDefault()).format(HomeUiState.TIME_FORMATTER)}"
                        }
                    } else {
                        null
                    }

                    val scheduledAlarms = if (schedules.isNullOrEmpty()) {
                        emptyList()
                    } else {
                        val alarmZone = ZoneId.systemDefault()
                        schedules
                            .flatMap { schedule ->
                                schedule.alarms.map { alarm ->
                                    val triggerLocal = alarm.triggerAt.atZone(alarmZone)
                                    val dayName = triggerLocal.dayOfWeek.getDisplayName(
                                        TextStyle.SHORT_STANDALONE,
                                        java.util.Locale.getDefault()
                                    )
                                    val monthDay = triggerLocal.month.getDisplayName(
                                        TextStyle.SHORT_STANDALONE,
                                        java.util.Locale.getDefault()
                                    )
                                    val hour12 = if (triggerLocal.hour == 0) 12
                                    else if (triggerLocal.hour > 12) triggerLocal.hour - 12
                                    else triggerLocal.hour
                                    val period = if (triggerLocal.hour < 12) "AM" else "PM"
                                    val minuteStr = triggerLocal.minute.toString().padStart(2, '0')
                                    val formattedTime = "$dayName, $monthDay ${triggerLocal.dayOfMonth} at $hour12:$minuteStr $period"

                                    HomeUiState.ScheduledAlarm(
                                        requestCode = alarm.requestCode,
                                        formattedTime = formattedTime,
                                        day = schedule.day,
                                        ringtoneUri = alarm.ringtoneUri,
                                        isUpcoming = alarm.triggerAt.isAfter(now)
                                    )
                                }
                            }
                            .sortedBy { it.requestCode }
                    }

                    _uiState.value = HomeUiState(
                        nextAlarmText = nextAlarmText,
                        bedtimeText = bedtimeText,
                        nextEvent = nextEvent,
                        overrideText = overrideText,
                        scheduledAlarms = scheduledAlarms,
                        currentDay = clock.now().atZone(zone).toLocalDate()
                    )
                }
        }

        viewModelScope.launch {
            syncStatus.isSyncing
                .collect { syncing ->
                    _uiState.value = _uiState.value.copy(isSyncing = syncing)
                }
        }

        viewModelScope.launch {
            alarmRepository.lastSyncTime.collect { lastSync ->
                val warning = lastSync?.let {
                    val hoursAgo = (Instant.now().toEpochMilli() - it) / 3600000.0
                    if (hoursAgo > 24) "Data may be stale (last sync ${hoursAgo.toInt()}h ago)" else null
                }
                _uiState.value = _uiState.value.copy(staleDataWarning = warning)
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

    fun resetOverrides() {
        val today = clock.now().atZone(zoneProvider.zone).toLocalDate()
        viewModelScope.launch {
            alarmRepository.clearAllOverrides()
            alarmRepository.clearDismissalForDay(today)
            syncOperations.syncImmediately()
        }
    }

    fun dismissRemainingAlarmsForToday() {
        val today = clock.now().atZone(zoneProvider.zone).toLocalDate()
        viewModelScope.launch {
            alarmRepository.dismissAlarmsForDay(today)
        }
    }

    fun skipAlarm(scheduledAlarm: HomeUiState.ScheduledAlarm) {
        viewModelScope.launch {
            val override = ManualOverride(
                type = OverrideType.SKIP_ALARM,
                value = scheduledAlarm.requestCode.toString(),
                expiresAt = Instant.now().plusSeconds(86400)
            )
            alarmRepository.saveOverride(override)
            alarmRepository.cancelAlarm(scheduledAlarm.requestCode)
            syncOperations.syncImmediately()
        }
    }
}
