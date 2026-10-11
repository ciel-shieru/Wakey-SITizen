package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.model.OverrideType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object OverrideResolver {

    /**
     * Selects the single override to apply, honouring the priority
     * CUSTOM_TIME > SNOOZE > SKIP_TOMORROW (ADR-0006). Expired overrides
     * are ignored. [zone] is accepted for API symmetry with the other
     * schedulers but is not required for selection.
     */
    fun resolve(
        overrides: List<ManualOverride>,
        now: Instant,
        zone: ZoneId
    ): ManualOverride? {
        val active = overrides.filter { it.expiresAt.isAfter(now) }
        if (active.isEmpty()) return null

        return active.find { it.type == OverrideType.CUSTOM_TIME }
            ?: active.find { it.type == OverrideType.SNOOZE }
            ?: active.find { it.type == OverrideType.SKIP_TOMORROW }
    }

    /**
     * Applies a manual override to a computed schedule (ADR-0006):
     * - SNOOZE: shifts every alarm (and the first-alarm time) later.
     * - SKIP_TOMORROW: suppresses all alarms for the day.
     * - SKIP_ALARM: removes only the alarm matching the override value timestamp.
     * - CUSTOM_TIME: replaces the base time, preserving the repeat interval
     *   and count of the original schedule.
     */
    fun applyOverride(
        override: ManualOverride,
        schedule: AlarmSchedule,
        zone: ZoneId
    ): AlarmSchedule {
        return when (override.type) {
            OverrideType.SNOOZE -> {
                val minutes = parseSnoozeMinutes(override.value)
                schedule.copy(
                    alarms = schedule.alarms.map { alarm ->
                        alarm.copy(triggerAt = alarm.triggerAt.plus(minutes, ChronoUnit.MINUTES))
                    },
                    firstAlarmTime = schedule.firstAlarmTime?.plus(minutes, ChronoUnit.MINUTES)
                )
            }
            OverrideType.SKIP_TOMORROW -> {
                schedule.copy(alarms = emptyList())
            }
            OverrideType.SKIP_ALARM -> {
                val requestCode = override.value.toIntOrNull()
                if (requestCode == null) return schedule
                val wasRemoved = schedule.alarms.any { it.requestCode == requestCode }
                val remaining = schedule.alarms.filter { it.requestCode != requestCode }
                schedule.copy(
                    alarms = remaining,
                    firstAlarmTime = if (wasRemoved && remaining.isNotEmpty()) {
                        remaining.minByOrNull { it.triggerAt.toEpochMilli() }?.triggerAt
                    } else if (wasRemoved) {
                        null
                    } else {
                        schedule.firstAlarmTime
                    }
                )
            }
            OverrideType.CUSTOM_TIME -> {
                val parts = override.value.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull()
                val minute = parts.getOrNull(1)?.toIntOrNull()
                if (hour == null || minute == null) return schedule
                val newInstant = schedule.day.atTime(hour, minute).atZone(zone).toInstant()

                // Preserve the repeat interval from the original schedule.
                val intervalMinutes = if (schedule.alarms.size >= 2) {
                    schedule.alarms[1].triggerAt.until(schedule.alarms[0].triggerAt, ChronoUnit.MINUTES)
                } else {
                    5L
                }

                schedule.copy(
                    alarms = schedule.alarms.mapIndexed { index, alarm ->
                        alarm.copy(
                            triggerAt = newInstant.plus(index.toLong() * intervalMinutes, ChronoUnit.MINUTES)
                        )
                    },
                    firstAlarmTime = newInstant
                )
            }
        }
    }

    fun clearExpired(overrides: List<ManualOverride>, now: Instant): List<ManualOverride> {
        return overrides.filter { it.expiresAt.isAfter(now) }
    }

    /**
     * Accepts both a full duration ("PT30M", per ADR-0006) and a bare
     * minute count ("30"). Falls back to a 30-minute default on any
     * parse failure.
     */
    private fun parseSnoozeMinutes(value: String): Long =
        try {
            Duration.parse(value).toMinutes()
        } catch (e: Exception) {
            value.trim().toLongOrNull() ?: 30L
        }
}
