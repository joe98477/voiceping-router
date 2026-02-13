---
phase: 13-send-transport-producer-integration
plan: 01
subsystem: android-mediasoup-send
tags: [mediasoup, webrtc, ptt, audio-transmission, send-transport, producer]
dependency_graph:
  requires:
    - phase-12-recv-transport-integration (RecvTransport pattern established)
    - phase-11-library-upgrade (PeerConnectionFactory and Device initialized)
  provides:
    - SendTransport singleton creation
    - Producer lifecycle with AudioSource/AudioTrack
    - Opus PTT codec configuration
  affects:
    - PttManager (no longer uses AudioCaptureManager callbacks)
tech_stack:
  added:
    - libmediasoup-android SendTransport and Producer
    - WebRTC AudioSource for microphone capture
    - WebRTC AudioTrack for audio streaming
  patterns:
    - Singleton SendTransport (one per device, not per channel)
    - runBlocking bridge for JNI thread callbacks (onConnect, onProduce)
    - Correct native resource disposal order (AudioTrack before AudioSource)
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
    - android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt
decisions:
  - context: "SendTransport creation pattern"
    decision: "Singleton without channelId parameter"
    rationale: "App transmits to only one channel at a time (PTT arbitration), unlike receive side which monitors multiple channels"
    alternatives_considered:
      - "Per-channel SendTransport": Rejected - wasteful, PTT is mutually exclusive
  - context: "Producer lifecycle"
    decision: "Create on PTT press, close on PTT release (not pause/resume)"
    rationale: "PTT transmission is ephemeral; closing releases AudioRecord immediately"
  - context: "Audio capture mechanism"
    decision: "WebRTC AudioSource captures internally, no manual buffer forwarding"
    rationale: "AudioSource abstracts AudioRecord, Producer handles Opus encoding internally"
    impact: "Removes dependency on AudioCaptureManager callbacks, simplifies PTT flow"
metrics:
  duration_seconds: 362
  tasks_completed: 2
  files_modified: 2
  commits: 2
  loc_added: 171
  loc_removed: 131
  completed_at: "2026-02-13T08:42:39Z"
---

# Phase 13 Plan 01: SendTransport and Producer Integration Summary

**One-liner:** Real SendTransport singleton with onConnect/onProduce callbacks and Producer lifecycle using WebRTC AudioSource for PTT audio transmission.

## What Was Built

Replaced stubbed SendTransport and Producer code in MediasoupClient with real libmediasoup-android library calls, enabling PTT audio transmission via WebRTC.

### Task 1: SendTransport Creation with Callbacks (Commit a374223)

**Implemented:**
- Added imports: `SendTransport`, `Producer`, `AudioSource`, `MediaConstraints`
- Changed field types from `Any?` to real library types:
  - `sendTransport: SendTransport?`
  - `audioProducer: Producer?`
  - Added: `audioSource: AudioSource?`
  - Added: `pttAudioTrack: org.webrtc.AudioTrack?`
- Changed `createSendTransport()` signature: removed `channelId` parameter (singleton pattern)
- Implemented `createSendTransport()`:
  - Guard against duplicate creation
  - Request `CREATE_TRANSPORT` with `direction="send"` (no channelId)
  - Create `SendTransport` via `device.createSendTransport()` with listener:
    - `onConnect`: bridges to signaling via `runBlocking`
    - `onProduce`: bridges to signaling, returns producer ID from server
    - `onProduceData`: throws `UnsupportedOperationException` (audio-only app)
    - `onConnectionStateChange`: closes audioProducer on "failed"/"disconnected"
- Updated class KDoc: removed "skeleton" reference, described WebRTC AudioSource flow

**Files modified:**
- `android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt`
- `android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt` (deviation)

### Task 2: Producer Lifecycle with AudioSource/AudioTrack (Commit 524b947)

**Implemented:**
- Replaced `startProducing()` body:
  - Guard: `SendTransport` must exist
  - Create `AudioSource` from `PeerConnectionFactory`
  - Create `AudioTrack` from `AudioSource` with label "audio-ptt"
  - Define Opus PTT codec options: mono, DTX, FEC, 48kHz, 20ms ptime
  - Create `Producer` via `transport.produce()` with listener (onTransportClose calls `cleanupAudioResources()`)
  - Wrap in try/catch, call `cleanupAudioResources()` on failure
- Replaced `stopProducing()` body:
  - Close Producer, set to null
  - Call `cleanupAudioResources()`
  - Ensure cleanup even on error
- Added `cleanupAudioResources()` private method:
  - Dispose AudioTrack then AudioSource in correct order (CRITICAL for native memory)
- Deleted `sendAudioData()` method entirely (WebRTC AudioSource captures internally)
- Updated `cleanup()` method:
  - Replaced stubbed Producer close with `audioProducer?.close()`
  - Replaced stubbed SendTransport close with `sendTransport?.close()`
  - Call `cleanupAudioResources()`

**Files modified:**
- `android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt`
- `android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt` (deviation)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] PttManager calls to createSendTransport() with channelId parameter**
- **Found during:** Task 1
- **Issue:** `createSendTransport()` signature changed from `createSendTransport(channelId: String)` to `createSendTransport()` (singleton pattern). PttManager.kt at line 148 still called with `channelId` argument, blocking build.
- **Fix:** Updated PttManager.kt to call `createSendTransport()` without channelId parameter. Added comment explaining singleton pattern.
- **Files modified:** `android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt`
- **Commit:** a374223

