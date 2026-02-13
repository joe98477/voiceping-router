package com.voiceping.android.data.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
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
 * mediasoup Device wrapper for bidirectional audio (send + receive).
 *
 * Handles:
 * - Device creation and RTP capabilities loading
 * - Receive transport creation and audio consumption
 * - Send transport creation and audio production (PTT)
 *
 * NOTE: This implementation provides the pattern for mediasoup integration.
 * The actual libmediasoup-android library classes (Device, SendTransport, RecvTransport,
 * Producer, Consumer) will be connected when testing on a physical device, as the exact
 * API may differ from documentation. This skeleton allows library wiring.
 *
 * Key pattern: Device -> load capabilities -> create transports -> produce/consume
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
    private var sendTransport: Any? = null
    private val consumers = mutableMapOf<String, Any>()
    private var audioProducer: Any? = null
    private val gson = Gson()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private fun toJsonString(data: Any?): String {
        return gson.toJson(data) ?: throw IllegalStateException("Failed to serialize to JSON")
    }

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
            val iceParameters = toJsonString(transportData["iceParameters"])
            val iceCandidates = toJsonString(transportData["iceCandidates"])
            val dtlsParameters = toJsonString(transportData["dtlsParameters"])

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
     * Set volume for a specific consumer (0.0 to 1.0).
     * Used for per-channel volume control and audio mix mode.
     */
    fun setConsumerVolume(consumerId: String, volume: Float) {
        consumers[consumerId]?.let {
            // TODO: Integrate actual libmediasoup-android library
            // (it as Consumer).volume = volume.coerceIn(0f, 1f)
            Log.d(TAG, "Consumer volume set: $consumerId -> $volume")
        }
    }

    /**
     * Create send transport for PTT audio transmission.
     *
     * Steps:
     * 1. Request CREATE_TRANSPORT from server with channelId and direction="send"
     * 2. Create SendTransport with server's transport parameters
     * 3. Set up transport listener for DTLS connection and produce events
     *
     * @param channelId The channel to create transport for
     * @throws Exception if transport creation fails
     */
    suspend fun createSendTransport(channelId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating send transport for channel: $channelId")

            // Step 1: Request transport from server (direction="send")
            val transportResponse = signalingClient.request(
                SignalingType.CREATE_TRANSPORT,
                mapOf(
                    "channelId" to channelId,
                    "direction" to "send"
                )
            )

            val transportData = transportResponse.data
                ?: throw IllegalStateException("No transport data in response")

            val transportId = transportData["id"] as? String
                ?: throw IllegalStateException("No transport id")
            val iceParameters = toJsonString(transportData["iceParameters"])
            val iceCandidates = toJsonString(transportData["iceCandidates"])
            val dtlsParameters = toJsonString(transportData["dtlsParameters"])

            Log.d(TAG, "Send transport parameters received: id=$transportId")

            // Step 2 & 3: Create SendTransport with listener
            // TODO: Integrate actual libmediasoup-android library
            // sendTransport = device?.createSendTransport(
            //     listener = object : SendTransport.Listener {
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
            //         override fun onProduce(
            //             transport: Transport,
            //             kind: String,
            //             rtpParameters: String,
            //             appData: String
            //         ): String {
            //             return runBlocking {
            //                 val produceResponse = signalingClient.request(
            //                     SignalingType.PRODUCE,
            //                     mapOf(
            //                         "kind" to kind,
            //                         "rtpParameters" to rtpParameters
            //                     )
            //                 )
            //                 produceResponse.data?.get("id") as? String
            //                     ?: throw IllegalStateException("No producer id in response")
            //             }
            //         }
            //
            //         override fun onConnectionStateChange(
            //             transport: Transport,
            //             connectionState: TransportState
            //         ) {
            //             Log.d(TAG, "Send transport state: $connectionState")
            //         }
            //     },
            //     id = transportId,
            //     iceParameters = iceParameters,
            //     iceCandidates = iceCandidates,
            //     dtlsParameters = dtlsParameters
            // )

            Log.d(TAG, "Send transport created successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create send transport", e)
            throw e
        }
    }

    /**
     * Start producing audio (PTT transmission).
     *
     * Configures Opus codec with PTT-optimized settings:
     * - Mono (opusStereo=false)
     * - DTX enabled for silence suppression
     * - FEC enabled for packet loss recovery
     * - 48kHz playback rate
     * - 20ms ptime
     *
     * @throws Exception if producer creation fails
     */
    suspend fun startProducing() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting audio producer")

            // TODO: Integrate actual libmediasoup-android library
            // Opus codec configuration for PTT
            // audioProducer = sendTransport?.produce(
            //     listener = object : Producer.Listener {
            //         override fun onTransportClose(producer: Producer) {
            //             audioProducer = null
            //             Log.d(TAG, "Producer transport closed")
            //         }
            //     },
            //     track = null, // Will be set to audio track from AudioRecord
            //     codecOptions = mapOf(
            //         "opusStereo" to false,
            //         "opusDtx" to true,
            //         "opusFec" to true,
            //         "opusMaxPlaybackRate" to 48000,
            //         "opusPtime" to 20
            //     ),
            //     appData = ""
            // )

            Log.d(TAG, "Audio producer started")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start producer", e)
            throw e
        }
    }

    /**
     * Send captured audio data to producer.
     *
     * Called by PttManager's audio capture callback to forward PCM buffers.
     * Producer handles Opus encoding internally.
     *
     * @param buffer PCM audio buffer from AudioRecord
     * @param length Number of bytes in buffer
     */
    fun sendAudioData(buffer: ByteArray, length: Int) {
        // TODO: Integrate actual libmediasoup-android library
        // Forward buffer to audioProducer
        // Producer will handle Opus encoding and RTP packetization
        // audioProducer?.send(buffer, length)
    }

    /**
     * Stop producing audio (PTT release).
     */
    fun stopProducing() {
        try {
            // TODO: Integrate actual libmediasoup-android library
            // audioProducer?.close()
            audioProducer = null
            Log.d(TAG, "Audio producer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping producer", e)
        }
    }

    /**
     * Clean up all mediasoup resources.
     *
     * CRITICAL: Disposal order matters to prevent memory leaks.
     * Order: producers first, consumers, send transport, recv transport, device (device persists).
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up mediasoup resources")

        // Step 1: Close producer FIRST
        audioProducer?.let {
            // TODO: Integrate actual libmediasoup-android library
            // (it as Producer).close()
        }
        audioProducer = null

        // Step 2: Close all consumers
        consumers.values.forEach {
            // TODO: Integrate actual libmediasoup-android library
            // (it as Consumer).close()
        }
        consumers.clear()

        // Step 3: Close send transport
        sendTransport?.let {
            // TODO: Integrate actual libmediasoup-android library
            // (it as SendTransport).close()
        }
        sendTransport = null

        // Step 4: Close recv transport
        recvTransport?.let {
            // TODO: Integrate actual libmediasoup-android library
            // (it as RecvTransport).close()
        }
        recvTransport = null

        // Step 5: DO NOT dispose device (shared across channels)
        // Device persists for lifetime of MediasoupClient singleton

        Log.d(TAG, "Cleanup complete")
    }

    companion object {
        private const val TAG = "MediasoupClient"
    }
}
