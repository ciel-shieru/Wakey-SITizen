package com.cielshieru.wakey.fakes

import androidx.work.ExistingPeriodicWorkPolicy
import com.cielshieru.wakey.usecase.SyncOperations

class FakeSyncUseCase : SyncOperations {
    var scheduleCalled = false
    var syncCalled = false
    var cancelCalled = false

    override fun schedulePeriodicSync(policy: ExistingPeriodicWorkPolicy) {
        scheduleCalled = true
    }

    override fun syncImmediately(uniqueName: String) {
        syncCalled = true
    }

    override fun cancelSync() {
        cancelCalled = true
    }
}
