package com.voiceping.android.domain.model

/**
 * Per-channel monitoring state for multi-channel audio management.
 *
 * Tracks active speaker, timing, consumer ID, and volume for each monitored channel.
 * Used by scan mode logic to determine which channel to display and for audio mixing.
 */
data class ChannelMonitoringState(
    val channelId: String,
    val channelName: String,
    val teamName: String,
    val isPrimary: Boolean,
    val isMuted: Boolean = false,
    val currentSpeaker: User? = null,
    val lastSpeaker: User? = null,
    val speakerStartTime: Long = 0L,  // System.currentTimeMillis() when speaker started
    val consumerId: String? = null,     // mediasoup consumer ID for this channel
    val volume: Float = 1.0f,          // 0.0-1.0 range for per-channel volume
    val userCount: Int = 0             // number of users connected to this channel
)
