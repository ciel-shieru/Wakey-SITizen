package com.cielshieru.wakey.usecase

import androidx.work.*
import com.cielshieru.wakey.worker.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SyncUseCase @Inject constructor(
    private val workManager: WorkManager
) {
    fun schedulePeriodicSync(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
        val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            12, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "sync_work",
            policy,
            request
        )
    }

    companion object {
        fun schedulePeriodicSync(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                12, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(
                "sync_work",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun syncImmediately() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .build()
        workManager.enqueueUniqueWork(
            "sync_work",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelSync() {
        workManager.cancelUniqueWork("sync_work")
    }
}
