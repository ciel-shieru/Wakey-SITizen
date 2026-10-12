package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.TimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class FakeTimetableRepository : TimetableRepository {
    private val _events = MutableStateFlow<List<TimetableEvent>>(emptyList())
    override val eventsFlow: StateFlow<List<TimetableEvent>> = _events.asStateFlow()

    var events: List<TimetableEvent> = emptyList()
        set(value) {
            field = value
            _events.value = value
        }

    override suspend fun getEventsForDate(date: LocalDate): List<TimetableEvent> = events
    override suspend fun getEventsForRange(start: LocalDate, end: LocalDate): List<TimetableEvent> = events
    override suspend fun refreshTimetable(): Result<List<TimetableEvent>> = Result.success(events)
}
