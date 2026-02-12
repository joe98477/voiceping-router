package com.voiceping.android.domain.model

/**
 * Audio output device type for indicator icon in top bar.
 *
 * Represents the current active audio output route detected by AudioRouter.
 * Used for visual indicator icon in the top bar to show user where audio is playing.
 *
 * Default: SPEAKER
 */
enum class AudioOutputDevice {
    /**
     * Device loudspeaker (speakerphone)
     */
    SPEAKER,

    /**
     * Earpiece (phone speaker used for calls)
     */
    EARPIECE,

    /**
     * Bluetooth headset/headphones
     */
    BLUETOOTH,

    /**
     * Wired headset/headphones (3.5mm jack or USB-C)
     */
    WIRED_HEADSET
}
