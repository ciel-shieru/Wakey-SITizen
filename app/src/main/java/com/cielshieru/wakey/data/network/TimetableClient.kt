package com.cielshieru.wakey.data.network

import com.cielshieru.wakey.data.model.WakeyResponse
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.cielshieru.wakey.data.store.JsonConfig

/**
 * Fetches the wakey-sitizen timetable JSON from a SIT-ICS-Go server
 * (ADR-0002, remote server mode):
 *
 * ```
 * GET {remoteServerUrl}/wakey-sitizen/timetable.json
 * ```
 *
 * Self-made on purpose (ADR-0009): `java.net.http.HttpClient` is built into
 * Android API 36+ and simple GET requests are all this app needs — OkHttp or
 * Retrofit would add dependency surface for no benefit.
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

    suspend fun fetch(): Result = try {
        val remoteServerUrl = remoteServerProvider.remoteServerUrl.first().trim().trimEnd('/')
        if (remoteServerUrl.isEmpty()) {
            Result.Error(
                IllegalStateException("Timetable remote server URL is not configured")
            )
        } else {
            val body = withContext(Dispatchers.IO) { httpGet("$remoteServerUrl$PATH") }
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
        val client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build()
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IOException("HTTP request failed with status ${response.statusCode()}")
        }
        return response.body()
    }

    companion object {
        const val PATH = "/wakey-sitizen/timetable.json"
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
    }
}

/** Thrown (inside [TimetableClient.Result.Error]) when the server's schema is too old. */
class UnsupportedSchemaVersion(
    val actual: Int,
    val minimum: Int
) : IllegalStateException(
    "Unsupported schema version $actual (minimum supported: $minimum)"
)
