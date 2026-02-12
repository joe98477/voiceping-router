package com.voiceping.android.domain.model

/**
 * Single transmission history entry for a channel.
 *
 * Captures speaker name, duration, and timestamp for UX-03 transmission history feature.
 * Session-only (in-memory), clears on app restart.
 */
data class TransmissionHistoryEntry(
    val speakerName: String,
    val timestamp: Long,           // System.currentTimeMillis() when transmission ended
    val durationSeconds: Int,       // Duration in seconds
    val channelId: String,
    val isOwnTransmission: Boolean  // true if user was the speaker
)
