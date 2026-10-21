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
    fun `omitting date preserves existing timetable URL`() {
        assertEquals(
            "http://localhost:42748/wakey-sitizen/timetable.json",
            TimetableClient.buildTimetableUrl("http://localhost:42748/", null)
        )
    }
}
