package com.voiceping.android.domain.model

/**
 * PTT state machine for Push-To-Talk interactions.
 *
 * States:
 * - Idle: Default resting state, no transmission
 * - Requesting: Waiting for server confirmation (shows subtle loading pulse)
 * - Transmitting: Server confirmed, mic active (shows red pulse + elapsed time)
 * - Denied: Channel busy, PTT rejected (shows error state briefly)
 */
sealed class PttState {
    /**
     * Default resting state - no transmission, PTT button available
     */
    data object Idle : PttState()

    /**
     * Waiting for server confirmation after PTT press
     * Shows subtle loading pulse on PTT button
     */
    data object Requesting : PttState()

    /**
     * Server confirmed transmission, mic is active
     * Shows red pulse animation + elapsed time counter
     *
     * @param startTime Epoch milliseconds when transmission started (for elapsed time display)
     */
    data class Transmitting(val startTime: Long) : PttState()

    /**
     * PTT request denied - channel is busy
     * Shows error state briefly with error tone + haptic feedback
     */
    data object Denied : PttState()
}