**2. [Rule 3 - Blocking] PttManager references to deleted sendAudioData() method**
- **Found during:** Task 2
- **Issue:** Deleting `sendAudioData()` method broke PttManager.kt line 144 (audio capture callback) and line 50 (class KDoc describing audio flow). Build failed with "Unresolved reference: sendAudioData".
- **Fix:** Removed audio capture callback setup (Step 4) from `requestPtt()`, updated class KDoc to describe WebRTC AudioSource flow instead of AudioCaptureManager callback loop, removed `audioCaptureManager.stopCapture()` calls from `releasePtt()` and `forceReleasePtt()`, renumbered steps accordingly.
- **Rationale:** WebRTC AudioSource captures microphone internally, no manual buffer forwarding needed.
- **Files modified:** `android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt`
- **Commit:** 524b947

## Verification Results

### Build Verification

```bash
cd /home/earthworm/Github-repos/voiceping-router/android && ./gradlew compileDebugKotlin
```

**Result:** BUILD SUCCESSFUL (only deprecation warnings, no errors)

### Type Verification

- `sendTransport` field type: `SendTransport?` ✓
- `audioProducer` field type: `Producer?` ✓
- `audioSource` field type: `AudioSource?` ✓
- `pttAudioTrack` field type: `org.webrtc.AudioTrack?` ✓
- `createSendTransport()` signature: no channelId parameter ✓
- `onProduce` callback returns: `String` (producer ID) ✓

### Functional Verification

- SendTransport singleton guard: ✓ (checks `if (sendTransport != null)`)
- Opus PTT config: ✓ (mono, DTX, FEC, 48kHz, 20ms ptime)
- Native resource disposal order: ✓ (AudioTrack disposed before AudioSource)
- `sendAudioData()` removed: ✓ (grep found no references)
- No TODO comments for send-side code: ✓

## Technical Notes

### SendTransport vs RecvTransport Patterns

| Aspect | RecvTransport | SendTransport |
|--------|---------------|---------------|
| **Cardinality** | One per channel | One per device (singleton) |
| **Reason** | Multi-channel monitoring | PTT is mutually exclusive |
| **Parameter** | `createRecvTransport(channelId)` | `createSendTransport()` (no channelId) |
| **Storage** | `Map<String, RecvTransport>` | `SendTransport?` field |

### runBlocking Bridge Pattern

Both `onConnect` and `onProduce` callbacks execute on native JNI threads (not coroutine context). They must call suspend functions (`signalingClient.request()`), requiring `runBlocking` bridge:

```kotlin
override fun onConnect(transport: Transport, dtlsParameters: String) {
    runBlocking {
        signalingClient.request(SignalingType.CONNECT_TRANSPORT, ...)
    }
}
```

This pattern was validated in Phase 12 for RecvTransport and applies identically to SendTransport.

### AudioSource vs AudioCaptureManager

**Old flow (Phase 6):**
AudioRecord → AudioCaptureManager callback → MediasoupClient.sendAudioData() → Producer (stubbed)

**New flow (Phase 13):**
WebRTC AudioSource (abstracts AudioRecord) → AudioTrack → Producer (encodes Opus internally) → SendTransport

**Benefits:**
- No manual buffer forwarding
- Producer handles Opus encoding via WebRTC pipeline
- Simpler PTT flow: `requestPtt()` → `createSendTransport()` + `startProducing()`, `releasePtt()` → `stopProducing()`

### Native Resource Disposal Order

CRITICAL: AudioTrack must be disposed before AudioSource to prevent native memory leaks.

```kotlin
private fun cleanupAudioResources() {
    pttAudioTrack?.dispose()  // Dispose AudioTrack FIRST
    pttAudioTrack = null

    audioSource?.dispose()     // Then AudioSource
    audioSource = null
}
```

Violating this order causes JNI crashes or memory leaks.

## Next Steps

**Immediate (Plan 02):**
- Wire SendTransport creation and Producer lifecycle into PttManager PTT flow
- Remove AudioCaptureManager dependency from PttManager (now handled by WebRTC AudioSource)
- Update PTT request flow: `requestPtt()` calls `createSendTransport()` then `startProducing()`
- Update PTT release flow: `releasePtt()` calls `stopProducing()` only (no stopCapture)

**Testing (Phase 15 - On-device Testing):**
- Verify AudioSource captures microphone audio
- Verify Producer transmits Opus audio to server
- Verify SendTransport DTLS handshake via onConnect callback
- Verify Producer ID returned from onProduce callback
- Verify AudioTrack/AudioSource disposal order prevents memory leaks

## Self-Check

### Files Created
None - plan only modified existing files.

### Files Modified

```bash
[ -f "android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt" ] && echo "FOUND: MediasoupClient.kt" || echo "MISSING: MediasoupClient.kt"
[ -f "android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt" ] && echo "FOUND: PttManager.kt" || echo "MISSING: PttManager.kt"
```

**Result:**
- FOUND: MediasoupClient.kt
- FOUND: PttManager.kt

### Commits Exist

```bash
git log --oneline --all | grep -q "a374223" && echo "FOUND: a374223" || echo "MISSING: a374223"
git log --oneline --all | grep -q "524b947" && echo "FOUND: 524b947" || echo "MISSING: 524b947"
```

**Result:**
- FOUND: a374223 (Task 1 commit)
- FOUND: 524b947 (Task 2 commit)

## Self-Check: PASSED

All files exist, all commits verified, build succeeds, no stub/TODO comments remain for send-side code.
