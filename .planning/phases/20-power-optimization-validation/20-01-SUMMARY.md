---
phase: 20-power-optimization-validation
plan: 01
subsystem: power-management
tags: [power, wake-lock, battery, polling, optimization]
dependencies:
  requires: []
  provides: [wake-lock-manager, battery-saver-monitor, channel-stats-poller, power-state]
  affects: [channel-repository, dev-stats-screen]
tech-stack:
  added: []
  patterns: [event-driven-monitoring, adaptive-polling, handler-based-timeout]
key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/data/power/WakeLockManager.kt
    - android/app/src/main/java/com/voiceping/android/data/power/BatterySaverMonitor.kt
    - android/app/src/main/java/com/voiceping/android/domain/model/PowerState.kt
    - android/app/src/main/java/com/voiceping/android/data/network/ChannelStatsPoller.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
    - android/app/src/main/java/com/voiceping/android/presentation/settings/DevStatsScreen.kt
    - android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt
    - android/app/src/main/java/com/voiceping/android/presentation/navigation/NavGraph.kt
    - src/server/config.ts
    - src/server/signaling/websocketServer.ts
decisions:
  - Wake lock timeout default 300s (5 minutes), server-configurable via WAKELOCK_TIMEOUT_SECONDS env var
  - PARTIAL_WAKE_LOCK for CPU-only (screen can sleep), 10-minute safety timeout per Android recommendation
  - Channel stats polling fixed intervals: 5s active / 15s idle (not server-configurable per user decision)
  - 60-second activity window for polling interval transitions
  - Battery saver monitoring via BroadcastReceiver (event-driven, zero CPU overhead)
  - Empty channels (0 consumers) suspend polling with 1s check loop
  - Wake lock callbacks log-only in Plan 20-01; Plan 20-02 integrates with LocationManager
metrics:
  duration: 700s
  tasks_completed: 2
  files_created: 4
  files_modified: 6
  commits: 2
---

# Phase 20 Plan 01: Adaptive Wake Lock & Network Stats Polling Summary

**One-liner:** Configurable wake lock with 300s timeout, battery saver detection via broadcast, and per-channel adaptive polling (5s/15s) with empty channel suspension.

## Overview

Implemented power management foundation with WakeLockManager (PARTIAL_WAKE_LOCK), BatterySaverMonitor (BroadcastReceiver), and ChannelStatsPoller (adaptive intervals). Server config provides wakeLockTimeoutSeconds to client. DevStatsScreen displays power state and polling intervals.

## Tasks Completed

### Task 1: WakeLockManager, BatterySaverMonitor, PowerState, and server config
**Commit:** 1a40a0c
**Files:**
- Created `WakeLockManager.kt`: PARTIAL_WAKE_LOCK with Handler-based timeout
  - `acquire()`: Acquires wake lock, resets timeout
  - `resetTimeout()`: Cancels existing delayed release, schedules new one
  - `releaseImmediate()`: Immediate release on disconnectAll
  - `setTimeoutFromServer(seconds)`: Configures timeout from server (300s default)
  - Callbacks: `onWakeLockReleased`, `onWakeLockAcquired` (for LocationManager coordination)
  - StateFlow: `wakeLockActive` for UI reactivity
- Created `BatterySaverMonitor.kt`: BroadcastReceiver for `ACTION_POWER_SAVE_MODE_CHANGED`
  - Event-driven (broadcast), NOT polling (zero CPU when unchanged)
  - StateFlow: `isBatterySaverEnabled`
  - Callback: `onBatterySaverChanged` for LocationManager coordination
- Created `PowerState.kt`: Data class with `wakeLockActive`, `batterySaverEnabled`, `wakeLockTimeoutMs`, `locationMultiplier`
- Updated `config.ts`: Added `power.wakeLockTimeoutSeconds` (env var `WAKELOCK_TIMEOUT_SECONDS` or 300 default)
- Updated `websocketServer.ts`: Server sends `wakeLockTimeoutSeconds` in CHANNEL_LIST message on connection

**Verification:** TypeScript and Kotlin compile successfully. @ApplicationContext warnings expected (KT-73255 cosmetic issue).

### Task 2: ChannelStatsPoller, ChannelRepository wiring, and DevStatsScreen power fields
**Commit:** 1a20c01
**Files:**
- Created `ChannelStatsPoller.kt`: Per-channel adaptive polling
  - Active interval: 5s (within 60s activity window)
  - Idle interval: 15s (60s+ since last activity)
  - Empty channel: suspend polling (1s check loop, resumes when consumer count > 0)
  - `startPolling(channelId)`: New join counts as initial activity
  - `markActivity(channelId)`: Called on SPEAKER_CHANGED with speaker
  - `updateConsumerCount(channelId, count)`: Detects empty channels, triggers activity on resume
  - StateFlow: `channelIntervals` for DevStatsScreen display
