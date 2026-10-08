package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.Clock
import com.cielshieru.wakey.domain.port.ZoneProvider
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object AlarmComputer {

    fun computeSchedule(
        events: List<TimetableEvent>,
        config: AlarmConfig,
        clock: Clock,
        zoneProvider: ZoneProvider
    ): List<AlarmSchedule> {
        val zone = zoneProvider.zone
        val today = LocalDate.ofInstant(clock.now(), zone)
        val tomorrow = today.plusDays(1)

        // Group events by the calendar day their DTSTART falls in.
        val eventsByDay = events.groupBy { event ->
            event.dtstart.toLocalDate()
        }

        val schedules = mutableListOf<AlarmSchedule>()

        // Compute for today and tomorrow only.
        for (day in listOf(today, tomorrow)) {
            val dayEvents = eventsByDay[day].orEmpty()
            if (dayEvents.isEmpty()) continue

            // First event of the day (earliest DTSTART).
            val firstEvent = dayEvents.minByOrNull { it.dtstart } ?: continue

            // Smart offset by event kind (ADR-0003).
            val offsetMinutes = when (firstEvent.eventType) {
                EventKind.ONLINE -> config.onlineOffsetMinutes
                EventKind.CAMPUS -> config.campusOffsetMinutes
            }

            val firstAlarmTime = firstEvent.dtstart.minusMinutes(offsetMinutes.toLong())
            val firstAlarmInstant = firstAlarmTime.atZone(zone).toInstant()

            // Repeat pattern: T, T + i*interval for i in 0..maxRepeats-1 (ADR-0003).
            val alarms = (0 until config.maxRepeats).map { index ->
                val triggerAt = firstAlarmInstant.plus(
                    (index * config.repeatIntervalMinutes).toLong(),
                    ChronoUnit.MINUTES
                )
                AlarmSchedule.Alarm(
                    index = index,
                    triggerAt = triggerAt,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(day, index)
                )
            }

            schedules.add(AlarmSchedule(day, alarms, firstAlarmInstant))
        }

        return schedules
    }
}
