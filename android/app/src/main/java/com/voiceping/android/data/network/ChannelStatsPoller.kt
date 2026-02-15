package com.voiceping.android.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-channel network stats polling coordinator with adaptive intervals.
 *
 * Polling intervals:
 * - Active (60s activity window): 5 seconds
 * - Idle (no activity for 60s): 15 seconds
 * - Empty (0 consumers): suspended (1s check loop)
 *
 * Activity markers:
 * - New channel join counts as initial activity
 * - SPEAKER_CHANGED with speaker → markActivity()
 * - Consumer count increase from 0 → triggers activity
 *
 * Note: Consumer.getStats() API from crow-misia library is undocumented.
 * Polling infrastructure is implemented, but stats parsing is stubbed pending device validation.
 */
@Singleton
class ChannelStatsPoller @Inject constructor(
    private val signalingClient: SignalingClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channelStates = ConcurrentHashMap<String, ChannelPollState>()

    private val _channelIntervals = MutableStateFlow<Map<String, Long>>(emptyMap())
    val channelIntervals: StateFlow<Map<String, Long>> = _channelIntervals.asStateFlow()

    /**
     * Per-channel polling state.
     *
     * @param channelId Channel ID
     * @param lastActivityMs Timestamp of last audio activity
     * @param currentIntervalMs Current polling interval (5000L or 15000L)
     * @param consumerCount Number of active consumers (0 = empty, suspend polling)
     * @param pollingJob Coroutine job for this channel's polling loop
     */
    data class ChannelPollState(
        val channelId: String,
        var lastActivityMs: Long,
        var currentIntervalMs: Long,
        var consumerCount: Int,
        var pollingJob: Job?
    )

    /**
     * Start polling for a channel.
     * Initial join counts as activity per user decision.
     */
    fun startPolling(channelId: String) {
        if (channelStates.containsKey(channelId)) {
            Log.d(TAG, "Channel $channelId already polling")
            return
        }

        val state = ChannelPollState(
            channelId = channelId,
            lastActivityMs = System.currentTimeMillis(), // Join = activity
            currentIntervalMs = ACTIVE_INTERVAL_MS,
            consumerCount = 0,
            pollingJob = null
        )

        channelStates[channelId] = state

        val job = scope.launch {
            while (isActive && channelStates.containsKey(channelId)) {
                val currentState = channelStates[channelId] ?: break

                // Suspend polling if channel is empty (zero consumers)
                if (currentState.consumerCount <= 0) {
                    delay(1000L) // Check again in 1s
                    continue
                }

                // Calculate interval based on 60s activity window
                val elapsed = System.currentTimeMillis() - currentState.lastActivityMs
                val interval = if (elapsed <= ACTIVITY_WINDOW_MS) ACTIVE_INTERVAL_MS else IDLE_INTERVAL_MS

                currentState.currentIntervalMs = interval
                updateIntervalFlow()

                // Poll stats (stub: crow-misia Consumer.getStats() undocumented)
                Log.d(TAG, "Polling channel $channelId: interval=${interval}ms, consumers=${currentState.consumerCount}")

                delay(interval)
            }
        }

        state.pollingJob = job
        Log.d(TAG, "Started polling for channel $channelId")
    }

    /**
     * Stop polling for a channel.
     */
    fun stopPolling(channelId: String) {
        channelStates[channelId]?.pollingJob?.cancel()
        channelStates.remove(channelId)
        updateIntervalFlow()
        Log.d(TAG, "Stopped polling for channel $channelId")
    }

    /**
     * Mark audio activity for a channel.
     * Called on SPEAKER_CHANGED with active speaker.
     */
    fun markActivity(channelId: String) {
        channelStates[channelId]?.let { state ->
            state.lastActivityMs = System.currentTimeMillis()
            Log.d(TAG, "Marked activity for channel $channelId")
        }
    }

    /**
     * Update consumer count for a channel.
     * If count goes from 0 to >0, triggers activity (resume polling at 5s).
     */
    fun updateConsumerCount(channelId: String, count: Int) {
        channelStates[channelId]?.let { state ->
            val wasEmpty = state.consumerCount == 0
            state.consumerCount = count

            // Resume from empty → trigger activity
            if (wasEmpty && count > 0) {
                state.lastActivityMs = System.currentTimeMillis()
                Log.d(TAG, "Channel $channelId resumed from empty, consumer count: $count")
            }
        }
    }

    /**
     * Stop all polling (called on disconnectAll).
     */
    fun stopAll() {
        channelStates.values.forEach { it.pollingJob?.cancel() }
        channelStates.clear()
        updateIntervalFlow()
        Log.d(TAG, "Stopped all channel polling")
    }

    /**
     * Update channelIntervals StateFlow for DevStatsScreen.
     */
    private fun updateIntervalFlow() {
        _channelIntervals.value = channelStates.mapValues { (_, state) -> state.currentIntervalMs }
    }

    companion object {
        private const val TAG = "ChannelStatsPoller"
        private const val ACTIVE_INTERVAL_MS = 5000L // 5 seconds
        private const val IDLE_INTERVAL_MS = 15000L // 15 seconds
        private const val ACTIVITY_WINDOW_MS = 60_000L // 60 seconds
    }
}
