package com.voiceping.android.data.location

import com.google.gson.JsonObject
import com.voiceping.android.domain.model.MotionState

/**
 * Location update data class for WebSocket transmission to server.
 *
 * Contains GPS coordinates, accuracy, movement metrics, detected motion state, and timestamp.
 * Provides JSON serialization for wire protocol.
 */
data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,        // Accuracy in meters
    val speed: Float?,          // Speed in m/s (null if unavailable)
    val heading: Float?,        // Bearing in degrees (null if unavailable)
    val motionState: MotionState,
    val timestamp: String       // ISO8601 UTC string
) {
    /**
     * Convert to JsonObject for WebSocket transmission.
     * Uses Gson's JsonObject builder for wire format.
     */
    fun toJsonObject(): JsonObject {
        return JsonObject().apply {
            addProperty("lat", latitude)
            addProperty("lng", longitude)
            addProperty("accuracy", accuracy)
            if (speed != null) {
                addProperty("speed", speed)
            }
            if (heading != null) {
                addProperty("heading", heading)
            }
            addProperty("motionState", motionState.toWireFormat())
            addProperty("timestamp", timestamp)
        }
    }
}
