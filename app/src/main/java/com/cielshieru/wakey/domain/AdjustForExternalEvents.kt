package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.ExternalEvent
import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object AdjustForExternalEvents {

    /**
     * Shifts the first alarm earlier when an external event starts within
     * [bufferMinutes] of the current first-alarm time (ADR-0005). Among the
     * qualifying events the latest DTSTART wins; the whole alarm set is
     * re-anchored to that event's start while keeping the repeat offsets.
     */
    fun adjust(
        schedule: AlarmSchedule,
        externalEvents: List<ExternalEvent>,
        bufferMinutes: Int,
        zone: ZoneId
    ): AlarmSchedule {
        val firstAlarm = schedule.firstAlarmTime ?: return schedule
        if (externalEvents.isEmpty()) return schedule

        val alarmLocalTime = firstAlarm.atZone(zone).toLocalDateTime()

        // Only events that start before the current first alarm are relevant.
        val relevantEvents = externalEvents.filter { it.dtstart.isBefore(alarmLocalTime) }
        if (relevantEvents.isEmpty()) return schedule

        // The event with the latest DTSTART is the most constraining.
        val latestEvent = relevantEvents.maxByOrNull { it.dtstart } ?: return schedule

        // The gap must be within the configured buffer to apply.
        val gapMinutes = Duration.between(latestEvent.dtstart, alarmLocalTime).toMinutes()
        if (gapMinutes > bufferMinutes) return schedule

        // Re-anchor the alarm set to the external event's start.
        val newAlarmTime = latestEvent.dtstart.atZone(zone).toInstant()
        return schedule.copy(
            alarms = schedule.alarms.map { alarm ->
                val offsetFromFirst = firstAlarm.until(alarm.triggerAt, ChronoUnit.MINUTES)
                alarm.copy(triggerAt = newAlarmTime.plus(offsetFromFirst, ChronoUnit.MINUTES))
            },
            firstAlarmTime = newAlarmTime
        )
    }
}
