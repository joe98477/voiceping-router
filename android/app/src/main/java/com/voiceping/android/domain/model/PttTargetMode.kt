package com.voiceping.android.domain.model

/**
 * PTT target mode for multi-channel scenario.
 *
 * ALWAYS_PRIMARY: PTT always transmits to primary channel (default)
 * DISPLAYED_CHANNEL: PTT transmits to currently displayed channel in scan mode
 *
 * Configurable in profile drawer settings.
 */
enum class PttTargetMode {
    ALWAYS_PRIMARY,
    DISPLAYED_CHANNEL
}
