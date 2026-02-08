package com.voiceping.android.data.repository

import com.voiceping.android.data.audio.AudioRouter
import com.voiceping.android.data.network.MediasoupClient
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val audioRouter: AudioRouter
) {
    private val _currentSpeaker = MutableStateFlow<User?>(null)
    val currentSpeaker: StateFlow<User?> = _currentSpeaker.asStateFlow()

    private val _joinedChannelId = MutableStateFlow<String?>(null)
    val joinedChannelId: StateFlow<String?> = _joinedChannelId.asStateFlow()

    private var speakerObserverJob: Job? = null
    private var currentConsumerId: String? = null

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
                            _currentSpeaker.value = User(speakerUserId, speakerName)

                            // Close previous consumer if exists
                            currentConsumerId?.let { mediasoupClient.closeConsumer(it) }

                            // Consume audio from this producer
                            mediasoupClient.consumeAudio(producerId, speakerUserId)
                            currentConsumerId = producerId
                        } else {
                            // Speaker stopped transmitting
                            _currentSpeaker.value = null

                            // Close the consumer
                            currentConsumerId?.let { mediasoupClient.closeConsumer(it) }
                            currentConsumerId = null
                        }
                    }
                }
        }
    }

    fun disconnectAll() {
        // Cancel speaker observer
        speakerObserverJob?.cancel()
        speakerObserverJob = null

        // If joined to a channel, clean up
        _joinedChannelId.value?.let { channelId ->
            CoroutineScope(Dispatchers.IO).launch {
                leaveChannel(channelId)
            }
        }
    }
}
