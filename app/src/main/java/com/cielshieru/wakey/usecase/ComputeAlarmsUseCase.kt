package com.cielshieru.wakey.usecase

import com.cielshieru.wakey.domain.AlarmComputer
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.TimetableEvent
import com.cielshieru.wakey.domain.port.Clock
import com.cielshieru.wakey.domain.port.ZoneProvider
import java.time.LocalDate
import javax.inject.Inject

class ComputeAlarmsUseCase @Inject constructor(
    private val clock: Clock,
    private val zoneProvider: ZoneProvider
) {
    fun compute(events: List<TimetableEvent>, config: AlarmConfig): List<AlarmSchedule> {
        return AlarmComputer.computeSchedule(events, config, clock, zoneProvider)
    }

    fun compute(events: List<TimetableEvent>, config: AlarmConfig, targetDateOverride: LocalDate?): List<AlarmSchedule> {
        return AlarmComputer.computeSchedule(events, config, clock, zoneProvider, targetDateOverride)
    }
}
