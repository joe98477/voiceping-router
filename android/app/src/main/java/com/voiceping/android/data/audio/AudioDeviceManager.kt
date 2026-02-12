package com.voiceping.android.data.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import com.voiceping.android.domain.model.AudioOutputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio device manager for Bluetooth and wired headset detection with automatic routing.
 *
 * Features:
 * - Detects Bluetooth A2DP/SCO/BLE headsets, wired headsets/headphones
 * - Automatically routes audio to last connected external device ("last connected wins")
 * - Falls back to previous device when external device disconnects
 * - Exposes current output device for UI indicator
 * - Invokes onBluetoothDisconnected callback for PTT auto-release (Phase 9 requirement)
 *
 * Usage:
 * - Call start() when ChannelMonitoringService starts (user joins first channel)
 * - Call stop() when service stops (user leaves all channels or disconnects)
 *
 * Integration:
 * - AudioRouter handles actual audio routing (Bluetooth/wired/speaker/earpiece modes)
 * - AudioDeviceManager detects devices and delegates routing to AudioRouter
 */
@Singleton
class AudioDeviceManager @Inject constructor(
    private val audioRouter: AudioRouter,
    @ApplicationContext private val context: Context
) {
    private val audioManager = audioRouter.getAudioManager()

    // Current output device for UI indicator
    private val _currentOutputDevice = MutableStateFlow(AudioOutputDevice.SPEAKER)
    val currentOutputDevice: StateFlow<AudioOutputDevice> = _currentOutputDevice.asStateFlow()

    // Previous device before external device connected (for fallback on disconnect)
    private var previousDevice: AudioOutputDevice = AudioOutputDevice.SPEAKER

    // Last connected external device (Bluetooth or wired)
    private var lastConnectedExternalDevice: AudioDeviceInfo? = null

    // Callback for Bluetooth disconnect while PTT active (triggers auto-release)
    var onBluetoothDisconnected: (() -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            // Filter for output (sink) devices only
            val outputDevices = addedDevices.filter { it.isSink }

            for (device in outputDevices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                        // Bluetooth headset connected
                        Log.d(TAG, "Bluetooth device connected: ${device.productName} (type=${device.type})")

                        // Save previous device for fallback, update last connected
                        if (lastConnectedExternalDevice == null) {
                            previousDevice = _currentOutputDevice.value
                        }
                        lastConnectedExternalDevice = device

                        // Route audio to Bluetooth
                        audioRouter.setBluetoothMode(device)
                        _currentOutputDevice.value = AudioOutputDevice.BLUETOOTH
                    }

                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        // Wired headset/headphones connected
                        Log.d(TAG, "Wired device connected: ${device.productName} (type=${device.type})")

                        // Last connected device wins: wired headset after Bluetooth replaces Bluetooth
                        if (lastConnectedExternalDevice == null) {
                            previousDevice = _currentOutputDevice.value
                        }
                        lastConnectedExternalDevice = device

                        // Route audio to wired headset
                        audioRouter.setWiredHeadsetMode()
                        _currentOutputDevice.value = AudioOutputDevice.WIRED_HEADSET
                    }

                    else -> {
                        // Other device types (USB, HDMI, etc.) - ignore for now
                        Log.d(TAG, "Audio device added (ignored): ${device.productName} (type=${device.type})")
                    }
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            for (device in removedDevices) {
                // Check if removed device is our last connected external device
                if (device == lastConnectedExternalDevice) {
                    Log.d(TAG, "External device disconnected: ${device.productName} (type=${device.type})")

                    val wasBluetoothDevice = when (device.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET -> true
                        else -> false
                    }

                    // Fall back to previous device
                    when (previousDevice) {
                        AudioOutputDevice.SPEAKER -> {
                            audioRouter.setSpeakerMode()
                            _currentOutputDevice.value = AudioOutputDevice.SPEAKER
                            Log.d(TAG, "Fell back to speaker mode")
                        }
                        AudioOutputDevice.EARPIECE -> {
                            audioRouter.setEarpieceMode()
                            _currentOutputDevice.value = AudioOutputDevice.EARPIECE
                            Log.d(TAG, "Fell back to earpiece mode")
                        }
                        else -> {
                            // Fallback to speaker if previous was also external (edge case)
                            audioRouter.setSpeakerMode()
                            _currentOutputDevice.value = AudioOutputDevice.SPEAKER
                            Log.d(TAG, "Fell back to speaker mode (default)")
                        }
                    }

                    // Clear communication device (stops Bluetooth SCO if needed)
                    audioRouter.clearCommunicationDevice()

                    // Clear last connected device
                    lastConnectedExternalDevice = null

                    // If was Bluetooth device: invoke callback (auto-release PTT if active)
                    if (wasBluetoothDevice) {
                        Log.d(TAG, "Bluetooth disconnected, invoking callback")
                        onBluetoothDisconnected?.invoke()
                    }
                }
            }
        }
    }

    /**
     * Start audio device monitoring.
     *
     * Registers AudioDeviceCallback to detect Bluetooth/wired device connections/disconnections.
     * Also scans currently connected devices to detect already-connected headsets.
     *
     * Call when ChannelMonitoringService starts (user joins first channel).
     */
    fun start() {
        Log.d(TAG, "Starting audio device monitoring")

        // Register callback for future device changes
        audioManager.registerAudioDeviceCallback(deviceCallback, null)

        // Scan currently connected devices (in case Bluetooth/wired already connected before start)
        val currentDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in currentDevices) {
            if (device.isSink) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                        Log.d(TAG, "Already connected Bluetooth device detected: ${device.productName}")
                        previousDevice = _currentOutputDevice.value
                        lastConnectedExternalDevice = device
                        audioRouter.setBluetoothMode(device)
                        _currentOutputDevice.value = AudioOutputDevice.BLUETOOTH
                    }

                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        Log.d(TAG, "Already connected wired device detected: ${device.productName}")
                        previousDevice = _currentOutputDevice.value
                        lastConnectedExternalDevice = device
                        audioRouter.setWiredHeadsetMode()
                        _currentOutputDevice.value = AudioOutputDevice.WIRED_HEADSET
                    }
                }
            }
        }
    }

    /**
     * Stop audio device monitoring.
     *
     * Unregisters AudioDeviceCallback and clears communication device.
     *
     * Call when ChannelMonitoringService stops (user leaves all channels or disconnects).
     */
    fun stop() {
        Log.d(TAG, "Stopping audio device monitoring")
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        audioRouter.clearCommunicationDevice()
        lastConnectedExternalDevice = null
    }

    /**
     * Get current output device.
     *
     * @return Current audio output device (for UI indicator)
     */
    fun getCurrentDevice(): AudioOutputDevice = _currentOutputDevice.value

    companion object {
        private const val TAG = "AudioDeviceManager"
    }
}
