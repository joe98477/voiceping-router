package com.voiceping.android.presentation.channels.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voiceping.android.domain.model.TransmissionHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transmission history bottom sheet composable.
 *
 * Access: Long-press on channel row opens this bottom sheet.
 * Content: Shows last 20 transmissions per channel with speaker name, timestamp, and duration.
 * Session only (in-memory): clears on app restart.
 *
 * @param channelName Name of channel for display in title
 * @param history List of transmission entries in reverse chronological order (newest first)
 * @param onDismiss Callback when bottom sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransmissionHistorySheet(
    channelName: String,
    history: List<TransmissionHistoryEntry>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Title
            Text(
                text = "$channelName - History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // History list
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transmissions yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(history) { entry ->
                        val timestamp = timeFormat.format(Date(entry.timestamp))
                        val duration = "${entry.durationSeconds}s"

                        ListItem(
                            headlineContent = {
                                Text(entry.speakerName)
                            },
                            supportingContent = {
                                Text("$timestamp - $duration")
                            },
                            leadingContent = {
                                // Small circle indicator (primary for own transmission, onSurfaceVariant for others)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (entry.isOwnTransmission) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            shape = CircleShape
                                        )
                                )
                            }
                        )
                    }

                    // Bottom padding for navigation bar insets
                    item {
                        Spacer(modifier = Modifier.padding(bottom = 32.dp))
                    }
                }
            }
        }
    }
}
