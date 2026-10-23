package com.cielshieru.wakey.data.calendar

import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import com.cielshieru.wakey.domain.model.ExternalEvent
import com.cielshieru.wakey.domain.port.CalendarEventSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads selected device-calendar instances through [CalendarContract]. Extended
 * Properties are read separately and best-effort because calendar providers may
 * omit custom ICS metadata. Missing PeopleSoft metadata falls back to the
 * ordinary event title and an empty course code.
 *
 * Requires READ_CALENDAR; callers only invoke this after permission is granted.
 */
data class CalendarEntry(
    val id: String,
    val displayName: String,
    val accountName: String,
    val accountType: String
)

suspend fun getReadableCalendars(context: Context): List<CalendarEntry> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val uri = CalendarContract.Calendars.CONTENT_URI
    val projection = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_NAME,
        CalendarContract.Calendars.ACCOUNT_TYPE
    )
    val cursor = resolver.query(uri, projection, null, null, null)
        ?: return@withContext emptyList()

    cursor.use { c ->
        val calendars = mutableListOf<CalendarEntry>()
        while (c.moveToNext()) {
            calendars.add(
                CalendarEntry(
                    id = c.getInt(0).toString(),
                    displayName = c.getString(1).orEmpty(),
                    accountName = c.getString(2).orEmpty(),
                    accountType = c.getString(3).orEmpty()
                )
            )
        }
        calendars
    }
}

