package com.cielshieru.wakey.data.repository

import com.cielshieru.wakey.data.model.WakeyEvent
import com.cielshieru.wakey.data.network.TimetableClient
import com.cielshieru.wakey.data.store.TimetableStore.saveTimetable
import com.cielshieru.wakey.data.store.TimetableStore.timetableFlow
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.TimetableRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class TimetableRepositoryImpl(
    private val timetableClient: TimetableClient,
    private val datastore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
) : TimetableRepository {

    override suspend fun getEventsForDate(date: LocalDate): List<TimetableEvent> {
        return getEventsForRange(date, date)
    }

    override suspend fun getEventsForRange(start: LocalDate, end: LocalDate): List<TimetableEvent> {
        val response = datastore.data.timetableFlow().first()
        return response?.events?.map { WakeyEvent ->
            TimetableEvent.fromJson(WakeyEvent)
        } ?: emptyList()
    }

    override suspend fun refreshTimetable(): Result<List<TimetableEvent>> {
        val result = timetableClient.fetch()
        return when (result) {
            is TimetableClient.Result.Success -> {
                datastore.saveTimetable(result.response)
                val events = result.response.events.map { WakeyEvent ->
                    TimetableEvent.fromJson(WakeyEvent)
                }
                Result.success(events)
            }
            is TimetableClient.Result.Error -> {
                val events = datastore.data.timetableFlow().first()?.events?.map { WakeyEvent ->
                    TimetableEvent.fromJson(WakeyEvent)
                } ?: emptyList()
                Result.success(events)
            }
        }
    }
}
