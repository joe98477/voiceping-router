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
     * Callback for phone call interruption (Plan 07-03 will wire TonePlayer.playCallInterruptionBeep())
     * Distinct from onPttReleased which plays roger beep (intentional stop).
     */
    var onPttInterrupted: (() -> Unit)? = null

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

        // Guard: check connection state (PTT stays interactive, error on press while disconnected)
        val currentState = signalingClient.connectionState.value
        if (currentState != com.voiceping.android.domain.model.ConnectionState.CONNECTED) {
            Log.w(TAG, "PTT press ignored: not connected (state=$currentState)")
            // Trigger error feedback without changing PTT state
            onPttDenied?.invoke()
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

        // Step 1: Cancel max duration timer if active
        maxDurationJob?.cancel()
        maxDurationJob = null

        // Step 2: Notify callback (tone/haptic feedback)
        onPttReleased?.invoke()

        // Step 3: Reset state immediately (UI responsive)
        _pttState.value = PttState.Idle
        val channelId = currentChannelId
        transmissionStartTime = 0
        currentChannelId = null

        // Step 4: Cleanup on IO thread (stopCapture joins thread for up to 1s)
        scope.launch {
            try {
                audioCaptureManager.stopCapture()
                mediasoupClient.stopProducing()

                val stopIntent = Intent(context, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_STOP
                }
                context.startService(stopIntent)

                channelId?.let {
                    signalingClient.send(
                        SignalingType.PTT_STOP,
                        mapOf("channelId" to it)
                    )
                }

                Log.d(TAG, "PTT released")
            } catch (e: Exception) {
                Log.e(TAG, "Error during PTT release cleanup", e)
            }
        }
    }

    /**
     * Force-release PTT due to phone call interruption.
     *
     * Distinct from normal releasePtt():
     * - Uses onPttInterrupted callback (double beep) instead of onPttReleased (roger beep)
     * - Signals to other users that speaker was interrupted by phone call
     *
     * User decision: "If user was transmitting during call: force-release PTT with
     * a distinct double beep (different from normal roger beep) to signal call
     * interruption to other users"
     */
    fun forceReleasePtt() {
        if (_pttState.value !is PttState.Transmitting) {
            Log.d(TAG, "Not transmitting, nothing to force-release")
            return
        }

        Log.d(TAG, "Force-releasing PTT (phone call interruption)")

        // Step 1: Cancel max duration timer if active
        maxDurationJob?.cancel()
        maxDurationJob = null

        // Step 2: Play call interruption beep (distinct from roger beep)
        onPttInterrupted?.invoke()

        // Step 3: Reset state immediately
        _pttState.value = PttState.Idle
        val channelId = currentChannelId
        transmissionStartTime = 0
        currentChannelId = null

        // Step 4: Cleanup on IO thread
        scope.launch {
            try {
                audioCaptureManager.stopCapture()
                mediasoupClient.stopProducing()

                val stopIntent = Intent(context, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_STOP
                }
                context.startService(stopIntent)

                channelId?.let {
                    signalingClient.send(
                        SignalingType.PTT_STOP,
                        mapOf("channelId" to it)
                    )
                }

                Log.d(TAG, "PTT force-released (phone call interruption)")
            } catch (e: Exception) {
                Log.e(TAG, "Error during force PTT release cleanup", e)
            }
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
