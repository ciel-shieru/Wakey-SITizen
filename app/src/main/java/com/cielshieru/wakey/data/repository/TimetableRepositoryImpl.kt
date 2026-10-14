package com.cielshieru.wakey.data.repository

import com.cielshieru.wakey.data.model.WakeyEvent
import com.cielshieru.wakey.data.network.TimetableClient
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.deviceCalendarEventsFlow
import com.cielshieru.wakey.data.store.TimetableStore.saveTimetable
import com.cielshieru.wakey.data.store.TimetableStore.timetableFlow
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class TimetableRepositoryImpl(
    private val timetableClient: TimetableClient,
    private val datastore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    private val configFlow: Flow<AlarmConfig>
) : TimetableRepository {

    override val eventsFlow: Flow<List<TimetableEvent>> = combine(
        configFlow,
        datastore.data.timetableFlow(),
        datastore.data.deviceCalendarEventsFlow()
    ) { config, remoteResponse, deviceEvents ->
        when (config.discoveryMode) {
            DiscoveryMode.RemoteServer -> {
                remoteResponse?.events?.map { WakeyEvent ->
                    TimetableEvent.fromJson(WakeyEvent)
                } ?: emptyList()
            }
            DiscoveryMode.DeviceCalendar -> {
                deviceEvents ?: emptyList()
            }
        }
    }

    override suspend fun getEventsForDate(date: LocalDate): List<TimetableEvent> {
        return getEventsForRange(date, date)
    }

    override suspend fun getEventsForRange(start: LocalDate, end: LocalDate): List<TimetableEvent> {
        val config = configFlow.first()
        return when (config.discoveryMode) {
            DiscoveryMode.RemoteServer -> {
                val response = datastore.data.timetableFlow().first()
                response?.events?.map { WakeyEvent ->
                    TimetableEvent.fromJson(WakeyEvent)
                } ?: emptyList()
            }
            DiscoveryMode.DeviceCalendar -> {
                val deviceEvents = datastore.data.deviceCalendarEventsFlow().first()
                deviceEvents ?: emptyList()
            }
        }
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