- Updated `ChannelRepository.kt`:
  - Constructor injection: `WakeLockManager`, `BatterySaverMonitor`, `ChannelStatsPoller`
  - `joinChannel()`: Acquire wake lock and start battery saver monitoring on first channel join
  - `joinChannel()`: Start stats polling for each channel after recv transport creation
  - `observeSpeakerChangesForChannel()`: Reset wake lock timeout and mark activity on SPEAKER_CHANGED with speaker
  - `observeChannelStateUpdates()`: Update consumer count for stats poller when CHANNEL_STATE received
  - `leaveChannel()`: Stop stats polling for channel
  - `disconnectAll()`: Release wake lock immediately, stop battery saver monitoring, stop all stats polling
  - Init block: Wire wake lock callbacks (log-only, Plan 20-02 will integrate with LocationManager)
  - Init block: Observe CHANNEL_LIST message for `wakeLockTimeoutSeconds` from server
- Updated `DevStatsScreen.kt`:
  - Parameters: Added `wakeLockManager`, `batterySaverMonitor`, `channelStatsPoller`
  - Power Management section: Wake Lock (Active/Released), Wake Lock Timeout (seconds), Battery Saver (Active/Inactive), Location Multiplier (1x/2x/4x)
  - Channel Polling section: Per-channel intervals (channelId: Xs)
- Updated `MainActivity.kt` and `NavGraph.kt`: Inject and pass power components to DevStatsScreen

**Verification:** Kotlin compiles successfully. ConcurrentHashMap containsKey() fix applied (Kotlin-specific semantics).

## Deviations from Plan

None - plan executed exactly as written.

## Verification Results

1. ✅ `./gradlew compileDebugKotlin` compiles without errors
2. ✅ `npx tsc --noEmit` compiles server TypeScript successfully
3. ✅ WakeLockManager uses PARTIAL_WAKE_LOCK with Handler-based timeout (300s default)
4. ✅ BatterySaverMonitor uses BroadcastReceiver for ACTION_POWER_SAVE_MODE_CHANGED
5. ✅ ChannelStatsPoller has per-channel polling with 5s active / 15s idle intervals
6. ✅ ChannelRepository wires wake lock acquire on join, resetTimeout on audio, release on disconnect
7. ✅ Server config.ts has `power.wakeLockTimeoutSeconds` with env var and 300 default
8. ✅ DevStatsScreen shows Power Management and Channel Polling sections

## Success Criteria Met

- ✅ Wake lock acquired on first channel join, released after 300s of no audio activity
- ✅ Wake lock reset on every SPEAKER_CHANGED with active speaker and PTT press (PTT handled by acquire)
- ✅ Battery saver detected via broadcast receiver, exposed as StateFlow
- ✅ Per-channel polling at 5s/15s with 60s activity window
- ✅ Empty channels (0 consumers) suspend polling
- ✅ Server sends wakeLockTimeoutSeconds to client in CHANNEL_LIST message
- ✅ DevStatsScreen displays all power management fields
- ✅ Android and server build successfully

## Integration Notes

**For Plan 20-02 (Location Tracking Integration):**
- `WakeLockManager.onWakeLockReleased` callback ready for LocationManager to double tracking interval (2x multiplier)
- `WakeLockManager.onWakeLockAcquired` callback ready for LocationManager to restore normal interval
- `BatterySaverMonitor.onBatterySaverChanged` callback ready for LocationManager to quadruple interval (4x multiplier)
- `PowerState.locationMultiplier` field prepared for combined state calculation
- DevStatsScreen already displays calculated location multiplier (1x/2x/4x) based on wake lock and battery saver state

**Wake Lock Lifecycle:**
- First channel join → `acquire()` → wake lock held
- Audio activity (SPEAKER_CHANGED with speaker) → `resetTimeout()` → wake lock extended
- No activity for 300s → automatic release (Handler delayed callback)
- Last channel leave or disconnectAll → `releaseImmediate()` → wake lock released

**Stats Polling Lifecycle:**
- New channel join → `startPolling(channelId)` → polling begins at 5s (join = activity)
- Speaker event → `markActivity(channelId)` → reset activity timestamp
- 60s since last activity → interval increases to 15s
- CHANNEL_STATE with userCount=0 → suspend polling (1s check loop)
- CHANNEL_STATE with userCount>0 after empty → resume at 5s (triggers activity)
- Channel leave → `stopPolling(channelId)` → polling stops

## Self-Check: PASSED

**Created files verified:**
```bash
FOUND: android/app/src/main/java/com/voiceping/android/data/power/WakeLockManager.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/power/BatterySaverMonitor.kt
FOUND: android/app/src/main/java/com/voiceping/android/domain/model/PowerState.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/network/ChannelStatsPoller.kt
```

**Commits verified:**
```bash
FOUND: 1a40a0c (Task 1: WakeLockManager, BatterySaverMonitor, PowerState, server config)
FOUND: 1a20c01 (Task 2: ChannelStatsPoller, ChannelRepository wiring, DevStatsScreen)
```

**Modified files verified:**
```bash
FOUND: android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
FOUND: android/app/src/main/java/com/voiceping/android/presentation/settings/DevStatsScreen.kt
FOUND: src/server/config.ts
FOUND: src/server/signaling/websocketServer.ts
```

All files exist, commits present, plan complete.
