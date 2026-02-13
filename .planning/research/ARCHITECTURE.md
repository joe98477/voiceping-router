# Architecture Patterns: libmediasoup-android Integration

**Domain:** Android PTT app with mediasoup WebRTC library integration
**Researched:** 2026-02-13
**Confidence:** MEDIUM (Official docs + WebRTC patterns verified, specific threading details require source inspection)

## Executive Summary

libmediasoup-android (crow-misia wrapper) integrates WebRTC's native threading model with Android's Kotlin/coroutine architecture. The library manages its own PeerConnectionFactory with dedicated worker/network/signaling threads, while Transport listener callbacks execute synchronously on **WebRTC's signaling thread** — requiring careful coroutine bridging to avoid blocking. AudioTrack instances are created and managed by the library's internal PeerConnectionFactory, not externally provided. The existing singleton pattern (MediasoupClient, ChannelRepository, PttManager) requires minimal changes, but Transport.Listener callbacks need `runBlocking` or suspendCoroutine wrappers to call SignalingClient's suspend functions.

**Critical Integration Points:**
1. **PeerConnectionFactory initialization** - Library creates internally, requires ApplicationContext
2. **Transport.Listener callbacks** - Run on WebRTC signaling thread, must bridge to coroutines
3. **AudioTrack lifecycle** - Library-managed, tied to Producer/Consumer lifecycle
4. **Threading model** - WebRTC's 3-thread model (worker/network/signaling) coexists with Kotlin coroutines

## Recommended Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│ Android Application Layer (Kotlin + Coroutines)             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────┐      ┌─────────────────┐             │
│  │ ChannelRepository│◄─────┤ PttManager      │             │
│  │  (@Singleton)    │      │  (@Singleton)   │             │
│  └────────┬─────────┘      └────────┬────────┘             │
│           │                         │                       │
│           │ calls                   │ calls                 │
│           ▼                         ▼                       │
│  ┌──────────────────────────────────────────┐              │
│  │ MediasoupClient (@Singleton)             │              │
│  │ ┌──────────────────────────────────────┐ │              │
│  │ │ Device (singleton, holds RTP caps)   │ │              │
│  │ ├──────────────────────────────────────┤ │              │
│  │ │ RecvTransport                        │ │              │
│  │ │  - Listener callbacks                │ │              │
│  │ │  - Consumers Map<String, Consumer>   │ │              │
│  │ ├──────────────────────────────────────┤ │              │
│  │ │ SendTransport                        │ │              │
│  │ │  - Listener callbacks                │ │              │
│  │ │  - Producer (audio)                  │ │              │
│  │ └──────────────────────────────────────┘ │              │
│  └────────────┬─────────────────────────────┘              │
│               │ listener callbacks                         │
│               │ (signaling thread)                         │
│               ▼                                             │
│  ┌──────────────────────────────────────────┐              │
│  │ SignalingClient (@Singleton)             │              │
│  │  - suspend fun request()                 │              │
│  │  - WebSocket coroutines (Dispatchers.IO) │              │
│  └──────────────────────────────────────────┘              │
│                                                              │
├─────────────────────────────────────────────────────────────┤
│ libmediasoup-android (Native Bridge)                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────┐              │
│  │ PeerConnectionFactory (library-created)  │              │
│  │  - network_thread (write packets)        │              │
│  │  - worker_thread (codec, processing)     │              │
│  │  - signaling_thread (API calls, events)  │              │
│  ├──────────────────────────────────────────┤              │
│  │ AudioTrack (library-managed)             │              │
│  │  - Created by Consumer.resume()          │              │
│  │  - Plays audio via AudioManager          │              │
│  ├──────────────────────────────────────────┤              │
│  │ AudioSource (library-managed)            │              │
│  │  - Created for Producer                  │              │
│  │  - Reads from AudioRecord                │              │
│  └──────────────────────────────────────────┘              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Integration Points

