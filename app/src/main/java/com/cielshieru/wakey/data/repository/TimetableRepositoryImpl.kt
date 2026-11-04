package com.cielshieru.wakey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cielshieru.wakey.data.network.TimetableClient
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.deviceCalendarEventsFlow
import com.cielshieru.wakey.data.store.TimetableStore.saveTimetable
import com.cielshieru.wakey.data.store.TimetableStore.timetableFlow
import com.cielshieru.wakey.domain.DeviceCalendarEventMapper
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.CalendarEventSource
import com.cielshieru.wakey.domain.port.TimetableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TimetableRepositoryImpl(
    private val timetableClient: TimetableClient,
    private val datastore: DataStore<Preferences>,
    private val configFlow: Flow<AlarmConfig>,
    private val calendarEventSource: CalendarEventSource
) : TimetableRepository {

    override val eventsFlow: Flow<List<TimetableEvent>> = combine(
        configFlow,
        datastore.data.timetableFlow(),
        datastore.data.deviceCalendarEventsFlow()
    ) { config, remoteResponse, deviceEvents ->
        when (config.discoveryMode) {
            DiscoveryMode.RemoteServer -> {
                remoteResponse?.events?.map { event -> TimetableEvent.fromJson(event) } ?: emptyList()
            }
            DiscoveryMode.DeviceCalendar -> {
                deviceEvents ?: emptyList()
            }
        }
    }

    override suspend fun getEventsForDate(date: LocalDate): List<TimetableEvent> =
        getEventsForRange(date, date)

    override suspend fun getEventsForRange(start: LocalDate, end: LocalDate): List<TimetableEvent> {
        require(!end.isBefore(start)) { "The end date must not be before the start date." }
        val config = configFlow.first()
        val events = when (config.discoveryMode) {
            DiscoveryMode.RemoteServer -> {
                datastore.data.timetableFlow().first()?.events
                    ?.map { event -> TimetableEvent.fromJson(event) } ?: emptyList()
            }
            DiscoveryMode.DeviceCalendar -> {
                datastore.data.deviceCalendarEventsFlow().first() ?: emptyList()
            }
        }
        return events.filter { event ->
            val eventDate = event.dtstart.toLocalDate()
            !eventDate.isBefore(start) && !eventDate.isAfter(end)
        }
    }

    /**
     * Fetches a complete forecast window without changing the app's alarm timetable cache.
     * The configured remote endpoint accepts inclusive start/end dates, so remote
     * mode uses one HTTP request. Device-calendar mode performs one range query over
     * the calendars already selected for the timetable.
     */
    override suspend fun fetchEventsForRange(
        start: LocalDate,
        endInclusive: LocalDate
    ): Result<List<TimetableEvent>> {
        if (endInclusive.isBefore(start)) {
            return Result.failure(IllegalArgumentException("The end date must not be before the start date."))
        }
        val rangeDays = ChronoUnit.DAYS.between(start, endInclusive) + 1L
        if (rangeDays !in 1L..MAX_FORECAST_DAYS) {
            return Result.failure(
                IllegalArgumentException("Forecast ranges must contain between 1 and $MAX_FORECAST_DAYS days.")
            )
        }

        return try {
            val config = configFlow.first()
            val events = when (config.discoveryMode) {
                DiscoveryMode.RemoteServer -> fetchRemoteEventsForRange(start, endInclusive)
                DiscoveryMode.DeviceCalendar -> fetchDeviceCalendarEventsForRange(
                    start = start,
                    endInclusive = endInclusive,
                    config = config
                )
            }
            Result.success(events.filter { event ->
                val eventDate = event.dtstart.toLocalDate()
                !eventDate.isBefore(start) && !eventDate.isAfter(endInclusive)
            }.distinctBy { event ->
                listOf(event.courseCode, event.title, event.dtstart, event.dtend, event.location, event.eventType)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun fetchRemoteEventsForRange(
        start: LocalDate,
        endInclusive: LocalDate
    ): List<TimetableEvent> = when (val result = timetableClient.fetch(start, endInclusive)) {
        is TimetableClient.Result.Success ->
            result.response.events.map { event -> TimetableEvent.fromJson(event) }
        is TimetableClient.Result.Error -> throw result.cause
    }

    private suspend fun fetchDeviceCalendarEventsForRange(
        start: LocalDate,
        endInclusive: LocalDate,
        config: AlarmConfig
    ): List<TimetableEvent> {
        if (config.selectedCalendarIds.isEmpty()) {
            throw IllegalStateException("No timetable calendars are selected.")
        }

        val externalEvents = calendarEventSource.getTimetableEventsForRange(
            start = start,
            endInclusive = endInclusive,
            calendarIds = config.selectedCalendarIds
        ).filterNot { event ->
            event.title.startsWith(BRIGHTSPACE_PREFIX, ignoreCase = true)
        }

        return DeviceCalendarEventMapper.map(
            events = externalEvents,
            onlineCalendarId = config.onlineCalendarId,
            campusCalendarId = config.campusCalendarId
        )
    }

    override suspend fun refreshTimetable(): Result<List<TimetableEvent>> {
        val result = timetableClient.fetch()
        return when (result) {
            is TimetableClient.Result.Success -> {
                datastore.saveTimetable(result.response)
                val events = result.response.events.map { event -> TimetableEvent.fromJson(event) }
                Result.success(events)
            }
            is TimetableClient.Result.Error -> {
                val events = datastore.data.timetableFlow().first()?.events
                    ?.map { event -> TimetableEvent.fromJson(event) } ?: emptyList()
                Result.success(events)
            }
        }
    }

    private companion object {
        const val MAX_FORECAST_DAYS = 60L
        const val BRIGHTSPACE_PREFIX = "brightspace-"
    }
}
