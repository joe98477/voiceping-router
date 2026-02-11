package com.voiceping.android.domain.model

/**
 * Audio mix mode for multi-channel monitoring.
 *
 * EQUAL_VOLUME: All channels play at same volume (default)
 * PRIMARY_PRIORITY: Primary channel plays louder, secondary channels ducked
 *
 * Configurable in profile drawer settings.
 */
enum class AudioMixMode {
    EQUAL_VOLUME,
    PRIMARY_PRIORITY
}
