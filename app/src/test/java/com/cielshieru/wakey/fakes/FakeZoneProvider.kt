package com.cielshieru.wakey.fakes

import com.cielshieru.wakey.domain.port.ZoneProvider
import java.time.ZoneId

class FakeZoneProvider : ZoneProvider {
    var zone: ZoneId = ZoneId.of("Asia/Singapore")
}
