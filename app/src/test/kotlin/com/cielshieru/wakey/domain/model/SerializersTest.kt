package com.cielshieru.wakey.domain.model

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.Instant
import java.time.LocalDate

class SerializersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `InstantSerializer round-trip preserves epoch millisecond`() {
        val original = Instant.ofEpochMilli(1700000000000L)
        val serialized = json.encodeToString(InstantSerializer, original)
        val deserialized = json.decodeFromString(InstantSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `InstantSerializer round-trip with epoch zero`() {
        val original = Instant.EPOCH
        val serialized = json.encodeToString(InstantSerializer, original)
        val deserialized = json.decodeFromString(InstantSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `InstantSerializer round-trip with max milliseconds`() {
        val original = Instant.ofEpochMilli(Long.MAX_VALUE)
        val serialized = json.encodeToString(InstantSerializer, original)
        val deserialized = json.decodeFromString(InstantSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `InstantSerializer serializes to number`() {
        val instant = Instant.ofEpochMilli(1234567890L)
        val serialized = json.encodeToString(InstantSerializer, instant)
        assertEquals("1234567890", serialized)
    }

    @Test
    fun `LocalDateSerializer round-trip preserves date`() {
        val original = LocalDate.of(2025, 11, 15)
        val serialized = json.encodeToString(LocalDateSerializer, original)
        val deserialized = json.decodeFromString(LocalDateSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `LocalDateSerializer round-trip with epoch date`() {
        val original = LocalDate.of(1970, 1, 1)
        val serialized = json.encodeToString(LocalDateSerializer, original)
        val deserialized = json.decodeFromString(LocalDateSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `LocalDateSerializer round-trip with year 2099`() {
        val original = LocalDate.of(2099, 12, 31)
        val serialized = json.encodeToString(LocalDateSerializer, original)
        val deserialized = json.decodeFromString(LocalDateSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `LocalDateSerializer serializes to ISO string`() {
        val date = LocalDate.of(2025, 11, 15)
        val serialized = json.encodeToString(LocalDateSerializer, date)
        assertEquals("\"2025-11-15\"", serialized)
    }

    @Test
    fun `AlarmSchedule round-trip with single alarm`() {
        val day = LocalDate.of(2025, 11, 15)
        val trigger = Instant.ofEpochMilli(1700000000000L)
        val schedule = AlarmSchedule(
            day = day,
            alarms = listOf(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = trigger,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(day, 0)
                )
            ),
            firstAlarmTime = trigger
        )

        val jsonStr = json.encodeToString(AlarmSchedule.serializer(), schedule)
        val deserialized = json.decodeFromString(AlarmSchedule.serializer(), jsonStr)

        assertEquals(schedule.day, deserialized.day)
        assertEquals(schedule.alarms.size, deserialized.alarms.size)
        assertEquals(schedule.alarms[0].index, deserialized.alarms[0].index)
        assertEquals(schedule.alarms[0].triggerAt, deserialized.alarms[0].triggerAt)
        assertEquals(schedule.alarms[0].requestCode, deserialized.alarms[0].requestCode)
        assertEquals(schedule.firstAlarmTime, deserialized.firstAlarmTime)
    }

    @Test
    fun `AlarmSchedule round-trip with multiple alarms`() {
        val day = LocalDate.of(2025, 6, 1)
        val alarm1 = Instant.ofEpochMilli(1700000000000L)
        val alarm2 = Instant.ofEpochMilli(1700003600000L)
        val schedule = AlarmSchedule(
            day = day,
            alarms = listOf(
                AlarmSchedule.Alarm(0, alarm1, AlarmSchedule.Alarm.computeRequestCode(day, 0)),
                AlarmSchedule.Alarm(1, alarm2, AlarmSchedule.Alarm.computeRequestCode(day, 1))
            ),
            firstAlarmTime = alarm1
        )

        val jsonStr = json.encodeToString(AlarmSchedule.serializer(), schedule)
        val deserialized = json.decodeFromString(AlarmSchedule.serializer(), jsonStr)

        assertEquals(schedule, deserialized)
    }

    @Test
    fun `AlarmSchedule round-trip with empty alarms and null firstAlarmTime`() {
        val day = LocalDate.of(2025, 12, 25)
        val schedule = AlarmSchedule(
            day = day,
            alarms = emptyList(),
            firstAlarmTime = null
        )

        val jsonStr = json.encodeToString(AlarmSchedule.serializer(), schedule)
        val deserialized = json.decodeFromString(AlarmSchedule.serializer(), jsonStr)

        assertEquals(schedule.day, deserialized.day)
        assertEquals(schedule.alarms, deserialized.alarms)
        assertEquals(schedule.firstAlarmTime, deserialized.firstAlarmTime)
    }

    @Test
    fun `AlarmSchedule with null firstAlarmTime serializes to null`() {
        val schedule = AlarmSchedule(
            day = LocalDate.of(2025, 1, 1),
            alarms = emptyList(),
            firstAlarmTime = null
        )

        val jsonStr = json.encodeToString(AlarmSchedule.serializer(), schedule)
        assertEquals("{\"day\":\"2025-01-01\",\"alarms\":[],\"firstAlarmTime\":null}", jsonStr)
    }

    @Test
    fun `AlarmSchedule with non-null firstAlarmTime serializes to number`() {
        val trigger = Instant.ofEpochMilli(1700000000000L)
        val schedule = AlarmSchedule(
            day = LocalDate.of(2025, 1, 1),
            alarms = emptyList(),
            firstAlarmTime = trigger
        )

        val jsonStr = json.encodeToString(AlarmSchedule.serializer(), schedule)
        assertEquals("{\"day\":\"2025-01-01\",\"alarms\":[],\"firstAlarmTime\":1700000000000}", jsonStr)
    }

    @Test
    fun `ManualOverride round-trip with SNOOZE type`() {
        val expiresAt = Instant.ofEpochMilli(1700100000000L)
        val override = ManualOverride(
            type = OverrideType.SNOOZE,
            value = "5",
            expiresAt = expiresAt,
            applied = true
        )

        val jsonStr = json.encodeToString(ManualOverride.serializer(), override)
        val deserialized = json.decodeFromString(ManualOverride.serializer(), jsonStr)

        assertEquals(override.type, deserialized.type)
        assertEquals(override.value, deserialized.value)
        assertEquals(override.expiresAt, deserialized.expiresAt)
        assertEquals(override.applied, deserialized.applied)
    }

    @Test
    fun `ManualOverride round-trip with SKIP_TOMORROW type`() {
        val expiresAt = Instant.ofEpochMilli(1700200000000L)
        val override = ManualOverride(
            type = OverrideType.SKIP_TOMORROW,
            value = "",
            expiresAt = expiresAt,
            applied = false
        )

        val jsonStr = json.encodeToString(ManualOverride.serializer(), override)
        val deserialized = json.decodeFromString(ManualOverride.serializer(), jsonStr)

        assertEquals(override, deserialized)
    }

    @Test
    fun `ManualOverride round-trip with CUSTOM_TIME type`() {
        val expiresAt = Instant.ofEpochMilli(1700300000000L)
        val override = ManualOverride(
            type = OverrideType.CUSTOM_TIME,
            value = "08:30",
            expiresAt = expiresAt,
            applied = true
        )

        val jsonStr = json.encodeToString(ManualOverride.serializer(), override)
        val deserialized = json.decodeFromString(ManualOverride.serializer(), jsonStr)

        assertEquals(override, deserialized)
    }

    @Test
    fun `ManualOverride default applied value`() {
        val expiresAt = Instant.ofEpochMilli(1700000000000L)
        val override = ManualOverride(
            type = OverrideType.SNOOZE,
            value = "10",
            expiresAt = expiresAt
        )

        val jsonStr = json.encodeToString(ManualOverride.serializer(), override)
        val deserialized = json.decodeFromString(ManualOverride.serializer(), jsonStr)

        assertEquals(false, deserialized.applied)
    }

    @Test
    fun `OverrideType enum round-trip all values`() {
        for (type in OverrideType.values()) {
            val override = ManualOverride(
                type = type,
                value = "test",
                expiresAt = Instant.EPOCH,
                applied = false
            )

            val jsonStr = json.encodeToString(ManualOverride.serializer(), override)
            val deserialized = json.decodeFromString(ManualOverride.serializer(), jsonStr)

            assertEquals(type, deserialized.type)
        }
    }
}
