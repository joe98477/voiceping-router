---
phase: 17
plan: 03
subsystem: transport-health-auto-rejoin
tags: [reliability, transport, reconnection, ptt]
requires: [17-01, mediasoup-client, signaling-client, ptt-manager, channel-repository]
provides: [transport-health-state-machine, auto-rejoin-logic, mid-transmission-recovery, orphan-cleanup]
affects: [MediasoupClient.kt, PttManager.kt, ChannelRepository.kt, TransportHealthState.kt]
dependency-graph:
  requires:
    - phase: 17
      plan: 01
      artifact: producer-retry-logic
    - phase: 13
      plan: 01
      artifact: MediasoupClient
    - phase: 08
      plan: 01
      artifact: PttManager
  provides:
    - transport-health-monitoring
    - 2s-grace-period
    - 15s-orphan-cleanup
    - auto-rejoin-5-attempts
    - persistent-banner-on-failure
  affects:
    - subsystem: mediasoup-client
      reason: transport state monitoring
    - subsystem: ptt-manager
      reason: transport-aware PTT control
    - subsystem: channel-repository
      reason: auto-rejoin orchestration
tech-stack:
  added:
    - TransportHealthState enum (HEALTHY/SEND_DEGRADED/FULLY_DISCONNECTED/RECONNECTING)
  patterns:
    - grace-period-before-failure
    - orphan-cleanup-timers
    - exponential-backoff-auto-rejoin
    - partial-failure-amber-state
key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/domain/model/TransportHealthState.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
    - android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt
    - android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
decisions:
  - decision: 2s grace period for mid-transmission transport failures
    rationale: Gives WebRTC time to recover before releasing PTT (reduces false failures)
    alternatives: [immediate release, 5s grace period]
    chosen: 2s balance between responsiveness and resilience
  - decision: 15s orphan cleanup for disconnected transports
    rationale: Aligns with WebRTC auto-recovery window, prevents resource leaks
    alternatives: [immediate cleanup, 30s window]
    chosen: 15s matches ICE timeout behavior
  - decision: 5 max auto-rejoin attempts with exponential backoff
    rationale: Balances automatic recovery with user control (persistent banner after exhaustion)
    alternatives: [infinite retries, 3 attempts, no auto-rejoin]
    chosen: 5 attempts gives reasonable coverage for transient network issues
  - decision: SEND_DEGRADED state for partial failures (amber PTT)
    rationale: User can still hear others when send transport fails (graceful degradation)
    alternatives: [treat as full disconnect, disable everything]
    chosen: partial operation maintains receive audio
  - decision: Silent recovery to HEALTHY state
    rationale: User decision - no toast on recovery, reduces notification fatigue
    alternatives: [success toast, confirmation tone]
    chosen: silent transition per user preference
metrics:
  duration: 351s
  tasks: 2
  commits: 2
  files_modified: 4
  completed_at: 2026-02-15T04:05:16Z
---

# Phase 17 Plan 03: Transport Health Monitoring & Auto-Rejoin Summary

**One-liner:** Mid-transmission transport failure holds PTT for 2s grace period, orphaned transports cleaned after 15s, full disconnects auto-rejoin with 5-attempt exponential backoff, amber PTT during partial failure.

## What Was Built

### Transport Health State Machine

Created `TransportHealthState` enum with 4 states:
- **HEALTHY**: Both send and receive transports connected
- **SEND_DEGRADED**: Send transport failed, receive still works (amber PTT button)
- **FULLY_DISCONNECTED**: Both transports gone, triggers auto-rejoin
- **RECONNECTING**: Auto-rejoin in progress, PTT disabled

### Mid-Transmission Failure Handling

**SendTransport "disconnected" flow:**
1. Cancel previous grace period job
2. Start 2s grace period timer
3. After 2s, check if still disconnected/failed
4. Determine SEND_DEGRADED (recv works) vs FULLY_DISCONNECTED (all gone)
5. Call `onSendTransportFailed` callback → force-releases PTT

**SendTransport "failed" (terminal):**
- Immediate cleanup (no grace period)
- Set SEND_DEGRADED or FULLY_DISCONNECTED based on recv transports
- Call `onSendTransportFailed`

**SendTransport "connected" (recovery):**
- Cancel grace period and orphan cleanup jobs
- Silent transition from SEND_DEGRADED to HEALTHY
- Call `onSendTransportRecovered` (no toast per user decision)

### Orphan Cleanup Timers

**SendTransport orphan cleanup:**
- 15s timer starts on "disconnected"
- If still disconnected/failed after 15s: close producer, dispose audio resources, close/null transport
- Silent cleanup (no user notification)

