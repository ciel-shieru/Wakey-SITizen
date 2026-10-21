package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.EventKind
import com.cielshieru.wakey.domain.model.ExternalEvent
import com.cielshieru.wakey.domain.model.TimetableEvent

/**
 * Converts device-calendar events while preserving which selected timetable
 * calendar each event came from. Settings store online and campus calendar IDs
 * separately so their roles do not depend on list ordering or missing choices.
 */
object DeviceCalendarEventMapper {

    fun map(
        events: List<ExternalEvent>,
        onlineCalendarId: String?,
        campusCalendarId: String?
    ): List<TimetableEvent> = events.map { event ->
        val eventKind = when {
            onlineCalendarId != null && event.calendarId == onlineCalendarId -> EventKind.ONLINE
            campusCalendarId != null && event.calendarId == campusCalendarId -> EventKind.CAMPUS
            else -> EventKind.CAMPUS
        }
        val title = event.peopleSoftTitle?.takeIf { it.isNotBlank() } ?: event.title
        TimetableEvent(
            courseCode = event.peopleSoftCourseCode?.takeIf { it.isNotBlank() }.orEmpty(),
            title = title,
            summary = title,
            dtstart = event.dtstart,
            dtend = event.dtend,
            location = event.location,
            eventType = eventKind
        )
    }
}
