package com.voiceping.android.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio routing manager for VoicePing app.
 *
 * Handles:
 * - Audio output routing (earpiece vs speaker)
 * - Audio focus management (pausing during phone calls, resuming after)
 *
 * Default: Earpiece mode (quiet/private) - MODE_IN_COMMUNICATION with speakerphone off.
 * User can toggle to speaker mode in Phase 6 settings.
 */
@Singleton
class AudioRouter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var modeControlEnabled = true

    // Phone call detection callbacks (wired by ChannelRepository in Plan 03)
    var onPhoneCallStarted: (() -> Unit)? = null
    var onPhoneCallEnded: (() -> Unit)? = null

    // Track phone call state
    private var isInPhoneCall = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Phone call started (incoming or outgoing)
                // User decision: immediate pause of all channel audio (no fade)
                Log.d(TAG, "Audio focus lost (transient): phone call detected")
                isInPhoneCall = true
                onPhoneCallStarted?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isInPhoneCall) {
                    // Phone call ended — resume audio
                    // User decision: auto-resume channel audio immediately, no delay
                    Log.d(TAG, "Audio focus regained after phone call: resuming")
                    isInPhoneCall = false
                    onPhoneCallEnded?.invoke()
                } else {
                    Log.d(TAG, "Audio focus regained (not from phone call)")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Another app wants to duck (e.g., navigation prompt)
                // User decision: duck other audio apps, restore volume after
                // System handles ducking automatically on API 26+ when setWillPauseWhenDucked(false)
                Log.d(TAG, "Audio focus: ducking for transient sound")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss (e.g., music app started playing)
                // User decision: duck our audio, don't pause. Radio takes priority but doesn't kill music.
                // Note: With AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, music apps duck us,
                // but if they request permanent focus, we log it. Audio continues playing.
                Log.d(TAG, "Audio focus lost permanently: another app claimed focus")
            }
        }
    }

    /**
     * Disable AudioManager mode control (called after WebRTC PeerConnectionFactory init).
     * WebRTC's AudioDeviceModule will own MODE_IN_COMMUNICATION.
     * AudioRouter continues to handle routing (speakerphone, Bluetooth device selection).
     */
    fun disableModeControl() {
        modeControlEnabled = false
        Log.d(TAG, "AudioManager mode control disabled (WebRTC owns MODE_IN_COMMUNICATION)")
    }

    /**
     * Set audio routing to earpiece (default for receive-only mode).
     *
     * Mode: MODE_IN_COMMUNICATION enables echo cancellation and noise suppression.
     * Speakerphone: OFF routes audio to earpiece (quiet/private).
     */
    fun setEarpieceMode() {
        Log.d(TAG, "Setting audio mode: earpiece")
        if (modeControlEnabled) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        audioManager.isSpeakerphoneOn = false
    }

    /**
     * Set audio routing to speaker (loud mode).
     *
     * Mode: MODE_IN_COMMUNICATION enables echo cancellation and noise suppression.
     * Speakerphone: ON routes audio to speaker (loud/shared).
     */
    fun setSpeakerMode() {
        Log.d(TAG, "Setting audio mode: speaker")
        if (modeControlEnabled) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        audioManager.isSpeakerphoneOn = true
    }

    /**
     * Request audio focus for voice communication.
     *
     * Focus type: AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK allows ducking other audio
     * (e.g., music playing in background gets quieter during PTT).
     *
     * Usage: USAGE_VOICE_COMMUNICATION signals to system this is voice audio.
     */
    fun requestAudioFocus() {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(false) // Enable automatic ducking (API 26+)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus granted (with phone call listener)")
            audioFocusRequest = focusRequest
        } else {
            Log.w(TAG, "Audio focus request failed: $result")
        }
    }

    /**
     * Release audio focus when no longer needed.
     */
    fun releaseAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            Log.d(TAG, "Audio focus released")
            audioFocusRequest = null
        }
        isInPhoneCall = false
    }

    /**
     * Check if currently in a phone call (detected via audio focus loss).
     */
    fun isInPhoneCall(): Boolean = isInPhoneCall

    /**
     * Reset audio mode to normal (when leaving channel).
     */
    fun resetAudioMode() {
        Log.d(TAG, "Resetting audio mode to normal")
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    /**
     * Set audio routing to Bluetooth device.
     *
     * On API 31+: Uses setCommunicationDevice (modern API).
     * On API 26-30: Uses startBluetoothSco + isBluetoothScoOn flag (legacy).
     *
     * @param device The Bluetooth AudioDeviceInfo to route audio to
     */
    fun setBluetoothMode(device: AudioDeviceInfo) {
        Log.d(TAG, "Setting audio mode: Bluetooth (${device.productName}, type=${device.type})")

        // Reject A2DP devices — they are media-only and cannot be communication devices
        if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
            Log.w(TAG, "Rejecting A2DP device for communication routing (media-only)")
            return
        }

        if (modeControlEnabled) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: Modern API
            // Only SCO and BLE devices can be set as communication devices
            try {
                val result = audioManager.setCommunicationDevice(device)
                if (result) {
                    Log.d(TAG, "Bluetooth communication device set successfully")
                } else {
                    Log.w(TAG, "Failed to set Bluetooth communication device")
                }
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Device type ${device.type} not valid for communication: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error setting communication device: ${e.message}")
            }
        } else {
            // API 26-30: Legacy SCO API
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            Log.d(TAG, "Bluetooth SCO started (legacy API)")
        }
    }

    /**
     * Set audio routing to wired headset (headphones or headset with mic).
     *
     * Wired headset is automatically selected by the system when connected,
     * just need to disable speakerphone and set MODE_IN_COMMUNICATION.
     */
    fun setWiredHeadsetMode() {
        Log.d(TAG, "Setting audio mode: Wired headset")
        if (modeControlEnabled) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        audioManager.isSpeakerphoneOn = false
    }

    /**
     * Clear communication device (used when Bluetooth/wired headset disconnects).
     *
     * On API 31+: Calls clearCommunicationDevice.
     * On API 26-30: Stops Bluetooth SCO.
     */
    fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
            Log.d(TAG, "Communication device cleared")
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            Log.d(TAG, "Bluetooth SCO stopped (legacy API)")
        }
    }

    /**
     * Get the AudioManager instance.
     * Allows AudioDeviceManager to register AudioDeviceCallback on the same instance.
     */
    fun getAudioManager(): AudioManager = audioManager

    companion object {
        private const val TAG = "AudioRouter"
    }
}