**RecvTransport orphan cleanup:**
- 15s timer per channel on "disconnected"
- If still disconnected/failed after 15s: close/remove transport
- Silent cleanup

### Auto-Rejoin Logic

**ChannelRepository observes `transportHealthState`:**
- **HEALTHY**: Clear autoRejoinAttempts, cancel job, hide banner, enable PTT
- **SEND_DEGRADED**: Log warning (amber PTT handled by ViewModel)
- **FULLY_DISCONNECTED**: Disable PTT (`pttDisabledForReconnect = true`), start auto-rejoin

**Auto-rejoin flow:**
1. Set `_isAutoRejoining = true`
2. Reset `transportHealthState` to HEALTHY (let ChannelRepository manage RECONNECTING)
3. Loop up to 5 attempts with exponential backoff (1s, 2s, 4s, 8s, 16s capped at 30s)
4. Each attempt: call `rejoinAllMonitoredChannels()`
5. On success: reset attempts, hide banner, enable PTT, exit
6. On exhaustion: show persistent banner with "Retry" button

**Manual retry:**
- `manualRetryRejoin()` hides banner and restarts auto-rejoin

### PTT Control During Transport Failures

**PttManager guards in `requestPtt()`:**
1. **Reconnect guard**: If `pttDisabledForReconnect == true`, deny PTT
2. **SEND_DEGRADED guard**: If `transportHealthState == SEND_DEGRADED`, deny PTT

**Transport failure force-release:**
- `forceReleasePttTransportFailure()`: Uses `onPttError` callback (double-buzz), does NOT send PTT_STOP (transport broken)
- Distinct from `forceReleasePtt()` (phone call interruption, uses `onPttInterrupted`)

### Wiring in ChannelRepository

```kotlin
// Wire transport failure callbacks
mediasoupClient.onSendTransportFailed = {
    pttManager.forceReleasePttTransportFailure()
}

mediasoupClient.onSendTransportRecovered = {
    Log.d(TAG, "Send transport recovered, PTT auto-recovers to normal state")
}

// Observe transport health state
scope.launch {
    mediasoupClient.transportHealthState.collect { state ->
        when (state) {
            HEALTHY -> { /* clear reconnection state, enable PTT */ }
            SEND_DEGRADED -> { /* amber PTT, receive still works */ }
            FULLY_DISCONNECTED -> { /* disable PTT, start auto-rejoin */ }
            RECONNECTING -> { /* no-op */ }
        }
    }
}
```

## Deviations from Plan

None - plan executed exactly as written. All tasks completed, all verification criteria passed.

## Implementation Notes

### Task 1: Transport Health State Machine

**Files:**
- Created `TransportHealthState.kt` with 4 enum values
- Modified `MediasoupClient.kt`: added `_transportHealthState` StateFlow, monitoring scope, grace period/orphan jobs, `resetTransportHealth()`
- Modified `PttManager.kt`: added `forceReleasePttTransportFailure()`

**Key additions:**
- `monitorScope`: CoroutineScope for transport monitoring jobs
- `sendGracePeriodJob`: 2s timer for mid-transmission recovery
- `sendOrphanCleanupJob`: 15s timer for orphaned send transport
- `recvOrphanCleanupJobs`: Map of 15s timers per recv transport
- `onSendTransportFailed` / `onSendTransportRecovered`: Callbacks for ChannelRepository wiring

**Grace period logic:**
```kotlin
sendGracePeriodJob = monitorScope.launch {
    delay(2000)
    if (transport.connectionState in [disconnected, failed]) {
        _transportHealthState.value = if (hasWorkingRecvTransport) {
            SEND_DEGRADED
        } else {
            FULLY_DISCONNECTED
        }
        onSendTransportFailed?.invoke()
    }
}
```

**Orphan cleanup logic:**
```kotlin
sendOrphanCleanupJob = monitorScope.launch {
    delay(15_000)
    if (transport.connectionState in [disconnected, failed]) {
        // Silent cleanup
        audioProducer?.close()
        cleanupAudioResources()
        sendTransport?.close()
        sendTransport = null
    }
}
```

### Task 2: Auto-Rejoin and PTT Disable

**Files:**
- Modified `ChannelRepository.kt`: added auto-rejoin state, observed transport health, wired callbacks, implemented `startAutoRejoin()` and `manualRetryRejoin()`
- Modified `PttManager.kt`: added `pttDisabledForReconnect` flag, guards in `requestPtt()`

**Key additions:**
- `autoRejoinAttempts` / `maxAutoRejoinAttempts = 5`
- `_showDisconnectedBanner` / `_isAutoRejoining` StateFlows for UI observation
- `autoRejoinJob`: Exponential backoff coroutine

