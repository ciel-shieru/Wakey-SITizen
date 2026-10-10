package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.port.ZoneProvider
import java.time.ZoneId

class FakeZoneProvider : ZoneProvider {
    override var zone: ZoneId = ZoneId.of("Asia/Singapore")
}
