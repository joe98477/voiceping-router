---
phase: 10-network-resilience-ux-polish
plan: 04
subsystem: ux
tags: [network-quality, transmission-history, signal-bars, bottom-sheet, ux-polish]

# Dependency graph
requires:
  - phase: 05-android-foundation
    provides: SignalingClient with latency measurement
  - phase: 10-network-resilience-ux-polish
    plan: 01
    provides: NetworkMonitor and latency measurement via heartbeat PING
provides:
  - NetworkQuality enum mapping latency (ms) to signal bars (1-4)
  - NetworkQualityIndicator composable (signal bars with tap-to-reveal popup)
  - TransmissionHistoryEntry domain model
  - TransmissionHistoryRepository (in-memory, last 20 transmissions per channel)
  - TransmissionHistorySheet composable (ModalBottomSheet)
  - ChannelRow long-press handler for transmission history
affects: [ux, network-monitoring, channel-history, user-awareness]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Signal bars UI pattern (4 vertical bars drawn with Canvas)
    - ModalBottomSheet for transmission history
    - In-memory circular buffer with reactive Flow observation
    - Long-press gesture on ChannelRow to open bottom sheet

key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/domain/model/NetworkQuality.kt
    - android/app/src/main/java/com/voiceping/android/domain/model/TransmissionHistoryEntry.kt
    - android/app/src/main/java/com/voiceping/android/data/repository/TransmissionHistoryRepository.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/NetworkQualityIndicator.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/TransmissionHistorySheet.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt

key-decisions:
  - "NetworkQuality.fromLatency() thresholds: <100ms EXCELLENT (4 bars), 100-300ms GOOD (3 bars), 300-600ms FAIR (2 bars), >600ms/null POOR (1 bar)"
  - "Signal bars drawn with Canvas (4 vertical bars of increasing height) instead of Material Icons (limited bar-count icons available)"
  - "Circular buffer stores last 20 transmissions per channel in ConcurrentHashMap for thread safety"
  - "Long-press on ChannelRow opens transmission history (not set primary channel)"
  - "TransmissionHistorySheet shows newest first (reverse chronological order)"
  - "NetworkMonitor started in ViewModel init, stopped in onCleared()"

patterns-established:
  - "NetworkQualityIndicator placement: TopAppBar actions, between audio device icon and connection status dot"
  - "Transmission history access pattern: long-press ChannelRow → ModalBottomSheet → observe repository via ViewModel StateFlow"
  - "In-memory circular buffer pattern: ConcurrentHashMap + MutableSharedFlow for change notifications"

# Metrics
duration: 16min
completed: 2026-02-12
---

# Phase 10 Plan 04: Network Quality Indicator & Transmission History Summary

**Network quality indicator (signal bars with tap-to-reveal detail popup) and transmission history (bottom sheet with last 20 transmissions per channel via long-press on channel row)**

## Performance

- **Duration:** 15 min 51 sec
- **Started:** 2026-02-12T20:41:24Z
- **Completed:** 2026-02-12T20:57:15Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- NetworkQuality enum maps latency to signal bars (EXCELLENT 4 bars, GOOD 3, FAIR 2, POOR 1)
- NetworkQualityIndicator composable displays signal bars in TopAppBar with tap-to-reveal popup (latency, network type, server)
- TransmissionHistoryEntry data class captures speaker name, timestamp, duration, channel, ownership
- TransmissionHistoryRepository stores last 20 transmissions per channel in-memory with reactive Flow
- TransmissionHistorySheet ModalBottomSheet shows transmission history (newest first)
- ChannelRow long-press opens transmission history (changed from set primary channel)
- ViewModel exposes latency, networkType, serverUrl, selectedHistoryChannelId, transmissionHistory StateFlows
- NetworkMonitor lifecycle managed in ViewModel (start in init, stop in onCleared)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create NetworkQuality model, TransmissionHistoryRepository, and domain models** - `6fd6cb9` (feat)
2. **Task 2: Create NetworkQualityIndicator, TransmissionHistorySheet, and wire to UI** - `3bc1c34` (feat)

## Files Created/Modified
- `android/app/src/main/java/com/voiceping/android/domain/model/NetworkQuality.kt` - Enum mapping latency (ms) to signal bars (1-4)
- `android/app/src/main/java/com/voiceping/android/domain/model/TransmissionHistoryEntry.kt` - Data class for transmission history entries
- `android/app/src/main/java/com/voiceping/android/data/repository/TransmissionHistoryRepository.kt` - In-memory repository with circular buffer (last 20 per channel) and reactive Flow
- `android/app/src/main/java/com/voiceping/android/presentation/channels/components/NetworkQualityIndicator.kt` - Signal bars composable with tap-to-reveal popup
- `android/app/src/main/java/com/voiceping/android/presentation/channels/components/TransmissionHistorySheet.kt` - ModalBottomSheet displaying transmission history
- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt` - Inject NetworkMonitor and TransmissionHistoryRepository, expose StateFlows, lifecycle management
- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt` - Wire NetworkQualityIndicator to TopAppBar, TransmissionHistorySheet to long-press, simplify ProfileDrawer call

