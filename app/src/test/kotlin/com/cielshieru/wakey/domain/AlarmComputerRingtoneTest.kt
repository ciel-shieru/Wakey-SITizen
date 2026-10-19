package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.*
import com.cielshieru.wakey.fakes.FakeClock
import com.cielshieru.wakey.fakes.FakeZoneProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class AlarmComputerRingtoneTest {

    private val clock = FakeClock()
    private val zoneProvider = FakeZoneProvider()
    private val baseConfig = AlarmConfig(
        onlineOffsetMinutes = 60,
        campusOffsetMinutes = 90,
        repeatIntervalMinutes = 5,
        maxRepeats = 3
    )

    @Before
    fun setUp() {
        zoneProvider.zone = ZoneOffset.UTC
        clock.set(java.time.Instant.parse("2026-10-05T00:00:00Z"))
    }

    @Test
    fun `computeSchedule assigns null ringtoneUri when playlist is empty`() {
        val config = baseConfig.copy(ringtonePlaylist = RingtonePlaylist.DEFAULT)
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

        assertEquals(1, schedules.size)
        for (alarm in schedules[0].alarms) {
            assertEquals(null, alarm.ringtoneUri)
        }
    }

    @Test
    fun `computeSchedule assigns ringtones from playlist in linear mode`() {
        val uris = listOf("content://ringtone1", "content://ringtone2", "content://ringtone3")
        val config = baseConfig.copy(
            ringtonePlaylist = RingtonePlaylist(uris, shuffleEnabled = false)
        )
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

        assertEquals(1, schedules.size)
        assertEquals(3, schedules[0].alarms.size)
        assertEquals("content://ringtone1", schedules[0].alarms[0].ringtoneUri)
        assertEquals("content://ringtone2", schedules[0].alarms[1].ringtoneUri)
        assertEquals("content://ringtone3", schedules[0].alarms[2].ringtoneUri)
    }

    @Test
    fun `computeSchedule cycles ringtones when more alarms than ringtones`() {
        val uris = listOf("content://a", "content://b")
        val config = baseConfig.copy(
            maxRepeats = 4,
            ringtonePlaylist = RingtonePlaylist(uris, shuffleEnabled = false)
        )
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

        assertEquals(4, schedules[0].alarms.size)
        assertEquals("content://a", schedules[0].alarms[0].ringtoneUri)
        assertEquals("content://b", schedules[0].alarms[1].ringtoneUri)
        assertEquals("content://a", schedules[0].alarms[2].ringtoneUri)
        assertEquals("content://b", schedules[0].alarms[3].ringtoneUri)
    }

    @Test
    fun `computeSchedule assigns ringtones deterministically in shuffle mode`() {
        val uris = listOf("content://x", "content://y", "content://z")
        val config = baseConfig.copy(
            ringtonePlaylist = RingtonePlaylist(uris, shuffleEnabled = true)
        )
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

        val schedules1 = AlarmComputer.computeSchedule(events, config, clock, zoneProvider)
        val schedules2 = AlarmComputer.computeSchedule(events, config, clock, zoneProvider)

        for (i in schedules1[0].alarms.indices) {
            assertEquals(
                "Ringtone for index $i should be deterministic",
                schedules1[0].alarms[i].ringtoneUri,
                schedules2[0].alarms[i].ringtoneUri
            )
        }
    }

    @Test
    fun `computeSchedule with single ringtone assigns it to all alarms`() {
        val config = baseConfig.copy(
            maxRepeats = 5,
            ringtonePlaylist = RingtonePlaylist(listOf("content://only"), shuffleEnabled = false)
        )
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

        for (alarm in schedules[0].alarms) {
            assertEquals("content://only", alarm.ringtoneUri)
        }
    }

    @Test
    fun `computeSchedule with no events has no alarms with ringtones`() {
        val config = baseConfig.copy(
            ringtonePlaylist = RingtonePlaylist(listOf("content://a"), shuffleEnabled = false)
        )

        val schedules = AlarmComputer.computeSchedule(emptyList(), config, clock, zoneProvider)

        assertEquals(0, schedules.size)
    }

    @Test
    fun `computeSchedule assigns ringtones for multiple days`() {
        val uris = listOf("content://morning", "content://afternoon")
        val config = baseConfig.copy(
            ringtonePlaylist = RingtonePlaylist(uris, shuffleEnabled = false)
        )
        val events = listOf(
            TimetableEvent(
                courseCode = "CS101",
                title = "Morning Class",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 5, 9, 0),
                dtend = LocalDateTime.of(2026, 10, 5, 10, 0),
                location = "W1-05-07",
                eventType = EventKind.CAMPUS
            ),
            TimetableEvent(
                courseCode = "CS102",
                title = "Tomorrow Class",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 6, 14, 0),
                dtend = LocalDateTime.of(2026, 10, 6, 15, 0),
                location = "W1-05-07",
                eventType = EventKind.CAMPUS
            )
        )

        val schedules = AlarmComputer.computeSchedule(events, config, clock, zoneProvider)

        assertEquals(2, schedules.size)
        for (schedule in schedules) {
            assertEquals(3, schedule.alarms.size)
            assertEquals("content://morning", schedule.alarms[0].ringtoneUri)
            assertEquals("content://afternoon", schedule.alarms[1].ringtoneUri)
            assertEquals("content://morning", schedule.alarms[2].ringtoneUri)
        }
    }
}
