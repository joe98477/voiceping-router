package com.voiceping.android.data.network

import android.content.Context
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voiceping.android.data.audio.AudioRouter
import com.voiceping.android.data.network.dto.SignalingType
import com.voiceping.android.domain.model.ConsumerNetworkStats
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.crow_misia.mediasoup.Consumer
import io.github.crow_misia.mediasoup.Device
import io.github.crow_misia.mediasoup.Producer
import io.github.crow_misia.mediasoup.RecvTransport
import io.github.crow_misia.mediasoup.SendTransport
import io.github.crow_misia.mediasoup.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject
import javax.inject.Singleton

/** Safe accessor: returns null for both missing keys and JSON null values. */
private fun JsonElement?.asStringOrNull(): String? =
    if (this == null || this.isJsonNull) null else this.asString

/**
 * mediasoup Device wrapper for bidirectional audio (send + receive).
 *
 * Handles:
 * - Device creation and RTP capabilities loading
 * - Receive transport creation and audio consumption (multi-channel monitoring)
 * - Send transport creation and audio production (PTT transmission)
 *
 * Audio flow:
 * - Receive: RecvTransport per channel -> Consumer per producer -> AudioTrack playback
 * - Send: AudioSource captures mic -> AudioTrack -> Producer encodes Opus -> SendTransport -> server
 *
 * Key pattern: Device -> load capabilities -> create transports -> produce/consume
 */
