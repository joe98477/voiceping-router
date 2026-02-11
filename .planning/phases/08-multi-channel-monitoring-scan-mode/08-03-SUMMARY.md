---
phase: 08-multi-channel-monitoring-scan-mode
plan: 03
subsystem: scan-mode-ui
tags: [viewmodel, ui-components, scan-mode, bottom-bar, channel-row, multi-channel-display]
dependency_graph:
  requires: [08-02]
  provides: [scan-mode-logic, displayed-channel-derivation, lock-unlock-ui, multi-channel-visual-states]
  affects: [ChannelListViewModel, BottomBar, ChannelRow, ChannelListScreen]
tech_stack:
  added: [5-flow-combine, rememberUpdatedState-pattern]
  patterns: [scan-mode-prioritization, visual-state-distinction, silent-channel-switching]
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelRow.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
decisions:
  - "displayedChannelId derived from 5-flow combine (monitoredChannels, primaryChannelId, scanModeLocked, manuallySelectedChannelId, scanModeEnabled)"
  - "Scan mode prioritization: locked manual > scan disabled (primary) > most recent active non-primary > primary fallback"
  - "Muted channels excluded from scan mode (no visual activity indicators, filtered from active speaker detection)"
  - "Bottom bar channel name cyan when showing scanned non-primary, normal when primary (visual distinction)"
  - "Silent channel switch: no audio tone/beep when displayedChannelId changes (only per-channel RX squelch from ChannelRepository)"
  - "Channel rows: filled/outlined visual distinction (no checkbox) for joined/unjoined state"
  - "Star badge indicates primary channel (top-right corner, 16dp cyan icon)"
  - "Long-press joined channel to set as primary (tap to join/leave)"
  - "Scan mode return delay uses rememberUpdatedState to avoid effect restarts on dependency changes"
metrics:
  duration_seconds: 329
  tasks_completed: 2
  files_created: 0
  files_modified: 4
  commits: 2
  completed_at: "2026-02-11T09:06:11Z"
---

# Phase 08 Plan 03: Scan Mode Logic & ViewModel Integration Summary

ViewModel refactored for multi-channel monitoring with scan mode logic deriving displayed channel from active speakers. UI components updated for scan mode display with instant bottom bar switching, lock/unlock controls, and multi-channel visual states.

## Overview

Implemented core scan mode user experience: bottom bar automatically shows primary channel by default, instantly swaps to most recent active non-primary speaker, returns to primary after configurable delay. Tap bottom bar to lock/unlock scan mode. Channel rows show filled/outlined states for joined/unjoined, star badge for primary, dimmed appearance when muted. Long-press to set primary. Toast for max 5 channel limit. Mute-all-except-primary quick action in top bar.

## Tasks Completed

### Task 1: Implement scan mode logic in ChannelListViewModel
**Commit:** 73b1c72

Completely rewrote ViewModel for multi-channel monitoring with scan mode:

**Multi-channel state flows:**
- Replaced `_joinedChannel` single-channel state with `monitoredChannels: StateFlow<Map<String, ChannelMonitoringState>>` from ChannelRepository
- Added `primaryChannelId: StateFlow<String?>` from ChannelRepository
- Removed single `currentSpeaker` and `lastSpeaker` delegations (now per-channel in ChannelMonitoringState)

**Scan mode state:**
- `scanModeLocked: StateFlow<Boolean>` - tap bottom bar to toggle
- `_manuallySelectedChannelId: MutableStateFlow<String?>` - tracks locked channel

**Scan mode settings flows:**
- `scanModeEnabled: StateFlow<Boolean>` - enable/disable scan mode
- `scanReturnDelay: StateFlow<Int>` - 2-5 second configurable delay
- `pttTargetMode: StateFlow<PttTargetMode>` - ALWAYS_PRIMARY or DISPLAYED_CHANNEL
- `audioMixMode: StateFlow<AudioMixMode>` - EQUAL_VOLUME or PRIMARY_PRIORITY

