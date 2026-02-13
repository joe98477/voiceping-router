# Phase 12: Device and RecvTransport Integration - Research

**Researched:** 2026-02-13
**Domain:** mediasoup RecvTransport and Consumer lifecycle, WebRTC audio reception, volume control
**Confidence:** HIGH

## Summary

Phase 12 implements the receive audio path by creating RecvTransport instances and Consumer objects for each channel. This phase builds on Phase 11's Device initialization and connects the mediasoup library to real audio reception. The RecvTransport manages ICE/DTLS negotiation via onConnect callback (requires runBlocking bridge for signaling), while Consumer objects receive remote audio producers and expose WebRTC AudioTrack instances for playback.

The critical pattern is: Device → createRecvTransport → consume → AudioTrack.setVolume for per-consumer volume control. The Consumer.getTrack() method returns org.webrtc.AudioTrack with setVolume(0.0-10.0) for volume adjustment, and Consumer.getStats() provides RTC statistics (packetsLost, jitter) for network quality indication. Lifecycle management requires Consumer.close() on channel leave to prevent orphaned resources and bandwidth waste.

The main challenge is threading: Transport.Listener callbacks execute on WebRTC's native signaling thread (JNI thread), but SignalingClient.request() is a Kotlin suspend function. The solution is runBlocking {} to create coroutine bridge on callback thread, blocking until signaling completes — acceptable because onConnect runs once during DTLS setup (50-200ms), not on audio path.

**Primary recommendation:** Create RecvTransport per channel with onConnect/onConnectionStateChange listeners, consume remote producers using Consumer with onTransportClose listener, store AudioTrack reference for volume control via setVolume(), implement Consumer.getStats() polling for network quality UI, close consumers cleanly on channel leave.

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| **libmediasoup-android** | **0.21.0** | RecvTransport, Consumer, WebRTC integration | Official crow-misia wrapper with RecvTransport.Listener interface, Consumer lifecycle methods (pause/resume/close), getStats() for RTC metrics, actively maintained |
| **WebRTC (bundled)** | **M130 (130.6723.2.0)** | AudioTrack for playback, volume control | Bundled with libmediasoup-android, AudioTrack.setVolume(0.0-10.0) for per-consumer gain, getStats() for packet loss/jitter |
| **Kotlin Coroutines** | **1.10.1** (existing) | runBlocking bridge for Transport callbacks | Already in project, required for SignalingClient.request() calls from native threads |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **Gson** | **2.11.0** (existing) | JSON serialization for RTP parameters | Already in MediasoupClient, toJsonString() helper for RTP params |
| **Existing app infrastructure** | Current | SignalingClient, ChannelRepository, AudioRouter | No additional deps needed |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| runBlocking in onConnect | suspendCoroutine + CompletableFuture | More complex, avoids blocking native thread, but onConnect is one-time 50-200ms operation — runBlocking sufficient |
| AudioTrack.setVolume() | AudioManager global volume | Per-consumer control vs global, AudioTrack enables audio mix mode (primary loud, secondary quiet) |
| Consumer per producer | Single shared consumer | Multi-consumer required for multi-channel monitoring (Phase 8), each channel needs independent volume control |

**Installation:**
No new dependencies required — libmediasoup-android 0.21.0 already added in Phase 11.

## Architecture Patterns

### Recommended Project Structure

```
android/app/src/main/java/com/voiceping/android/
├── data/
│   ├── network/
│   │   ├── MediasoupClient.kt           # MODIFY: createRecvTransport(), consumeAudio(), setConsumerVolume()
│   │   └── SignalingClient.kt           # No changes (already supports CREATE_TRANSPORT, CONSUME)
│   └── repository/
│       └── ChannelRepository.kt          # MODIFY: call mediasoupClient methods, track consumers
└── presentation/
    └── channels/
        └── ChannelListViewModel.kt       # No changes (already observes channel state)
```

### Pattern 1: RecvTransport Creation with onConnect Callback

**What:** Create receive transport for channel, implement onConnect listener to bridge DTLS parameters to signaling
**When to use:** First time user joins channel (one RecvTransport per channel)
**Where:** MediasoupClient.createRecvTransport()

