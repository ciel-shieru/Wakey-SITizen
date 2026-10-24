package com.cielshieru.wakey.data.repository

import com.cielshieru.wakey.data.model.WakeyEvent
import com.cielshieru.wakey.data.model.WakeyResponse
import com.cielshieru.wakey.data.network.TimetableClient
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.deviceCalendarEventsFlow
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.saveDeviceCalendarEvents
import com.cielshieru.wakey.data.store.TimetableStore.saveTimetable
import com.cielshieru.wakey.data.store.TimetableStore.timetableFlow
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.RemoteServerProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class TimetableRepositoryImplDeviceCalendarTest {

    private object EmptyCalendarEventSource : com.cielshieru.wakey.domain.port.CalendarEventSource {
        override suspend fun getEventsForDate(
            date: java.time.LocalDate,
            calendarIds: List<String>
        ): List<com.cielshieru.wakey.domain.model.ExternalEvent> = emptyList()
    }


    private val zone = ZoneId.of("Asia/Singapore")

    @Test
    fun `eventsFlow emits Remote Server events when discovery mode is RemoteServer`() = runTest {
        val remoteEvents = listOf(
            TimetableEvent(
                courseCode = "CS1010",
                title = "Programming Methodology",
                summary = "Lecture",
                dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 11, 0),
                location = "LT19",
                eventType = EventKind.CAMPUS
            )
        )

        val deviceEvents = listOf(
            TimetableEvent(
                courseCode = "cal-abc123",
                title = "Device Calendar Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 14, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 15, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val repo = createRepository(
            mode = DiscoveryMode.RemoteServer,
            remoteEvents = remoteEvents,
            deviceEvents = deviceEvents
        )

        val events = repo.eventsFlow.first()

        assertEquals(1, events.size)
        assertEquals("CS1010", events[0].courseCode)
        assertEquals("Programming Methodology", events[0].title)
    }

    @Test
    fun `eventsFlow emits Device Calendar events when discovery mode is DeviceCalendar`() = runTest {
        val remoteEvents = listOf(
            TimetableEvent(
                courseCode = "CS1010",
                title = "Remote Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 11, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val deviceEvents = listOf(
            TimetableEvent(
                courseCode = "cal-abc123",
                title = "Device Calendar Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 14, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 15, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val repo = createRepository(
            mode = DiscoveryMode.DeviceCalendar,
            remoteEvents = remoteEvents,
            deviceEvents = deviceEvents
        )

        val events = repo.eventsFlow.first()

        assertEquals(1, events.size)
        assertEquals("cal-abc123", events[0].courseCode)
        assertEquals("Device Calendar Event", events[0].title)
    }

    @Test
    fun `eventsFlow switches from Remote Server to Device Calendar mode`() = runTest {
        val remoteEvents = listOf(
            TimetableEvent(
                courseCode = "CS1010",
                title = "Remote Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 11, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val deviceEvents = listOf(
            TimetableEvent(
                courseCode = "cal-xyz789",
                title = "Device Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 15, 9, 0),
                dtend = LocalDateTime.of(2026, 10, 15, 10, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val configFlow = MutableStateFlow(AlarmConfig(discoveryMode = DiscoveryMode.RemoteServer))
        val repo = createRepository(
            configFlow = configFlow,
            remoteEvents = remoteEvents,
            deviceEvents = deviceEvents
        )

        var events = repo.eventsFlow.first()
        assertEquals("CS1010", events[0].courseCode)

        configFlow.value = AlarmConfig(discoveryMode = DiscoveryMode.DeviceCalendar)

        events = repo.eventsFlow.first()
        assertEquals("cal-xyz789", events[0].courseCode)
        assertEquals("Device Event", events[0].title)
    }

    @Test
    fun `eventsFlow switches from Device Calendar to Remote Server mode`() = runTest {
        val remoteEvents = listOf(
            TimetableEvent(
                courseCode = "MA1521",
                title = "Math Analysis",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 11, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val deviceEvents = listOf(
            TimetableEvent(
                courseCode = "cal-dev001",
                title = "Device Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 14, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 15, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val configFlow = MutableStateFlow(AlarmConfig(discoveryMode = DiscoveryMode.DeviceCalendar))
        val repo = createRepository(
            configFlow = configFlow,
            remoteEvents = remoteEvents,
            deviceEvents = deviceEvents
        )

        var events = repo.eventsFlow.first()
        assertEquals("cal-dev001", events[0].courseCode)

        configFlow.value = AlarmConfig(discoveryMode = DiscoveryMode.RemoteServer)

        events = repo.eventsFlow.first()
        assertEquals("MA1521", events[0].courseCode)
        assertEquals("Math Analysis", events[0].title)
    }

    @Test
    fun `eventsFlow emits empty list when both sources are empty`() = runTest {
        val repo = createRepository(
            mode = DiscoveryMode.RemoteServer,
            remoteEvents = emptyList(),
            deviceEvents = emptyList()
        )

        val events = repo.eventsFlow.first()

        assertEquals(0, events.size)
    }

    @Test
    fun `eventsFlow emits empty list when Device Calendar source is null`() = runTest {
        val configFlow = MutableStateFlow(AlarmConfig(discoveryMode = DiscoveryMode.DeviceCalendar))
        val repo = createRepository(
            configFlow = configFlow,
            remoteEvents = emptyList(),
            deviceEvents = null
        )

        val events = repo.eventsFlow.first()

        assertEquals(0, events.size)
    }

    @Test
    fun `getEventsForRange returns Remote Server events in RemoteServer mode`() = runTest {
        val remoteEvents = listOf(
            TimetableEvent(
                courseCode = "CS2030",
                title = "Data Structures",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 11, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val repo = createRepository(
            mode = DiscoveryMode.RemoteServer,
            remoteEvents = remoteEvents,
            deviceEvents = emptyList()
        )

        val events = repo.getEventsForRange(
            java.time.LocalDate.of(2026, 10, 14),
            java.time.LocalDate.of(2026, 10, 15)
        )

        assertEquals(1, events.size)
        assertEquals("CS2030", events[0].courseCode)
    }

    @Test
    fun `getEventsForRange returns Device Calendar events in DeviceCalendar mode`() = runTest {
        val deviceEvents = listOf(
            TimetableEvent(
                courseCode = "cal-test",
                title = "Calendar Event",
                summary = "",
                dtstart = LocalDateTime.of(2026, 10, 14, 14, 0),
                dtend = LocalDateTime.of(2026, 10, 14, 15, 0),
                location = "",
                eventType = EventKind.CAMPUS
            )
        )

        val repo = createRepository(
            mode = DiscoveryMode.DeviceCalendar,
            remoteEvents = emptyList(),
            deviceEvents = deviceEvents
        )

        val events = repo.getEventsForRange(
            java.time.LocalDate.of(2026, 10, 14),
            java.time.LocalDate.of(2026, 10, 15)
        )

        assertEquals(1, events.size)
        assertEquals("cal-test", events[0].courseCode)
        assertEquals("Calendar Event", events[0].title)
    }

    private fun createRepository(
        mode: DiscoveryMode,
        remoteEvents: List<TimetableEvent>,
        deviceEvents: List<TimetableEvent>?
    ): TimetableRepositoryImpl {
        val configFlow = MutableStateFlow(AlarmConfig(discoveryMode = mode))
        return createRepository(configFlow, remoteEvents, deviceEvents)
    }

    private fun createRepository(
        configFlow: MutableStateFlow<AlarmConfig>,
        remoteEvents: List<TimetableEvent>,
        deviceEvents: List<TimetableEvent>?
    ): TimetableRepositoryImpl {
        val testFile = File("/tmp/test_repo_dc_${System.nanoTime()}.preferences_pb")
        val datastore = PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )

        if (remoteEvents.isNotEmpty()) {
            val zoneOffset = java.time.ZoneOffset.ofHours(8)
            val wakeyEvents = remoteEvents.map { event ->
                WakeyEvent(
                    course_code = event.courseCode,
                    title = event.title,
                    summary = event.summary,
                    dtstart = event.dtstart.atOffset(zoneOffset).toString(),
                    dtend = event.dtend.atOffset(zoneOffset).toString(),
                    location = event.location,
                    event_type = event.eventType.name
                )
            }
            val response = WakeyResponse(
                schema_version = 1,
                generated_at = java.time.Instant.now().toString(),
                timezone = "Asia/Singapore",
                events = wakeyEvents
            )
            runTest {
                datastore.saveTimetable(response)
            }
        }

        if (deviceEvents != null && deviceEvents.isNotEmpty()) {
            runTest {
                datastore.saveDeviceCalendarEvents(deviceEvents)
            }
        }

        val fakeRemoteServerProvider = object : RemoteServerProvider {
            override val remoteServerUrl: Flow<String> = MutableStateFlow("http://localhost:42748")
        }
        val timetableClient = TimetableClient(fakeRemoteServerProvider)

        return TimetableRepositoryImpl(
            timetableClient = timetableClient,
            datastore = datastore,
            configFlow = configFlow,
            calendarEventSource = EmptyCalendarEventSource
        )
    }
}
