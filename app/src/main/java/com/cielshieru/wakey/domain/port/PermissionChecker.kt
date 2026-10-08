package com.cielshieru.wakey.domain.port

import android.content.Context
import com.cielshieru.wakey.domain.model.PermissionEntry

interface PermissionChecker {
    fun checkNotificationsPermission(context: Context): Boolean
    fun checkCalendarPermission(context: Context): Boolean
    fun getAllPermissionEntries(context: Context): List<PermissionEntry>
}
