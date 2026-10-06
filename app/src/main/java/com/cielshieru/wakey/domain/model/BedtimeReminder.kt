package com.cielshieru.wakey.domain.model

import java.time.Instant

data class BedtimeReminder(
    val triggerAt: Instant,
    val message: String
)
