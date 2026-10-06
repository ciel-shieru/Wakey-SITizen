package com.cielshieru.wakey.domain

import com.cielshieru.wakey.domain.model.AlarmConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmConfigValidationTest {

    @Test
    fun `validate coerces online offset to valid range`() {
        val config = AlarmConfig(onlineOffsetMinutes = 1000)
        val validated = AlarmConfig.validate(config)
        assertEquals(720, validated.onlineOffsetMinutes)
    }

    @Test
    fun `validate coerces repeat interval to valid range`() {
        val config = AlarmConfig(repeatIntervalMinutes = 50)
        val validated = AlarmConfig.validate(config)
        assertEquals(30, validated.repeatIntervalMinutes)
    }

    @Test
    fun `validate coerces max repeats to valid range`() {
        val config = AlarmConfig(maxRepeats = 50)
        val validated = AlarmConfig.validate(config)
        assertEquals(20, validated.maxRepeats)
    }

    @Test
    fun `validate leaves valid values unchanged`() {
        val config = AlarmConfig(
            onlineOffsetMinutes = 60,
            campusOffsetMinutes = 90,
            repeatIntervalMinutes = 5,
            maxRepeats = 6
        )
        val validated = AlarmConfig.validate(config)

        assertEquals(config.onlineOffsetMinutes, validated.onlineOffsetMinutes)
        assertEquals(config.campusOffsetMinutes, validated.campusOffsetMinutes)
        assertEquals(config.repeatIntervalMinutes, validated.repeatIntervalMinutes)
        assertEquals(config.maxRepeats, validated.maxRepeats)
    }
}
