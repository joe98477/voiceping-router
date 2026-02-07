package com.voiceping.android.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for networking layer dependencies.
 *
 * This module exists primarily for documentation and organization.
 * Since SignalingClient, MediasoupClient, and AudioRouter all use
 * @Inject constructor + @Singleton annotation, Hilt constructs them
 * automatically without explicit @Provides methods.
 *
 * Future configuration values (e.g., server URL, timeouts) can be
 * provided here if needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // All networking classes use @Inject constructor, so no @Provides needed
    // SignalingClient - WebSocket client
    // MediasoupClient - mediasoup Device wrapper
    // AudioRouter - Audio routing manager
}
