package org.maksec.shared.navigation.components

import kotlinx.coroutines.flow.StateFlow

interface PermissionManager {
    val permissionsFlow: StateFlow<List<AppPermission>>

    suspend fun requestPermission(type: AppPermissionType)
    fun updatePermissionList()
}

data class AppPermission(
    val type: AppPermissionType,
    val isGranted: Boolean
)

enum class AppPermissionType {
    READ_CONTACTS,
    WRITE_CONTACTS,
    POST_NOTIFICATIONS,
    READ_AND_RECEIVE_SMS,
    AUTO_LAUNCH,
    RECORD_AUDIO,
    STORAGE,
    NOTIFICATION_LISTENER
}