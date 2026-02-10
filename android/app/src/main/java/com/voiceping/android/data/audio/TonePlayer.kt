package com.voiceping.android.data.audio

import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Log
import com.voiceping.android.data.storage.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio tone feedback for PTT events using ToneGenerator.
 *
 * Generates distinct tones for:
 * - PTT start chirp (configurable)
 * - Roger beep / TX end chirp (configurable, default on)
 * - RX squelch open (configurable)
 * - RX squelch close (configurable, tied to RX squelch toggle)
 * - Error tone (always plays, not configurable)
 *
 * All tones check SettingsRepository for user preferences before playing.
 * Designed for radio-style audio feedback familiar to field workers.
 *
 * Volume: 50% (balanced - audible but not jarring)
 * Stream: STREAM_VOICE_CALL (matches voice audio, respects call volume)
 */
@Singleton
class TonePlayer @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            // Initialize ToneGenerator with 50% volume on voice call stream
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 50)
            Log.d(TAG, "ToneGenerator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Play PTT start tone - short high chirp on PTT button press.
     *
     * Tone: DTMF 1 (1209 Hz + 697 Hz) for 100ms
     * Configurable: Yes (user can disable in settings)
     * Purpose: Confirms PTT button press acknowledged
     */
    fun playPttStartTone() {
        try {
            if (settingsRepository.getCachedPttStartToneEnabled()) {
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_1, 100)
                Log.d(TAG, "Playing PTT start tone")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing PTT start tone", e)
        }
    }

    /**
     * Play roger beep - short low chirp when transmission ends.
     *
     * Tone: DTMF 0 (1336 Hz + 941 Hz) for 150ms
     * Configurable: Yes (user can disable in settings, default ON)
     * Purpose: Confirms transmission ended, classic radio "roger beep"
     */
    fun playRogerBeep() {
        try {
            if (settingsRepository.getCachedRogerBeepEnabled()) {
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_0, 150)
                Log.d(TAG, "Playing roger beep")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing roger beep", e)
        }
    }

    /**
     * Play RX squelch open - brief squelch when incoming transmission starts.
     *
     * Tone: PROP_NACK (400 Hz + 200 Hz) for 80ms
     * Configurable: Yes (user can enable/disable RX squelch in settings)
     * Purpose: Radio-style squelch open sound when someone starts speaking
     */
    fun playRxSquelchOpen() {
        try {
            if (settingsRepository.getCachedRxSquelchEnabled()) {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 80)
                Log.d(TAG, "Playing RX squelch open")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing RX squelch open", e)
        }
    }

    /**
     * Play RX squelch close - brief squelch tail when incoming transmission ends.
     *
     * Tone: PROP_NACK (400 Hz + 200 Hz) for 60ms
     * Configurable: Yes (tied to RX squelch toggle)
     * Purpose: Radio-style squelch close sound when transmission finishes
     */
    fun playRxSquelchClose() {
        try {
            if (settingsRepository.getCachedRxSquelchEnabled()) {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 60)
                Log.d(TAG, "Playing RX squelch close")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing RX squelch close", e)
        }
    }

    /**
     * Play error tone - distinct double beep when PTT is denied.
     *
     * Tone: PROP_BEEP2 (two beeps: 2 x 100ms @ 400Hz + 800Hz)
     * Configurable: No (always plays)
     * Purpose: Clear audio feedback that PTT was rejected (channel busy)
     */
    fun playErrorTone() {
        try {
            // Always play error tone (no toggle) - user needs to know PTT was denied
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
            Log.d(TAG, "Playing error tone")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing error tone", e)
        }
    }

    /**
     * Play call interruption beep - distinct double beep when phone call interrupts PTT.
     *
     * Tone: Two DTMF_A tones (697 Hz + 1633 Hz) separated by 100ms pause
     * Configurable: No (always plays - user must know PTT was interrupted)
     * Purpose: Signals to other channel users that the speaker was interrupted
     *          by a phone call (distinct from normal roger beep which means
     *          intentional stop)
     */
    fun playCallInterruptionBeep() {
        try {
            // Always play (no toggle) - signals call interruption to other users
            // First beep
            toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 100)
            // Schedule second beep after pause
            // Note: ToneGenerator.startTone is async, so we use Thread.sleep for timing
            Thread.sleep(200) // 100ms tone + 100ms pause
            toneGenerator?.startTone(ToneGenerator.TONE_DTMF_A, 100)
            Log.d(TAG, "Playing call interruption double beep")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing call interruption beep", e)
        }
    }

    /**
     * Cleanup ToneGenerator resources.
     * Call when TonePlayer is no longer needed.
     */
    fun cleanup() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            Log.d(TAG, "ToneGenerator released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ToneGenerator", e)
        }
    }

    companion object {
        private const val TAG = "TonePlayer"
    }
}
