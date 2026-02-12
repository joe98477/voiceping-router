package com.voiceping.android.presentation.channels.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voiceping.android.data.ptt.PttState
import com.voiceping.android.domain.model.ConnectionState
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.User

@Composable
fun BottomBar(
    displayedChannelName: String?,
    isPrimaryChannel: Boolean,
    isLocked: Boolean,
    currentSpeaker: User?,
    pttState: PttState,
    pttMode: PttMode,
    transmissionDuration: Long,
    connectionState: ConnectionState,
    onToggleLock: () -> Unit,
    onPttPressed: () -> Unit,
    onPttReleased: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (displayedChannelName != null) {
                // Left side: channel info and status (clickable for lock toggle)
                Column(
                    modifier = Modifier.clickable { onToggleLock() }
                ) {
                    // Channel name with offline badge if disconnected
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayedChannelName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isPrimaryChannel) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.primary  // Cyan for scanned channel
                            }
                        )

                        // Offline badge when disconnected
                        if (connectionState == ConnectionState.RECONNECTING ||
                            connectionState == ConnectionState.FAILED ||
                            connectionState == ConnectionState.DISCONNECTED) {
                            Text(
                                text = "(Offline)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Status text based on state
                    when {
                        pttState is PttState.Transmitting -> {
                            Text(
                                text = "Transmitting...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD32F2F) // Red
                            )
                        }
                        currentSpeaker != null -> {
                            Text(
                                text = currentSpeaker.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary // Cyan
                            )
                        }
                        isLocked -> {
                            Text(
                                text = "Locked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                text = "Listening...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Right side: PTT button
                val isBusy = currentSpeaker != null && pttState !is PttState.Transmitting
                PttButton(
                    pttState = pttState,
                    pttMode = pttMode,
                    transmissionDuration = transmissionDuration,
                    isBusy = isBusy,
                    onPttPressed = onPttPressed,
                    onPttReleased = onPttReleased
                )
            } else {
                Text(
                    text = "No channel selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
