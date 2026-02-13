# Feature Research: libmediasoup-android Integration

**Domain:** WebRTC PTT Audio via mediasoup-android
**Researched:** 2026-02-13
**Confidence:** MEDIUM

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Device initialization with RTP capabilities | Core mediasoup pattern, server determines codec support | LOW | `Device()` + `device.load(rtpCapabilities)` - Already stubbed in MediasoupClient.kt |
| Send/Recv transport creation | Required for bidirectional audio (PTT send + listen receive) | MEDIUM | `device.createSendTransport()` + `device.createRecvTransport()` with ICE/DTLS params from server |
| Producer creation for PTT audio | Send audio when PTT pressed | MEDIUM | `sendTransport.produce(audioTrack, codecOptions)` with Opus config |
| Consumer creation for incoming audio | Receive audio from other channel participants | MEDIUM | `recvTransport.consume(consumerId, producerId, kind, rtpParameters)` |
| AudioTrack creation via PeerConnectionFactory | WebRTC audio source for Producer | MEDIUM | `peerConnectionFactory.createAudioSource(constraints)` + `createAudioTrack(id, source)` |
| Transport listener callbacks (onConnect, onProduce) | Mediasoup signaling protocol requirement | MEDIUM | Server expects DTLS params via onConnect, producer ID via onProduce |
| Consumer pause/resume for listening control | User expects to stop/start receiving audio | LOW | `consumer.resume()` starts playback, `consumer.pause()` stops |
| Producer close on PTT release | Stop sending audio when PTT released | LOW | `producer.close()` - Already stubbed in MediasoupClient.kt |
| Ordered resource disposal | Prevent memory leaks and crashes | LOW | Order: producers → consumers → transports → device (never dispose device) |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Per-consumer volume control | Individual channel volume (already in UI via ChannelVolumeDialog.kt) | LOW | `audioTrack.setVolume(0.0-10.0)` on Consumer's track - WebRTC AudioTrack API |
| Opus codec PTT optimization | Better voice quality + bandwidth efficiency | LOW | Already configured in MediasoupClient.kt: opusDtx=true, opusFec=true, mono, 48kHz |
| Audio device switching (earpiece/speaker/BT) | Already implemented in AudioRouter.kt, needs WebRTC integration | MEDIUM | JavaAudioDeviceModule custom config or PeerConnectionFactory.Options |
| Consumer statistics monitoring | Network quality indicators (already in UI) | MEDIUM | `consumer.getStats()` for packet loss, jitter, bitrate |
| Echo cancellation + noise suppression | Critical for PTT clarity | LOW | MediaConstraints: googEchoCancellation=true, googNoiseSuppression=true |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Manual AudioRecord integration | Direct PCM buffer control | libmediasoup uses WebRTC's internal audio capture; manual AudioRecord bypasses echo cancellation, requires custom Opus encoding | Use PeerConnectionFactory's AudioSource - handles capture, AEC, Opus encoding automatically |
| Producer volume control | User wants to adjust "mic gain" | WebRTC Producer doesn't expose volume API; belongs in capture layer | Use AudioSource constraints (googAudioMirroring, googAutoGainControl) or OS mic gain |
| Consumer audio routing override | Trying to force earpiece/speaker per-consumer | WebRTC audio routing is global (JavaAudioDeviceModule); per-consumer routing creates conflicts | Use AudioRouter.kt for global device switching (already implemented) |
| Optimistic producer state (start sending before server grants) | Reduce perceived latency | Server may deny PTT (busy channel); optimistic send wastes bandwidth, confuses state machine | Current PttState.Requesting pattern is correct - wait for server grant |

## Feature Dependencies

