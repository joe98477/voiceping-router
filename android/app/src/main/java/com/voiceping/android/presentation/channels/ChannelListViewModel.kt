package com.voiceping.android.presentation.channels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceping.android.data.audio.AudioRouter
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.ptt.PttManager
import com.voiceping.android.data.ptt.PttState
import com.voiceping.android.data.repository.ChannelRepository
import com.voiceping.android.data.repository.EventRepository
import com.voiceping.android.data.storage.PreferencesManager
import com.voiceping.android.data.storage.SettingsRepository
import com.voiceping.android.domain.model.AudioRoute
import com.voiceping.android.domain.model.Channel
import com.voiceping.android.domain.model.ConnectionState
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.User
import com.voiceping.android.domain.usecase.JoinChannelUseCase
import com.voiceping.android.domain.usecase.LeaveChannelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val signalingClient: SignalingClient,
    private val channelRepository: ChannelRepository,
    private val joinChannelUseCase: JoinChannelUseCase,
    private val leaveChannelUseCase: LeaveChannelUseCase,
    private val preferencesManager: PreferencesManager,
    private val pttManager: PttManager,
    private val settingsRepository: SettingsRepository,
    private val audioRouter: AudioRouter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _joinedChannel = MutableStateFlow<Channel?>(null)
    val joinedChannel: StateFlow<Channel?> = _joinedChannel.asStateFlow()

    val currentSpeaker: StateFlow<User?> = channelRepository.currentSpeaker
    val lastSpeaker: StateFlow<User?> = channelRepository.lastSpeaker
    val connectionState: StateFlow<ConnectionState> = signalingClient.connectionState

    // PTT state and settings
    val pttState: StateFlow<PttState> = channelRepository.pttState
    val pttMode: StateFlow<PttMode> = settingsRepository.getPttMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PttMode.PRESS_AND_HOLD)
    val audioRoute: StateFlow<AudioRoute> = settingsRepository.getAudioRoute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioRoute.SPEAKER)
    val toggleMaxDuration: StateFlow<Int> = settingsRepository.getToggleMaxDuration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    val pttStartToneEnabled: StateFlow<Boolean> = settingsRepository.getPttStartToneEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val rogerBeepEnabled: StateFlow<Boolean> = settingsRepository.getRogerBeepEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val rxSquelchEnabled: StateFlow<Boolean> = settingsRepository.getRxSquelchEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Mic permission tracking
    private val _needsMicPermission = MutableStateFlow(false)
    val needsMicPermission: StateFlow<Boolean> = _needsMicPermission.asStateFlow()

    private val eventId: String? = savedStateHandle.get<String>("eventId")
        ?: preferencesManager.getLastEventId()

    init {
        eventId?.let { loadChannels(it) }

        // Observe ChannelRepository.joinedChannelId to sync with _joinedChannel
        viewModelScope.launch {
            channelRepository.joinedChannelId.collect { joinedId ->
                if (joinedId == null) {
                    _joinedChannel.value = null
                } else {
                    // Find the channel with this ID in our channels list
                    _joinedChannel.value = _channels.value.find { it.id == joinedId }
                }
            }
        }

        // Observe settings and update PttManager and AudioRouter
        viewModelScope.launch {
            settingsRepository.getToggleMaxDuration().collect { duration ->
                pttManager.maxToggleDuration = duration
            }
        }
        viewModelScope.launch {
            settingsRepository.getPttMode().collect { mode ->
                pttManager.currentPttMode = mode
            }
        }
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
                Log.d("ChannelListViewModel", "Leaving channel: ${channel.name}")
                val result = leaveChannelUseCase(channel.id)
                if (result.isFailure) {
                    Log.e("ChannelListViewModel", "Failed to leave channel", result.exceptionOrNull())
                }
                _joinedChannel.value = null
            } else {
                // Phase 5: single channel only - leave previous, join new
                if (current != null) {
                    Log.d("ChannelListViewModel", "Leaving previous channel: ${current.name}")
                    leaveChannelUseCase(current.id)
                }

                // Join new channel
                Log.d("ChannelListViewModel", "Joining channel: ${channel.name}")
                val result = joinChannelUseCase(channel.id)
                if (result.isSuccess) {
                    _joinedChannel.value = channel
                } else {
                    Log.e("ChannelListViewModel", "Failed to join channel", result.exceptionOrNull())
                    // TODO: Show error to user (Plan 06 or later)
                }
            }
        }
    }

    // PTT actions
    fun onPttPressed() {
        // Check mic permission first
        if (!channelRepository.hasMicPermission()) {
            Log.d("ChannelListViewModel", "Mic permission not granted, requesting")
            _needsMicPermission.value = true
            return
        }

        val channelId = _joinedChannel.value?.id
        if (channelId == null) {
            Log.w("ChannelListViewModel", "No channel joined, cannot request PTT")
            return
        }

        pttManager.requestPtt(channelId)
    }

    fun onPttReleased() {
        pttManager.releasePtt()
    }

    fun onMicPermissionResult(granted: Boolean) {
        _needsMicPermission.value = false
        if (granted) {
            // Retry PTT press after permission granted
            Log.d("ChannelListViewModel", "Mic permission granted, retrying PTT")
            onPttPressed()
        } else {
            Log.w("ChannelListViewModel", "Mic permission denied by user")
        }
    }

    // Settings actions
    fun setPttMode(mode: PttMode) {
        viewModelScope.launch {
            settingsRepository.setPttMode(mode)
        }
    }

    fun setAudioRoute(route: AudioRoute) {
        viewModelScope.launch {
            settingsRepository.setAudioRoute(route)
            // Apply audio route immediately via AudioRouter
            when (route) {
                AudioRoute.SPEAKER -> audioRouter.setSpeakerMode()
                AudioRoute.EARPIECE -> audioRouter.setEarpieceMode()
                AudioRoute.BLUETOOTH -> {
                    // Bluetooth routing handled by AudioRouter based on device state
                    // Note: Plan doesn't specify setBluetoothMode() call here
                    Log.d("ChannelListViewModel", "Bluetooth route selected, will auto-route when BT connects")
                }
            }
        }
    }

    fun setPttStartToneEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPttStartToneEnabled(enabled)
        }
    }

    fun setRogerBeepEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRogerBeepEnabled(enabled)
        }
    }

    fun setRxSquelchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRxSquelchEnabled(enabled)
        }
    }

    fun setToggleMaxDuration(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setToggleMaxDuration(seconds)
        }
    }

    // Get transmission duration for UI display
    fun getTransmissionDuration(): Long {
        return pttManager.getTransmissionDurationSeconds()
    }

    override fun onCleared() {
        super.onCleared()
        // Disconnect from all channels on ViewModel cleanup
        channelRepository.disconnectAll()
    }
}
