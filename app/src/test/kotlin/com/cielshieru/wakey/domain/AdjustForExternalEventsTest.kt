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

        val expectedMinute: Long = 7 * 60 + 15
        val actualMinute = result.firstAlarmTime!!.epochSecond / 60 % 1440
        assertEquals(expectedMinute, actualMinute)
    }

    @Test
    fun `adjust preserves repeat offsets when shifting base alarm`() {
        val day = LocalDate.of(2026, 10, 6)
        val firstAlarm = Instant.parse("2026-10-06T07:30:00Z")
        val schedule = AlarmSchedule(
            day = day,
            alarms = listOf(
                AlarmSchedule.Alarm(0, firstAlarm, 1),
                AlarmSchedule.Alarm(1, firstAlarm.plusSeconds(300), 2),
                AlarmSchedule.Alarm(2, firstAlarm.plusSeconds(600), 3)
            ),
            firstAlarmTime = firstAlarm
        )
        val event = ExternalEvent(
            title = "Briefing",
            dtstart = LocalDateTime.of(2026, 10, 6, 7, 15),
            dtend = LocalDateTime.of(2026, 10, 6, 8, 0)
        )

        val result = AdjustForExternalEvents.adjust(schedule, listOf(event), 720, ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-10-06T07:15:00Z"), result.alarms[0].triggerAt)
        assertEquals(Instant.parse("2026-10-06T07:20:00Z"), result.alarms[1].triggerAt)
        assertEquals(Instant.parse("2026-10-06T07:25:00Z"), result.alarms[2].triggerAt)
    }

    @Test
    fun `adjust shifts alarm for relevant event from previous calendar day`() {
        val day = LocalDate.of(2026, 10, 6)
        val firstAlarm = Instant.parse("2026-10-06T00:15:00Z")
        val schedule = AlarmSchedule(
            day = day,
            alarms = listOf(AlarmSchedule.Alarm(0, firstAlarm, 1)),
            firstAlarmTime = firstAlarm
        )
        val event = ExternalEvent(
            title = "Late briefing",
            dtstart = LocalDateTime.of(2026, 10, 5, 23, 55),
            dtend = LocalDateTime.of(2026, 10, 6, 0, 20)
        )

        val result = AdjustForExternalEvents.adjust(schedule, listOf(event), 30, ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-10-05T23:55:00Z"), result.firstAlarmTime)
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
