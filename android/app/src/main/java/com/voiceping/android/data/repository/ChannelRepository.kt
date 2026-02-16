package com.voiceping.android.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.voiceping.android.data.audio.AudioDeviceManager
import com.voiceping.android.data.audio.AudioRouter
import com.voiceping.android.data.audio.HapticFeedback
import com.voiceping.android.data.audio.TonePlayer
import com.voiceping.android.data.hardware.MediaButtonHandler
import com.voiceping.android.data.location.LocationManager
import com.voiceping.android.data.network.ChannelStatsPoller
import com.voiceping.android.data.network.MediasoupClient
import com.voiceping.android.data.network.NetworkMonitor
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.data.power.BatterySaverMonitor
import com.voiceping.android.data.power.WakeLockManager
import com.voiceping.android.data.ptt.PttManager
import com.voiceping.android.data.ptt.PttState
import com.voiceping.android.data.storage.SettingsRepository
import com.voiceping.android.domain.model.AudioMixMode
import com.voiceping.android.domain.model.AudioOutputDevice
import com.voiceping.android.domain.model.ChannelMonitoringState
import com.voiceping.android.domain.model.PttTargetMode
import com.voiceping.android.domain.model.User
import com.voiceping.android.service.ChannelMonitoringService
import com.google.gson.JsonElement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Safe accessor: returns null for both missing keys and JSON null values. */
private fun JsonElement?.asStringOrNull(): String? =
    if (this == null || this.isJsonNull) null else this.asString

private fun JsonElement?.asIntOrNull(): Int? =
    if (this == null || this.isJsonNull) null else this.asInt

private fun JsonElement?.asLongOrNull(): Long? =
    if (this == null || this.isJsonNull) null else this.asLong

