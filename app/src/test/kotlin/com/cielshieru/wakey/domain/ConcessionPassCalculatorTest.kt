package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcessionPassCalculatorTest {

    @Test
    fun `campus visits count unique dates in the inclusive 30 day window`() {
        val start = LocalDate.of(2026, 10, 23)
        val events = listOf(
            event(start, EventKind.CAMPUS, "First class"),
            event(start, EventKind.CAMPUS, "Second class"),
            event(start.plusDays(29), EventKind.CAMPUS, "Last day of window"),
            event(start.plusDays(30), EventKind.CAMPUS, "Outside window"),
            event(start.minusDays(1), EventKind.CAMPUS, "Before window"),
            event(start.plusDays(1), EventKind.ONLINE, "Online class")
        )

        assertEquals(2, ConcessionPassCalculator.campusVisitCount(events, start))
    }

    @Test
    fun `cost parser accepts cents and rejects invalid dollar formats`() {
        assertEquals(125L, ConcessionPassCalculator.parseCostToCents("1.25"))
        assertEquals(100L, ConcessionPassCalculator.parseCostToCents("1"))
        assertEquals(0L, ConcessionPassCalculator.parseCostToCents("0.00"))
        assertNull(ConcessionPassCalculator.parseCostToCents(""))
        assertNull(ConcessionPassCalculator.parseCostToCents("-1.00"))
        assertNull(ConcessionPassCalculator.parseCostToCents("1.234"))
        assertNull(ConcessionPassCalculator.parseCostToCents("$1.00"))
        assertFalse(ConcessionPassCalculator.isCostInputAllowed(".50"))
        assertFalse(ConcessionPassCalculator.isCostInputAllowed("1.234"))
    }

    @Test
    fun `total and recommendations use exact cents and strict greater than comparison`() {
        val total = ConcessionPassCalculator.totalCostCents(
            toCampusCents = 250L,
            fromCampusCents = 250L,
            visits = 10
        )

        assertEquals(5_000L, total)
        assertFalse(ConcessionPassCalculator.shouldBuy(4_800L, MonthlyConcessionPass.TRAIN_ONLY))
        assertTrue(ConcessionPassCalculator.shouldBuy(4_801L, MonthlyConcessionPass.TRAIN_ONLY))
        assertFalse(ConcessionPassCalculator.shouldBuy(5_550L, MonthlyConcessionPass.BUS_ONLY))
        assertTrue(ConcessionPassCalculator.shouldBuy(8_101L, MonthlyConcessionPass.BUS_TRAIN_HYBRID))
    }

    @Test
    fun `best start dates rank each inclusive window by net savings`() {
        val today = LocalDate.of(2026, 10, 24)
        val events = listOf(
            event(today, EventKind.CAMPUS, "Today"),
            event(today.plusDays(29), EventKind.CAMPUS, "End of first window"),
            event(today.plusDays(58), EventKind.CAMPUS, "End of last candidate window")
        )

        val rankings = ConcessionPassCalculator.rankBestStartDates(
            events = events,
            toCampusCents = 3_000L,
            fromCampusCents = 3_000L,
            firstCandidateDate = today
        )
        val trainPassDates = rankings.getValue(MonthlyConcessionPass.TRAIN_ONLY)

        assertEquals(
            listOf(today, today.plusDays(29), today.plusDays(1)),
            trainPassDates.map { it.startDate }
        )
        assertEquals(2, trainPassDates[0].campusVisitDays)
        assertEquals(12_000L, trainPassDates[0].estimatedTransportCostCents)
        assertEquals(7_200L, trainPassDates[0].netSavingsCents)
        assertEquals(6_000L, trainPassDates[2].estimatedTransportCostCents)
        assertEquals(1_200L, trainPassDates[2].netSavingsCents)
        assertEquals(3, trainPassDates.size)
    }

    @Test
    fun `pass savings can be negative when fares cost less than the pass`() {
        assertEquals(
            -1_800L,
            ConcessionPassCalculator.savingsCents(3_000L, MonthlyConcessionPass.TRAIN_ONLY)
        )
        assertEquals(
            0L,
            ConcessionPassCalculator.savingsCents(4_800L, MonthlyConcessionPass.TRAIN_ONLY)
        )
    }

    private fun event(date: LocalDate, kind: EventKind, title: String) = TimetableEvent(
        courseCode = "TEST1000",
        title = title,
        summary = title,
        dtstart = LocalDateTime.of(date, java.time.LocalTime.of(9, 0)),
        dtend = LocalDateTime.of(date, java.time.LocalTime.of(10, 0)),
        location = if (kind == EventKind.ONLINE) "Online" else "Test Campus",
        eventType = kind
    )
}
