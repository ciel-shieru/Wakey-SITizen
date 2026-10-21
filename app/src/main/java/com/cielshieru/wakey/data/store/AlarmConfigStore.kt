package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.BedtimeReminderMode
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.model.RingtonePlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

enum class NumericConfigField {
    ONLINE_OFFSET,
    CAMPUS_OFFSET,
    REPEAT_INTERVAL,
    MAX_REPEATS,
    SLEEP_DURATION,
    BEDTIME_OFFSET,
    EXTERNAL_BUFFER
}

object AlarmConfigStore {
    private val ONLINE_OFFSET = intPreferencesKey("online_offset_minutes")
    private val CAMPUS_OFFSET = intPreferencesKey("campus_offset_minutes")
    private val REPEAT_INTERVAL = intPreferencesKey("repeat_interval_minutes")
    private val MAX_REPEATS = intPreferencesKey("max_repeats")
    private val SLEEP_DURATION = intPreferencesKey("sleep_duration_hours")
    private val BEDTIME_OFFSET = intPreferencesKey("bedtime_offset_minutes")
    private val BEDTIME_REMINDER_MODE = stringPreferencesKey("bedtime_reminder_mode")
    private val BEDTIME_ABSOLUTE_TIME = stringPreferencesKey("bedtime_absolute_time")
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
            bedtimeReminderMode = if (prefs[BEDTIME_REMINDER_MODE] == "relative") BedtimeReminderMode.Relative else BedtimeReminderMode.Absolute,
            bedtimeReminderAbsoluteTime = prefs[BEDTIME_ABSOLUTE_TIME]?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: AlarmConfig.DEFAULT_BEDTIME_ABSOLUTE_TIME,
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

    fun Flow<Preferences>.explicitNumericConfigFieldsFlow(): Flow<Set<NumericConfigField>> = map { prefs ->
        buildSet {
            if (prefs.contains(ONLINE_OFFSET)) add(NumericConfigField.ONLINE_OFFSET)
            if (prefs.contains(CAMPUS_OFFSET)) add(NumericConfigField.CAMPUS_OFFSET)
            if (prefs.contains(REPEAT_INTERVAL)) add(NumericConfigField.REPEAT_INTERVAL)
            if (prefs.contains(MAX_REPEATS)) add(NumericConfigField.MAX_REPEATS)
            if (prefs.contains(SLEEP_DURATION)) add(NumericConfigField.SLEEP_DURATION)
            if (prefs.contains(BEDTIME_OFFSET)) add(NumericConfigField.BEDTIME_OFFSET)
            if (prefs.contains(BUFFER_MINUTES)) add(NumericConfigField.EXTERNAL_BUFFER)
        }
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

    suspend fun DataStore<Preferences>.saveConfig(
        config: AlarmConfig,
        explicitlyConfiguredNumericFields: Set<NumericConfigField> = NumericConfigField.entries.toSet()
    ) {
        edit { prefs ->
            if (NumericConfigField.ONLINE_OFFSET in explicitlyConfiguredNumericFields) {
                prefs[ONLINE_OFFSET] = config.onlineOffsetMinutes
            } else {
                prefs.remove(ONLINE_OFFSET)
            }
            if (NumericConfigField.CAMPUS_OFFSET in explicitlyConfiguredNumericFields) {
                prefs[CAMPUS_OFFSET] = config.campusOffsetMinutes
            } else {
                prefs.remove(CAMPUS_OFFSET)
            }
            if (NumericConfigField.REPEAT_INTERVAL in explicitlyConfiguredNumericFields) {
                prefs[REPEAT_INTERVAL] = config.repeatIntervalMinutes
            } else {
                prefs.remove(REPEAT_INTERVAL)
            }
            if (NumericConfigField.MAX_REPEATS in explicitlyConfiguredNumericFields) {
                prefs[MAX_REPEATS] = config.maxRepeats
            } else {
                prefs.remove(MAX_REPEATS)
            }
            if (NumericConfigField.SLEEP_DURATION in explicitlyConfiguredNumericFields) {
                prefs[SLEEP_DURATION] = config.targetSleepDurationHours
            } else {
                prefs.remove(SLEEP_DURATION)
            }
            if (NumericConfigField.BEDTIME_OFFSET in explicitlyConfiguredNumericFields) {
                prefs[BEDTIME_OFFSET] = config.bedtimeReminderOffsetMinutes
            } else {
                prefs.remove(BEDTIME_OFFSET)
            }
            prefs[BEDTIME_REMINDER_MODE] = when (config.bedtimeReminderMode) {
                BedtimeReminderMode.Relative -> "relative"
                BedtimeReminderMode.Absolute -> "absolute"
            }
            prefs[BEDTIME_ABSOLUTE_TIME] = config.bedtimeReminderAbsoluteTime.toString()
            if (NumericConfigField.EXTERNAL_BUFFER in explicitlyConfiguredNumericFields) {
                prefs[BUFFER_MINUTES] = config.externalEventBufferMinutes
            } else {
                prefs.remove(BUFFER_MINUTES)
            }
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
