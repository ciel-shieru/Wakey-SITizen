package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.BedtimeReminder
import com.cielshieru.wakey.domain.model.BedtimeReminderMode
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
        val reminderTime = when (config.bedtimeReminderMode) {
            BedtimeReminderMode.Relative -> targetBedtime
                .minus(config.bedtimeReminderOffsetMinutes.toLong(), ChronoUnit.MINUTES)
            BedtimeReminderMode.Absolute -> {
                val bedtimeDate = targetBedtime.atZone(zone).toLocalDate()
                val candidate = bedtimeDate.atTime(config.bedtimeReminderAbsoluteTime)
                    .atZone(zone).toInstant()
                // An absolute reminder must precede this bedtime. For after-midnight
                // bedtimes, the configured evening time belongs to the previous date.
                if (candidate.isBefore(targetBedtime)) candidate
                else bedtimeDate.minusDays(1).atTime(config.bedtimeReminderAbsoluteTime)
                    .atZone(zone).toInstant()
            }
        }

        // The target bedtime remains valid even if its reminder trigger has passed.
        // Delivery scheduling is handled separately by SyncWorker.
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
