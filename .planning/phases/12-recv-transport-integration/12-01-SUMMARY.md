---
phase: 12-recv-transport-integration
plan: 01
subsystem: android-mediasoup-integration
tags: [recv-transport, consumer, webrtc, audio-playback, volume-control]
dependency_graph:
  requires:
    - "11-02: Device RTP capabilities and PeerConnectionFactory"
    - "Server: CREATE_TRANSPORT, CONSUME, CONNECT_TRANSPORT signaling endpoints"
  provides:
    - "RecvTransport per channel with DTLS connection handling"
    - "Consumer lifecycle with resume, volume control, cleanup"
    - "Per-channel audio receive path ready for multi-channel monitoring"
  affects:
    - "ChannelRepository: consumeAudio signature changed to include channelId"
    - "MediasoupClient: recvTransport changed from single to per-channel map"
tech_stack:
  added:
    - library: "libmediasoup-android 0.21.0 (crow-misia)"
      components: ["RecvTransport", "Consumer", "Transport.Listener"]
      purpose: "WebRTC receive audio path"
    - library: "org.webrtc:AudioTrack"
      purpose: "Per-consumer volume control (0.0-10.0 range)"
  patterns:
    - "runBlocking bridge for native JNI thread callbacks to Kotlin suspend functions"
    - "Per-channel RecvTransport map for multi-channel monitoring"
    - "Consumer.resume() immediately after creation for audio playback"
    - "Ordered cleanup: consumers first, then transports"
key_files:
  created: []
  modified:
    - path: "android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt"
      lines_changed: 106
      significance: "Core receive audio implementation — RecvTransport, Consumer, volume control"
    - path: "android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt"
      lines_changed: 6
      significance: "Updated consumeAudio callsites to pass channelId, added cleanupChannel call"
decisions:
  - id: "12-01-D1"
    question: "How to bridge native JNI callbacks to Kotlin suspend functions?"
    decision: "Use runBlocking without dispatcher in onConnect callback"
    rationale: "Transport callbacks run on native threads (not coroutine context). runBlocking blocks the native thread for 50-200ms during one-time DTLS handshake, acceptable for connection setup. Alternative of posting to dispatcher adds complexity and latency for this critical path."
    alternatives:
      - "Post to Dispatchers.IO via GlobalScope.launch — adds latency and complexity"
      - "Use callback-based signaling client — breaks existing suspend-based API"
  - id: "12-01-D2"
    question: "Single RecvTransport or per-channel RecvTransport?"
    decision: "Per-channel RecvTransport map (recvTransports: Map<String, RecvTransport>)"
    rationale: "Multi-channel monitoring requires independent transport lifecycle per channel. Allows channels to be joined/left independently without affecting other channels. Matches server-side architecture where transports are per-channel."
    alternatives:
      - "Single RecvTransport with all consumers — doesn't support independent channel lifecycle"
  - id: "12-01-D3"
    question: "Volume control range conversion?"
    decision: "Convert app 0.0-1.0 to WebRTC 0.0-10.0 via multiplication"
    rationale: "WebRTC AudioTrack.setVolume() uses 0.0-10.0 range (not 0.0-1.0). Simple multiplication preserves granularity. Coerce input to prevent out-of-range values."
    alternatives:
      - "Use WebRTC's default 1.0 — no per-channel volume control"
      - "Use Android AudioManager volume — wrong abstraction layer, conflicts with AudioRouter"
metrics:
  duration_seconds: 218
  completed_date: "2026-02-13"
  tasks_completed: 2
  files_modified: 2
  commits: 2
  build_verified: true
---

# Phase 12 Plan 01: RecvTransport and Consumer Integration Summary

**One-liner:** Real RecvTransport creation per channel with DTLS bridge, Consumer lifecycle with resume, and AudioTrack volume control (0-10 range)

## What Was Built

Replaced MediasoupClient RecvTransport and Consumer TODO stubs with real libmediasoup-android 0.21.0 library calls, implementing the complete receive audio path for multi-channel monitoring.

### RecvTransport Implementation

**Changed from:** `private var recvTransport: Any? = null` (single stub)
**Changed to:** `private val recvTransports = mutableMapOf<String, RecvTransport>()` (per-channel real instances)

**createRecvTransport() changes:**
- Replaced TODO block with real `device.createRecvTransport()` call
- Implemented `RecvTransport.Listener` with two callbacks:
  - `onConnect(transport, dtlsParameters)`: Bridges DTLS to signaling via `runBlocking` (blocks native JNI thread 50-200ms for one-time handshake)
  - `onConnectionStateChange(transport, newState)`: Logs state, removes failed transports from map
- Stores transport in `recvTransports[channelId]` for per-channel lifecycle management

**Key pattern:** Native JNI thread callback → runBlocking → suspend signaling request

### Consumer Implementation

**Changed from:** `private val consumers = mutableMapOf<String, Any>()` (stub)
**Changed to:** `private val consumers = mutableMapOf<String, Consumer>()` (typed)

**consumeAudio() changes:**
- **Signature:** Added `channelId` parameter, changed return type from `Unit` to `String` (consumerId)
- **CONSUME request:** Now sends `rtpCapabilities` from device for server codec validation
- Replaced TODO block with real `transport.consume()` call
- Implemented `Consumer.Listener.onTransportClose()` to remove consumer from map
- **CRITICAL:** Added `consumer.resume()` immediately after creation — consumers start paused, no audio without this call
- Returns consumerId for caller tracking

**setConsumerVolume() changes:**
- Replaced TODO with real AudioTrack volume control
- Cast `consumer.track` to `AudioTrack` (org.webrtc, not android.media)
- Convert app 0.0-1.0 range to WebRTC 0.0-10.0 range via multiplication
- Log warning if track is not AudioTrack

