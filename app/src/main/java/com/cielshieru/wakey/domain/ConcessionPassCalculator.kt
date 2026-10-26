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

data class ConcessionPassStartDateRecommendation(
    val startDate: LocalDate,
    val campusVisitDays: Int,
    val estimatedTransportCostCents: Long,
    /** Positive is savings, zero is break-even, and negative is a loss. */
    val netSavingsCents: Long
)

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

    /**
     * Ranks candidate pass start dates by the net amount saved versus paying
     * regular fares. Ties are stable and prefer the earlier date.
     */
    fun rankBestStartDates(
        events: Iterable<TimetableEvent>,
        toCampusCents: Long,
        fromCampusCents: Long,
        firstCandidateDate: LocalDate,
        candidateDays: Int = WINDOW_DAYS.toInt(),
        topCount: Int = 3
    ): Map<MonthlyConcessionPass, List<ConcessionPassStartDateRecommendation>> {
        require(toCampusCents >= 0L && fromCampusCents >= 0L) {
            "Trip costs must not be negative."
        }
        require(candidateDays in 1..WINDOW_DAYS.toInt()) {
            "Candidate dates must cover between 1 and ${WINDOW_DAYS} days."
        }
        require(topCount > 0) { "The number of ranked dates must be positive." }

        val eventSnapshot = events.toList()
        return MonthlyConcessionPass.values().associateWith { pass ->
            (0 until candidateDays).mapNotNull { offset ->
                val startDate = firstCandidateDate.plusDays(offset.toLong())
                val visitDays = campusVisitCount(eventSnapshot, startDate)
                val totalCost = totalCostCents(toCampusCents, fromCampusCents, visitDays)
                    ?: return@mapNotNull null
                ConcessionPassStartDateRecommendation(
                    startDate = startDate,
                    campusVisitDays = visitDays,
                    estimatedTransportCostCents = totalCost,
                    netSavingsCents = savingsCents(totalCost, pass)
                )
            }.sortedWith(
                compareByDescending<ConcessionPassStartDateRecommendation> { it.netSavingsCents }
                    .thenBy { it.startDate }
            ).take(topCount)
        }
    }

    /** Positive is money saved; negative means paying more for the pass than fares. */
    fun savingsCents(totalCostCents: Long, pass: MonthlyConcessionPass): Long =
        totalCostCents - pass.priceCents

    /** A pass is worth buying only when its price is strictly below the fare total. */
    fun shouldBuy(totalCostCents: Long, pass: MonthlyConcessionPass): Boolean =
        totalCostCents > pass.priceCents
}
