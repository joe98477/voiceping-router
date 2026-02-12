package com.voiceping.android.presentation.channels.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.voiceping.android.domain.model.NetworkQuality
import com.voiceping.android.domain.model.NetworkType

/**
 * Network quality indicator composable with signal bars and tap-to-reveal detail popup.
 *
 * Visual form: 4 vertical signal bars (like cellular signal strength).
 * Tap action: Shows popup with latency (ms), connection type, and server name.
 *
 * @param latency Round-trip latency in milliseconds, or null if unavailable
 * @param networkType Current network connection type (WIFI, CELLULAR, NONE, OTHER)
 * @param serverUrl Server URL for display in detail popup
 * @param modifier Modifier for layout customization
 */
@Composable
fun NetworkQualityIndicator(
    latency: Long?,
    networkType: NetworkType,
    serverUrl: String,
    modifier: Modifier = Modifier
) {
    val quality = NetworkQuality.fromLatency(latency)
    var showPopup by remember { mutableStateOf(false) }

    // Color based on quality
    val barColor = when (quality) {
        NetworkQuality.EXCELLENT, NetworkQuality.GOOD -> MaterialTheme.colorScheme.primary
        NetworkQuality.FAIR -> Color(0xFFFFA726) // Amber/yellow
        NetworkQuality.POOR -> MaterialTheme.colorScheme.error
    }

    Box(modifier = modifier) {
        // Signal bars icon button
        IconButton(
            onClick = { showPopup = !showPopup },
            modifier = Modifier.size(40.dp)
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                val barWidth = 3.dp.toPx()
                val spacing = 2.dp.toPx()
                val maxHeight = size.height
                val bars = quality.bars

                // Draw 4 bars of increasing height
                for (i in 0 until 4) {
                    val barHeight = maxHeight * (i + 1) / 4f
                    val x = i * (barWidth + spacing)
                    val color = if (i < bars) barColor else barColor.copy(alpha = 0.3f)

                    drawLine(
                        color = color,
                        start = Offset(x, maxHeight),
                        end = Offset(x, maxHeight - barHeight),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Quality detail popup
        DropdownMenu(
            expanded = showPopup,
            onDismissRequest = { showPopup = false }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Latency: ${latency?.let { "${it}ms" } ?: "--"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Type: ${networkType.name}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Server: $serverUrl",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
