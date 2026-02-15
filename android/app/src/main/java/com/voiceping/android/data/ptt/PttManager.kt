package com.voiceping.android.data.ptt

import android.content.Context
import android.content.Intent
import android.util.Log
import com.voiceping.android.data.network.MediasoupClient
import com.voiceping.android.data.network.SignalingClient
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.domain.model.TransportHealthState
import com.voiceping.android.service.AudioCaptureService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
 * - Error: Producer creation failed after retries, auto-returns to Idle after 500ms
 *
 * CRITICAL: State transition requires server confirmation (NOT optimistic).
 * User sees Requesting state (subtle loading pulse) until server responds.
 */
sealed class PttState {
    object Idle : PttState()
    object Requesting : PttState()
    object Transmitting : PttState()
    object Denied : PttState()
    data class Error(val reason: String) : PttState()
}

/**
 * PTT Manager - orchestrates PTT transmission flow.
 *
 * Flow:
 * 1. requestPtt() -> send PTT_START to server -> wait for response
 * 2. If granted: start foreground service -> create SendTransport (idempotent) -> start producing
 * 3. Audio flows: WebRTC AudioSource (internal capture) -> Producer (Opus encoding) -> SendTransport -> RTP
 * 4. releasePtt() -> stop producing (closes Producer, disposes resources) -> stop service -> send PTT_STOP
 *
 * Callbacks: onPttGranted, onPttDenied, onPttReleased allow Plan 04 to wire in
 * TonePlayer/HapticFeedback without circular dependencies.
 */