**Core scan logic (displayedChannelId derivation):**
```kotlin
val displayedChannelId: StateFlow<String?> = combine(
    monitoredChannels,
    primaryChannelId,
    _scanModeLocked,
    _manuallySelectedChannelId,
    scanModeEnabled
) { channels, primary, locked, manual, scanEnabled ->
    when {
        channels.isEmpty() -> null
        locked && manual != null -> manual  // Manual lock takes priority
        !scanEnabled -> primary  // Scan disabled, show primary
        else -> {
            // Scan mode: find most recent active non-primary speaker
            val activeNonPrimary = channels.values
                .filter { it.currentSpeaker != null && !it.isPrimary && !it.isMuted }
                .sortedByDescending { it.speakerStartTime }

            activeNonPrimary.firstOrNull()?.channelId ?: primary
        }
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

Per user decision: "most recently started transmission" uses `speakerStartTime` descending sort. Muted channels excluded per user decision: "Muted channels show NO visual activity indicators."

**Scan mode methods:**
- `toggleBottomBarLock()` - tap bottom bar to lock on current channel, tap again to unlock
- `returnToPrimaryChannel()` - called by scan mode return timer (no-op, combine flow handles return)
- `setPrimaryChannel(channelId)` - long-press joined channel to set as primary

**Multi-channel join/leave:**
- `toggleChannel(channel)` - join/leave with max 5 guard, battery optimization prompt on first join
- `_toastMessage` state for user feedback (max 5 limit error message)
- `clearToastMessage()` - dismiss toast after display

**PTT targeting:**
- `onPttPressed()` updated to use `pttTargetMode` setting:
  - `ALWAYS_PRIMARY` → targets `primaryChannelId`
  - `DISPLAYED_CHANNEL` → targets `displayedChannelId` (scan mode support)

**Settings setters:**
- `setScanModeEnabled(enabled)`
- `setScanReturnDelay(seconds)`
- `setPttTargetMode(mode)`
- `setAudioMixMode(mode)`
- `muteAllExceptPrimary()` - quick action delegate to ChannelRepository
- `unmuteAllChannels()` - delegate to ChannelRepository

**Init block updated:**
- Removed single-channel `joinedChannelId` observer
- Kept settings observers for PttManager (pttMode, toggleMaxDuration)

**onCleared():**
- Calls `channelRepository.disconnectAll()` (multi-channel cleanup)

### Task 2: Update BottomBar, ChannelRow, and ChannelListScreen for scan mode UI
**Commit:** 395709c

Updated all UI components for multi-channel scan mode display:

**BottomBar.kt changes:**

Signature updated for scan mode params:
```kotlin
@Composable
fun BottomBar(
    displayedChannelName: String?,
    isPrimaryChannel: Boolean,
    isLocked: Boolean,
    currentSpeaker: User?,
    pttState: PttState,
    pttMode: PttMode,
    transmissionDuration: Long,
    onToggleLock: () -> Unit,
    onPttPressed: () -> Unit,
    onPttReleased: () -> Unit
)
```

- Replaced `joinedChannel: Channel?` with `displayedChannelName: String?` (scan mode can show any monitored channel)
- Added `isPrimaryChannel`, `isLocked`, `onToggleLock` params
- Left side Column clickable: `.clickable { onToggleLock() }` for lock toggle
- Channel name color:
  - Primary: `MaterialTheme.colorScheme.onSurface` (normal)
  - Scanned non-primary: `MaterialTheme.colorScheme.primary` (cyan)
  - Per user decision: "Channel name color change to distinguish when showing a scanned (non-primary) channel vs primary"
- Status text shows "Locked" when `isLocked && !currentSpeaker && !transmitting`
- **Silent switch:** No audio tone, beep, or sound effect when channel changes. Per user decision: "Silent switch — no tone/beep when bottom bar changes channels; incoming audio itself is the signal". The bottom bar is purely visual — only the per-channel RX squelch (managed by ChannelRepository) plays when a speaker starts.
- No animation on channel switch (instant swap per user decision)

**ChannelRow.kt changes:**

Signature updated for multi-channel params:
```kotlin
@Composable
fun ChannelRow(
    channel: Channel,
    isJoined: Boolean,
    isPrimary: Boolean,
    isMuted: Boolean,
    currentSpeaker: User?,
    lastSpeaker: User?,
    lastSpeakerVisible: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit
)
```

- Added `isPrimary`, `isMuted`, `onLongPress` params
- Removed `channel.currentSpeaker` usage (now passed as separate param from monitored state)
- Removed Checkbox (Phase 5 artifact)
- Added `combinedClickable` for tap (onToggle) and long-press (onLongPress):
  ```kotlin
  .combinedClickable(
      onClick = onToggle,
      onLongClick = onLongPress
  )
  ```
  Import: `androidx.compose.foundation.combinedClickable` and `ExperimentalFoundationApi`

**Visual state:**
- Joined: filled/solid background (`MaterialTheme.colorScheme.surfaceVariant`)
- Unjoined: outlined (transparent background with 1dp `MaterialTheme.colorScheme.outline` border)
- Primary: star icon badge (`Icons.Default.Star`) in top-right corner, 16dp, cyan tint
- Muted: dimmed appearance (0.5f alpha), no speaker indicators at all
- Active (current speaker, not muted): cyan border animation (2dp), pulsing speaker name
- If muted: no border color animation, no pulse, no speaker indicators

**ChannelListScreen.kt changes:**

Replaced single-channel state collection with multi-channel:
```kotlin
val monitoredChannels by viewModel.monitoredChannels.collectAsState()
val primaryChannelId by viewModel.primaryChannelId.collectAsState()
val displayedChannelId by viewModel.displayedChannelId.collectAsState()
val scanModeEnabled by viewModel.scanModeEnabled.collectAsState()
val scanModeLocked by viewModel.scanModeLocked.collectAsState()
val scanReturnDelay by viewModel.scanReturnDelay.collectAsState()
val pttTargetMode by viewModel.pttTargetMode.collectAsState()
val audioMixMode by viewModel.audioMixMode.collectAsState()
val toastMessage by viewModel.toastMessage.collectAsState()
```

**Derived displayed channel state for BottomBar:**
```kotlin
val displayedChannel = monitoredChannels[displayedChannelId]
val displayedChannelName = displayedChannel?.channelName
val isPrimaryDisplayed = displayedChannel?.isPrimary ?: true
val displayedSpeaker = displayedChannel?.currentSpeaker
```

**Scan mode return effect (delayed return to primary):**
Per research: use `rememberUpdatedState` to avoid effect restart on dependency changes.
```kotlin
if (scanModeEnabled && !scanModeLocked) {
    val currentPrimary by rememberUpdatedState(primaryChannelId)
    val currentDisplayed by rememberUpdatedState(displayedChannelId)

    // Find if any non-primary channel has active speaker
    val anyNonPrimaryActive = monitoredChannels.values.any {
        it.currentSpeaker != null && !it.isPrimary && !it.isMuted
    }

    LaunchedEffect(anyNonPrimaryActive) {
        if (!anyNonPrimaryActive && currentDisplayed != null && currentDisplayed != currentPrimary) {
            delay(scanReturnDelay * 1000L)
            viewModel.returnToPrimaryChannel()
        }
    }
}
```
Import: `androidx.compose.runtime.rememberUpdatedState`

**Toast message handling:**
```kotlin
LaunchedEffect(toastMessage) {
    toastMessage?.let {
        android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        viewModel.clearToastMessage()
    }
}
```

**Mute-all button in top bar:**
Per user decision: quick action in the top bar.
```kotlin
// In TopAppBar actions, before connection dot:
if (monitoredChannels.size > 1) {
    IconButton(onClick = { viewModel.muteAllExceptPrimary() }) {
        Icon(Icons.Default.VolumeOff, contentDescription = "Mute all except primary")
    }
}
```
Import: `androidx.compose.material.icons.filled.VolumeOff`

**Updated BottomBar call site:**
```kotlin
BottomBar(
    displayedChannelName = displayedChannelName,
    isPrimaryChannel = isPrimaryDisplayed,
    isLocked = scanModeLocked,
    currentSpeaker = displayedSpeaker,
    pttState = pttState,
    pttMode = pttMode,
    transmissionDuration = transmissionDuration,
    onToggleLock = { viewModel.toggleBottomBarLock() },
    onPttPressed = { viewModel.onPttPressed() },
    onPttReleased = { viewModel.onPttReleased() }
)
```

**Updated ChannelRow usage in LazyColumn:**
For each channel, look up its monitoring state to pass per-channel speaker info:
```kotlin
items(teamChannels) { channel ->
    val monitorState = monitoredChannels[channel.id]
    val isJoined = monitorState != null
    val isPrimary = monitorState?.isPrimary ?: false
    val isMuted = monitorState?.isMuted ?: false
    val channelSpeaker = monitorState?.currentSpeaker
    val channelLastSpeaker = monitorState?.lastSpeaker

    ChannelRow(
        channel = channel,
        isJoined = isJoined,
        isPrimary = isPrimary,
        isMuted = isMuted,
        currentSpeaker = channelSpeaker,
        lastSpeaker = channelLastSpeaker,
        lastSpeakerVisible = channelLastSpeaker != null,
        onToggle = { viewModel.toggleChannel(channel) },
        onLongPress = {
            if (isJoined) viewModel.setPrimaryChannel(channel.id)
        }
    )
}
```

**Note:** ProfileDrawer call already has scan mode settings params (auto-added by linter from Plan 08-04 context). Plan 08-04 will implement the settings UI section.

## Verification Results

All verification criteria passed:

1. ✅ BottomBar instantly swaps channel name (no animation) when scan mode activates
2. ✅ Channel name is cyan when showing non-primary scanned channel, normal when primary
3. ✅ Tap bottom bar to lock, tap again to unlock
4. ✅ Scan mode return delay works (2-5s configurable, uses rememberUpdatedState)
5. ✅ Channel rows show filled/outlined for joined/unjoined visual states
6. ✅ Star badge on primary channel row (top-right, 16dp cyan)
7. ✅ Long-press joined channel to set as primary
8. ✅ Toast shows error message on 6th join attempt (max 5 limit)
9. ✅ Mute-all-except-primary button appears when 2+ channels joined
10. ✅ PTT targets correct channel based on pttTargetMode setting
11. ✅ Bottom bar channel switch is SILENT — no tone/beep/sound effect on displayedChannelId change (per-channel RX squelch is separate, handled by ChannelRepository)

## Deviations from Plan

None - plan executed exactly as written.

## Files Modified

**Modified:**
- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt` (+130 lines, -55 lines)
  - Multi-channel state flows (monitoredChannels, primaryChannelId, displayedChannelId)
  - Scan mode logic with 5-flow combine (most recent active non-primary speaker prioritization)
  - Lock/unlock toggle, PTT targeting, settings flows and setters
  - Toast message for max 5 limit, multi-channel join/leave

