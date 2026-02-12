package com.voiceping.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceping.android.data.audio.AudioRouter
import com.voiceping.android.data.storage.SettingsRepository
import com.voiceping.android.domain.model.AudioMixMode
import com.voiceping.android.domain.model.AudioRoute
import com.voiceping.android.domain.model.PttMode
import com.voiceping.android.domain.model.PttTargetMode
import com.voiceping.android.domain.model.VolumeKeyPttConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val audioRouter: AudioRouter
) : ViewModel() {

    // PTT Settings
    val pttMode: StateFlow<PttMode> = settingsRepository.getPttMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PttMode.PRESS_AND_HOLD)

    val toggleMaxDuration: StateFlow<Int> = settingsRepository.getToggleMaxDuration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    // Audio Settings
    val audioRoute: StateFlow<AudioRoute> = settingsRepository.getAudioRoute()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioRoute.SPEAKER)

    val pttStartToneEnabled: StateFlow<Boolean> = settingsRepository.getPttStartToneEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val rogerBeepEnabled: StateFlow<Boolean> = settingsRepository.getRogerBeepEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val rxSquelchEnabled: StateFlow<Boolean> = settingsRepository.getRxSquelchEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Scan Mode Settings
    val scanModeEnabled: StateFlow<Boolean> = settingsRepository.getScanModeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val scanReturnDelay: StateFlow<Int> = settingsRepository.getScanReturnDelay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val pttTargetMode: StateFlow<PttTargetMode> = settingsRepository.getPttTargetMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PttTargetMode.ALWAYS_PRIMARY)

    val audioMixMode: StateFlow<AudioMixMode> = settingsRepository.getAudioMixMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioMixMode.EQUAL_VOLUME)

    // Hardware Button Settings
    val volumeKeyPttConfig: StateFlow<VolumeKeyPttConfig> = settingsRepository.getVolumeKeyPttConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VolumeKeyPttConfig.DISABLED)

    val bluetoothPttEnabled: StateFlow<Boolean> = settingsRepository.getBluetoothPttEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val bluetoothPttButtonKeycode: StateFlow<Int> = settingsRepository.getBluetoothPttButtonKeycode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 85)

    val bootAutoStartEnabled: StateFlow<Boolean> = settingsRepository.getBootAutoStartEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // PTT Settings setters
    fun setPttMode(mode: PttMode) = viewModelScope.launch {
        settingsRepository.setPttMode(mode)
    }

    fun setToggleMaxDuration(seconds: Int) = viewModelScope.launch {
        settingsRepository.setToggleMaxDuration(seconds)
    }

    // Audio Settings setters
    fun setAudioRoute(route: AudioRoute) = viewModelScope.launch {
        settingsRepository.setAudioRoute(route)
        // Apply audio route immediately via AudioRouter
        when (route) {
            AudioRoute.SPEAKER -> audioRouter.setSpeakerMode()
            AudioRoute.EARPIECE -> audioRouter.setEarpieceMode()
            AudioRoute.BLUETOOTH -> {
                // Bluetooth routing handled by AudioRouter based on device state
                android.util.Log.d("SettingsViewModel", "Bluetooth route selected, will auto-route when BT connects")
            }
        }
    }

    fun setPttStartToneEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setPttStartToneEnabled(enabled)
    }

    fun setRogerBeepEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setRogerBeepEnabled(enabled)
    }

    fun setRxSquelchEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setRxSquelchEnabled(enabled)
    }

    // Scan Mode Settings setters
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

    // Hardware Button Settings setters
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
}
