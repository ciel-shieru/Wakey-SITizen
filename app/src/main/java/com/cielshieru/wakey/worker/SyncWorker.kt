package com.cielshieru.wakey.worker

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import com.cielshieru.wakey.android.AlarmService
import com.cielshieru.wakey.data.network.TimetableClient
import com.cielshieru.wakey.data.store.AlarmConfigStore.configFlow
import com.cielshieru.wakey.data.store.AlarmDismissalStore.clearDismissedAlarmDaysBefore
import com.cielshieru.wakey.data.store.AlarmDismissalStore.dismissedAlarmDaysFlow
import com.cielshieru.wakey.data.store.DebugConfigStore.debugConfigFlow
import com.cielshieru.wakey.data.store.AlarmStore.saveSchedules
import com.cielshieru.wakey.data.store.AlarmStore.schedulesFlow
import com.cielshieru.wakey.data.store.OverrideStore.overridesFlow
import com.cielshieru.wakey.data.store.OverrideStore.saveOverrides
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.clearDeviceCalendarEvents
import com.cielshieru.wakey.data.store.DeviceCalendarTimetableStore.saveDeviceCalendarEvents
import com.cielshieru.wakey.data.store.TimetableStore.saveTimetable
import com.cielshieru.wakey.data.store.TimetableStore.timetableFlow
import com.cielshieru.wakey.domain.AdjustForExternalEvents
import com.cielshieru.wakey.domain.DeviceCalendarEventMapper
import com.cielshieru.wakey.domain.AlarmComputer
import com.cielshieru.wakey.domain.BedtimeComputer
import com.cielshieru.wakey.domain.DebugLogger
import com.cielshieru.wakey.domain.OverrideResolver
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.CalendarEventSource
import com.cielshieru.wakey.domain.port.Clock
import com.cielshieru.wakey.domain.port.ZoneProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Background sync worker executing the full alarm-management pipeline
 * (ADR-0007). Enqueued as a 12-hour periodic request (30-minute flex) and as
 * a one-time request on boot (ADR-0007, boot recovery).
 *
 * Pipeline:
 * 1. **Fetch timetable** — remote server JSON when discovery mode is
 *    [DiscoveryMode.RemoteServer] (stale-while-revalidate on failure); or
 *    device calendar events when discovery mode is
 *    [DiscoveryMode.DeviceCalendar].
 * 2. **Parse and filter** — drop x-site/BrightSpace events (`brightspace-`
 *    course-code prefix).
 * 3. **Compute alarm schedule** — first event per day, smart offset, repeat
 *    pattern (ADR-0003).
 * 4. **Adjust for external events** — when enabled, scan device calendars for
 *    pre-timetable events and shift the first alarm earlier (ADR-0005).
 * 5. **Apply manual overrides** — active override from DataStore
 *    (ADR-0006); expired overrides are cleared.
 * 6. **Reconcile alarms** — clear-then-set via [AlarmService] (ADR-0003),
 *    including days that dropped out of the new schedule so no stale alarms
 *    survive.
 * 7. **Compute bedtime reminder** — derive from the first alarm, set via
 *    [AlarmService] when in the future (ADR-0004).
 *
 * The resulting schedules are persisted to DataStore for the UI and for
 * boot recovery.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val timetableClient: TimetableClient,
    private val alarmService: AlarmService,
    private val calendarEventSource: CalendarEventSource,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val debugLogger: DebugLogger
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val zone = zoneProvider.zone
        val now = clock.now()
        try {
            val config = dataStore.data.configFlow().first()
            dataStore.clearDismissedAlarmDaysBefore(now.atZone(zone).toLocalDate())

            // Debug: read debug config and log diagnostic info.
            val debugConfig = dataStore.data.debugConfigFlow().first()
            val targetDateLocal = debugConfig.targetDateMillis?.let { millis ->
                Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            }
            if (debugConfig.debugModeEnabled) {
                Log.d(DEBUG_TAG, "Debug mode active, targetDate=$targetDateLocal")
                debugLogger.logDiscoveryMode(config)
            }

            // Step 1: fetch timetable (stale-while-revalidate on failure).
            val events = fetchTimetable(config, now, zone, targetDateLocal)

            if (debugConfig.debugModeEnabled && config.discoveryMode == DiscoveryMode.DeviceCalendar && targetDateLocal != null) {
                Log.d(DEBUG_TAG, "[Discovery] Fetching DeviceCalendar events for $targetDateLocal .. ${targetDateLocal.plusDays(6)}")
            }

            if (debugConfig.debugModeEnabled) {
                val source = when (config.discoveryMode) {
                    DiscoveryMode.RemoteServer -> "RemoteServer"
                    DiscoveryMode.DeviceCalendar -> "DeviceCalendar"
                }
                Log.d(DEBUG_TAG, "Fetch complete: ${events.size} event(s) from $source")
                events.forEach { event ->
                    debugLogger.logEvent(event, source)
                }
            }

            // Step 2: parse and filter — drop BrightSpace/x-site events.
            val filteredEvents = events.filterNot {
                it.courseCode.startsWith(BRIGHTSPACE_PREFIX, ignoreCase = true)
            }

            if (debugConfig.debugModeEnabled && filteredEvents.size != events.size) {
                Log.d(DEBUG_TAG, "After filtering: ${filteredEvents.size} non-BrightSpace event(s) " +
                    "(${events.size - filteredEvents.size} dropped)")
            }

            // Step 3: compute alarm schedule (ADR-0003).
            val schedules =
                AlarmComputer.computeSchedule(filteredEvents, config, clock, zoneProvider, targetDateLocal)

            if (debugConfig.debugModeEnabled) {
                Log.d(DEBUG_TAG, "AlarmComputer produced ${schedules.size} schedule(s)")
                schedules.forEach { schedule ->
                    val dayEvents = filteredEvents.filter {
                        it.dtstart.toLocalDate() == schedule.day
                    }
                    val firstEvt = dayEvents.minByOrNull { it.dtstart }
                    val offsetMinutes = when (firstEvt?.eventType) {
                        EventKind.ONLINE -> config.onlineOffsetMinutes
                        EventKind.CAMPUS -> config.campusOffsetMinutes
                        else -> 0
                    }
                    val dummyEvent = firstEvt ?: TimetableEvent(
                        courseCode = "?", title = "?", summary = "",
                        dtstart = java.time.LocalDateTime.now(),
                        dtend = java.time.LocalDateTime.now(),
                        location = "", eventType = EventKind.CAMPUS
                    )
                    debugLogger.logAlarmCalculation(
                        schedule.day, dummyEvent,
                        offsetMinutes, schedule.firstAlarmTime!!,
                        config.repeatIntervalMinutes, config.maxRepeats
                    )
                    debugLogger.logRepeatGeneration(schedule.alarms.size, config.repeatIntervalMinutes, config.maxRepeats)
                }
            }

            // Step 4: adjust for external events (ADR-0005), when enabled.
            val adjustedSchedules = if (config.externalEventEnabled) {
                schedules.map { schedule ->
                    // Include the previous calendar day because the supported buffer
                    // can span midnight (up to 12 hours). Deduplicate instances that
                    // overlap both date queries.
                    val externalEvents = listOf(schedule.day.minusDays(1), schedule.day)
                        .flatMap { date ->
                            calendarEventSource.getEventsForDate(date, config.externalEventCalendarIds)
                        }
                        .distinctBy { event ->
                            listOf(event.calendarId, event.title, event.dtstart, event.dtend)
                        }
                    AdjustForExternalEvents.adjust(
                        schedule,
                        externalEvents,
                        config.externalEventBufferMinutes,
                        zone
                    )
                }
            } else {
                schedules
            }

            if (debugConfig.debugModeEnabled && config.externalEventEnabled) {
                Log.d(DEBUG_TAG, "External events adjustment applied: ${adjustedSchedules.size} schedule(s)")
            }

            // Step 5: apply manual overrides (ADR-0006); clear expired ones.
            val overrides = dataStore.data.overridesFlow().first()
            val activeOverrides = OverrideResolver.clearExpired(overrides, now)
            if (activeOverrides.size != overrides.size) {
                dataStore.saveOverrides(activeOverrides)
            }
            if (debugConfig.debugModeEnabled) {
                Log.d(DEBUG_TAG, "Overrides: ${activeOverrides.size} active, ${overrides.size - activeOverrides.size} expired")
            }
            val resolvedSchedules =
                OverrideResolver.resolve(activeOverrides, now, zone)?.let { resolved ->
                    adjustedSchedules.map { schedule ->
                        OverrideResolver.applyOverride(resolved, schedule, zone)
                    }
                } ?: adjustedSchedules

            // A user dismissal is authoritative for that calendar day. Persisted
            // dismissals ensure a later periodic/boot sync cannot re-arm alarms.
            val dismissedDays = dataStore.data.dismissedAlarmDaysFlow().first()
            val finalSchedules = resolvedSchedules.filterNot { it.day in dismissedDays }

            // Step 6: reconcile alarms — clear-then-set (ADR-0003). Also
            // cancel days that had alarms before but are absent now, so no
            // stale alarms survive a timetable change.
            val previousDays =
                dataStore.data.schedulesFlow().first().orEmpty()
                    .map { it.day }
                    .toSet()
            val currentDays = finalSchedules.map { it.day }.toSet()
            if (debugConfig.debugModeEnabled) {
                (previousDays - currentDays).forEach { day ->
                    debugLogger.logAlarmCancelled(day)
                }
            }
            finalSchedules.forEach { schedule ->
                alarmService.clearAndSetAlarms(schedule.day, schedule.alarms)
                if (debugConfig.debugModeEnabled) {
                    schedule.alarms.forEach { alarm ->
                        val evtTitle = filteredEvents
                            .filter { it.dtstart.toLocalDate() == schedule.day }
                            .minByOrNull { it.dtstart }
                            ?.title
                        debugLogger.logAlarmSet(schedule.day, alarm, evtTitle)
                    }
                }
            }

            // Step 7: compute and set the bedtime reminder (ADR-0004).
            val targetDateInstant = targetDateLocal?.atStartOfDay(zone)?.toInstant() ?: now

            if (debugConfig.debugModeEnabled && config.bedtimeEnabled) {
                val firstAlarmTime = finalSchedules
                    .mapNotNull { it.firstAlarmTime }
                    .minOrNull()

                if (firstAlarmTime != null) {
                    val sleepDurationMinutes = config.targetSleepDurationHours.toLong() * 60L
                    val targetBedtime = firstAlarmTime.minus(sleepDurationMinutes, java.time.temporal.ChronoUnit.MINUTES)
                    val reminderTime = targetBedtime.minus(config.bedtimeReminderOffsetMinutes.toLong(), java.time.temporal.ChronoUnit.MINUTES)

                    debugLogger.logBedtimeCalculation(
                        firstAlarmTime,
                        config.targetSleepDurationHours,
                        targetBedtime,
                        config.bedtimeReminderOffsetMinutes,
                        reminderTime
                    )
                }
            }

            val bedtime = BedtimeComputer.compute(finalSchedules, config, now, zone)
            val shouldScheduleBedtimeReminder = bedtime?.triggerAt?.isAfter(now) == true
            if (debugConfig.debugModeEnabled) {
                if (bedtime == null) {
                    debugLogger.logBedtimeSkipped("no upcoming target bedtime")
                } else if (shouldScheduleBedtimeReminder) {
                    debugLogger.logBedtimeSet(bedtime)
                } else {
                    debugLogger.logBedtimeSkipped("reminder trigger has already passed")
                }
            }
            if (bedtime != null && shouldScheduleBedtimeReminder) {
                alarmService.setBedtimeReminder(
                    bedtime.triggerAt.toEpochMilli(),
                    bedtime.bedtimeAt.toEpochMilli()
                )
            } else {
                // Keep past reminders from firing immediately, even when the target
                // bedtime remains upcoming and can still be shown on the Home screen.
                alarmService.cancelBedtimeReminder()
            }

            // Persist schedules for the UI and for boot recovery.
            dataStore.saveSchedules(finalSchedules)
            saveLastSyncTime(now)

            Log.i(
                TAG,
                "Sync complete: ${finalSchedules.size} schedule(s), " +
                    "${finalSchedules.sumOf { it.alarms.size }} alarm(s)"
            )
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            // Let WorkManager retry with backoff (ADR-0007) before giving up.
            return Result.retry()
        }
    }

    /**
      * Step 1. When discovery mode is [DiscoveryMode.RemoteServer] the timetable is
      * fetched from the configured remote server URL; on failure the last-parsed events
      * from DataStore are returned (stale-while-revalidate, ADR-0007) — an
      * empty list if there is none.
      *
      * When discovery mode is [DiscoveryMode.DeviceCalendar] events are read
      * from the device calendar for the upcoming days needed by
      * [AlarmComputer].
      */
    private suspend fun fetchTimetable(
        config: AlarmConfig,
        now: java.time.Instant,
        zone: java.time.ZoneId,
        targetDateOverride: LocalDate? = null
    ): List<TimetableEvent> {
        val events = when (config.discoveryMode) {
            DiscoveryMode.RemoteServer -> {
                when (val result = timetableClient.fetch()) {
                    is TimetableClient.Result.Success -> {
                        dataStore.saveTimetable(result.response)
                        dataStore.clearDeviceCalendarEvents()
                        result.response.events.map { TimetableEvent.fromJson(it) }
                    }
                    is TimetableClient.Result.Error -> {
                        Log.w(TAG, "Timetable fetch failed; falling back to last-known data", result.cause)
                        dataStore.data.timetableFlow().first()
                            ?.events
                            ?.map { TimetableEvent.fromJson(it) }
                            ?: emptyList()
                    }
                }
            }
            DiscoveryMode.DeviceCalendar -> {
                val today = targetDateOverride ?: now.atZone(zone).toLocalDate()
                val daysToFetch = 7
                val externalEvents = (0 until daysToFetch).flatMap { offset ->
                    calendarEventSource.getEventsForDate(today.plusDays(offset.toLong()), config.selectedCalendarIds)
                }.filterNot { external ->
                    external.title.startsWith("brightspace-", ignoreCase = true)
                }
                DeviceCalendarEventMapper.map(
                    externalEvents,
                    config.onlineCalendarId,
                    config.campusCalendarId
                )
            }
        }

        if (config.discoveryMode == DiscoveryMode.DeviceCalendar) {
            dataStore.saveDeviceCalendarEvents(events)
        }

        return events
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val DEBUG_TAG = "WakeyDebug"
        private const val BRIGHTSPACE_PREFIX = "brightspace-"
        const val BACKOFF_DELAY_MILLIS = 5L * 60 * 1000L // 5 minutes for exponential backoff
        private val LAST_SYNC_TIME_KEY = stringPreferencesKey("last_sync_time")
    }

    suspend fun saveLastSyncTime(instant: java.time.Instant) {
        dataStore.edit { prefs ->
            prefs[LAST_SYNC_TIME_KEY] = instant.toEpochMilli().toString()
        }
    }
}
