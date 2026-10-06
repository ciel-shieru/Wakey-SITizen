package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.*
import com.cielshieru.wakey.fakes.FakeClock
import com.cielshieru.wakey.fakes.FakeZoneProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class AlarmComputerTest {

    private val clock = FakeClock()
    private val zoneProvider = FakeZoneProvider()
    private val config = AlarmConfig(
        onlineOffsetMinutes = 60,
        campusOffsetMinutes = 90,
        repeatIntervalMinutes = 5,
        maxRepeats = 3
    )

    @Before
    fun setUp() {
        zoneProvider.zone = ZoneOffset.UTC
        clock.set(java.time.Instant.now())
    }

    @Test
    fun `computeSchedule returns single alarm for single event`() {
        val events = listOf(
            TimetableEvent(
                courseCode = "CS101",
                title = "Intro to CS",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 6, 9, 0),
                dtend = LocalDateTime.of(2026, 10, 6, 10, 0),
                location = "W1-05-07",
                eventType = EventKind.CAMPUS
            )
        )

        val schedules = AlarmComputer.computeSchedule(events, config, clock, zoneProvider)

        assertEquals(1, schedules.size)
        assertEquals(3, schedules[0].alarms.size)
        assertEquals(7 * 60 + 30, schedules[0].firstAlarmTime!!.epochSecond / 60 % 1440)
    }

    @Test
    fun `computeSchedule applies online offset`() {
        val events = listOf(
            TimetableEvent(
                courseCode = "CS101",
                title = "Online Lecture",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 6, 9, 0),
                dtend = LocalDateTime.of(2026, 10, 6, 10, 0),
                location = "Online",
                eventType = EventKind.ONLINE
            )
        )

        val schedules = AlarmComputer.computeSchedule(events, config, clock, zoneProvider)

        assertEquals(1, schedules.size)
        val expectedMinute: Long = 8 * 60
        val actualMinute = schedules[0].firstAlarmTime!!.epochSecond / 60 % 1440
        assertEquals(expectedMinute, actualMinute)
    }

    @Test
    fun `computeSchedule returns empty for no events`() {
        val schedules = AlarmComputer.computeSchedule(emptyList(), config, clock, zoneProvider)
        assertEquals(0, schedules.size)
    }

    @Test
    fun `computeSchedule selects first event of day`() {
        val events = listOf(
            TimetableEvent(
                courseCode = "CS101",
                title = "First Class",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 6, 8, 0),
                dtend = LocalDateTime.of(2026, 10, 6, 9, 0),
                location = "W1-05-07",
                eventType = EventKind.CAMPUS
            ),
            TimetableEvent(
                courseCode = "CS102",
                title = "Second Class",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 6, 14, 0),
                dtend = LocalDateTime.of(2026, 10, 6, 15, 0),
                location = "W1-05-07",
                eventType = EventKind.CAMPUS
            )
        )

        val schedules = AlarmComputer.computeSchedule(events, config, clock, zoneProvider)

        assertEquals(1, schedules.size)
        val expectedMinute: Long = 6 * 60 + 30
        val actualMinute = schedules[0].firstAlarmTime!!.epochSecond / 60 % 1440
        assertEquals(expectedMinute, actualMinute)
    }

    @Test
    fun `computeSchedule generates repeat alarms`() {
        val events = listOf(
            TimetableEvent(
                courseCode = "CS101",
                title = "Class",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 6, 9, 0),
                dtend = LocalDateTime.of(2026, 10, 6, 10, 0),
                location = "W1-05-07",
                eventType = EventKind.CAMPUS
            )
        )

        val schedules = AlarmComputer.computeSchedule(events, config, clock, zoneProvider)

        assertEquals(3, schedules[0].alarms.size)
        val day = LocalDate.of(2026, 10, 6)
        assertEquals(day.hashCode() * 1000 + 0, schedules[0].alarms[0].requestCode)
        assertEquals(day.hashCode() * 1000 + 1, schedules[0].alarms[1].requestCode)
        assertEquals(day.hashCode() * 1000 + 2, schedules[0].alarms[2].requestCode)
    }
}
