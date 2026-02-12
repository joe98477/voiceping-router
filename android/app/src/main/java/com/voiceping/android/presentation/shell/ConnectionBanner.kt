package com.voiceping.android.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voiceping.android.domain.model.ConnectionState
import kotlinx.coroutines.delay

@Composable
fun ConnectionBanner(
    connectionState: ConnectionState,
    onRetry: () -> Unit
) {
    // Track when RECONNECTING state began
    var reconnectingSince by remember { mutableLongStateOf(0L) }
    var showBanner by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            ConnectionState.RECONNECTING -> {
                if (reconnectingSince == 0L) {
                    reconnectingSince = System.currentTimeMillis()
                }
                // Wait 5 seconds before showing banner (brief drops stay silent)
                delay(5000L)
                // Still reconnecting after 5s? Show banner
                if (connectionState == ConnectionState.RECONNECTING) {
                    showBanner = true
                }
            }
            ConnectionState.FAILED -> {
                // FAILED always shows banner immediately (5-min timeout already elapsed)
                showBanner = true
            }
            ConnectionState.CONNECTING -> {
                // CONNECTING shows banner immediately
                showBanner = true
            }
            else -> {
                // CONNECTED or other: hide banner, reset timer
                showBanner = false
                reconnectingSince = 0L
            }
        }
    }

    AnimatedVisibility(
        visible = showBanner,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when (connectionState) {
                        ConnectionState.CONNECTING -> Color(0xFFFFA726) // Amber
                        ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                        ConnectionState.FAILED -> MaterialTheme.colorScheme.errorContainer
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.RECONNECTING -> "Reconnecting..."
                    ConnectionState.FAILED -> "Connection lost"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (connectionState) {
                    ConnectionState.CONNECTING -> Color.White
                    ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.onSecondaryContainer
                    ConnectionState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                    else -> Color.White
                }
            )

            // Retry button only for FAILED state
            if (connectionState == ConnectionState.FAILED) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = "Retry",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
