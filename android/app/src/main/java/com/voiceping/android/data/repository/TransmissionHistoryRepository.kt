package com.voiceping.android.data.repository

import com.voiceping.android.domain.model.TransmissionHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory repository for transmission history per channel.
 *
 * Stores last 20 transmissions per channel with reactive Flow observation.
 * Session-only: clears on app restart per user decision.
 *
 * Thread-safe using ConcurrentHashMap for multi-channel concurrent access.
 */
@Singleton
class TransmissionHistoryRepository @Inject constructor() {

    private val history = ConcurrentHashMap<String, MutableList<TransmissionHistoryEntry>>()
    private val maxEntriesPerChannel = 20
    private val _historyUpdated = MutableSharedFlow<String>()

    /**
     * Add transmission entry to channel history.
     *
     * Circular buffer: oldest entry removed when limit reached.
     * Emits channelId on _historyUpdated for UI reactivity.
     */
    suspend fun addEntry(entry: TransmissionHistoryEntry) {
        val list = history.getOrPut(entry.channelId) { mutableListOf() }

        synchronized(list) {
            if (list.size >= maxEntriesPerChannel) {
                list.removeFirst() // Remove oldest
            }
            list.add(entry)
        }

        _historyUpdated.emit(entry.channelId)
    }

    /**
     * Get transmission history for a channel.
     *
     * @param channelId Channel ID
     * @return List of entries in reverse chronological order (newest first), or empty list
     */
    fun getHistory(channelId: String): List<TransmissionHistoryEntry> {
        val list = history[channelId] ?: return emptyList()
        return synchronized(list) {
            list.toList().reversed() // Newest first
        }
    }

    /**
     * Observe transmission history for a channel as reactive Flow.
     *
     * Emits whenever history changes for this channel.
     * Starts with current history on collection.
     *
     * @param channelId Channel ID
     * @return Flow emitting history list on changes
     */
    fun observeHistory(channelId: String): Flow<List<TransmissionHistoryEntry>> {
        return _historyUpdated
            .filter { it == channelId }
            .map { getHistory(channelId) }
            .onStart { emit(getHistory(channelId)) }
    }

    /**
     * Clear all history (called on disconnect/logout).
     */
    fun clearAll() {
        history.clear()
    }
}
