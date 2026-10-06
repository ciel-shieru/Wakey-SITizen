package com.cielshieru.wakey.domain.port

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.ManualOverride
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    val config: Flow<AlarmConfig>
    suspend fun saveConfig(config: AlarmConfig)
    val overrides: Flow<List<ManualOverride>>
    suspend fun saveOverride(override: ManualOverride)
    suspend fun clearExpiredOverrides(): Int
}