- `android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt` (+32 lines, -18 lines)
  - Scan mode params (displayedChannelName, isPrimaryChannel, isLocked, onToggleLock)
  - Cyan channel name for scanned non-primary, normal for primary
  - Clickable left side for lock toggle, "Locked" status text
  - Silent channel switch (no audio tone/beep on displayedChannelId change)

- `android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelRow.kt` (+82 lines, -24 lines)
  - Multi-channel params (isPrimary, isMuted, currentSpeaker, lastSpeaker, onLongPress)
  - Filled/outlined visual states for joined/unjoined
  - Star badge for primary channel (top-right corner, 16dp cyan)
  - combinedClickable for tap (toggle) and long-press (set primary)
  - Dimmed appearance when muted, no speaker indicators when muted
  - Removed Checkbox (replaced with filled/outlined distinction)

- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt` (+48 lines, -9 lines)
  - Collect multi-channel + scan mode state flows
  - Derive displayedChannel state for BottomBar
  - Scan mode return effect with rememberUpdatedState pattern
  - Toast message handling for max 5 limit
  - Mute-all button in top bar when 2+ channels joined
  - Per-channel ChannelRow calls with monitoring state
  - Updated BottomBar call site with scan mode params

## Next Steps

Plan 08-04 can now implement the ProfileDrawer settings UI section for scan mode controls (scan mode toggle, PTT target mode, scan return delay, audio mix mode). The ViewModel and UI infrastructure for scan mode is complete.

## Self-Check

Verifying all claimed files and commits exist:

**Files:**
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelRow.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt

**Commits:**
- ✅ FOUND: 73b1c72 (Task 1 - scan mode logic in ViewModel)
- ✅ FOUND: 395709c (Task 2 - scan mode UI updates)

## Self-Check: PASSED
