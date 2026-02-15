package com.voiceping.android.presentation.permissions

import android.Manifest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Permission rationale dialog with permission-specific messaging.
 * Shows when user attempts an action without required permission.
 */
@Composable
fun PermissionRationaleDialog(
    permission: String,
    onDismiss: () -> Unit,
    onGrant: () -> Unit
) {
    val (title, message) = when (permission) {
        Manifest.permission.RECORD_AUDIO -> {
            "Microphone Access Needed" to "VoicePing needs microphone access to transmit your voice using Push-to-Talk. Without it, you can listen to others but cannot speak."
        }
        Manifest.permission.ACCESS_COARSE_LOCATION -> {
            "Location Access Needed" to "VoicePing shares your general location with dispatch so they can coordinate your team effectively."
        }
        Manifest.permission.POST_NOTIFICATIONS -> {
            "Notification Access Needed" to "VoicePing uses notifications to keep audio running in the background. Without this, audio may stop when the screen is off."
        }
        else -> {
            "Permission Needed" to "This permission is required for the app to function properly."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text("Grant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
