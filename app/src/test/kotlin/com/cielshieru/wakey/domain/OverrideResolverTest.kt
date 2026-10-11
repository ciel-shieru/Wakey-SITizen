package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.model.OverrideType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class OverrideResolverTest {

    @Test
    fun `resolve returns null when no overrides`() {
        val result = OverrideResolver.resolve(emptyList(), Instant.now(), java.time.ZoneOffset.UTC)
        assertNull(result)
    }

    @Test
    fun `resolve returns null when all expired`() {
        val overrides = listOf(
            ManualOverride(
                type = OverrideType.SNOOZE,
                value = "30",
                expiresAt = Instant.now().minusSeconds(3600)
            )
        )

        val result = OverrideResolver.resolve(overrides, Instant.now(), java.time.ZoneOffset.UTC)
        assertNull(result)
    }

    @Test
    fun `resolve returns CUSTOM_TIME over SNOOZE`() {
        val now = Instant.now()
        val overrides = listOf(
            ManualOverride(type = OverrideType.SNOOZE, value = "30", expiresAt = now.plusSeconds(3600)),
            ManualOverride(type = OverrideType.CUSTOM_TIME, value = "8:00", expiresAt = now.plusSeconds(3600))
        )

        val result = OverrideResolver.resolve(overrides, now, java.time.ZoneOffset.UTC)

        assertEquals(OverrideType.CUSTOM_TIME, result?.type)
    }

    @Test
    fun `resolve returns SNOOZE over SKIP_TOMORROW`() {
        val now = Instant.now()
        val overrides = listOf(
            ManualOverride(type = OverrideType.SKIP_TOMORROW, value = "2026-10-07", expiresAt = now.plusSeconds(3600)),
            ManualOverride(type = OverrideType.SNOOZE, value = "30", expiresAt = now.plusSeconds(3600))
        )

        val result = OverrideResolver.resolve(overrides, now, java.time.ZoneOffset.UTC)

        assertEquals(OverrideType.SNOOZE, result?.type)
    }

    @Test
    fun `clearExpired removes expired overrides`() {
        val now = Instant.now()
        val overrides = listOf(
            ManualOverride(type = OverrideType.SNOOZE, value = "30", expiresAt = now.minusSeconds(3600)),
            ManualOverride(type = OverrideType.CUSTOM_TIME, value = "8:00", expiresAt = now.plusSeconds(3600))
        )

        val result = OverrideResolver.clearExpired(overrides, now)

        assertEquals(1, result.size)
        assertEquals(OverrideType.CUSTOM_TIME, result[0].type)
    }

    @Test
    fun `applyOverride SKIP_ALARM removes matching alarm`() {
        val triggerAt = Instant.parse("2026-10-15T07:30:00Z")
        val schedule = AlarmSchedule(
            day = LocalDate.of(2026, 10, 15),
            alarms = listOf(
                AlarmSchedule.Alarm(index = 0, triggerAt = triggerAt, requestCode = 123),
                AlarmSchedule.Alarm(index = 1, triggerAt = triggerAt.plusSeconds(1800), requestCode = 124)
            ),
            firstAlarmTime = triggerAt
        )

        val override = ManualOverride(
            type = OverrideType.SKIP_ALARM,
            value = "123",
            expiresAt = Instant.now().plusSeconds(86400)
        )

        val result = OverrideResolver.applyOverride(override, schedule, ZoneOffset.UTC)

        assertEquals(1, result.alarms.size)
        assertEquals(124, result.alarms[0].requestCode)
    }

    @Test
    fun `applyOverride SKIP_ALARM with invalid value returns unchanged schedule`() {
        val triggerAt = Instant.parse("2026-10-15T07:30:00Z")
        val schedule = AlarmSchedule(
            day = LocalDate.of(2026, 10, 15),
            alarms = listOf(
                AlarmSchedule.Alarm(index = 0, triggerAt = triggerAt, requestCode = 123)
            ),
            firstAlarmTime = triggerAt
        )

        val override = ManualOverride(
            type = OverrideType.SKIP_ALARM,
            value = "not-a-number",
            expiresAt = Instant.now().plusSeconds(86400)
        )

        val result = OverrideResolver.applyOverride(override, schedule, ZoneOffset.UTC)

        assertEquals(1, result.alarms.size)
        assertEquals(123, result.alarms[0].requestCode)
    }

    @Test
    fun `applyOverride SKIP_ALARM removes last alarm and clears firstAlarmTime`() {
        val triggerAt = Instant.parse("2026-10-15T07:30:00Z")
        val schedule = AlarmSchedule(
            day = LocalDate.of(2026, 10, 15),
            alarms = listOf(
                AlarmSchedule.Alarm(index = 0, triggerAt = triggerAt, requestCode = 123)
            ),
            firstAlarmTime = triggerAt
        )

        val override = ManualOverride(
            type = OverrideType.SKIP_ALARM,
            value = "123",
            expiresAt = Instant.now().plusSeconds(86400)
        )

        val result = OverrideResolver.applyOverride(override, schedule, ZoneOffset.UTC)

        assertEquals(0, result.alarms.size)
        assertNull(result.firstAlarmTime)
    }
}
