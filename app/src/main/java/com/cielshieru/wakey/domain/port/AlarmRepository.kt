package com.cielshieru.wakey.domain.port

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.DebugConfig
import com.cielshieru.wakey.domain.model.ManualOverride
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    val config: Flow<AlarmConfig>
    suspend fun saveConfig(config: AlarmConfig)
    val overrides: Flow<List<ManualOverride>>
    suspend fun saveOverride(override: ManualOverride)
    val schedules: Flow<List<AlarmSchedule>?>
    val lastSyncTime: Flow<Long?>
    suspend fun saveSchedules(schedules: List<AlarmSchedule>)
    suspend fun clearExpiredOverrides(): Int
    suspend fun clearAllOverrides()
    val debugConfig: Flow<DebugConfig>
    suspend fun saveDebugConfig(config: DebugConfig)
}
