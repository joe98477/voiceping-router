---
phase: 15
plan: 02
subsystem: android-device-testing
tags: [physical-device, end-to-end-audio, battery-profiling, bug-fixes, race-condition]
dependency_graph:
  requires: ["15-01"]
  provides:
    - physical-device-audio-verified
    - battery-profiling-passed
    - producer-race-condition-fixed
    - security-exception-crash-fixed
  affects:
    - android-audio-pipeline
    - android-release-quality
tech_stack:
  added: []
  patterns:
    - Volatile flag for async producer lifecycle coordination
    - try-catch for Android 16 permission changes
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/audio/AudioRouter.kt
    - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
decisions:
  - decision: "Use @Volatile producingRequested flag to coordinate produce/stop race"
    rationale: "transport.produce() blocks ~700ms for onConnect+onProduce server roundtrips; stopProducing() can run on a different coroutine during this window"
    alternatives: "Mutex around entire produce/stop cycle (would deadlock), or cancel coroutine (doesn't work with JNI blocking call)"
    impact: "Eliminates orphaned producers that caused audio-only-transmits-once bug"
  - decision: "Wrap telephonyManager.callState in try-catch instead of adding READ_PHONE_STATE permission"
    rationale: "Less invasive than requesting a sensitive permission; phone call detection is best-effort, not critical"
    alternatives: "Add READ_PHONE_STATE to manifest"
    impact: "App won't crash on Android 16 Samsung devices when audio focus changes"
  - decision: "Update sendTransportChannelId on SendTransport reuse"
    rationale: "onProduce callback reads sendTransportChannelId to tell server which channel to associate the producer with"
    alternatives: "Pass channelId through produce() appData (requires protocol change)"
    impact: "PTT transmits to correct channel when switching between channels"
metrics:
  completed: "2026-02-15"
  tasks_completed: 2
  files_modified: 2
  bugs_fixed: 3
  device: "Samsung Galaxy S21 (SM-S906E, Android 16 Beta BP2A.250605.031)"
---

# Phase 15 Plan 02: Physical Device Testing and Battery Profiling Summary

**One-liner:** End-to-end audio verified on Samsung Galaxy S21 (Android 16) after fixing 3 bugs discovered during testing — SecurityException crash, producer race condition, and wrong channel routing

## What Was Done

Installed release APK on a physical Samsung Galaxy S21 running Android 16 Beta. Initial testing revealed 3 bugs from bugreport analysis. All 3 were fixed, a new release APK was built, and all 8 end-to-end audio tests passed. Battery profiling confirmed 5%/hour drain (well within the 10%/hour threshold).

## Bugs Found and Fixed

### Bug 1: SecurityException crash on first app open

**Symptom:** App crashed on first launch. Bugreport showed `java.lang.SecurityException: getCallState: Neither user 10502 nor current process has android.permission.READ_PHONE_STATE` at obfuscated `l8.d.onAudioFocusChange` (AudioRouter).

**Root cause:** On Android 16 (Samsung), `TelephonyManager.getCallState()` requires `READ_PHONE_STATE` permission. AudioRouter's `audioFocusChangeListener` calls it when processing `AUDIOFOCUS_LOSS_TRANSIENT` (triggered by Bluetooth SCO activation).

**Fix:** Wrapped `telephonyManager.callState` in try-catch for SecurityException, defaulting to `CALL_STATE_IDLE`. Phone call detection is best-effort — false negatives only mean we don't pause channels during phone calls, which is acceptable.

**File:** `AudioRouter.kt` — `audioFocusChangeListener` lambda

### Bug 2: Audio only transmits once (producer race condition)

**Symptom:** First PTT works, subsequent PTT presses don't produce audio on web UI. Logcat showed "Producer already exists, skipping" on second PTT.

**Root cause:** `transport.produce()` blocks ~700ms (onConnect + onProduce server roundtrips). User releases PTT before it returns. `stopProducing()` runs but `audioProducer` is still null (produce hasn't returned), so close is a no-op but `cleanupAudioResources()` disposes audioSource/audioTrack. Then produce returns, sets `audioProducer` to a producer with disposed audio resources. Next PTT sees non-null audioProducer and skips creation.

**Fix:** Added `@Volatile producingRequested` flag. `startProducing()` sets it true before `produce()`, checks after return. `stopProducing()` sets it false. If flag is false after produce returns, orphaned producer is immediately closed. Also changed `startProducing()` entry to close stale producers instead of skipping.

**File:** `MediasoupClient.kt` — `startProducing()`, `stopProducing()`, `cleanup()`

### Bug 3: Wrong channel in onProduce when switching channels

**Symptom:** Log showed `onProduce: channel=da9a276c` when PTT was requested for channel `856ea3c0`.

**Root cause:** `createSendTransport()` returns early when transport already exists WITHOUT updating `sendTransportChannelId`. The `onProduce` callback reads the stale value.

**Fix:** Added `sendTransportChannelId = channelId` before the early return in `createSendTransport()`.

**File:** `MediasoupClient.kt` — `createSendTransport()`

## Test Results

### End-to-End Audio Tests (Task 1) — ALL PASSED

| # | Test | Result |
|---|------|--------|
| 1 | Login | PASS |
| 2 | Select event | PASS |
| 3 | Join channel | PASS |
| 4 | Receive audio | PASS |
| 5 | Transmit audio (PTT) | PASS |
| 6 | Release PTT | PASS |
| 7 | Monitor channels | PASS |
| 8 | Background operation | PASS |

No `UnsatisfiedLinkError`, `NoSuchMethodError`, or app crashes in logcat.

### Battery Profiling (Task 2) — PASSED

- **Device:** Samsung Galaxy S21 (SM-S906E)
- **OS:** Android 16 Beta (BP2A.250605.031)
- **Test duration:** 2 hours
- **Drain rate:** 5%/hour
- **Threshold:** Under 10%/hour (VALID-03)
- **Rating:** Acceptable (5-10%/hour range)

## Files Modified

| File | Changes | Purpose |
|------|---------|---------|
| `android/app/src/main/java/com/voiceping/android/data/audio/AudioRouter.kt` | try-catch around `telephonyManager.callState` | Fix SecurityException crash on Android 16 |
| `android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt` | `@Volatile producingRequested` flag, stale producer cleanup, `sendTransportChannelId` update on reuse | Fix producer race condition and wrong channel routing |

## Verification Results

All success criteria met:

1. VALID-02 satisfied: Release APK tested on physical Android device with end-to-end audio (transmit and receive)
2. VALID-03 satisfied: Battery profiling shows 5%/hour drain with screen off (under 10%/hour threshold)
3. No JNI class stripping errors encountered at runtime (ProGuard/R8 rules from Plan 15-01 validated)

## Dependencies

**Provides:**
- `physical-device-audio-verified` — End-to-end audio works on real hardware
- `battery-profiling-passed` — Battery drain within acceptable limits
- `producer-race-condition-fixed` — PTT works reliably on every press
- `security-exception-crash-fixed` — No crash on Android 16 Samsung devices

## Self-Check: PASSED

**Tests verified:**
```
PASSED: 8/8 end-to-end audio tests on Samsung Galaxy S21
PASSED: Battery profiling 5%/hour (threshold: <10%/hour)
PASSED: No UnsatisfiedLinkError or NoSuchMethodError in logcat
```

**Bugs fixed:**
```
FIXED: SecurityException crash (AudioRouter.kt)
FIXED: Producer race condition (MediasoupClient.kt)
FIXED: Wrong channel in onProduce (MediasoupClient.kt)
```

**Build verified:**
```
PASSED: compileDebugKotlin
PASSED: assembleRelease
```
