package com.cielshieru.wakey.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimetableClientUrlTest {

    @Test
    fun `date is sent as ISO local date query parameter`() {
        assertEquals(
            "http://localhost:42748/wakey-sitizen/timetable.json?date=2026-10-21",
            TimetableClient.buildTimetableUrl("http://localhost:42748", LocalDate.of(2026, 10, 21))
        )
    }

    @Test
    fun `date range is sent as start and inclusive end query parameters`() {
        assertEquals(
            "http://localhost:42748/wakey-sitizen/timetable.json?date=2026-10-01&end_date=2026-10-30",
            TimetableClient.buildTimetableUrl(
                "http://localhost:42748",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 30)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `end date requires a start date`() {
        TimetableClient.buildTimetableUrl(
            "http://localhost:42748",
            null,
            LocalDate.of(2026, 10, 30)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `end date cannot precede start date`() {
        TimetableClient.buildTimetableUrl(
            "http://localhost:42748",
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 10, 1)
        )
    }

    @Test
    fun `omitting date preserves existing timetable URL`() {
        assertEquals(
            "http://localhost:42748/wakey-sitizen/timetable.json",
            TimetableClient.buildTimetableUrl("http://localhost:42748/", null)
        )
    }
}