```
Device Initialization
    └──requires──> Router RTP Capabilities (from server)
        └──enables──> Transport Creation

Send Transport
    ├──requires──> Device loaded
    ├──requires──> Transport params from server (ICE/DTLS)
    └──enables──> Producer Creation

Recv Transport
    ├──requires──> Device loaded
    ├──requires──> Transport params from server (ICE/DTLS)
    └──enables──> Consumer Creation

Producer
    ├──requires──> Send Transport created
    ├──requires──> AudioTrack from PeerConnectionFactory
    ├──requires──> onConnect callback fired (DTLS connected)
    └──triggers──> onProduce callback (server assigns producer ID)

Consumer
    ├──requires──> Recv Transport created
    ├──requires──> Server newConsumer event (producerId, rtpParameters)
    └──requires──> consumer.resume() to start playback

PeerConnectionFactory
    ├──required-by──> AudioTrack creation
    └──initializes──> WebRTC native libraries

AudioSource (from PeerConnectionFactory)
    ├──requires──> MediaConstraints (AEC, NS, AGC)
    └──feeds──> AudioTrack → Producer

Volume Control
    ├──requires──> Consumer created
    └──accesses──> consumer.track.setVolume()

Resource Cleanup
    ├──requires──> All producers closed FIRST
    ├──then──> All consumers closed
    ├──then──> Send transport closed
    ├──then──> Recv transport closed
    └──never──> Device disposal (singleton, reused)
```

### Dependency Notes

- **Device must load RTP capabilities before any transport creation:** Server's router capabilities determine codec support. Calling `createSendTransport()` before `device.load()` will fail.
- **AudioTrack requires PeerConnectionFactory initialization:** WebRTC's `PeerConnectionFactory.initialize()` must be called once at app startup. Factory creates AudioSource → AudioTrack pipeline.
- **Producer requires onConnect AND onProduce callbacks:** Transport's `onConnect()` signals DTLS params to server. Only after DTLS connects will `produce()` trigger `onProduce()` callback, which MUST return server-assigned producer ID.
- **Consumer requires explicit resume():** Unlike Producer (starts automatically), Consumer is created paused. Must call `consumer.resume()` to start audio playback.
- **Volume control is per-AudioTrack, not per-Consumer:** Each Consumer has a WebRTC AudioTrack. Call `consumer.track.setVolume(gain)` where gain is 0.0-10.0.
- **Cleanup order prevents crashes:** Disposing transport before closing producers/consumers causes native crashes. Disposing device breaks singleton pattern (device is reused across channels).

## MVP Definition

### Launch With (v1) - Milestone v3.0

Minimum viable mediasoup integration for PTT audio.

- [ ] **PeerConnectionFactory initialization** — Initialize WebRTC once at app startup, create factory for AudioTrack/AudioSource
- [ ] **Device creation and RTP capabilities loading** — Initialize Device, load server's router capabilities (already stubbed)
- [ ] **Send transport with PTT producer** — Create send transport, produce audio with Opus config when PTT pressed
- [ ] **Receive transport with consumers** — Create recv transport, consume remote audio from channel participants
- [ ] **Transport listener callbacks** — Implement onConnect (DTLS signaling) and onProduce (get producer ID from server)
- [ ] **AudioTrack creation with AEC/NS** — Create AudioSource with MediaConstraints (echo cancel, noise suppress), feed to Producer
- [ ] **Consumer resume on newConsumer event** — Start audio playback when server signals new remote producer
- [ ] **Ordered resource cleanup** — Dispose producers → consumers → transports in MediasoupClient.cleanup()

### Add After Validation (v1.x) - Post-Launch Enhancements

Features to add once core audio is working.

- [ ] **Per-consumer volume control** — Wire ChannelVolumeDialog.kt to `consumer.track.setVolume()` API
- [ ] **Consumer statistics (getStats)** — Integrate `consumer.getStats()` for NetworkQualityIndicator.kt (packet loss, jitter)
- [ ] **Audio device module custom config** — Replace default JavaAudioDeviceModule with custom config for AudioRouter.kt integration
- [ ] **Producer pause/resume** — Support toggle mode (PTT stays on until second press) with `producer.pause()`

