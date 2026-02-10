package com.voiceping.android.data.ptt

import android.content.Context
import android.content.Intent
import android.util.Log
import com.voiceping.android.data.audio.AudioCaptureManager
import com.voiceping.android.data.audio.AudioRouter
import com.voiceping.android.data.network.MediasoupClient
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.service.AudioCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PTT state machine.
 *
 * States:
 * - Idle: Not transmitting, ready for PTT request
 * - Requesting: Waiting for server PTT grant
 * - Transmitting: Server granted, actively sending audio
 * - Denied: Server denied PTT request (busy channel), will auto-return to Idle after 500ms
 *
 * CRITICAL: State transition requires server confirmation (NOT optimistic).
 * User sees Requesting state (subtle loading pulse) until server responds.
 */
sealed class PttState {
    object Idle : PttState()
    object Requesting : PttState()
    object Transmitting : PttState()
    object Denied : PttState()
}

/**
 * PTT Manager - orchestrates PTT transmission flow.
 *
 * Flow:
 * 1. requestPtt() -> send PTT_START to server -> wait for response
 * 2. If granted: start foreground service -> create send transport -> start producing -> start capture
 * 3. Audio flows: AudioRecord -> AudioCaptureManager callback -> MediasoupClient.sendAudioData()
 * 4. releasePtt() -> stop capture -> stop producing -> stop service -> send PTT_STOP
 *
 * Callbacks: onPttGranted, onPttDenied, onPttReleased allow Plan 04 to wire in
 * TonePlayer/HapticFeedback without circular dependencies.
 */
@Singleton
class PttManager @Inject constructor(
    private val signalingClient: SignalingClient,
    private val mediasoupClient: MediasoupClient,
    private val audioCaptureManager: AudioCaptureManager,
    private val audioRouter: AudioRouter,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pttState = MutableStateFlow<PttState>(PttState.Idle)
    val pttState: StateFlow<PttState> = _pttState.asStateFlow()

    private var transmissionStartTime: Long = 0
    private var currentChannelId: String? = null

    /**
     * Callbacks for Plan 04 integration (TonePlayer, HapticFeedback).
     * These avoid circular dependencies by not injecting those components directly.
     */
    var onPttGranted: (() -> Unit)? = null
    var onPttDenied: (() -> Unit)? = null
    var onPttReleased: (() -> Unit)? = null

    /**
     * Toggle mode configuration (set by ViewModel from SettingsRepository)
     */
    var maxToggleDuration: Int = 60
    var currentPttMode: com.voiceping.android.domain.model.PttMode = com.voiceping.android.domain.model.PttMode.PRESS_AND_HOLD
    private var maxDurationJob: Job? = null

    /**
     * Request PTT from server.
     *
     * CRITICAL: NOT optimistic. State goes Idle -> Requesting -> (Transmitting | Denied).
     * User sees Requesting state (loading pulse) until server responds.
     *
     * @param channelId Channel to request PTT for
     */
    fun requestPtt(channelId: String) {
        // Guard: already in use
        if (_pttState.value !is PttState.Idle) {
            Log.w(TAG, "PTT already active, ignoring request")
            return
        }

        _pttState.value = PttState.Requesting
        Log.d(TAG, "PTT requested for channel: $channelId")

        scope.launch {
            try {
                // Step 1: Request PTT from server (WAIT for response)
                val response = signalingClient.request(
                    SignalingType.PTT_START,
                    mapOf("channelId" to channelId)
                )

                // Step 2: Check if granted
                if (response.error == null) {
                    // PTT GRANTED
                    Log.d(TAG, "PTT granted by server")
                    _pttState.value = PttState.Transmitting
                    transmissionStartTime = System.currentTimeMillis()
                    currentChannelId = channelId

                    // Step 3: Start foreground service (microphone permission)
                    val startIntent = Intent(context, AudioCaptureService::class.java).apply {
                        action = AudioCaptureService.ACTION_START
                    }
                    context.startForegroundService(startIntent)

                    // Step 4: Set audio capture callback to forward to mediasoup
                    audioCaptureManager.onAudioData = { buffer, length ->
                        mediasoupClient.sendAudioData(buffer, length)
                    }

                    // Step 5: Create send transport
                    mediasoupClient.createSendTransport(channelId)

                    // Step 6: Start producing (configure Opus codec)
                    mediasoupClient.startProducing()

                    // Step 7: Start audio capture
                    audioCaptureManager.startCapture()

                    // Step 8: Notify callback (Plan 04 will wire in tone/haptic)
                    onPttGranted?.invoke()

                    // Step 9: If TOGGLE mode, start max duration timer
                    if (currentPttMode == com.voiceping.android.domain.model.PttMode.TOGGLE) {
                        maxDurationJob?.cancel()
                        maxDurationJob = scope.launch {
                            delay(maxToggleDuration * 1000L)
                            Log.d(TAG, "Toggle mode max duration reached, auto-releasing PTT")
                            releasePtt()
                        }
                    }

                    Log.d(TAG, "PTT transmission started")

                } else {
                    // PTT DENIED (channel busy)
                    Log.w(TAG, "PTT denied by server: ${response.error}")
                    _pttState.value = PttState.Denied
                    onPttDenied?.invoke()

                    // Auto-return to Idle after 500ms
                    delay(500)
                    _pttState.value = PttState.Idle
                }

            } catch (e: Exception) {
                Log.e(TAG, "PTT request failed", e)
                _pttState.value = PttState.Idle
                onPttDenied?.invoke()
            }
        }
    }

    /**
     * Release PTT (stop transmission).
     *
     * Cleanup order: callback -> stop capture -> stop producing -> stop service -> send PTT_STOP
     */
    fun releasePtt() {
        if (_pttState.value !is PttState.Transmitting) {
            Log.w(TAG, "Not transmitting, ignoring release")
            return
        }

        Log.d(TAG, "Releasing PTT")

        try {
            // Step 1: Cancel max duration timer if active
            maxDurationJob?.cancel()
            maxDurationJob = null

            // Step 2: Notify callback (Plan 04 will wire in tone/haptic)
            onPttReleased?.invoke()

            // Step 3: Stop audio capture
            audioCaptureManager.stopCapture()

            // Step 4: Stop producing
            mediasoupClient.stopProducing()

            // Step 5: Stop foreground service
            val stopIntent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_STOP
            }
            context.startService(stopIntent)

            // Step 6: Send PTT_STOP to server
            signalingClient.send(
                SignalingType.PTT_STOP,
                mapOf("channelId" to currentChannelId!!)
            )

            // Step 7: Reset state
            _pttState.value = PttState.Idle
            transmissionStartTime = 0
            currentChannelId = null

            Log.d(TAG, "PTT released")

        } catch (e: Exception) {
            Log.e(TAG, "Error releasing PTT", e)
            _pttState.value = PttState.Idle
        }
    }

    /**
     * Get current transmission duration in seconds.
     *
     * @return Duration in seconds, or 0 if not transmitting
     */
    fun getTransmissionDurationSeconds(): Long {
        return if (_pttState.value is PttState.Transmitting) {
            (System.currentTimeMillis() - transmissionStartTime) / 1000
        } else {
            0
        }
    }

    companion object {
        private const val TAG = "PttManager"
    }
}
