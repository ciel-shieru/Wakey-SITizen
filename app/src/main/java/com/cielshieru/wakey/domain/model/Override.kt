package com.cielshieru.wakey.domain.model

import java.time.Instant

data class ManualOverride(
    val type: OverrideType,
    val value: String,
    val expiresAt: Instant,
    val applied: Boolean = false
)

enum class OverrideType {
    SNOOZE,
    SKIP_TOMORROW,
    CUSTOM_TIME
}
