package com.voiceping.android.presentation.channels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(
    channel: Channel,
    isJoined: Boolean,
    isPrimary: Boolean,
    isMuted: Boolean,
    currentSpeaker: User?,
    lastSpeaker: User?,
    lastSpeakerVisible: Boolean,
    monitoredUserCount: Int? = null,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    // Determine if this channel is active (has current speaker, and not muted)
    val isActiveChannel = currentSpeaker != null && !isMuted

    // Animate cyan border color when channel becomes active (not for muted channels)
    val borderColor by animateColorAsState(
        targetValue = if (isActiveChannel) Color(0xFF00BCD4) else Color.Transparent,
        animationSpec = tween(300),
        label = "borderColor"
    )

    // Visual state: filled for joined, outlined for unjoined
    val backgroundColor = if (isJoined) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }

    val outlineColor = if (!isJoined) {
        MaterialTheme.colorScheme.outline
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = if (!isJoined) 1.dp else 2.dp,
                    color = if (!isJoined) outlineColor else borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = onLongPress
                )
                .padding(16.dp)
                .alpha(if (isMuted) 0.5f else 1.0f),  // Dimmed when muted
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Speaker indicator with pulsing animation (not shown when muted)
                if (currentSpeaker != null && !isMuted) {
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
                            text = currentSpeaker.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alpha(pulseAlpha)
                        )
                    }
                } else if (lastSpeakerVisible && lastSpeaker != null && !isMuted) {
                    // Last speaker fade animation (2-3 second fade) - not shown when muted
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
                if (currentSpeaker == null && !(lastSpeakerVisible && lastSpeaker != null)) {
                    val displayCount = monitoredUserCount ?: channel.userCount
                    Spacer(modifier = Modifier.padding(top = 2.dp))
                    Text(
                        text = "$displayCount users",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Settings icon for joined channels
            if (isJoined) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Channel settings",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Star badge for primary channel (top-right corner)
        if (isPrimary) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Primary channel",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.primary  // Cyan
            )
        }
    }
}
