package com.voiceping.android.data.network

import android.util.Log
import com.google.gson.Gson
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

/**
 * WebSocket signaling client for VoicePing server.
 *
 * Handles:
 * - WebSocket connection with JWT authentication via Sec-WebSocket-Protocol header
 * - Request-response correlation using UUID message IDs
 * - Broadcast message handling via SharedFlow
 * - Connection state management via StateFlow
 * - Heartbeat (PING every 25 seconds)
 */
@Singleton
class SignalingClient @Inject constructor(
    private val gson: Gson
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

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
                _connectionState.value = ConnectionState.FAILED

                // Complete all pending requests exceptionally
                pendingRequests.values.forEach { it.completeExceptionally(t) }
                pendingRequests.clear()

                heartbeatJob?.cancel()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: code=$code, reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                heartbeatJob?.cancel()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                heartbeatJob?.cancel()
            }
        })
    }

    /**
     * Send a request message and wait for response (request-response pattern).
     *
     * @param type Signaling message type
     * @param data Optional message data
     * @return Response message from server
     * @throws IllegalStateException if WebSocket not connected
     * @throws kotlinx.coroutines.TimeoutCancellationException if response not received within 10 seconds
     */
    suspend fun request(type: SignalingType, data: Map<String, Any> = emptyMap()): SignalingMessage {
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
        val message = SignalingMessage(type, null, data)
        val json = gson.toJson(message)
        webSocket?.send(json)
    }

    /**
     * Disconnect from WebSocket server.
     */
    fun disconnect() {
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
     * Start heartbeat coroutine that sends PING every 25 seconds.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_MS)
                send(SignalingType.PING)
            }
        }
    }

    companion object {
        private const val TAG = "SignalingClient"
        private const val REQUEST_TIMEOUT_MS = 10_000L // 10 seconds
        private const val HEARTBEAT_INTERVAL_MS = 25_000L // 25 seconds
    }
}