### Future Consideration (v2+) - Advanced Features

Features to defer until product-market fit is established.

- [ ] **Simulcast for bandwidth adaptation** — Enable `encodings` parameter in `produce()` for multi-bitrate streams
- [ ] **Data channel support** — Use DataProducer/DataConsumer for text chat or metadata
- [ ] **Video track support** — Extend to video producers/consumers for future video PTT

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Device + Transport creation | HIGH | MEDIUM | P1 |
| Producer (send audio) | HIGH | MEDIUM | P1 |
| Consumer (receive audio) | HIGH | MEDIUM | P1 |
| PeerConnectionFactory + AudioTrack | HIGH | MEDIUM | P1 |
| Transport callbacks (onConnect, onProduce) | HIGH | MEDIUM | P1 |
| Consumer resume() | HIGH | LOW | P1 |
| Ordered cleanup | HIGH | LOW | P1 |
| AEC + noise suppression constraints | HIGH | LOW | P1 |
| Per-consumer volume control | MEDIUM | LOW | P2 |
| Consumer getStats() | MEDIUM | MEDIUM | P2 |
| Audio device module config | MEDIUM | HIGH | P2 |
| Producer pause/resume | LOW | LOW | P2 |
| Simulcast | LOW | HIGH | P3 |
| Data channel | LOW | MEDIUM | P3 |

**Priority key:**
- P1: Must have for launch (real audio via mediasoup)
- P2: Should have, add when possible (volume, stats, device routing)
- P3: Nice to have, future consideration (advanced features)

## Implementation Pattern Analysis

### Device Lifecycle (Already Stubbed in MediasoupClient.kt)

**Current skeleton:**
```kotlin
// Line 62-86: initialize()
val capsResponse = signalingClient.request(SignalingType.GET_ROUTER_CAPABILITIES)
val rtpCapabilities = toJsonString(capsResponse.data?.get("routerRtpCapabilities"))
// TODO: device = Device()
// TODO: device.load(rtpCapabilities)
```

**Actual implementation:**
```kotlin
import org.mediasoup.droid.Device

suspend fun initialize() = withContext(Dispatchers.IO) {
    val capsResponse = signalingClient.request(SignalingType.GET_ROUTER_CAPABILITIES)
    val rtpCapabilities = capsResponse.data?.get("routerRtpCapabilities") as? String
        ?: throw IllegalStateException("No routerRtpCapabilities")

    device = Device()
    device?.load(rtpCapabilities) // JSON string expected
    _isInitialized.value = true
}
```

**CRITICAL:** Server returns JSON object, but `device.load()` expects JSON string. Use `toJsonString()` helper (line 48-50).

### Transport Creation Pattern

**Send transport with listener (lines 262-341):**
```kotlin
import org.mediasoup.droid.SendTransport
import org.mediasoup.droid.Transport

sendTransport = device?.createSendTransport(
    object : SendTransport.Listener {
        override fun onConnect(transport: Transport, dtlsParameters: String): String {
            // CRITICAL: This fires BEFORE produce()
            // Must signal DTLS params to server for ICE/DTLS handshake
            runBlocking {
                signalingClient.request(
                    SignalingType.CONNECT_TRANSPORT,
                    mapOf(
                        "transportId" to transportId,
                        "dtlsParameters" to dtlsParameters // Already JSON string
                    )
                )
            }
            return "" // Return value ignored
        }

        override fun onProduce(
            transport: Transport,
            kind: String,
            rtpParameters: String,
            appData: String
        ): String {
            // CRITICAL: Return server-assigned producer ID
            // produce() call blocks until this returns
            return runBlocking {
                val response = signalingClient.request(
                    SignalingType.PRODUCE,
                    mapOf("kind" to kind, "rtpParameters" to rtpParameters)
                )
                response.data?.get("id") as? String
                    ?: throw IllegalStateException("No producer id")
            }
        }

        override fun onConnectionStateChange(transport: Transport, state: String) {
            Log.d(TAG, "Send transport state: $state")
        }
    },
    id = transportId,
    iceParameters = iceParameters, // JSON string
    iceCandidates = iceCandidates,  // JSON string
    dtlsParameters = dtlsParameters // JSON string
)
```