## Decisions Made
- **NetworkQuality thresholds:** <100ms = EXCELLENT (4 bars), 100-300ms = GOOD (3 bars), 300-600ms = FAIR (2 bars), >600ms or null = POOR (1 bar). Thresholds chosen for PTT use case where <300ms is acceptable, >600ms is unusable.
- **Canvas signal bars:** Material Icons library has limited signal bar icons (SignalCellular4Bar, SignalCellularAlt, etc. may not exist or have inconsistent naming). Canvas drawing provides full control over 4 bars of increasing height with color tinting.
- **ConcurrentHashMap for thread safety:** TransmissionHistoryRepository uses ConcurrentHashMap because channel transmissions can end concurrently on different threads. Prevents race conditions on circular buffer operations.
- **Long-press for transmission history:** Changed ChannelRow long-press from "set primary channel" to "show transmission history" per plan requirement. Primary channel can still be set via drag-and-drop or settings (future UX patterns).
- **NetworkMonitor lifecycle in ViewModel:** Simplifies integration - ViewModel owns NetworkMonitor lifecycle, starts on init, stops on onCleared. Avoids manual start/stop in Application or Activity.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed missing manualRetry() method in ViewModel**
- **Found during:** Task 1 verification
- **Issue:** ConnectionBanner onRetry callback requires viewModel.manualRetry() but method doesn't exist (added in plan 10-02 but not committed)
- **Fix:** Added manualRetry() method to ChannelListViewModel delegating to signalingClient.manualRetry()
- **Files modified:** android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
- **Committed in:** 6fd6cb9 (Task 1)

**2. [Rule 3 - Blocking] Fixed missing connectionState parameter on BottomBar and ConnectionBanner**
- **Found during:** Task 1 verification
- **Issue:** BottomBar and ConnectionBanner require connectionState parameter (added in plan 10-02) but ChannelListScreen calls missing the parameter
- **Fix:** Added connectionState parameter to both BottomBar and ConnectionBanner calls in ChannelListScreen
- **Files modified:** android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
- **Committed in:** 6fd6cb9 (Task 1)

**3. [Rule 3 - Blocking] Fixed ProfileDrawer signature mismatch**
- **Found during:** Task 2 verification
- **Issue:** Plan 10-05 simplified ProfileDrawer to basic parameters (userName, userEmail, onSwitchEvent, onSettings, onLogout, content) but ChannelListScreen still calling with old signature (all PTT settings parameters). Compilation failed with "No parameter with name 'pttMode' found" errors.
- **Fix:** Updated ProfileDrawer call in ChannelListScreen to simplified signature per plan 10-05
- **Files modified:** android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
- **Committed in:** 3bc1c34 (Task 2)

**4. [Rule 3 - Blocking] Added @OptIn for ExperimentalCoroutinesApi**
- **Found during:** Task 2 verification
- **Issue:** flatMapLatest usage in transmissionHistory StateFlow requires ExperimentalCoroutinesApi opt-in (Kotlin compiler warning)
- **Fix:** Added import and @OptIn(ExperimentalCoroutinesApi::class) annotation on transmissionHistory property
- **Files modified:** android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
- **Committed in:** 3bc1c34 (Task 2)

---

**Total deviations:** 4 auto-fixed (4 blocking issues from incomplete plan 10-02 and plan 10-05 execution)
**Impact on plan:** All fixes were pre-existing issues from previous plans not fully integrated. No scope creep. Plan executed exactly as specified.

## Issues Encountered
- **Plan 10-05 incomplete integration:** Plan 10-05 simplified ProfileDrawer but didn't update ChannelListScreen to match. This created a broken compilation state in master. Fixed as Rule 3 blocking issue.
- **File corruption during edits:** Repeatedly encountered file corruption where ProfileDrawer parameters were removed mid-edit. Root cause: Plan 10-05 changed ProfileDrawer signature but git HEAD had mixed state. Resolved by restoring from git and applying correct simplified signature.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- NetworkQualityIndicator and TransmissionHistorySheet complete
- Ready for Plan 05: Dedicated Settings screen (consolidate all PTT/scan/hardware settings)
- Network quality metrics exposed via ViewModel for future health monitoring UX
- Transmission history foundation ready for future enhancements (persistence, filtering)

## Self-Check

**Files:**
- FOUND: android/app/src/main/java/com/voiceping/android/domain/model/NetworkQuality.kt
- FOUND: android/app/src/main/java/com/voiceping/android/domain/model/TransmissionHistoryEntry.kt
- FOUND: android/app/src/main/java/com/voiceping/android/data/repository/TransmissionHistoryRepository.kt
- FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/components/NetworkQualityIndicator.kt
- FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/components/TransmissionHistorySheet.kt

**Commits:**
- FOUND: 6fd6cb9 (Task 1: Create NetworkQuality model, TransmissionHistoryRepository, and domain models)
- FOUND: 3bc1c34 (Task 2: Create NetworkQualityIndicator, TransmissionHistorySheet, and wire to UI)

**Result:** PASSED
