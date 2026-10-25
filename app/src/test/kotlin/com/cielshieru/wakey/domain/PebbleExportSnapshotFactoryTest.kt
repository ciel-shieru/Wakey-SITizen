package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class PebbleExportSnapshotFactoryTest {
    @Test
    fun selectsNextFutureAlarmAndEarlierCalculatedBedtime() {
        val firstAlarm = Instant.parse("2026-10-26T07:30:00Z")
        val schedule = AlarmSchedule(
            day = LocalDate.of(2026, 10, 26),
            alarms = emptyList(),
            firstAlarmTime = firstAlarm,
        )
        val config = AlarmConfig(
            targetSleepDurationHours = 8,
            bedtimeEnabled = true,
        )
        val now = Instant.parse("2026-10-25T18:00:00Z")
        val updatedAt = now.minus(5, ChronoUnit.MINUTES)

        val snapshot = PebbleExportSnapshotFactory.create(listOf(schedule), config, now, updatedAt)

        assertEquals(firstAlarm, snapshot.nextAlarmAt)
        assertEquals(firstAlarm.minus(8, ChronoUnit.HOURS), snapshot.bedtimeAt)
        assertEquals(now, snapshot.calculatedAt)
        assertEquals(updatedAt, snapshot.updatedAt)
    }

    @Test
    fun doesNotExportBedtimeWhenRemindersAreDisabled() {
        val firstAlarm = Instant.parse("2026-10-26T07:30:00Z")
        val schedule = AlarmSchedule(
            day = LocalDate.of(2026, 10, 26),
            alarms = emptyList(),
            firstAlarmTime = firstAlarm,
        )
        val config = AlarmConfig(bedtimeEnabled = false)
        val now = Instant.parse("2026-10-25T18:00:00Z")

        val snapshot = PebbleExportSnapshotFactory.create(listOf(schedule), config, now, now)

        assertEquals(firstAlarm, snapshot.nextAlarmAt)
        assertNull(snapshot.bedtimeAt)
    }

    @Test
    fun returnsNullTimestampsWhenNoFutureAlarmExists() {
        val past = Instant.parse("2026-10-25T07:30:00Z")
        val schedule = AlarmSchedule(
            day = LocalDate.of(2026, 10, 25),
            alarms = emptyList(),
            firstAlarmTime = past,
        )
        val now = Instant.parse("2026-10-25T18:00:00Z")

        val snapshot = PebbleExportSnapshotFactory.create(
            listOf(schedule), AlarmConfig(bedtimeEnabled = true), now, now,
        )

        assertNull(snapshot.nextAlarmAt)
        assertNull(snapshot.bedtimeAt)
    }
}
