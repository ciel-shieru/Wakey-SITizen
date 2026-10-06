package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.TimetableRepository
import java.time.LocalDate

class FakeTimetableRepository : TimetableRepository {
    var events: List<TimetableEvent> = emptyList()

    override suspend fun getEventsForDate(date: LocalDate): List<TimetableEvent> = events
    override suspend fun getEventsForRange(start: LocalDate, end: LocalDate): List<TimetableEvent> = events
    override suspend fun refreshTimetable(): Result<List<TimetableEvent>> = Result.success(events)
}
