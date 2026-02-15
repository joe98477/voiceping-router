package com.voiceping.android.domain.model

/**
 * Power management state.
 *
 * Tracks wake lock status, battery saver mode, and location multiplier
 * for adaptive power optimization.
 */
data class PowerState(
    val wakeLockActive: Boolean = false,
    val batterySaverEnabled: Boolean = false,
    val wakeLockTimeoutMs: Long = 300_000L,
    val locationMultiplier: Int = 1 // 1x normal, 2x wake lock released, 4x battery saver
)
