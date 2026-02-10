package com.voiceping.android.data.audio

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Haptic feedback for PTT events using VibrationEffect.
 *
 * Provides distinct vibration patterns for:
 * - PTT press confirmation (short firm click)
 * - PTT error/denied (buzz-pause-buzz pattern)
 * - PTT release confirmation (subtle pulse)
 *
 * All vibrations check device capability before executing.
 * Compatible with API 26+ (matches minSdk 26).
 */
@Singleton
class HapticFeedback @Inject constructor(
    @ApplicationContext context: Context
) {
    private val vibrator: Vibrator? = try {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get Vibrator service", e)
        null
    }

    /**
     * Vibrate on PTT button press - short firm click.
     *
     * Pattern: Single pulse, 50ms duration, default amplitude
     * Purpose: Confirms user pressed PTT button
     */
    fun vibratePttPress() {
        try {
            if (vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
                Log.d(TAG, "PTT press vibration triggered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating on PTT press", e)
        }
    }

    /**
     * Vibrate on PTT error/denied - distinct buzz-pause-buzz pattern.
     *
     * Pattern: Buzz (100ms), pause (50ms), buzz (100ms)
     * Purpose: Clear tactile feedback that PTT was rejected (channel busy)
     * Distinct from press confirmation - user immediately knows something is wrong
     */
    fun vibrateError() {
        try {
            if (vibrator?.hasVibrator() == true) {
                // Waveform: [delay, on, off, on]
                // timings: [0ms delay, 100ms on, 50ms off, 100ms on]
                // amplitudes: -1 means "repeat off" (no repeat)
                val timings = longArrayOf(0, 100, 50, 100)
                val effect = VibrationEffect.createWaveform(timings, -1)
                vibrator.vibrate(effect)
                Log.d(TAG, "PTT error vibration triggered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating on PTT error", e)
        }
    }

    /**
     * Vibrate on PTT release - subtle pulse.
     *
     * Pattern: Single pulse, 30ms duration, half amplitude
     * Purpose: Subtle confirmation that PTT button was released
     * Softer than press to distinguish release from press
     */
    fun vibrateRelease() {
        try {
            if (vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE / 2)
                vibrator.vibrate(effect)
                Log.d(TAG, "PTT release vibration triggered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating on PTT release", e)
        }
    }

    companion object {
        private const val TAG = "HapticFeedback"
    }
}
