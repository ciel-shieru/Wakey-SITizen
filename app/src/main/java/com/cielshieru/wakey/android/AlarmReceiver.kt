package com.cielshieru.wakey.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cielshieru.wakey.data.store.AlarmDismissalStore.dismissAlarmsForDay
import com.cielshieru.wakey.data.store.AlarmStore.removeScheduleForDay
import com.cielshieru.wakey.di.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Receives alarm-fire broadcasts from [AlarmManager] (ADR-0003/0004/0007).
 *
 * The system sends the `PendingIntent`s created by [AlarmService] to this
 * receiver:
 * - [AlarmService.ACTION_WAKE_UP_ALARM] → post a wake-up alarm notification.
 * - [AlarmService.ACTION_BEDTIME_REMINDER] → post a bedtime reminder notification.
 *
 * Declared `exported="false"` in the manifest: these broadcasts are sent by
 * the system to this app's own process, never by external components
 * (ADR-0007).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmService.ACTION_WAKE_UP_ALARM -> {
                val alarmIndex = intent.getIntExtra(AlarmService.EXTRA_ALARM_INDEX, -1)
                val alarmDay = intent.getStringExtra(AlarmService.EXTRA_ALARM_DAY)
                val ringtoneUri = intent.getStringExtra(AlarmService.EXTRA_RINGTONE_URI)
                AlarmPlaybackService.start(context, alarmIndex.takeIf { it >= 0 }, alarmDay, ringtoneUri)
            }

            AlarmService.ACTION_BEDTIME_REMINDER ->
                NotificationService.showBedtimeNotification(context)

            AlarmService.ACTION_DISMISS_REMAINING_ALARMS -> {
                val day = intent.getStringExtra(AlarmService.EXTRA_ALARM_DAY)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now()
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        context.dataStore.dismissAlarmsForDay(day)
                        context.dataStore.removeScheduleForDay(day)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist dismissed alarms for $day", e)
                    } finally {
                        // Always cancel actual PendingIntents and stop live playback, even if
                        // persistence fails; log above makes the sync-restoration risk visible.
                        AlarmService.cancelScheduledAlarmsForDay(context, day)
                        context.stopService(Intent(context, AlarmPlaybackService::class.java))
                        NotificationService.cancelAlarmNotification(context)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
