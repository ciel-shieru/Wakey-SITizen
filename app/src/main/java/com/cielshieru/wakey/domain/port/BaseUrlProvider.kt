package com.cielshieru.wakey.domain.port

import kotlinx.coroutines.flow.Flow

interface BaseUrlProvider {
    val baseUrl: Flow<String>
}
