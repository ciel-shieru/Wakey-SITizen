package com.cielshieru.wakey.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WakeyResponse(
    val schema_version: Int,
    val generated_at: String,
    val timezone: String,
    val events: List<WakeyEvent>
)
