package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class MonthlyConcessionPass(
    val label: String,
    val priceCents: Long
) {
    TRAIN_ONLY("Train-only Pass", 4_800L),
    BUS_ONLY("Bus-only Pass", 5_550L),
    BUS_TRAIN_HYBRID("Bus+Train (Hybrid) Pass", 8_100L)
}

/** Pure fare calculations using integer cents to avoid floating-point rounding. */
object ConcessionPassCalculator {
    const val WINDOW_DAYS = 30L
    private val costInputRegex = Regex("""\d{1,7}(?:\.\d{0,2})?""")

    /**
     * Counts distinct dates with a campus event in the inclusive
     * [startDate, startDate + 29 days] range.
     */
    fun campusVisitCount(events: Iterable<TimetableEvent>, startDate: LocalDate): Int {
        val endExclusive = startDate.plusDays(WINDOW_DAYS)
        return events.asSequence()
            .filter { it.eventType == EventKind.CAMPUS }
            .map { it.dtstart.toLocalDate() }
            .filter { eventDate ->
                !eventDate.isBefore(startDate) && eventDate.isBefore(endExclusive)
            }
            .distinct()
            .count()
    }

    /** Accepts a plain dollar amount with up to seven whole digits and two decimals. */
    fun isCostInputAllowed(input: String): Boolean =
        input.isEmpty() ||
            (costInputRegex.matches(input) &&
                (!input.contains('.') || input.substringBefore('.').isNotEmpty()))

    /** Parses a cost as integer cents, or null while input is empty or invalid. */
    fun parseCostToCents(input: String): Long? {
        if (input.isEmpty() || !isCostInputAllowed(input)) return null
        return try {
            BigDecimal(input)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** Calculates (to + from) × visits without floating-point rounding. */
    fun totalCostCents(toCampusCents: Long, fromCampusCents: Long, visits: Int): Long? {
        if (toCampusCents < 0L || fromCampusCents < 0L || visits < 0) return null
        return try {
            Math.multiplyExact(Math.addExact(toCampusCents, fromCampusCents), visits.toLong())
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** A pass is worth buying only when its price is strictly below the fare total. */
    fun shouldBuy(totalCostCents: Long, pass: MonthlyConcessionPass): Boolean =
        totalCostCents > pass.priceCents
}
