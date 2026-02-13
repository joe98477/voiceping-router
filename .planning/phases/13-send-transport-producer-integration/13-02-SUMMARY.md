---
phase: 13-send-transport-producer-integration
plan: 02
subsystem: android-ptt-flow
tags: [mediasoup, ptt, producer-lifecycle, code-deletion, refactoring]
dependency_graph:
  requires:
    - phase-13-plan-01 (SendTransport and Producer integration)
  provides:
    - PttManager wired to Producer lifecycle (no AudioCaptureManager)
    - AudioCaptureManager.kt deleted (168 LOC removed)
  affects:
    - PTT flow simplified: requestPtt -> createSendTransport + startProducing, releasePtt -> stopProducing
tech_stack:
  removed:
    - AudioCaptureManager custom audio capture (168 LOC)
    - AudioRouter from PttManager (unused dependency)
  patterns:
    - Direct Producer lifecycle management in PttManager
    - WebRTC AudioSource handles microphone capture internally
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt
  deleted:
    - android/app/src/main/java/com/voiceping/android/data/audio/AudioCaptureManager.kt
decisions:
  - context: "AudioCaptureManager removal"
    decision: "Delete entirely, replaced by WebRTC AudioSource"
    rationale: "WebRTC AudioSource in MediasoupClient.startProducing() handles mic capture internally, no callback pattern needed"
    impact: "168 LOC deleted, PTT flow simplified, no manual buffer forwarding"
  - context: "AudioRouter dependency in PttManager"
    decision: "Remove from constructor (unused)"
    rationale: "AudioRouter was injected but never used in PttManager methods"
    impact: "Cleaner dependency graph"
metrics:
  duration_seconds: 117
  tasks_completed: 2
  files_modified: 1
  files_deleted: 1
  commits: 2
  loc_added: 15
  loc_removed: 176
  completed_at: "2026-02-13T09:25:24Z"
---

# Phase 13 Plan 02: PttManager Producer Lifecycle Wiring Summary

**One-liner:** PttManager refactored to use MediasoupClient Producer lifecycle directly, AudioCaptureManager.kt deleted (168 LOC removed).

## What Was Built

Completed the send-side integration by wiring PttManager's PTT flow directly to MediasoupClient's Producer lifecycle, eliminating 168 LOC of redundant audio capture code that WebRTC's AudioSource now handles internally.

### Task 1: Refactor PttManager to Use Producer Lifecycle (Commit adb3697)

**Removed dependencies:**
- Deleted `AudioCaptureManager` import and constructor injection
- Deleted `AudioRouter` import and constructor injection (unused - was injected but never called in any method)

**Updated documentation:**
- Class KDoc: Changed flow description from "AudioRecord -> AudioCaptureManager callback" to "WebRTC AudioSource (internal capture) -> Producer (Opus encoding) -> SendTransport -> RTP"
- requestPtt() KDoc: Added explicit 5-step flow when granted:
  1. Server grants PTT
  2. Start foreground service
  3. Create SendTransport (idempotent singleton)
  4. Start producing (creates AudioSource + AudioTrack + Producer with Opus config)
  5. Notify callback (tone/haptic feedback)
- releasePtt() KDoc: Updated to clarify 4-step cleanup:
  1. Cancel timer
  2. Notify callback
  3. Reset state
  4. Stop producing (closes Producer, disposes AudioSource + AudioTrack) -> stop service -> notify server

**Note:** Plan 13-01 already applied blocking deviations that removed actual AudioCaptureManager *calls*:
- `createSendTransport()` already called without channelId
- `startProducing()` already called (no audio capture callback setup)
- `stopProducing()` already called in releasePtt() and forceReleasePtt() (no stopCapture calls)

Task 1 completed the cleanup by removing the *dependencies* and updating *documentation*.

**Files modified:**
- `android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt`

### Task 2: Delete AudioCaptureManager.kt (Commit 2cbab18)

**Deleted file:**
- `android/app/src/main/java/com/voiceping/android/data/audio/AudioCaptureManager.kt` (168 LOC)

**What AudioCaptureManager provided (now handled by WebRTC):**
- **AudioRecord creation** (48kHz mono VOICE_COMMUNICATION source) → WebRTC JavaAudioDeviceModule
- **Buffer read loop** with THREAD_PRIORITY_URGENT_AUDIO → WebRTC native capture thread
- **AcousticEchoCanceler setup** → JavaAudioDeviceModule hardware AEC (configured in Phase 11)
- **onAudioData callback** → Not needed, AudioSource feeds Producer internally
- **Resource cleanup** → AudioSource.dispose() / AudioTrack.dispose() in MediasoupClient.cleanupAudioResources()

**Verification:**
- Searched codebase for "AudioCaptureManager" → 0 references found
- Build passes with no compilation errors
- Only AudioCaptureService (foreground service for mic permission) remains, which is correct

## Deviations from Plan

None - plan executed exactly as written. Plan 13-01 already handled the blocking code changes as deviations, leaving only dependency removal and documentation updates for this plan.

## Verification Results

### Build Verification

```bash
cd /home/earthworm/Github-repos/voiceping-router/android && ./gradlew compileDebugKotlin
```

**Result:** BUILD SUCCESSFUL (only deprecation warnings, no errors)

### Code Verification

- PttManager.kt has no import/reference to AudioCaptureManager ✓
- PttManager.kt has no import/reference to AudioRouter ✓
- PttManager.kt calls `mediasoupClient.createSendTransport()` without channelId ✓
- PttManager.kt does NOT call audioCaptureManager.startCapture() or stopCapture() ✓
- PttManager.kt does NOT call mediasoupClient.sendAudioData() ✓
- forceReleasePtt() uses mediasoupClient.stopProducing() (not audioCaptureManager) ✓
- AudioCaptureManager.kt file does not exist ✓
- No file in codebase imports or references AudioCaptureManager ✓

