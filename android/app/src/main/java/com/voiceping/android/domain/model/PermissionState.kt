package com.voiceping.android.domain.model

/**
 * Sealed class representing the state of a runtime permission.
 *
 * States:
 * - Granted: Permission is currently granted
 * - Denied: Permission is denied but can be requested again (user tapped "Deny" once)
 * - PermanentlyDenied: Permission is denied and shouldShowRequestPermissionRationale returns false,
 *   meaning the user selected "Don't ask again" or has denied twice on API 30+
 */
sealed class PermissionState {
    data object Granted : PermissionState()
    data object Denied : PermissionState()
    data object PermanentlyDenied : PermissionState()
}
