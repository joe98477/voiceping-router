package com.voiceping.android.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.voiceping.android.data.network.dto.SignalingMessage
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.domain.model.ConnectionState
import kotlinx.coroutines.CompletableDeferred
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * WebSocket signaling client for VoicePing server.
 *
 * Handles:
 * - WebSocket connection with JWT authentication via Sec-WebSocket-Protocol header
 * - Request-response correlation using UUID message IDs
 * - Broadcast message handling via SharedFlow
 * - Connection state management via StateFlow
 * - Heartbeat (PING every 25 seconds)
 * - Automatic reconnection with exponential backoff (1s-30s cap, 5-minute max)
 * - Network-aware retry (resets backoff on network restore)
 * - Latency measurement via heartbeat PING round-trip time
 */
@Singleton
class SignalingClient @Inject constructor(
    private val gson: Gson,
    private val networkMonitor: NetworkMonitor
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<SignalingMessage>>()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    val messages: SharedFlow<SignalingMessage> = _messages.asSharedFlow()

    private val _latency = MutableStateFlow<Long?>(null)
    val latency: StateFlow<Long?> = _latency.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    // Reconnection state
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var lastServerUrl: String? = null
    private var lastToken: String? = null
    private var disconnectedAt: Long? = null
    private val maxReconnectDurationMs = 5 * 60 * 1000L // 5 minutes
    private var reconnectStartTime: Long? = null
    private var intentionalDisconnect = false

    init {
        // Observe network availability for intelligent reconnection
        scope.launch {
            networkMonitor.isNetworkAvailable.collect { available ->
                if (available && _connectionState.value == ConnectionState.RECONNECTING) {
                    Log.d(TAG, "Network available, resetting backoff and retrying immediately")
                    reconnectAttempt = 0 // Reset backoff per user decision
                    reconnectJob?.cancel()
                    scheduleReconnect()
                }
            }
        }
    }

    /**
     * Connect to WebSocket server with JWT authentication.
     *
     * @param serverUrl Base server URL (e.g., "wss://example.com" or "ws://localhost:3000")
     * @param token JWT token for authentication
     *
     * Authentication: JWT passed via Sec-WebSocket-Protocol header as "voiceping, <token>".
     * Server's handleProtocols callback extracts the token.
     */
    suspend fun connect(serverUrl: String, token: String) {
        // Store connection params for reconnection
        lastServerUrl = serverUrl
        lastToken = token
        intentionalDisconnect = false

        // Reset reconnection state on fresh connect
        if (_connectionState.value != ConnectionState.RECONNECTING) {
            reconnectAttempt = 0
        }

        val wsUrl = serverUrl.trimEnd('/') + "/ws"

        val request = Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", "voiceping, $token")
            .build()

        _connectionState.value = ConnectionState.CONNECTING

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected: ${response.message}")
                _connectionState.value = ConnectionState.CONNECTED
                resetReconnectionState()
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, SignalingMessage::class.java)

                    // Response to pending request (has correlation ID)
                    if (message.id != null && pendingRequests.containsKey(message.id)) {
                        pendingRequests.remove(message.id)?.complete(message)
                    } else {
                        // Broadcast message (no correlation ID, or server-initiated)
                        scope.launch {
                            _messages.emit(message)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse message: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)

                // Complete all pending requests exceptionally
                pendingRequests.values.forEach { it.completeExceptionally(t) }
                pendingRequests.clear()

                heartbeatJob?.cancel()

                // Start reconnection if not intentionally disconnected
                if (!intentionalDisconnect) {
                    if (disconnectedAt == null) {
                        disconnectedAt = System.currentTimeMillis()
                    }
                    if (reconnectStartTime == null) {
                        reconnectStartTime = System.currentTimeMillis()
                    }
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.FAILED
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: code=$code, reason=$reason")
                heartbeatJob?.cancel()

                // If close code is NOT 1000 (normal close), treat as unexpected disconnect
                if (code != 1000 && !intentionalDisconnect) {
                    if (disconnectedAt == null) {
                        disconnectedAt = System.currentTimeMillis()
                    }
                    if (reconnectStartTime == null) {
                        reconnectStartTime = System.currentTimeMillis()
                    }
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                heartbeatJob?.cancel()

                // If close code is NOT 1000 (normal close), treat as unexpected disconnect
                if (code != 1000 && !intentionalDisconnect) {
                    if (disconnectedAt == null) {
                        disconnectedAt = System.currentTimeMillis()
                    }
                    if (reconnectStartTime == null) {
                        reconnectStartTime = System.currentTimeMillis()
                    }
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        })
    }

    /**
     * Send a request message and wait for response (request-response pattern).
     *
     * @param type Signaling message type
     * @param data Optional message data as Map (simple string key-value pairs only;
     *             JSON string values will be double-encoded — use JsonObject overload for nested JSON)
     * @return Response message from server
     * @throws IllegalStateException if WebSocket not connected
     * @throws kotlinx.coroutines.TimeoutCancellationException if response not received within 10 seconds
     */
    suspend fun request(type: SignalingType, data: Map<String, Any> = emptyMap()): SignalingMessage {
        val jsonData = if (data.isEmpty()) null else gson.toJsonTree(data).asJsonObject
        return request(type, jsonData)
    }

    /**
     * Send a request with pre-built JsonObject data (preserves nested JSON structure).
     *
     * Use this overload when data contains nested JSON values (e.g., dtlsParameters,
     * rtpParameters) to avoid double-encoding JSON strings as string literals.
     */
    suspend fun request(type: SignalingType, data: JsonObject?): SignalingMessage {
        val id = UUID.randomUUID().toString()
        val message = SignalingMessage(type, id, data)
        val deferred = CompletableDeferred<SignalingMessage>()

        pendingRequests[id] = deferred

        val json = gson.toJson(message)
        val sent = webSocket?.send(json) ?: run {
            pendingRequests.remove(id)
            throw IllegalStateException("WebSocket not connected")
        }

        if (!sent) {
            pendingRequests.remove(id)
            throw IllegalStateException("Failed to send message (buffer full or connection closed)")
        }

        return try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            throw e
        }
    }

    /**
     * Send a fire-and-forget message (no response expected).
     *
     * @param type Signaling message type
     * @param data Optional message data
     */
    fun send(type: SignalingType, data: Map<String, Any> = emptyMap()) {
        val jsonData = if (data.isEmpty()) null else gson.toJsonTree(data).asJsonObject
        val message = SignalingMessage(type, null, jsonData)
        val json = gson.toJson(message)
        webSocket?.send(json)
    }

    /**
     * Disconnect from WebSocket server.
     */
    fun disconnect() {
        intentionalDisconnect = true
        resetReconnectionState()

        heartbeatJob?.cancel()
        heartbeatJob = null

        webSocket?.close(1000, "Client disconnect")
        webSocket = null

        pendingRequests.values.forEach {
            it.completeExceptionally(Exception("WebSocket disconnected"))
        }
        pendingRequests.clear()

        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Start heartbeat coroutine that sends PING every 25 seconds and measures latency.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_MS)

                // Measure round-trip latency via PING request-response
                try {
                    val startTime = System.currentTimeMillis()
                    request(SignalingType.PING)
                    val rtt = System.currentTimeMillis() - startTime
                    _latency.value = rtt
                    Log.d(TAG, "PING latency: ${rtt}ms")
                } catch (e: Exception) {
                    Log.w(TAG, "PING failed: ${e.message}")
                    _latency.value = null
                }
            }
        }
    }

    /**
     * Calculate exponential backoff delay for reconnection.
     *
     * Formula: 2^attempt * 1000ms, capped at 30 seconds.
     */
    private fun calculateBackoff(): Long {
        val delay = (2.0.pow(reconnectAttempt.toDouble()) * 1000L).toLong()
        return delay.coerceAtMost(30_000L) // Cap at 30 seconds per user decision
    }

    /**
     * Schedule next reconnection attempt with exponential backoff.
     *
     * Gives up after 5 minutes total retry window.
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel() // Cancel existing to prevent race conditions

        if (_connectionState.value != ConnectionState.RECONNECTING) return

        // Check 5-minute max retry window
        val startTime = reconnectStartTime ?: return
        if (System.currentTimeMillis() - startTime > maxReconnectDurationMs) {
            Log.w(TAG, "Reconnection timeout after 5 minutes, giving up")
            _connectionState.value = ConnectionState.FAILED
            resetReconnectionState()
            return
        }

        reconnectJob = scope.launch {
            val delay = calculateBackoff()
            Log.d(TAG, "Scheduling reconnect attempt ${reconnectAttempt + 1} in ${delay}ms")
            delay(delay)
            reconnectAttempt++
            try {
                val url = lastServerUrl ?: return@launch
                val token = lastToken ?: return@launch
                connect(url, token)
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect attempt failed", e)
                // onFailure will handle next retry
            }
        }
    }

    /**
     * Reset reconnection state after successful connection or intentional disconnect.
     */
    private fun resetReconnectionState() {
        reconnectAttempt = 0
        reconnectStartTime = null
        disconnectedAt = null
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * Manually retry connection after FAILED state (triggered by Retry button).
     */
    fun manualRetry() {
        if (_connectionState.value != ConnectionState.FAILED) return
        reconnectStartTime = System.currentTimeMillis() // Reset 5-minute window
        reconnectAttempt = 0
        _connectionState.value = ConnectionState.RECONNECTING
        scheduleReconnect()
    }

    companion object {
        private const val TAG = "SignalingClient"
        private const val REQUEST_TIMEOUT_MS = 10_000L // 10 seconds
        private const val HEARTBEAT_INTERVAL_MS = 25_000L // 25 seconds
    }
}
