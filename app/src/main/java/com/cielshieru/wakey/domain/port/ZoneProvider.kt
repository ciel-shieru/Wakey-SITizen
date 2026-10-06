package com.cielshieru.wakey.domain.port

import java.time.ZoneId

interface ZoneProvider {
    val zone: ZoneId
}
