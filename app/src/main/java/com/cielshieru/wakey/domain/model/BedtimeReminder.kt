package com.cielshieru.wakey.domain.model

import java.time.Instant

data class BedtimeReminder(
    /** When the notification should be delivered. */
    val triggerAt: Instant,
    /** The actual target bedtime, independent of the reminder trigger offset. */
    val bedtimeAt: Instant,
    val message: String
)