**Receive transport (lines 99-159):** Same pattern, but RecvTransport.Listener only needs `onConnect` and `onConnectionStateChange`.

### Producer Creation with AudioTrack

**PeerConnectionFactory setup (add to MediasoupClient or separate manager):**
```kotlin
import org.webrtc.PeerConnectionFactory
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints

// One-time initialization (call in Application.onCreate or MediasoupClient init)
PeerConnectionFactory.initialize(
    PeerConnectionFactory.InitializationOptions.builder(context)
        .setEnableInternalTracer(false)
        .createInitializationOptions()
)

val factory = PeerConnectionFactory.builder()
    .setOptions(PeerConnectionFactory.Options())
    .createPeerConnectionFactory()

// Create audio track with AEC + NS
val audioConstraints = MediaConstraints().apply {
    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
}

val audioSource = factory.createAudioSource(audioConstraints)
val audioTrack = factory.createAudioTrack("audio-track-${System.currentTimeMillis()}", audioSource)
```

**Producer creation (lines 355-385):**
```kotlin
import org.mediasoup.droid.Producer

audioProducer = sendTransport?.produce(
    object : Producer.Listener {
        override fun onTransportClose(producer: Producer) {
            audioProducer = null
            Log.d(TAG, "Producer closed by transport")
        }
    },
    audioTrack, // WebRTC AudioTrack from PeerConnectionFactory
    null, // encodings (null = single stream, no simulcast)
    JSONObject().apply {
        // Opus codec options for PTT (already in stub line 369-375)
        put("opusStereo", false)      // Mono for PTT
        put("opusDtx", true)           // Discontinuous TX (silence suppression)
        put("opusFec", true)           // Forward error correction
        put("opusMaxPlaybackRate", 48000)
        put("opusPtime", 20)           // 20ms packets
    }.toString(),
    null // appData
)
```

**CRITICAL:** MediasoupClient.sendAudioData() (lines 396-401) is NOT needed. Producer sends audio automatically from AudioTrack's internal capture.

### Consumer Creation and Playback

**Consumer creation (lines 173-224):**
```kotlin
import org.mediasoup.droid.Consumer

val consumer = recvTransport?.consume(
    object : Consumer.Listener {
        override fun onTransportClose(consumer: Consumer) {
            consumers.remove(consumerId)
            Log.d(TAG, "Consumer closed: $consumerId")
        }
    },
    consumerId,      // Server-provided
    producerId,      // Server-provided
    kind,            // "audio"
    rtpParameters,   // JSON string from server
    null             // appData
)

consumer?.let {
    consumers[consumerId] = it
    it.resume() // CRITICAL: Consumer starts paused, must resume to play audio
}
```

**Volume control (lines 243-249):**
```kotlin
fun setConsumerVolume(consumerId: String, volume: Float) {
    consumers[consumerId]?.let { consumer ->
        (consumer as Consumer).track?.setVolume(volume.toDouble().coerceIn(0.0, 10.0))
    }
}
```

**Consumer statistics (add to MediasoupClient):**
```kotlin
suspend fun getConsumerStats(consumerId: String): String? = withContext(Dispatchers.IO) {
    consumers[consumerId]?.let { (it as Consumer).stats }
}
```

### Resource Cleanup Order (lines 423-458)

**Current pattern is CORRECT:**
```kotlin
// 1. Producer first
audioProducer?.close()
audioProducer = null

// 2. Consumers
consumers.values.forEach { (it as Consumer).close() }
consumers.clear()

// 3. Send transport
sendTransport?.close()
sendTransport = null

// 4. Recv transport
recvTransport?.close()
recvTransport = null

// 5. Device NEVER disposed (singleton, reused)
```

