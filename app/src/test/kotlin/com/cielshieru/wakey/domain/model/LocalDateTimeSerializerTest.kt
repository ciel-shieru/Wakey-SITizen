package com.cielshieru.wakey.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class LocalDateTimeSerializerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `LocalDateTimeSerializer round-trip preserves date and time`() {
        val original = LocalDateTime.of(2026, 10, 14, 10, 30)
        val serialized = json.encodeToString(LocalDateTimeSerializer, original)
        val deserialized = json.decodeFromString(LocalDateTimeSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `LocalDateTimeSerializer round-trip with midnight`() {
        val original = LocalDateTime.of(2026, 1, 1, 0, 0)
        val serialized = json.encodeToString(LocalDateTimeSerializer, original)
        val deserialized = json.decodeFromString(LocalDateTimeSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `LocalDateTimeSerializer round-trip with end of day`() {
        val original = LocalDateTime.of(2026, 12, 31, 23, 59)
        val serialized = json.encodeToString(LocalDateTimeSerializer, original)
        val deserialized = json.decodeFromString(LocalDateTimeSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `LocalDateTimeSerializer serializes to ISO string`() {
        val dateTime = LocalDateTime.of(2026, 10, 14, 10, 30)
        val serialized = json.encodeToString(LocalDateTimeSerializer, dateTime)
        assertEquals("\"2026-10-14T10:30\"", serialized)
    }

    @Test
    fun `LocalDateTimeSerializer handles leap year`() {
        val original = LocalDateTime.of(2028, 2, 29, 12, 0)
        val serialized = json.encodeToString(LocalDateTimeSerializer, original)
        val deserialized = json.decodeFromString(LocalDateTimeSerializer, serialized)
        assertEquals(original, deserialized)
    }
}
