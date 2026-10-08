package com.cielshieru.wakey.domain.model

sealed interface PermissionStatus {
    object Granted : PermissionStatus
    object Denied : PermissionStatus
    object Required : PermissionStatus
}

data class PermissionEntry(
    val permission: String,
    val status: PermissionStatus,
    val justification: String,
    val isRuntimePermission: Boolean = false
)