**closeConsumer() changes:**
- Replaced cast with typed `consumer.close()`

**New cleanupChannel() method:**
- Per-channel cleanup: removes and closes RecvTransport for specific channelId
- Called by ChannelRepository.leaveChannel() after closing consumers

### ChannelRepository Integration

**Updated consumeAudio callsites (2 locations):**
1. `observeSpeakerChangesForChannel()` line 496: Added `channelId` parameter
2. `unmuteChannel()` line 605: Added `channelId` parameter

**Updated leaveChannel():**
- Added `mediasoupClient.cleanupChannel(channelId)` after closing consumers
- Ensures RecvTransport closed before final cleanup() call (if last channel)

### Cleanup Order

**Updated cleanup() method:**
- Changed recvTransport cleanup from single var to map iteration
- Order preserved: consumers first → recv transports → device persists

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking Issue] Removed invalid createRecvTransport parameters**
- **Found during:** Task 1 compilation
- **Issue:** Plan specified `iceServers`, `peerConnectionOptions`, `appData` parameters, but crow-misia 0.21.0 API does not accept these
- **Fix:** Removed invalid parameters from createRecvTransport() call
- **Files modified:** MediasoupClient.kt line 252-254
- **Commit:** 631f2e4

**2. [Rule 3 - Blocking Issue] Fixed parameter name in onConnectionStateChange**
- **Found during:** Task 1 compilation warning
- **Issue:** Compiler warning: "The corresponding parameter in the supertype 'Listener' is named 'newState'"
- **Fix:** Changed parameter name from `connectionState` to `newState` to match interface
- **Files modified:** MediasoupClient.kt line 240
- **Commit:** 631f2e4

## Verification Results

### Build Verification
```
cd /home/earthworm/Github-repos/voiceping-router/android && ./gradlew compileDebugKotlin
BUILD SUCCESSFUL in 9s
```

### Pattern Verification
```bash
grep -c "device.createRecvTransport" MediasoupClient.kt     # Returns: 1
grep -c "transport.consume" MediasoupClient.kt              # Returns: 1
grep -c "consumer.resume()" MediasoupClient.kt              # Returns: 1
grep -c "audioTrack.setVolume" MediasoupClient.kt           # Returns: 1
grep "consumeAudio(channelId" ChannelRepository.kt          # Returns: 2 matches
grep "recvTransports" MediasoupClient.kt                    # Returns: map usage confirmed
```

### Success Criteria Status

- [x] MediasoupClient.createRecvTransport() creates real RecvTransport with onConnect callback using runBlocking bridge
- [x] MediasoupClient.consumeAudio() creates real Consumer, resumes it, stores reference
- [x] MediasoupClient.setConsumerVolume() uses AudioTrack.setVolume(0-10) with range conversion
- [x] MediasoupClient.closeConsumer() calls Consumer.close()
- [x] MediasoupClient.cleanup() disposes all consumers then all transports in correct order
- [x] ChannelRepository passes channelId to consumeAudio()
- [x] Gradle compileDebugKotlin succeeds

## Technical Notes

### runBlocking Decision

Used `runBlocking` without dispatcher in `onConnect` callback because:
- Transport callbacks run on native JNI threads (not coroutine context)
- DTLS handshake is one-time connection setup (not per-packet overhead)
- Blocking 50-200ms on native thread is acceptable for connection setup
- Alternative of posting to dispatcher adds complexity without benefit for this critical path

### Volume Control Range

WebRTC AudioTrack.setVolume() uses 0.0-10.0 range (not standard 0.0-1.0):
- App layer uses 0.0-1.0 for consistency with other audio controls
- MediasoupClient converts via `volume * 10.0` before setting
- Coerces input to prevent out-of-range values

### Consumer Resume Critical

Consumers created by transport.consume() **start paused by default**. The `consumer.resume()` call is **CRITICAL** — without it, no audio plays. This is a mediasoup protocol requirement to prevent audio starting before app is ready.

## Commits

| Commit | Type | Description | Files |
|--------|------|-------------|-------|
| 631f2e4 | feat | Implement RecvTransport with real library calls | MediasoupClient.kt |
| 0586659 | feat | Implement Consumer with real library calls and volume control | MediasoupClient.kt, ChannelRepository.kt |

## Next Steps

**Phase 12 Plan 02:** Implement SendTransport and Producer for PTT audio transmission.

**Dependencies for Plan 02:**
- RecvTransport pattern (this plan) → apply to SendTransport
- onConnect runBlocking bridge → reuse for SendTransport.onConnect
- onProduce callback → return producerId from server PRODUCE request

## Self-Check: PASSED

### Files Verified
```bash
[ -f "android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt" ] && echo "FOUND"
# Output: FOUND

[ -f "android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt" ] && echo "FOUND"
# Output: FOUND
```

### Commits Verified
```bash
git log --oneline --all | grep -q "631f2e4" && echo "FOUND: 631f2e4"
# Output: FOUND: 631f2e4

git log --oneline --all | grep -q "0586659" && echo "FOUND: 0586659"
# Output: FOUND: 0586659
```

### Implementation Verified
```bash
grep -q "recvTransports = mutableMapOf<String, RecvTransport>()" android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt && echo "FOUND: recvTransports map"
# Output: FOUND: recvTransports map

grep -q "consumer.resume()" android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt && echo "FOUND: consumer.resume()"
# Output: FOUND: consumer.resume()

grep -q "audioTrack.setVolume(webRtcVolume)" android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt && echo "FOUND: volume control"
# Output: FOUND: volume control
```

All files created, commits exist, and implementation patterns verified.