@Singleton
class ChannelRepository @Inject constructor(
    private val signalingClient: SignalingClient,
    private val mediasoupClient: MediasoupClient,
    private val audioRouter: AudioRouter,
    private val pttManager: PttManager,
    private val tonePlayer: TonePlayer,
    private val hapticFeedback: HapticFeedback,
    private val settingsRepository: SettingsRepository,
    private val audioDeviceManager: AudioDeviceManager,
    private val mediaButtonHandler: MediaButtonHandler,
    private val networkMonitor: NetworkMonitor,
    private val locationManager: LocationManager,
    private val wakeLockManager: WakeLockManager,
    private val batterySaverMonitor: BatterySaverMonitor,
    private val channelStatsPoller: ChannelStatsPoller,
    @ApplicationContext private val context: Context
) {
    private val _monitoredChannels = MutableStateFlow<Map<String, ChannelMonitoringState>>(emptyMap())
    val monitoredChannels: StateFlow<Map<String, ChannelMonitoringState>> = _monitoredChannels.asStateFlow()

    private val _primaryChannelId = MutableStateFlow<String?>(null)
    val primaryChannelId: StateFlow<String?> = _primaryChannelId.asStateFlow()

    // Per-channel consumer tracking: channelId -> (producerId -> consumerId)
    // ConcurrentHashMap: accessed from speaker observer coroutines, mute/unmute, phone call callback, and cleanup
    private val channelConsumers = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    // Per-channel speaker observer jobs
    // ConcurrentHashMap: accessed from joinChannel, leaveChannel, and disconnectAll on different coroutines
    private val speakerObserverJobs = ConcurrentHashMap<String, Job>()

    // Per-channel state update observer jobs
    private val channelStateObserverJobs = ConcurrentHashMap<String, Job>()

    // Per-channel last speaker fade jobs
    private val lastSpeakerFadeJobs = ConcurrentHashMap<String, Job>()

    private var isServiceRunning = false
    private var currentAudioMixMode = AudioMixMode.EQUAL_VOLUME

    // Expose PTT state via delegation to PttManager
    val pttState: StateFlow<PttState> = pttManager.pttState

    // Expose current audio output device via delegation to AudioDeviceManager
    val currentOutputDevice: StateFlow<AudioOutputDevice> = audioDeviceManager.currentOutputDevice

    // Track current displayed channel ID for hardware PTT targeting
    // Updated by ChannelListViewModel whenever displayedChannelId changes
    var currentDisplayedChannelId: String? = null

    // Track disconnection timing for connection tone decisions and channel rejoin
    private var disconnectedSinceMs: Long? = null
    private var previousConnectionState: com.voiceping.android.domain.model.ConnectionState? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Auto-rejoin state tracking
    private var autoRejoinAttempts = 0
    private val maxAutoRejoinAttempts = 5
    private var autoRejoinJob: Job? = null

    private val _showDisconnectedBanner = MutableStateFlow(false)
    val showDisconnectedBanner: StateFlow<Boolean> = _showDisconnectedBanner.asStateFlow()

    private val _isAutoRejoining = MutableStateFlow(false)
    val isAutoRejoining: StateFlow<Boolean> = _isAutoRejoining.asStateFlow()

    init {
        // Wire PttManager callbacks for tone/haptic feedback
        pttManager.onPttGranted = {
            tonePlayer.playPttStartTone()
            hapticFeedback.vibratePttPress()
        }
        pttManager.onPttDenied = {
            tonePlayer.playErrorTone()
            // Use busy vibration for channel busy (double-tap pattern)
            // This covers the most common PTT denial case (channel occupied by another speaker)
            hapticFeedback.vibrateBusy()
        }
        pttManager.onPttReleased = {
            tonePlayer.playRogerBeep()
            hapticFeedback.vibrateRelease()
        }

        // Wire call interruption beep (distinct from roger beep)
        pttManager.onPttInterrupted = {
            tonePlayer.playCallInterruptionBeep()
        }

        // Wire error feedback (producer creation failure after retries)
        pttManager.onPttError = {
            hapticFeedback.vibrateErrorRelease()
        }

        // Collect ACK results for confirmation tone
        scope.launch {
            pttManager.ackResult.collect { success ->
                tonePlayer.playConfirmationTone(success)
            }
        }

        // Wire phone call handling via AudioRouter (audio focus listener)
        // User decision: immediate pause, force-release PTT, auto-resume after call ends
        audioRouter.onPhoneCallStarted = {
            Log.d(TAG, "Phone call started: pausing all channels, force-releasing PTT")

            // Force-release PTT if transmitting (plays call interruption double beep)
            if (pttManager.pttState.value is PttState.Transmitting) {
                pttManager.forceReleasePtt()
            }

            // Close consumers for ALL monitored channels (pause everything)
            channelConsumers.values.forEach { consumers ->
                consumers.values.forEach { consumerId ->
                    mediasoupClient.closeConsumer(consumerId)
                }
            }
            Log.d(TAG, "Phone call: closed all consumers")
        }

        audioRouter.onPhoneCallEnded = {
            // Audio will resume on next speaker change event
            // If there's an active speaker, we need to re-consume
            // For now, log it — the speaker observation will handle new speaker events
            Log.d(TAG, "Phone call ended: ready to receive audio")
        }

        // Observe audio mix mode changes
        CoroutineScope(Dispatchers.IO).launch {
            settingsRepository.getAudioMixMode().collect { mode ->
                currentAudioMixMode = mode
                applyAudioMixMode(mode)
            }
        }

        // Observe mute state from monitoring service notification
        // Note: This applies to primary channel only
        CoroutineScope(Dispatchers.IO).launch {
            ChannelMonitoringService.isMutedFlow.collect { muted ->
                _primaryChannelId.value?.let { primaryId ->
                    if (muted) {
                        muteChannel(primaryId)
                    } else {
                        unmuteChannel(primaryId)
                    }
                }
            }
        }

        // Wire AudioDeviceManager Bluetooth disconnect callback
        audioDeviceManager.onBluetoothDisconnected = {
            // Per user decision: auto-release PTT on BT disconnect, play interrupted beep
            Log.d(TAG, "Bluetooth disconnected, force-releasing PTT")
            pttManager.forceReleasePtt()
        }

        // Wire MediaButtonHandler PTT callbacks
        mediaButtonHandler.onPttPress = {
            val targetChannelId = getHardwarePttTargetChannelId()
            if (targetChannelId != null) {
                pttManager.requestPtt(targetChannelId)
            }
        }
        mediaButtonHandler.onPttRelease = {
            pttManager.releasePtt()
        }

        // Start NetworkMonitor for connectivity detection
        networkMonitor.start()

        // Observe connection state transitions for tones, notification updates, and channel rejoin
        scope.launch {
            signalingClient.connectionState.collect { currentState ->
                val prevState = previousConnectionState

                // Track disconnection start time
                if (prevState == com.voiceping.android.domain.model.ConnectionState.CONNECTED &&
                    (currentState == com.voiceping.android.domain.model.ConnectionState.RECONNECTING ||
                     currentState == com.voiceping.android.domain.model.ConnectionState.DISCONNECTED)) {
                    disconnectedSinceMs = System.currentTimeMillis()
                }

                // Handle transition to CONNECTED (reconnection succeeded)
                if (currentState == com.voiceping.android.domain.model.ConnectionState.CONNECTED &&
                    (prevState == com.voiceping.android.domain.model.ConnectionState.RECONNECTING ||
                     prevState == com.voiceping.android.domain.model.ConnectionState.CONNECTING)) {

                    // Calculate disconnect duration
                    val duration = disconnectedSinceMs?.let { System.currentTimeMillis() - it }

                    // Play connection tone only for long disconnections (5+ seconds)
                    if (duration != null && duration >= 5000) {
                        tonePlayer.playConnectionTone()
                    }

                    // Always rejoin channels after reconnection — server drops all
                    // channel memberships when the WebSocket disconnects, so even brief
                    // disconnections require full rejoin + transport recreation.
                    if (prevState == com.voiceping.android.domain.model.ConnectionState.RECONNECTING &&
                        _monitoredChannels.value.isNotEmpty()) {
                        launch {
                            Log.d(TAG, "Reconnected, cleaning up stale resources and rejoining ${_monitoredChannels.value.size} channels")
                            mediasoupClient.cleanup()
                            rejoinAllMonitoredChannels()
                        }
                    }

                    // Reset disconnect tracking
                    disconnectedSinceMs = null

                    // Update notification: no longer reconnecting
                    if (isServiceRunning) {
                        updateServiceNotificationReconnectingState(false)
                    }
                }

                // Handle transition to RECONNECTING
                if (currentState == com.voiceping.android.domain.model.ConnectionState.RECONNECTING) {
                    // Update notification: reconnecting
                    if (isServiceRunning) {
                        updateServiceNotificationReconnectingState(true)
                    }

                    // Schedule disconnection tone after 5 seconds (aligned with banner delay)
                    launch {
                        delay(5000L)
                        // Still not connected after 5s? Play disconnection tone
                        if (signalingClient.connectionState.value != com.voiceping.android.domain.model.ConnectionState.CONNECTED) {
                            tonePlayer.playDisconnectionTone()
                        }
                    }
                }

                // Handle transition to DISCONNECTED (full connection loss)
                if (currentState == com.voiceping.android.domain.model.ConnectionState.DISCONNECTED &&
                    prevState != com.voiceping.android.domain.model.ConnectionState.DISCONNECTED) {
                    Log.d(TAG, "Signaling disconnected, cleaning up mediasoup resources")
                    mediasoupClient.cleanup()
                }

                // Track previous state for next transition
                previousConnectionState = currentState
            }
        }

        // Wire MediasoupClient transport failure callbacks
        mediasoupClient.onSendTransportFailed = {
            pttManager.forceReleasePttTransportFailure()
        }

        mediasoupClient.onSendTransportRecovered = {
            Log.d(TAG, "Send transport recovered, PTT auto-recovers to normal state")
            // No toast, silent transition per user decision
        }

        // Observe transport health state and react
        scope.launch {
            mediasoupClient.transportHealthState.collect { state ->
                when (state) {
                    com.voiceping.android.domain.model.TransportHealthState.HEALTHY -> {
                        // Clear any reconnection state
                        autoRejoinAttempts = 0
                        autoRejoinJob?.cancel()
                        _showDisconnectedBanner.value = false
                        _isAutoRejoining.value = false
                        pttManager.pttDisabledForReconnect = false
                    }
                    com.voiceping.android.domain.model.TransportHealthState.SEND_DEGRADED -> {
                        // Partial failure: send broken, receive works
                        // User can still hear others but can't transmit
                        // Amber PTT handled by ViewModel via transportHealthState
                        Log.w(TAG, "Send transport degraded, receive still working")
                    }
                    com.voiceping.android.domain.model.TransportHealthState.FULLY_DISCONNECTED -> {
                        // Both transports gone — start auto-rejoin
                        Log.e(TAG, "Fully disconnected, starting auto-rejoin")
                        pttManager.pttDisabledForReconnect = true
                        startAutoRejoin()
                    }
                    com.voiceping.android.domain.model.TransportHealthState.RECONNECTING -> {
                        // In progress, no action needed
                    }
                }
            }
        }

        // Wire wake lock → location coordination
        wakeLockManager.onWakeLockReleased = {
            locationManager.onWakeLockReleased()
        }
        wakeLockManager.onWakeLockAcquired = {
            locationManager.onWakeLockAcquired()
        }

        // Wire battery saver → location coordination
        scope.launch {
            batterySaverMonitor.isBatterySaverEnabled.collect { enabled ->
                locationManager.onBatterySaverChanged(enabled)
            }
        }

        // Observe CHANNEL_LIST message for server power config (wakeLockTimeoutSeconds)
        scope.launch {
            signalingClient.messages
                .filter { it.type == SignalingType.CHANNEL_LIST }
                .collect { message ->
                    val data = message.data
                    val wakeLockTimeoutSeconds = data?.get("wakeLockTimeoutSeconds").asLongOrNull()
                    if (wakeLockTimeoutSeconds != null) {
                        wakeLockManager.setTimeoutFromServer(wakeLockTimeoutSeconds)
                    }
                }
        }
    }

    companion object {
        private const val TAG = "ChannelRepository"
        private const val MAX_CHANNELS = 5
    }

    suspend fun joinChannel(channelId: String, channelName: String, teamName: String): Result<Unit> {
        return try {
            // Guard: max 5 channels
            if (_monitoredChannels.value.size >= MAX_CHANNELS && channelId !in _monitoredChannels.value) {
                return Result.failure(Exception("Maximum $MAX_CHANNELS channels. Leave a channel to join another."))
            }

            // If channel already joined, return success (no-op)
            if (channelId in _monitoredChannels.value) {
                Log.d(TAG, "Channel $channelId already joined")
                return Result.success(Unit)
            }

            // Request JOIN_CHANNEL from server
            val joinResponse = signalingClient.request(
                SignalingType.JOIN_CHANNEL,
                mapOf("channelId" to channelId)
            )

            if (joinResponse.error != null) {
                return Result.failure(Exception(joinResponse.error))
            }

            // If first channel: set up audio routing and power management
            val isFirstChannel = _monitoredChannels.value.isEmpty()
            if (isFirstChannel) {
                audioRouter.requestAudioFocus()
                audioRouter.setSpeakerMode()

                // Set as primary
                _primaryChannelId.value = channelId

                // Acquire wake lock on first channel join
                wakeLockManager.acquire()

                // Start battery saver monitoring
                batterySaverMonitor.start()
            }

            // Create recv transport for every channel (each channel needs its own for audio consumption)
            mediasoupClient.createRecvTransport(channelId)

            // Start stats polling for this channel
            channelStatsPoller.startPolling(channelId)

            // Parse user count from join response
            val joinUserCount = joinResponse.data?.get("userCount").asIntOrNull() ?: 0

            // Create ChannelMonitoringState
            val channelState = ChannelMonitoringState(
                channelId = channelId,
                channelName = channelName,
                teamName = teamName,
                isPrimary = isFirstChannel,
                userCount = joinUserCount
            )

            // Add to monitored channels map
            _monitoredChannels.value = _monitoredChannels.value + (channelId to channelState)

            // Start observing speaker changes and channel state updates for this channel
            observeSpeakerChangesForChannel(channelId)
            observeChannelStateUpdates(channelId)

            // Persist monitored channels
            settingsRepository.setMonitoredChannels(_monitoredChannels.value.keys)

            // If primary, persist
            if (isFirstChannel) {
                settingsRepository.setPrimaryChannel(channelId)
            }

            // Start foreground service if first channel
            if (isFirstChannel && !isServiceRunning) {
                val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
                    action = ChannelMonitoringService.ACTION_START
                    putExtra(ChannelMonitoringService.EXTRA_CHANNEL_NAME, channelName)
                    putExtra(ChannelMonitoringService.EXTRA_MONITORING_COUNT, 0)
                }
                context.startForegroundService(serviceIntent)
                isServiceRunning = true
                Log.d(TAG, "Started ChannelMonitoringService")

                // Start AudioDeviceManager and MediaButtonHandler
                audioDeviceManager.start()
                mediaButtonHandler.setActive(true)
                // Load configured BT keycode from settings (use default if read fails)
                val btKeycode = try {
                    settingsRepository.getBluetoothPttButtonKeycode().first()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read BT keycode, using default", e)
                    85 // default keycode
                }
                mediaButtonHandler.setConfiguredKeyCode(btKeycode)
                Log.d(TAG, "Started AudioDeviceManager and MediaButtonHandler")

                // Auto-start location tracking on first channel join (if permission granted)
                try {
                    locationManager.startTracking()
                    Log.d(TAG, "Started location tracking")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Location permission not granted, skipping location tracking")
                }
            }

            // Update notification
            updateServiceNotification()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveChannel(channelId: String): Result<Unit> {
        return try {
            // Cancel speaker observer for this channel
            speakerObserverJobs[channelId]?.cancel()
            speakerObserverJobs.remove(channelId)

            // Cancel channel state observer for this channel
            channelStateObserverJobs[channelId]?.cancel()
            channelStateObserverJobs.remove(channelId)

            // Cancel fade job for this channel
            lastSpeakerFadeJobs[channelId]?.cancel()
            lastSpeakerFadeJobs.remove(channelId)

            // Stop stats polling for this channel
            channelStatsPoller.stopPolling(channelId)

            // Close all consumers for this channel
            channelConsumers[channelId]?.values?.forEach { consumerId ->
                mediasoupClient.closeConsumer(consumerId)
            }
            channelConsumers.remove(channelId)

            // Clean up channel's RecvTransport
            mediasoupClient.cleanupChannel(channelId)

            // Remove from monitored channels map
            _monitoredChannels.value = _monitoredChannels.value - channelId

            // If was primary and other channels remain, reassign primary to first remaining channel
            val wasPrimary = _primaryChannelId.value == channelId
            if (wasPrimary && _monitoredChannels.value.isNotEmpty()) {
                val newPrimary = _monitoredChannels.value.keys.first()
                setPrimaryChannel(newPrimary)
            } else if (_monitoredChannels.value.isEmpty()) {
                _primaryChannelId.value = null
            }

            // If last channel, clean up everything
            val isLastChannel = _monitoredChannels.value.isEmpty()
            if (isLastChannel) {
                audioRouter.releaseAudioFocus()
                audioRouter.resetAudioMode()
                mediasoupClient.cleanup()

                // Stop AudioDeviceManager and MediaButtonHandler
                audioDeviceManager.stop()
                mediaButtonHandler.setActive(false)
                Log.d(TAG, "Stopped AudioDeviceManager and MediaButtonHandler")

                // Stop monitoring service
                if (isServiceRunning) {
                    val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
                        action = ChannelMonitoringService.ACTION_STOP
                    }
                    context.startService(serviceIntent)
                    isServiceRunning = false
                    Log.d(TAG, "Stopped ChannelMonitoringService")
                }
            }

            // Send LEAVE_CHANNEL to server
            signalingClient.request(
                SignalingType.LEAVE_CHANNEL,
                mapOf("channelId" to channelId)
            )

            // Persist updated monitored channels
            settingsRepository.setMonitoredChannels(_monitoredChannels.value.keys)
            if (_primaryChannelId.value != null) {
                settingsRepository.setPrimaryChannel(_primaryChannelId.value!!)
            }

            // Update notification (or stop service if last channel)
            if (!isLastChannel) {
                updateServiceNotification()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun observeChannelStateUpdates(channelId: String) {
        channelStateObserverJobs[channelId]?.cancel()

        val job = CoroutineScope(Dispatchers.IO).launch {
            signalingClient.messages
                .filter { it.type == SignalingType.CHANNEL_STATE }
                .collect { message ->
                    val data = message.data ?: return@collect
                    val messageChannelId = data.get("channelId").asStringOrNull()
                    if (messageChannelId == channelId) {
                        val userCount = data.get("userCount").asIntOrNull()
                        if (userCount != null) {
                            updateChannelState(channelId) { state ->
                                state.copy(userCount = userCount)
                            }
                            // Update consumer count for stats polling (empty channel detection)
                            channelStatsPoller.updateConsumerCount(channelId, userCount)
                        }
                    }
                }
        }
        channelStateObserverJobs[channelId] = job
    }

    private fun observeSpeakerChangesForChannel(channelId: String) {
        // Cancel any existing observer for this channel
        speakerObserverJobs[channelId]?.cancel()

        // Launch new coroutine to collect speaker changed broadcasts
        val job = CoroutineScope(Dispatchers.IO).launch {
            signalingClient.messages
                .filter { it.type == SignalingType.SPEAKER_CHANGED }
                .collect { message ->
                    val data = message.data ?: return@collect

                    // Extract data from broadcast
                    val messageChannelId = data.get("channelId").asStringOrNull()
                    val speakerUserId = data.get("currentSpeaker").asStringOrNull()
                    val speakerName = data.get("speakerName").asStringOrNull()
                    val producerId = data.get("producerId").asStringOrNull()

                    // Only process messages for this channel
                    if (messageChannelId == channelId) {
                        if (speakerUserId != null && speakerName != null && producerId != null) {
                            // Speaker started transmitting
                            val newSpeaker = User(speakerUserId, speakerName)

                            // Update channel state
                            updateChannelState(channelId) { state ->
                                state.copy(
                                    currentSpeaker = newSpeaker,
                                    speakerStartTime = System.currentTimeMillis(),
                                    consumerId = producerId
                                )
                            }

                            // Cancel fade job when new speaker starts
                            lastSpeakerFadeJobs[channelId]?.cancel()
                            lastSpeakerFadeJobs.remove(channelId)

                            // Reset wake lock timeout on incoming audio activity
                            wakeLockManager.resetTimeout()

                            // Mark channel activity for stats polling
                            channelStatsPoller.markActivity(channelId)

                            // Play RX squelch open and transmission start haptic (only for incoming speakers, not own transmission)
                            if (pttManager.pttState.value !is PttState.Transmitting) {
                                tonePlayer.playRxSquelchOpen()
                                hapticFeedback.vibrateTransmissionStart()
                            }

                            // Close previous consumer if exists for this channel
                            channelConsumers[channelId]?.get(producerId)?.let { oldConsumerId ->
                                mediasoupClient.closeConsumer(oldConsumerId)
                            }

                            // Consume audio from this producer (guard: only if not muted)
                            val channelState = _monitoredChannels.value[channelId]
                            if (channelState?.isMuted == false) {
                                val actualConsumerId = mediasoupClient.consumeAudio(channelId, producerId, speakerUserId)

                                // Track consumer: producerId -> actual consumerId (NOT producerId!)
                                // The actual consumerId is needed for closeConsumer() and setConsumerVolume()
                                if (channelConsumers[channelId] == null) {
                                    channelConsumers[channelId] = ConcurrentHashMap()
                                }
                                channelConsumers[channelId]!![producerId] = actualConsumerId

                                // Apply audio mix mode to new consumer
                                applyAudioMixMode(currentAudioMixMode)
                            }
                        } else {
                            // Speaker stopped transmitting
                            val channelState = _monitoredChannels.value[channelId]
                            val previousSpeaker = channelState?.currentSpeaker

                            // Update channel state
                            updateChannelState(channelId) { state ->
                                state.copy(
                                    currentSpeaker = null,
                                    lastSpeaker = previousSpeaker
                                )
                            }

                            // Play RX squelch close (only for incoming speakers, not own transmission)
                            if (pttManager.pttState.value !is PttState.Transmitting) {
                                tonePlayer.playRxSquelchClose()
                            }

                            // Start last speaker fade timer
                            previousSpeaker?.let {
                                lastSpeakerFadeJobs[channelId]?.cancel()
                                lastSpeakerFadeJobs[channelId] = CoroutineScope(Dispatchers.IO).launch {
                                    delay(2500)
                                    updateChannelState(channelId) { state ->
                                        state.copy(lastSpeaker = null)
                                    }
                                    lastSpeakerFadeJobs.remove(channelId)
                                }
                            }

                            // Close all consumers for this channel
                            channelConsumers[channelId]?.values?.forEach { consId ->
                                mediasoupClient.closeConsumer(consId)
                            }
                            channelConsumers[channelId]?.clear()
                        }
                    }
                }
        }

        speakerObserverJobs[channelId] = job
    }

    suspend fun setPrimaryChannel(channelId: String) {
        // Guard: channel must be in monitored map
        if (channelId !in _monitoredChannels.value) {
            Log.w(TAG, "Cannot set primary: channel $channelId not monitored")
            return
        }

        // Update primary channel ID
        _primaryChannelId.value = channelId

        // Update all ChannelMonitoringState entries: set isPrimary true for target, false for others
        _monitoredChannels.value = _monitoredChannels.value.mapValues { (id, state) ->
            state.copy(isPrimary = id == channelId)
        }

        // Persist
        settingsRepository.setPrimaryChannel(channelId)

        // Apply audio mix mode (primary changed, volumes may need adjustment)
        applyAudioMixMode(currentAudioMixMode)

        // Update notification
        updateServiceNotification()
    }

    suspend fun muteChannel(channelId: String) {
        // Close ALL consumers for this channel (bandwidth savings)
        channelConsumers[channelId]?.values?.forEach { consumerId ->
            mediasoupClient.closeConsumer(consumerId)
        }
        channelConsumers[channelId]?.clear()

        // Update channel state
        updateChannelState(channelId) { state ->
            state.copy(
                isMuted = true,
                currentSpeaker = null
            )
        }

        Log.d(TAG, "Channel $channelId muted")
    }

    suspend fun unmuteChannel(channelId: String) {
        // Update channel state
        updateChannelState(channelId) { state ->
            state.copy(isMuted = false)
        }

        // Explicit active speaker check: if someone is currently speaking, immediately create consumer
        val channelState = _monitoredChannels.value[channelId]
        if (channelState?.currentSpeaker != null && channelState.consumerId != null) {
            val producerId = channelState.consumerId
            val speakerId = channelState.currentSpeaker.id

            val actualConsumerId = mediasoupClient.consumeAudio(channelId, producerId, speakerId)

            // Track consumer: producerId -> actual consumerId
            if (channelConsumers[channelId] == null) {
                channelConsumers[channelId] = ConcurrentHashMap()
            }
            channelConsumers[channelId]!![producerId] = actualConsumerId

            // Apply audio mix mode
            applyAudioMixMode(currentAudioMixMode)
        }

        Log.d(TAG, "Channel $channelId unmuted")
    }

    suspend fun setChannelVolume(channelId: String, volume: Float) {
        // Clamp volume to valid range
        val clampedVolume = volume.coerceIn(0.0f, 1.0f)

        // Update channel state
        updateChannelState(channelId) { state ->
            state.copy(volume = clampedVolume)
        }

        // Apply volume to active consumers for this channel
        channelConsumers[channelId]?.values?.forEach { consumerId ->
            mediasoupClient.setConsumerVolume(consumerId, clampedVolume)
        }

        Log.d(TAG, "Channel $channelId volume set to $clampedVolume")
    }

    private fun applyAudioMixMode(audioMixMode: AudioMixMode) {
        _monitoredChannels.value.forEach { (channelId, state) ->
            // Calculate target volume based on mode
            val targetVolume = when (audioMixMode) {
                AudioMixMode.EQUAL_VOLUME -> state.volume
                AudioMixMode.PRIMARY_PRIORITY -> {
                    if (state.isPrimary) state.volume else state.volume * 0.5f
                }
            }

            // Apply to all active consumers for this channel
            channelConsumers[channelId]?.values?.forEach { consumerId ->
                mediasoupClient.setConsumerVolume(consumerId, targetVolume)
            }
        }

        Log.d(TAG, "Applied audio mix mode: $audioMixMode")
    }

    suspend fun muteAllExceptPrimary() {
        _monitoredChannels.value.forEach { (channelId, state) ->
            if (!state.isPrimary && !state.isMuted) {
                muteChannel(channelId)
            }
        }
        Log.d(TAG, "Muted all channels except primary")
    }

    suspend fun unmuteAllChannels() {
        _monitoredChannels.value.forEach { (channelId, state) ->
            if (state.isMuted) {
                unmuteChannel(channelId)
            }
        }
        Log.d(TAG, "Unmuted all channels")
    }

    /**
     * Check if microphone permission is granted.
     * Used by ViewModel before requesting PTT.
     */
    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get hardware PTT target channel ID based on scan mode settings.
     *
     * Used by hardware PTT handlers (volume keys, Bluetooth buttons) to determine
     * which channel to transmit on.
     *
     * @return Channel ID to transmit on, or null if no channel available
     */
    fun getHardwarePttTargetChannelId(): String? {
        // Read PTT target mode from settings (cached, safe for main thread)
        val pttTargetMode = runBlocking {
            settingsRepository.getPttTargetMode().first()
        }

        return when (pttTargetMode) {
            PttTargetMode.ALWAYS_PRIMARY -> _primaryChannelId.value
            PttTargetMode.DISPLAYED_CHANNEL -> currentDisplayedChannelId ?: _primaryChannelId.value
        }
    }

    private suspend fun rejoinAllMonitoredChannels() {
        val currentChannels = _monitoredChannels.value
        if (currentChannels.isEmpty()) {
            Log.d(TAG, "No channels to rejoin")
            return
        }

        Log.d(TAG, "Rejoining ${currentChannels.size} channels after reconnection")

        // Re-acquire audio focus (may have been released during disconnect)
        audioRouter.requestAudioFocus()
        audioRouter.setSpeakerMode()

        for ((channelId, state) in currentChannels) {
            try {
                // Step 1: Rejoin channel on server
                signalingClient.request(
                    SignalingType.JOIN_CHANNEL,
                    mapOf("channelId" to channelId)
                )

                // Step 2: Recreate recv transport (cleanup() closed all transports)
                mediasoupClient.createRecvTransport(channelId)

                Log.d(TAG, "Rejoined channel with transport: ${state.channelName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rejoin channel ${state.channelName}", e)
            }
        }
    }

    fun disconnectAll() {
        // Stop NetworkMonitor
        networkMonitor.stop()

        // Stop AudioDeviceManager and MediaButtonHandler
        audioDeviceManager.stop()
        mediaButtonHandler.setActive(false)

        // Stop location tracking
        locationManager.stopTracking()
        Log.d(TAG, "Stopped location tracking")

        // Release wake lock immediately and stop battery saver monitoring
        wakeLockManager.releaseImmediate()
        batterySaverMonitor.stop()

        // Stop all channel stats polling
        channelStatsPoller.stopAll()

        // Stop monitoring service
        if (isServiceRunning) {
            val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
                action = ChannelMonitoringService.ACTION_STOP
            }
            context.startService(serviceIntent)
            isServiceRunning = false
            Log.d(TAG, "Stopped ChannelMonitoringService (disconnectAll)")
        }

        // Cancel all speaker observer jobs
        speakerObserverJobs.values.forEach { it.cancel() }
        speakerObserverJobs.clear()

        // Cancel all channel state observer jobs
        channelStateObserverJobs.values.forEach { it.cancel() }
        channelStateObserverJobs.clear()

        // Cancel all fade jobs
        lastSpeakerFadeJobs.values.forEach { it.cancel() }
        lastSpeakerFadeJobs.clear()

        // Leave all channels
        val channelIds = _monitoredChannels.value.keys.toList()
        CoroutineScope(Dispatchers.IO).launch {
            channelIds.forEach { channelId ->
                leaveChannel(channelId)
            }

            // Clear all maps
            channelConsumers.clear()
            _monitoredChannels.value = emptyMap()
            _primaryChannelId.value = null

            // Clear persisted state
            settingsRepository.clearMonitoredChannels()
        }
    }

    private fun updateChannelState(channelId: String, transform: (ChannelMonitoringState) -> ChannelMonitoringState) {
        _monitoredChannels.value[channelId]?.let { state ->
            _monitoredChannels.value = _monitoredChannels.value + (channelId to transform(state))
        }
    }

    private fun updateServiceNotification() {
        val primaryName = _monitoredChannels.value[_primaryChannelId.value]?.channelName ?: return
        val otherCount = _monitoredChannels.value.size - 1
        val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
            action = ChannelMonitoringService.ACTION_UPDATE_CHANNEL
            putExtra(ChannelMonitoringService.EXTRA_CHANNEL_NAME, primaryName)
            putExtra(ChannelMonitoringService.EXTRA_MONITORING_COUNT, otherCount)
        }
        context.startService(serviceIntent)
    }

    private fun updateServiceNotificationReconnectingState(isReconnecting: Boolean) {
        val primaryName = _monitoredChannels.value[_primaryChannelId.value]?.channelName ?: return
        val otherCount = _monitoredChannels.value.size - 1
        val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
            action = ChannelMonitoringService.ACTION_UPDATE_CHANNEL
            putExtra(ChannelMonitoringService.EXTRA_CHANNEL_NAME, primaryName)
            putExtra(ChannelMonitoringService.EXTRA_MONITORING_COUNT, otherCount)
            putExtra(ChannelMonitoringService.EXTRA_IS_RECONNECTING, isReconnecting)
        }
        context.startService(serviceIntent)
    }

    /**
     * Start Bluetooth button detection mode.
     * Waits for user to press any button on their Bluetooth headset.
     * Calls onButtonDetected callback with the detected keycode.
     */
    fun startButtonDetection(onButtonDetected: (Int) -> Unit) {
        mediaButtonHandler.onButtonDetected = onButtonDetected
        mediaButtonHandler.startDetectionMode()
    }

    /**
     * Stop Bluetooth button detection mode.
     */
    fun stopButtonDetection() {
        mediaButtonHandler.stopDetectionMode()
        mediaButtonHandler.onButtonDetected = null
    }

    /**
     * Start auto-rejoin with exponential backoff.
     * Attempts up to 5 times, then shows persistent banner.
     */
    private fun startAutoRejoin() {
        if (_isAutoRejoining.value) return // Already in progress

        _isAutoRejoining.value = true
        mediasoupClient.resetTransportHealth() // Set to RECONNECTING handled by us
        autoRejoinAttempts = 0

        autoRejoinJob?.cancel()
        autoRejoinJob = scope.launch {
            while (autoRejoinAttempts < maxAutoRejoinAttempts) {
                autoRejoinAttempts++
                val delayMs = (1000L * (1 shl (autoRejoinAttempts - 1))).coerceAtMost(30_000L)
                Log.d(TAG, "Auto-rejoin attempt $autoRejoinAttempts/$maxAutoRejoinAttempts in ${delayMs}ms")

                delay(delayMs)

                try {
                    // Rejoin all monitored channels
                    rejoinAllMonitoredChannels()

                    // If successful, transport callbacks will set HEALTHY
                    Log.d(TAG, "Auto-rejoin attempt $autoRejoinAttempts succeeded")
                    _isAutoRejoining.value = false
                    _showDisconnectedBanner.value = false
                    autoRejoinAttempts = 0
                    pttManager.pttDisabledForReconnect = false
                    return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-rejoin attempt $autoRejoinAttempts failed: ${e.message}")
                }
            }

            // All attempts exhausted — show persistent banner with manual retry
            Log.e(TAG, "Auto-rejoin failed after $maxAutoRejoinAttempts attempts")
            _isAutoRejoining.value = false
            _showDisconnectedBanner.value = true
        }
    }

    /**
     * Manual retry after auto-rejoin exhaustion.
     * Triggered by "Retry" button in persistent banner.
     */
    fun manualRetryRejoin() {
        _showDisconnectedBanner.value = false
        startAutoRejoin()
    }
}
