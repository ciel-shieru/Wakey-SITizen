package com.cielshieru.wakey.domain.port

import com.cielshieru.wakey.domain.model.ExternalEvent
import java.time.LocalDate

interface CalendarEventSource {
    suspend fun getEventsForDate(date: LocalDate, calendarIds: List<String>): List<ExternalEvent>
}
