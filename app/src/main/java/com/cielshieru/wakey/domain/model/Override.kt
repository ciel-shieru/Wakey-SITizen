package com.cielshieru.wakey.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ManualOverride(
    val type: OverrideType,
    val value: String,
    @Serializable(with = InstantSerializer::class)
    val expiresAt: Instant,
    val applied: Boolean = false
)

@Serializable
enum class OverrideType {
    SNOOZE,
    SKIP_TOMORROW,
    SKIP_ALARM,
    CUSTOM_TIME
}
