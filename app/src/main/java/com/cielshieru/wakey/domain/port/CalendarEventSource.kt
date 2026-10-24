package com.cielshieru.wakey.domain.port

import com.cielshieru.wakey.domain.model.ExternalEvent
import java.time.LocalDate

interface CalendarEventSource {
    suspend fun getEventsForDate(date: LocalDate, calendarIds: List<String>): List<ExternalEvent>

    /**
     * Reads timetable events. Implementations can enrich timetable events with
     * timetable-specific metadata; external-event adjustment uses the basic
     * [getEventsForDate] method instead.
     */
    suspend fun getTimetableEventsForDate(date: LocalDate, calendarIds: List<String>): List<ExternalEvent> =
        getEventsForDate(date, calendarIds)

    /**
     * Loads timetable events for the inclusive date range. The default implementation
     * preserves compatibility with sources that only implement per-day queries;
     * platform sources should override this to perform one bounded range query.
     */
    suspend fun getTimetableEventsForRange(
        start: LocalDate,
        endInclusive: LocalDate,
        calendarIds: List<String>
    ): List<ExternalEvent> {
        require(!endInclusive.isBefore(start)) { "The end date must not be before the start date." }
        val dayCount = java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive).toInt()
        return (0..dayCount).flatMap { offset ->
            getTimetableEventsForDate(start.plusDays(offset.toLong()), calendarIds)
        }
    }
}
