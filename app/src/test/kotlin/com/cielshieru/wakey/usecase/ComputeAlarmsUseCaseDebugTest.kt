package com.cielshieru.wakey.usecase

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.fakes.FakeClock
import com.cielshieru.wakey.fakes.FakeZoneProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalDate

class ComputeAlarmsUseCaseDebugTest {

    private val clock = FakeClock()
    private val zoneProvider = FakeZoneProvider()
    private val useCase = ComputeAlarmsUseCase(clock, zoneProvider)

    private fun makeEvent(day: Int, hour: Int): TimetableEvent {
        return TimetableEvent(
            courseCode = "CS101",
            title = "Lecture",
            summary = "Lecture",
            dtstart = LocalDateTime.of(2026, 10, day, hour, 0),
            dtend = LocalDateTime.of(2026, 10, day, hour + 1, 0),
            location = "S17",
            eventType = EventKind.CAMPUS
        )
    }

    @Test
    fun `compute without override uses clock time`() {
        val events = listOf(makeEvent(10, 8))
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = java.time.ZoneOffset.UTC

        val config = AlarmConfig()
        val result = useCase.compute(events, config)

        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 10, 10), result[0].day)
    }

    @Test
    fun `compute with targetDateOverride passes override to AlarmComputer`() {
        val events = listOf(makeEvent(14, 8))
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = java.time.ZoneOffset.UTC

        val config = AlarmConfig()
        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = useCase.compute(events, config, overrideDate)

        assertEquals(1, result.size)
        assertEquals(overrideDate, result[0].day)
    }

    @Test
    fun `compute with null targetDateOverride behaves like no-arg overload`() {
        val events = listOf(makeEvent(10, 8))
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = java.time.ZoneOffset.UTC

        val config = AlarmConfig()
        val resultWithNull = useCase.compute(events, config, null)
        val resultWithoutOverride = useCase.compute(events, config)

        assertEquals(resultWithoutOverride.size, resultWithNull.size)
        assertEquals(resultWithoutOverride[0].day, resultWithNull[0].day)
    }

    @Test
    fun `compute with override produces different day than clock time`() {
        val events = listOf(makeEvent(10, 8), makeEvent(14, 8))
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = java.time.ZoneOffset.UTC

        val config = AlarmConfig()
        val resultNoOverride = useCase.compute(events, config)
        val overrideDate = LocalDate.of(2026, 10, 14)
        val resultWithOverride = useCase.compute(events, config, overrideDate)

        assertEquals(LocalDate.of(2026, 10, 10), resultNoOverride[0].day)
        assertEquals(overrideDate, resultWithOverride[0].day)
    }

    @Test
    fun `compute with override and empty events returns empty list`() {
        val clock = FakeClock()
        val zoneProvider = FakeZoneProvider()
        val useCase = ComputeAlarmsUseCase(clock, zoneProvider)
        clock.set(java.time.Instant.parse("2026-10-10T12:00:00Z"))
        zoneProvider.zone = java.time.ZoneOffset.UTC

        val config = AlarmConfig()
        val overrideDate = LocalDate.of(2026, 10, 14)
        val result = useCase.compute(emptyList(), config, overrideDate)

        assertEquals(0, result.size)
    }
}
