package com.cielshieru.wakey.usecase

import androidx.work.ExistingPeriodicWorkPolicy

interface SyncOperations {
    fun schedulePeriodicSync(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP)
    fun syncImmediately(uniqueName: String = SyncUseCase.UNIQUE_IMMEDIATE_SYNC)
    fun cancelSync()
}
