package com.voiceping.android.domain.model

/**
 * Audio output routing options for VoicePing app.
 *
 * SPEAKER: Loudspeaker output (default when no headset connected)
 * EARPIECE: Earpiece output (quiet/private)
 * BLUETOOTH: Bluetooth headset output (auto-selected when connected)
 */
enum class AudioRoute {
    /**
     * Loudspeaker output - loud, shared listening
     * Default when no headset or Bluetooth device connected
     */
    SPEAKER,

    /**
     * Earpiece output - quiet, private listening
     * User must explicitly select in settings
     */
    EARPIECE,

    /**
     * Bluetooth headset output
     * Automatically selected when Bluetooth audio device connects
     * Falls back to previous setting when Bluetooth disconnects
     */
    BLUETOOTH
}
