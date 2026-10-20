package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Persisted user dismissals prevent a routine sync from re-arming alarms that
 * the user explicitly dismissed for the current day. Older dates are pruned.
 */
object AlarmDismissalStore {
    private val DISMISSED_ALARM_DAYS = stringPreferencesKey("dismissed_alarm_days")

    fun Flow<Preferences>.dismissedAlarmDaysFlow(): Flow<Set<LocalDate>> = map { prefs ->
        parseDays(prefs[DISMISSED_ALARM_DAYS])
    }

    suspend fun DataStore<Preferences>.dismissAlarmsForDay(day: LocalDate) {
        edit { prefs ->
            prefs[DISMISSED_ALARM_DAYS] =
                (parseDays(prefs[DISMISSED_ALARM_DAYS]) + day).sorted().joinToString(",")
        }
    }

    suspend fun DataStore<Preferences>.clearDismissalForDay(day: LocalDate) {
        edit { prefs ->
            val retained = parseDays(prefs[DISMISSED_ALARM_DAYS]) - day
            if (retained.isEmpty()) {
                prefs.remove(DISMISSED_ALARM_DAYS)
            } else {
                prefs[DISMISSED_ALARM_DAYS] = retained.sorted().joinToString(",")
            }
        }
    }

    suspend fun DataStore<Preferences>.clearDismissedAlarmDaysBefore(today: LocalDate) {
        edit { prefs ->
            val retained = parseDays(prefs[DISMISSED_ALARM_DAYS]).filter { !it.isBefore(today) }
            if (retained.isEmpty()) {
                prefs.remove(DISMISSED_ALARM_DAYS)
            } else {
                prefs[DISMISSED_ALARM_DAYS] = retained.sorted().joinToString(",")
            }
        }
    }

    private fun parseDays(value: String?): Set<LocalDate> =
        value.orEmpty()
            .split(",")
            .mapNotNull { token -> runCatching { LocalDate.parse(token) }.getOrNull() }
            .toSet()
}
