package com.cielshieru.wakey.android

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus
import com.cielshieru.wakey.domain.port.PermissionChecker

class PlatformPermissionChecker : PermissionChecker {
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
                status = determineRuntimePermissionStatus(calendarGranted),
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
}
