package com.cielshieru.wakey.domain

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.BedtimeReminder
import com.cielshieru.wakey.domain.model.DebugConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.data.store.AlarmConfigStore.configFlow
import com.cielshieru.wakey.data.store.DebugConfigStore.debugConfigFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate

class DebugLogger(
    @Suppress("UNUSED_PARAMETER") private val context: Context?,
    private val debugModeEnabled: Boolean
) {
    private val TAG = "WakeyDebug"

    fun logDiscoveryMode(config: AlarmConfig) {
        if (!debugModeEnabled) return
        when (config.discoveryMode) {
            DiscoveryMode.RemoteServer -> {
                Log.d(TAG, "[Discovery] Mode=RemoteServer URL=${config.remoteServerUrl}")
            }
            DiscoveryMode.DeviceCalendar -> {
                val ids = config.selectedCalendarIds.ifEmpty { "<none>" }
                Log.d(TAG, "[Discovery] Mode=DeviceCalendar Calendars=$ids")
            }
        }
    }

    fun logEvent(event: TimetableEvent, source: String) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Event] Source=$source Name=\"${event.title}\" " +
            "Course=${event.courseCode} Kind=${event.eventType} " +
            "Start=${event.dtstart} Location=\"${event.location}\"")
    }

    fun logAlarmCalculation(day: LocalDate, firstEvent: TimetableEvent,
                            offsetMinutes: Int, firstAlarmTime: Instant,
                            repeatInterval: Int, maxRepeats: Int) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Calc] Day=$day FirstEvent=\"${firstEvent.title}\" " +
            "Kind=${firstEvent.eventType} Offset=${offsetMinutes}min " +
            "FirstAlarm=$firstAlarmTime RepeatInterval=${repeatInterval}min " +
            "MaxRepeats=$maxRepeats")
    }

    fun logRepeatGeneration(count: Int, repeatInterval: Int, maxRepeats: Int) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Calc] Generated $count repeat alarms " +
            "(interval=${repeatInterval}min, maxRepeats=$maxRepeats)")
    }

    fun logAlarmSet(day: LocalDate, alarm: AlarmSchedule.Alarm,
                    eventTitle: String?) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Alarm] Set day=$day number=${alarm.index + 1} " +
            "requestCode=${alarm.requestCode} triggerAt=${alarm.triggerAt} " +
            "event=\"$eventTitle\"")
    }

    fun logAlarmCancelled(day: LocalDate) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Alarm] Cancelled alarms for day=$day (no longer in schedule)")
    }

    fun logBedtimeCalculation(firstAlarmTime: Instant,
                              sleepDurationHours: Int,
                              targetBedtime: Instant,
                              reminderOffset: Int,
                              reminderTime: Instant) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Bedtime] FirstAlarm=$firstAlarmTime " +
            "SleepDuration=${sleepDurationHours}h " +
            "TargetBedtime=$targetBedtime " +
            "ReminderOffset=${reminderOffset}min " +
            "ReminderTime=$reminderTime")
    }

    fun logBedtimeSkipped(reason: String) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Bedtime] Skipped: $reason")
    }

    fun logBedtimeReminderSet(triggerAtMillis: Long) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Alarm] Bedtime reminder set triggerAt=$triggerAtMillis")
    }

    fun logAlarmClearAndSet(day: LocalDate, totalAlarms: Int, activeAlarms: Int) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Alarm] Cleared+Set day=$day: $totalAlarms alarm(s) total, $activeAlarms active (future)")
    }

    fun logBedtimeSet(reminder: BedtimeReminder) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Bedtime] Set triggerAt=${reminder.triggerAt} " +
            "message=\"${reminder.message}\"")
    }

    fun logFullConfig(config: AlarmConfig, debug: DebugConfig) {
        if (!debugModeEnabled) return
        Log.d(TAG, "[Config] ===== Full Configuration =====")
        logAlarmConfig(config)
        logDebugConfig(debug)
        Log.d(TAG, "[Config] ============================")
    }

    fun logFullConfigFromDataStore(datastore: DataStore<Preferences>) {
        if (!debugModeEnabled) return
        val config = runBlocking { datastore.data.configFlow().first() }
        val debug = runBlocking { datastore.data.debugConfigFlow().first() }
        logFullConfig(config, debug)
    }

    private fun logAlarmConfig(config: AlarmConfig) {
        Log.d(TAG, "[Config] AlarmConfig:")
        Log.d(TAG, "[Config]   onlineOffsetMinutes=${config.onlineOffsetMinutes}")
        Log.d(TAG, "[Config]   campusOffsetMinutes=${config.campusOffsetMinutes}")
        Log.d(TAG, "[Config]   repeatIntervalMinutes=${config.repeatIntervalMinutes}")
        Log.d(TAG, "[Config]   maxRepeats=${config.maxRepeats}")
        Log.d(TAG, "[Config]   targetSleepDurationHours=${config.targetSleepDurationHours}")
        Log.d(TAG, "[Config]   bedtimeReminderOffsetMinutes=${config.bedtimeReminderOffsetMinutes}")
        Log.d(TAG, "[Config]   externalEventBufferMinutes=${config.externalEventBufferMinutes}")
        Log.d(TAG, "[Config]   externalEventEnabled=${config.externalEventEnabled}")
        Log.d(TAG, "[Config]   selectedCalendarIds=${config.selectedCalendarIds}")
        Log.d(TAG, "[Config]   bedtimeEnabled=${config.bedtimeEnabled}")
        Log.d(TAG, "[Config]   remoteServerUrl=${config.remoteServerUrl}")
        Log.d(TAG, "[Config]   discoveryMode=${config.discoveryMode}")
    }

    private fun logDebugConfig(debug: DebugConfig) {
        Log.d(TAG, "[Config] DebugConfig:")
        Log.d(TAG, "[Config]   debugModeEnabled=${debug.debugModeEnabled}")
        Log.d(TAG, "[Config]   targetDateMillis=${debug.targetDateMillis}")
    }
}
