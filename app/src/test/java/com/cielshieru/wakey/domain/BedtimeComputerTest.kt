package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BedtimeComputerTest {

    @Test
    fun `compute returns reminder for valid schedule`() {
        val day = LocalDate.of(2026, 10, 7)
        val firstAlarm = Instant.parse("2026-10-07T07:30:00Z")
        val schedule = AlarmSchedule(
            day = day,
            alarms = emptyList(),
            firstAlarmTime = firstAlarm
        )

        val config = AlarmConfig(
            targetSleepDurationHours = 8,
            bedtimeReminderOffsetMinutes = 30,
            bedtimeEnabled = true
        )
        val now = Instant.parse("2026-10-06T12:00:00Z")

        val result = BedtimeComputer.compute(listOf(schedule), config, now)

        assertNull(result)
    }

    @Test
    fun `compute returns null when bedtime disabled`() {
        val day = LocalDate.of(2026, 10, 7)
        val firstAlarm = Instant.parse("2026-10-07T07:30:00Z")
        val schedule = AlarmSchedule(
            day = day,
            alarms = emptyList(),
            firstAlarmTime = firstAlarm
        )

        val config = AlarmConfig(
            targetSleepDurationHours = 8,
            bedtimeReminderOffsetMinutes = 30,
            bedtimeEnabled = false
        )
        val now = Instant.parse("2026-10-06T12:00:00Z")

        val result = BedtimeComputer.compute(listOf(schedule), config, now)

        assertNull(result)
    }

    @Test
    fun `compute returns null for no events`() {
        val config = AlarmConfig(
            targetSleepDurationHours = 8,
            bedtimeReminderOffsetMinutes = 30,
            bedtimeEnabled = true
        )
        val now = Instant.parse("2026-10-06T12:00:00Z")

        val result = BedtimeComputer.compute(emptyList(), config, now)

        assertNull(result)
    }

    @Test
    fun `compute returns null when reminder already passed`() {
        val day = LocalDate.of(2026, 10, 7)
        val firstAlarm = Instant.parse("2026-10-07T07:30:00Z")
        val schedule = AlarmSchedule(
            day = day,
            alarms = emptyList(),
            firstAlarmTime = firstAlarm
        )

        val config = AlarmConfig(
            targetSleepDurationHours = 8,
            bedtimeReminderOffsetMinutes = 30,
            bedtimeEnabled = true
        )
        val now = Instant.parse("2026-10-06T23:00:00Z")

        val result = BedtimeComputer.compute(listOf(schedule), config, now)

        assertNull(result)
    }
}
