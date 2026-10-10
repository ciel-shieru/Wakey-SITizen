package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.cielshieru.wakey.domain.model.DebugConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object DebugConfigStore {
    private val DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")
    internal val TARGET_DATE_MILLIS = longPreferencesKey("target_date_millis")

    fun Flow<Preferences>.debugConfigFlow(): Flow<DebugConfig> = map { prefs ->
        DebugConfig(
            debugModeEnabled = prefs[DEBUG_MODE_ENABLED] ?: false,
            targetDateMillis = prefs[TARGET_DATE_MILLIS]
        )
    }

    suspend fun DataStore<Preferences>.saveDebugConfig(config: DebugConfig) {
        edit { prefs ->
            prefs[DEBUG_MODE_ENABLED] = config.debugModeEnabled
            if (config.targetDateMillis != null) {
                prefs[TARGET_DATE_MILLIS] = config.targetDateMillis
            } else {
                prefs.remove(TARGET_DATE_MILLIS)
            }
        }
    }
}
