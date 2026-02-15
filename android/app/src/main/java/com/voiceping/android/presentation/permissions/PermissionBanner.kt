package com.voiceping.android.presentation.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material3 permission warning banner.
 *
 * Shows persistent warning with Fix action button when permissions are missing.
 * Auto-dismisses when permission granted (not user-dismissible).
 *
 * Design:
 * - errorContainer background with 2dp tonal elevation
 * - Warning icon (error tint)
 * - Message text (bodyMedium)
 * - "Fix" TextButton action
 *
 * @param message Banner message text (e.g., "Microphone permission needed for Push-to-Talk")
 * @param actionLabel Action button label (e.g., "Fix")
 * @param onAction Callback when action button is clicked
 * @param modifier Optional modifier
 */
@Composable
fun PermissionBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Warning icon
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            // Message text (fills available space)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )

            // Fix action button
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
