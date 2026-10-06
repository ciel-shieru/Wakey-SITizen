package com.cielshieru.wakey.domain.port

import com.cielshieru.wakey.domain.model.TimetableEvent
import java.time.LocalDate

interface TimetableRepository {
    suspend fun getEventsForDate(date: LocalDate): List<TimetableEvent>
    suspend fun getEventsForRange(start: LocalDate, end: LocalDate): List<TimetableEvent>
    suspend fun refreshTimetable(): Result<List<TimetableEvent>>
}
