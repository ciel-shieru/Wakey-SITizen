package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.model.ExternalEvent
import com.cielshieru.wakey.domain.port.CalendarEventSource
import java.time.LocalDate

class FakeCalendarEventSource : CalendarEventSource {
    var events: List<ExternalEvent> = emptyList()
    private var eventsByDate: Map<LocalDate, List<ExternalEvent>> = emptyMap()
    val requestedDates = mutableListOf<LocalDate>()

    override suspend fun getEventsForDate(date: LocalDate, calendarIds: List<String>): List<ExternalEvent> {
        requestedDates.add(date)
        return eventsByDate.getOrElse(date) { events }
    }

    fun setEventsByDate(map: Map<LocalDate, List<ExternalEvent>>) {
        eventsByDate = map
        requestedDates.clear()
    }

    fun clearRequestedDates() {
        requestedDates.clear()
    }
}