class SystemCalendarSource(
    private val contentResolver: ContentResolver,
    private val zone: ZoneId = ZoneId.systemDefault()
) : CalendarEventSource {

    override suspend fun getEventsForDate(
        date: LocalDate,
        calendarIds: List<String>
    ): List<ExternalEvent> = queryEventsForDate(date, calendarIds, includePeopleSoftMetadata = false)

    override suspend fun getTimetableEventsForDate(
        date: LocalDate,
        calendarIds: List<String>
    ): List<ExternalEvent> = queryEventsForRange(
        start = date,
        endInclusive = date,
        calendarIds = calendarIds,
        includePeopleSoftMetadata = true
    )

    override suspend fun getTimetableEventsForRange(
        start: LocalDate,
        endInclusive: LocalDate,
        calendarIds: List<String>
    ): List<ExternalEvent> = queryEventsForRange(
        start = start,
        endInclusive = endInclusive,
        calendarIds = calendarIds,
        includePeopleSoftMetadata = true
    )

    private suspend fun queryEventsForDate(
        date: LocalDate,
        calendarIds: List<String>,
        includePeopleSoftMetadata: Boolean
    ): List<ExternalEvent> = queryEventsForRange(
        start = date,
        endInclusive = date,
        calendarIds = calendarIds,
        includePeopleSoftMetadata = includePeopleSoftMetadata
    )

    private suspend fun queryEventsForRange(
        start: LocalDate,
        endInclusive: LocalDate,
        calendarIds: List<String>,
        includePeopleSoftMetadata: Boolean
    ): List<ExternalEvent> = withContext(Dispatchers.IO) {
        require(!endInclusive.isBefore(start)) { "The end date must not be before the start date." }
        if (calendarIds.isEmpty()) return@withContext emptyList()

        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMillis.toString())
            .appendPath(endMillis.toString())
            .build()
        val placeholders = calendarIds.joinToString(", ") { "?" }
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
        val selectionArgs = calendarIds.toTypedArray()

        val cursor = contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.EVENT_LOCATION
            ),
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC"
        ) ?: return@withContext emptyList()

        val eventsWithIds = cursor.use { c ->
            val events = mutableListOf<Pair<String, ExternalEvent>>()
            while (c.moveToNext()) {
                // All-day events are not suitable for minute-level timetable alarms.
                if (c.getInt(COL_ALL_DAY) != 0) continue

                val startMillis = c.getLong(COL_DTSTART)
                val endMillis = c.getLong(COL_DTEND)
                val event = ExternalEvent(
                    title = c.getString(COL_TITLE).orEmpty(),
                    dtstart = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDateTime(),
                    dtend = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDateTime(),
                    calendarId = c.getLong(COL_CALENDAR_ID).toString(),
                    location = c.getString(COL_LOCATION).orEmpty()
                )
                events.add(c.getLong(COL_EVENT_ID).toString() to event)
            }
            events
        }

        if (!includePeopleSoftMetadata) {
            return@withContext eventsWithIds.map { it.second }
        }

        val propertiesByEvent = readPeopleSoftProperties(eventsWithIds.map { it.first })
        eventsWithIds.map { (eventId, event) ->
            val properties = propertiesByEvent[eventId].orEmpty()
            event.copy(
                peopleSoftCourseCode = properties[COURSE_CODE_PROPERTY]?.takeIf { it.isNotBlank() },
                peopleSoftTitle = properties[TITLE_PROPERTY]?.takeIf { it.isNotBlank() }
            )
        }
    }

    /**
     * Extended property queries use bound event IDs and bounded batches. Failures
     * here must not suppress otherwise readable calendar events.
     */
    private fun readPeopleSoftProperties(eventIds: List<String>): Map<String, Map<String, String>> {
        if (eventIds.isEmpty()) return emptyMap()

        val propertiesByEvent = mutableMapOf<String, MutableMap<String, String>>()
        eventIds.distinct().chunked(EXTENDED_PROPERTY_BATCH_SIZE).forEach { ids ->
            try {
                val placeholders = ids.joinToString(", ") { "?" }
                val cursor = contentResolver.query(
                    CalendarContract.ExtendedProperties.CONTENT_URI,
                    arrayOf(
                        CalendarContract.ExtendedProperties.EVENT_ID,
                        CalendarContract.ExtendedProperties.NAME,
                        CalendarContract.ExtendedProperties.VALUE
                    ),
                    "${CalendarContract.ExtendedProperties.EVENT_ID} IN ($placeholders)",
                    ids.toTypedArray(),
                    null
                ) ?: return@forEach

                cursor.use { c ->
                    while (c.moveToNext()) {
                        val eventId = c.getString(COL_PROPERTY_EVENT_ID).orEmpty()
                        val propertyName = canonicalPeopleSoftPropertyName(
                            c.getString(COL_PROPERTY_NAME).orEmpty()
                        ) ?: continue
                        val value = unescapeIcsText(c.getString(COL_PROPERTY_VALUE).orEmpty())
                        propertiesByEvent.getOrPut(eventId) { mutableMapOf() }[propertyName] = value
                    }
                }
            } catch (_: Exception) {
                // Some calendar providers do not expose extended properties.
                // Continue with ordinary event titles and blank course codes.
            }
        }
        return propertiesByEvent
    }

    private fun canonicalPeopleSoftPropertyName(name: String): String? {
        // Providers may store a bare ICS property name or a namespaced
        // "{scheme}#{local-name}" extended-property name.
        val localName = name.substringAfterLast('#')
        return when {
            localName.equals(COURSE_CODE_PROPERTY, ignoreCase = true) -> COURSE_CODE_PROPERTY
            localName.equals(TITLE_PROPERTY, ignoreCase = true) -> TITLE_PROPERTY
            else -> null
        }
    }

    private fun unescapeIcsText(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '\\' && index + 1 < value.length) {
                when (val escaped = value[index + 1]) {
                    'n', 'N' -> result.append('\n')
                    ',', ';', '\\' -> result.append(escaped)
                    else -> result.append(escaped)
                }
                index += 2
            } else {
                result.append(current)
                index++
            }
        }
        return result.toString()
    }

    private companion object {
        const val COL_TITLE = 0
        const val COL_DTSTART = 1
        const val COL_DTEND = 2
        const val COL_ALL_DAY = 3
        const val COL_CALENDAR_ID = 4
        const val COL_EVENT_ID = 5
        const val COL_LOCATION = 6
        const val COL_PROPERTY_EVENT_ID = 0
        const val COL_PROPERTY_NAME = 1
        const val COL_PROPERTY_VALUE = 2
        const val COURSE_CODE_PROPERTY = "X-PeopleSoft-CourseCode"
        const val TITLE_PROPERTY = "X-PeopleSoft-Title"
        const val EXTENDED_PROPERTY_BATCH_SIZE = 400
    }
}
