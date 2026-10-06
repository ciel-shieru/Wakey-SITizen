package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.model.OverrideType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object OverrideStore {
    private val OVERRIDES_KEY = stringPreferencesKey("manual_overrides")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun DataStore<Preferences>.saveOverrides(overrides: List<ManualOverride>) {
        edit { prefs ->
            prefs[OVERRIDES_KEY] = json.encodeToString(overrides)
        }
    }

    fun Flow<Preferences>.overridesFlow(): Flow<List<ManualOverride>> = map { prefs ->
        prefs[OVERRIDES_KEY]?.let {
            runCatching { json.decodeFromString<List<ManualOverride>>(it) }.getOrNull() ?: emptyList()
        } ?: emptyList()
    }

    suspend fun DataStore<Preferences>.clearExpired(nowMillis: Long) {
        edit { prefs ->
            val current = prefs[OVERRIDES_KEY]?.let {
                runCatching { json.decodeFromString<List<ManualOverride>>(it) }.getOrNull()
            } ?: emptyList()
            val filtered = current.filter { it.expiresAt.toEpochMilli() > nowMillis }
            prefs[OVERRIDES_KEY] = json.encodeToString(filtered)
        }
    }
}
