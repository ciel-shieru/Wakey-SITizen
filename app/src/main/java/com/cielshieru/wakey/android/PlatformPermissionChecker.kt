package com.cielshieru.wakey.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cielshieru.wakey.data.store.PermissionStore
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus
import com.cielshieru.wakey.domain.port.PermissionChecker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class PlatformPermissionChecker @Inject constructor(
    private val datastore: DataStore<Preferences>
) : PermissionChecker {
    override fun checkNotificationsPermission(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun checkCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun getAllPermissionEntries(context: Context): List<PermissionEntry> {
        val notificationsGranted = checkNotificationsPermission(context)
        val calendarGranted = checkCalendarPermission(context)
        val calendarDenied = runBlocking { datastore.data.first()[PermissionStore.CALENDAR_PERMISSION_DENIED_KEY] } ?: false

        return listOf(
            PermissionEntry(
                permission = "USE_EXACT_ALARM",
                featureName = "Wake-up alarms",
                status = PermissionStatus.Granted,
                justification = "Precise wake-up alarm delivery via AlarmManager",
                isRuntimePermission = false
            ),
            PermissionEntry(
                permission = "POST_NOTIFICATIONS",
                featureName = "Bedtime reminders",
                status = determineRuntimePermissionStatus(notificationsGranted),
                justification = "Bedtime reminder and alarm-fire notifications",
                isRuntimePermission = true
            ),
            PermissionEntry(
                permission = "RECEIVE_BOOT_COMPLETED",
                featureName = "Boot recovery",
                status = PermissionStatus.Granted,
                justification = "Restores alarms after device reboot",
                isRuntimePermission = false
            ),
            PermissionEntry(
                permission = "INTERNET",
                featureName = "Timetable fetch",
                status = PermissionStatus.Granted,
                justification = "Fetches timetable from configured SIT-ICS-Go server",
                isRuntimePermission = false
            ),
            PermissionEntry(
                permission = "READ_CALENDAR",
                featureName = "External events",
                status = determineCalendarPermissionStatus(calendarGranted, calendarDenied),
                justification = "Reads calendar events for external schedule adjustment",
                isRuntimePermission = true
            )
        )
    }

    private fun determineRuntimePermissionStatus(granted: Boolean): PermissionStatus {
        return if (granted) {
            PermissionStatus.Granted
        } else {
            PermissionStatus.Required
        }
    }

    private fun determineCalendarPermissionStatus(granted: Boolean, explicitlyDenied: Boolean): PermissionStatus {
        return if (granted) {
            PermissionStatus.Granted
        } else if (explicitlyDenied) {
            PermissionStatus.Denied
        } else {
            PermissionStatus.Required
        }
    }
}
