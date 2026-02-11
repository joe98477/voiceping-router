package com.voiceping.android.presentation.channels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ChannelVolumeDialog(
    channelName: String,
    volume: Float,          // 0.0-1.0
    isMuted: Boolean,
    onVolumeChanged: (Float) -> Unit,
    onMuteToggled: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channelName) },
        text = {
            Column {
                // Volume slider
                Text("Volume: ${(volume * 100).roundToInt()}%")
                Slider(
                    value = volume,
                    onValueChange = onVolumeChanged,
                    enabled = !isMuted,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Mute toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isMuted) "Muted" else "Active")
                    Switch(
                        checked = !isMuted,
                        onCheckedChange = { onMuteToggled() }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
