package com.cielshieru.wakey.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cielshieru.wakey.ui.MainActivity
import java.time.LocalDate

/**
 * Notification channels and posting for wake-up alarms and bedtime reminders.
 * Alarm audio is owned by [AlarmPlaybackService], which loops it until an
 * explicit stop or a subsequent scheduled alarm replaces it.
 */
object NotificationService {

    const val CHANNEL_ALARMS = "alarms"
    const val CHANNEL_BEDTIME = "bedtime_reminders"

    const val ALARM_NOTIFICATION_ID = 1
    const val BEDTIME_NOTIFICATION_ID = 2

    private const val STOP_ALARM_REQUEST_CODE = 20_001
    private const val DISMISS_ALARMS_REQUEST_CODE = 20_002

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARMS,
            "Wake-up Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Persistent notifications for ringing wake-up alarms"
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

    /**
     * Builds the foreground-service notification. It is ongoing and has an
     * explicit Stop alarm action; swiping it away does not stop the alarm.
     */
    fun buildAlarmNotification(
        context: Context,
        alarmIndex: Int? = null,
        alarmDay: String? = null,
        ringtoneUri: String? = null
    ): Notification {
        val contentText = if (alarmIndex != null && alarmDay != null) {
            "Alarm $alarmIndex for $alarmDay is ringing now."
        } else {
            "Your alarm is ringing now."
        }
        val stopIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            STOP_ALARM_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmService.ACTION_DISMISS_REMAINING_ALARMS
            putExtra(AlarmService.EXTRA_ALARM_DAY, alarmDay ?: LocalDate.now().toString())
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            DISMISS_ALARMS_REQUEST_CODE,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to wake up!")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(mainActivityPendingIntent(context))
            .addAction(android.R.drawable.ic_media_pause, "Stop alarm", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss remaining today", dismissPendingIntent)
            .build()
    }

    /** Posts an alarm notification when called outside the playback service. */
    fun showAlarmNotification(
        context: Context,
        alarmIndex: Int? = null,
        alarmDay: String? = null,
        ringtoneUri: String? = null
    ) {
        notifyIfEnabled(
            context,
            ALARM_NOTIFICATION_ID,
            buildAlarmNotification(context, alarmIndex, alarmDay, ringtoneUri)
        )
    }

    fun cancelAlarmNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ALARM_NOTIFICATION_ID)
    }

    fun showBedtimeNotification(context: Context) {
        val builder = NotificationCompat.Builder(context, CHANNEL_BEDTIME)
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setContentTitle("Bedtime Reminder")
            .setContentText("Time to wind down and prepare for sleep.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))

        notifyIfEnabled(context, BEDTIME_NOTIFICATION_ID, builder.build())
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
        notification: Notification
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled; skipping notification $notificationId")
            return
        }
        manager.notify(notificationId, notification)
    }

    private const val TAG = "NotificationService"
}