### 1. Library Initialization

**What:** One-time global initialization of WebRTC subsystem
**Where:** `MediasoupClient.init()` or `VoicePingApplication.onCreate()`
**Thread:** Main thread (called before any mediasoup operations)

```kotlin
@Singleton
class MediasoupClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signalingClient: SignalingClient
) {
    init {
        // Initialize WebRTC native libraries (call once per app lifecycle)
        MediasoupClient.initialize(context)
    }

    private val device: Device by lazy {
        Device()
    }
}
```

**Why:** WebRTC requires context-based initialization for Android-specific audio/video subsystems.

**Consequences:** Initialization failure will crash app; must happen before Device creation.

**Evidence:** [haiyangwu/mediasoup-client-android](https://github.com/haiyangwu/mediasoup-client-android) shows `MediasoupClient.initialize(getApplicationContext())` pattern.

---

### 2. Device and RTP Capabilities Loading

**What:** Load router's RTP capabilities into Device (codecs, extensions, header extensions)
**Where:** `MediasoupClient.initialize()` suspend function
**Thread:** Caller thread (blocks until complete, marked @async)

```kotlin
suspend fun initialize() = withContext(Dispatchers.IO) {
    try {
        // Step 1: Request router capabilities from server
        val capsResponse = signalingClient.request(SignalingType.GET_ROUTER_CAPABILITIES)
        val rtpCapabilities = toJsonString(capsResponse.data?.get("routerRtpCapabilities")
            ?: throw IllegalStateException("No routerRtpCapabilities"))

        // Step 2: Load capabilities (BLOCKS current thread until complete)
        device.load(rtpCapabilities, null)

        _isInitialized.value = true
        Log.d(TAG, "Device loaded with RTP capabilities")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Device", e)
        throw e
    }
}
```

**Why:** Device.load() is marked @async and **blocks the calling thread** until WebRTC initialization completes (typically 50-200ms). Running on Dispatchers.IO prevents main thread blocking.

**Threading Model:** `Device.load()` is synchronous from caller perspective despite internal async WebRTC operations.

**Evidence:** [mediasoup API docs](https://mediasoup.org/documentation/v3/libmediasoupclient/api/) state "@async methods block current thread until operation completes."

---

### 3. Transport.Listener Callbacks with Coroutine Bridging

**CRITICAL:** Transport listener callbacks execute on **WebRTC's signaling thread**, not Kotlin coroutine context. Calling suspend functions requires bridging via `runBlocking` or `suspendCoroutine`.

#### Pattern 1: RecvTransport.Listener (Receive Audio)

```kotlin
suspend fun createRecvTransport(channelId: String) = withContext(Dispatchers.IO) {
    val transportResponse = signalingClient.request(
        SignalingType.CREATE_TRANSPORT,
        mapOf("channelId" to channelId, "direction" to "recv")
    )

    val transportData = transportResponse.data ?: throw IllegalStateException("No transport data")
    val transportId = transportData["id"] as String
    val iceParameters = toJsonString(transportData["iceParameters"])
    val iceCandidates = toJsonString(transportData["iceCandidates"])
    val dtlsParameters = toJsonString(transportData["dtlsParameters"])

    // Create transport with listener
    recvTransport = device.createRecvTransport(
        object : RecvTransport.Listener {
            override fun onConnect(transport: Transport, dtlsParameters: String): Future<String> {
                // RUNS ON WEBRTC SIGNALING THREAD - must bridge to coroutines
                return runBlocking {
                    try {
                        signalingClient.request(
                            SignalingType.CONNECT_TRANSPORT,
                            mapOf(
                                "transportId" to transportId,
                                "dtlsParameters" to dtlsParameters
                            )
                        )
                        "" // Return empty string on success
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to connect recv transport", e)
                        throw e
                    }
                }
            }

            override fun onConnectionStateChange(
                transport: Transport,
                connectionState: TransportState
            ) {
                // RUNS ON WEBRTC SIGNALING THREAD
                Log.d(TAG, "RecvTransport state: $connectionState")
                // Can update StateFlow here (thread-safe)
                // DO NOT call suspend functions directly
            }
        },
        id = transportId,
        iceParameters = iceParameters,
        iceCandidates = iceCandidates,
        dtlsParameters = dtlsParameters
    )
}
```

**Why runBlocking:** Callback executes on WebRTC's signaling thread (not coroutine dispatcher). SignalingClient.request() is suspend function requiring coroutine context. runBlocking creates coroutine context on current thread.

**Consequence:** Blocks signaling thread during request (typically 10-50ms for CONNECT_TRANSPORT). Alternative: Use `GlobalScope.launch` + CompletableFuture, but adds complexity.

**Evidence:** [WebRTC threading model](https://dyte.io/blog/understanding-libwebrtc/) confirms "all external callbacks run on signaling_thread."

#### Pattern 2: SendTransport.Listener (Send Audio)

```kotlin
suspend fun createSendTransport(channelId: String) = withContext(Dispatchers.IO) {
    val transportResponse = signalingClient.request(
        SignalingType.CREATE_TRANSPORT,
        mapOf("channelId" to channelId, "direction" to "send")
    )

    val transportData = transportResponse.data ?: throw IllegalStateException("No transport data")
    val transportId = transportData["id"] as String
    val iceParameters = toJsonString(transportData["iceParameters"])
    val iceCandidates = toJsonString(transportData["iceCandidates"])
    val dtlsParameters = toJsonString(transportData["dtlsParameters"])

    sendTransport = device.createSendTransport(
        object : SendTransport.Listener {
            override fun onConnect(transport: Transport, dtlsParameters: String): Future<String> {
                // RUNS ON WEBRTC SIGNALING THREAD
                return runBlocking {
                    signalingClient.request(
                        SignalingType.CONNECT_TRANSPORT,
                        mapOf(
                            "transportId" to transportId,
                            "dtlsParameters" to dtlsParameters
                        )
                    )
                    ""
                }
            }

            override fun onProduce(
                transport: Transport,
                kind: String,
                rtpParameters: String,
                appData: String
            ): Future<String> {
                // RUNS ON WEBRTC SIGNALING THREAD
                return runBlocking {
                    val produceResponse = signalingClient.request(
                        SignalingType.PRODUCE,
                        mapOf(
                            "kind" to kind,
                            "rtpParameters" to rtpParameters
                        )
                    )
                    produceResponse.data?.get("id") as? String
                        ?: throw IllegalStateException("No producer id in response")
                }
            }

            override fun onConnectionStateChange(
                transport: Transport,
                connectionState: TransportState
            ) {
                Log.d(TAG, "SendTransport state: $connectionState")
            }
        },
        id = transportId,
        iceParameters = iceParameters,
        iceCandidates = iceCandidates,
        dtlsParameters = dtlsParameters
    )
}
```

**onProduce Timing:** Called when `transport.produce()` is invoked. Must return server-assigned producer ID synchronously (from callback perspective). runBlocking waits for server response before returning.

**Error Handling:** Exceptions thrown in callbacks propagate to WebRTC layer, which may close transport. Catch and log critical failures.

---

### 4. AudioTrack Creation and Management

**IMPORTANT:** The library creates and manages AudioTrack internally. You **do not** provide AudioTrack instances.

#### Receive Side (Consumer → AudioTrack)

```kotlin
suspend fun consumeAudio(producerId: String, peerId: String) = withContext(Dispatchers.IO) {
    val consumeResponse = signalingClient.request(
        SignalingType.CONSUME,
        mapOf("producerId" to producerId, "peerId" to peerId)
    )

    val consumeData = consumeResponse.data ?: throw IllegalStateException("No consume data")
    val consumerId = consumeData["id"] as String
    val kind = consumeData["kind"] as String
    val rtpParameters = toJsonString(consumeData["rtpParameters"])

    // Create consumer (library creates AudioTrack internally)
    val consumer = recvTransport?.consume(
        object : Consumer.Listener {
            override fun onTransportClose(consumer: Consumer) {
                consumers.remove(consumerId)
                Log.d(TAG, "Consumer closed: $consumerId")
            }
        },
        id = consumerId,
        producerId = producerId,
        kind = kind,
        rtpParameters = rtpParameters,
        appData = ""
    )

    consumer?.let {
        consumers[consumerId] = it
        it.resume() // START audio playback (AudioTrack.play() called internally)
        Log.d(TAG, "Consumer resumed, audio playing")
    }
}
```

**AudioTrack Creation:** Happens inside `consumer.resume()`. Library creates `org.webrtc.AudioTrack` with default audio routing (system AudioManager).

**Audio Routing:** AudioTrack routes to current Android audio output (earpiece/speaker/Bluetooth) based on `AudioManager.mode` and `isSpeakerphoneOn` settings (managed by AudioRouter).

**Threading:** AudioTrack creation and playback occur on WebRTC's worker thread. Consumer.resume() is synchronous but triggers async audio pipeline startup.

**Evidence:** [WebRTC Android patterns](https://www.videosdk.live/blog/webrtc-android) show PeerConnectionFactory creates tracks internally.

#### Send Side (AudioSource → Producer)

```kotlin
suspend fun startProducing() = withContext(Dispatchers.IO) {
    // Create audio source (library creates AudioRecord internally)
    val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())

    // Create local audio track
    val audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource)

    // Produce (no codec options in Kotlin API, configured server-side)
    audioProducer = sendTransport?.produce(
        object : Producer.Listener {
            override fun onTransportClose(producer: Producer) {
                audioProducer = null
                Log.d(TAG, "Producer closed")
            }
        },
        audioTrack,
        null, // encodings (use server defaults)
        null  // codecOptions (use server defaults)
    )

    Log.d(TAG, "Audio producer started")
}
```

**PeerConnectionFactory Access:** Library may expose factory via `Device.getPeerConnectionFactory()` or require separate initialization. Check crow-misia API.

**AudioSource:** Wraps Android AudioRecord. Opus encoding happens in WebRTC worker thread (native code).

**Codec Configuration:** Opus settings (DTX, FEC, ptime, stereo) may be configured via codecOptions parameter (JSON string) or server-side in mediasoup router settings.

**Alternative Pattern (if factory not exposed):** Use library's built-in audio track creation (check crow-misia docs for `Device.createAudioTrack()` equivalent).

---

### 5. Data Flow: PTT Audio Capture

Current code shows `AudioCaptureManager → MediasoupClient.sendAudioData()` pattern. This **changes** with library integration:

#### Before (Placeholder Code)
```kotlin
// PttManager
audioCaptureManager.onAudioData = { buffer, length ->
    mediasoupClient.sendAudioData(buffer, length)
}
```

#### After (Library Integration)
```kotlin
// AudioCaptureManager is REPLACED by WebRTC AudioSource
// Producer automatically captures from AudioRecord

suspend fun startProducing() = withContext(Dispatchers.IO) {
    val audioSource = createAudioSource() // Library creates AudioRecord
    val audioTrack = createAudioTrack(audioSource)

    audioProducer = sendTransport?.produce(
        producerListener,
        audioTrack,
        null,
        null
    )
    // Audio flows automatically: AudioRecord → AudioSource → Producer → RTP
}

fun stopProducing() {
    audioProducer?.close() // Stops AudioRecord, releases resources
    audioProducer = null
}
```

**Impact on PttManager:**
- Remove `audioCaptureManager.onAudioData` callback
- Remove `mediasoupClient.sendAudioData()` calls
- Simplify to: `startProducing()` on PTT press, `stopProducing()` on release
- AudioCaptureManager may be removed entirely (library handles capture)

**AudioCaptureService:** May still be needed for foreground service notification (mic permission), but audio capture logic moves to library.

---

## Threading Model Deep Dive

### WebRTC's 3-Thread Architecture

| Thread | Purpose | What Runs Here | Blocking Allowed? |
|--------|---------|----------------|-------------------|
| **signaling_thread** | API calls and callbacks | Device.load(), Transport.Listener callbacks, Consumer/Producer listeners | NO - callbacks must return quickly |
| **worker_thread** | Media processing | Opus encoding/decoding, RTP packetization, audio effects (AEC, NS) | N/A (internal) |
| **network_thread** | Network I/O | ICE negotiation, DTLS handshake, RTP/RTCP packet send/receive | N/A (internal) |

**Key Rule:** **Never block signaling_thread** in callbacks. Long operations (network requests, database queries) must run on separate threads/coroutines.

### Coroutine Integration Strategy

#### Strategy 1: runBlocking (Current Recommendation)

**Pros:**
- Simple, matches synchronous callback signature
- Waits for server response before returning

**Cons:**
- Blocks signaling thread (10-50ms per request)
- Can cause deadlocks if WebRTC waits on callback return while holding locks

**When to Use:** Short network requests (CONNECT_TRANSPORT, PRODUCE) where blocking is acceptable.

```kotlin
override fun onConnect(transport: Transport, dtlsParameters: String): Future<String> {
    return runBlocking {
        signalingClient.request(SignalingType.CONNECT_TRANSPORT, ...)
        ""
    }
}
```

#### Strategy 2: suspendCoroutine + GlobalScope (Advanced)

**Pros:**
- Non-blocking on signaling thread
- Better for long operations

**Cons:**
- More complex, requires CompletableFuture
- Callback returns immediately, transport may time out if async operation is slow

**When to Use:** Long operations (>100ms) or if experiencing deadlocks.

```kotlin
override fun onConnect(transport: Transport, dtlsParameters: String): Future<String> {
    val future = CompletableFuture<String>()
    GlobalScope.launch(Dispatchers.IO) {
        try {
            signalingClient.request(SignalingType.CONNECT_TRANSPORT, ...)
            future.complete("")
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
    }
    return future
}
```

**Recommendation:** Start with runBlocking. Only move to suspendCoroutine if profiling shows signaling thread stalls.

---

## Component Boundaries

### Existing Components (Minimal Changes)

| Component | Current Role | Library Integration Impact |
|-----------|--------------|---------------------------|
| **MediasoupClient** | Holds Device/Transport/Consumer/Producer refs, placeholder methods | Replace placeholder TODOs with actual library calls, add PeerConnectionFactory initialization |
| **ChannelRepository** | Orchestrates join/consume flow | No change (continues calling MediasoupClient) |
| **PttManager** | PTT state machine, AudioCaptureManager integration | Remove AudioCaptureManager dependency, simplify to startProducing()/stopProducing() |
| **SignalingClient** | WebSocket request/response | No change (continues being called from Transport listeners) |
| **AudioRouter** | AudioManager mode/routing | No change (library's AudioTrack respects system routing) |

### New Components

| Component | Purpose | Implementation |
|-----------|---------|----------------|
| **PeerConnectionFactory** | WebRTC subsystem initialization | Created by library during `MediasoupClient.initialize(context)`, may be accessible via `Device.getPeerConnectionFactory()` |
| **AudioSource** | Microphone audio capture | Created via `factory.createAudioSource(MediaConstraints())`, replaces AudioCaptureManager |
| **AudioTrack** | Audio playback | Created by library during `consumer.resume()`, destroyed on `consumer.close()` |

### Components to Remove/Refactor

| Component | Current Role | Action |
|-----------|--------------|--------|
| **AudioCaptureManager** | AudioRecord wrapper for PTT audio | **REMOVE** - AudioSource replaces this functionality |
| **AudioCaptureService** | Foreground service for mic permission | **KEEP** but simplify - still needed for notification, but audio capture logic removed |

---

## Data Flow Changes

### Before Library Integration (Placeholder)

```
PTT Press → PttManager.requestPtt()
         → SignalingClient.request(PTT_START)
         → MediasoupClient.createSendTransport() [placeholder]
         → MediasoupClient.startProducing() [placeholder]
         → AudioCaptureManager.startCapture()
         → AudioCaptureManager.onAudioData callback
         → MediasoupClient.sendAudioData() [no-op]
```

### After Library Integration

```
PTT Press → PttManager.requestPtt()
         → SignalingClient.request(PTT_START)
         → MediasoupClient.createSendTransport()
            → device.createSendTransport()
            → SendTransport.Listener.onConnect()
               → runBlocking { signalingClient.request(CONNECT_TRANSPORT) }
         → MediasoupClient.startProducing()
            → factory.createAudioSource()
            → factory.createAudioTrack(audioSource)
            → sendTransport.produce(audioTrack)
               → SendTransport.Listener.onProduce()
                  → runBlocking { signalingClient.request(PRODUCE) }
            → [Library AudioRecord captures audio automatically]
            → [Worker thread: Opus encode → RTP packets]
            → [Network thread: Send RTP via ICE/DTLS]
```

**Key Difference:** No manual audio buffer forwarding. Library's AudioSource manages AudioRecord lifecycle.

---

### Receive Flow

```
Server Broadcast (SPEAKER_CHANGED) → SignalingClient.messages flow
                                   → ChannelRepository.observeSpeakerChanges
                                   → MediasoupClient.consumeAudio(producerId)
                                      → SignalingClient.request(CONSUME)
                                      → recvTransport.consume()
                                         → consumer.resume()
                                            → [Library creates AudioTrack]
                                            → [Worker thread: RTP receive → Opus decode]
                                            → [AudioTrack plays via AudioManager routing]
```

**AudioRouter Integration:** AudioTrack respects system routing set by `AudioRouter.setEarpieceMode()` / `setSpeakerMode()` / `setBluetoothMode()`. No direct coupling needed.

---

## Scalability Considerations

| Concern | At 1 Channel | At 5 Channels (Scan Mode) | At 10+ Channels (Future) |
|---------|--------------|---------------------------|--------------------------|
| **Device instances** | 1 (singleton) | 1 (shared across transports) | 1 (Device is app-global) |
| **RecvTransport instances** | 1 | 1 (shared for all receive consumers) | Consider 1 per channel if latency issues |
| **SendTransport instances** | 1 (created on PTT) | 1 (reused for all PTT transmissions) | 1 (PTT is exclusive across channels) |
| **Consumer instances** | 1-5 per channel | 5-25 total (1 per active speaker * channels) | May hit WebRTC limits (50-100 consumers) |
| **Memory overhead** | ~10-20 MB (WebRTC baseline) | ~20-40 MB (additional consumers) | ~50-100 MB (track buffers, decoders) |
| **CPU (worker thread)** | 5-10% (1 Opus stream) | 15-30% (5 Opus streams) | 40-60% (10+ streams) |

**Optimization Strategies:**
- **Mute = Close Consumer:** Bandwidth and CPU savings (already implemented in ChannelRepository)
- **Reuse Transports:** One RecvTransport for all channels (already planned)
- **Consumer Pooling:** Reuse consumers for same producer (if speaker re-transmits)

---

## Architectural Decisions

### Decision 1: Singleton Device vs Per-Channel Devices

**Decision:** Use single Device singleton shared across all transports
**Rationale:** Device.load() is expensive (50-200ms), RTP capabilities identical for all channels, mediasoup design assumes one Device per client
**Alternative Rejected:** Per-channel Devices would waste memory (duplicate codec instances) and complicate capability management
**Consequence:** Device initialization must complete before any channel join

### Decision 2: runBlocking vs suspendCoroutine in Transport Callbacks

**Decision:** Use runBlocking for Transport.Listener callbacks (initial implementation)
**Rationale:** Simpler code, acceptable blocking duration (10-50ms), matches synchronous callback signature
**Alternative:** suspendCoroutine + CompletableFuture for non-blocking (use if profiling shows issues)
**Consequence:** Signaling thread blocks during server requests, may need tuning if deadlocks occur

### Decision 3: Remove AudioCaptureManager

**Decision:** Remove AudioCaptureManager, use library's AudioSource
**Rationale:** Duplicate functionality, library's AudioSource handles AudioRecord lifecycle correctly, native Opus encoding more efficient
**Alternative Rejected:** Keep AudioCaptureManager and manually feed AudioSource (adds complexity, no benefit)
**Consequence:** AudioCaptureService becomes minimal wrapper for foreground notification

### Decision 4: Shared RecvTransport for All Channels

**Decision:** One RecvTransport for all receive consumers across channels
**Rationale:** Mediasoup design allows multiple consumers per transport, reduces ICE negotiation overhead
**Alternative:** Per-channel transports (wasteful, more network overhead)
**Consequence:** Consumer management tracks channelId mapping explicitly

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Calling Suspend Functions Directly from Transport Callbacks

**What goes wrong:**
```kotlin
override fun onConnect(transport: Transport, dtlsParameters: String): Future<String> {
    // WRONG - suspend function can't be called from non-coroutine context
    signalingClient.request(SignalingType.CONNECT_TRANSPORT, ...)
}
```

**Why bad:** Compiler error: "suspend function can only be called from coroutine or another suspend function"
**Instead:** Wrap with runBlocking or suspendCoroutine

### Anti-Pattern 2: Providing External AudioTrack to Producer

**What goes wrong:**
```kotlin
// WRONG - manually creating AudioTrack
val audioTrack = AudioTrack(...)
sendTransport.produce(producerListener, audioTrack, null, null)
```

**Why bad:** Library expects `org.webrtc.AudioTrack` (WebRTC type), not `android.media.AudioTrack` (Android type). AudioTrack must come from PeerConnectionFactory.
**Instead:** Use `factory.createAudioTrack(audioSource)`

### Anti-Pattern 3: Blocking Main Thread with Device.load()

**What goes wrong:**
```kotlin
// WRONG - blocks UI thread
fun initialize() {
    device.load(rtpCapabilities, null) // Blocks 50-200ms
}
```

**Why bad:** Causes UI jank, potential ANR on slow devices
**Instead:** Run in `withContext(Dispatchers.IO)`

### Anti-Pattern 4: Creating Multiple Devices

**What goes wrong:**
```kotlin
// WRONG - creating Device per channel
fun joinChannel(channelId: String) {
    val device = Device()
    device.load(rtpCapabilities, null)
    // ...
}
```

**Why bad:** Device.load() is expensive, wastes memory, RTP capabilities are router-wide
**Instead:** Use singleton Device, create only transports per channel

---

## Integration Checklist

### Phase 1: Library Setup
- [ ] Add libmediasoup-android dependency to build.gradle.kts (already done: 0.7.0)
- [ ] Call `MediasoupClient.initialize(context)` in VoicePingApplication.onCreate()
- [ ] Verify library loads without crashing (test on physical device)

### Phase 2: Device Initialization
- [ ] Implement `MediasoupClient.initialize()` with Device.load()
- [ ] Run on Dispatchers.IO to avoid main thread blocking
- [ ] Expose `isInitialized` StateFlow for UI
- [ ] Handle load failures gracefully (retry, error screen)

### Phase 3: RecvTransport Integration
- [ ] Implement `createRecvTransport()` with actual library calls
- [ ] Add RecvTransport.Listener with onConnect callback using runBlocking
- [ ] Test transport creation succeeds (check logs for ICE/DTLS state)

### Phase 4: Consumer Integration
- [ ] Implement `consumeAudio()` with actual recvTransport.consume()
- [ ] Add Consumer.Listener for onTransportClose
- [ ] Call consumer.resume() to start playback
- [ ] Verify audio plays through device speaker/earpiece

### Phase 5: SendTransport Integration
- [ ] Implement `createSendTransport()` with SendTransport.Listener
- [ ] Add onConnect and onProduce callbacks using runBlocking
- [ ] Test transport creation and DTLS connection

### Phase 6: Producer Integration
- [ ] Remove AudioCaptureManager dependency from PttManager
- [ ] Implement `startProducing()` with factory.createAudioSource()
- [ ] Create AudioTrack and call sendTransport.produce()
- [ ] Verify audio transmits to server (test with another client)

### Phase 7: Cleanup & Edge Cases
- [ ] Implement proper disposal order (producers → consumers → transports)
- [ ] Test rapid PTT press/release (no memory leaks)
- [ ] Test multi-channel switching (consumers close correctly)
- [ ] Profile signaling thread usage (ensure no deadlocks)

---

## Open Questions

1. **PeerConnectionFactory Access:** Does crow-misia expose `Device.getPeerConnectionFactory()` or require separate initialization? Check API docs or source code.

2. **Codec Options in Kotlin:** Java API shows codecOptions as String (JSON). Verify crow-misia supports Opus DTX/FEC configuration or if it's server-side only.

3. **AudioRecord Permissions:** Does AudioSource creation trigger mic permission prompt or rely on AudioCaptureService's foreground service? Test on API 31+.

4. **Threading Deadlock Risk:** If WebRTC holds locks while waiting for callback return, runBlocking could deadlock. Monitor signaling thread (Android Profiler).

5. **Consumer Volume Control:** Does crow-misia expose `consumer.setVolume()` or is volume managed via AudioTrack? Check for per-channel volume support.

6. **Bluetooth SCO Interaction:** How does library's AudioTrack interact with AudioRouter's Bluetooth SCO setup? Test audio routing with BT headset.

---

## Sources

**HIGH Confidence:**
- [mediasoup libmediasoupclient API](https://mediasoup.org/documentation/v3/libmediasoupclient/api/) - Official API documentation for Device, Transport, Producer, Consumer classes
- [libmediasoup Design](https://mediasoup.org/documentation/v3/libmediasoupclient/design/) - Threading model and @async behavior documentation
- [WebRTC Threading Model](https://dyte.io/blog/understanding-libwebrtc/) - WebRTC's 3-thread architecture (signaling/worker/network)

**MEDIUM Confidence:**
- [crow-misia libmediasoup-android GitHub](https://github.com/crow-misia/libmediasoup-android) - Kotlin wrapper repository
- [haiyangwu mediasoup-client-android](https://github.com/haiyangwu/mediasoup-client-android) - Alternative Android wrapper with example code
- [WebRTC Android Guide](https://www.videosdk.live/blog/webrtc-android) - PeerConnectionFactory initialization patterns

**LOW Confidence (Needs Verification):**
- Codec options format for Kotlin API (assumed JSON string, verify with source)
- PeerConnectionFactory exposure in crow-misia (assumed accessible, verify API)
- Consumer volume control API (assumed exists, verify method signature)

**WebSearch Evidence:**
- [Building WebRTC with MediaSoup](https://webrtc.ventures/2022/05/webrtc-with-mediasoup/) - Architecture patterns
- [runBlocking Caution on Android](https://getstream.io/blog/caution-runblocking-android/) - Threading best practices
- [AudioTrack Threading Issues](https://groups.google.com/g/discuss-webrtc/c/ExMEMjyERIc) - WebRTC thread requirements
