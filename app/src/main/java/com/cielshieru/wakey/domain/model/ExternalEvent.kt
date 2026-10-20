package com.cielshieru.wakey.domain.model

import java.time.LocalDateTime

data class ExternalEvent(
    val title: String,
    val dtstart: LocalDateTime,
    val dtend: LocalDateTime,
    val calendarId: String? = null
)
