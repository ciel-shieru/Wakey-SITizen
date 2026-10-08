package com.cielshieru.wakey.data.store

import androidx.datastore.preferences.core.booleanPreferencesKey

object PermissionStore {
    val CALENDAR_PERMISSION_DENIED_KEY = booleanPreferencesKey("calendar_permission_denied")
}
