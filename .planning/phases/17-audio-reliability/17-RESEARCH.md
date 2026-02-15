# Phase 17: Audio Reliability - Research

**Researched:** 2026-02-15
**Domain:** WebRTC audio reliability, mediasoup transport/producer lifecycle, Opus codec configuration
**Confidence:** MEDIUM

## Summary

Phase 17 hardens audio reliability by adding producer retry logic, transport health monitoring, Opus codec tuning, and server acknowledgment for PTT transmissions. The existing architecture already has solid foundations: mediasoup-client 0.21.0 with crow-misia wrapper, transport connection state callbacks, and Opus codec configuration support. The primary implementation gaps are: (1) retry logic with exponential backoff and user feedback, (2) WebRTC jitter buffer tuning (limited Android API access), (3) transport failure recovery automation, and (4) server-side ACK mechanism for transmission confirmation.

Key finding: WebRTC's jitter buffer is not directly configurable via crow-misia/libmediasoup-android API. Jitter buffer optimization happens through Opus codec parameters (FEC, DTX, ptime, bitrate) and transport-level bandwidth estimation, not via explicit buffer size controls. Android's WebRTC implementation uses adaptive jitter buffer (NetEQ) that auto-tunes based on network conditions.

**Primary recommendation:** Focus implementation on producer creation retry logic with exponential backoff, Opus FEC/DTX tuning via existing codecOptions API, transport connection state monitoring with automated recovery, and server-side producer acknowledgment via existing request-response pattern. Defer explicit jitter buffer tuning (not accessible via API) in favor of codec-level optimizations that indirectly improve buffer behavior.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Retry Feedback:**
- PTT auto-releases on producer creation failure (no hold-during-retry)
- Toast notification "Unable to transmit. Check your connection." during retry — user-friendly only, no technical detail
- 2 retry attempts with exponential backoff before giving up
- Retry applies to all PTT sources equally (on-screen, volume keys, Bluetooth)
- Audio captured during retry window is buffered and sent once producer succeeds
- No cooldown after failure — user can re-press PTT immediately
- Distinct haptic pattern (double-buzz) on failed PTT release vs normal release
- New PttState.Error state (separate from PttState.Denied) with failure reason
- All retry attempts, backoff timing, and outcomes logged to Logcat at debug level

**Transmission Confirmation:**
- Green flash (300ms) on PTT button after successful server ACK — always, regardless of PTT source
- Red flash (300ms) on PTT button when server ACK not received within 2 seconds
- Separate "Confirmation tone" toggle in settings (independent of existing roger beep toggle)
- Confirmation tone is a variation of the existing roger beep: normal pitch = success, lower pitch = failure
- Server ACK level: server acknowledges receipt of audio stream (not end-to-end listener confirmation)

**Failure Behavior:**
- Mid-transmission transport failure: hold PTT state for ~2 seconds attempting transport reconnect, then release if fails
- Orphaned transport cleanup (15s disconnect): silent, no user-facing indicator
- Incoming audio (RX) stream failure: silent auto-recovery in background
- Full disconnect (both transports fail): auto-rejoin with exponential backoff
- 5 auto-rejoin attempts max, then persistent "Unable to connect" banner with manual "Retry" button
- PTT disabled (grayed out) during auto-rejoin attempts
- Partial operation supported: if only send transport fails, user can still hear others
- Amber PTT button during partial failure (send broken, receive working)
- Tapping amber PTT shows toast: "Transmit unavailable — reconnecting..."
- PTT auto-recovers to normal state when send transport reconnects (no toast, silent transition)

**Audio Quality Tuning:**
- Balanced approach: default to low latency, adapt toward clarity on poor networks
- Target end-to-end latency: under 500ms (standard two-way radio feel)
- Audio quality settings fixed by app — no user-facing controls
- Graceful degradation on poor networks: reduce bitrate, increase FEC redundancy (choppy audio better than no audio)
- Opus DTX disabled — continuous stream during PTT hold (comfort noise, smoother listening)
- Opus FEC always enabled — proactive protection, no gap when loss starts
- Adaptive jitter buffer: start small, grow on packet loss/jitter, shrink when network improves
- Audio quality metrics (jitter, packet loss, RTT) logged to Logcat AND exposed via hidden developer stats screen in settings

### Claude's Discretion

