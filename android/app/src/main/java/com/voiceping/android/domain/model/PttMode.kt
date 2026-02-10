package com.voiceping.android.domain.model

/**
 * PTT button interaction modes.
 *
 * PRESS_AND_HOLD: Default mode - hold button to transmit, release to stop
 * TOGGLE: Press once to start, press again to stop (with max duration limit)
 */
enum class PttMode {
    /**
     * Press-and-hold mode (default)
     * User must hold PTT button for entire transmission duration
     */
    PRESS_AND_HOLD,

    /**
     * Toggle mode
     * Press once to start transmission, press again to stop
     * Has configurable max transmission duration (default 60 seconds)
     */
    TOGGLE
}
