package com.cielshieru.wakey.usecase

import androidx.work.*
import com.cielshieru.wakey.android.BootReceiver
import com.cielshieru.wakey.worker.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SyncUseCase @Inject constructor(
    private val workManager: WorkManager
) {

    companion object {
        const val UNIQUE_IMMEDIATE_SYNC = "sync_immediate"
        const val UNIQUE_PERIODIC_SYNC = "sync_periodic"

        fun schedulePeriodicSync(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                12, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun schedulePeriodicSync(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
        val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            12, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC,
            policy,
            request
        )
    }

    fun syncImmediately(uniqueName: String = UNIQUE_IMMEDIATE_SYNC) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()
        workManager.enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelSync() {
        workManager.cancelUniqueWork(UNIQUE_IMMEDIATE_SYNC)
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_SYNC)
        workManager.cancelUniqueWork(BootReceiver.UNIQUE_BOOT_SYNC)
    }
}
