package com.cielshieru.wakey.data.network

import com.cielshieru.wakey.data.model.ServerStatusResponse
import com.cielshieru.wakey.data.store.JsonConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Duration
import javax.inject.Inject

/**
 * Calls only status/refresh paths on the user-configured SIT-ICS-Go base URL.
 * Redirect following is disabled so a server response cannot redirect these
 * requests to a different host.
 */
class ServerStatusClient @Inject constructor() {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT)
        .readTimeout(TIMEOUT)
        .writeTimeout(TIMEOUT)
        .callTimeout(TIMEOUT)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun fetchStatus(baseUrl: String): ServerStatusResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpointUrl(baseUrl, STATUS_PATH))
            .get()
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Status request failed with HTTP ${response.code}")
            }
            response.body.string()
        }
        JsonConfig.instance.decodeFromString<ServerStatusResponse>(body)
    }

    suspend fun requestRefresh(baseUrl: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpointUrl(baseUrl, REFRESH_PATH))
            .post("".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Refresh request failed with HTTP ${response.code}")
            }
        }
    }

    private fun endpointUrl(baseUrl: String, endpoint: String): HttpUrl {
        val parsed = baseUrl.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Remote Server URL is invalid")
        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "Remote Server URL must use HTTP or HTTPS"
        }
        require(
            parsed.username.isEmpty() &&
                parsed.password.isEmpty() &&
                parsed.query == null &&
                parsed.fragment == null
        ) {
            "Remote Server URL must not contain credentials, query parameters, or fragments"
        }

        val basePath = parsed.encodedPath.trimEnd('/')
        val endpointPath = (if (basePath.isEmpty()) "" else basePath) + endpoint
        return parsed.newBuilder().encodedPath(endpointPath).build()
    }

    companion object {
        const val STATUS_PATH = "/status"
        const val REFRESH_PATH = "/refresh"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TIMEOUT = Duration.ofSeconds(10)
    }
}
