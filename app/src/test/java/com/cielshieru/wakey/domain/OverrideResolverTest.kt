package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.model.OverrideType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

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
}
