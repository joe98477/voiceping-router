package com.voiceping.android.presentation.permissions

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * In-app permission settings section.
 * Shows grant status and action buttons for each permission.
 */
@Composable
fun PermissionSettingsSection(
    micGranted: Boolean,
    locationGranted: Boolean,
    notificationGranted: Boolean,
    onRequestPermission: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    // Section header
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Permissions",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Microphone permission
    ListItem(
        headlineContent = { Text("Microphone") },
        supportingContent = { Text("Required for PTT") },
        trailingContent = {
            if (micGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = Color(0xFF4CAF50) // Green
                )
            } else {
                TextButton(onClick = { onRequestPermission(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant")
                }
            }
        }
    )

    // Location permission
    ListItem(
        headlineContent = { Text("Location") },
        supportingContent = { Text("Position sharing with dispatch") },
        trailingContent = {
            if (locationGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = Color(0xFF4CAF50) // Green
                )
            } else {
                TextButton(onClick = { onRequestPermission(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                    Text("Grant")
                }
            }
        }
    )

    // Notification permission (only on API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ListItem(
            headlineContent = { Text("Notifications") },
            supportingContent = { Text("Background audio") },
            trailingContent = {
                if (notificationGranted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Granted",
                        tint = Color(0xFF4CAF50) // Green
                    )
                } else {
                    TextButton(onClick = { onRequestPermission(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Grant")
                    }
                }
            }
        )
    }
}