### Functional Verification

**PTT flow simplified:**

| Step | Old Flow (Phase 6) | New Flow (Phase 13) |
|------|-------------------|---------------------|
| **Request** | requestPtt() → start service → setup callback → createSendTransport → **startCapture()** → startProducing → notify | requestPtt() → start service → createSendTransport() → **startProducing()** → notify |
| **Audio** | AudioRecord → AudioCaptureManager callback → **sendAudioData()** → Producer (stubbed) | WebRTC AudioSource (internal) → AudioTrack → Producer (real) → SendTransport |
| **Release** | releasePtt() → **stopCapture()** → stopProducing → stop service → notify server | releasePtt() → **stopProducing()** → stop service → notify server |

**Key simplifications:**
- No manual audio callback setup
- No manual buffer forwarding (sendAudioData removed)
- No thread management (WebRTC handles capture thread)
- 4 fewer steps in requestPtt() flow

## Technical Notes

### PTT Flow Before vs After Phase 13

**Before (Phase 6 - custom capture):**
```kotlin
requestPtt() {
    // 9 steps total
    audioCaptureManager.onAudioData = { data, size ->
        mediasoupClient.sendAudioData(data, size)  // Manual forwarding
    }
    mediasoupClient.createSendTransport(channelId)
    mediasoupClient.startProducing()
    audioCaptureManager.startCapture()  // Start AudioRecord thread
}

releasePtt() {
    audioCaptureManager.stopCapture()  // Join thread (up to 1s)
    mediasoupClient.stopProducing()
}
```

**After (Phase 13 - WebRTC capture):**
```kotlin
requestPtt() {
    // 5 steps total (when granted)
    mediasoupClient.createSendTransport()  // No channelId
    mediasoupClient.startProducing()  // Creates AudioSource internally
    // AudioSource captures mic, no callback needed
}

releasePtt() {
    mediasoupClient.stopProducing()  // Disposes AudioSource + AudioTrack
}
```

### Why AudioCaptureManager Became Redundant

**Phase 6 design:** mediasoup-android stubs required manual audio capture and buffer forwarding.

**Phase 11-13 reality:** Real libmediasoup-android uses WebRTC's AudioSource/AudioTrack, which:
1. **Abstracts AudioRecord** (no manual AudioRecord creation needed)
2. **Manages capture thread** (THREAD_PRIORITY_URGENT_AUDIO set by WebRTC)
3. **Feeds Producer internally** (no onAudioData callback pattern needed)
4. **Handles AEC/AGC/NS** (JavaAudioDeviceModule configures hardware audio effects)

AudioCaptureManager duplicated work that WebRTC already does.

### Code Deletion Metrics

**Lines removed:** 176 total
- AudioCaptureManager.kt: 168 LOC
- PttManager.kt dependencies: 8 LOC (imports + constructor params)

**Complexity reduction:**
- 1 fewer singleton (AudioCaptureManager)
- 2 fewer constructor dependencies in PttManager
- 0 callback setup (was 5 lines)
- 0 manual thread management

## Next Steps

**Immediate (Phase 13 complete):**
- Phase 13 has 2 plans, both now complete
- SendTransport singleton creation (Plan 01) ✓
- Producer lifecycle with AudioSource (Plan 01) ✓
- PttManager wired to Producer lifecycle (Plan 02) ✓
- AudioCaptureManager deleted (Plan 02) ✓

**Testing (Phase 15 - On-device Testing):**
- Verify PTT button triggers Producer creation
- Verify AudioSource captures microphone audio
- Verify Producer transmits Opus audio to server
- Verify PTT release closes Producer and stops capture
- Verify no memory leaks from AudioSource/AudioTrack disposal

**Future phases:**
- Phase 14: Error recovery and reconnection logic
- Phase 15: On-device testing with physical Android device
- Phase 16: Performance optimization and battery efficiency

## Self-Check

### Files Modified

```bash
[ -f "android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt" ] && echo "FOUND: PttManager.kt" || echo "MISSING: PttManager.kt"
```

**Result:** FOUND: PttManager.kt

### Files Deleted

```bash
[ ! -f "android/app/src/main/java/com/voiceping/android/data/audio/AudioCaptureManager.kt" ] && echo "VERIFIED: AudioCaptureManager.kt deleted" || echo "ERROR: AudioCaptureManager.kt still exists"
```

**Result:** VERIFIED: AudioCaptureManager.kt deleted

### Commits Exist

```bash
git log --oneline --all | grep -q "adb3697" && echo "FOUND: adb3697" || echo "MISSING: adb3697"
git log --oneline --all | grep -q "2cbab18" && echo "FOUND: 2cbab18" || echo "MISSING: 2cbab18"
```

**Result:**
- FOUND: adb3697 (Task 1 commit)
- FOUND: 2cbab18 (Task 2 commit)

### Build Verification

```bash
cd /home/earthworm/Github-repos/voiceping-router/android && ./gradlew compileDebugKotlin
```

**Result:** BUILD SUCCESSFUL

### No AudioCaptureManager References

```bash
grep -r "AudioCaptureManager" android/app/src/main/java
```

**Result:** No matches found

## Self-Check: PASSED

All files modified as expected, deleted file verified gone, all commits verified, build succeeds, no AudioCaptureManager references remain in codebase.
