package com.cielshieru.wakey.domain.model

import java.time.LocalDateTime

data class ExternalEvent(
    val title: String,
    val dtstart: LocalDateTime,
    val dtend: LocalDateTime,
    val calendarId: String? = null,
    val location: String = "",
    val peopleSoftCourseCode: String? = null,
    val peopleSoftTitle: String? = null
)