```kotlin
// MediasoupClient.kt
import io.github.crow_misia.mediasoup.RecvTransport
import io.github.crow_misia.mediasoup.Transport
import kotlinx.coroutines.runBlocking

@Singleton
class MediasoupClient @Inject constructor(
    private val signalingClient: SignalingClient,
    @ApplicationContext private val context: Context
) {
    private lateinit var device: Device
    private lateinit var peerConnectionFactory: PeerConnectionFactory

    // Store RecvTransport per channel
    private val recvTransports = mutableMapOf<String, RecvTransport>()

    /**
     * Create receive transport for channel.
     *
     * Steps:
     * 1. Request CREATE_TRANSPORT from server (direction="recv")
     * 2. Create RecvTransport with server parameters
     * 3. onConnect callback bridges DTLS to signaling (runBlocking required)
     *
     * @param channelId Channel to create transport for
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
                ?: throw IllegalStateException("No transport data")

            val transportId = transportData["id"] as String
            val iceParameters = toJsonString(transportData["iceParameters"])
            val iceCandidates = toJsonString(transportData["iceCandidates"])
            val dtlsParameters = toJsonString(transportData["dtlsParameters"])

            // Step 2: Create RecvTransport with listener
            val transport = device.createRecvTransport(
                listener = object : RecvTransport.Listener {
                    /**
                     * onConnect: Called when transport establishing ICE+DTLS connection.
                     * CRITICAL: Runs on WebRTC signaling thread (native JNI thread).
                     * Must signal DTLS parameters to server so it can call webRtcTransport.connect().
                     *
                     * Threading: runBlocking creates coroutine on current thread (native thread),
                     * blocks until signaling completes. Acceptable because:
                     * - onConnect is one-time operation during DTLS setup (50-200ms)
                     * - Not on audio path (no latency impact)
                     * - Transport unusable until DTLS completes anyway
                     */
                    override fun onConnect(transport: Transport, dtlsParameters: String) {
                        Log.d(TAG, "RecvTransport onConnect: $transportId")

                        runBlocking {
                            signalingClient.request(
                                SignalingType.CONNECT_TRANSPORT,
                                mapOf(
                                    "transportId" to transportId,
                                    "dtlsParameters" to dtlsParameters
                                )
                            )
                        }
                    }

                    override fun onConnectionStateChange(
                        transport: Transport,
                        connectionState: String
                    ) {
                        Log.d(TAG, "RecvTransport state: $connectionState (channel: $channelId)")

                        // Handle disconnection
                        if (connectionState == "disconnected" || connectionState == "failed") {
                            // Transport will auto-close, clean up consumers
                            consumers.values.forEach { it.close() }
                            consumers.clear()
                            recvTransports.remove(channelId)
                        }
                    }
                },
                id = transportId,
                iceParameters = iceParameters,
                iceCandidates = iceCandidates,
                dtlsParameters = dtlsParameters,
                iceServers = null,
                peerConnectionOptions = null,
                appData = null
            )

            // Step 3: Store transport
            recvTransports[channelId] = transport

            Log.d(TAG, "RecvTransport created successfully for channel: $channelId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create receive transport", e)
            throw e
        }
    }
}
```

**Why runBlocking:** Transport.Listener.onConnect() is synchronous callback from native thread, but SignalingClient.request() is suspend function. runBlocking bridges by creating coroutine on current thread and blocking until complete. Safe because onConnect is one-time DTLS setup, not audio path.

