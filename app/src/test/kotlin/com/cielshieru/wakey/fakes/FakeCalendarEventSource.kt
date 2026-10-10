package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.model.ExternalEvent
import com.cielshieru.wakey.domain.port.CalendarEventSource
import java.time.LocalDate

class FakeCalendarEventSource : CalendarEventSource {
    var events: List<ExternalEvent> = emptyList()

    override suspend fun getEventsForDate(date: LocalDate, calendarIds: List<String>): List<ExternalEvent> = events
}
