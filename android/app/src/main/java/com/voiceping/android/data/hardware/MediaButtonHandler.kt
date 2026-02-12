package com.voiceping.android.data.hardware

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media button handler for Bluetooth headset button interception via MediaSession.
 *
 * Purpose:
 * Intercepts Bluetooth headset button presses (play/pause, next, previous, etc.) and routes them
 * to PTT actions. Uses Media3 MediaSession API which is the modern replacement for MediaSessionCompat.
 *
 * Features:
 * - Configurable button keycode (detected via press-to-detect screen)
 * - Detection mode for button learning (reports keyCode via callback)
 * - Only active when ChannelMonitoringService is running (avoids stealing media buttons from music apps)
 * - Handles common Bluetooth button codes: KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_HEADSETHOOK, etc.
 *
 * Integration:
 * - PttManager wires onPttPress/onPttRelease callbacks
 * - ChannelMonitoringService calls setActive(true) when service starts, setActive(false) when stops
 * - HardwareButtonSettingsScreen calls startDetectionMode() for press-to-detect
 *
 * CRITICAL: Only active when service is running to avoid interfering with music apps
 * (research pitfall #5: MediaSession must be released when not in use)
 */
@Singleton
class MediaButtonHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var isActive: Boolean = false
    private var isDetectionMode: Boolean = false

    // Configured button keycode (set by user via press-to-detect or settings)
    private var configuredKeyCode: Int = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE

    // Callbacks for PTT actions and button detection
    var onPttPress: (() -> Unit)? = null
    var onPttRelease: (() -> Unit)? = null
    var onButtonDetected: ((Int) -> Unit)? = null

    /**
     * Initialize MediaSession and ExoPlayer.
     *
     * Creates a minimal ExoPlayer instance (no actual playback) and a MediaSession that intercepts
     * media button events. The ExoPlayer is required by Media3 API but never used for playback.
     *
     * CRITICAL: Only called when service is active or detection mode is active.
     */
    fun initialize() {
        if (mediaSession != null) {
            Log.d(TAG, "MediaSession already initialized")
            return
        }

        Log.d(TAG, "Initializing MediaSession for Bluetooth button interception")

        // Create minimal ExoPlayer (required by Media3 MediaSession API)
        player = ExoPlayer.Builder(context).build()

        // Create MediaSession with callback that intercepts media button events
        mediaSession = MediaSession.Builder(context, player!!)
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null) {
                        return handleMediaButton(keyEvent)
                    }
                    return false
                }
            })
            .build()

        Log.d(TAG, "MediaSession initialized successfully")
    }

    /**
     * Handle media button events from Bluetooth headset.
     *
     * Detection mode: Reports keyCode via onButtonDetected callback (for press-to-detect screen).
     * Normal mode: Triggers PTT actions if keyCode matches configuredKeyCode.
     *
     * @param event KeyEvent from media button press
     * @return true if event was consumed, false otherwise
     */
    fun handleMediaButton(event: KeyEvent): Boolean {
        Log.d(TAG, "Media button event: keyCode=${event.keyCode}, action=${event.action}, repeatCount=${event.repeatCount}")

        // Detection mode: report any button press (first press only, ignore repeats)
        if (isDetectionMode) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.d(TAG, "Detection mode: button detected - keyCode=${event.keyCode}")
                onButtonDetected?.invoke(event.keyCode)
            }
            return true // Consume all events in detection mode
        }

        // Normal mode: check if keyCode matches configured button
        if (event.keyCode == configuredKeyCode) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        // First press only (ignore repeats)
                        Log.d(TAG, "Configured button pressed (keyCode=${event.keyCode})")
                        onPttPress?.invoke()
                    }
                }
                KeyEvent.ACTION_UP -> {
                    Log.d(TAG, "Configured button released (keyCode=${event.keyCode})")
                    onPttRelease?.invoke()
                }
            }
            return true // Consume event
        }

        // Not our configured button - don't consume
        Log.d(TAG, "Unhandled button keyCode=${event.keyCode} (configured=${configuredKeyCode})")
        return false
    }

    /**
     * Set MediaSession active state.
     *
     * Only activate MediaSession when ChannelMonitoringService is running to avoid stealing
     * media buttons from music apps when user is not in a channel.
     *
     * @param active true to activate MediaSession, false to release it
     */
    fun setActive(active: Boolean) {
        if (active == isActive) {
            return
        }

        Log.d(TAG, "Setting MediaSession active state: $active")

        if (active) {
            initialize()
        } else {
            if (!isDetectionMode) {
                // Only release if not in detection mode
                release()
            }
        }

        isActive = active
    }

    /**
     * Set configured button keycode.
     *
     * Called when user changes setting or after press-to-detect completes.
     *
     * @param keyCode KeyEvent keycode to trigger PTT (e.g., KEYCODE_MEDIA_PLAY_PAUSE)
     */
    fun setConfiguredKeyCode(keyCode: Int) {
        Log.d(TAG, "Setting configured keyCode: $keyCode")
        configuredKeyCode = keyCode
    }

    /**
     * Start button detection mode.
     *
     * Used by press-to-detect screen to learn which button user wants to use for PTT.
     * Any button press will be reported via onButtonDetected callback.
     */
    fun startDetectionMode() {
        Log.d(TAG, "Starting button detection mode")
        isDetectionMode = true
        if (mediaSession == null) {
            initialize()
        }
    }

    /**
     * Stop button detection mode.
     *
     * Returns to normal PTT mode. Releases MediaSession if service is not active.
     */
    fun stopDetectionMode() {
        Log.d(TAG, "Stopping button detection mode")
        isDetectionMode = false
        if (!isActive) {
            // Clean up if only started for detection
            release()
        }
    }

    /**
     * Release MediaSession and ExoPlayer.
     *
     * Called when service stops or detection mode ends (if service not running).
     * Releases resources and allows other apps to receive media button events.
     */
    fun release() {
        Log.d(TAG, "Releasing MediaSession and ExoPlayer")
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        isActive = false
    }

    companion object {
        private const val TAG = "MediaButtonHandler"

        // Common Bluetooth headset button keycodes
        const val KEYCODE_MEDIA_PLAY_PAUSE = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE // 85
        const val KEYCODE_HEADSETHOOK = KeyEvent.KEYCODE_HEADSETHOOK // 79
        const val KEYCODE_MEDIA_NEXT = KeyEvent.KEYCODE_MEDIA_NEXT // 87
        const val KEYCODE_MEDIA_PREVIOUS = KeyEvent.KEYCODE_MEDIA_PREVIOUS // 88
        const val KEYCODE_MEDIA_PLAY = KeyEvent.KEYCODE_MEDIA_PLAY // 126
        const val KEYCODE_MEDIA_PAUSE = KeyEvent.KEYCODE_MEDIA_PAUSE // 127
    }
}
