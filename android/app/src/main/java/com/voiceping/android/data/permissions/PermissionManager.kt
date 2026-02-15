package com.voiceping.android.data.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voiceping.android.domain.model.PermissionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PermissionManager singleton for managing runtime permissions.
 *
 * Features:
 * - In-memory denial tracking (resets on app restart per user decision)
 * - Permission state checks (Granted/Denied/PermanentlyDenied)
 * - Settings redirect after 2 denials of same permission
 * - Aggregate missing permissions list for UI
 *
 * Permissions managed:
 * - RECORD_AUDIO: Required for PTT microphone input
 * - ACCESS_COARSE_LOCATION: Required for location tracking (Phase 18, not yet in manifest)
 * - POST_NOTIFICATIONS: Required for notification permission on Android 13+ (API 33+)
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * In-memory denial counts per permission.
     * Key: Permission string (e.g., Manifest.permission.RECORD_AUDIO)
     * Value: Number of times user denied this permission
     * Resets on app restart.
     */
    private val denialCounts = mutableMapOf<String, Int>()

    /**
     * Set of permissions that have been requested at least once.
     * Used to distinguish first-time denial vs permanent denial.
     */
    private val hasRequested = mutableSetOf<String>()

    // Permission constants
    companion object {
        const val PERMISSION_MIC = Manifest.permission.RECORD_AUDIO
        const val PERMISSION_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
        const val PERMISSION_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    }

    /**
     * Check if microphone permission is granted.
     */
    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_MIC
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if location permission is granted.
     * Note: ACCESS_COARSE_LOCATION is not yet in AndroidManifest.xml until Phase 18.
     * This method will return false until manifest is updated.
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if notification permission is granted.
     * On Android < 13 (API < 33), notifications don't require runtime permission, so returns true.
     */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the current state of a permission.
     *
     * Returns:
     * - Granted: Permission is currently granted
     * - PermanentlyDenied: Permission is denied AND shouldShowRequestPermissionRationale returns false
     *   AND permission has been requested before (meaning user selected "Don't ask again" or denied twice)
     * - Denied: Permission is denied but can be requested again
     */
    fun getPermissionState(activity: Activity, permission: String): PermissionState {
        val isGranted = ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            return PermissionState.Granted
        }

        // Permission is denied
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            permission
        )

        // If we should NOT show rationale AND permission was requested before,
        // it means user selected "Don't ask again" or denied twice
        return if (!shouldShowRationale && hasRequested.contains(permission)) {
            PermissionState.PermanentlyDenied
        } else {
            PermissionState.Denied
        }
    }

    /**
     * Track that a permission was denied by the user.
     * Increments denial count for this permission.
     *
     * @return true if denial count >= 2 (should redirect to Settings), false otherwise
     */
    fun trackDenial(permission: String): Boolean {
        val currentCount = denialCounts.getOrDefault(permission, 0)
        val newCount = currentCount + 1
        denialCounts[permission] = newCount
        return newCount >= 2
    }

    /**
     * Mark that a permission has been requested.
     * Used to distinguish first-time denial vs permanent denial.
     */
    fun markRequested(permission: String) {
        hasRequested.add(permission)
    }

    /**
     * Reset denial count for a permission.
     * Called when permission is granted to clear any previous denial tracking.
     */
    fun resetDenialCount(permission: String) {
        denialCounts.remove(permission)
    }

    /**
     * Check if we should redirect to Settings instead of showing system prompt.
     * Returns true if user has denied this permission 2+ times.
     */
    fun shouldRedirectToSettings(permission: String): Boolean {
        return denialCounts.getOrDefault(permission, 0) >= 2
    }

    /**
     * Open app settings page where user can manually enable permissions.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Get list of missing permissions as human-readable strings.
     * Returns list of permission names like "Microphone", "Location", "Notifications".
     */
    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (!hasMicPermission()) {
            missing.add("Microphone")
        }
        if (!hasLocationPermission()) {
            missing.add("Location")
        }
        if (!hasNotificationPermission()) {
            missing.add("Notifications")
        }
        return missing
    }

    /**
     * Get count of missing permissions.
     */
    fun getMissingPermissionCount(): Int {
        return getMissingPermissions().size
    }
}
