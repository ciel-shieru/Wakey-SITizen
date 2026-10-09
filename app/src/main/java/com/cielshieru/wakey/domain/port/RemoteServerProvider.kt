package com.cielshieru.wakey.domain.port

import kotlinx.coroutines.flow.Flow

interface RemoteServerProvider {
    val remoteServerUrl: Flow<String>
}
