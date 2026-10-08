package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
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
    private val BASE_URL = stringPreferencesKey("base_url")
    private val DISCOVERY_MODE = stringPreferencesKey("discovery_mode")
    private val SELECTED_CALENDAR_IDS = stringPreferencesKey("selected_calendar_ids")

    fun Flow<Preferences>.configFlow(): Flow<AlarmConfig> = map { prefs ->
        AlarmConfig(
            onlineOffsetMinutes = prefs[ONLINE_OFFSET] ?: AlarmConfig.DEFAULT_ONLINE_OFFSET,
            campusOffsetMinutes = prefs[CAMPUS_OFFSET] ?: AlarmConfig.DEFAULT_CAMPUS_OFFSET,
            repeatIntervalMinutes = prefs[REPEAT_INTERVAL] ?: AlarmConfig.DEFAULT_REPEAT_INTERVAL,
            maxRepeats = prefs[MAX_REPEATS] ?: AlarmConfig.DEFAULT_MAX_REPEATS,
            targetSleepDurationHours = prefs[SLEEP_DURATION] ?: AlarmConfig.DEFAULT_SLEEP_DURATION,
            bedtimeReminderOffsetMinutes = prefs[BEDTIME_OFFSET] ?: AlarmConfig.DEFAULT_BEDTIME_OFFSET,
            externalEventBufferMinutes = prefs[BUFFER_MINUTES] ?: AlarmConfig.DEFAULT_BUFFER_MINUTES,
            externalEventEnabled = prefs[EXTERNAL_EVENTS_ENABLED] ?: false,
            selectedCalendarIds = prefs[SELECTED_CALENDAR_IDS]?.split(",")?.filter { it.isNotBlank() }
                ?: emptyList(),
            bedtimeEnabled = prefs[BEDTIME_ENABLED] ?: false,
            baseUrl = prefs[BASE_URL] ?: AlarmConfig.DEFAULT_BASE_URL,
            discoveryMode = parseDiscoveryMode(prefs[DISCOVERY_MODE])
        )
    }

    private fun parseDiscoveryMode(value: String?): DiscoveryMode {
        return when (value) {
            "device_calendar" -> DiscoveryMode.DeviceCalendar
            else -> DiscoveryMode.BaseUrl
        }
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
            prefs[SELECTED_CALENDAR_IDS] = config.selectedCalendarIds.joinToString(",")
            prefs[BEDTIME_ENABLED] = config.bedtimeEnabled
            prefs[BASE_URL] = config.baseUrl
            prefs[DISCOVERY_MODE] = when (config.discoveryMode) {
                DiscoveryMode.BaseUrl -> "base_url"
                DiscoveryMode.DeviceCalendar -> "device_calendar"
            }
        }
    }
}
