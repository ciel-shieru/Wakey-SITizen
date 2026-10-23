package com.cielshieru.wakey.domain.port

import com.cielshieru.wakey.domain.model.TimetableEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

interface TimetableRepository {
    suspend fun getEventsForDate(date: LocalDate): List<TimetableEvent>
    /** Reads cached events whose start dates fall within the inclusive date range. */
    suspend fun getEventsForRange(start: LocalDate, end: LocalDate): List<TimetableEvent>

    /**
     * Loads events for a forecast range. Implementations can fetch the necessary
     * windows from the configured server or read selected device calendars.
     */
    suspend fun fetchEventsForRange(start: LocalDate, endInclusive: LocalDate): Result<List<TimetableEvent>> =
        try {
            Result.success(getEventsForRange(start, endInclusive))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    suspend fun refreshTimetable(): Result<List<TimetableEvent>>
    val eventsFlow: Flow<List<TimetableEvent>>
}
