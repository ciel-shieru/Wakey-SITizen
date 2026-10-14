package com.cielshieru.wakey.domain.model

import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable

@Serializable
data class TimetableEvent(
    val courseCode: String,
    val title: String,
    val summary: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val dtstart: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val dtend: LocalDateTime,
    val location: String,
    val eventType: EventKind
) {
    companion object {
        fun fromJson(event: com.cielshieru.wakey.data.model.WakeyEvent): TimetableEvent {
            return TimetableEvent(
                courseCode = event.course_code,
                title = event.title,
                summary = event.summary,
                // Timestamps are RFC 3339 with a +08:00 offset (ADR-0002);
                // parse the offset form then drop the zone to a plain local time.
                dtstart = OffsetDateTime.parse(event.dtstart).toLocalDateTime(),
                dtend = OffsetDateTime.parse(event.dtend).toLocalDateTime(),
                location = event.location,
                eventType = if (event.location.equals("Online", ignoreCase = true)) {
                    EventKind.ONLINE
                } else {
                    EventKind.CAMPUS
                }
            )
        }
    }
}