@Singleton
class MediasoupClient @Inject constructor(
    private val signalingClient: SignalingClient,
    private val audioRouter: AudioRouter,
    @ApplicationContext private val context: Context
) {
    // WebRTC factory and audio module
    private lateinit var audioDeviceModule: JavaAudioDeviceModule
    private lateinit var peerConnectionFactory: PeerConnectionFactory

    // mediasoup Device (created after PeerConnectionFactory init)
    private lateinit var device: Device

    // Transport and producer/consumer placeholders (typed in Phase 12/13)
    private val recvTransports = mutableMapOf<String, RecvTransport>()
    private var sendTransport: SendTransport? = null
    private var sendTransportChannelId: String? = null
    private val consumers = mutableMapOf<String, Consumer>()
    private var audioProducer: Producer? = null
    private var audioSource: AudioSource? = null
    private var pttAudioTrack: org.webrtc.AudioTrack? = null

    // Flag to detect race condition: stopProducing() called while transport.produce() is in-flight.
    // transport.produce() blocks for ~700ms (onConnect + onProduce server roundtrip).
    // If user releases PTT before produce() returns, the late-arriving producer is orphaned.
    @Volatile
    private var producingRequested = false

    // Mutex for transport lifecycle protection (prevents concurrent creation/destruction)
    private val transportMutex = Mutex()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private fun toJsonString(data: com.google.gson.JsonElement?): String {
        return data?.toString() ?: throw IllegalStateException("Failed to serialize to JSON")
    }

    /**
     * Initialize PeerConnectionFactory with custom AudioDeviceModule.
     * MUST be called before Device.load() and any transport creation.
     *
     * Configures:
     * - Hardware acoustic echo cancellation (AEC) — prevents speaker audio feeding back into mic
     * - Hardware noise suppression (NS) — filters background noise for clear PTT transmission
     * - Error callbacks for AudioRecord/AudioTrack lifecycle monitoring
     *
     * After initialization, disables AudioRouter's MODE_IN_COMMUNICATION control
     * because WebRTC's AudioDeviceModule now owns that responsibility.
     */
    fun initializeWebRTC() {
        // Dispose previous Device to prevent native memory leak on re-initialization
        if (::device.isInitialized) {
            try { device.dispose() } catch (e: Exception) { Log.w(TAG, "Error disposing old device", e) }
        }

        audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String) {
                    Log.e(TAG, "AudioRecord init error: $errorMessage")
                }
                override fun onWebRtcAudioRecordStartError(
                    errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode,
                    errorMessage: String
                ) {
                    Log.e(TAG, "AudioRecord start error: $errorCode - $errorMessage")
                }
                override fun onWebRtcAudioRecordError(errorMessage: String) {
                    Log.e(TAG, "AudioRecord error: $errorMessage")
                }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(errorMessage: String) {
                    Log.e(TAG, "AudioTrack init error: $errorMessage")
                }
                override fun onWebRtcAudioTrackStartError(
                    errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode,
                    errorMessage: String
                ) {
                    Log.e(TAG, "AudioTrack start error: $errorCode - $errorMessage")
                }
                override fun onWebRtcAudioTrackError(errorMessage: String) {
                    Log.e(TAG, "AudioTrack error: $errorMessage")
                }
            })
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        // Create Device with PeerConnectionFactory
        device = Device(peerConnectionFactory)

        // Coordinate with AudioRouter: WebRTC now owns MODE_IN_COMMUNICATION
        audioRouter.disableModeControl()

        Log.d(TAG, "PeerConnectionFactory initialized with AEC and NS enabled")
    }

    /**
     * Initialize WebRTC subsystem (PeerConnectionFactory + Device creation).
     *
     * Does NOT load router capabilities — that requires a channelId and happens
     * lazily on first channel join via [loadDeviceCapabilities].
     *
     * @throws Exception if WebRTC initialization fails
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            initializeWebRTC()
            _isInitialized.value = true
            Log.d(TAG, "WebRTC initialized, Device created (capabilities not yet loaded)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
            throw e
        }
    }

    /**
     * Load router RTP capabilities into Device (idempotent — runs once).
     *
     * Uses native `device.loaded` property to check if already loaded,
     * preventing "already loaded" errors when native Device state diverges
     * from application state (e.g., after a failed load attempt).
     *
     * Server requires a channelId to look up the router for that channel.
     * Called before first transport creation.
     *
     * @param channelId Channel to fetch router capabilities for
     * @throws Exception if capabilities request fails or device load fails
     */
    suspend fun loadDeviceCapabilities(channelId: String) = withContext(Dispatchers.IO) {
        if (device.loaded) return@withContext

        Log.d(TAG, "Requesting router RTP capabilities for channel: $channelId")

        val capsResponse = signalingClient.request(
            SignalingType.GET_ROUTER_CAPABILITIES,
            mapOf("channelId" to channelId)
        )
        if (capsResponse.error != null) {
            throw IllegalStateException("Server error: ${capsResponse.error}")
        }
        val rtpCapabilities = toJsonString(capsResponse.data?.get("routerRtpCapabilities")
            ?: throw IllegalStateException("No routerRtpCapabilities in response (data keys: ${capsResponse.data?.keySet()})"))

        // Log capabilities for debugging native load failures
        Log.d(TAG, "Received RTP capabilities (${rtpCapabilities.length} chars), first 500: ${rtpCapabilities.take(500)}")

        // CRITICAL: Pass empty RTCConfiguration, not null. Null causes the native
        // PeerConnection to use device-default config which on some devices (Samsung)
        // generates SDP with extensions that crash mediasoup-client's extractRtpCapabilities
        // parser ([json.exception.type_error.305]). The demo app always passes an explicit config.
        //
        // Retry: SDP generation is non-deterministic (random ICE/DTLS params).
        // Some SDPs trigger a json parse error while others succeed. Retry up to 5 times.
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        var lastException: Exception? = null
        for (attempt in 1..10) {
            try {
                device.load(rtpCapabilities, rtcConfig)
                lastException = null
                break
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "device.load() attempt $attempt/10 failed: ${e.message}")
                if (attempt < 10) delay(200L * attempt)
            }
        }
        if (lastException != null) throw lastException!!

        // Validate Opus codec support
        val deviceCapsJson = device.rtpCapabilities
        val hasOpus = deviceCapsJson.contains("\"mimeType\":\"audio/opus\"", ignoreCase = true) ||
                      deviceCapsJson.contains("\"mimeType\": \"audio/opus\"", ignoreCase = true)

        if (!hasOpus) {
            throw IllegalStateException("Device does not support Opus codec — cannot proceed with audio")
        }

        Log.d(TAG, "Device loaded with Opus codec support validated")
    }

    /**
     * Get device RTP capabilities for server consume requests.
     * Server needs this to create consumers compatible with device's codecs.
     *
     * @return JSON string of device's RTP capabilities
     * @throws IllegalStateException if device not initialized
     */
    fun getRtpCapabilities(): String {
        if (!_isInitialized.value) {
            throw IllegalStateException("Device not initialized, call initialize() first")
        }
        return device.rtpCapabilities
    }

    /**
     * Ensure WebRTC subsystem is initialized (Device + PeerConnectionFactory exist).
     *
     * Safety guard for the rare case where createRecvTransport/createSendTransport
     * is called before LoadingViewModel's initialize(), or after a failed initialization.
     * In normal operation, initialize() runs once on app start and the device persists.
     */
    private fun ensureInitialized() {
        if (!_isInitialized.value || !::device.isInitialized) {
            Log.w(TAG, "MediasoupClient not initialized, initializing WebRTC now")
            initializeWebRTC()
            _isInitialized.value = true
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
        ensureInitialized()
        transportMutex.withLock {
            try {
                // Guard: Prevent duplicate RecvTransport creation for same channel
                if (recvTransports.containsKey(channelId)) {
                    Log.d(TAG, "RecvTransport already exists for channel: $channelId")
                    return@withContext
                }

                Log.d(TAG, "Creating receive transport for channel: $channelId")

                // Ensure Device has loaded router capabilities (idempotent)
                loadDeviceCapabilities(channelId)

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

                val transportId = transportData.get("id").asStringOrNull()
                    ?: throw IllegalStateException("No transport id")
                val iceParameters = toJsonString(transportData.get("iceParameters"))
                val iceCandidates = toJsonString(transportData.get("iceCandidates"))
                val dtlsParameters = toJsonString(transportData.get("dtlsParameters"))

                Log.d(TAG, "Transport parameters received: id=$transportId")

                // Step 2 & 3: Create RecvTransport with listener
                val transport = device.createRecvTransport(
                    listener = object : RecvTransport.Listener {
                        override fun onConnect(transport: Transport, dtlsParameters: String) {
                            Log.d(TAG, "RecvTransport onConnect: $transportId")
                            runBlocking {
                                // dtlsParameters is a JSON string from native —
                                // parse into JsonElement to avoid double-encoding
                                val connectData = JsonObject().apply {
                                    addProperty("transportId", transportId)
                                    add("dtlsParameters", JsonParser.parseString(dtlsParameters))
                                }
                                signalingClient.request(
                                    SignalingType.CONNECT_TRANSPORT,
                                    connectData
                                )
                            }
                        }

                        override fun onConnectionStateChange(
                            transport: Transport,
                            newState: String
                        ) {
                            when (newState) {
                                "disconnected" -> {
                                    // ICE connectivity lost — WebRTC will attempt auto-recovery (~15s window)
                                    // Do NOT remove transport, it may reconnect
                                    Log.w(TAG, "RecvTransport disconnected, waiting for auto-recovery (channel: $channelId)")
                                }
                                "failed" -> {
                                    // Auto-recovery failed — cleanup this channel's resources
                                    Log.e(TAG, "RecvTransport failed, cleaning up channel: $channelId")
                                    // Remove transport — Consumer.onTransportClose will clean up consumers
                                    recvTransports.remove(channelId)
                                }
                                "connected" -> {
                                    Log.d(TAG, "RecvTransport (re)connected: $channelId")
                                }
                            }
                        }
                    },
                    id = transportId,
                    iceParameters = iceParameters,
                    iceCandidates = iceCandidates,
                    dtlsParameters = dtlsParameters
                )
                recvTransports[channelId] = transport

                Log.d(TAG, "Receive transport created successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create receive transport", e)
                throw e
            }
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
     * @param channelId The channel ID for transport lookup
     * @param producerId The producer ID from server
     * @param peerId The peer ID producing audio
     * @return consumerId for tracking
     * @throws Exception if consume fails
     */
    suspend fun consumeAudio(channelId: String, producerId: String, peerId: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Consuming audio: channel=$channelId, producer=$producerId, peer=$peerId")

            // Step 1: Request consume from server
            // rtpCapabilities is a JSON string from native — parse into JsonElement
            // to avoid double-encoding as a string literal
            val consumeRequestData = JsonObject().apply {
                addProperty("channelId", channelId)
                addProperty("producerId", producerId)
                add("rtpCapabilities", JsonParser.parseString(device.rtpCapabilities))
            }
            val consumeResponse = signalingClient.request(
                SignalingType.CONSUME,
                consumeRequestData
            )

            val consumeData = consumeResponse.data
                ?: throw IllegalStateException("No consume data in response")

            val consumerId = consumeData.get("id").asStringOrNull()
                ?: throw IllegalStateException("No consumer id")
            val kind = consumeData.get("kind").asStringOrNull()
                ?: throw IllegalStateException("No kind")
            val rtpParameters = toJsonString(consumeData.get("rtpParameters"))

            val transport = recvTransports[channelId]
                ?: throw IllegalStateException("RecvTransport not found for channel: $channelId")

            val consumer = transport.consume(
                listener = object : Consumer.Listener {
                    override fun onTransportClose(consumer: Consumer) {
                        Log.d(TAG, "Consumer transport closed: $consumerId")
                        consumers.remove(consumerId)
                    }
                },
                id = consumerId,
                producerId = producerId,
                kind = kind,
                rtpParameters = rtpParameters
            )

            // CRITICAL: Resume consumer to start audio playback
            consumer.resume()

            consumers[consumerId] = consumer
            Log.d(TAG, "Consumer created and resumed: $consumerId")

            return@withContext consumerId

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
        consumers.remove(consumerId)?.let { consumer ->
            consumer.close()
            Log.d(TAG, "Consumer closed: $consumerId")
        }
    }

    /**
     * Set volume for a specific consumer (0.0 to 1.0).
     * Used for per-channel volume control and audio mix mode.
     */
    fun setConsumerVolume(consumerId: String, volume: Float) {
        consumers[consumerId]?.let { consumer ->
            val audioTrack = consumer.track as? AudioTrack
            if (audioTrack != null) {
                // Convert 0.0-1.0 app range to 0.0-10.0 WebRTC range
                val webRtcVolume = (volume.coerceIn(0f, 1f) * 10.0)
                audioTrack.setVolume(webRtcVolume)
                Log.d(TAG, "Consumer volume set: $consumerId -> $volume (WebRTC: $webRtcVolume)")
            } else {
                Log.w(TAG, "Consumer track is not AudioTrack: $consumerId")
            }
        } ?: Log.w(TAG, "Consumer not found for volume control: $consumerId")
    }

    /**
     * Get consumer statistics for network quality indicator.
     *
     * Parses RTCStatsReport from Consumer.stats to extract:
     * - packetsLost: Cumulative packets lost
     * - jitter: Packet arrival time variance
     * - packetsReceived: Total packets received
     *
     * Note: The actual implementation depends on crow-misia API. If consumer.stats
     * returns a String (JSON), we parse it. If it returns an RTCStatsReport object,
     * we iterate it. This stub returns default "Good" stats for compilation.
     *
     * @param consumerId Consumer ID to get stats for
     * @return ConsumerNetworkStats or null if consumer not found or stats unavailable
     */
    suspend fun getConsumerStats(consumerId: String): ConsumerNetworkStats? = withContext(Dispatchers.IO) {
        consumers[consumerId]?.let { consumer ->
            try {
                // TODO: Implement actual stats parsing when library API is confirmed
                // The crow-misia library's Consumer.stats property type is not documented.
                // Options:
                // 1. If it returns String (JSON like device.rtpCapabilities):
                //    val statsJson = consumer.stats as? String
                //    Parse JSON to extract inbound-rtp metrics
                // 2. If it returns RTCStatsReport object:
                //    Iterate through statsReport.getStatsIds() or similar
                // 3. If it's a method getStats() with callback:
                //    Wrap in suspendCancellableCoroutine
                //
                // For now, return default "Good" stats to allow compilation and UI wiring.
                // Will be updated after library API testing on device.

                return@withContext ConsumerNetworkStats(
                    packetsLost = 0,
                    jitter = 0.0,
                    packetsReceived = 100,
                    indicator = "Good"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get consumer stats: $consumerId", e)
                null
            }
        }
    }

    /**
     * Create send transport for PTT audio transmission (singleton).
     *
     * Steps:
     * 1. Request CREATE_TRANSPORT from server with channelId and direction="send"
     * 2. Create SendTransport with server's transport parameters
     * 3. Set up transport listener for DTLS connection and produce events
     *
     * @param channelId Channel to create transport for (server requires it)
     * @throws Exception if transport creation fails
     */
    suspend fun createSendTransport(channelId: String) = withContext(Dispatchers.IO) {
        ensureInitialized()
        transportMutex.withLock {
            try {
                // Guard: SendTransport is singleton (not per-channel)
                if (sendTransport != null) {
                    // Update channel so onProduce sends to the correct channel
                    sendTransportChannelId = channelId
                    Log.d(TAG, "SendTransport already exists, updated channel to $channelId")
                    return@withContext
                }

                Log.d(TAG, "Creating send transport for channel: $channelId")
                sendTransportChannelId = channelId

                // Step 1: Request transport from server
                val transportResponse = signalingClient.request(
                    SignalingType.CREATE_TRANSPORT,
                    mapOf(
                        "channelId" to channelId,
                        "direction" to "send"
                    )
                )

                val transportData = transportResponse.data
                    ?: throw IllegalStateException("No transport data in response")

                val transportId = transportData.get("id").asStringOrNull()
                    ?: throw IllegalStateException("No transport id")
                val iceParameters = toJsonString(transportData.get("iceParameters"))
                val iceCandidates = toJsonString(transportData.get("iceCandidates"))
                val dtlsParameters = toJsonString(transportData.get("dtlsParameters"))

                Log.d(TAG, "Send transport parameters received: id=$transportId")

                // Step 2 & 3: Create SendTransport with listener
                sendTransport = device.createSendTransport(
                    listener = object : SendTransport.Listener {
                        override fun onConnect(transport: Transport, dtlsParameters: String) {
                            Log.d(TAG, "SendTransport onConnect: $transportId")
                            runBlocking {
                                // dtlsParameters is a JSON string from native —
                                // parse into JsonElement to avoid double-encoding
                                val connectData = JsonObject().apply {
                                    addProperty("transportId", transportId)
                                    add("dtlsParameters", JsonParser.parseString(dtlsParameters))
                                }
                                signalingClient.request(
                                    SignalingType.CONNECT_TRANSPORT,
                                    connectData
                                )
                            }
                        }

                        override fun onProduce(
                            transport: Transport,
                            kind: String,
                            rtpParameters: String,
                            appData: String?
                        ): String {
                            Log.d(TAG, "SendTransport onProduce: kind=$kind, transport=$transportId, channel=$sendTransportChannelId")
                            return runBlocking {
                                // rtpParameters is a JSON string from native —
                                // parse into JsonElement to avoid double-encoding.
                                // Server requires transportId and channelId.
                                val produceData = JsonObject().apply {
                                    addProperty("transportId", transportId)
                                    addProperty("channelId", sendTransportChannelId)
                                    addProperty("kind", kind)
                                    add("rtpParameters", JsonParser.parseString(rtpParameters))
                                }
                                val produceResponse = signalingClient.request(
                                    SignalingType.PRODUCE,
                                    produceData
                                )
                                if (produceResponse.error != null) {
                                    throw IllegalStateException("Produce failed: ${produceResponse.error}")
                                }
                                produceResponse.data?.get("id").asStringOrNull()
                                    ?: throw IllegalStateException("No producer id in response")
                            }
                        }

                        override fun onProduceData(
                            transport: Transport,
                            sctpStreamParameters: String,
                            label: String,
                            protocol: String,
                            appData: String?
                        ): String {
                            // Not used for audio-only app
                            throw UnsupportedOperationException("Data channels not supported")
                        }

                        override fun onConnectionStateChange(
                            transport: Transport,
                            newState: String
                        ) {
                            when (newState) {
                                "disconnected" -> {
                                    // ICE connectivity lost — WebRTC will attempt auto-recovery (~15s window)
                                    // Do NOT cleanup resources yet, transport may reconnect
                                    Log.w(TAG, "SendTransport disconnected, waiting for auto-recovery")
                                }
                                "failed" -> {
                                    // Auto-recovery failed — manual cleanup required
                                    // Close producer and audio resources, null the transport
                                    // Next PTT press will recreate SendTransport
                                    Log.e(TAG, "SendTransport failed, cleaning up producer and transport")
                                    audioProducer?.close()
                                    audioProducer = null
                                    cleanupAudioResources()
                                    sendTransport = null
                                }
                                "connected" -> {
                                    Log.d(TAG, "SendTransport (re)connected")
                                }
                            }
                        }
                    },
                    id = transportId,
                    iceParameters = iceParameters,
                    iceCandidates = iceCandidates,
                    dtlsParameters = dtlsParameters
                )

                Log.d(TAG, "Send transport created successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to create send transport", e)
                throw e
            }
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
     * Creates WebRTC AudioSource and AudioTrack for microphone capture,
     * then produces via SendTransport.
     *
     * @throws Exception if producer creation fails
     */
    suspend fun startProducing() = withContext(Dispatchers.IO) {
        try {
            // Close any stale/orphaned producer from a previous race condition
            // (stopProducing ran before produce() returned, leaving a dangling producer)
            audioProducer?.let { staleProducer ->
                Log.w(TAG, "Closing stale producer before creating new one")
                staleProducer.close()
                audioProducer = null
                cleanupAudioResources()
            }

            Log.d(TAG, "Starting audio producer")
            producingRequested = true

            // Guard: SendTransport must exist
            val transport = sendTransport
                ?: throw IllegalStateException("SendTransport not created")

            // Create AudioSource for microphone capture
            val source = peerConnectionFactory.createAudioSource(MediaConstraints())
            audioSource = source

            // Create AudioTrack from AudioSource
            val track = peerConnectionFactory.createAudioTrack("audio-ptt", source)
            pttAudioTrack = track

            // Opus codec configuration for PTT
            val codecOptions = com.google.gson.JsonObject().apply {
                addProperty("opusStereo", false)
                addProperty("opusDtx", true)
                addProperty("opusFec", true)
                addProperty("opusMaxPlaybackRate", 48000)
                addProperty("opusPtime", 20)
            }

            // Create Producer — this BLOCKS for ~700ms (onConnect + onProduce server roundtrip).
            // During this time, user may release PTT causing stopProducing() to run.
            val producer = transport.produce(
                listener = object : Producer.Listener {
                    override fun onTransportClose(producer: Producer) {
                        audioProducer = null
                        cleanupAudioResources()
                        Log.d(TAG, "Producer transport closed")
                    }
                },
                track = track,
                encodings = emptyList(),
                codecOptions = toJsonString(codecOptions),
                codec = null,
                appData = null
            )

            // CRITICAL: Check if stopProducing() was called while produce() was blocking.
            // If so, the audio resources have been disposed and this producer is orphaned.
            if (!producingRequested) {
                Log.w(TAG, "stopProducing() called during produce(), closing orphaned producer")
                producer.close()
                return@withContext
            }

            audioProducer = producer
            Log.d(TAG, "Audio producer started")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start producer", e)
            producingRequested = false
            cleanupAudioResources()
            throw e
        }
    }

    /**
     * Stop producing audio (PTT release).
     *
     * Closes Producer and disposes native WebRTC resources in correct order.
     */
    fun stopProducing() {
        try {
            Log.d(TAG, "Stopping audio producer")

            // Signal to in-flight produce() that we want to stop
            producingRequested = false

            // Close producer (may be null if produce() hasn't returned yet —
            // the producingRequested flag handles that case)
            audioProducer?.close()
            audioProducer = null

            // Cleanup native resources
            cleanupAudioResources()

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping producer", e)
            // Ensure cleanup happens even on error
            producingRequested = false
            cleanupAudioResources()
        }
    }

    /**
     * Clean up audio resources (AudioTrack and AudioSource).
     *
     * CRITICAL: Disposal order matters for native memory.
     * AudioTrack must be disposed before AudioSource.
     */
    private fun cleanupAudioResources() {
        pttAudioTrack?.dispose()
        pttAudioTrack = null

        audioSource?.dispose()
        audioSource = null

        Log.d(TAG, "Audio resources cleaned up")
    }

    /**
     * Clean up resources for a specific channel.
     *
     * Called when leaving a channel to close its RecvTransport.
     * Consumers should be closed by caller first via closeConsumer().
     *
     * Mutex-protected to prevent race conditions with concurrent transport creation/destruction.
     *
     * @param channelId The channel to clean up
     */
    suspend fun cleanupChannel(channelId: String) {
        transportMutex.withLock {
            Log.d(TAG, "Cleaning up channel: $channelId")

            // Close RecvTransport for channel
            // Note: Consumers for this channel should already be closed by caller
            // (ChannelRepository closes consumers before calling this method)
            recvTransports.remove(channelId)?.let { transport ->
                transport.close()
                Log.d(TAG, "RecvTransport closed for channel: $channelId")
            }
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
        producingRequested = false
        audioProducer?.close()
        audioProducer = null
        cleanupAudioResources()

        // Step 2: Close all consumers
        consumers.values.forEach { it.close() }
        consumers.clear()

        // Step 3: Close send transport
        sendTransport?.close()
        sendTransport = null
        sendTransportChannelId = null

        // Step 4: Close all recv transports
        recvTransports.values.forEach { it.close() }
        recvTransports.clear()

        // Note: Device and PeerConnectionFactory are kept alive across cleanup cycles.
        // They're expensive to recreate, and initialize() is only called once in LoadingViewModel.
        // Device capabilities (loaded state) remain valid across transport teardown/recreation.

        Log.d(TAG, "Cleanup complete")
    }

    companion object {
        private const val TAG = "MediasoupClient"
    }
}
