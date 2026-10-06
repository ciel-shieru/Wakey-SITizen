package com.cielshieru.wakey.usecase

import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.ManualOverride
import com.cielshieru.wakey.domain.OverrideResolver
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class ApplyOverrideUseCase @Inject constructor() {
    fun apply(
        override: ManualOverride,
        schedule: AlarmSchedule,
        now: Instant,
        zone: ZoneId
    ): AlarmSchedule {
        return OverrideResolver.applyOverride(override, schedule, zone)
    }
}
