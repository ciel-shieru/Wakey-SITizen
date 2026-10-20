package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        assertNotNull(result)
        assertEquals(Instant.parse("2026-10-06T23:30:00Z"), result!!.bedtimeAt)
        assertEquals(Instant.parse("2026-10-06T23:00:00Z"), result.triggerAt)
        assertEquals("Bedtime is 11:30 PM. Your alarm is set for 7:30 AM.", result.message)
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
    fun `compute retains upcoming bedtime when reminder trigger already passed`() {
        val day = LocalDate.of(2026, 10, 7)
        val firstAlarm = Instant.parse("2026-10-07T07:30:00Z")
        val schedule = AlarmSchedule(day, emptyList(), firstAlarm)
        val config = AlarmConfig(
            targetSleepDurationHours = 8,
            bedtimeReminderOffsetMinutes = 30,
            bedtimeEnabled = true
        )
        val now = Instant.parse("2026-10-06T23:01:00Z")

        val result = BedtimeComputer.compute(listOf(schedule), config, now)

        assertNotNull(result)
        assertEquals(Instant.parse("2026-10-06T23:00:00Z"), result!!.triggerAt)
        assertEquals(Instant.parse("2026-10-06T23:30:00Z"), result.bedtimeAt)
    }

    @Test
    fun `compute returns null when target bedtime has already passed`() {
        val firstAlarm = Instant.parse("2026-10-07T07:30:00Z")
        val schedule = AlarmSchedule(LocalDate.of(2026, 10, 7), emptyList(), firstAlarm)
        val config = AlarmConfig(
            targetSleepDurationHours = 8,
            bedtimeEnabled = true
        )

        val result = BedtimeComputer.compute(
            listOf(schedule), config, Instant.parse("2026-10-06T23:31:00Z")
        )

        assertNull(result)
    }
}
