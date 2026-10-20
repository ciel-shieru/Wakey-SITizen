package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.RingtonePlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

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
    private val REMOTE_SERVER_LEGACY = stringPreferencesKey("base_url")
    private val REMOTE_SERVER_URL = stringPreferencesKey("remote_server_url")
    private val DISCOVERY_MODE = stringPreferencesKey("discovery_mode")
    private val SELECTED_CALENDAR_IDS = stringPreferencesKey("selected_calendar_ids")
    private val ONLINE_CALENDAR_ID = stringPreferencesKey("online_calendar_id")
    private val CAMPUS_CALENDAR_ID = stringPreferencesKey("campus_calendar_id")
    private val EXTERNAL_EVENT_CALENDAR_IDS = stringPreferencesKey("external_event_calendar_ids")
    private val RINGTONE_PLAYLIST = stringPreferencesKey("ringtone_playlist")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun Flow<Preferences>.configFlow(): Flow<AlarmConfig> = map { prefs ->
        val legacyCalendarIds = prefs[SELECTED_CALENDAR_IDS]?.split(",")?.filter { it.isNotBlank() }
            ?: emptyList()
        val onlineCalendarId = if (prefs.contains(ONLINE_CALENDAR_ID)) {
            prefs[ONLINE_CALENDAR_ID]?.takeIf { it.isNotBlank() }
        } else {
            legacyCalendarIds.getOrNull(0)
        }
        val campusCalendarId = if (prefs.contains(CAMPUS_CALENDAR_ID)) {
            prefs[CAMPUS_CALENDAR_ID]?.takeIf { it.isNotBlank() }
        } else {
            legacyCalendarIds.getOrNull(1)
        }

        AlarmConfig(
            onlineOffsetMinutes = prefs[ONLINE_OFFSET] ?: AlarmConfig.DEFAULT_ONLINE_OFFSET,
            campusOffsetMinutes = prefs[CAMPUS_OFFSET] ?: AlarmConfig.DEFAULT_CAMPUS_OFFSET,
            repeatIntervalMinutes = prefs[REPEAT_INTERVAL] ?: AlarmConfig.DEFAULT_REPEAT_INTERVAL,
            maxRepeats = prefs[MAX_REPEATS] ?: AlarmConfig.DEFAULT_MAX_REPEATS,
            targetSleepDurationHours = prefs[SLEEP_DURATION] ?: AlarmConfig.DEFAULT_SLEEP_DURATION,
            bedtimeReminderOffsetMinutes = prefs[BEDTIME_OFFSET] ?: AlarmConfig.DEFAULT_BEDTIME_OFFSET,
            externalEventBufferMinutes = prefs[BUFFER_MINUTES] ?: AlarmConfig.DEFAULT_BUFFER_MINUTES,
            externalEventEnabled = prefs[EXTERNAL_EVENTS_ENABLED] ?: false,
            externalEventCalendarIds = prefs[EXTERNAL_EVENT_CALENDAR_IDS]?.split(",")?.filter { it.isNotBlank() }
                ?: emptyList(),
            selectedCalendarIds = legacyCalendarIds,
            onlineCalendarId = onlineCalendarId,
            campusCalendarId = campusCalendarId,
            bedtimeEnabled = prefs[BEDTIME_ENABLED] ?: false,
            remoteServerUrl = prefs[REMOTE_SERVER_LEGACY] ?: prefs[REMOTE_SERVER_URL] ?: AlarmConfig.DEFAULT_REMOTE_SERVER_URL,
            discoveryMode = parseDiscoveryMode(prefs[DISCOVERY_MODE]),
            ringtonePlaylist = parseRingtonePlaylist(prefs[RINGTONE_PLAYLIST])
        )
    }

    private fun parseDiscoveryMode(value: String?): DiscoveryMode {
        return when (value) {
            "device_calendar" -> DiscoveryMode.DeviceCalendar
            else -> DiscoveryMode.RemoteServer
        }
    }

    private fun parseRingtonePlaylist(value: String?): RingtonePlaylist {
        return if (value.isNullOrEmpty()) {
            RingtonePlaylist.DEFAULT
        } else {
            runCatching {
                json.decodeFromString<RingtonePlaylist>(value)
            }.getOrNull() ?: RingtonePlaylist.DEFAULT
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
            prefs[EXTERNAL_EVENT_CALENDAR_IDS] = config.externalEventCalendarIds.joinToString(",")
            prefs[SELECTED_CALENDAR_IDS] = config.selectedCalendarIds.joinToString(",")
            prefs[ONLINE_CALENDAR_ID] = config.onlineCalendarId.orEmpty()
            prefs[CAMPUS_CALENDAR_ID] = config.campusCalendarId.orEmpty()
            prefs[BEDTIME_ENABLED] = config.bedtimeEnabled
            prefs[REMOTE_SERVER_URL] = config.remoteServerUrl
            prefs[DISCOVERY_MODE] = when (config.discoveryMode) {
                DiscoveryMode.DeviceCalendar -> "device_calendar"
                DiscoveryMode.RemoteServer -> "remote_server"
            }
            prefs[RINGTONE_PLAYLIST] = json.encodeToString(RingtonePlaylist.serializer(), config.ringtonePlaylist)
        }
    }
}