## Known Pitfalls (from Research)

### Chrome/Android Initial Codec Issue
**Problem:** First `device.load()` on Android Chrome may only expose Opus audio codec; full codec list appears after page refresh.

**Mitigation:** Not applicable to native Android app (WebView issue only).

### Device-Specific Codec Support
**Problem:** Some Android devices support H.264 decoding but not encoding.

**Mitigation:** Not applicable to audio-only app. If adding video, check `device.canProduce("video")` before creating video producer.

### RTP Capabilities Filtering
**Problem:** Some RTP header extensions cause compatibility issues.

**Mitigation:** Use server's `router.rtpCapabilities` as-is. Don't filter unless specific extension causes crashes.

### Negative Producer ID Response
**Problem:** If `onProduce()` callback doesn't return server's producer ID, `produce()` call hangs indefinitely.

**Mitigation:** Already handled in stub (lines 310-318) - throw exception if no ID in response.

### AudioTrack Disposal Before Producer Close
**Problem:** Disposing WebRTC AudioTrack while Producer still references it causes native crash.

**Mitigation:** Close producer first (line 427-431), then dispose AudioTrack (if manually managed).

## Competitor Feature Analysis

| Feature | Zello | TeamSpeak Mobile | Our Approach (VoicePing) |
|---------|-------|------------------|--------------------------|
| Audio codec | Opus (proprietary modifications) | Opus/Speex | Opus via mediasoup (standard WebRTC) |
| PTT latency optimization | Optimistic sending (starts before grant) | Server confirmation required | Server confirmation (PttState.Requesting) - prevents wasted bandwidth |
| Volume control | Per-channel volume in UI | Master volume only | Per-consumer volume via AudioTrack.setVolume() |
| Echo cancellation | Proprietary AEC | Standard WebRTC AEC | WebRTC MediaConstraints (googEchoCancellation) |
| Audio routing | Auto-switch to earpiece/speaker/BT | Manual selection | AudioRouter.kt (already implemented) |
| Network quality indicator | Real-time bitrate/latency display | Connection bars only | Consumer.getStats() → NetworkQualityIndicator.kt |

## Sources

**API Documentation (HIGH confidence):**
- [mediasoup libmediasoupclient API](https://mediasoup.org/documentation/v3/libmediasoupclient/api/) - Device, Transport, Producer, Consumer lifecycle
- [mediasoup RTP Parameters and Capabilities](https://mediasoup.org/documentation/v3/mediasoup/rtp-parameters-and-capabilities/) - Opus codec configuration

**Library Repositories (MEDIUM confidence):**
- [crow-misia/libmediasoup-android](https://github.com/crow-misia/libmediasoup-android) - Maven Central library
- [haiyangwu/mediasoup-client-android](https://github.com/haiyangwu/mediasoup-client-android) - Example implementations

**WebRTC AudioTrack API (HIGH confidence):**
- [org.webrtc.AudioTrack setVolume() documentation](https://getstream.github.io/webrtc-android/stream-webrtc-android/org.webrtc/-audio-track/set-volume.html)
- [WebRTC Android PeerConnectionFactory examples](https://www.tabnine.com/code/java/classes/org.webrtc.AudioTrack)

**Community Best Practices (LOW confidence - needs verification):**
- [mediasoup Device.load() error handling](https://github.com/versatica/mediasoup-client/issues/120)
- [mediasoup SendTransport callbacks discussion](https://mediasoup.discourse.group/t/libmediasoupclient-mysendtransportlistener-onconnect-and-onproduce-events-not-firing/1151)
- [WebRTC Android audio constraints](https://groups.google.com/g/discuss-webrtc/c/SM7p5qzl_ZQ)

---
*Feature research for: libmediasoup-android integration in VoicePing PTT*
*Researched: 2026-02-13*
*Confidence: MEDIUM (API patterns verified via official docs, implementation details from community sources)*
