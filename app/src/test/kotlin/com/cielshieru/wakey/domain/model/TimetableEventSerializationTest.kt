package com.cielshieru.wakey.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TimetableEventSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `TimetableEvent serializes and deserializes correctly`() {
        val event = TimetableEvent(
            courseCode = "CS1010",
            title = "Programming Methodology",
            summary = "Lecture",
            dtstart = LocalDateTime.of(2026, 10, 14, 10, 0),
            dtend = LocalDateTime.of(2026, 10, 14, 11, 0),
            location = "LT19",
            eventType = EventKind.CAMPUS
        )

        val jsonStr = json.encodeToString(event)
        val deserialized = json.decodeFromString<TimetableEvent>(jsonStr)

        assertEquals(event.courseCode, deserialized.courseCode)
        assertEquals(event.title, deserialized.title)
        assertEquals(event.summary, deserialized.summary)
        assertEquals(event.dtstart, deserialized.dtstart)
        assertEquals(event.dtend, deserialized.dtend)
        assertEquals(event.location, deserialized.location)
        assertEquals(event.eventType, deserialized.eventType)
    }

    @Test
    fun `TimetableEvent serializes with LocalDateTime fields`() {
        val event = TimetableEvent(
            courseCode = "CS1010",
            title = "Test",
            summary = "",
            dtstart = LocalDateTime.of(2026, 10, 14, 10, 30),
            dtend = LocalDateTime.of(2026, 10, 14, 11, 30),
            location = "LT19",
            eventType = EventKind.CAMPUS
        )

        val jsonStr = json.encodeToString(event)

        val deserialized = json.decodeFromString<TimetableEvent>(jsonStr)

        assertEquals(event.dtstart, deserialized.dtstart)
        assertEquals(event.dtend, deserialized.dtend)
    }

    @Test
    fun `List of TimetableEvent serializes and deserializes correctly`() {
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

        val jsonStr = json.encodeToString(events)
        val deserialized = json.decodeFromString<List<TimetableEvent>>(jsonStr)

        assertEquals(events.size, deserialized.size)
        assertEquals(events[0].courseCode, deserialized[0].courseCode)
        assertEquals(events[1].courseCode, deserialized[1].courseCode)
    }

    @Test
    fun `empty list of TimetableEvent serializes correctly`() {
        val events: List<TimetableEvent> = emptyList()

        val jsonStr = json.encodeToString(events)
        val deserialized = json.decodeFromString<List<TimetableEvent>>(jsonStr)

        assertEquals(0, deserialized.size)
    }

    @Test
    fun `TimetableEvent with ONLINE kind serializes correctly`() {
        val event = TimetableEvent(
            courseCode = "CS2103T",
            title = "AI-Enabled Software Development",
            summary = "Online Session",
            dtstart = LocalDateTime.of(2026, 10, 16, 15, 0),
            dtend = LocalDateTime.of(2026, 10, 16, 16, 30),
            location = "Online",
            eventType = EventKind.ONLINE
        )

        val jsonStr = json.encodeToString(event)
        val deserialized = json.decodeFromString<TimetableEvent>(jsonStr)

        assertEquals(EventKind.ONLINE, deserialized.eventType)
    }

    @Test
    fun `TimetableEvent with empty strings serializes correctly`() {
        val event = TimetableEvent(
            courseCode = "",
            title = "",
            summary = "",
            dtstart = LocalDateTime.of(2026, 1, 1, 0, 0),
            dtend = LocalDateTime.of(2026, 1, 1, 1, 0),
            location = "",
            eventType = EventKind.CAMPUS
        )

        val jsonStr = json.encodeToString(event)
        val deserialized = json.decodeFromString<TimetableEvent>(jsonStr)

        assertEquals("", deserialized.courseCode)
        assertEquals("", deserialized.title)
        assertEquals("", deserialized.summary)
        assertEquals("", deserialized.location)
    }
}
