package com.voiceping.android.presentation.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme

/**
 * Helper function to map keycodes to friendly names for Bluetooth button detection.
 */
fun keyCodeToName(keyCode: Int): String {
    return when (keyCode) {
        85 -> "Play/Pause"
        79 -> "Headset Hook"
        87 -> "Next Track"
        88 -> "Previous Track"
        126 -> "Play"
        127 -> "Pause"
        else -> "Key $keyCode"
    }
}

/**
 * Dialog for detecting Bluetooth headset button presses.
 *
 * Shows a press-to-detect UI that waits for user to press any button
 * on their Bluetooth headset, then displays the detected button and
 * allows confirmation.
 *
 * @param isOpen Whether the dialog is visible
 * @param detectedKeyCode The keycode of the detected button (null if none detected yet)
 * @param detectedKeyName The friendly name of the detected button
 * @param onDismiss Called when user cancels the dialog
 * @param onConfirm Called when user confirms the detected button (Int is the keycode)
 */
@Composable
fun ButtonDetectionDialog(
    isOpen: Boolean,
    detectedKeyCode: Int?,
    detectedKeyName: String?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Detect Bluetooth Button")
        },
        text = {
            if (detectedKeyCode == null) {
                Text(
                    text = "Press any button on your Bluetooth headset...\n\nTry the play/pause or call button on your headset",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Detected: $detectedKeyName (code: $detectedKeyCode)",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            if (detectedKeyCode != null) {
                TextButton(
                    onClick = { onConfirm(detectedKeyCode) }
                ) {
                    Text("Use This Button")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
