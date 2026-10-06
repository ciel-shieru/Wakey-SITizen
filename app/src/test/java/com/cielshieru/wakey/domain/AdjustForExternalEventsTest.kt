package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class AdjustForExternalEventsTest {

    @Test
    fun `adjust returns unchanged when no external events`() {
        val day = LocalDate.of(2026, 10, 6)
        val firstAlarm = Instant.parse("2026-10-06T07:30:00Z")
        val schedule = AlarmSchedule(
            day = day,
            alarms = emptyList(),
            firstAlarmTime = firstAlarm
        )

        val result = AdjustForExternalEvents.adjust(schedule, emptyList(), 30, ZoneOffset.UTC)

        assertEquals(firstAlarm, result.firstAlarmTime)
    }

    @Test
    fun `adjust shifts alarm when external event within buffer`() {
        val day = LocalDate.of(2026, 10, 6)
        val firstAlarm = Instant.parse("2026-10-06T07:30:00Z")
        val schedule = AlarmSchedule(
            day = day,
            alarms = emptyList(),
            firstAlarmTime = firstAlarm
        )

        val externalEvent = ExternalEvent(
            title = "Club Meeting",
            dtstart = LocalDateTime.of(2026, 10, 6, 7, 15),
            dtend = LocalDateTime.of(2026, 10, 6, 8, 0)
        )

        val result = AdjustForExternalEvents.adjust(schedule, listOf(externalEvent), 30, ZoneOffset.UTC)

        val expectedMinute = 7 * 60 + 15
        val actualMinute = result.firstAlarmTime!!.epochSecond / 60 % 1440
        assertEquals(expectedMinute, actualMinute)
    }

    @Test
    fun `adjust does not shift when external event outside buffer`() {
        val day = LocalDate.of(2026, 10, 6)
        val firstAlarm = Instant.parse("2026-10-06T07:30:00Z")
        val schedule = AlarmSchedule(
            day = day,
            alarms = emptyList(),
            firstAlarmTime = firstAlarm
        )

        val externalEvent = ExternalEvent(
            title = "Early Event",
            dtstart = LocalDateTime.of(2026, 10, 6, 6, 0),
            dtend = LocalDateTime.of(2026, 10, 6, 7, 0)
        )

        val result = AdjustForExternalEvents.adjust(schedule, listOf(externalEvent), 30, ZoneOffset.UTC)

        assertEquals(firstAlarm, result.firstAlarmTime)
    }
}
