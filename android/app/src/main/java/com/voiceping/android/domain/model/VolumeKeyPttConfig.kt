package com.voiceping.android.domain.model

/**
 * Volume key PTT configuration options.
 *
 * User opt-in control for which volume keys trigger PTT on long press (>300ms).
 * Short tap always adjusts volume normally, regardless of setting.
 *
 * Default: DISABLED (user must explicitly enable volume key PTT)
 */
enum class VolumeKeyPttConfig {
    /**
     * Volume keys function as normal volume controls only.
     * Long press does NOT trigger PTT.
     */
    DISABLED,

    /**
     * Volume UP key: short tap = volume up, long press = PTT
     */
    VOLUME_UP,

    /**
     * Volume DOWN key: short tap = volume down, long press = PTT
     */
    VOLUME_DOWN,

    /**
     * Both volume keys: short tap = volume adjustment, long press = PTT
     */
    BOTH
}
