package com.voiceping.android.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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

    /**
     * Set audio routing to earpiece (default for receive-only mode).
     *
     * Mode: MODE_IN_COMMUNICATION enables echo cancellation and noise suppression.
     * Speakerphone: OFF routes audio to earpiece (quiet/private).
     */
    fun setEarpieceMode() {
        Log.d(TAG, "Setting audio mode: earpiece")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
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
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
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
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus granted")
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
    }

    /**
     * Reset audio mode to normal (when leaving channel).
     */
    fun resetAudioMode() {
        Log.d(TAG, "Resetting audio mode to normal")
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    companion object {
        private const val TAG = "AudioRouter"
    }
}
