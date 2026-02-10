package com.voiceping.android.presentation.channels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voiceping.android.domain.model.Channel
import com.voiceping.android.domain.model.User

@Composable
fun ChannelRow(
    channel: Channel,
    isJoined: Boolean,
    onToggle: () -> Unit,
    lastSpeaker: User? = null,
    lastSpeakerVisible: Boolean = false
) {
    // Determine if this channel is active (has current speaker)
    val isActiveChannel = channel.currentSpeaker != null

    // Animate cyan border color when channel becomes active
    val borderColor by animateColorAsState(
        targetValue = if (isActiveChannel) Color(0xFF00BCD4) else Color.Transparent,
        animationSpec = tween(300),
        label = "borderColor"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onToggle)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Speaker indicator with pulsing animation
            if (channel.currentSpeaker != null) {
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing cyan animation on speaker name
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.6f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )

                    Text(
                        text = channel.currentSpeaker.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alpha(pulseAlpha)
                    )
                }
            } else if (lastSpeakerVisible && lastSpeaker != null) {
                // Last speaker fade animation (2-3 second fade)
                Spacer(modifier = Modifier.padding(top = 4.dp))
                AnimatedVisibility(
                    visible = lastSpeakerVisible,
                    exit = fadeOut(animationSpec = tween(2500))
                ) {
                    Text(
                        text = lastSpeaker.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // User count (only show when no speaker indicator)
            if (channel.currentSpeaker == null && !(lastSpeakerVisible && lastSpeaker != null)) {
                Spacer(modifier = Modifier.padding(top = 2.dp))
                Text(
                    text = "${channel.userCount} users",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Toggle checkbox (single channel only in Phase 5)
        Checkbox(
            checked = isJoined,
            onCheckedChange = { onToggle() }
        )
    }
}
