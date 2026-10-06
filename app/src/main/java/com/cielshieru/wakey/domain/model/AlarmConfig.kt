package com.cielshieru.wakey.domain.model

data class AlarmConfig(
    val onlineOffsetMinutes: Int = DEFAULT_ONLINE_OFFSET,
    val campusOffsetMinutes: Int = DEFAULT_CAMPUS_OFFSET,
    val repeatIntervalMinutes: Int = DEFAULT_REPEAT_INTERVAL,
    val maxRepeats: Int = DEFAULT_MAX_REPEATS,
    val targetSleepDurationHours: Int = DEFAULT_SLEEP_DURATION,
    val bedtimeReminderOffsetMinutes: Int = DEFAULT_BEDTIME_OFFSET,
    val externalEventBufferMinutes: Int = DEFAULT_BUFFER_MINUTES,
    val externalEventEnabled: Boolean = false,
    val bedtimeEnabled: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL
) {
    companion object {
        const val DEFAULT_ONLINE_OFFSET = 60
        const val DEFAULT_CAMPUS_OFFSET = 90
        const val DEFAULT_REPEAT_INTERVAL = 5
        const val DEFAULT_MAX_REPEATS = 6
        const val DEFAULT_SLEEP_DURATION = 8
        const val DEFAULT_BEDTIME_OFFSET = 30
        const val DEFAULT_BUFFER_MINUTES = 30
        const val DEFAULT_BASE_URL = "http://localhost:42748"

        fun validate(config: AlarmConfig): AlarmConfig = config.copy(
            onlineOffsetMinutes = config.onlineOffsetMinutes.coerceIn(1, 720),
            campusOffsetMinutes = config.campusOffsetMinutes.coerceIn(1, 720),
            repeatIntervalMinutes = config.repeatIntervalMinutes.coerceIn(1, 30),
            maxRepeats = config.maxRepeats.coerceIn(1, 20),
            targetSleepDurationHours = config.targetSleepDurationHours.coerceIn(4, 12),
            bedtimeReminderOffsetMinutes = config.bedtimeReminderOffsetMinutes.coerceIn(5, 60),
            externalEventBufferMinutes = config.externalEventBufferMinutes.coerceIn(5, 120)
        )
    }
}
