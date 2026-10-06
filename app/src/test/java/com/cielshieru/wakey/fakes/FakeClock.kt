package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.port.Clock
import java.time.Instant

class FakeClock : Clock {
    var now: Instant = Instant.EPOCH

    override fun now(): Instant = now

    fun set(time: Instant) {
        now = time
    }
}
