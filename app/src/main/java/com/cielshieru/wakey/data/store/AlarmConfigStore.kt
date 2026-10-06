package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.domain.model.AlarmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object AlarmConfigStore {
    private val ONLINE_OFFSET = intPreferencesKey("online_offset_minutes")
    private val CAMPUS_OFFSET = intPreferencesKey("campus_offset_minutes")
    private val REPEAT_INTERVAL = intPreferencesKey("repeat_interval_minutes")
    private val MAX_REPEATS = intPreferencesKey("max_repeats")
    private val SLEEP_DURATION = intPreferencesKey("sleep_duration_hours")
    private val BEDTIME_OFFSET = intPreferencesKey("bedtime_offset_minutes")
    private val BUFFER_MINUTES = intPreferencesKey("buffer_minutes")
    private val EXTERNAL_EVENTS_ENABLED = booleanPreferencesKey("external_events_enabled")
    private val BEDTIME_ENABLED = booleanPreferencesKey("bedtime_enabled")

    fun Flow<Preferences>.configFlow(): Flow<AlarmConfig> = map { prefs ->
        AlarmConfig(
            onlineOffsetMinutes = prefs[ONLINE_OFFSET] ?: 60,
            campusOffsetMinutes = prefs[CAMPUS_OFFSET] ?: 90,
            repeatIntervalMinutes = prefs[REPEAT_INTERVAL] ?: 5,
            maxRepeats = prefs[MAX_REPEATS] ?: 6,
            targetSleepDurationHours = prefs[SLEEP_DURATION] ?: 8,
            bedtimeReminderOffsetMinutes = prefs[BEDTIME_OFFSET] ?: 30,
            externalEventBufferMinutes = prefs[BUFFER_MINUTES] ?: 30,
            externalEventEnabled = prefs[EXTERNAL_EVENTS_ENABLED] ?: false,
            bedtimeEnabled = prefs[BEDTIME_ENABLED] ?: false
        )
    }

    suspend fun DataStore<Preferences>.saveConfig(config: AlarmConfig) {
        edit { prefs ->
            prefs[ONLINE_OFFSET] = config.onlineOffsetMinutes
            prefs[CAMPUS_OFFSET] = config.campusOffsetMinutes
            prefs[REPEAT_INTERVAL] = config.repeatIntervalMinutes
            prefs[MAX_REPEATS] = config.maxRepeats
            prefs[SLEEP_DURATION] = config.targetSleepDurationHours
            prefs[BEDTIME_OFFSET] = config.bedtimeReminderOffsetMinutes
            prefs[BUFFER_MINUTES] = config.externalEventBufferMinutes
            prefs[EXTERNAL_EVENTS_ENABLED] = config.externalEventEnabled
            prefs[BEDTIME_ENABLED] = config.bedtimeEnabled
        }
    }
}
