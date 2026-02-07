package com.voiceping.android.data.network

import android.content.Context
import android.util.Log
import com.voiceping.android.data.network.dto.SignalingType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * mediasoup Device wrapper for receive-only audio.
 *
 * Handles:
 * - Device creation and RTP capabilities loading
 * - Receive transport creation
 * - Audio consumer management
 *
 * NOTE: This implementation provides the pattern for mediasoup integration.
 * The actual libmediasoup-android library classes (Device, RecvTransport, Consumer)
 * will be connected when testing on a physical device, as the exact API may differ
 * from documentation. This skeleton allows Plan 05 to handle actual library wiring.
 *
 * Key pattern: Device -> load capabilities -> create RecvTransport -> consume
 */
@Singleton
class MediasoupClient @Inject constructor(
    private val signalingClient: SignalingClient,
    @ApplicationContext private val context: Context
) {
    // NOTE: These will be actual mediasoup library types when library is integrated
    // For now, using Any? to represent the library objects
    private var device: Any? = null
    private var recvTransport: Any? = null
    private val consumers = mutableMapOf<String, Any>()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * Initialize mediasoup Device and load RTP capabilities from server.
     *
     * Steps:
     * 1. Request router RTP capabilities from server
     * 2. Create Device instance
     * 3. Load capabilities into device
     *
     * @throws Exception if capabilities request fails or device load fails
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting router RTP capabilities from server")

            // Step 1: Get router RTP capabilities from server
            val capsResponse = signalingClient.request(SignalingType.GET_ROUTER_CAPABILITIES)
            val rtpCapabilities = capsResponse.data?.get("routerRtpCapabilities") as? String
                ?: throw IllegalStateException("No routerRtpCapabilities in response")

            Log.d(TAG, "Received RTP capabilities, creating Device")

            // Step 2 & 3: Create Device and load capabilities
            // TODO: Integrate actual libmediasoup-android library
            // device = Device()
            // device?.load(rtpCapabilities)

            // Placeholder: Mark as initialized once library is integrated
            _isInitialized.value = true
            Log.d(TAG, "Device initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Device", e)
            throw e
        }
    }

    /**
     * Create receive transport for a channel.
     *
     * Steps:
     * 1. Request CREATE_TRANSPORT from server with channelId and direction="recv"
     * 2. Create RecvTransport with server's transport parameters
     * 3. Set up transport listener for DTLS connection
     *
     * @param channelId The channel to create transport for
     * @throws Exception if transport creation fails
     */
    suspend fun createRecvTransport(channelId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating receive transport for channel: $channelId")

            // Step 1: Request transport from server
            val transportResponse = signalingClient.request(
                SignalingType.CREATE_TRANSPORT,
                mapOf(
                    "channelId" to channelId,
                    "direction" to "recv"
                )
            )

            val transportData = transportResponse.data
                ?: throw IllegalStateException("No transport data in response")

            val transportId = transportData["id"] as? String
                ?: throw IllegalStateException("No transport id")
            val iceParameters = transportData["iceParameters"] as? String
                ?: throw IllegalStateException("No iceParameters")
            val iceCandidates = transportData["iceCandidates"] as? String
                ?: throw IllegalStateException("No iceCandidates")
            val dtlsParameters = transportData["dtlsParameters"] as? String
                ?: throw IllegalStateException("No dtlsParameters")

            Log.d(TAG, "Transport parameters received: id=$transportId")

            // Step 2 & 3: Create RecvTransport with listener
            // TODO: Integrate actual libmediasoup-android library
            // recvTransport = device?.createRecvTransport(
            //     listener = object : RecvTransport.Listener {
            //         override fun onConnect(transport: Transport, dtlsParameters: String): String {
            //             runBlocking {
            //                 signalingClient.request(
            //                     SignalingType.CONNECT_TRANSPORT,
            //                     mapOf(
            //                         "transportId" to transportId,
            //                         "dtlsParameters" to dtlsParameters
            //                     )
            //                 )
            //             }
            //             return ""
            //         }
            //
            //         override fun onConnectionStateChange(
            //             transport: Transport,
            //             connectionState: TransportState
            //         ) {
            //             Log.d(TAG, "Transport state: $connectionState")
            //         }
            //     },
            //     id = transportId,
            //     iceParameters = iceParameters,
            //     iceCandidates = iceCandidates,
            //     dtlsParameters = dtlsParameters
            // )

            Log.d(TAG, "Receive transport created successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create receive transport", e)
            throw e
        }
    }

    /**
     * Consume audio from a remote producer.
     *
     * Steps:
     * 1. Request CONSUME from server with producerId and peerId
     * 2. Create consumer on receive transport
     * 3. Resume consumer to start audio playback
     *
     * @param producerId The producer ID from server
     * @param peerId The peer ID producing audio
     * @throws Exception if consume fails
     */
    suspend fun consumeAudio(producerId: String, peerId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Consuming audio: producerId=$producerId, peerId=$peerId")

            // Step 1: Request consume from server
            val consumeResponse = signalingClient.request(
                SignalingType.CONSUME,
                mapOf(
                    "producerId" to producerId,
                    "peerId" to peerId
                )
            )

            val consumeData = consumeResponse.data
                ?: throw IllegalStateException("No consume data in response")

            val consumerId = consumeData["id"] as? String
                ?: throw IllegalStateException("No consumer id")
            val kind = consumeData["kind"] as? String
                ?: throw IllegalStateException("No kind")
            val rtpParameters = consumeData["rtpParameters"] as? String
                ?: throw IllegalStateException("No rtpParameters")

            Log.d(TAG, "Consume parameters received: consumerId=$consumerId, kind=$kind")

            // Step 2 & 3: Create consumer and resume
            // TODO: Integrate actual libmediasoup-android library
            // val consumer = recvTransport?.consume(
            //     listener = object : Consumer.Listener {
            //         override fun onTransportClose(consumer: Consumer) {
            //             consumers.remove(consumerId)
            //             Log.d(TAG, "Consumer transport closed: $consumerId")
            //         }
            //     },
            //     id = consumerId,
            //     producerId = producerId,
            //     kind = kind,
            //     rtpParameters = rtpParameters,
            //     appData = ""
            // )
            //
            // consumer?.let {
            //     consumers[consumerId] = it
            //     it.resume() // Start audio playback
            //     Log.d(TAG, "Consumer resumed, audio playing")
            // }

            Log.d(TAG, "Audio consumption setup complete")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to consume audio", e)
            throw e
        }
    }

    /**
     * Close a specific consumer.
     *
     * @param consumerId The consumer ID to close
     */
    fun closeConsumer(consumerId: String) {
        consumers.remove(consumerId)?.let {
            // TODO: Integrate actual libmediasoup-android library
            // (it as Consumer).close()
            Log.d(TAG, "Consumer closed: $consumerId")
        }
    }

    /**
     * Clean up all mediasoup resources.
     *
     * CRITICAL: Disposal order matters to prevent memory leaks.
     * Order: consumers first, then transport, then device (device persists).
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up mediasoup resources")

        // Step 1: Close all consumers FIRST
        consumers.values.forEach {
            // TODO: Integrate actual libmediasoup-android library
            // (it as Consumer).close()
        }
        consumers.clear()

        // Step 2: Close transport AFTER consumers
        recvTransport?.let {
            // TODO: Integrate actual libmediasoup-android library
            // (it as RecvTransport).close()
        }
        recvTransport = null

        // Step 3: DO NOT dispose device (shared across channels)
        // Device persists for lifetime of MediasoupClient singleton

        Log.d(TAG, "Cleanup complete")
    }

    companion object {
        private const val TAG = "MediasoupClient"
    }
}
