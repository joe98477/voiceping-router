package com.voiceping.android.domain.model

/**
 * User motion state detected from ActivityTransition API or GPS displacement.
 *
 * Used to optimize location tracking intervals:
 * - STILL: 5 minutes (stationary users don't need frequent updates)
 * - WALKING: 60 seconds (moderate movement)
 * - DRIVING: 30 seconds (fast movement requires more frequent updates)
 * - UNKNOWN: 60 seconds (default/fallback)
 */
enum class MotionState {
    STILL,    // User is stationary
    WALKING,  // User is walking (includes cycling per user decision)
    DRIVING,  // User is in a vehicle
    UNKNOWN;  // Initial state or detection unavailable

    /**
     * Convert to wire format for WebSocket transmission.
     * Returns lowercase string representation.
     */
    fun toWireFormat(): String {
        return when (this) {
            STILL -> "still"
            WALKING -> "walking"
            DRIVING -> "driving"
            UNKNOWN -> "unknown"
        }
    }
}
