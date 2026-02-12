package com.voiceping.android.data.hardware

import android.util.Log
import android.view.KeyEvent
import com.voiceping.android.data.storage.SettingsRepository
import com.voiceping.android.domain.model.VolumeKeyPttConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware key handler for volume key dual-purpose PTT logic.
 *
 * Implements user decision: volume keys have dual-purpose behavior when configured:
 * - Short tap (<300ms): Normal volume adjustment (system handles)
 * - Long press (>=300ms): PTT activation via callbacks
 *
 * CRITICAL per research: Do NOT consume the initial ACTION_DOWN event.
 * Volume adjustment must work for short taps. Only consume events after
 * long press threshold is confirmed via repeat events.
 *
 * Configuration:
 * - User can enable PTT for VOLUME_UP, VOLUME_DOWN, BOTH, or DISABLED (default)
 * - Long press threshold is configurable (default 300ms)
 * - Settings are read from SettingsRepository cached accessors (safe for main thread)
 *
 * Integration:
 * - Activity.dispatchKeyEvent() calls handleKeyEvent() before system processes key
 * - Returns true to consume event (PTT), false to pass through (volume)
 * - onPttPress/onPttRelease callbacks notify PttManager to start/stop transmission
 */
@Singleton
class HardwareKeyHandler @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    /**
     * State tracking for current key press.
     */
    private var keyDownTime: Long = 0
    private var isLongPressActive: Boolean = false
    private var activeKeyCode: Int = 0

    /**
     * Callbacks for PTT start/stop.
     * Wired by ChannelRepository or MainActivity to PttManager.requestPtt/releasePtt.
     */
    var onPttPress: (() -> Unit)? = null
    var onPttRelease: (() -> Unit)? = null

    /**
     * Check if volume key PTT is enabled for the given key.
     *
     * @param keyCode KeyEvent.KEYCODE_VOLUME_UP or KEYCODE_VOLUME_DOWN
     * @return true if this key should activate PTT on long press
     */
    fun isVolumeKeyPttEnabled(keyCode: Int): Boolean {
        val config = settingsRepository.getCachedVolumeKeyPttConfig()
        return when (config) {
            VolumeKeyPttConfig.DISABLED -> false
            VolumeKeyPttConfig.VOLUME_UP -> keyCode == KeyEvent.KEYCODE_VOLUME_UP
            VolumeKeyPttConfig.VOLUME_DOWN -> keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            VolumeKeyPttConfig.BOTH -> keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        }
    }

    /**
     * Handle volume key event for dual-purpose PTT logic.
     *
     * Flow:
     * 1. ACTION_DOWN (repeatCount == 0): Record timestamp, return FALSE (allow volume adjustment)
     * 2. ACTION_DOWN (repeatCount > 0): Check duration, activate PTT if >= threshold, return TRUE (consume)
     * 3. ACTION_UP: If was long press, release PTT and return TRUE (consume). Else return FALSE (short tap, allow volume).
     *
     * @param event KeyEvent from Activity.dispatchKeyEvent()
     * @return true to consume event (PTT), false to pass through (volume)
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // Only handle volume keys that are configured for PTT
        if (!isVolumeKeyPttEnabled(keyCode)) {
            return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    // Initial press: record timestamp, allow volume adjustment
                    keyDownTime = event.eventTime
                    activeKeyCode = keyCode
                    isLongPressActive = false
                    Log.d(TAG, "Volume key DOWN (initial): keyCode=$keyCode")
                    return false // DON'T consume - allow system volume adjustment for short tap

                } else {
                    // Repeat event (held): check if long press threshold reached
                    val duration = event.eventTime - keyDownTime
                    val threshold = settingsRepository.getCachedLongPressThresholdMs()

                    if (duration >= threshold && !isLongPressActive) {
                        // Long press threshold reached: activate PTT
                        isLongPressActive = true
                        Log.d(TAG, "Volume key LONG PRESS: duration=${duration}ms, threshold=${threshold}ms")
                        onPttPress?.invoke()
                        return true // Consume - prevent further volume adjustment
                    }

                    if (isLongPressActive) {
                        // PTT already active: consume repeat events
                        return true
                    }

                    // Still under threshold: pass through
                    return false
                }
            }

            KeyEvent.ACTION_UP -> {
                // Key released: check if this was a long press or short tap
                val wasLongPress = isLongPressActive

                // Reset state
                isLongPressActive = false
                keyDownTime = 0
                activeKeyCode = 0

                if (wasLongPress) {
                    // Long press: release PTT and consume event
                    Log.d(TAG, "Volume key UP (after long press): releasing PTT")
                    onPttRelease?.invoke()
                    return true // Consume - don't adjust volume after PTT release
                } else {
                    // Short tap: let system handle volume adjustment
                    Log.d(TAG, "Volume key UP (short tap): allowing volume adjustment")
                    return false
                }
            }

            else -> {
                return false
            }
        }
    }

    companion object {
        private const val TAG = "HardwareKeyHandler"
    }
}