@Singleton
class PttManager @Inject constructor(
    private val signalingClient: SignalingClient,
    private val mediasoupClient: MediasoupClient,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pttState = MutableStateFlow<PttState>(PttState.Idle)
    val pttState: StateFlow<PttState> = _pttState.asStateFlow()

    private val _ackResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val ackResult: SharedFlow<Boolean> = _ackResult.asSharedFlow()

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
     * Callbacks for error feedback and server acknowledgment.
     * - onPttError: Triggered on producer creation failure after retries (double-buzz haptic + error toast)
     * - onServerAck: Triggered when server acknowledges transmission (Plan 17-02 wires green/red flash)
     */
    var onPttError: (() -> Unit)? = null
    var onServerAck: ((success: Boolean) -> Unit)? = null

    /**
     * Toggle mode configuration (set by ViewModel from SettingsRepository)
     */
    var maxToggleDuration: Int = 60
    var currentPttMode: com.voiceping.android.domain.model.PttMode = com.voiceping.android.domain.model.PttMode.PRESS_AND_HOLD
    private var maxDurationJob: Job? = null

    /**
     * Set by ChannelRepository when auto-rejoin is in progress.
     * When true, requestPtt() is blocked (PTT button grayed out).
     */
    @Volatile
    var pttDisabledForReconnect: Boolean = false

    /**
     * Request PTT from server.
     *
     * CRITICAL: NOT optimistic. State goes Idle -> Requesting -> (Transmitting | Denied).
     * User sees Requesting state (loading pulse) until server responds.
     *
     * Steps when granted:
     * 1. Server grants PTT
     * 2. Start foreground service
     * 3. Create SendTransport (idempotent singleton)
     * 4. Start producing (creates AudioSource + AudioTrack + Producer with Opus config)
     * 5. Notify callback (tone/haptic feedback)
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

        // Guard: PTT disabled during auto-rejoin
        if (pttDisabledForReconnect) {
            Log.w(TAG, "PTT disabled: auto-rejoin in progress")
            onPttDenied?.invoke()
            return
        }

        // Guard: Send transport degraded (amber state)
        if (mediasoupClient.transportHealthState.value == com.voiceping.android.domain.model.TransportHealthState.SEND_DEGRADED) {
            Log.w(TAG, "PTT press during send transport degradation")
            // Toast will be shown by ViewModel observing this
            onPttDenied?.invoke() // Triggers existing denied feedback
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

                    // Steps 4 & 5: Create send transport and start producing with retry logic
                    // Producer creation retries up to 2 times with exponential backoff on failure
                    val maxRetries = 2
                    var lastError: Exception? = null
                    var producerCreated = false
                    var producerId: String? = null

                    for (attempt in 0..maxRetries) {
                        // Check PTT state each iteration — user may have cancelled
                        if (_pttState.value !is PttState.Transmitting) {
                            Log.d(TAG, "PTT state changed during retry, aborting")
                            return@launch
                        }

                        try {
                            if (attempt > 0) {
                                val delayMs = (1000L * (1 shl (attempt - 1))) // 1s, 2s
                                Log.d(TAG, "Retry attempt $attempt/$maxRetries after ${delayMs}ms")
                                delay(delayMs)
                            }

                            mediasoupClient.createSendTransport(channelId)
                            producerId = mediasoupClient.startProducing()
                            producerCreated = true
                            Log.d(TAG, "Producer created on attempt ${attempt + 1}: $producerId")
                            break
                        } catch (e: Exception) {
                            lastError = e
                            Log.w(TAG, "Producer creation failed (attempt ${attempt + 1}/${maxRetries + 1}): ${e.message}")
                        }
                    }

                    if (producerCreated && producerId != null) {
                        // Step 6: Notify callback (Plan 04 will wire in tone/haptic)
                        onPttGranted?.invoke()

                        // Step 6a: Async ACK check (does not block PTT grant)
                        scope.launch {
                            try {
                                val ackResponse = withTimeout(2000L) {
                                    signalingClient.request(
                                        SignalingType.PRODUCER_ACK,
                                        mapOf("producerId" to producerId, "channelId" to channelId)
                                    )
                                }
                                val success = ackResponse.error == null
                                _ackResult.tryEmit(success)
                                Log.d(TAG, "Server ACK: $success")
                            } catch (e: Exception) {
                                Log.w(TAG, "Server ACK timeout or error: ${e.message}")
                                _ackResult.tryEmit(false)
                            }
                        }

                        // Step 7: If TOGGLE mode, start max duration timer
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
                        // All retries exhausted
                        Log.e(TAG, "Producer creation failed after ${maxRetries + 1} attempts: ${lastError?.message}")
                        _pttState.value = PttState.Error(lastError?.message ?: "Unable to transmit")
                        onPttError?.invoke()

                        // Auto-release to Idle after brief error display (500ms, same as Denied)
                        delay(500)
                        _pttState.value = PttState.Idle

                        // Stop foreground service since we can't transmit
                        val stopIntent = Intent(context, AudioCaptureService::class.java).apply {
                            action = AudioCaptureService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    }

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
                _pttState.value = PttState.Error(e.message ?: "Connection error")
                onPttError?.invoke()
                delay(500)
                _pttState.value = PttState.Idle
            }
        }
    }

    /**
     * Release PTT (stop transmission).
     *
     * Steps:
     * 1. Cancel timer
     * 2. Notify callback (tone/haptic feedback)
     * 3. Reset state
     * 4. Stop producing (closes Producer, disposes AudioSource + AudioTrack) -> stop service -> notify server
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

        // Step 4: Cleanup on IO thread
        scope.launch {
            try {
                // Stop producing (closes Producer, disposes AudioSource + AudioTrack)
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
                // Stop producing (closes Producer, disposes AudioSource + AudioTrack)
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
     * Force-release PTT due to transport failure.
     *
     * Distinct from forceReleasePtt() (phone call interruption):
     * - Uses onPttError callback (double-buzz) instead of onPttInterrupted (double beep)
     * - Does NOT send PTT_STOP to server (transport is already broken)
     */
    fun forceReleasePttTransportFailure() {
        if (_pttState.value !is PttState.Transmitting && _pttState.value !is PttState.Requesting) {
            Log.d(TAG, "Not transmitting, nothing to force-release for transport failure")
            return
        }

        Log.w(TAG, "Force-releasing PTT (transport failure)")

        maxDurationJob?.cancel()
        maxDurationJob = null

        // Error feedback (double-buzz + error tone)
        onPttError?.invoke()

        _pttState.value = PttState.Idle
        val channelId = currentChannelId
        transmissionStartTime = 0
        currentChannelId = null

        // Cleanup on IO thread — do NOT send PTT_STOP (transport broken)
        scope.launch {
            try {
                mediasoupClient.stopProducing()

                val stopIntent = Intent(context, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_STOP
                }
                context.startService(stopIntent)

                Log.d(TAG, "PTT force-released (transport failure)")
            } catch (e: Exception) {
                Log.e(TAG, "Error during transport failure PTT release cleanup", e)
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
