package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.port.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSyncStatus : SyncStatus {
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: Flow<Boolean> = _isSyncing.asStateFlow()

    var isSyncingValue: Boolean
        get() = _isSyncing.value
        set(value) {
            _isSyncing.value = value
        }
}
