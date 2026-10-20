package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.BedtimeReminder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object BedtimeComputer {

    fun compute(
        alarmSchedules: List<AlarmSchedule>,
        config: AlarmConfig,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): BedtimeReminder? {
        if (!config.bedtimeEnabled) return null

        // Ignore first-alarm times that have already passed; the next upcoming alarm
        // determines the next relevant target bedtime.
        val firstAlarmTime = alarmSchedules
            .mapNotNull { it.firstAlarmTime }
            .filter { it.isAfter(now) }
            .minOrNull()
            ?: return null

        // Target bedtime is derived from required sleep only. The reminder offset
        // controls delivery time, not the bedtime displayed to the user.
        val sleepDurationMinutes = config.targetSleepDurationHours.toLong() * 60L
        val targetBedtime = firstAlarmTime.minus(sleepDurationMinutes, ChronoUnit.MINUTES)
        val reminderTime = targetBedtime
            .minus(config.bedtimeReminderOffsetMinutes.toLong(), ChronoUnit.MINUTES)

        // Keep the target bedtime available to the UI even when its reminder trigger
        // has passed. The worker independently decides whether notification delivery
        // can still be scheduled.
        if (targetBedtime.isBefore(now)) return null

        return BedtimeReminder(
            triggerAt = reminderTime,
            bedtimeAt = targetBedtime,
            message = "Bedtime is ${formatTime(targetBedtime, zone)}. " +
                "Your alarm is set for ${formatTime(firstAlarmTime, zone)}."
        )
    }

    private fun formatTime(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a"))
}
