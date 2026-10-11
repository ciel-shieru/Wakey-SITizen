package com.cielshieru.wakey.data.repository

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cielshieru.wakey.domain.port.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [SyncStatus] that wraps [WorkManager] to observe
 * the state of sync-related work requests tagged with "sync_work".
 */
class WorkManagerSyncStatus(
    private val workManager: WorkManager
) : SyncStatus {

    override val isSyncing: Flow<Boolean> =
        workManager.getWorkInfosByTagFlow("sync_work").map { workInfos ->
            workInfos.any {
                it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.BLOCKED
            }
        }
}
