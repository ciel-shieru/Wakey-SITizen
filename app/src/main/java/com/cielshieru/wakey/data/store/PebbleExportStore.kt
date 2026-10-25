package com.cielshieru.wakey.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PebbleExportSettings(
    val enabled: Boolean = false,
    val tokenConfigured: Boolean = false,
)

object PebbleExportStore {
    private val ENABLED = booleanPreferencesKey("pebble_export_enabled")
    private val TOKEN_SHA256 = stringPreferencesKey("pebble_export_token_sha256")

    fun settingsFlow(dataStore: DataStore<Preferences>): Flow<PebbleExportSettings> =
        dataStore.data.map { preferences ->
            PebbleExportSettings(
                enabled = preferences[ENABLED] ?: false,
                tokenConfigured = !preferences[TOKEN_SHA256].isNullOrBlank(),
            )
        }

    suspend fun isEnabled(dataStore: DataStore<Preferences>): Boolean =
        dataStore.data.first()[ENABLED] ?: false

    suspend fun tokenHash(dataStore: DataStore<Preferences>): String? =
        dataStore.data.first()[TOKEN_SHA256]

    /** Generates 256-bit entropy; only the SHA-256 digest is persisted. */
    suspend fun createOrRotateToken(dataStore: DataStore<Preferences>): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val digest = hashToken(token)
        dataStore.edit { preferences ->
            preferences[TOKEN_SHA256] = digest
            preferences[ENABLED] = true
        }
        return token
    }

    /** Disabling export revokes the current token rather than keeping it reusable. */
    suspend fun disableAndRevoke(dataStore: DataStore<Preferences>) {
        dataStore.edit { preferences ->
            preferences[ENABLED] = false
            preferences.remove(TOKEN_SHA256)
        }
    }

    suspend fun isTokenAuthorized(
        dataStore: DataStore<Preferences>,
        candidate: String,
    ): Boolean {
        if (candidate.length !in TOKEN_MIN_LENGTH..TOKEN_MAX_LENGTH) return false
        if (candidate.any { it.code !in 0x21..0x7e }) return false

        val preferences = dataStore.data.first()
        if (preferences[ENABLED] != true) return false
        val expected = preferences[TOKEN_SHA256] ?: return false
        val actual = hashToken(candidate)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            actual.toByteArray(Charsets.US_ASCII),
        )
    }

    internal fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private const val TOKEN_MIN_LENGTH = 40
    private const val TOKEN_MAX_LENGTH = 80
}