None — all implementation details locked by user decisions.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| io.github.crow-misia.libmediasoup-android | 0.21.0 | mediasoup-client wrapper for Android | Official Android binding for mediasoup, actively maintained, bundles native WebRTC |
| org.webrtc (bundled) | ~ | WebRTC native implementation | Industry-standard real-time communication, powers mediasoup transport layer |
| Kotlin Coroutines | 1.9.0+ | Async/await for retry logic and backoff timers | Standard Android async pattern, needed for suspend functions and delay() |
| Gson | 2.11.0 | JSON parsing for RTC stats | Already in project, lightweight, fast |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-coroutines-test | 1.9.0+ | Unit testing retry logic | Test exponential backoff timing without real delays |
| MockK | 1.13.0+ | Mocking mediasoup APIs | Test producer creation failure scenarios |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| mediasoup-client | Jitsi Meet SDK | Jitsi has built-in jitter buffer tuning but entire SFU stack replacement required, huge migration cost |
| Exponential backoff (manual) | Kotlin Retry library | Adds dependency for simple retry logic, manual implementation more transparent |
| Server ACK via request-response | Custom WebSocket ping-pong | Existing SignalingClient.request() already provides correlation IDs and timeout, no need to rebuild |

**Installation:**

Already installed — no new dependencies required. Existing stack covers all needs:
```kotlin
// Already in android/app/build.gradle.kts:
implementation("io.github.crow-misia.libmediasoup-android:libmediasoup-android:0.21.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
implementation("com.google.code.gson:gson:2.11.0")
```

## Architecture Patterns

### Recommended Project Structure

Existing structure already fits Phase 17 needs:
```
android/app/src/main/java/com/voiceping/android/
├── data/
│   ├── ptt/
│   │   └── PttManager.kt              # Add retry logic, Error state, buffering
│   ├── network/
│   │   ├── MediasoupClient.kt         # Add stats collection, Opus FEC/DTX tuning
│   │   └── SignalingClient.kt         # Already has request() for ACK pattern
│   ├── audio/
│   │   ├── TonePlayer.kt              # Add confirmation tone variants (pitch shift)
│   │   └── HapticFeedback.kt          # Add double-buzz error pattern
│   └── storage/
│       └── SettingsRepository.kt      # Add confirmation tone toggle
├── domain/model/
│   └── PttState.kt                    # Add Error(reason: String) state
└── presentation/
    └── channels/
        ├── ChannelListViewModel.kt    # Handle ACK timeouts, visual flash
        └── components/
            └── PttButtonKt.kt         # Green/red/amber states
```

### Pattern 1: Producer Creation with Exponential Backoff Retry

**What:** Wrap MediasoupClient.startProducing() in retry logic with user feedback

**When to use:** On producer creation failure (transport not ready, network hiccup, server error)

**Example:**
```kotlin
// In PttManager.kt
suspend fun requestPtt(channelId: String) {
    // ... existing state check and server PTT_START request ...

    // Producer creation with retry
    var attempt = 0
    val maxAttempts = 2
    var lastError: Exception? = null

    while (attempt <= maxAttempts) {
        try {
            if (attempt > 0) {
                val delayMs = (2.0.pow(attempt - 1) * 1000).toLong()
                Log.d(TAG, "Retry attempt $attempt after ${delayMs}ms")
                delay(delayMs)
                // Show toast: "Unable to transmit. Check your connection."
                onRetryAttempt?.invoke()
            }

            // Create transport if needed
            mediasoupClient.createSendTransport(channelId)

            // Start producing - THIS is the operation we're retrying
            mediasoupClient.startProducing()

            // SUCCESS - producer created
            Log.d(TAG, "Producer created on attempt ${attempt + 1}")
            onPttGranted?.invoke() // Visual + tone + haptic feedback
            return

        } catch (e: Exception) {
            lastError = e
            Log.w(TAG, "Producer creation failed (attempt ${attempt + 1}): ${e.message}")
            attempt++
        }
    }

    // FAILURE - all retries exhausted
    Log.e(TAG, "Producer creation failed after $maxAttempts retries: ${lastError?.message}")
    _pttState.value = PttState.Error(lastError?.message ?: "Unknown error")
    onPttError?.invoke() // Double-buzz haptic + red flash + error tone

    // Auto-release PTT state (user decision: no hold-during-retry)
    _pttState.value = PttState.Idle
}
```

