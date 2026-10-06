package com.cielshieru.wakey.usecase

import androidx.work.*
import com.cielshieru.wakey.worker.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SyncUseCase @Inject constructor(
    private val workManager: WorkManager
) {
    fun schedulePeriodicSync() {
        val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            12, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "sync_work",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelSync() {
        workManager.cancelAllWorkByTag("sync_work")
    }
}
