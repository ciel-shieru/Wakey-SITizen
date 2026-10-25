package com.cielshieru.wakey.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.SystemClock
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cielshieru.wakey.data.store.AlarmConfigStore.configFlow
import com.cielshieru.wakey.data.store.AlarmStore.schedulesFlow
import com.cielshieru.wakey.data.store.PebbleExportStore
import com.cielshieru.wakey.di.dataStore
import com.cielshieru.wakey.domain.PebbleExportSnapshotFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Opt-in, read-only IPC endpoint for JankTime2. Calls require the pairing token.
 * Only call() is supported; no URI-backed data table is exposed.
 */
class PebbleExportProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != METHOD_GET_SNAPSHOT) return unavailable()
        val appContext = context?.applicationContext ?: return unavailable()
        val candidate = extras?.getString(EXTRA_PAIRING_TOKEN).orEmpty()
        if (candidate.isEmpty() || candidate.length > MAX_TOKEN_LENGTH) return unavailable()

        val callerUid = Binder.getCallingUid()
        if (!rateLimiter.allow(callerUid)) return unavailable()

        return try {
            val store = appContext.dataStore
            val snapshot = runBlocking(Dispatchers.IO) {
                if (!PebbleExportStore.isTokenAuthorized(store, candidate)) return@runBlocking null

                val now = Clock.systemUTC().instant()
                val preferences = store.data.first()
                val schedules = store.data.schedulesFlow().first().orEmpty()
                val config = store.data.configFlow().first()
                val updatedAt = preferences[stringPreferencesKey(LAST_SYNC_TIME_KEY)]
                    ?.toLongOrNull()
                    ?.let(Instant::ofEpochMilli) ?: now

                PebbleExportSnapshotFactory.create(schedules, config, now, updatedAt)
            } ?: return unavailable()

            Bundle().apply {
                putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
                putString(KEY_NEXT_ALARM_AT, snapshot.nextAlarmAt?.toString())
                putString(KEY_BEDTIME_AT, snapshot.bedtimeAt?.toString())
                putString(KEY_CALCULATED_AT, snapshot.calculatedAt.toString())
                putString(KEY_UPDATED_AT, snapshot.updatedAt.toString())
            }
        } catch (_: Exception) {
            unavailable()
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun unavailable(): Bundle = Bundle().apply {
        putString(KEY_ERROR, ERROR_UNAVAILABLE)
    }

    private class FailureRateLimiter {
        private val lastFailureByUid = ConcurrentHashMap<Int, Long>()

        fun allow(uid: Int): Boolean {
            val now = SystemClock.elapsedRealtime()
            val previous = lastFailureByUid[uid] ?: 0L
            if (previous > 0L && now - previous < FAILURE_INTERVAL_MILLIS) return false
            lastFailureByUid[uid] = now
            return true
        }
    }

    companion object {
        const val AUTHORITY = "com.cielshieru.wakey.pebble-export"
        const val METHOD_GET_SNAPSHOT = "get_snapshot"
        const val EXTRA_PAIRING_TOKEN = "pairing_token"
        const val KEY_SCHEMA_VERSION = "schemaVersion"
        const val KEY_NEXT_ALARM_AT = "nextAlarmAt"
        const val KEY_BEDTIME_AT = "bedtimeAt"
        const val KEY_CALCULATED_AT = "calculatedAt"
        const val KEY_UPDATED_AT = "updatedAt"
        const val KEY_ERROR = "error"
        const val SCHEMA_VERSION = 1
        private const val LAST_SYNC_TIME_KEY = "last_sync_time"
        private const val ERROR_UNAVAILABLE = "unavailable"
        private const val MAX_TOKEN_LENGTH = 80
        private const val FAILURE_INTERVAL_MILLIS = 250L
        private val rateLimiter = FailureRateLimiter()
    }
}
