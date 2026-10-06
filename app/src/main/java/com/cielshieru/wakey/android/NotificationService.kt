package com.cielshieru.wakey.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cielshieru.wakey.ui.MainActivity

/**
 * Notification channels and posting for wake-up alarms and bedtime reminders
 * (ADR-0004/0008).
 *
 * Channels:
 * - [CHANNEL_ALARMS] (`"alarms"`): HIGH importance, vibrating — the user must
 *   hear the wake-up alarm.
 * - [CHANNEL_BEDTIME] (`"bedtime_reminders"`): DEFAULT importance — a gentle
 *   wind-down reminder the user can silence independently of the alarms.
 *
 * On API 33+ [NotificationManager.notify] throws [SecurityException] when the
 * runtime `POST_NOTIFICATIONS` permission is not granted, so every post is
 * guarded by [NotificationManager.areNotificationsEnabled]: if notifications
 * are off the post is skipped and the app continues to function
 * (ADR-0008, permission denial handling).
 */
object NotificationService {

    const val CHANNEL_ALARMS = "alarms"
    const val CHANNEL_BEDTIME = "bedtime_reminders"

    const val ALARM_NOTIFICATION_ID = 1
    const val BEDTIME_NOTIFICATION_ID = 2

    /**
     * Creates the notification channels. Safe to call on every app launch:
     * `createNotificationChannel` is a no-op for channels that already exist
     * (it only updates properties the user has not locked).
     */
    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARMS,
            "Wake-up Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for wake-up alarms"
            enableVibration(true)
        }

        val bedtimeChannel = NotificationChannel(
            CHANNEL_BEDTIME,
            "Bedtime Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications to remind you to go to bed"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(bedtimeChannel)
    }

    /** Posts the wake-up alarm notification on the [CHANNEL_ALARMS] channel. */
    fun showAlarmNotification(context: Context) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Time to wake up!")
            .setContentText("Your alarm is ringing now.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))

        notifyIfEnabled(context, ALARM_NOTIFICATION_ID, builder)
    }

    /** Posts the bedtime reminder notification on the [CHANNEL_BEDTIME] channel. */
    fun showBedtimeNotification(context: Context) {
        val builder = NotificationCompat.Builder(context, CHANNEL_BEDTIME)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("Bedtime Reminder")
            .setContentText("Time to wind down and prepare for sleep.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))

        notifyIfEnabled(context, BEDTIME_NOTIFICATION_ID, builder)
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifyIfEnabled(
        context: Context,
        notificationId: Int,
        builder: NotificationCompat.Builder
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            Log.w(
                TAG,
                "Notifications are disabled; skipping notification $notificationId (ADR-0008)"
            )
            return
        }
        manager.notify(notificationId, builder.build())
    }

    private const val TAG = "NotificationService"
}
