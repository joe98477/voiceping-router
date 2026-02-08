package com.voiceping.android.data.network.dto

/**
 * Response from POST /api/router/token.
 * Returns JWT for WebSocket authentication and channel name mapping.
 */
data class RouterTokenResponse(
    val token: String,
    val channelNames: Map<String, String>
)
