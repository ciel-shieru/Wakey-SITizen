package com.cielshieru.wakey.domain.model

import java.time.Instant
import java.time.LocalDate

data class AlarmSchedule(
    val day: LocalDate,
    val alarms: List<Alarm>,
    val firstAlarmTime: Instant?
) {
    data class Alarm(
        val index: Int,
        val triggerAt: Instant,
        val requestCode: Int
    ) {
        companion object {
            fun computeRequestCode(day: LocalDate, index: Int): Int =
                day.hashCode() * 1000 + index
        }
    }
}
