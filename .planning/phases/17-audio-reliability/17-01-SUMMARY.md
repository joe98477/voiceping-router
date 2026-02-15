---
phase: 17-audio-reliability
plan: 01
subsystem: ptt-reliability
tags: [audio, reliability, error-handling, opus-codec]
dependency_graph:
  requires: [phase-13-audio-transport]
  provides: [ptt-retry-logic, error-feedback, confirmation-tone-infrastructure]
  affects: [ptt-manager, mediasoup-client, settings]
tech_stack:
  added: [exponential-backoff, opus-fec, confirmation-tone]
  patterns: [retry-with-backoff, state-based-error-handling]
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt
    - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
    - android/app/src/main/java/com/voiceping/android/data/audio/HapticFeedback.kt
    - android/app/src/main/java/com/voiceping/android/data/audio/TonePlayer.kt
    - android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt
decisions:
  - "Producer retry logic: 2 retries (3 total attempts) with exponential backoff (1s, 2s)"
  - "Opus DTX disabled for continuous stream (comfort noise, smoother listening)"
  - "Opus FEC always enabled with packetLossPercentage=10 to activate encoder"
  - "Confirmation tone default ON, independent toggle from roger beep"
  - "Error haptic pattern: double-buzz at full amplitude (distinct from denied/release)"
metrics:
  duration_seconds: 284
  completed_at: "2026-02-15T03:56:12Z"
---

# Phase 17 Plan 01: Audio Reliability Foundation Summary

**One-liner:** Producer retry logic with exponential backoff, PttState.Error handling, Opus codec tuned for PTT (DTX off, FEC on), confirmation tone infrastructure, and error feedback patterns.

## What Was Built

### Producer Retry Logic with Exponential Backoff

Added automatic retry mechanism to `PttManager.requestPtt()` that wraps producer creation (steps 4 & 5: createSendTransport + startProducing):

- **Retry count:** 2 retries (3 total attempts)
- **Backoff:** Exponential — 1s after first failure, 2s after second failure
- **State checking:** Each iteration checks PTT state before attempting, preventing orphaned producers if user cancels
- **Error handling:** After all retries exhausted, transitions to `PttState.Error`, invokes `onPttError` callback, displays error for 500ms, returns to Idle, stops foreground service

### PttState.Error

Added `PttState.Error(reason: String)` as a data class to the sealed class, allowing specific error messages to be captured and displayed.

- **Distinct state:** Separate from `Denied` (server busy) and `Idle` (no activity)
- **Reason string:** Carries exception message for debugging/user feedback
- **Auto-transition:** Returns to `Idle` after 500ms (same pattern as `Denied`)

### New PttManager Callbacks

- **`onPttError: (() -> Unit)?`** — Invoked on producer creation failure after retries (Plan 17-02 wires double-buzz haptic + error toast)
- **`onServerAck: ((success: Boolean) -> Unit)?`** — Reserved for Plan 17-02 (green/red flash on server acknowledgment)

### Opus Codec Tuning for PTT

Modified `MediasoupClient.startProducing()` codec options:

| Parameter | Old | New | Rationale |
|-----------|-----|-----|-----------|
| `opusDtx` | `true` | `false` | DTX disabled: continuous stream during PTT (comfort noise, smoother listening) |
| `opusFec` | `true` | `true` | FEC always enabled (no change) |
| `packetLossPercentage` | N/A | `10` | Activates FEC encoder (needs >0 to work) |
| `maxBitrate` | N/A | `40000` | 40kbps max (high quality when network allows) |
| `minBitrate` | N/A | `16000` | 16kbps min (graceful degradation on poor networks) |
| `opusPtime` | `20` | `20` | 20ms packet time for low latency (no change) |

**Impact:** Continuous audio stream during PTT (no silence suppression gaps), proactive packet loss protection via FEC, adaptive bitrate for network resilience.

### Error Feedback Patterns

**HapticFeedback.vibrateErrorRelease():**
- **Pattern:** Strong buzz (80ms), pause (60ms), strong buzz (80ms)
- **Amplitude:** Full (255) for urgency
- **Purpose:** Distinct from normal release (subtle pulse at 128) and denied (buzz-pause-buzz at default amplitude)

### Confirmation Tone Infrastructure

