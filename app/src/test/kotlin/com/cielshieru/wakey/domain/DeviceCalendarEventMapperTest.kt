package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.ExternalEvent
import com.cielshieru.wakey.fakes.FakeClock
import com.cielshieru.wakey.fakes.FakeZoneProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DeviceCalendarEventMapperTest {

    private val zone = ZoneId.of("Asia/Singapore")

    @Test
    fun `online calendar event uses online offset rather than campus offset`() {
        val clock = FakeClock().apply { set(Instant.parse("2026-10-19T16:00:00Z")) }
        val zoneProvider = FakeZoneProvider().apply { zone = this@DeviceCalendarEventMapperTest.zone }
        val onlineEvent = ExternalEvent(
            title = "Online class",
            dtstart = LocalDateTime.of(2026, 10, 20, 9, 0),
            dtend = LocalDateTime.of(2026, 10, 20, 10, 0),
            calendarId = "online-calendar"
        )

        val mapped = DeviceCalendarEventMapper.map(
            events = listOf(onlineEvent),
            onlineCalendarId = "online-calendar",
            campusCalendarId = "campus-calendar"
        )
        assertEquals(EventKind.ONLINE, mapped.single().eventType)

        val schedule = AlarmComputer.computeSchedule(
            mapped,
            AlarmConfig(onlineOffsetMinutes = 90, campusOffsetMinutes = 150),
            clock,
            zoneProvider
        ).single()

        assertEquals(
            LocalDateTime.of(2026, 10, 20, 7, 30).atZone(zone).toInstant(),
            schedule.firstAlarmTime
        )
    }

    @Test
    fun `campus calendar event uses campus event kind`() {
        val event = ExternalEvent(
            title = "Campus class",
            dtstart = LocalDateTime.of(2026, 10, 20, 9, 0),
            dtend = LocalDateTime.of(2026, 10, 20, 10, 0),
            calendarId = "campus-calendar"
        )

        val mapped = DeviceCalendarEventMapper.map(
            listOf(event),
            onlineCalendarId = "online-calendar",
            campusCalendarId = "campus-calendar"
        )

        assertEquals(EventKind.CAMPUS, mapped.single().eventType)
    }

    @Test
    fun `PeopleSoft metadata overrides calendar title and preserves location`() {
        val event = ExternalEvent(
            title = "Fallback calendar title",
            dtstart = LocalDateTime.of(2026, 10, 20, 9, 0),
            dtend = LocalDateTime.of(2026, 10, 20, 10, 0),
            calendarId = "campus-calendar",
            location = "Room A",
            peopleSoftCourseCode = "ALT2501",
            peopleSoftTitle = "Lecture 1"
        )

        val mapped = DeviceCalendarEventMapper.map(
            listOf(event),
            onlineCalendarId = "online-calendar",
            campusCalendarId = "campus-calendar"
        ).single()

        assertEquals("ALT2501", mapped.courseCode)
        assertEquals("Lecture 1", mapped.title)
        assertEquals("Lecture 1", mapped.summary)
        assertEquals("Room A", mapped.location)
    }

    @Test
    fun `missing PeopleSoft metadata falls back to calendar title and blank course code`() {
        val event = ExternalEvent(
            title = "Calendar event title",
            dtstart = LocalDateTime.of(2026, 10, 20, 9, 0),
            dtend = LocalDateTime.of(2026, 10, 20, 10, 0),
            calendarId = "campus-calendar",
            location = "Room B"
        )

        val mapped = DeviceCalendarEventMapper.map(
            listOf(event),
            onlineCalendarId = "online-calendar",
            campusCalendarId = "campus-calendar"
        ).single()

        assertEquals("", mapped.courseCode)
        assertEquals("Calendar event title", mapped.title)
        assertEquals("Calendar event title", mapped.summary)
        assertEquals("Room B", mapped.location)
    }

    @Test
    fun `missing calendar IDs do not accidentally classify events as online`() {
        val event = ExternalEvent(
            title = "Unclassified",
            dtstart = LocalDateTime.of(2026, 10, 20, 9, 0),
            dtend = LocalDateTime.of(2026, 10, 20, 10, 0)
        )

        val mapped = DeviceCalendarEventMapper.map(listOf(event), null, null)

        assertEquals(EventKind.CAMPUS, mapped.single().eventType)
    }
}
