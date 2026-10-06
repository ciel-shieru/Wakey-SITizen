package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.data.model.WakeyResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TimetableStore {
    private val TIMETABLE_KEY = stringPreferencesKey("last_timetable")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun DataStore<Preferences>.saveTimetable(response: WakeyResponse) {
        edit { prefs ->
            prefs[TIMETABLE_KEY] = json.encodeToString(response)
        }
    }

    fun Flow<Preferences>.timetableFlow(): Flow<WakeyResponse?> = map { prefs ->
        prefs[TIMETABLE_KEY]?.let { json.decodeFromString<WakeyResponse>(it) }
    }
}
