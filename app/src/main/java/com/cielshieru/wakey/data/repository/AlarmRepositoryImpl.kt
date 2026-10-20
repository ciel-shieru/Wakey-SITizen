package com.cielshieru.wakey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.android.AlarmService
import com.cielshieru.wakey.data.store.AlarmConfigStore.configFlow
import com.cielshieru.wakey.data.store.AlarmConfigStore.saveConfig
import com.cielshieru.wakey.data.store.AlarmStore.saveSchedules
import com.cielshieru.wakey.data.store.AlarmStore.removeScheduleForDay
import com.cielshieru.wakey.data.store.AlarmDismissalStore.dismissAlarmsForDay as persistDismissedDay
import com.cielshieru.wakey.data.store.DebugConfigStore.debugConfigFlow
import com.cielshieru.wakey.data.store.DebugConfigStore.saveDebugConfig
import com.cielshieru.wakey.data.store.AlarmStore.schedulesFlow
import com.cielshieru.wakey.data.store.OverrideStore.overridesFlow
import com.cielshieru.wakey.data.store.OverrideStore.saveOverrides
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.DebugConfig
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.port.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class AlarmRepositoryImpl(
    private val datastore: DataStore<Preferences>,
    private val alarmService: AlarmService
) : AlarmRepository {

    private val LAST_SYNC_TIME_KEY = stringPreferencesKey("last_sync_time")

    override val config: Flow<AlarmConfig> = datastore.data.configFlow()

    override val lastSyncTime: Flow<Long?> = datastore.data.map { it[LAST_SYNC_TIME_KEY]?.toLongOrNull() }

    override suspend fun saveConfig(config: AlarmConfig) {
        datastore.saveConfig(config)
    }

    override val overrides: Flow<List<ManualOverride>> = datastore.data.overridesFlow()

    override val schedules: Flow<List<AlarmSchedule>?> = datastore.data.schedulesFlow()

    override suspend fun saveSchedules(schedules: List<AlarmSchedule>) {
        datastore.saveSchedules(schedules)
    }

    override suspend fun saveOverride(override: ManualOverride) {
        val current = overrides.first()
        val updated = current.filter { it.expiresAt <= Instant.now() } + listOf(override)
        datastore.saveOverrides(updated)
    }

    override suspend fun clearExpiredOverrides(): Int {
        val now = Instant.now().toEpochMilli()
        val current = overrides.first()
        val expired = current.count { it.expiresAt.toEpochMilli() <= now }
        if (expired > 0) {
            val filtered = current.filter { it.expiresAt.toEpochMilli() > now }
            datastore.saveOverrides(filtered)
        }
        return expired
    }

    override suspend fun clearAllOverrides() {
        datastore.saveOverrides(emptyList())
    }

    override val debugConfig: Flow<DebugConfig> = datastore.data.debugConfigFlow()

    override suspend fun saveDebugConfig(config: DebugConfig) {
        datastore.saveDebugConfig(config)
    }

    override suspend fun cancelAlarm(requestCode: Int) {
        alarmService.cancelAlarmByRequestCode(requestCode)
    }

    override suspend fun dismissAlarmsForDay(day: LocalDate) {
        datastore.persistDismissedDay(day)
        datastore.removeScheduleForDay(day)
        alarmService.cancelAlarmsForDay(day)
        alarmService.stopCurrentAlarm()
    }
}
