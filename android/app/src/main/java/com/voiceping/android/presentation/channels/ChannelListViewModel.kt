package com.voiceping.android.presentation.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.repository.EventRepository
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.domain.model.Channel
import com.voiceping.android.domain.model.ConnectionState
import com.voiceping.android.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val signalingClient: SignalingClient,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _joinedChannel = MutableStateFlow<Channel?>(null)
    val joinedChannel: StateFlow<Channel?> = _joinedChannel.asStateFlow()

    private val _currentSpeaker = MutableStateFlow<User?>(null)
    val currentSpeaker: StateFlow<User?> = _currentSpeaker.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = signalingClient.connectionState

    private val eventId: String? = savedStateHandle.get<String>("eventId")
        ?: preferencesManager.getLastEventId()

    init {
        eventId?.let { loadChannels(it) }
    }

    fun loadChannels(eventId: String) {
        viewModelScope.launch {
            val result = eventRepository.getChannelsForEvent(eventId)
            if (result.isSuccess) {
                _channels.value = result.getOrNull() ?: emptyList()
            }
        }
    }

    fun toggleChannel(channel: Channel) {
        viewModelScope.launch {
            val current = _joinedChannel.value

            if (current?.id == channel.id) {
                // Leave current channel
                _joinedChannel.value = null
                _currentSpeaker.value = null
                // TODO: Call leaveChannel() when implemented in Plan 05
            } else {
                // Phase 5: single channel only - leave previous, join new
                if (current != null) {
                    // TODO: Call leaveChannel() for current when implemented in Plan 05
                }

                // Join new channel
                _joinedChannel.value = channel
                // TODO: Call joinChannel() when implemented in Plan 05
            }
        }
    }
}
