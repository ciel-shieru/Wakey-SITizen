package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.DebugConfig
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.port.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAlarmRepository : AlarmRepository {
    private val _config = MutableStateFlow(AlarmConfig())
    override val config: Flow<AlarmConfig> = _config.asStateFlow()

    private val _overrides = MutableStateFlow<List<ManualOverride>>(emptyList())
    override val overrides: Flow<List<ManualOverride>> = _overrides.asStateFlow()

    private val _schedules = MutableStateFlow<List<AlarmSchedule>?>(null)
    override val schedules: Flow<List<AlarmSchedule>?> = _schedules.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    override val lastSyncTime: Flow<Long?> = _lastSyncTime

    var lastSyncTimeMillis: Long?
        get() = _lastSyncTime.value
        set(value) {
            _lastSyncTime.value = value
        }

    private val _debugConfig = MutableStateFlow(DebugConfig())
    override val debugConfig: Flow<DebugConfig> = _debugConfig.asStateFlow()

    var savedConfig: AlarmConfig? = null
    var savedOverrides: List<ManualOverride>? = null
    var savedSchedules: List<AlarmSchedule>? = null
    var savedDebugConfig: DebugConfig? = null

    override suspend fun saveConfig(config: AlarmConfig) {
        savedConfig = config
        _config.value = config
    }

    override suspend fun saveOverride(override: ManualOverride) {
        val current = _overrides.value.toMutableList()
        current.add(override)
        _overrides.value = current
    }

    override suspend fun saveSchedules(schedules: List<AlarmSchedule>) {
        savedSchedules = schedules
        _schedules.value = schedules
    }

    override suspend fun clearExpiredOverrides(): Int {
        val now = java.time.Instant.now()
        val current = _overrides.value
        val expired = current.count { it.expiresAt <= now }
        if (expired > 0) {
            _overrides.value = current.filter { it.expiresAt > now }
        }
        return expired
    }

    var clearedAllOverrides = false

    override suspend fun clearAllOverrides() {
        clearedAllOverrides = true
        _overrides.value = emptyList()
    }

    val cancelledRequestCodes = mutableListOf<Int>()

    override suspend fun cancelAlarm(requestCode: Int) {
        cancelledRequestCodes.add(requestCode)
    }

    override suspend fun saveDebugConfig(config: DebugConfig) {
        savedDebugConfig = config
        _debugConfig.value = config
    }
}