**TonePlayer.playConfirmationTone(success: Boolean):**
- **Success tone:** DTMF_0 (1336 Hz + 941 Hz) for 150ms — same pitch as roger beep
- **Failure tone:** DTMF_7 (1209 Hz + 852 Hz) for 150ms — lower pitch indicates problem
- **Configurable:** Yes, independent toggle from roger beep
- **Purpose:** Audio feedback when screen off/headset — confirms transmission was received by server

**SettingsRepository additions:**
- `CONFIRMATION_TONE_ENABLED` key (default `true`)
- `setConfirmationToneEnabled(enabled: Boolean)` / `getConfirmationToneEnabled(): Flow<Boolean>`
- `getCachedConfirmationToneEnabled(): Boolean` — synchronous access for audio thread

## Deviations from Plan

None — plan executed exactly as written.

## Verification Results

All verification criteria passed:

1. **Build:** `./gradlew compileDebugKotlin` — ✅ PASSED
2. **PttState.Error exists:** Grep found sealed class member and usage in 2 locations — ✅ PASSED
3. **Opus DTX disabled:** Grep found `opusDtx.*false` — ✅ PASSED
4. **FEC activation:** Grep found `packetLossPercentage` in codec options and KDoc — ✅ PASSED
5. **vibrateErrorRelease exists:** Grep found method definition — ✅ PASSED
6. **playConfirmationTone exists:** Grep found method definition — ✅ PASSED
7. **CONFIRMATION_TONE_ENABLED exists:** Grep found key definition and usage in 4 locations — ✅ PASSED
8. **getCachedConfirmationToneEnabled exists:** Grep found method definition — ✅ PASSED

## Dependencies for Next Plans

**Plan 17-02** will wire these callbacks:
- `PttManager.onPttError` → `HapticFeedback.vibrateErrorRelease()` + error toast
- `PttManager.onServerAck` → green/red PTT button flash + `TonePlayer.playConfirmationTone(success)`

**Plan 17-03** will add:
- Network quality indicator using consumer stats
- Jitter buffer tuning (if crow-misia API allows access)

## Technical Decisions

1. **Why 2 retries (3 total attempts)?**
   - Balance between resilience and responsiveness
   - Each attempt ~700ms (onConnect + onProduce roundtrip)
   - 3 attempts with backoff = ~3.7s max latency (1s + 2s delays + 3×700ms)
   - Beyond 3 attempts, user expects failure not extended wait

2. **Why exponential backoff (1s, 2s)?**
   - Linear growth (not doubling) to keep max delay reasonable
   - 1s gives network/transport time to stabilize
   - 2s second delay allows for transient issues to resolve
   - Total retry window ~3s is acceptable for PTT press

3. **Why disable DTX?**
   - PTT transmissions are short bursts (avg 3-5s)
   - Silence suppression gaps create choppy audio perception
   - Comfort noise (continuous stream) provides smoother listening experience
   - Bitrate savings from DTX negligible for short PTT transmissions

4. **Why packetLossPercentage=10?**
   - Opus FEC encoder requires non-zero packet loss expectation to activate
   - 10% is conservative estimate for real-world mobile networks
   - FEC overhead ~20-30% bitrate (acceptable for PTT quality priority)

5. **Why confirmation tone separate from roger beep?**
   - Roger beep = local event (user releases button)
   - Confirmation tone = server event (transmission acknowledged/failed)
   - Users may want server confirmation without local beep clutter
   - Independent toggles allow fine-grained preference control

## Commits

- **4318b03:** feat(17-01): add PttState.Error, producer retry logic, and Opus codec tuning
- **9c5f17a:** feat(17-01): add error feedback patterns and confirmation tone infrastructure

## Self-Check: PASSED

**Files created:** None (all modifications to existing files)

**Files modified (verification):**
- ✅ android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt
- ✅ android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
- ✅ android/app/src/main/java/com/voiceping/android/data/audio/HapticFeedback.kt
- ✅ android/app/src/main/java/com/voiceping/android/data/audio/TonePlayer.kt
- ✅ android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt

**Commits (verification):**
- ✅ 4318b03 exists in git log
- ✅ 9c5f17a exists in git log

All claimed files exist. All claimed commits exist.
