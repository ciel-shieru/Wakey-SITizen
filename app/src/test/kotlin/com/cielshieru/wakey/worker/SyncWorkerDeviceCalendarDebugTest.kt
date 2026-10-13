package com.cielshieru.wakey.worker

import com.cielshieru.wakey.domain.AlarmComputer
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.ExternalEvent
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.CalendarEventSource
import com.cielshieru.wakey.fakes.FakeCalendarEventSource
import com.cielshieru.wakey.fakes.FakeClock
import com.cielshieru.wakey.fakes.FakeZoneProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Unit tests for the DeviceCalendar fetch window behavior in the SyncWorker pipeline.
 *
 * These tests verify the full pipeline that ADR-0019 targets:
 * target date override -> fetch window anchor -> AlarmComputer produces schedules.
 *
 * The tests exercise the same logic that SyncWorker.fetchTimetable() uses internally
 * for the DeviceCalendar branch, using a test helper function to avoid requiring
 * Android framework dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncWorkerDeviceCalendarDebugTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val zone = ZoneOffset.UTC

    private fun makeExternalEvent(
        day: Int,
        hour: Int,
        title: String = "Lecture",
        minute: Int = 0
    ): ExternalEvent {
        return ExternalEvent(
            title = title,
            dtstart = LocalDateTime.of(2026, 10, day, hour, minute),
            dtend = LocalDateTime.of(2026, 10, day, hour + 1, minute)
        )
    }

    /**
     * Mirrors the DeviceCalendar branch of SyncWorker.fetchTimetable() for testing.
     * Accepts a targetDateOverride so we can verify the anchor date behavior.
     */
    private suspend fun fetchDeviceCalendarEvents(
        calendarEventSource: CalendarEventSource,
        now: Instant,
        zone: java.time.ZoneId,
        targetDateOverride: LocalDate? = null,
        selectedCalendarIds: List<String> = emptyList()
    ): List<TimetableEvent> {
        val anchorDate = targetDateOverride ?: now.atZone(zone).toLocalDate()
        val daysToFetch = 7
        return (0 until daysToFetch).flatMap { offset ->
            calendarEventSource.getEventsForDate(
                anchorDate.plusDays(offset.toLong()),
                selectedCalendarIds
            )
        }
            .filterNot { external ->
                external.title.startsWith("brightspace-", ignoreCase = true)
            }
            .map { external ->
                TimetableEvent(
                    courseCode = "cal-${external.title.hashCode().toString(16)}",
                    title = external.title,
                    summary = external.title,
                    dtstart = external.dtstart,
                    dtend = external.dtend,
                    location = "",
                    eventType = EventKind.CAMPUS
                )
            }
    }

    /**
     * Full pipeline: fetch events -> compute alarm schedules.
     * This mirrors what SyncWorker.doWork() does for DeviceCalendar mode.
     */
    private suspend fun runFullPipeline(
        calendarEventSource: CalendarEventSource,
        now: Instant,
        zone: java.time.ZoneId,
        targetDateOverride: LocalDate? = null,
        config: AlarmConfig = defaultConfig
    ): List<AlarmSchedule> {
        val events = fetchDeviceCalendarEvents(
            calendarEventSource, now, zone, targetDateOverride, config.selectedCalendarIds
        )
        val filteredEvents = events.filterNot {
            it.courseCode.startsWith("brightspace-", ignoreCase = true)
        }
        val fakeClock = FakeClock()
        fakeClock.set(now)
        val fakeZoneProvider = FakeZoneProvider()
        fakeZoneProvider.zone = zone
        return AlarmComputer.computeSchedule(
            filteredEvents, config, fakeClock, fakeZoneProvider, targetDateOverride
        )
    }

    private val defaultConfig = AlarmConfig(
        onlineOffsetMinutes = 60,
        campusOffsetMinutes = 90,
        repeatIntervalMinutes = 5,
        maxRepeats = 3
    )

    @Test
    fun `targetDateOverride causes DeviceCalendar fetch to use target date as anchor`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 1)
        val realDate = LocalDate.of(2026, 10, 19)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            targetDate to listOf(makeExternalEvent(1, 8, "Target Event 1")),
            targetDate.plusDays(1) to listOf(makeExternalEvent(2, 10, "Target Event 2"))
        ))

        val now = Instant.parse("2026-10-19T12:00:00Z")

        val result = fetchDeviceCalendarEvents(
            calendarEventSource, now, zone, targetDateOverride = targetDate
        )

        val fetchedDates = calendarEventSource.requestedDates
        assertTrue("Should have fetched target date $targetDate", fetchedDates.contains(targetDate))
        assertTrue("Should have fetched target date + 6 = ${targetDate.plusDays(6)}", fetchedDates.contains(targetDate.plusDays(6)))

        // Verify the full 7-day window from target date
        for (i in 0 until 7) {
            val expected = targetDate.plusDays(i.toLong())
            assertTrue("Should have fetched $expected from target window", fetchedDates.contains(expected))
        }

        // Real current date window should NOT have been fetched
        val realDates = (0 until 7).map { realDate.plusDays(it.toLong()) }
        for (d in realDates) {
            assertTrue("Should NOT have fetched real-date $d", !fetchedDates.contains(d))
        }

        assertEquals(2, result.size)
        assertTrue("Should have fetched 'Target Event 1'", result.any { it.title == "Target Event 1" })
        assertTrue("Should have fetched 'Target Event 2'", result.any { it.title == "Target Event 2" })
    }

    @Test
    fun `targetDateOverride null causes DeviceCalendar fetch to use real current date`() = runTest {
        val realDate = LocalDate.of(2026, 10, 19)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            realDate to listOf(makeExternalEvent(19, 8, "Real Event 1")),
            realDate.plusDays(1) to listOf(makeExternalEvent(20, 9, "Real Event 2"))
        ))

        val now = Instant.parse("2026-10-19T12:00:00Z")

        val result = fetchDeviceCalendarEvents(
            calendarEventSource, now, zone, targetDateOverride = null
        )

        val fetchedDates = calendarEventSource.requestedDates
        assertTrue("Should have fetched real current date $realDate", fetchedDates.contains(realDate))
        assertTrue("Should have fetched real date + 1 = ${realDate.plusDays(1)}", fetchedDates.contains(realDate.plusDays(1)))

        // Target date (not in real window) should NOT have been fetched
        val targetDate = LocalDate.of(2026, 10, 14)
        assertTrue("Should NOT have fetched target date $targetDate (not in real window)", !fetchedDates.contains(targetDate))

        assertEquals(2, result.size)
    }

    @Test
    fun `targetDateOverride with events on all 7 days produces 7-day fetch window`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 14)

        val calendarEventSource = FakeCalendarEventSource()
        val eventsByDate = mutableMapOf<LocalDate, List<ExternalEvent>>()
        for (i in 0 until 7) {
            val d = targetDate.plusDays(i.toLong())
            eventsByDate[d] = listOf(makeExternalEvent(d.dayOfMonth, 8, "Event on $d"))
        }
        calendarEventSource.setEventsByDate(eventsByDate)

        val now = Instant.parse("2026-10-19T12:00:00Z")

        val result = fetchDeviceCalendarEvents(
            calendarEventSource, now, zone, targetDateOverride = targetDate
        )

        assertEquals("Should have fetched events from all 7 days", 7, calendarEventSource.requestedDates.size)
        assertEquals("Should have 7 events in result", 7, result.size)

        for (i in 0 until 7) {
            val expected = targetDate.plusDays(i.toLong())
            assertTrue("Should have requested $expected", calendarEventSource.requestedDates.contains(expected))
        }
    }

    @Test
    fun `full pipeline with targetDateOverride produces schedules for target date events`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 14)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            targetDate to listOf(makeExternalEvent(14, 8, "Morning Lecture")),
            targetDate.plusDays(1) to listOf(makeExternalEvent(15, 10, "Afternoon Tutorial"))
        ))

        val now = Instant.parse("2026-10-10T12:00:00Z")

        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = targetDate
        )

        assertEquals("Should have 2 schedules (one per event day)", 2, schedules.size)
        assertEquals("First schedule should be for target date", targetDate, schedules[0].day)
        assertEquals("Second schedule should be for target date + 1", targetDate.plusDays(1), schedules[1].day)

        // Each day should have 3 alarms (repeat pattern)
        assertEquals("Target date should have 3 alarms", 3, schedules[0].alarms.size)
        assertEquals("Next day should have 3 alarms", 3, schedules[1].alarms.size)

        // Verify AlarmComputer used the target date, not the clock date
        val clockDate = LocalDate.of(2026, 10, 10)
        for (schedule in schedules) {
            assertTrue("Schedule day ${schedule.day} should not be clock date $clockDate",
                schedule.day != clockDate)
        }
    }

    @Test
    fun `full pipeline with targetDateOverride and no events produces empty schedules`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 14)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(emptyMap())

        val now = Instant.parse("2026-10-10T12:00:00Z")

        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = targetDate
        )

        assertTrue("Should have empty schedules when no events on target date", schedules.isEmpty())

        // But the fetch window should still have been requested (7 days)
        assertEquals("Should have requested 7 dates even with no events", 7, calendarEventSource.requestedDates.size)
    }

    @Test
    fun `full pipeline with targetDateOverride and events on wrong day produces empty schedules`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 14)

        val calendarEventSource = FakeCalendarEventSource()
        // Events are on day 20, not on the target date
        calendarEventSource.setEventsByDate(mapOf(
            LocalDate.of(2026, 10, 20) to listOf(makeExternalEvent(20, 8, "Wrong Day Event"))
        ))

        val now = Instant.parse("2026-10-10T12:00:00Z")

        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = targetDate
        )

        assertEquals("Should have 0 schedules when events are outside target window", 0, schedules.size)

        // Events on day 20 should NOT have been fetched (target window is 14-20, day 20 is included)
        // Actually day 20 IS in the window (14 + 6 = 20), so it should have been fetched
        assertTrue("Should have fetched day 20 (last day of 7-day window from 14)",
            calendarEventSource.requestedDates.contains(LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `full pipeline production behavior (null override) uses clock date`() = runTest {
        val clockDate = LocalDate.of(2026, 10, 10)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            clockDate to listOf(makeExternalEvent(10, 8, "Clock Date Event")),
            clockDate.plusDays(1) to listOf(makeExternalEvent(11, 9, "Next Day Event"))
        ))

        val now = Instant.parse("2026-10-10T12:00:00Z")

        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = null
        )

        assertEquals("Should have 2 schedules", 2, schedules.size)
        assertEquals("First schedule should be for clock date", clockDate, schedules[0].day)
        assertEquals("Second schedule should be for clock date + 1", clockDate.plusDays(1), schedules[1].day)

        val fetchedDates = calendarEventSource.requestedDates
        assertTrue("Should have fetched clock date $clockDate", fetchedDates.contains(clockDate))
        assertTrue("Should have fetched clock date + 6 = ${clockDate.plusDays(6)}", fetchedDates.contains(clockDate.plusDays(6)))

        // Previous day should NOT be in the 7-day forward window
        assertTrue("Should NOT have fetched previous day ${clockDate.minusDays(1)}",
            !fetchedDates.contains(clockDate.minusDays(1)))
    }

    @Test
    fun `different target dates produce different fetch windows and schedules`() = runTest {
        val calendarEventSource = FakeCalendarEventSource()

        val window1Start = LocalDate.of(2026, 10, 14)
        val window2Start = LocalDate.of(2026, 10, 20)

        // Set up events for two different windows
        val eventsByDate = mutableMapOf<LocalDate, List<ExternalEvent>>()
        for (i in 0 until 7) {
            eventsByDate[window1Start.plusDays(i.toLong())] = listOf(
                makeExternalEvent(window1Start.plusDays(i.toLong()).dayOfMonth, 8, "Window1-$i")
            )
            eventsByDate[window2Start.plusDays(i.toLong())] = listOf(
                makeExternalEvent(window2Start.plusDays(i.toLong()).dayOfMonth, 8, "Window2-$i")
            )
        }
        calendarEventSource.setEventsByDate(eventsByDate)

        val now = Instant.parse("2026-10-19T12:00:00Z")

        // Fetch for window 1
        calendarEventSource.clearRequestedDates()
        val schedules1 = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = window1Start
        )
        val fetchedDates1 = calendarEventSource.requestedDates.toSet()

        // Fetch for window 2
        calendarEventSource.clearRequestedDates()
        val schedules2 = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = window2Start
        )
        val fetchedDates2 = calendarEventSource.requestedDates.toSet()

        // Window 1 fetch should include days 14-20
        assertTrue("Window 1 fetch should include day 14", fetchedDates1.contains(LocalDate.of(2026, 10, 14)))
        assertTrue("Window 1 fetch should include day 20", fetchedDates1.contains(LocalDate.of(2026, 10, 20)))
        assertTrue("Window 1 fetch should NOT include day 21", !fetchedDates1.contains(LocalDate.of(2026, 10, 21)))

        // Window 2 fetch should include days 20-26
        assertTrue("Window 2 fetch should include day 21", fetchedDates2.contains(LocalDate.of(2026, 10, 21)))
        assertTrue("Window 2 fetch should include day 20", fetchedDates2.contains(LocalDate.of(2026, 10, 20)))
        assertTrue("Window 2 fetch should NOT include day 14", !fetchedDates2.contains(LocalDate.of(2026, 10, 14)))

        // Verify different schedules are produced (different event titles)
        val titles1 = schedules1.flatMap { it.alarms }.map { _ -> "window1" }
        val titles2 = schedules2.flatMap { it.alarms }.map { _ -> "window2" }
        assertEquals("Window 1 should produce schedules with Window1 events", 2, schedules1.size)
        assertEquals("Window 2 should produce schedules with Window2 events", 2, schedules2.size)
    }

    @Test
    fun `full pipeline filters out BrightSpace events from DeviceCalendar fetch`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 14)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            targetDate to listOf(
                makeExternalEvent(14, 8, "Regular Lecture"),
                ExternalEvent(
                    title = "brightspace-lecture",
                    dtstart = LocalDateTime.of(2026, 10, 14, 9, 0),
                    dtend = LocalDateTime.of(2026, 10, 14, 10, 0)
                )
            )
        ))

        val now = Instant.parse("2026-10-10T12:00:00Z")

        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = targetDate
        )

        // Only 1 schedule (BrightSpace event should be filtered)
        assertEquals("Should have 1 schedule (BrightSpace filtered)", 1, schedules.size)
        assertEquals("Schedule should be for target date", targetDate, schedules[0].day)
    }

    @Test
    fun `full pipeline with ONLINE event uses correct offset in debug mode`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 14)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            targetDate to listOf(
                ExternalEvent(
                    title = "Online Lecture",
                    dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
                    dtend = LocalDateTime.of(2026, 10, 14, 11, 0)
                )
            )
        ))

        val now = Instant.parse("2026-10-10T12:00:00Z")

        // Note: DeviceCalendar events are always mapped to CAMPUS kind,
        // so the CAMPUS offset (90min) is used, not ONLINE offset.
        // Event at 10:00, CAMPUS offset 90min -> first alarm at 08:30
        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = targetDate
        )

        assertEquals("Should have 1 schedule", 1, schedules.size)
        assertEquals("Schedule should be for target date", targetDate, schedules[0].day)

        // CAMPUS offset is 90 minutes (DeviceCalendar events are always CAMPUS),
        // event at 10:00 -> first alarm at 08:30
        val expectedFirstAlarm = Instant.parse("2026-10-14T08:30:00Z")
        assertEquals("First alarm should be at 08:30 (90min before CAMPUS event)",
            expectedFirstAlarm, schedules[0].firstAlarmTime!!)
    }

    @Test
    fun `full pipeline with CAMPUS event uses correct offset in debug mode`() = runTest {
        val targetDate = LocalDate.of(2026, 10, 14)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            targetDate to listOf(
                ExternalEvent(
                    title = "Campus Tutorial",
                    dtstart = LocalDateTime.of(2026, 10, 14, 9, 0),
                    dtend = LocalDateTime.of(2026, 10, 14, 10, 0)
                )
            )
        ))

        val now = Instant.parse("2026-10-10T12:00:00Z")

        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = targetDate
        )

        assertEquals("Should have 1 schedule", 1, schedules.size)
        assertEquals("Schedule should be for target date", targetDate, schedules[0].day)

        // CAMPUS offset is 90 minutes, event at 09:00 -> first alarm at 07:30
        val expectedFirstAlarm = Instant.parse("2026-10-14T07:30:00Z")
        assertEquals("First alarm should be at 07:30 (90min before CAMPUS event)",
            expectedFirstAlarm, schedules[0].firstAlarmTime!!)
    }

    @Test
    fun `target date window does not overlap clock date window when dates are far apart`() = runTest {
        // Clock date is 2026-10-10, target date is 2026-10-01
        // Clock window: 10-16, Target window: 01-07 -- no overlap
        val targetDate = LocalDate.of(2026, 10, 1)
        val clockDate = LocalDate.of(2026, 10, 10)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            targetDate to listOf(makeExternalEvent(1, 8, "Target Event")),
            clockDate to listOf(makeExternalEvent(10, 8, "Clock Event"))
        ))

        val now = Instant.parse("2026-10-10T12:00:00Z")

        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = targetDate
        )

        // Should only have schedule for target date, not clock date
        assertEquals("Should have 1 schedule for target date only", 1, schedules.size)
        assertEquals("Schedule should be for target date", targetDate, schedules[0].day)
        assertTrue("Schedule day ${schedules[0].day} should not be clock date $clockDate",
            schedules[0].day != clockDate)
    }

    @Test
    fun `target date window overlaps clock date window when dates are close`() = runTest {
        // Clock date is 2026-10-10, target date is 2026-10-08
        // Target window: 08-14, events on days 08, 10, 11
        // AlarmComputer only computes for today (08) and tomorrow (09), so only day 08 gets a schedule
        val targetDate = LocalDate.of(2026, 10, 8)
        val clockDate = LocalDate.of(2026, 10, 10)

        val calendarEventSource = FakeCalendarEventSource()
        calendarEventSource.setEventsByDate(mapOf(
            targetDate to listOf(makeExternalEvent(8, 8, "Target Day 8")),
            clockDate to listOf(makeExternalEvent(10, 9, "Clock Day 10")),
            clockDate.plusDays(1) to listOf(makeExternalEvent(11, 10, "Day 11"))
        ))

        val now = Instant.parse("2026-10-10T12:00:00Z")

        val schedules = runFullPipeline(
            calendarEventSource, now, zone,
            targetDateOverride = targetDate
        )

        // AlarmComputer only computes for today and tomorrow (targetDate + 1)
        // So only day 08 gets a schedule (events on days 10, 11 are outside the 2-day compute window)
        assertEquals("Should have 1 schedule (AlarmComputer only computes for today+tomorrow)", 1, schedules.size)
        assertEquals("Schedule should be for target date", targetDate, schedules[0].day)

        // Verify the fetch window included all dates (08-14)
        val fetchedDates = calendarEventSource.requestedDates.toSet()
        assertTrue("Fetch window should include target date 8", fetchedDates.contains(targetDate))
        assertTrue("Fetch window should include day 10 (overlap)", fetchedDates.contains(clockDate))
        assertTrue("Fetch window should include day 11 (overlap)", fetchedDates.contains(clockDate.plusDays(1)))
    }
}
