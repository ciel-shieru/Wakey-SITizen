package com.cielshieru.wakey.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WakeyEvent(
    val course_code: String,
    val title: String,
    val summary: String,
    val dtstart: String,
    val dtend: String,
    val location: String,
    val event_type: String
)