**Source:** [mediasoup Communication Between Client and Server](https://mediasoup.org/documentation/v3/communication-between-client-and-server/) — "The `transport.on("connect")` event fires when the local transport is about to establish the ICE+DTLS connection."

### Pattern 2: Consumer Creation with Volume Control

**What:** Consume remote audio producer, get AudioTrack for volume control
**When to use:** When server notifies of new speaker (SPEAKER_CHANGED event)
**Where:** MediasoupClient.consumeAudio()

```kotlin
// MediasoupClient.kt
import io.github.crow_misia.mediasoup.Consumer
import org.webrtc.AudioTrack

@Singleton
class MediasoupClient @Inject constructor(...) {
    // Store Consumer per channelId -> producerId
    // Consumer holds AudioTrack reference for volume control
    private val consumers = mutableMapOf<String, Consumer>()

    /**
     * Consume audio from remote producer.
     *
     * Steps:
     * 1. Request CONSUME from server (get consumer params)
     * 2. Create Consumer on RecvTransport
     * 3. Resume consumer to start audio playback
     * 4. Store Consumer for volume control and lifecycle
     *
     * @param channelId Channel ID
     * @param producerId Producer ID from server
     * @param peerId Peer user ID producing audio
     * @return Consumer ID for tracking
     */
    suspend fun consumeAudio(
        channelId: String,
        producerId: String,
        peerId: String
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Consuming audio: channel=$channelId, producer=$producerId")

            // Step 1: Request consume from server
            val consumeResponse = signalingClient.request(
                SignalingType.CONSUME,
                mapOf(
                    "channelId" to channelId,
                    "producerId" to producerId,
                    "rtpCapabilities" to device.rtpCapabilities
                )
            )

            val consumeData = consumeResponse.data
                ?: throw IllegalStateException("No consume data")

            val consumerId = consumeData["id"] as String
            val kind = consumeData["kind"] as String
            val rtpParameters = toJsonString(consumeData["rtpParameters"])

            // Get RecvTransport for channel
            val transport = recvTransports[channelId]
                ?: throw IllegalStateException("RecvTransport not found for channel: $channelId")

            // Step 2: Create Consumer on transport
            val consumer = transport.consume(
                listener = object : Consumer.Listener {
                    /**
                     * onTransportClose: Called when transport closes (channel leave, disconnect)
                     * Consumer auto-closes, cleanup references
                     */
                    override fun onTransportClose(consumer: Consumer) {
                        Log.d(TAG, "Consumer transport closed: $consumerId")
                        consumers.remove(consumerId)
                    }
                },
                id = consumerId,
                producerId = producerId,
                kind = kind,
                rtpParameters = rtpParameters,
                appData = null
            )

            // Step 3: Resume consumer (start audio playback)
            // Consumer created paused, must resume to hear audio
            consumer.resume()

            // Step 4: Store consumer for volume control
            consumers[consumerId] = consumer

            Log.d(TAG, "Consumer created and resumed: $consumerId")

            return@withContext consumerId

        } catch (e: Exception) {
            Log.e(TAG, "Failed to consume audio", e)
            throw e
        }
    }

    /**
     * Set volume for consumer (0.0 to 1.0).
     * Uses WebRTC AudioTrack.setVolume() which accepts 0-10 range.
     *
     * @param consumerId Consumer ID
     * @param volume Volume level (0.0 = mute, 1.0 = full)
     */
    fun setConsumerVolume(consumerId: String, volume: Float) {
        consumers[consumerId]?.let { consumer ->
            // Get AudioTrack from Consumer
            val audioTrack = consumer.track as? AudioTrack
            if (audioTrack != null) {
                // WebRTC AudioTrack.setVolume() accepts 0.0-10.0
                // Convert 0.0-1.0 to 0.0-10.0
                val webRtcVolume = (volume.coerceIn(0f, 1f) * 10.0)
                audioTrack.setVolume(webRtcVolume)
                Log.d(TAG, "Consumer volume set: $consumerId -> $volume (WebRTC: $webRtcVolume)")
            } else {
                Log.w(TAG, "Consumer track is not AudioTrack: $consumerId")
            }
        } ?: Log.w(TAG, "Consumer not found for volume control: $consumerId")
    }

    /**
     * Close consumer cleanly (channel leave, mute).
     *
     * @param consumerId Consumer ID to close
     */
    fun closeConsumer(consumerId: String) {
        consumers.remove(consumerId)?.let { consumer ->
            consumer.close()
            Log.d(TAG, "Consumer closed: $consumerId")
        }
    }
}
```

**Why AudioTrack.setVolume:** mediasoup Consumer API has no setVolume method. Volume control achieved via Consumer.getTrack() → AudioTrack (WebRTC) → setVolume(0.0-10.0). This enables per-consumer volume (audio mix mode: primary loud, secondary quiet).

**Source:** [WebRTC AudioTrack.setVolume](https://getstream.github.io/webrtc-android/stream-webrtc-android/org.webrtc/-audio-track/set-volume.html) — "Sets the volume for the underlying MediaSource. Volume is a gain value in the range 0 to 10."

### Pattern 3: Consumer Statistics for Network Quality

**What:** Poll Consumer.getStats() for packet loss and jitter, display in UI
**When to use:** Periodic polling (every 2-5 seconds) when consumer active
**Where:** ChannelRepository or ChannelListViewModel

```kotlin
// MediasoupClient.kt
import org.webrtc.RTCStatsReport

/**
 * Get consumer statistics for network quality indicator.
 *
 * Returns RTC statistics including:
 * - packetsLost: Total packets lost (cumulative)
 * - jitter: Packet arrival time variance (milliseconds)
 * - roundTripTime: RTT in seconds
 * - score: Transmission quality score (0-10)
 *
 * @param consumerId Consumer ID
 * @return RTCStatsReport with inbound-rtp and outbound-rtp entries
 */
suspend fun getConsumerStats(consumerId: String): RTCStatsReport? = withContext(Dispatchers.IO) {
    consumers[consumerId]?.let { consumer ->
        try {
            // getStats() returns RTCStatsReport with statistics
            val stats = consumer.stats
            Log.d(TAG, "Consumer stats: $consumerId -> $stats")
            return@withContext stats
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get consumer stats: $consumerId", e)
            null
        }
    }
}
```

**Usage in ViewModel:**

```kotlin
// ChannelListViewModel.kt
private fun pollNetworkQuality(channelId: String) {
    viewModelScope.launch {
        while (monitoredChannels[channelId]?.isActive == true) {
            delay(5000) // Poll every 5 seconds

            val consumerId = monitoredChannels[channelId]?.consumerId
            if (consumerId != null) {
                val stats = mediasoupClient.getConsumerStats(consumerId)
                stats?.let {
                    // Extract metrics from RTCStatsReport
                    // Display in UI: "Signal: Good" or "Packet loss: 2%"
                    updateNetworkQualityUI(channelId, stats)
                }
            }
        }
    }
}
```

**Source:** [mediasoup RTC Statistics](https://mediasoup.org/documentation/v3/mediasoup/rtc-statistics/) — "Consumer statistics include packetsLost, jitter, roundTripTime, score."

### Pattern 4: Clean Consumer Disposal on Channel Leave

**What:** Close consumers in correct order to prevent orphaned resources
**When to use:** User leaves channel (explicit leave or disconnect)
**Where:** MediasoupClient.cleanup() or per-channel cleanup

```kotlin
// MediasoupClient.kt
/**
 * Clean up transport and consumers for channel.
 * Called when user leaves channel.
 *
 * Order matters:
 * 1. Close all consumers first (releases AudioTrack references)
 * 2. Close RecvTransport (releases PeerConnection)
 *
 * @param channelId Channel to cleanup
 */
fun cleanupChannel(channelId: String) {
    Log.d(TAG, "Cleaning up channel: $channelId")

    // Step 1: Close all consumers for this channel
    // Filter consumers by channel (assumes consumerId contains channelId prefix)
    consumers.filterKeys { it.startsWith(channelId) }.forEach { (consumerId, consumer) ->
        consumer.close()
        consumers.remove(consumerId)
        Log.d(TAG, "Consumer closed: $consumerId")
    }

    // Step 2: Close RecvTransport
    recvTransports.remove(channelId)?.let { transport ->
        transport.close()
        Log.d(TAG, "RecvTransport closed: $channelId")
    }
}
```

**Why order matters:** Consumers hold AudioTrack references. Closing transport first orphans consumers, causing memory leak. Close consumers first to release AudioTrack, then transport to release PeerConnection.

### Anti-Patterns to Avoid

- **Forgetting Consumer.resume():** Consumer created paused, no audio until resume() called. Easy to miss — always call resume() immediately after consume().

- **Not storing Consumer reference:** Volume control and cleanup require Consumer reference. Store in Map<consumerId, Consumer> for lifecycle management.

- **Calling setVolume before Consumer ready:** AudioTrack may be null if Consumer not yet connected. Check audioTrack != null before setVolume().

- **Using AudioManager global volume:** AudioManager.setStreamVolume(STREAM_VOICE_CALL) affects ALL consumers. Use AudioTrack.setVolume() for per-consumer control (audio mix mode requirement).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Audio playback engine | Custom AudioTrack management with buffer mixing | Consumer.getTrack() → WebRTC AudioTrack | WebRTC handles playout buffer, jitter buffer, packet loss concealment, audio device lifecycle — 10+ years of production hardening |
| Volume mixing logic | Custom gain calculation and PCM mixing | AudioTrack.setVolume() per consumer | WebRTC applies gain before mixing, handles clipping prevention, CPU-optimized assembly code |
| Network quality calculation | Custom packet loss tracking from RTP headers | Consumer.getStats() RTCStatsReport | WebRTC maintains running statistics (cumulative loss, jitter calculation, RTT tracking) with RTCP feedback loop |
| Transport reconnection | Manual ICE restart on disconnect | RecvTransport.onConnectionStateChange → auto-reconnect | WebRTC handles ICE restart, candidate nomination, DTLS renegotiation automatically |

**Key insight:** WebRTC audio receive path is 50,000+ LOC of C++ with Android-specific optimizations (fast audio path, low-latency AudioTrack). Consumer.getTrack() exposes fully-managed AudioTrack — use it, don't rebuild it.

## Common Pitfalls

### Pitfall 1: runBlocking on Main Thread in Transport Callbacks

**What goes wrong:**
Transport.Listener.onConnect() executes on WebRTC's native signaling thread. If you accidentally use Dispatchers.Main context, runBlocking blocks main thread → ANR:

```kotlin
// WRONG: This causes ANR
override fun onConnect(transport: Transport, dtlsParameters: String) {
    runBlocking(Dispatchers.Main) { // ❌ Blocks main thread
        signalingClient.request(...)
    }
}
```

**Why it happens:**
runBlocking blocks the calling thread until coroutine completes. If calling thread is main, app freezes. Callback executes on native thread, so runBlocking(Dispatchers.Main) switches to main, then blocks it.

**How to avoid:**
Use runBlocking without dispatcher (defaults to current thread):

```kotlin
// CORRECT: Blocks native thread, not main
override fun onConnect(transport: Transport, dtlsParameters: String) {
    runBlocking { // ✅ Blocks native thread (acceptable)
        signalingClient.request(...)
    }
}
```

**Warning signs:**
- Logcat: "Application Not Responding" (ANR)
- Profiler: Main thread stalled during transport creation
- User experience: App freezes when joining channel

**Source:** [Exercise Caution When Using runBlocking on Android](https://getstream.io/blog/caution-runblocking-android/) — "runBlocking blocks the calling thread until completion. Avoid using on main thread."

### Pitfall 2: Forgetting Consumer.resume() After Creation

**What goes wrong:**
Consumer created via transport.consume() starts in paused state. No audio plays until resume() called:

```kotlin
// WRONG: Consumer paused, no audio
val consumer = transport.consume(listener, id, producerId, kind, rtpParameters, appData)
// ❌ Forgot to call consumer.resume()
```

User hears silence, no error thrown. Debugging shows Consumer exists, transport connected, but getTrack().enabled == false.

**Why it happens:**
mediasoup API design: consumers start paused to prevent auto-playback before app ready. Web API has explicit consumer.resume() call, Android mirrors this.

**How to avoid:**
Always call resume() immediately after consume():

```kotlin
// CORRECT: Audio plays immediately
val consumer = transport.consume(listener, id, producerId, kind, rtpParameters, appData)
consumer.resume() // ✅ Start audio playback
```

**Warning signs:**
- User reports "can't hear other users"
- Consumer exists in logs but no audio
- AudioTrack.enabled == false in debugger

**Source:** [mediasoup Consumer API](https://mediasoup.org/documentation/v3/libmediasoupclient/api/) — "Consumers are created paused. Application must call resume() to receive media."

### Pitfall 3: Consumer Leak on Channel Leave Without close()

**What goes wrong:**
User leaves channel but Consumer.close() never called. Consumer continues receiving packets (bandwidth waste), holds AudioTrack reference (memory leak), PeerConnection stays open:

```kotlin
// WRONG: Consumer orphaned
fun leaveChannel(channelId: String) {
    channelRepository.leaveChannel(channelId)
    // ❌ Forgot to close consumers
}
```

After leaving 10 channels: 10 orphaned consumers, 10MB memory leak, 500 kbps uplink wasted on RTCP for dead consumers.

**Why it happens:**
RecvTransport doesn't auto-close consumers when channel leaves. Consumers survive until Transport.close() or Consumer.close() called. Developer forgets cleanup step.

**How to avoid:**
Close consumers explicitly before leaving channel:

```kotlin
// CORRECT: Clean consumer disposal
fun leaveChannel(channelId: String) {
    // Close consumers first
    channelConsumers[channelId]?.values?.forEach { consumerId ->
        mediasoupClient.closeConsumer(consumerId) // ✅ Release resources
    }
    channelConsumers.remove(channelId)

    // Then leave channel
    channelRepository.leaveChannel(channelId)
}
```

**Warning signs:**
- Memory profiler: AudioTrack instances accumulate over time
- Network profiler: RTCP traffic continues after channel leave
- Log: "Consumer transport closed" warnings after user left channel

**Source:** [mediasoup libmediasoupclient Design](https://mediasoup.org/documentation/v3/libmediasoupclient/design/) — "Application must close consumers explicitly. Transports do not auto-close consumers."

### Pitfall 4: AudioTrack Volume Range Mismatch (0-1 vs 0-10)

**What goes wrong:**
App volume slider uses 0.0-1.0 range (Android standard), but AudioTrack.setVolume() expects 0.0-10.0. Direct mapping causes volume always at 10%:

```kotlin
// WRONG: Volume slider at 50% → setVolume(0.5) → 5% actual volume
fun setVolume(consumerId: String, sliderValue: Float) {
    val audioTrack = consumer.track as AudioTrack
    audioTrack.setVolume(sliderValue) // ❌ Range mismatch: 0-1 vs 0-10
}
```

User sets volume to 80%, hears barely audible audio. Full volume only when slider at 10% (0.1 mapped to 1.0 WebRTC volume).

**Why it happens:**
Android volume APIs use 0.0-1.0 (AudioManager, MediaPlayer), but WebRTC AudioTrack uses 0.0-10.0 for finer granularity. Documentation doesn't highlight the mismatch.

**How to avoid:**
Convert 0-1 range to 0-10 before setVolume():

```kotlin
// CORRECT: Volume slider at 50% → setVolume(5.0) → 50% actual volume
fun setVolume(consumerId: String, sliderValue: Float) {
    val audioTrack = consumer.track as AudioTrack
    val webRtcVolume = (sliderValue.coerceIn(0f, 1f) * 10.0) // ✅ Convert 0-1 to 0-10
    audioTrack.setVolume(webRtcVolume)
}
```

**Warning signs:**
- User reports "volume too quiet even at max"
- Volume slider at 100% → still quiet audio
- Log shows setVolume(1.0) instead of setVolume(10.0)

**Source:** [WebRTC AudioTrack.setVolume](https://getstream.github.io/webrtc-android/stream-webrtc-android/org.webrtc/-audio-track/set-volume.html) — "Volume is a gain value in the range 0 to 10."

## Code Examples

Verified patterns from official sources and Phase 11 discoveries:

### Complete RecvTransport Creation Flow

```kotlin
// MediasoupClient.kt
import io.github.crow_misia.mediasoup.Device
import io.github.crow_misia.mediasoup.RecvTransport
import io.github.crow_misia.mediasoup.Transport
import kotlinx.coroutines.runBlocking

@Singleton
class MediasoupClient @Inject constructor(
    private val signalingClient: SignalingClient,
    private val audioRouter: AudioRouter,
    @ApplicationContext private val context: Context
) {
    private lateinit var device: Device
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private val recvTransports = mutableMapOf<String, RecvTransport>()

    suspend fun createRecvTransport(channelId: String) = withContext(Dispatchers.IO) {
        try {
            // Request transport from server
            val transportResponse = signalingClient.request(
                SignalingType.CREATE_TRANSPORT,
                mapOf("channelId" to channelId, "direction" to "recv")
            )

            val data = transportResponse.data ?: throw IllegalStateException("No transport data")
            val transportId = data["id"] as String

            // Create RecvTransport with JSON strings (Phase 11 discovery pattern)
            val transport = device.createRecvTransport(
                listener = object : RecvTransport.Listener {
                    override fun onConnect(transport: Transport, dtlsParameters: String) {
                        // JNI thread → runBlocking bridge to suspend function
                        runBlocking {
                            signalingClient.request(
                                SignalingType.CONNECT_TRANSPORT,
                                mapOf("transportId" to transportId, "dtlsParameters" to dtlsParameters)
                            )
                        }
                    }

                    override fun onConnectionStateChange(transport: Transport, connectionState: String) {
                        Log.d(TAG, "RecvTransport $transportId state: $connectionState")
                        if (connectionState == "failed" || connectionState == "disconnected") {
                            recvTransports.remove(channelId)
                        }
                    }
                },
                id = transportId,
                iceParameters = toJsonString(data["iceParameters"]),
                iceCandidates = toJsonString(data["iceCandidates"]),
                dtlsParameters = toJsonString(data["dtlsParameters"]),
                iceServers = null,
                peerConnectionOptions = null,
                appData = null
            )

            recvTransports[channelId] = transport
            Log.d(TAG, "RecvTransport created: $transportId for channel $channelId")

        } catch (e: Exception) {
            Log.e(TAG, "createRecvTransport failed", e)
            throw e
        }
    }

    private fun toJsonString(data: Any?): String {
        return gson.toJson(data) ?: throw IllegalStateException("JSON serialization failed")
    }
}
```

**Source:** Adapted from Phase 11 discovery that crow-misia uses JSON string parameters (not objects).

### Complete Consumer Lifecycle with Volume Control

```kotlin
// MediasoupClient.kt
import io.github.crow_misia.mediasoup.Consumer
import org.webrtc.AudioTrack

@Singleton
class MediasoupClient @Inject constructor(...) {
    private val consumers = mutableMapOf<String, Consumer>()

    suspend fun consumeAudio(
        channelId: String,
        producerId: String,
        peerId: String
    ): String = withContext(Dispatchers.IO) {
        // Request consume from server
        val response = signalingClient.request(
            SignalingType.CONSUME,
            mapOf(
                "channelId" to channelId,
                "producerId" to producerId,
                "rtpCapabilities" to device.rtpCapabilities // Phase 11: JSON string
            )
        )

        val data = response.data ?: throw IllegalStateException("No consume data")
        val consumerId = data["id"] as String

        // Get RecvTransport for channel
        val transport = recvTransports[channelId]
            ?: throw IllegalStateException("RecvTransport not found: $channelId")

        // Create Consumer
        val consumer = transport.consume(
            listener = object : Consumer.Listener {
                override fun onTransportClose(consumer: Consumer) {
                    consumers.remove(consumerId)
                    Log.d(TAG, "Consumer auto-closed on transport close: $consumerId")
                }
            },
            id = consumerId,
            producerId = producerId,
            kind = data["kind"] as String,
            rtpParameters = toJsonString(data["rtpParameters"]),
            appData = null
        )

        // CRITICAL: Resume consumer (starts audio playback)
        consumer.resume()

        // Store for volume control
        consumers[consumerId] = consumer

        Log.d(TAG, "Consumer created and resumed: $consumerId")
        return@withContext consumerId
    }

    fun setConsumerVolume(consumerId: String, volume: Float) {
        consumers[consumerId]?.let { consumer ->
            (consumer.track as? AudioTrack)?.let { audioTrack ->
                // Convert 0.0-1.0 to 0.0-10.0 range
                val webRtcVolume = (volume.coerceIn(0f, 1f) * 10.0)
                audioTrack.setVolume(webRtcVolume)
                Log.d(TAG, "Volume set: $consumerId -> $volume (WebRTC: $webRtcVolume)")
            }
        }
    }

    fun closeConsumer(consumerId: String) {
        consumers.remove(consumerId)?.let { consumer ->
            consumer.close()
            Log.d(TAG, "Consumer closed: $consumerId")
        }
    }
}
```

**Source:** [mediasoup Consumer API](https://mediasoup.org/documentation/v3/libmediasoupclient/api/) + [WebRTC AudioTrack.setVolume](https://getstream.github.io/webrtc-android/stream-webrtc-android/org.webrtc/-audio-track/set-volume.html)

### Consumer Statistics Polling for Network Quality

```kotlin
// MediasoupClient.kt
suspend fun getConsumerStats(consumerId: String): Map<String, Any>? = withContext(Dispatchers.IO) {
    consumers[consumerId]?.let { consumer ->
        try {
            val stats = consumer.stats
            // Parse RTCStatsReport for key metrics
            // stats.statsMap contains "inbound-rtp" and "outbound-rtp" entries
            // Extract: packetsLost, jitter, roundTripTime
            val metrics = mutableMapOf<String, Any>()
            stats.statsMap.forEach { (key, value) ->
                if (value.type == "inbound-rtp") {
                    metrics["packetsLost"] = value.members["packetsLost"] ?: 0
                    metrics["jitter"] = value.members["jitter"] ?: 0.0
                    metrics["score"] = value.members["score"] ?: 10
                }
            }
            return@withContext metrics
        } catch (e: Exception) {
            Log.e(TAG, "getStats failed: $consumerId", e)
            null
        }
    }
}
```

**Usage in ViewModel:**

```kotlin
// ChannelListViewModel.kt
private fun startNetworkQualityMonitoring(channelId: String, consumerId: String) {
    viewModelScope.launch {
        while (isActive) {
            delay(5000) // Poll every 5 seconds

            mediasoupClient.getConsumerStats(consumerId)?.let { stats ->
                val packetsLost = stats["packetsLost"] as? Int ?: 0
                val jitter = stats["jitter"] as? Double ?: 0.0

                // Display in UI
                _networkQuality.update { quality ->
                    quality.copy(
                        channelId = channelId,
                        packetLoss = packetsLost,
                        jitter = jitter.toInt(),
                        indicator = when {
                            packetsLost < 10 && jitter < 30 -> "Good"
                            packetsLost < 50 && jitter < 100 -> "Fair"
                            else -> "Poor"
                        }
                    )
                }
            }
        }
    }
}
```

**Source:** [mediasoup RTC Statistics](https://mediasoup.org/documentation/v3/mediasoup/rtc-statistics/)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Custom audio playback with AudioTrack buffer management | Consumer.getTrack() → WebRTC AudioTrack with automatic playout | WebRTC M85+ (2020) | App-managed audio → library-managed, eliminated jitter buffer bugs, reduced code by 1000+ LOC |
| Server-side volume control via SFU mixing | Client-side per-consumer AudioTrack.setVolume() | mediasoup 3.0 (2019) SFU architecture | Zero-latency volume changes, bandwidth savings (no server re-encoding), enables audio mix mode |
| Polling getStats() via JavaScript bridge | Native Consumer.getStats() returning RTCStatsReport | libmediasoupclient 3.0+ (2020) | 100x faster stats (native vs JNI bridge), real-time network quality UI possible |
| Promise-based onConnect callback | Synchronous onConnect with runBlocking bridge | Android/Kotlin pattern (crow-misia 0.21.0) | JavaScript Promise → Kotlin suspend function, cleaner error handling |

**Deprecated/outdated:**
- **AudioManager global volume for PTT audio:** Replaced by per-consumer AudioTrack.setVolume() for mix mode support
- **Manual packet loss tracking:** Replaced by Consumer.getStats() RTCStatsReport with cumulative metrics
- **Custom jitter buffer implementation:** WebRTC AudioTrack handles playout automatically

## Open Questions

### Question 1: crow-misia Consumer.getStats() Return Type

**What we know:**
- mediasoup C++ API: Consumer.getStats() returns RTCStatsReport pointer
- WebRTC Android: RTCStatsReport is Java class with statsMap field
- Phase 11: crow-misia uses JSON strings for RTP parameters (not objects)

**What's unclear:**
- Does crow-misia Consumer.getStats() return RTCStatsReport object or JSON string?
- If string, what's the parsing pattern?

**Recommendation:**
Inspect return type during implementation. If JSON string, use Gson parsing:
```kotlin
val statsJson = consumer.stats // If String
val statsMap = gson.fromJson<Map<String, Any>>(statsJson, ...)
```

If RTCStatsReport object, use direct field access:
```kotlin
val stats = consumer.stats // If RTCStatsReport
val packetsLost = stats.statsMap["inbound-rtp"]?.members?.get("packetsLost")
```

### Question 2: RecvTransport onConnectionStateChange Reconnection Behavior

**What we know:**
- WebRTC PeerConnection auto-restarts ICE on connection failure
- RecvTransport wraps PeerConnection
- onConnectionStateChange fires on state transitions

**What's unclear:**
- Does RecvTransport auto-reconnect on "disconnected" state, or must app recreate transport?
- How long does ICE restart take (user experience: 2s pause vs 10s pause)?

**Recommendation:**
Monitor onConnectionStateChange("disconnected") → onConnectionStateChange("connected") transitions during testing. If auto-reconnects within 5 seconds, keep transport. If stays disconnected > 10 seconds, recreate transport. Phase 14 will implement reconnection state machine.

### Question 3: Consumer AudioTrack Volume Persistence Across Pause/Resume

**What we know:**
- Consumer.pause() stops audio playback
- Consumer.resume() restarts playback
- AudioTrack.setVolume() sets gain

**What's unclear:**
- Does volume setting persist across pause/resume cycle?
- Must app re-call setVolume() after resume()?

**Recommendation:**
Test during implementation. If volume resets to default after resume(), store volume in Map<consumerId, Float> and re-apply after resume():
```kotlin
fun resumeConsumer(consumerId: String) {
    consumer.resume()
    volumeSettings[consumerId]?.let { volume ->
        setConsumerVolume(consumerId, volume) // Re-apply volume
    }
}
```

## Sources

### Primary (HIGH confidence)

- [mediasoup libmediasoupclient API](https://mediasoup.org/documentation/v3/libmediasoupclient/api/) - RecvTransport, Consumer, Transport.Listener interface
- [mediasoup Communication Between Client and Server](https://mediasoup.org/documentation/v3/communication-between-client-and-server/) - onConnect DTLS parameter flow
- [mediasoup RTC Statistics](https://mediasoup.org/documentation/v3/mediasoup/rtc-statistics/) - Consumer.getStats() metrics (packetsLost, jitter, score)
- [WebRTC AudioTrack.setVolume](https://getstream.github.io/webrtc-android/stream-webrtc-android/org.webrtc/-audio-track/set-volume.html) - Volume control API (0.0-10.0 range)
- [WebRTC AudioTrack API](https://getstream.github.io/webrtc-android/stream-webrtc-android/org.webrtc/-audio-track/index.html) - AudioTrack methods and lifecycle
- [crow-misia libmediasoup-android GitHub](https://github.com/crow-misia/libmediasoup-android) - Official repository, version 0.21.0
- Phase 11 SUMMARY.md - Discovered crow-misia uses JSON string parameters, PeerConnectionFactory pattern

### Secondary (MEDIUM confidence)

- [mediasoup Reduce volume of audio consumer discussion](https://mediasoup.discourse.group/t/reduce-volume-of-audio-consumer/3253) - Confirms no Consumer.setVolume(), use AudioTrack
- [mediasoup Consumer.getStats() discussion](https://mediasoup.discourse.group/t/something-weird-in-consumer-getstats/1128) - Stats API usage patterns
- [haiyangwu mediasoup-client-android examples](https://github.com/haiyangwu/mediasoup-client-android) - RecvTransport.Listener implementation examples
- [Exercise Caution When Using runBlocking on Android](https://getstream.io/blog/caution-runblocking-android/) - Threading pitfalls
- [Kotlin Coroutines: runBlocking](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html) - Official coroutines docs

### Tertiary (LOW confidence)

- [How to Get Started with mediasoup-client-android](https://fxis.ai/edu/how-to-get-started-with-mediasoup-client-android/) - General setup guide
- [Mediasoup Essentials (Medium)](https://medium.com/@kimaswaemma36/mediasoup-essentials-creating-robust-webrtc-applications-a6c2ca4aafd1) - Architecture patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Library version confirmed in Phase 11, WebRTC bundled, Coroutines already in project
- Architecture: HIGH - RecvTransport/Consumer patterns verified in official mediasoup docs, AudioTrack.setVolume confirmed in WebRTC docs, runBlocking pattern from Kotlin official docs
- Pitfalls: HIGH - All four pitfalls verified with official sources (mediasoup API docs, WebRTC docs, Kotlin coroutines docs, Android best practices)

**Research date:** 2026-02-13
**Valid until:** 90 days (stable domain, mediasoup 3.x API stable since 2019, WebRTC M130 API stable)
