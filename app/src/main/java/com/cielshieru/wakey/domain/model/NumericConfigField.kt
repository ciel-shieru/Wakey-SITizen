package com.cielshieru.wakey.domain.model

/**
 * Identifies numeric settings explicitly entered by the user. Absence means
 * the setting is still using the current application default.
 */
enum class NumericConfigField {
    ONLINE_OFFSET,
    CAMPUS_OFFSET,
    REPEAT_INTERVAL,
    MAX_REPEATS,
    SLEEP_DURATION,
    BEDTIME_OFFSET,
    EXTERNAL_BUFFER
}
