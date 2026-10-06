package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.domain.model.AlarmSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AlarmStore {
    private val ALARM_SCHEDULES_KEY = stringPreferencesKey("alarm_schedules")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun DataStore<Preferences>.saveSchedules(schedules: List<AlarmSchedule>) {
        edit { prefs ->
            prefs[ALARM_SCHEDULES_KEY] = json.encodeToString(schedules)
        }
    }

    fun Flow<Preferences>.schedulesFlow(): Flow<List<AlarmSchedule>?> = map { prefs ->
        prefs[ALARM_SCHEDULES_KEY]?.let { json.decodeFromString<List<AlarmSchedule>>(it) }
    }
}
