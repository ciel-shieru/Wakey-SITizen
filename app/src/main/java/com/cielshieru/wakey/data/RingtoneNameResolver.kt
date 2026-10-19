package com.cielshieru.wakey.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

open class RingtoneNameResolver(
    private val context: android.content.Context?
) {
    open fun resolveName(uriString: String): String {
        val uri = Uri.parse(uriString) ?: return "Unknown"
        return resolveName(uri)
    }

    open fun resolveName(uri: Uri): String {
        return try {
            val ctx = context ?: return uri.lastPathSegment ?: "Unknown"
            val ringtoneManager = RingtoneManager(ctx)
            val cursor = ringtoneManager.cursor
            var name = "Unknown"
            var titleColumn = -1
            var dataColumn = -1
            while (cursor.moveToNext()) {
                for (i in 0 until cursor.columnCount) {
                    when {
                        cursor.getColumnName(i).contains("title", ignoreCase = true) -> titleColumn = i
                        cursor.getColumnName(i).contains("data", ignoreCase = true) -> dataColumn = i
                    }
                }
                if (dataColumn >= 0) {
                    val ringtoneUri = Uri.parse(cursor.getString(dataColumn))
                    if (ringtoneUri == uri) {
                        if (titleColumn >= 0) {
                            name = cursor.getString(titleColumn)
                        }
                        break
                    }
                }
            }
            cursor.close()
            if (name.isNullOrEmpty() || name == "Unknown") {
                uri.lastPathSegment ?: "Unknown"
            } else {
                name
            }
        } catch (e: Exception) {
            uri.lastPathSegment ?: "Unknown"
        }
    }
}
