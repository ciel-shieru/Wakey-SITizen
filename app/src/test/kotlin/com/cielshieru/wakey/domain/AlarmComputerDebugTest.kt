package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.fakes.FakeClock
import com.cielshieru.wakey.fakes.FakeZoneProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class AlarmComputerDebugTest {

    private val zone = ZoneOffset.UTC
    private val config = AlarmConfig(
        onlineOffsetMinutes = 60,
        campusOffsetMinutes = 90,
        repeatIntervalMinutes = 5,
        maxRepeats = 3
    )

    private fun makeEvent(day: Int, hour: Int, kind: EventKind = EventKind.CAMPUS): TimetableEvent {
        return TimetableEvent(
            courseCode = "CS101",
            title = "Lecture",
            summary = "Lecture",
            dtstart = LocalDateTime.of(2026, 10, day, hour, 0),
            dtend = LocalDateTime.of(2026, 10, day, hour + 1, 0),
            location = "S17",
            eventType = kind
        )
    }

    @Test
    fun `targetDateOverride shifts computed schedules to the override date`() {
        val events = listOf(
            makeEvent(14, 8)
        )
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = zone

        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = AlarmComputer.computeSchedule(events, config, clock, zoneProvider, overrideDate)

        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 10, 14), result[0].day)
        assertEquals(3, result[0].alarms.size)
    }

    @Test
    fun `targetDateOverride with both today and tomorrow events produces two schedules`() {
        val events = listOf(
            makeEvent(14, 8),
            makeEvent(15, 10)
        )
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = zone

        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = AlarmComputer.computeSchedule(events, config, clock, zoneProvider, overrideDate)

        assertEquals(2, result.size)
        assertEquals(LocalDate.of(2026, 10, 14), result[0].day)
        assertEquals(LocalDate.of(2026, 10, 15), result[1].day)
    }

    @Test
    fun `targetDateOverride = null falls back to clock time`() {
        val events = listOf(
            makeEvent(10, 8)
        )
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = zone

        val result = AlarmComputer.computeSchedule(events, config, clock, zoneProvider, null)

        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 10, 10), result[0].day)
    }

    @Test
    fun `targetDateOverride with no timetable events produces empty schedules`() {
        val events = listOf<TimetableEvent>()
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = zone

        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = AlarmComputer.computeSchedule(events, config, clock, zoneProvider, overrideDate)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `targetDateOverride with events on non-override day produces empty schedules`() {
        val events = listOf(
            makeEvent(20, 8)
        )
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = zone

        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = AlarmComputer.computeSchedule(events, config, clock, zoneProvider, overrideDate)

        assertEquals(0, result.size)
    }

    @Test
    fun `targetDateOverride with event on both override day and next day`() {
        val events = listOf(
            makeEvent(14, 8),
            makeEvent(15, 14)
        )
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = zone

        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = AlarmComputer.computeSchedule(events, config, clock, zoneProvider, overrideDate)

        assertEquals(2, result.size)
        assertEquals(LocalDate.of(2026, 10, 14), result[0].day)
        assertEquals(LocalDate.of(2026, 10, 15), result[1].day)
    }

    @Test
    fun `targetDateOverride day with single event produces correct alarm times`() {
        val events = listOf(
            TimetableEvent(
                courseCode = "MA1511",
                title = "Tutorial",
                summary = "Tutorial",
                dtstart = LocalDateTime.of(2026, 10, 14, 9, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 10, 0),
                location = "S17",
                eventType = EventKind.CAMPUS
            )
        )
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = zone

        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = AlarmComputer.computeSchedule(events, config, clock, zoneProvider, overrideDate)

        assertEquals(1, result.size)
        assertEquals(3, result[0].alarms.size)
        // Campus offset is 90 minutes, event at 09:00 → first alarm at 07:30
        val expectedFirstAlarm = java.time.Instant.parse("2026-10-14T07:30:00Z")
        assertEquals(expectedFirstAlarm, result[0].alarms[0].triggerAt)
    }

    @Test
    fun `targetDateOverride with ONLINE event uses online offset`() {
        val events = listOf(
            TimetableEvent(
                courseCode = "CS1010",
                title = "Lecture",
                summary = "Lecture",
                dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 11, 0),
                location = "Online",
                eventType = EventKind.ONLINE
            )
        )
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = zone

        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = AlarmComputer.computeSchedule(events, config, clock, zoneProvider, overrideDate)

        assertEquals(1, result.size)
        // Online offset is 60 minutes, event at 10:00 → first alarm at 09:00
        val expectedFirstAlarm = java.time.Instant.parse("2026-10-14T09:00:00Z")
        assertEquals(expectedFirstAlarm, result[0].firstAlarmTime)
    }
}
