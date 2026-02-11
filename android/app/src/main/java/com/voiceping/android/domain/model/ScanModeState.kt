package com.voiceping.android.domain.model

/**
 * Scan mode display state for bottom bar UI.
 *
 * Controls which channel is displayed, scan lock state, and return delay.
 * Default enabled=true per user decision: scan mode ON when 2+ channels joined.
 */
data class ScanModeState(
    val enabled: Boolean = true,
    val displayedChannelId: String? = null,
    val isLocked: Boolean = false,
    val returnDelaySeconds: Int = 2
)
