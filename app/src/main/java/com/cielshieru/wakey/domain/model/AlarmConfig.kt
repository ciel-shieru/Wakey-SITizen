package com.cielshieru.wakey.domain.model

data class AlarmConfig(
    val onlineOffsetMinutes: Int = 60,
    val campusOffsetMinutes: Int = 90,
    val repeatIntervalMinutes: Int = 5,
    val maxRepeats: Int = 6,
    val targetSleepDurationHours: Int = 8,
    val bedtimeReminderOffsetMinutes: Int = 30,
    val externalEventBufferMinutes: Int = 30,
    val externalEventEnabled: Boolean = false,
    val bedtimeEnabled: Boolean = false
) {
    companion object {
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
