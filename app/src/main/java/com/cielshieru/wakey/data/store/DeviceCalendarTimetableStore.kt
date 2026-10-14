package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.domain.model.TimetableEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

object DeviceCalendarTimetableStore {
    private val DEVICE_CALENDAR_EVENTS_KEY = stringPreferencesKey("last_device_calendar_events")

    suspend fun DataStore<Preferences>.saveDeviceCalendarEvents(events: List<TimetableEvent>) {
        edit { prefs ->
            prefs[DEVICE_CALENDAR_EVENTS_KEY] = JsonConfig.instance.encodeToString(events)
        }
    }

    fun Flow<Preferences>.deviceCalendarEventsFlow(): Flow<List<TimetableEvent>?> = map { prefs ->
        prefs[DEVICE_CALENDAR_EVENTS_KEY]?.let {
            JsonConfig.instance.decodeFromString<List<TimetableEvent>>(it)
        }
    }

    suspend fun DataStore<Preferences>.clearDeviceCalendarEvents() {
        edit { prefs ->
            prefs.remove(DEVICE_CALENDAR_EVENTS_KEY)
        }
    }
}
