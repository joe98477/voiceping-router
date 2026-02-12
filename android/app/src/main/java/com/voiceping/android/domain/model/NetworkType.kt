package com.voiceping.android.domain.model

/**
 * Network connection type.
 *
 * Used by NetworkMonitor to track current connectivity type.
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    NONE,
    OTHER
}
