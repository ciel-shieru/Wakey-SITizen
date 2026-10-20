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
 * [calendarIds] explicitly limits the scan to selected calendars; an empty
 * selection returns no events. Instances are queried rather than event
 * definitions so recurring events are expanded by the Calendar Provider.
 * Only title, start/end, all-day status, and selected-calendar identity are read.
 *
 * Requires `READ_CALENDAR` (ADR-0008); callers are responsible for only
 * invoking this once the permission is granted.
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
    val selection: String? = null
    val selectionArgs: Array<String>? = null

    val cursor = resolver.query(uri, projection, selection, selectionArgs, null) ?: return@withContext emptyList()

    cursor.use { c ->
        val calendars = mutableListOf<CalendarEntry>()
        while (c.moveToNext()) {
            val id = c.getInt(0).toString()
            val displayName = c.getString(1).orEmpty()
            val accountName = c.getString(2).orEmpty()
            val accountType = c.getString(3).orEmpty()
            calendars.add(CalendarEntry(id, displayName, accountName, accountType))
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
        if (calendarIds.isEmpty()) return@withContext emptyList()

        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
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
                CalendarContract.Instances.CALENDAR_ID
            ),
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC"
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
                        dtend = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDateTime(),
                        calendarId = c.getLong(COL_CALENDAR_ID).toString()
                    )
                )
            }
            events
        }
    }

    private companion object {
        const val COL_TITLE = 0
        const val COL_DTSTART = 1
        const val COL_DTEND = 2
        const val COL_ALL_DAY = 3
        const val COL_CALENDAR_ID = 4
    }
}
