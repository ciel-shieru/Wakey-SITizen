package com.cielshieru.wakey.data.model

import kotlinx.serialization.Serializable

/** In-memory integrity/status metadata returned by SIT-ICS-Go GET /status. */
@Serializable
data class ServerStatusResponse(
    val last_fetch_job_time: String? = null,
    val etags: Map<String, String> = emptyMap(),
    val sha256: Map<String, String> = emptyMap()
)
