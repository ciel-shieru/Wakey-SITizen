package com.cielshieru.wakey.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cielshieru.wakey.domain.DebugLogger
import com.cielshieru.wakey.domain.model.AlarmSchedule
import java.time.Instant
import java.time.LocalDate

/**
 * AlarmManager wrapper implementing the alarm scheduling policy (ADR-0003) and
 * bedtime reminder delivery (ADR-0004).
 *
 * Policy summary:
 * - Precise delivery via [AlarmManager.setExactAndAllowWhileIdle] (works in Doze mode;
 *   requires `USE_EXACT_ALARM`, declared in the manifest, auto-granted on API 33+).
 * - Each wake-up alarm's `requestCode` is `day.hashCode() * 1000 + index`, collision-free
 *   within the [MAX_ALARMS_PER_DAY] cap; the bedtime reminder uses a reserved code.
 * - Idempotent reconciliation via clear-then-set: [clearAndSetAlarms] cancels every
 *   pending alarm for the day before setting the freshly computed set, so no stale
 *   alarms accumulate across syncs.
 */
class AlarmService(
    private val context: Context,
    private val debugLogger: DebugLogger
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    companion object {
        const val ACTION_WAKE_UP_ALARM = "com.cielshieru.wakey.ACTION_WAKE_UP_ALARM"
        const val ACTION_BEDTIME_REMINDER = "com.cielshieru.wakey.ACTION_BEDTIME_REMINDER"
        const val ACTION_DISMISS_REMAINING_ALARMS = "com.cielshieru.wakey.ACTION_DISMISS_REMAINING_ALARMS"
        const val EXTRA_ALARM_INDEX = "alarm_index"
        const val EXTRA_ALARM_DAY = "alarm_day"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val EXTRA_BEDTIME_AT_MILLIS = "bedtime_at_millis"

        /**
         * Hard cap on alarms per day (ADR-0003). With `maxRepeats <= 20` the
         * `day * 1000 + index` request-code range never collides between days.
         */
        const val MAX_ALARMS_PER_DAY = 20

        /**
         * Reserved request code for the single bedtime reminder (ADR-0004).
         * Day-derived codes are `|hashCode| * 1000 + index` (magnitude >= 10^9 for
         * current years) so 99_999 can never collide with them.
         */
        const val BEDTIME_REQUEST_CODE = 99_999
        const val DEBUG_TEST_ALARM_REQUEST_CODE = 99_998

        /**
         * Cancel the ordinary AlarmManager PendingIntents for one day without
         * requiring an injected AlarmService instance (used by notification actions).
         */
        fun cancelScheduledAlarmsForDay(context: Context, day: LocalDate) {
            val manager = context.getSystemService(AlarmManager::class.java)
            for (index in 0 until MAX_ALARMS_PER_DAY) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = ACTION_WAKE_UP_ALARM
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    AlarmSchedule.Alarm.computeRequestCode(day, index),
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                ) ?: continue
                manager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    /**
     * Schedules a single wake-up alarm at [alarm.triggerAt] via
     * [AlarmManager.setExactAndAllowWhileIdle].
     */
    fun setAlarm(alarm: AlarmSchedule.Alarm, day: LocalDate) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_WAKE_UP_ALARM
            putExtra(EXTRA_ALARM_INDEX, alarm.index)
            putExtra(EXTRA_ALARM_DAY, day.toString())
            alarm.ringtoneUri?.let { putExtra(EXTRA_RINGTONE_URI, it) }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarm.triggerAt.toEpochMilli(),
            pendingIntent
        )

        debugLogger.logAlarmSet(day, alarm, null)
    }

    /**
     * Schedules the bedtime reminder (ADR-0004) via
     * [AlarmManager.setExactAndAllowWhileIdle], targeting [AlarmReceiver] with
     * [ACTION_BEDTIME_REMINDER].
     *
     * Cancels any previously scheduled reminder first (cancel-then-set) so a
     * re-sync replaces it with the freshly computed time (ADR-0004, edge case 4).
     */
    fun setBedtimeReminder(triggerAtMillis: Long, bedtimeAtMillis: Long = triggerAtMillis) {
        cancelBedtimeReminder()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_BEDTIME_REMINDER
            putExtra(EXTRA_BEDTIME_AT_MILLIS, bedtimeAtMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            BEDTIME_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )

        debugLogger.logBedtimeReminderSet(triggerAtMillis)
    }

    /**
     * Cancels every pending wake-up alarm for [day] by iterating the
     * `day.hashCode() * 1000 + index` request codes (ADR-0003, idempotent
     * reconciliation).
     */
    fun cancelAlarmsForDay(day: LocalDate) = cancelScheduledAlarmsForDay(context, day)

    /** Stops any current looping playback and removes its notification. */
    fun stopCurrentAlarm() {
        context.stopService(Intent(context, AlarmPlaybackService::class.java))
        NotificationService.cancelAlarmNotification(context)
    }

    /** Cancels the pending bedtime reminder, if any. */
    fun cancelBedtimeReminder() {
        cancelPending(BEDTIME_REQUEST_CODE, ACTION_BEDTIME_REMINDER)
    }

    /**
     * Idempotent reconciliation for one day (ADR-0003): clears all pending
     * alarms for [day], then sets [alarms].
     *
     * Alarms whose trigger time has already passed are not re-armed, so an
     * evening re-sync does not fire a stale morning alarm immediately.
     */
    fun clearAndSetAlarms(day: LocalDate, alarms: List<AlarmSchedule.Alarm>) {
        cancelAlarmsForDay(day)
        val now = Instant.now()
        alarms.filter { it.triggerAt.isAfter(now) }
            .forEach { alarm -> setAlarm(alarm, day) }
        val activeCount = alarms.count { it.triggerAt.isAfter(now) }
        debugLogger.logAlarmClearAndSet(day, alarms.size, activeCount)
    }

    /**
     * Cancels a single pending wake-up alarm by [requestCode].
     *
     * The lookup intent must carry the same [action] the PendingIntent was
     * created with: a broadcast PendingIntent's identity is the triple
     * (requestCode, target package, action), so a mismatched action would
     * silently fail to find the pending intent.
     */
    fun cancelAlarmByRequestCode(requestCode: Int) {
        cancelPending(requestCode, ACTION_WAKE_UP_ALARM)
    }

    /**
     * Cancels a single pending alarm by [requestCode].
     *
     * The lookup intent must carry the same [action] the PendingIntent was
     * created with: a broadcast PendingIntent's identity is the triple
     * (requestCode, target package, action), so a mismatched action would
     * silently fail to find the pending intent.
     */
    private fun cancelPending(requestCode: Int, action: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
