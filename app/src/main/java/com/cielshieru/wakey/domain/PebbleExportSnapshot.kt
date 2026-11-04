package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import java.time.Instant

/** Minimal view of alarm/bedtime state for the paired Pebble companion. */
data class PebbleExportSnapshot(
    val nextAlarmAt: Instant?,
    val bedtimeAt: Instant?,
    val calculatedAt: Instant,
    val updatedAt: Instant,
)

object PebbleExportSnapshotFactory {
    fun create(
        schedules: List<AlarmSchedule>,
        config: AlarmConfig,
        now: Instant,
        updatedAt: Instant,
    ): PebbleExportSnapshot {
        val nextAlarmAt = schedules
            .flatMap { schedule ->
                buildList {
                    schedule.firstAlarmTime?.let { add(it) }
                    schedule.alarms.forEach { add(it.triggerAt) }
                }
            }
            .filter { it.isAfter(now) }
            .minOrNull()

        if (nextAlarmAt == null) {
            return PebbleExportSnapshot(
                nextAlarmAt = null,
                bedtimeAt = null,
                calculatedAt = now,
                updatedAt = updatedAt,
            )
        }

        val nextAlarm = nextAlarmAt
        val computedBedtime = BedtimeComputer
            .compute(schedules, config, now)
            ?.bedtimeAt
            ?.takeIf { bedtime -> bedtime.isAfter(now) && bedtime.isBefore(nextAlarm) }

        return PebbleExportSnapshot(
            nextAlarmAt = nextAlarmAt,
            bedtimeAt = computedBedtime,
            calculatedAt = now,
            updatedAt = updatedAt,
        )
    }
}
