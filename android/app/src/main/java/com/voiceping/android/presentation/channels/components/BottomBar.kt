package com.voiceping.android.presentation.channels.components

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
import com.voiceping.android.domain.model.Channel
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.PttState
import com.voiceping.android.domain.model.User

@Composable
fun BottomBar(
    joinedChannel: Channel?,
    currentSpeaker: User?,
    pttState: PttState,
    pttMode: PttMode,
    transmissionDuration: Long,
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
            if (joinedChannel != null) {
                // Left side: channel info and status
                Column {
                    Text(
                        text = joinedChannel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Status text based on state
                    val isUserTransmitting = pttState is PttState.Transmitting
                    val isChannelBusy = currentSpeaker != null && !isUserTransmitting

                    when {
                        isUserTransmitting -> {
                            Text(
                                text = "Transmitting...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD32F2F) // Red
                            )
                        }
                        isChannelBusy -> {
                            Text(
                                text = currentSpeaker!!.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary // Cyan
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
