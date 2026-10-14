package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.clearDeviceCalendarEvents
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.deviceCalendarEventsFlow
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.saveDeviceCalendarEvents
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class DeviceCalendarTimetableStoreTest {

    @Test
    fun `save and load device calendar events round-trip`() = runTest {
        val testFile = File("/tmp/test_device_calendar_roundtrip_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val events = listOf(
            TimetableEvent(
                courseCode = "CS1010",
                title = "Programming Methodology",
                summary = "Lecture",
                dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 11, 0),
                location = "LT19",
                eventType = EventKind.CAMPUS
            ),
            TimetableEvent(
                courseCode = "MA1521",
                title = "Mathematical Analysis I",
                summary = "Tutorial",
                dtstart = LocalDateTime.of(2026, 10, 14, 14, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 15, 0),
                location = "T-MASTER1",
                eventType = EventKind.CAMPUS
            )
        )

        dataStore.saveDeviceCalendarEvents(events)

        val loaded = dataStore.data.deviceCalendarEventsFlow().first()

        assertEquals(2, loaded!!.size)
        assertEquals(events[0].courseCode, loaded[0].courseCode)
        assertEquals(events[0].title, loaded[0].title)
        assertEquals(events[0].dtstart, loaded[0].dtstart)
        assertEquals(events[0].dtend, loaded[0].dtend)
        assertEquals(events[0].location, loaded[0].location)
        assertEquals(events[0].eventType, loaded[0].eventType)
        assertEquals(events[1].courseCode, loaded[1].courseCode)
        assertEquals(events[1].title, loaded[1].title)

        testFile.delete()
    }

    @Test
    fun `save empty list and load returns empty list`() = runTest {
        val testFile = File("/tmp/test_device_calendar_empty_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        dataStore.saveDeviceCalendarEvents(emptyList())

        val loaded = dataStore.data.deviceCalendarEventsFlow().first()

        assertTrue("Empty list should be stored and loaded as empty", loaded!!.isEmpty())

        testFile.delete()
    }

    @Test
    fun `load before save returns null`() = runTest {
        val testFile = File("/tmp/test_device_calendar_null_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val loaded = dataStore.data.deviceCalendarEventsFlow().first()

        assertTrue("Should return null when nothing has been saved", loaded == null)

        testFile.delete()
    }

    @Test
    fun `clear removes all device calendar events`() = runTest {
        val testFile = File("/tmp/test_device_calendar_clear_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val events = listOf(
            TimetableEvent(
                courseCode = "CS2030",
                title = "Data Structures",
                summary = "Lecture",
                dtstart = LocalDateTime.of(2026, 10, 15, 9, 0),
                dtend = LocalDateTime.of(2026, 10, 15, 10, 0),
                location = "LT12",
                eventType = EventKind.CAMPUS
            )
        )

        dataStore.saveDeviceCalendarEvents(events)
        dataStore.clearDeviceCalendarEvents()

        val loaded = dataStore.data.deviceCalendarEventsFlow().first()

        assertTrue("Should return null after clearing", loaded == null)

        testFile.delete()
    }

    @Test
    fun `events with ONLINE kind serialize and deserialize correctly`() = runTest {
        val testFile = File("/tmp/test_device_calendar_online_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val events = listOf(
            TimetableEvent(
                courseCode = "CS2103T",
                title = "AI-Enabled Software Development",
                summary = "Online Session",
                dtstart = LocalDateTime.of(2026, 10, 16, 15, 0),
                dtend = LocalDateTime.of(2026, 10, 16, 16, 30),
                location = "Online",
                eventType = EventKind.ONLINE
            )
        )

        dataStore.saveDeviceCalendarEvents(events)

        val loaded = dataStore.data.deviceCalendarEventsFlow().first()

        assertEquals(1, loaded?.size)
        assertEquals(EventKind.ONLINE, loaded!![0].eventType)
        assertEquals("Online", loaded[0].location)

        testFile.delete()
    }

    @Test
    fun `multiple saves overwrite previous data`() = runTest {
        val testFile = File("/tmp/test_device_calendar_overwrite_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        val events1 = listOf(
            TimetableEvent(
                courseCode = "CS1010",
                title = "First Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 8, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 9, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val events2 = listOf(
            TimetableEvent(
                courseCode = "CS2030",
                title = "Second Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 15, 10, 0),
                dtend = LocalDateTime.of(2026, 10, 15, 11, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        dataStore.saveDeviceCalendarEvents(events1)
        dataStore.saveDeviceCalendarEvents(events2)

        val loaded = dataStore.data.deviceCalendarEventsFlow().first()

        assertEquals(1, loaded?.size)
        assertEquals("CS2030", loaded!![0].courseCode)
        assertEquals("Second Event", loaded[0].title)

        testFile.delete()
    }
}
