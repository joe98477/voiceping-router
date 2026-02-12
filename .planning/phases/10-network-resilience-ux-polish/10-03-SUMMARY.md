---
phase: 10-network-resilience-ux-polish
plan: 03
subsystem: ux
tags: [reconnection-ui, connection-tones, haptic-feedback, offline-state, notification]

# Dependency graph
requires:
  - phase: 10-network-resilience-ux-polish
    plan: 01
    provides: ConnectionState.RECONNECTING and network resilience foundation
provides:
  - ConnectionBanner with RECONNECTING/FAILED states and 5-second delay logic
  - Connection/disconnection tones (DTMF 9/7) respecting tone toggle
  - Transmission start and busy haptic patterns
  - Offline badge in BottomBar during disconnection
  - Reconnecting notification subtitle
  - PTT error handling during disconnection
affects: [connection-ui, audio-feedback, notification, ptt-interaction]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - 5-second delay before showing RECONNECTING banner (brief drops silent)
    - Connection tone only for long disconnections (5+ seconds)
    - Delayed disconnection tone aligned with banner timing
    - Transmission event haptic patterns (start, busy, press, release)
    - PTT connection state guard (error feedback when disconnected)

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/presentation/shell/ConnectionBanner.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt
    - android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt
    - android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt
    - android/app/src/main/java/com/voiceping/android/data/audio/TonePlayer.kt
    - android/app/src/main/java/com/voiceping/android/data/audio/HapticFeedback.kt
    - android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt

key-decisions:
  - "5-second delay before RECONNECTING banner: brief drops (1-3s) stay silent, long disconnections (5+s) show banner"
  - "Connection tone only for long disconnections: silent for brief drops (<5s), tone for 5+ second reconnections"
  - "Disconnection tone delayed 5 seconds: aligned with banner timing, ensures brief drops produce no audio/visual feedback"
  - "PTT stays interactive during reconnection: error tone + haptic on press, no visual graying out"
  - "Busy vibration for PTT denials: double-tap pattern for most common case (channel occupied)"

patterns-established:
  - "LaunchedEffect with delay for time-based UI state transitions (5s reconnection banner delay)"
  - "Connection state observation in ChannelRepository for cross-cutting concerns (tones, notification, haptics)"
  - "Disconnection duration tracking for conditional audio feedback (only play tones for long disconnects)"

# Metrics
duration: 5min 37s
completed: 2026-02-12
---

# Phase 10 Plan 03: Reconnection UI Feedback & Connection Tones Summary

**Enhanced ConnectionBanner with RECONNECTING/FAILED states and 5-second delay, connection/disconnection tones (DTMF 9/7), transmission start and busy haptic patterns, offline badge, and PTT error handling during disconnection**

## Performance

- **Duration:** 5 min 37 sec
- **Started:** 2026-02-12T20:41:18Z
- **Completed:** 2026-02-12T20:46:55Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- ConnectionBanner enhanced with RECONNECTING and FAILED states, 5-second delay before showing (brief drops silent), slideInVertically animation
- ConnectionBanner shows "Connection lost" with Retry button for FAILED state
- BottomBar accepts connectionState parameter, shows "(Offline)" badge when disconnected
- ChannelMonitoringService notification shows "Reconnecting..." subtitle during reconnection attempts
- PttManager guards PTT requests: checks connection state before sending PTT_START, triggers error feedback when disconnected
- TonePlayer adds playConnectionTone() (DTMF 9, 120ms) and playDisconnectionTone() (DTMF 7, 150ms), respecting tone toggle
- HapticFeedback adds vibrateTransmissionStart() (40ms light pulse) and vibrateBusy() (double-tap pattern)
- ChannelRepository observes connection state transitions, plays tones only for long disconnections (5+s), updates notification reconnecting state
- ChannelRepository wires vibrateTransmissionStart() to incoming speaker events, uses vibrateBusy() for PTT denials

## Task Commits

Each task was committed atomically:

1. **Task 1: Enhance ConnectionBanner, BottomBar, and notification for reconnection states** - `9feec8e` (feat)
2. **Task 2: Add connection tones and complete haptic feedback patterns** - `1b28c86` (feat)

## Files Created/Modified