**Source:** Based on exponential backoff pattern from [Managing Retry Logic with Exponential Backoff](https://medium.com/@linz07m/managing-retry-logic-with-exponential-backoff-44d370e38df8) adapted for mediasoup producer creation.

### Pattern 2: Server ACK for Transmission Confirmation

**What:** Use existing SignalingClient.request() correlation ID pattern to confirm producer creation server-side

**When to use:** After startProducing() succeeds locally, verify server received the producer

**Example:**
```kotlin
// In MediasoupClient.kt startProducing()
suspend fun startProducing(): String {
    // ... existing producer creation code ...

    val producer = transport.produce(/* ... */)
    audioProducer = producer

    // Producer ID returned from transport.produce() onProduce callback
    val producerId = producer.id
    Log.d(TAG, "Audio producer started: $producerId")

    return producerId // Return to caller for ACK verification
}

// In PttManager.kt
suspend fun requestPtt(channelId: String) {
    // ... retry logic ...

    val producerId = mediasoupClient.startProducing()

    // Wait for server ACK with timeout
    withTimeout(2000) { // 2 second timeout per user decision
        try {
            val ackResponse = signalingClient.request(
                SignalingType.PRODUCER_ACK,
                mapOf("producerId" to producerId, "channelId" to channelId)
            )

            if (ackResponse.error == null) {
                // SUCCESS - server confirmed audio stream received
                onServerAck?.invoke() // Green flash + confirmation tone (normal pitch)
            } else {
                // Server ACK failed
                Log.w(TAG, "Server ACK error: ${ackResponse.error}")
                onAckFailure?.invoke() // Red flash + confirmation tone (low pitch)
            }
        } catch (e: TimeoutCancellationException) {
            // ACK timeout (2 seconds elapsed)
            Log.w(TAG, "Server ACK timeout")
            onAckFailure?.invoke() // Red flash + confirmation tone (low pitch)
        }
    }
}
```

**Source:** Existing SignalingClient.request() pattern from codebase, extended for producer acknowledgment.

### Pattern 3: Transport Connection State Monitoring with Auto-Recovery

**What:** Monitor SendTransport.onConnectionStateChange() and auto-recover on "disconnected" state

**When to use:** Detect mid-transmission transport failure and attempt reconnection before giving up

**Example:**
```kotlin
// In MediasoupClient.kt createSendTransport()
override fun onConnectionStateChange(transport: Transport, newState: String) {
    when (newState) {
        "disconnected" -> {
            // ICE connectivity lost — WebRTC will attempt auto-recovery (~15s window)
            // Hold PTT state for ~2 seconds per user decision
            Log.w(TAG, "SendTransport disconnected, holding PTT state for recovery")
            scope.launch {
                delay(2000) // 2 second grace period

                if (transport.connectionState == "disconnected") {
                    // Still disconnected after grace period - force release PTT
                    Log.e(TAG, "SendTransport recovery failed, releasing PTT")
                    pttManager.forceReleasePtt() // Calls onPttInterrupted callback
                }
            }
        }
        "failed" -> {
            // Auto-recovery failed — manual cleanup required
            Log.e(TAG, "SendTransport failed, cleaning up producer and transport")
            audioProducer?.close()
            audioProducer = null
            cleanupAudioResources()
            sendTransport = null

            // Trigger amber PTT state (send broken, receive still working if RecvTransport OK)
            onSendTransportFailed?.invoke()
        }
        "connected" -> {
            Log.d(TAG, "SendTransport (re)connected")
            // Auto-recover to normal PTT state per user decision (silent, no toast)
            onSendTransportRecovered?.invoke()
        }
    }
}
```

**Source:** Based on [WebRTC State Machines](https://www.giacomovacca.com/2026/02/understanding-webrtc-state-machines.html) and [WebRTC Reconnection for Mobile](https://webrtc.ventures/2023/06/implementing-a-reconnection-mechanism-for-webrtc-mobile-applications/) patterns.

### Pattern 4: Opus Codec Optimization for PTT

**What:** Configure Opus codec via MediasoupClient codecOptions for low latency + packet loss resilience

**When to use:** During producer creation (already in codebase at MediasoupClient.startProducing())

**Example:**
```kotlin
// In MediasoupClient.kt startProducing() — MODIFY EXISTING CONFIG
val codecOptions = com.google.gson.JsonObject().apply {
    addProperty("opusStereo", false)         // Mono for lower bitrate
    addProperty("opusDtx", false)            // CHANGE: DTX disabled per user decision (continuous stream)
    addProperty("opusFec", true)             // ALREADY SET: FEC enabled for packet loss recovery
    addProperty("opusMaxPlaybackRate", 48000) // 48kHz playback
    addProperty("opusPtime", 20)              // 20ms packet time

    // NEW: Bitrate tuning for graceful degradation
    addProperty("maxBitrate", 40000)         // 40kbps max (high quality)
    addProperty("minBitrate", 16000)         // 16kbps min (degrade to this on poor network)

    // NEW: Packet loss expectation (activates FEC encoder)
    addProperty("packetLossPercentage", 10)  // Expect 10% loss, FEC compensates
}
```

**Key tuning rationale:**
- **opusDtx: false** — Continuous stream during PTT hold (comfort noise, smoother listening) per user decision
- **opusFec: true** — Always enabled for proactive protection (no gap when loss starts)
- **packetLossPercentage: 10** — Tells Opus encoder to expect packet loss, activates FEC redundancy
- **minBitrate: 16kbps** — Graceful degradation on poor networks (choppy audio better than no audio)

**Source:** [Opus FEC Configuration for WebRTC](https://ddanilov.me/how-to-enable-in-band-fec-for-opus-codec/) and [WebRTC Media Resilience](https://getstream.io/resources/projects/webrtc/advanced/media-resilience/).

### Pattern 5: Audio Quality Metrics Collection

**What:** Parse WebRTC RTCStatsReport from Consumer.getStats() for jitter, packet loss, RTT

**When to use:** Periodic polling (every 5-15 seconds) for active consumers, log to Logcat + expose in dev stats screen

**Example:**
```kotlin
// In MediasoupClient.kt
suspend fun getConsumerStats(consumerId: String): ConsumerNetworkStats? = withContext(Dispatchers.IO) {
    consumers[consumerId]?.let { consumer ->
        try {
            // Consumer.stats property returns RTCStatsReport (type unknown in crow-misia docs)
            // Assumption: Returns JSON string similar to device.rtpCapabilities
            val statsJson = consumer.stats as? String ?: return@withContext null

            // Parse JSON to extract inbound-rtp metrics
            val statsObj = JsonParser.parseString(statsJson).asJsonObject

            // RTCStatsReport format: { "inbound-rtp": { "packetsLost": N, "jitter": J, ... } }
            val inboundRtp = statsObj.getAsJsonObject("inbound-rtp")
                ?: return@withContext null

            val packetsLost = inboundRtp.get("packetsLost")?.asInt ?: 0
            val jitter = inboundRtp.get("jitter")?.asDouble ?: 0.0
            val packetsReceived = inboundRtp.get("packetsReceived")?.asInt ?: 0

            // Calculate quality indicator
            val lossRate = if (packetsReceived > 0) {
                packetsLost.toDouble() / packetsReceived
            } else 0.0

            val indicator = when {
                lossRate < 0.02 && jitter < 0.030 -> "Good"    // <2% loss, <30ms jitter
                lossRate < 0.05 && jitter < 0.060 -> "Fair"    // <5% loss, <60ms jitter
                else -> "Poor"                                  // >=5% loss or >=60ms jitter
            }

            Log.d(TAG, "Consumer stats: loss=$packetsLost, jitter=${jitter}ms, indicator=$indicator")

            return@withContext ConsumerNetworkStats(
                packetsLost = packetsLost,
                jitter = jitter,
                packetsReceived = packetsReceived,
                indicator = indicator
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse consumer stats: $consumerId", e)
            null
        }
    }
}
```

**CAVEAT:** crow-misia library documentation does not specify Consumer.stats return type. This pattern assumes JSON string format similar to device.rtpCapabilities. Plan 17-02 MUST validate actual return type on device during implementation.

**Source:** [WebRTC NetEQ Jitter Buffer](https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/) and WebRTC RTCStatsReport spec.

### Anti-Patterns to Avoid

- **Don't retry indefinitely:** Cap retry attempts (2 retries per user decision) to prevent infinite loops and battery drain
- **Don't show technical errors to users:** Toast must be user-friendly ("Unable to transmit. Check your connection.") not "Producer.onProduce() threw IllegalStateException"
- **Don't modify jitter buffer directly:** WebRTC's NetEQ jitter buffer is adaptive and not exposed via crow-misia API. Tune codec parameters (FEC, bitrate, ptime) instead
- **Don't disable FEC on good networks:** Always-on FEC (user decision) prevents audio gaps when network suddenly degrades mid-transmission
- **Don't block UI thread:** All retry logic, stats collection, and transport monitoring must run on IO dispatcher (Kotlin coroutines)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Exponential backoff timing | Custom delay calculation with Thread.sleep() | Kotlin Coroutines `delay()` with 2^n formula | Coroutine-aware, cancellable, testable with virtual time |
| Request-response correlation | Custom message ID tracking | Existing SignalingClient.request() | Already implements UUID correlation, timeout, CompletableDeferred |
| WebRTC stats parsing | Custom binary parser for RTC stats | Gson JSON parsing | RTCStatsReport is JSON, Gson already in project |
| Transport state machine | Manual state tracking flags | WebRTC Transport.connectionState property | WebRTC ICE state machine handles complexity (new → connecting → connected → disconnected → failed) |
| Jitter buffer tuning | Custom audio buffer implementation | Opus codec params + WebRTC NetEQ defaults | NetEQ is production-tested adaptive jitter buffer, hand-rolled buffers risk glitches |

**Key insight:** WebRTC and mediasoup have battle-tested implementations for complex audio problems (jitter buffering, packet loss recovery, ICE reconnection). Phase 17 should wire existing mechanisms together with retry/recovery logic, not replace them.

## Common Pitfalls

### Pitfall 1: Producer Creation Race Condition During Retry

**What goes wrong:** User releases PTT while retry logic is sleeping between attempts, late-arriving producer becomes orphaned

**Why it happens:** Retry logic runs in background coroutine while user can interact with PTT button asynchronously

**How to avoid:**
1. Check PTT state at start of each retry iteration (skip attempt if no longer in Requesting/Transmitting state)
2. Use coroutine Job cancellation to abort retry when PTT state transitions to Idle
3. Close any orphaned producer after retry loop exits

**Warning signs:**
- Log shows "Producer created" but user already released PTT
- Audio plays from wrong user after channel switch
- Native memory leak from unclosed Producer

**Code example:**
```kotlin
val retryJob = scope.launch {
    while (attempt <= maxAttempts && _pttState.value is PttState.Requesting) {
        // State check prevents retry after user cancellation
        try {
            mediasoupClient.startProducing()
            return@launch
        } catch (e: Exception) {
            if (!isActive) return@launch // Job cancelled, abort retry
            attempt++
            delay(backoff)
        }
    }
}

// When user releases PTT: retryJob.cancel()
```

### Pitfall 2: ACK Timeout Fires After Producer Already Closed

**What goes wrong:** 2-second ACK timeout elapses, triggers red flash + error tone, but producer is already closed (user released PTT)

**Why it happens:** ACK timeout coroutine runs independently of PTT state lifecycle

**How to avoid:**
1. Cancel ACK timeout Job when PTT state transitions to Idle
2. Check PTT state before triggering ACK failure feedback (skip if no longer transmitting)
3. Use structured concurrency to tie ACK timeout lifetime to PTT transmission scope

**Warning signs:**
- Red flash appears after user already released PTT
- Error tone plays when transmission was successful
- Logcat shows "ACK timeout" but producer closed 1 second earlier

### Pitfall 3: Opus FEC Not Activating Despite opusFec: true

**What goes wrong:** FEC enabled in codec options but FEC redundancy not present in RTP packets (verified via server-side stats)

**Why it happens:** Opus FEC encoder requires packetLossPercentage > 0 to activate (threshold: 12.4kbps bitrate minimum)

**How to avoid:**
1. Set packetLossPercentage explicitly (10-15% recommended for mobile networks)
2. Ensure maxBitrate >= 16kbps (below 12.4kbps, FEC disabled by Opus encoder)
3. Test FEC activation via server-side RTP packet inspection (check for redundancy payload)

**Warning signs:**
- Audio drops on lossy networks despite FEC enabled
- Server RTP stats show no FEC packets received
- Opus encoder logs "FEC disabled due to low bitrate"

**Source:** [Opus FEC Requirements](https://ddanilov.me/how-to-enable-in-band-fec-for-opus-codec/) - FEC needs ptime >= 10ms AND bitrate >= 12.4kbps AND packetLossPercentage > 0.

### Pitfall 4: Transport "disconnected" State Confusion

**What goes wrong:** Transport enters "disconnected" state and immediately triggers PTT release, but ICE recovers 500ms later

**Why it happens:** "disconnected" is transient (ICE gathering candidates, network blip), "failed" is terminal

**How to avoid:**
1. Add grace period (2 seconds per user decision) after "disconnected" before releasing PTT
2. Monitor for transition back to "connected" during grace period
3. Only force-release PTT if state is still "disconnected" after grace period expires
4. Treat "failed" state as immediate terminal failure (no grace period)

**Warning signs:**
- PTT releases during brief network blips
- Transport reconnects immediately after PTT force-released
- User experiences "flaky" PTT that releases unexpectedly

**Source:** [Transport Connection State - mediasoup](https://mediasoup.discourse.group/t/transport-connectionstate-never-changes-to-failed-stuck-on-disconnected/4125) - "disconnected" is transient, "failed" is terminal.

### Pitfall 5: Jitter Buffer "Configuration" Myth

**What goes wrong:** Attempt to configure WebRTC jitter buffer size via Android API or crow-misia library

**Why it happens:** Documentation and tutorials reference jitter buffer tuning, but Android WebRTC implementation doesn't expose buffer controls

**How to avoid:**
1. Accept that WebRTC NetEQ jitter buffer is adaptive and NOT directly configurable on Android
2. Tune jitter buffer behavior INDIRECTLY via Opus codec params (ptime, bitrate, FEC)
3. Use smaller ptime (20ms) for lower latency, larger ptime (40-60ms) for more jitter tolerance
4. Log audio quality metrics (jitter from RTCStatsReport) to verify buffer effectiveness

**Warning signs:**
- Searching for "setJitterBufferSize()" or similar API (doesn't exist)
- Attempting to modify PeerConnectionFactory.Options (no jitter buffer fields)
- Expecting explicit buffer size control like AudioTrack.setBufferSizeInFrames()

**Source:** [WebRTC Jitter Buffer](https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/) - NetEQ is adaptive, controlled by codec params not explicit API.

## Code Examples

Verified patterns from official sources and existing codebase:

### Exponential Backoff Retry Loop
```kotlin
// Source: Existing SignalingClient.kt scheduleReconnect() pattern adapted for producer retry
suspend fun retryOperation(maxAttempts: Int, operation: suspend () -> Unit) {
    var attempt = 0
    var lastException: Exception? = null

    while (attempt <= maxAttempts) {
        try {
            if (attempt > 0) {
                val delayMs = (2.0.pow(attempt - 1) * 1000).toLong()
                Log.d(TAG, "Retry attempt $attempt after ${delayMs}ms")
                delay(delayMs)
            }

            operation()
            lastException = null
            break

        } catch (e: Exception) {
            lastException = e
            Log.w(TAG, "Operation failed (attempt ${attempt + 1}/${maxAttempts + 1}): ${e.message}")
            attempt++
        }
    }

    if (lastException != null) {
        throw lastException!!
    }
}
```

### Server ACK Request-Response Pattern
```kotlin
// Source: Existing SignalingClient.kt request() pattern
// Server handler to add (in src/server/signaling/handlers.ts):
async handleProducerAck(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    const { producerId, channelId } = message.data as { producerId: string; channelId: string };

    // Verify producer exists and belongs to this user
    const producer = this.producerConsumerManager.getProducer(producerId);
    if (producer && producer.appData.userId === ctx.userId) {
        this.sendResponse(ctx, message.id, { success: true });
        logger.debug(`Producer ${producerId} acknowledged for ${ctx.userId}`);
    } else {
        this.sendError(ctx, message.id, 'Producer not found or unauthorized');
    }
}
```

### Opus Codec Configuration
```kotlin
// Source: Existing MediasoupClient.kt startProducing() with user decision overrides
val codecOptions = com.google.gson.JsonObject().apply {
    addProperty("opusStereo", false)
    addProperty("opusDtx", false)              // CHANGED: DTX disabled per user decision
    addProperty("opusFec", true)               // UNCHANGED: FEC enabled
    addProperty("opusMaxPlaybackRate", 48000)
    addProperty("opusPtime", 20)

    // NEW ADDITIONS for Phase 17:
    addProperty("maxBitrate", 40000)           // 40kbps max
    addProperty("minBitrate", 16000)           // 16kbps min for degradation
    addProperty("packetLossPercentage", 10)    // Activate FEC encoder
}
```

### Transport Connection State Monitoring
```kotlin
// Source: Existing MediasoupClient.kt SendTransport.Listener pattern
override fun onConnectionStateChange(transport: Transport, newState: String) {
    when (newState) {
        "disconnected" -> {
            Log.w(TAG, "SendTransport disconnected, starting grace period")
            _transportState.value = TransportState.RECOVERING

            scope.launch {
                delay(2000) // 2 second grace period per user decision

                if (transport.connectionState == "disconnected") {
                    Log.e(TAG, "Transport still disconnected after grace period, force release")
                    _transportState.value = TransportState.FAILED
                    onRecoveryFailed?.invoke()
                }
            }
        }
        "connected" -> {
            Log.d(TAG, "SendTransport connected/recovered")
            _transportState.value = TransportState.CONNECTED
            onRecovered?.invoke()
        }
        "failed" -> {
            Log.e(TAG, "SendTransport failed (terminal)")
            _transportState.value = TransportState.FAILED
            onTerminalFailure?.invoke()
        }
    }
}
```

### Confirmation Tone Pitch Variation
```kotlin
// Source: Existing TonePlayer.kt pattern extended for pitch variation
fun playConfirmationTone(success: Boolean) {
    try {
        if (settingsRepository.getCachedConfirmationToneEnabled()) {
            // Success: DTMF_0 (normal roger beep pitch)
            // Failure: DTMF_7 (lower pitch - 1209 Hz + 852 Hz vs 1336 Hz + 941 Hz)
            val tone = if (success) ToneGenerator.TONE_DTMF_0 else ToneGenerator.TONE_DTMF_7
            val duration = 150 // Same duration, different pitch
            toneGenerator?.startTone(tone, duration)
            Log.d(TAG, "Playing confirmation tone: ${if (success) "success" else "failure"}")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error playing confirmation tone", e)
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual jitter buffer tuning | Adaptive NetEQ (WebRTC default) | WebRTC M85+ (2020) | Jitter buffer auto-adjusts to network conditions, explicit tuning not needed or exposed |
| Opus DTX always on | Configurable via opusDtx | mediasoup-client v3+ (2019) | PTT apps can disable DTX for continuous stream (comfort noise, no silence suppression gaps) |
| Producer retry via app logic | No built-in retry in mediasoup | Still current (2026) | Apps must implement retry logic themselves, mediasoup focuses on WebRTC layer not app UX |
| Server-side producer tracking | Producer.appData for metadata | mediasoup v3+ (2018) | Easier to track userId/channelId per producer for ACK verification |
| Ice connection state only | Separate DTCPtransport state | WebRTC Unified Plan (2019) | More granular state machine (disconnected vs failed distinction) |

**Deprecated/outdated:**
- **opusDtx default: true** — Older PTT tutorials enable DTX by default, but Phase 17 user decision disables it for continuous stream (comfort noise)
- **Manual ICE restart on disconnect** — Old WebRTC apps called restartIce() immediately on disconnect, modern pattern waits for auto-recovery grace period
- **Jitter buffer size configuration** — Ancient WebRTC APIs exposed buffer size, modern Android WebRTC uses NetEQ with no direct control

## Open Questions

### 1. **crow-misia Consumer.stats Return Type**
   - **What we know:** mediasoup-client docs say getStats() returns RTCStatsReport, but crow-misia Kotlin API type is undocumented
   - **What's unclear:** Does Consumer.stats return String (JSON), RTCStatsReport object, or something else?
   - **Recommendation:** Plan 17-02 MUST test Consumer.stats on device and log result type. If String, parse JSON. If object, iterate properties. Have fallback to stub stats if parsing fails.

### 2. **Server-Side Producer Acknowledgment Timing**
   - **What we know:** Producer.onProduce callback confirms transport.produce() succeeded, server creates Producer object
   - **What's unclear:** Does onProduce guarantee audio RTP packets flowing, or just that Producer created? Latency between onProduce and first RTP packet?
   - **Recommendation:** Server ACK should respond to PRODUCER_ACK message AFTER onProduce callback completes (confirms server has Producer object). For true RTP-level ACK, server would need to wait for first RTP packet received, but this adds latency. User decision says "server acknowledges receipt of audio stream" - interpret as "Producer created" not "first packet received".

### 3. **Opus Bitrate Adaptation Speed**
   - **What we know:** WebRTC bandwidth estimation adjusts Opus bitrate dynamically based on network conditions
   - **What's unclear:** How fast does bitrate adapt? Does it degrade gracefully or drop suddenly? Can we configure adaptation aggressiveness?
   - **Recommendation:** Use default WebRTC bandwidth estimation (no config exposed). Monitor via Producer.getStats() bitrate field if available. Graceful degradation happens automatically via minBitrate: 16000 setting.

### 4. **Transport Grace Period Cancellation**
   - **What we know:** User decision says "hold PTT state for ~2 seconds attempting transport reconnect"
   - **What's unclear:** If transport reconnects during grace period, should we cancel the timeout Job immediately or let it run to completion?
   - **Recommendation:** Cancel grace period timeout immediately when transport.connectionState returns to "connected". Prevents unnecessary PTT release if recovery happens at 1.5 seconds.

## Sources

### Primary (HIGH confidence)

- **crow-misia/libmediasoup-android GitHub** - https://github.com/crow-misia/libmediasoup-android - Library structure and version info
- **mediasoup-client API Documentation** - https://mediasoup.org/documentation/v3/mediasoup-client/api/ - Producer/Consumer/Transport API reference
- **Existing Codebase** - android/app/src/main/java/com/voiceping/android/data/* - PttManager, MediasoupClient, SignalingClient patterns
- **Existing Server Code** - src/server/signaling/handlers.ts - Producer creation flow and request-response pattern

### Secondary (MEDIUM confidence)

- **WebRTC State Machines** (2026) - https://www.giacomovacca.com/2026/02/understanding-webrtc-state-machines.html - Connection state transitions
- **How WebRTC's NetEQ Jitter Buffer Provides Smooth Audio** - https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/ - Jitter buffer fundamentals
- **WebRTC Media Resilience** - https://getstream.io/resources/projects/webrtc/advanced/media-resilience/ - FEC and packet loss recovery
- **How to enable in-band FEC for Opus codec** - https://ddanilov.me/how-to-enable-in-band-fec-for-opus-codec/ - Opus FEC configuration requirements
- **Implementing A Reconnection Mechanism for WebRTC Mobile Applications** - https://webrtc.ventures/2023/06/implementing-a-reconnection-mechanism-for-webrtc-mobile-applications/ - Android reconnection patterns
- **Managing Retry Logic with Exponential Backoff** - https://medium.com/@linz07m/managing-retry-logic-with-exponential-backoff-44d370e38df8 - Retry pattern best practices

### Tertiary (LOW confidence, needs validation)

- **WebRTC Low Latency Guide** - https://www.videosdk.live/developer-hub/webrtc/webrtc-low-latency - Latency optimization strategies (generic, not PTT-specific)
- **Transport connectionState stuck on disconnected** - https://mediasoup.discourse.group/t/transport-connectionstate-never-changes-to-failed-stuck-on-disconnected/4125 - Community issue (anecdotal, not official guidance)

## Metadata

**Confidence breakdown:**
- **Standard stack:** HIGH - All libraries already in project, versions confirmed from build.gradle.kts
- **Architecture patterns:** MEDIUM - Retry/ACK patterns based on existing codebase structure and mediasoup docs, but Consumer.stats parsing needs device validation
- **Opus FEC/DTX config:** MEDIUM - mediasoup API confirmed, but packetLossPercentage threshold from secondary source needs testing
- **Jitter buffer tuning:** HIGH - Confirmed NOT directly configurable via crow-misia API, must tune via codec params
- **Pitfalls:** MEDIUM - Based on WebRTC documentation and community patterns, but specific crow-misia behavior needs device testing

**Research date:** 2026-02-15
**Valid until:** 2026-03-15 (30 days - WebRTC/mediasoup APIs stable, crow-misia active but slow release cycle)
