package com.cielshieru.wakey.domain.port

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow

/**
 * Domain port for observing sync/work status.
 *
 * Abstracts WorkManager so the domain layer and ViewModels never depend
 * on the concrete WorkManager class — following the project convention of
 * keeping Android framework dependencies in the data/android layers.
 */
interface SyncStatus {
    /**
     * Flow of booleans indicating whether any sync-related work is running
     * or blocked. Emits `true` when at least one work with tag "sync_work"
     * is in [WorkInfo.State.RUNNING] or [WorkInfo.State.BLOCKED] state.
     */
    val isSyncing: Flow<Boolean>
}
