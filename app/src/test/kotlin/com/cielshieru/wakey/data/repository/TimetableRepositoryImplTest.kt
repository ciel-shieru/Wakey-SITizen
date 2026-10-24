package com.cielshieru.wakey.data.repository

import com.cielshieru.wakey.data.model.WakeyEvent
import com.cielshieru.wakey.data.model.WakeyResponse
import com.cielshieru.wakey.data.network.TimetableClient
import com.cielshieru.wakey.data.store.TimetableStore.saveTimetable
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.RemoteServerProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime

class TimetableRepositoryImplTest {

    private object EmptyCalendarEventSource : com.cielshieru.wakey.domain.port.CalendarEventSource {
        override suspend fun getEventsForDate(
            date: java.time.LocalDate,
            calendarIds: List<String>
        ): List<com.cielshieru.wakey.domain.model.ExternalEvent> = emptyList()
    }


    @Test
    fun `eventsFlow emits Remote Server events by default`() = runTest {
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

        val testFile = File("/tmp/test_repo_default_${System.nanoTime()}.preferences_pb")
        val datastore = PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )
        val response = WakeyResponse(
            schema_version = 1,
            generated_at = java.time.Instant.now().toString(),
            timezone = "Asia/Singapore",
            events = remoteEvents.map { event ->
                WakeyEvent(
                    course_code = event.courseCode,
                    title = event.title,
                    summary = event.summary,
                    dtstart = event.dtstart.atOffset(java.time.ZoneOffset.ofHours(8)).toString(),
                    dtend = event.dtend.atOffset(java.time.ZoneOffset.ofHours(8)).toString(),
                    location = event.location,
                    event_type = event.eventType.name
                )
            }
        )
        datastore.saveTimetable(response)

        val configFlow = MutableStateFlow(AlarmConfig(discoveryMode = DiscoveryMode.RemoteServer))
        val fakeRemoteServerProvider = object : RemoteServerProvider {
            override val remoteServerUrl: kotlinx.coroutines.flow.Flow<String> = MutableStateFlow("http://localhost:42748")
        }
        val repo = TimetableRepositoryImpl(
            timetableClient = TimetableClient(fakeRemoteServerProvider),
            datastore = datastore,
            configFlow = configFlow,
            calendarEventSource = EmptyCalendarEventSource
        )

        val events = repo.eventsFlow.first()

        assertEquals(1, events.size)
        assertEquals("CS1010", events[0].courseCode)

        testFile.delete()
    }
}
