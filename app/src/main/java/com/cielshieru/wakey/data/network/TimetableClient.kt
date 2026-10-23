package com.cielshieru.wakey.data.network

import com.cielshieru.wakey.data.model.WakeyResponse
import com.cielshieru.wakey.data.store.JsonConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Fetches the wakey-sitizen timetable JSON from a SIT-ICS-Go server
 * (ADR-0002, remote server mode):
 *
 * ```
 * GET {remoteServerUrl}/wakey-sitizen/timetable.json
 * ```
 *
 * Uses OkHttp (ADR-0009): `java.net.http.HttpClient` is not available on Android.
 * OkHttp is a well-tested, lightweight HTTP client that works across all Android API levels.
 *
 * The remote server URL is user-configured, so it is resolved through [remoteServerProvider]
 * at fetch time (the provider reads it from DataStore).
 *
 * Failure contract: [fetch] never throws. Network errors, non-2xx responses,
 * malformed JSON, and unsupported `schema_version` values all yield
 * [Result.Error], letting callers apply the stale-while-revalidate fallback
 * (ADR-0007).
 */
class TimetableClient(
    private val remoteServerProvider: com.cielshieru.wakey.domain.port.RemoteServerProvider
) {

    sealed interface Result {
        data class Success(val response: WakeyResponse) : Result
        data class Error(val cause: Throwable) : Result
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT)
        .readTimeout(CONNECT_TIMEOUT)
        .writeTimeout(CONNECT_TIMEOUT)
        .callTimeout(CONNECT_TIMEOUT)
        .build()

    suspend fun fetch(date: LocalDate? = null, endDate: LocalDate? = null): Result = try {
        val remoteServerUrl = remoteServerProvider.remoteServerUrl.first().trim().trimEnd('/')
        if (remoteServerUrl.isEmpty()) {
            Result.Error(
                IllegalStateException("Timetable remote server URL is not configured")
            )
        } else {
            val body = withContext(Dispatchers.IO) { httpGet(buildTimetableUrl(remoteServerUrl, date, endDate)) }
            val response = JsonConfig.instance.decodeFromString<WakeyResponse>(body)
            if (response.schema_version < MIN_SUPPORTED_SCHEMA_VERSION) {
                Result.Error(
                    UnsupportedSchemaVersion(
                        response.schema_version,
                        MIN_SUPPORTED_SCHEMA_VERSION
                    )
                )
            } else {
                Result.Success(response)
            }
        }
    } catch (e: Exception) {
        Result.Error(e)
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..299) {
                throw IOException("HTTP request failed with status ${response.code}")
            }
            return response.body.string()
        }
    }

    companion object {
        const val PATH = "/wakey-sitizen/timetable.json"
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        private val CONNECT_TIMEOUT = Duration.ofSeconds(10)

        /**
         * Builds the timetable URL and, when supplied, adds the server's date-window
         * query without string-concatenating untrusted URL components.
         */
        internal fun buildTimetableUrl(
            remoteServerUrl: String,
            date: LocalDate?,
            endDate: LocalDate? = null
        ): String {
            val url = (remoteServerUrl.trim().trimEnd('/') + PATH).toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Timetable remote server URL is invalid")
            if (endDate != null) {
                require(date != null) { "A start date is required when an end date is supplied." }
                require(!endDate.isBefore(date)) { "The end date must be on or after the start date." }
            }
            val builder = url.newBuilder()
            date?.let { builder.addQueryParameter("date", it.toString()) }
            endDate?.let { builder.addQueryParameter("end_date", it.toString()) }
            return builder.build().toString()
        }
    }
}

/** Thrown (inside [TimetableClient.Result.Error]) when the server's schema is too old. */
class UnsupportedSchemaVersion(
    val actual: Int,
    val minimum: Int
) : IllegalStateException(
    "Unsupported schema version $actual (minimum supported: $minimum)"
)
