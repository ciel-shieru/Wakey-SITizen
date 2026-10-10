package com.cielshieru.wakey.domain.model

data class DebugConfig(
    val debugModeEnabled: Boolean = false,
    val targetDateMillis: Long? = null
)
