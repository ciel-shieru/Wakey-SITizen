package com.cielshieru.wakey.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cielshieru.wakey.data.store.AlarmConfigStore.configFlow
import com.cielshieru.wakey.data.store.AlarmConfigStore.saveConfig
import com.cielshieru.wakey.data.store.OverrideStore.overridesFlow
import com.cielshieru.wakey.data.store.OverrideStore.saveOverrides
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.port.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant

class AlarmRepositoryImpl(
    private val datastore: DataStore<Preferences>
) : AlarmRepository {

    override val config: Flow<AlarmConfig> = datastore.data.configFlow()

    override suspend fun saveConfig(config: AlarmConfig) {
        datastore.saveConfig(config)
    }

    override val overrides: Flow<List<ManualOverride>> = datastore.data.overridesFlow()

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
}
