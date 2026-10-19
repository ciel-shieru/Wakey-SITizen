package com.cielshieru.wakey.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
data class AlarmSchedule(
    @Serializable(with = LocalDateSerializer::class)
    val day: LocalDate,
    val alarms: List<Alarm>,
    @Serializable(with = InstantSerializer::class)
    val firstAlarmTime: Instant?
) {
    @Serializable
    data class Alarm(
        val index: Int,
        @Serializable(with = InstantSerializer::class)
        val triggerAt: Instant,
        val requestCode: Int,
        val ringtoneUri: String? = null
    ) {
        companion object {
            fun computeRequestCode(day: LocalDate, index: Int): Int =
                day.hashCode() * 1000 + index
        }
    }
}
