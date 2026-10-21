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
}
