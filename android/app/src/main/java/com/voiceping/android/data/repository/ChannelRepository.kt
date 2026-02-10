package com.voiceping.android.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.voiceping.android.data.audio.AudioRouter
import com.voiceping.android.data.audio.HapticFeedback
import com.voiceping.android.data.audio.TonePlayer
import com.voiceping.android.data.network.MediasoupClient
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.data.ptt.PttManager
import com.voiceping.android.data.ptt.PttState
import com.voiceping.android.domain.model.User
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
    @ApplicationContext private val context: Context
) {
    private val _currentSpeaker = MutableStateFlow<User?>(null)
    val currentSpeaker: StateFlow<User?> = _currentSpeaker.asStateFlow()

    private val _joinedChannelId = MutableStateFlow<String?>(null)
    val joinedChannelId: StateFlow<String?> = _joinedChannelId.asStateFlow()

    private val _lastSpeaker = MutableStateFlow<User?>(null)
    val lastSpeaker: StateFlow<User?> = _lastSpeaker.asStateFlow()

    private var speakerObserverJob: Job? = null
    private var currentConsumerId: String? = null
    private var lastSpeakerFadeJob: Job? = null

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
    }

    suspend fun joinChannel(channelId: String): Result<Unit> {
        return try {
            // 1. Request JOIN_CHANNEL from server
            val joinResponse = signalingClient.request(
                SignalingType.JOIN_CHANNEL,
                mapOf("channelId" to channelId)
            )

            if (joinResponse.error != null) {
                return Result.failure(Exception(joinResponse.error))
            }

            // 2. Set up audio routing (earpiece mode, request audio focus)
            audioRouter.requestAudioFocus()
            audioRouter.setEarpieceMode()

            // 3. Create receive transport
            mediasoupClient.createRecvTransport(channelId)

            // 4. Start observing speaker changes for this channel
            observeSpeakerChanges(channelId)

            // 5. Update joined channel ID
            _joinedChannelId.value = channelId

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveChannel(channelId: String): Result<Unit> {
        return try {
            // 1. Cancel speaker observer
            speakerObserverJob?.cancel()
            speakerObserverJob = null

            // 2. Clean up mediasoup (closes consumers and transport)
            mediasoupClient.cleanup()
            currentConsumerId = null

            // 3. Release audio focus and reset audio mode
            audioRouter.releaseAudioFocus()
            audioRouter.resetAudioMode()

            // 4. Request LEAVE_CHANNEL from server
            signalingClient.request(
                SignalingType.LEAVE_CHANNEL,
                mapOf("channelId" to channelId)
            )

            // 5. Clear current speaker
            _currentSpeaker.value = null

            // 6. Clear joined channel ID
            _joinedChannelId.value = null

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun observeSpeakerChanges(channelId: String) {
        // Cancel any existing observer
        speakerObserverJob?.cancel()

        // Launch new coroutine to collect speaker changed broadcasts
        speakerObserverJob = CoroutineScope(Dispatchers.IO).launch {
            signalingClient.messages
                .filter { it.type == SignalingType.SPEAKER_CHANGED }
                .collect { message ->
                    val data = message.data ?: return@collect

                    // Extract data from broadcast
                    val messageChannelId = data["channelId"] as? String
                    val speakerUserId = data["speakerUserId"] as? String
                    val speakerName = data["speakerName"] as? String
                    val producerId = data["producerId"] as? String

                    // Only process messages for our joined channel
                    if (messageChannelId == channelId) {
                        if (speakerUserId != null && speakerName != null && producerId != null) {
                            // Speaker started transmitting
                            val newSpeaker = User(speakerUserId, speakerName)
                            _currentSpeaker.value = newSpeaker

                            // Cancel fade job when new speaker starts
                            lastSpeakerFadeJob?.cancel()
                            _lastSpeaker.value = null

                            // Play RX squelch open (only for incoming speakers, not own transmission)
                            if (pttManager.pttState.value !is PttState.Transmitting) {
                                tonePlayer.playRxSquelchOpen()
                            }

                            // Close previous consumer if exists
                            currentConsumerId?.let { mediasoupClient.closeConsumer(it) }

                            // Consume audio from this producer
                            mediasoupClient.consumeAudio(producerId, speakerUserId)
                            currentConsumerId = producerId
                        } else {
                            // Speaker stopped transmitting
                            val previousSpeaker = _currentSpeaker.value
                            _currentSpeaker.value = null

                            // Play RX squelch close (only for incoming speakers, not own transmission)
                            if (pttManager.pttState.value !is PttState.Transmitting) {
                                tonePlayer.playRxSquelchClose()
                            }

                            // Start last speaker fade timer
                            previousSpeaker?.let { speaker ->
                                _lastSpeaker.value = speaker
                                lastSpeakerFadeJob?.cancel()
                                lastSpeakerFadeJob = CoroutineScope(Dispatchers.IO).launch {
                                    delay(2500)
                                    _lastSpeaker.value = null
                                }
                            }

                            // Close the consumer
                            currentConsumerId?.let { mediasoupClient.closeConsumer(it) }
                            currentConsumerId = null
                        }
                    }
                }
        }
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
        // Cancel speaker observer
        speakerObserverJob?.cancel()
        speakerObserverJob = null

        // Cancel last speaker fade timer
        lastSpeakerFadeJob?.cancel()
        lastSpeakerFadeJob = null

        // If joined to a channel, clean up
        _joinedChannelId.value?.let { channelId ->
            CoroutineScope(Dispatchers.IO).launch {
                leaveChannel(channelId)
            }
        }
    }
}
