package com.voiceping.android.domain.model

/**
 * Network quality levels based on WebSocket latency.
 *
 * Maps latency thresholds to signal bars (1-4) for UI visualization.
 */
enum class NetworkQuality(val bars: Int) {
    EXCELLENT(4), // <100ms - LAN/WiFi optimal
    GOOD(3),      // 100-300ms - Normal WiFi/cellular
    FAIR(2),      // 300-600ms - Degraded connection
    POOR(1);      // >600ms or null - Unusable for PTT

    companion object {
        /**
         * Derive network quality from latency measurement.
         *
         * @param latencyMs Round-trip latency in milliseconds, or null if unavailable
         * @return NetworkQuality enum representing signal strength
         */
        fun fromLatency(latencyMs: Long?): NetworkQuality {
            return when {
                latencyMs == null -> POOR
                latencyMs < 100 -> EXCELLENT
                latencyMs < 300 -> GOOD
                latencyMs < 600 -> FAIR
                else -> POOR
            }
        }
    }
}
