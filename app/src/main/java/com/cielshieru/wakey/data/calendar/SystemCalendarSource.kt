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
 * Reads device-calendar events for external-event adjustment (ADR-0002,
 * device-calendar mode / ADR-0005) via [CalendarContract].
 *
 * Self-made on purpose (ADR-0009): `CalendarContract` is a platform API and
 * the required projection is a single straightforward query.
 *
 * [calendarIds] selects the calendars to scan; an empty list scans all
 * calendars on the device. Only timed events are returned — all-day events
 * carry a date (not a timestamp) in `DTSTART` and would produce meaningless
 * local-datetime values for the adjustment algorithm.
 *
 * Requires `READ_CALENDAR` (ADR-0008); callers are responsible for only
 * invoking this once the permission is granted.
 */
data class CalendarEntry(
    val id: String,
    val displayName: String,
    val accountName: String
)

suspend fun getReadableCalendars(context: Context): List<CalendarEntry> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val uri = CalendarContract.Calendars.CONTENT_URI
    val projection = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_NAME
    )
    val selection = "access_level >= ?"
    val selectionArgs = arrayOf("1")

    val cursor = resolver.query(uri, projection, selection, selectionArgs, null) ?: return@withContext emptyList()

    cursor.use { c ->
        val calendars = mutableListOf<CalendarEntry>()
        while (c.moveToNext()) {
            val id = c.getString(0) ?: continue
            val displayName = c.getString(1).orEmpty()
            val accountName = c.getString(2).orEmpty()
            calendars.add(CalendarEntry(id, displayName, accountName))
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
    ): List<ExternalEvent> = withContext(Dispatchers.IO) {
        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val calendarClause = if (calendarIds.isNotEmpty()) {
            val placeholders = calendarIds.joinToString(", ") { "?" }
            " AND ${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)"
        } else {
            ""
        }
        val selection =
            "${CalendarContract.Events.DTSTART} >= ? AND " +
                "${CalendarContract.Events.DTSTART} < ?$calendarClause"
        val selectionArgs: Array<String> =
            arrayOf(startMillis.toString(), endMillis.toString()) + calendarIds

        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.ALL_DAY
            ),
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        ) ?: return@withContext emptyList()

        cursor.use { c ->
            val events = mutableListOf<ExternalEvent>()
            while (c.moveToNext()) {
                // Skip all-day events (their DTSTART/DTEND are local dates, not timestamps).
                if (c.getInt(COL_ALL_DAY) != 0) continue

                val startMillis = c.getLong(COL_DTSTART)
                val endMillis = c.getLong(COL_DTEND)
                events.add(
                    ExternalEvent(
                        title = c.getString(COL_TITLE).orEmpty(),
                        dtstart = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDateTime(),
                        dtend = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDateTime()
                    )
                )
            }
            events
        }
    }

    private companion object {
        const val COL_ID = 0
        const val COL_DISPLAY_NAME = 1
        const val COL_ACCOUNT_NAME = 2
        const val COL_TITLE = 3
        const val COL_DTSTART = 4
        const val COL_DTEND = 5
        const val COL_LOCATION = 6
        const val COL_ALL_DAY = 7
    }
}
