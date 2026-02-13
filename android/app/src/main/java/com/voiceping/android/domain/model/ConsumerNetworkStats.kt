package com.voiceping.android.domain.model

/**
 * Network quality metrics from WebRTC consumer statistics.
 * Parsed from RTCStatsReport inbound-rtp entries.
 *
 * @param packetsLost Cumulative packets lost
 * @param jitter Inter-arrival jitter in seconds (convert to ms for display)
 * @param packetsReceived Total packets received (for loss percentage calc)
 * @param indicator Quality indicator: "Good", "Fair", or "Poor"
 */
data class ConsumerNetworkStats(
    val packetsLost: Long = 0,
    val jitter: Double = 0.0,
    val packetsReceived: Long = 0,
    val indicator: String = "Good"
) {
    val lossPercentage: Double
        get() = if (packetsReceived + packetsLost > 0) {
            (packetsLost.toDouble() / (packetsReceived + packetsLost)) * 100.0
        } else 0.0

    val jitterMs: Int
        get() = (jitter * 1000).toInt()

    companion object {
        fun calculateIndicator(packetsLost: Long, jitterMs: Int): String = when {
            packetsLost < 10 && jitterMs < 30 -> "Good"
            packetsLost < 50 && jitterMs < 100 -> "Fair"
            else -> "Poor"
        }
    }
}
