package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.BedtimeReminder
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object BedtimeComputer {

    fun compute(
        alarmSchedules: List<AlarmSchedule>,
        config: AlarmConfig,
        now: Instant
    ): BedtimeReminder? {
        if (!config.bedtimeEnabled) return null

        // Use the earliest upcoming first-alarm time across the provided schedules.
        val firstAlarmTime = alarmSchedules
            .mapNotNull { it.firstAlarmTime }
            .minOrNull()
            ?: return null

        // targetBedtime = firstAlarmTime - targetSleepDuration (ADR-0004).
        val sleepDurationMinutes = config.targetSleepDurationHours.toLong() * 60L
        val targetBedtime = firstAlarmTime.minus(sleepDurationMinutes, ChronoUnit.MINUTES)

        // bedtimeReminderTime = targetBedtime - bedtimeReminderOffset.
        val reminderTime = targetBedtime
            .minus(config.bedtimeReminderOffsetMinutes.toLong(), ChronoUnit.MINUTES)

        // Edge case: reminder already passed -> skip (ADR-0004).
        if (reminderTime.isBefore(now)) return null

        return BedtimeReminder(
            triggerAt = reminderTime,
            message = "Time to wind down! Your alarm is set for ${formatTime(firstAlarmTime)}"
        )
    }

    private fun formatTime(instant: Instant): String {
        val zoned = instant.atZone(ZoneOffset.UTC)
        val hour = zoned.hour.toString().padStart(2, '0')
        val minute = zoned.minute.toString().padStart(2, '0')
        return "$hour:$minute"
    }
}
