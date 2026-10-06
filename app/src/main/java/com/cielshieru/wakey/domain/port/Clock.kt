package com.cielshieru.wakey.domain.port

import java.time.Instant

interface Clock {
    fun now(): Instant
}
