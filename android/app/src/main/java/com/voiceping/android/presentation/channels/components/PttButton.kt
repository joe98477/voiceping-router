package com.voiceping.android.presentation.channels.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.voiceping.android.data.ptt.PttState
import com.voiceping.android.domain.model.PttMode

/**
 * PTT button composable with press-and-hold/toggle modes, pulse animations, and elapsed time.
 *
 * Visual states per user decisions:
 * - Idle: Cyan primary color, Mic icon, enabled
 * - Requesting: Subtle gray loading pulse (NOT red - server confirmation wait)
 * - Transmitting: Red pulsing with elapsed time counter
 * - Denied: Brief dark red flash (handled by PttManager state, 500ms then back to idle)
 * - Busy: Dimmed/grayed out, not clickable when channel is busy
 *
 * @param pttState Current PTT state from PttManager
 * @param pttMode Interaction mode (PRESS_AND_HOLD or TOGGLE)
 * @param transmissionDuration Elapsed time in seconds (shown during Transmitting)
 * @param isBusy True when channel is busy (someone else speaking)
 * @param onPttPressed Callback when PTT button is pressed
 * @param onPttReleased Callback when PTT button is released
 * @param modifier Optional modifier
 */
@Composable
fun PttButton(
    pttState: PttState,
    pttMode: PttMode,
    transmissionDuration: Long,
    isBusy: Boolean,
    onPttPressed: () -> Unit,
    onPttReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTransmitting = pttState is PttState.Transmitting
    val isRequesting = pttState is PttState.Requesting
    val isDenied = pttState is PttState.Denied
    val isIdle = pttState is PttState.Idle

    // Determine button color based on state
    val buttonColor = when {
        isDenied -> Color(0xFFB71C1C) // Dark red for denied (brief flash)
        isTransmitting -> Color(0xFFD32F2F) // Red for transmitting
        isRequesting -> Color(0xFF9E9E9E) // Gray for requesting (loading)
        isBusy && isIdle -> Color(0xFF757575) // Dimmed gray when busy
        else -> MaterialTheme.colorScheme.primary // Cyan for idle/enabled
    }

    // Transmitting pulse animation (scale + alpha)
    val infiniteTransition = rememberInfiniteTransition(label = "pttPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Requesting pulse animation (alpha only, more subtle)
    val requestingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "requestingAlpha"
    )

    // Apply animations based on state
    val finalScale = if (isTransmitting) pulseScale else 1.0f
    val finalAlpha = when {
        isTransmitting -> pulseAlpha
        isRequesting -> requestingAlpha
        else -> 1.0f
    }

    // Determine if button is clickable
    val isClickable = !isBusy || isTransmitting

    // Button content
    val buttonModifier = modifier
        .size(72.dp)
        .scale(finalScale)
        .alpha(finalAlpha)
        .background(color = buttonColor, shape = CircleShape)

    Box(
        modifier = when (pttMode) {
            PttMode.PRESS_AND_HOLD -> {
                if (isClickable) {
                    buttonModifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onPttPressed()
                                tryAwaitRelease()
                                onPttReleased()
                            }
                        )
                    }
                } else {
                    buttonModifier
                }
            }
            PttMode.TOGGLE -> {
                if (isClickable) {
                    buttonModifier.clickable {
                        if (isTransmitting) {
                            onPttReleased()
                        } else {
                            onPttPressed()
                        }
                    }
                } else {
                    buttonModifier
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        if (isTransmitting) {
            // Show elapsed time during transmission
            Text(
                text = "${transmissionDuration}s",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        } else {
            // Show mic icon for all other states
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Push to Talk",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
