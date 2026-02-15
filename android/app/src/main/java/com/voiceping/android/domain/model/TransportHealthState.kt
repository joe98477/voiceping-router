package com.voiceping.android.domain.model

/**
 * Transport health state for monitoring send/receive transport connectivity.
 *
 * States:
 * - HEALTHY: Both send and receive transports connected or available
 * - SEND_DEGRADED: Send transport failed/disconnected but receive still works (amber PTT)
 * - FULLY_DISCONNECTED: Both transports failed, auto-rejoin needed
 * - RECONNECTING: Auto-rejoin in progress, PTT disabled
 */
enum class TransportHealthState {
    HEALTHY,
    SEND_DEGRADED,
    FULLY_DISCONNECTED,
    RECONNECTING
}
