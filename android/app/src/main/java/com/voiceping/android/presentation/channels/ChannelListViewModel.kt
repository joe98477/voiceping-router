package com.voiceping.android.presentation.channels

import android.content.Context
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
import com.voiceping.android.domain.model.AudioMixMode
import com.voiceping.android.domain.model.AudioOutputDevice
import com.voiceping.android.domain.model.AudioRoute
import com.voiceping.android.domain.model.Channel
import com.voiceping.android.domain.model.ChannelMonitoringState
import com.voiceping.android.domain.model.ConnectionState
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.PttTargetMode
import com.voiceping.android.domain.model.VolumeKeyPttConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val signalingClient: SignalingClient,
    private val channelRepository: ChannelRepository,
    private val preferencesManager: PreferencesManager,
    private val pttManager: PttManager,
    private val settingsRepository: SettingsRepository,
    private val audioRouter: AudioRouter,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ChannelListViewModel"
    }

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    // Multi-channel monitoring state
    val monitoredChannels: StateFlow<Map<String, ChannelMonitoringState>> = channelRepository.monitoredChannels
    val primaryChannelId: StateFlow<String?> = channelRepository.primaryChannelId

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

    // Scan mode settings
    val scanModeEnabled: StateFlow<Boolean> = settingsRepository.getScanModeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val scanReturnDelay: StateFlow<Int> = settingsRepository.getScanReturnDelay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)
    val pttTargetMode: StateFlow<PttTargetMode> = settingsRepository.getPttTargetMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PttTargetMode.ALWAYS_PRIMARY)
    val audioMixMode: StateFlow<AudioMixMode> = settingsRepository.getAudioMixMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioMixMode.EQUAL_VOLUME)

    // Hardware button settings
    val volumeKeyPttConfig: StateFlow<VolumeKeyPttConfig> = settingsRepository.getVolumeKeyPttConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VolumeKeyPttConfig.DISABLED)
    val bluetoothPttEnabled: StateFlow<Boolean> = settingsRepository.getBluetoothPttEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val bluetoothPttButtonKeycode: StateFlow<Int> = settingsRepository.getBluetoothPttButtonKeycode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 85)
    val bootAutoStartEnabled: StateFlow<Boolean> = settingsRepository.getBootAutoStartEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Current audio output device
    val currentOutputDevice: StateFlow<AudioOutputDevice> = channelRepository.currentOutputDevice

    // Button detection state
    private val _showButtonDetection = MutableStateFlow(false)
    val showButtonDetection: StateFlow<Boolean> = _showButtonDetection.asStateFlow()
    private val _detectedKeyCode = MutableStateFlow<Int?>(null)
    val detectedKeyCode: StateFlow<Int?> = _detectedKeyCode.asStateFlow()

    // Scan mode lock state
    private val _scanModeLocked = MutableStateFlow(false)
    val scanModeLocked: StateFlow<Boolean> = _scanModeLocked.asStateFlow()

    private val _manuallySelectedChannelId = MutableStateFlow<String?>(null)

    // Derive displayed channel ID (core scan logic)
    val displayedChannelId: StateFlow<String?> = combine(
        monitoredChannels,
        primaryChannelId,
        _scanModeLocked,
        _manuallySelectedChannelId,
        scanModeEnabled
    ) { channels, primary, locked, manual, scanEnabled ->
        when {
            channels.isEmpty() -> null
            locked && manual != null -> manual  // Manual lock takes priority
            !scanEnabled -> primary  // Scan disabled, show primary
            else -> {
                // Scan mode: find most recent active non-primary speaker
                val activeNonPrimary = channels.values
                    .filter { it.currentSpeaker != null && !it.isPrimary && !it.isMuted }
                    .sortedByDescending { it.speakerStartTime }

                activeNonPrimary.firstOrNull()?.channelId ?: primary
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Toast message for user feedback
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Mic permission tracking
    private val _needsMicPermission = MutableStateFlow(false)
    val needsMicPermission: StateFlow<Boolean> = _needsMicPermission.asStateFlow()

    // Battery optimization tracking
    private val _showBatteryOptimizationPrompt = MutableStateFlow(false)
    val showBatteryOptimizationPrompt: StateFlow<Boolean> = _showBatteryOptimizationPrompt.asStateFlow()
    private var hasCheckedBatteryOptimization = false

    private val eventId: String? = savedStateHandle.get<String>("eventId")
        ?: preferencesManager.getLastEventId()

    init {
        eventId?.let { loadChannels(it) }

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

        // Update ChannelRepository with displayed channel ID for hardware PTT targeting
        viewModelScope.launch {
            displayedChannelId.collect { channelId ->
                channelRepository.currentDisplayedChannelId = channelId
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
            val isCurrentlyJoined = monitoredChannels.value.containsKey(channel.id)

            if (isCurrentlyJoined) {
                // Leave this channel
                val result = channelRepository.leaveChannel(channel.id)
                if (result.isFailure) {
                    Log.e(TAG, "Failed to leave channel", result.exceptionOrNull())
                }
            } else {
                // Join this channel (may fail if at 5-channel limit)
                val result = channelRepository.joinChannel(channel.id, channel.name, channel.teamName)
                if (result.isSuccess) {
                    // Check battery optimization on first channel join
                    if (!hasCheckedBatteryOptimization) {
                        checkBatteryOptimization()
                        hasCheckedBatteryOptimization = true
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to join channel"
                    _toastMessage.value = error  // Show toast for max 5 limit
                    Log.e(TAG, "Failed to join channel: $error")
                }
            }
        }
    }

    fun toggleBottomBarLock() {
        val current = displayedChannelId.value
        if (_scanModeLocked.value) {
            // Unlock: return to scan mode
            _scanModeLocked.value = false
            _manuallySelectedChannelId.value = null
        } else {
            // Lock: freeze on current channel
            _scanModeLocked.value = true
            _manuallySelectedChannelId.value = current
        }
    }

    fun returnToPrimaryChannel() {
        if (!_scanModeLocked.value) {
            // displayedChannelId will naturally resolve to primary when no active speakers
            // This is a no-op signal - the combine flow handles it
        }
    }

    fun setPrimaryChannel(channelId: String) = viewModelScope.launch {
        channelRepository.setPrimaryChannel(channelId)
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    // PTT actions
    fun onPttPressed() {
        if (!channelRepository.hasMicPermission()) {
            _needsMicPermission.value = true
            return
        }

        // Determine PTT target based on setting
        val targetChannelId = when (pttTargetMode.value) {
            PttTargetMode.ALWAYS_PRIMARY -> primaryChannelId.value
            PttTargetMode.DISPLAYED_CHANNEL -> displayedChannelId.value
        }

        if (targetChannelId == null) {
            Log.w(TAG, "No channel to target for PTT")
            return
        }

        pttManager.requestPtt(targetChannelId)
    }

    fun onPttReleased() {
        pttManager.releasePtt()
    }

    fun onMicPermissionResult(granted: Boolean) {
        _needsMicPermission.value = false
        if (granted) {
            // Retry PTT press after permission granted
            Log.d(TAG, "Mic permission granted, retrying PTT")
            onPttPressed()
        } else {
            Log.w(TAG, "Mic permission denied by user")
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
                    Log.d(TAG, "Bluetooth route selected, will auto-route when BT connects")
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

    // Scan mode settings setters
    fun setScanModeEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setScanModeEnabled(enabled)
    }

    fun setScanReturnDelay(seconds: Int) = viewModelScope.launch {
        settingsRepository.setScanReturnDelay(seconds)
    }

    fun setPttTargetMode(mode: PttTargetMode) = viewModelScope.launch {
        settingsRepository.setPttTargetMode(mode)
    }

    fun setAudioMixMode(mode: AudioMixMode) = viewModelScope.launch {
        settingsRepository.setAudioMixMode(mode)
    }

    // Hardware button settings setters
    fun setVolumeKeyPttConfig(config: VolumeKeyPttConfig) = viewModelScope.launch {
        settingsRepository.setVolumeKeyPttConfig(config)
    }

    fun setBluetoothPttEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBluetoothPttEnabled(enabled)
    }

    fun setBluetoothPttButtonKeycode(keyCode: Int) = viewModelScope.launch {
        settingsRepository.setBluetoothPttButtonKeycode(keyCode)
    }

    fun setBootAutoStartEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBootAutoStartEnabled(enabled)
    }

    // Button detection methods
    fun startButtonDetection() {
        _showButtonDetection.value = true
        _detectedKeyCode.value = null
        channelRepository.startButtonDetection { keyCode ->
            _detectedKeyCode.value = keyCode
        }
    }

    fun confirmDetectedButton() {
        _detectedKeyCode.value?.let { keyCode ->
            setBluetoothPttButtonKeycode(keyCode)
        }
        stopButtonDetection()
    }

    fun stopButtonDetection() {
        _showButtonDetection.value = false
        _detectedKeyCode.value = null
        channelRepository.stopButtonDetection()
    }

    // Multi-channel mute actions
    fun muteAllExceptPrimary() = viewModelScope.launch {
        channelRepository.muteAllExceptPrimary()
    }

    fun unmuteAllChannels() = viewModelScope.launch {
        channelRepository.unmuteAllChannels()
    }

    // Per-channel control
    fun muteChannel(channelId: String) = viewModelScope.launch {
        channelRepository.muteChannel(channelId)
    }

    fun unmuteChannel(channelId: String) = viewModelScope.launch {
        channelRepository.unmuteChannel(channelId)
    }

    fun setChannelVolume(channelId: String, volume: Float) = viewModelScope.launch {
        channelRepository.setChannelVolume(channelId, volume)
    }

    // Get transmission duration for UI display
    fun getTransmissionDuration(): Long {
        return pttManager.getTransmissionDurationSeconds()
    }

    private fun checkBatteryOptimization() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val packageName = context.packageName
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            _showBatteryOptimizationPrompt.value = true
        }
    }

    fun dismissBatteryOptimizationPrompt() {
        _showBatteryOptimizationPrompt.value = false
    }

    override fun onCleared() {
        super.onCleared()
        // Disconnect from all channels on ViewModel cleanup
        channelRepository.disconnectAll()
    }
}
