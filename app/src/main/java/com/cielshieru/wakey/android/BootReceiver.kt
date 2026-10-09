package com.cielshieru.wakey.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cielshieru.wakey.worker.SyncWorker

/**
 * Restores alarm state after a device reboot (ADR-0007, boot recovery).
 *
 * Android clears all [android.app.AlarmManager] alarms on reboot. When the
 * system broadcasts `ACTION_BOOT_COMPLETED`, this receiver enqueues a
 * one-time [SyncWorker] under the unique name `"boot_sync"` with
 * [ExistingWorkPolicy.REPLACE] so that at most one boot recovery runs at a
 * time. The one-time sync executes the full sync pipeline (same as the
 * periodic sync) and re-arms the alarms from persisted config.
 *
 * Declared `exported="false"` in the manifest with the
 * `RECEIVE_BOOT_COMPLETED` permission (ADR-0007/0008): `BOOT_COMPLETED` is a
 * system broadcast that no external component can send.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(TAG_BOOT_SYNC)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                1L,
                java.util.concurrent.TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_BOOT_SYNC, ExistingWorkPolicy.REPLACE, workRequest)

        val periodicSync = PeriodicWorkRequestBuilder<SyncWorker>(
            12, java.util.concurrent.TimeUnit.HOURS,
            30, java.util.concurrent.TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "sync_work",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicSync
            )
    }

    companion object {
        const val TAG_BOOT_SYNC = "boot_sync"
        const val UNIQUE_BOOT_SYNC = "boot_sync"
    }
}