**Auto-rejoin exponential backoff:**
```kotlin
val delayMs = (1000L * (1 shl (autoRejoinAttempts - 1))).coerceAtMost(30_000L)
// Attempt 1: 1s, 2: 2s, 3: 4s, 4: 8s, 5: 16s (capped at 30s)
```

**PTT disable guards:**
```kotlin
// Guard 1: Auto-rejoin in progress
if (pttDisabledForReconnect) {
    onPttDenied?.invoke()
    return
}

// Guard 2: Send transport degraded
if (transportHealthState.value == SEND_DEGRADED) {
    onPttDenied?.invoke()
    return
}
```

## Verification Results

All verification criteria passed:

1. **Build:** `./gradlew compileDebugKotlin` SUCCESS
2. **TransportHealthState enum:** HEALTHY, SEND_DEGRADED, FULLY_DISCONNECTED, RECONNECTING ✅
3. **2s grace period:** Found in SendTransport "disconnected" handler ✅
4. **15s orphan cleanup:** Found for both send (2 instances) and recv transports ✅
5. **Auto-rejoin 5 max attempts:** `maxAutoRejoinAttempts = 5` ✅
6. **Persistent banner:** `showDisconnectedBanner` StateFlow ✅
7. **PTT disabled during auto-rejoin:** `pttDisabledForReconnect` flag ✅
8. **Amber PTT state:** `SEND_DEGRADED` guard in `requestPtt()` ✅
9. **forceReleasePttTransportFailure():** Uses error feedback pattern ✅

## Success Criteria Met

- [x] Transport health state machine tracks HEALTHY/SEND_DEGRADED/FULLY_DISCONNECTED/RECONNECTING
- [x] Mid-transmission failure holds 2s before releasing PTT
- [x] Orphaned transports cleaned after 15s disconnect
- [x] Auto-rejoin: exponential backoff, 5 attempts max, persistent banner on exhaustion
- [x] PTT disabled during reconnection, amber during partial failure
- [x] Recovery to HEALTHY clears all reconnection state silently

## Artifacts Delivered

### TransportHealthState.kt
- 4-state enum for transport health monitoring
- HEALTHY: All transports connected
- SEND_DEGRADED: Send broken, receive works (amber PTT)
- FULLY_DISCONNECTED: All transports gone (auto-rejoin)
- RECONNECTING: Auto-rejoin in progress (PTT disabled)

### MediasoupClient.kt Enhancements
- `transportHealthState` StateFlow for ViewModel observation
- 2s grace period for SendTransport disconnections
- 15s orphan cleanup for send and recv transports
- `resetTransportHealth()` for auto-rejoin coordination
- `onSendTransportFailed` / `onSendTransportRecovered` callbacks

### PttManager.kt Enhancements
- `forceReleasePttTransportFailure()`: Transport-aware error release
- `pttDisabledForReconnect` flag for auto-rejoin control
- SEND_DEGRADED guard for amber PTT state

### ChannelRepository.kt Auto-Rejoin
- `showDisconnectedBanner` / `isAutoRejoining` StateFlows
- `startAutoRejoin()`: 5-attempt exponential backoff
- `manualRetryRejoin()`: Persistent banner retry
- Transport health state observer

## Dependencies

**Requires:**
- Plan 17-01: Producer retry logic (extends with transport-level monitoring)
- Phase 13-01: MediasoupClient baseline (extended with health monitoring)
- Phase 08-01: PttManager (extended with transport-aware controls)

**Provides for:**
- Plan 17-04 (if exists): UI wiring for amber PTT button, persistent banner, auto-rejoin indicators
- Future reliability features: Transport health metrics, connection quality indicators

## Technical Debt / Future Work

None identified. Implementation complete per plan.

## Self-Check

### Files Created

```bash
[ -f "android/app/src/main/java/com/voiceping/android/domain/model/TransportHealthState.kt" ] && echo "FOUND" || echo "MISSING"
```
**Result:** FOUND ✅

### Files Modified

```bash
[ -f "android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt" ] && echo "FOUND" || echo "MISSING"
[ -f "android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt" ] && echo "FOUND" || echo "MISSING"
[ -f "android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt" ] && echo "FOUND" || echo "MISSING"
```
**Result:** All FOUND ✅

### Commits Exist

```bash
git log --oneline --all | grep -q "746a37e" && echo "FOUND: 746a37e" || echo "MISSING: 746a37e"
git log --oneline --all | grep -q "b1a9b94" && echo "FOUND: b1a9b94" || echo "MISSING: b1a9b94"
```
**Result:**
- FOUND: 746a37e (Task 1) ✅
- FOUND: b1a9b94 (Task 2) ✅

## Self-Check: PASSED ✅

All files created, all commits exist, all verification criteria met.
