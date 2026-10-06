package com.cielshieru.wakey.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
            AlarmService.ACTION_WAKE_UP_ALARM ->
                NotificationService.showAlarmNotification(context)

            AlarmService.ACTION_BEDTIME_REMINDER ->
                NotificationService.showBedtimeNotification(context)
        }
    }
}
