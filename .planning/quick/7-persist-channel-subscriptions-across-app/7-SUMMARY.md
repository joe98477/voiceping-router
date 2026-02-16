---
phase: quick-7
plan: 01
subsystem: android-client
tags: [datastore, channel-persistence, auto-rejoin, viewmodel-lifecycle]

requires:
  - phase: v2.0
    provides: "ChannelRepository with joinChannel/leaveChannel and SettingsRepository DataStore persistence"
provides:
  - "Channel subscriptions persist across app restart via DataStore auto-rejoin"
  - "disconnectAllPreservingState / disconnectAllAndClearState split for lifecycle vs explicit actions"
affects: [channel-management, logout-flow, event-switch-flow]

tech-stack:
  added: []
  patterns:
    - "Preserve-state vs clear-state disconnect pattern for ViewModel lifecycle management"
    - "Auto-rejoin with channel-list-ready wait and timeout"

key-files:
  created: []
  modified:
    - "android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt"
    - "android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt"
    - "android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt"

key-decisions:
  - "disconnectAllPreservingState skips both LEAVE_CHANNEL and clearMonitoredChannels -- WebSocket disconnect handles server cleanup"
  - "Auto-rejoin waits up to 10s for channel list to load before giving up"
  - "Primary channel is joined first during auto-rejoin to restore correct primary assignment"

duration: 3min
completed: 2026-02-16
---

# Quick Task 7: Persist Channel Subscriptions Across App Summary

**Auto-rejoin persisted channels on app restart via DataStore, with preserve-state/clear-state disconnect split for lifecycle vs explicit logout**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-16T09:38:22Z
- **Completed:** 2026-02-16T09:41:20Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Split `disconnectAll()` into `disconnectAllPreservingState()` (keeps DataStore) and `disconnectAllAndClearState()` (clears DataStore)
- ViewModel `onCleared()` now preserves persisted channel IDs so app restart can auto-rejoin
- Added auto-rejoin logic in ChannelListViewModel init that reads persisted channels, waits for channel list to load, and joins them in order with primary first
- Logout and switch-event explicitly clear persisted state so channels are not restored inappropriately

## Task Commits

Each task was committed atomically:

1. **Task 1: Split disconnectAll into preserve-state and clear-state variants** - `ea086b5` (feat)
2. **Task 2: Auto-rejoin persisted channels on ChannelListViewModel init** - `03fa2d8` (feat)

## Files Created/Modified

- `android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt` - Split disconnectAll into preserving and clearing variants
- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt` - Added auto-rejoin logic, logout/switchEvent methods, updated onCleared
- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt` - Updated ProfileDrawer callbacks to call viewModel.logout()/switchEvent() before navigation

## Decisions Made

- `disconnectAllPreservingState` skips both LEAVE_CHANNEL server messages and DataStore clearing -- the WebSocket disconnect itself tells the server the user left all channels
- Auto-rejoin uses `withTimeoutOrNull(10_000L)` waiting for channel list to load -- if offline cache is slow, we skip rather than block
- Primary channel is joined first during auto-rejoin so it naturally becomes the primary via the existing first-channel-is-primary logic in `joinChannel`

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Steps

- Test on physical device: close app, reopen, verify same channels are joined
- Test logout flow: verify channels are NOT restored after re-login
- Test switch-event flow: verify old event channels are cleared

---
*Quick Task: 7-persist-channel-subscriptions-across-app*
*Completed: 2026-02-16*
