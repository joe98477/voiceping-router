package com.voiceping.android.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.voiceping.android.data.audio.AudioRouter
import com.voiceping.android.data.audio.HapticFeedback
import com.voiceping.android.data.audio.TonePlayer
import com.voiceping.android.data.network.MediasoupClient
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.data.ptt.PttManager
import com.voiceping.android.data.ptt.PttState
import com.voiceping.android.data.storage.SettingsRepository
import com.voiceping.android.domain.model.AudioMixMode
import com.voiceping.android.domain.model.ChannelMonitoringState
import com.voiceping.android.domain.model.User
import com.voiceping.android.service.ChannelMonitoringService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val signalingClient: SignalingClient,
    private val mediasoupClient: MediasoupClient,
    private val audioRouter: AudioRouter,
    private val pttManager: PttManager,
    private val tonePlayer: TonePlayer,
    private val hapticFeedback: HapticFeedback,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    private val _monitoredChannels = MutableStateFlow<Map<String, ChannelMonitoringState>>(emptyMap())
    val monitoredChannels: StateFlow<Map<String, ChannelMonitoringState>> = _monitoredChannels.asStateFlow()

    private val _primaryChannelId = MutableStateFlow<String?>(null)
    val primaryChannelId: StateFlow<String?> = _primaryChannelId.asStateFlow()

    // Per-channel consumer tracking: channelId -> (producerId -> consumerId)
    private val channelConsumers = mutableMapOf<String, MutableMap<String, String>>()

    // Per-channel speaker observer jobs
    private val speakerObserverJobs = mutableMapOf<String, Job>()

    // Per-channel last speaker fade jobs
    private val lastSpeakerFadeJobs = mutableMapOf<String, Job>()

    private var isServiceRunning = false
    private var currentAudioMixMode = AudioMixMode.EQUAL_VOLUME

    // Expose PTT state via delegation to PttManager
    val pttState: StateFlow<PttState> = pttManager.pttState

    init {
        // Wire PttManager callbacks for tone/haptic feedback
        pttManager.onPttGranted = {
            tonePlayer.playPttStartTone()
            hapticFeedback.vibratePttPress()
        }
        pttManager.onPttDenied = {
            tonePlayer.playErrorTone()
            hapticFeedback.vibrateError()
        }
        pttManager.onPttReleased = {
            tonePlayer.playRogerBeep()
            hapticFeedback.vibrateRelease()
        }

        // Wire call interruption beep (distinct from roger beep)
        pttManager.onPttInterrupted = {
            tonePlayer.playCallInterruptionBeep()
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

            // If first channel: set up audio routing and create recv transport
            val isFirstChannel = _monitoredChannels.value.isEmpty()
            if (isFirstChannel) {
                audioRouter.requestAudioFocus()
                audioRouter.setEarpieceMode()
                mediasoupClient.createRecvTransport(channelId)

                // Set as primary
                _primaryChannelId.value = channelId
            }

            // Create ChannelMonitoringState
            val channelState = ChannelMonitoringState(
                channelId = channelId,
                channelName = channelName,
                teamName = teamName,
                isPrimary = isFirstChannel
            )

            // Add to monitored channels map
            _monitoredChannels.value = _monitoredChannels.value + (channelId to channelState)

            // Start observing speaker changes for this channel
            observeSpeakerChangesForChannel(channelId)

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

            // Cancel fade job for this channel
            lastSpeakerFadeJobs[channelId]?.cancel()
            lastSpeakerFadeJobs.remove(channelId)

            // Close all consumers for this channel
            channelConsumers[channelId]?.values?.forEach { consumerId ->
                mediasoupClient.closeConsumer(consumerId)
            }
            channelConsumers.remove(channelId)

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
                    val messageChannelId = data["channelId"] as? String
                    val speakerUserId = data["speakerUserId"] as? String
                    val speakerName = data["speakerName"] as? String
                    val producerId = data["producerId"] as? String

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

                            // Play RX squelch open (only for incoming speakers, not own transmission)
                            if (pttManager.pttState.value !is PttState.Transmitting) {
                                tonePlayer.playRxSquelchOpen()
                            }

                            // Close previous consumer if exists for this channel
                            channelConsumers[channelId]?.get(producerId)?.let { oldConsumerId ->
                                mediasoupClient.closeConsumer(oldConsumerId)
                            }

                            // Consume audio from this producer (guard: only if not muted)
                            val channelState = _monitoredChannels.value[channelId]
                            if (channelState?.isMuted == false) {
                                mediasoupClient.consumeAudio(producerId, speakerUserId)

                                // Track consumer
                                if (channelConsumers[channelId] == null) {
                                    channelConsumers[channelId] = mutableMapOf()
                                }
                                channelConsumers[channelId]!![producerId] = producerId

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

                            // Close the consumer
                            channelState?.consumerId?.let { consumerId ->
                                mediasoupClient.closeConsumer(consumerId)
                                channelConsumers[channelId]?.remove(consumerId)
                            }
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

            mediasoupClient.consumeAudio(producerId, speakerId)

            // Track consumer
            if (channelConsumers[channelId] == null) {
                channelConsumers[channelId] = mutableMapOf()
            }
            channelConsumers[channelId]!![producerId] = producerId

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

    fun disconnectAll() {
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
}