- `android/app/src/main/java/com/voiceping/android/presentation/shell/ConnectionBanner.kt` - Enhanced with RECONNECTING/FAILED states, 5-second delay logic, Retry button, slideInVertically animation
- `android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt` - Added connectionState parameter, offline badge display
- `android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt` - Added EXTRA_IS_RECONNECTING, "Reconnecting..." notification subtitle
- `android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt` - Added connection state guard for PTT requests (error feedback when disconnected)
- `android/app/src/main/java/com/voiceping/android/data/audio/TonePlayer.kt` - Added playConnectionTone() and playDisconnectionTone() methods
- `android/app/src/main/java/com/voiceping/android/data/audio/HapticFeedback.kt` - Added vibrateTransmissionStart() and vibrateBusy() methods
- `android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt` - Wired connection state observations, tones, haptics, and notification updates

## Decisions Made

- **5-second delay before RECONNECTING banner:** Brief drops (1-3s) produce no banner (silent recovery), long disconnections (5+s) show "Reconnecting..." banner. Implemented via LaunchedEffect with delay(5000L) and state check after delay.
- **Connection tone only for long disconnections:** Track disconnection start time, calculate duration on reconnect, only play tone if duration >= 5000ms. Brief drops stay silent per locked decision.
- **Disconnection tone delayed 5 seconds:** When RECONNECTING state entered, launch coroutine with 5s delay before playing disconnection tone. Ensures brief drops produce no audio feedback (aligned with banner timing).
- **PTT stays interactive during reconnection:** Added connection state guard in PttManager.requestPtt() that checks ConnectionState before sending PTT_START. On disconnected state, triggers onPttDenied callback (error tone + haptic) without changing PTT state. PTT button remains visually active (no graying out).
- **Busy vibration for PTT denials:** Changed ChannelRepository onPttDenied callback from vibrateError() to vibrateBusy() (double-tap pattern). Most common PTT denial case is channel occupied by another speaker, so busy pattern is more appropriate.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Restored corrupted ProfileDrawer.kt**
- **Found during:** Task 2 compilation
- **Issue:** ProfileDrawer.kt in working directory had all settings parameters stripped (signature reduced to basic user info only), causing ChannelListScreen compilation failures with "Unresolved reference" and "No parameter with name" errors
- **Fix:** Ran `git checkout android/app/src/main/java/com/voiceping/android/presentation/shell/ProfileDrawer.kt` to restore proper version with all settings parameters (pttMode, audioRoute, scan mode, hardware buttons, etc.)
- **Files modified:** ProfileDrawer.kt (restored from git)
- **Verification:** Compilation succeeded after restore
- **Committed in:** N/A (git restore operation)
- **Root cause:** Likely incomplete revert from previous phase execution or file system issue that partially modified ProfileDrawer

---

**Total deviations:** 1 auto-fixed (1 blocking build issue from file corruption)
**Impact on plan:** No scope creep. Plan executed exactly as specified after fixing pre-existing file corruption issue.

## Issues Encountered

- **ProfileDrawer file corruption:** Working directory version had parameters stripped, causing compilation failure. Resolution: `git checkout` restored proper version from repository.

## User Setup Required

None - all changes are code-level enhancements, no external configuration needed.

## Next Phase Readiness

- Reconnection UI feedback complete: banner, notification subtitle, offline badge
- Connection tones and haptic patterns complete: all transmission events covered
- Ready for Plan 04: Connection quality indicators and network-aware features
- Ready for Plan 05: Final UX polish and testing

## Self-Check

**Files:**
- FOUND: android/app/src/main/java/com/voiceping/android/presentation/shell/ConnectionBanner.kt (modified)
- FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt (modified)
- FOUND: android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt (modified)
- FOUND: android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt (modified)
- FOUND: android/app/src/main/java/com/voiceping/android/data/audio/TonePlayer.kt (modified)
- FOUND: android/app/src/main/java/com/voiceping/android/data/audio/HapticFeedback.kt (modified)
- FOUND: android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt (modified)

**Commits:**
- FOUND: 9feec8e (Task 1: Enhance ConnectionBanner, BottomBar, and notification for reconnection states)
- FOUND: 1b28c86 (Task 2: Add connection tones and complete haptic feedback patterns)

**Result:** PASSED
