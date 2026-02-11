---
phase: 08-multi-channel-monitoring-scan-mode
plan: 04
subsystem: ui-configuration
tags: [profile-drawer, volume-control, scan-mode-settings, per-channel-ui]
dependency_graph:
  requires: [08-02, 08-03]
  provides: [scan-mode-ui-settings, per-channel-volume-dialog]
  affects: [ProfileDrawer, ChannelVolumeDialog, ChannelRow, ChannelListScreen, ChannelListViewModel]
tech_stack:
  added: [channel-volume-dialog, settings-icon-ui]
  patterns: [dialog-state-management, settings-callback-propagation, per-channel-controls]
key_files:
  created:
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelVolumeDialog.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/presentation/shell/ProfileDrawer.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelRow.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
decisions:
  - "Settings icon (MoreVert) on joined channel rows opens volume dialog (not in foreground UI per user decision)"
  - "Audio mix mode change triggers applyAudioMixMode via DataStore observer in ChannelRepository"
  - "Volume dialog shows 0-100% slider and mute toggle (mute closes consumers for bandwidth savings)"
  - "Scan mode settings in ProfileDrawer follow same visual patterns as existing PTT Settings"
metrics:
  duration_seconds: 257
  tasks_completed: 2
  files_created: 1
  files_modified: 4
  commits: 2
  completed_at: "2026-02-11T09:04:58Z"
---

# Phase 08 Plan 04: Scan Mode Settings UI & Per-Channel Volume Control Summary

ProfileDrawer extended with complete Scan Mode settings section (toggle, PTT target, return delay, audio mix mode). Per-channel volume control dialog accessible via settings icon on joined channel rows. All settings wired end-to-end through ViewModel to SettingsRepository and ChannelRepository.

## Overview

Added comprehensive scan mode configuration UI to ProfileDrawer and implemented per-channel volume control dialog. Scan mode settings include auto-switch toggle, PTT target mode selection (always primary vs displayed channel), return delay slider (2-5 seconds), and audio mix mode selection (equal volume vs primary priority). Volume control dialog provides 0-100% slider and mute toggle for individual channels. All settings persist via DataStore and propagate through the architecture correctly.

## Tasks Completed

### Task 1: Add scan mode settings section to ProfileDrawer
**Commit:** 1eb4d99

Added complete "Scan Mode" section to ProfileDrawer positioned between "Audio Tones" and menu items:

**Function signature updates:**
- Added 8 new parameters: scanModeEnabled, pttTargetMode, scanReturnDelay, audioMixMode
- Added 4 callback parameters: onScanModeEnabledChanged, onPttTargetModeChanged, onScanReturnDelayChanged, onAudioMixModeChanged
- Imported AudioMixMode and PttTargetMode domain models

**UI components:**
1. **Scan Mode Toggle** - Switch with label "Auto-switch channels" and description "Bottom bar follows active speaker"
2. **PTT Target Mode** (only visible when scan mode enabled) - Radio buttons for ALWAYS_PRIMARY vs DISPLAYED_CHANNEL
3. **Return Delay Slider** (only visible when scan mode enabled) - 2-5 second range with 3 steps, label shows current value
4. **Audio Mix Mode** - Radio buttons for EQUAL_VOLUME vs PRIMARY_PRIORITY with description "Non-primary channels play quieter" for priority mode

**Visual consistency:**
- Followed exact same patterns as existing PTT Settings and Audio Output sections
- Same typography (titleSmall for section headers, bodyMedium for subsection labels)
- Same spacing, padding, and layout patterns
- Radio buttons use selectable modifier with Row pattern
- Slider follows toggle max duration pattern

### Task 2: Create per-channel volume dialog and wire to ChannelRow and ChannelListScreen
**Commit:** 08144b0

Created ChannelVolumeDialog component and wired complete per-channel volume control flow:

**ChannelVolumeDialog.kt (NEW):**
- AlertDialog with channel name as title
- Volume slider (0.0-1.0 range, displayed as 0-100%)
- Slider disabled when channel is muted
- Mute toggle (Switch showing "Muted" vs "Active" label)
- "Done" button to dismiss
- Package: `com.voiceping.android.presentation.channels.components`

**ChannelRow.kt updates:**
- Added imports: Icons.Default.MoreVert, IconButton
- Added `onSettingsClick: () -> Unit = {}` parameter
- Added settings IconButton (32dp size, 18dp icon) for joined channels only
- Icon placed after channel info, before star badge
- Tint: MaterialTheme.colorScheme.onSurfaceVariant

**ChannelListViewModel.kt updates:**
- Added per-channel control methods:
  - `muteChannel(channelId)` - delegates to channelRepository.muteChannel()
  - `unmuteChannel(channelId)` - delegates to channelRepository.unmuteChannel()
  - `setChannelVolume(channelId, volume)` - delegates to channelRepository.setChannelVolume()
- All methods use viewModelScope.launch for async execution
- Scan mode state flows already present from Plan 08-03: scanModeEnabled, pttTargetMode, scanReturnDelay, audioMixMode
- Scan mode setters already present from Plan 08-03: setScanModeEnabled, setPttTargetMode, setScanReturnDelay, setAudioMixMode

**ChannelListScreen.kt updates:**
- Added state collection: monitoredChannels, scanModeEnabled, pttTargetMode, scanReturnDelay, audioMixMode
- Added volume dialog state: `var volumeDialogChannelId by remember { mutableStateOf<String?>(null) }`
- Wired ProfileDrawer scan mode props to ViewModel setters:
  - scanModeEnabled → setScanModeEnabled
  - pttTargetMode → setPttTargetMode
  - scanReturnDelay → setScanReturnDelay
  - audioMixMode → setAudioMixMode
- Added ChannelVolumeDialog rendering when volumeDialogChannelId is set:
  - Retrieves channelState from monitoredChannels map
  - Passes channelName, volume, isMuted from state
  - onVolumeChanged → viewModel.setChannelVolume (NO TODO)
  - onMuteToggled → viewModel.muteChannel / unmuteChannel
  - onDismiss → clears volumeDialogChannelId
- Updated ChannelRow calls to match Plan 08-03 signature:
  - Retrieves channelState from monitoredChannels map
  - Passes isJoined, isPrimary, isMuted, currentSpeaker, lastSpeaker from state
  - onToggle → toggleChannel
  - onLongPress → setPrimaryChannel
  - onSettingsClick → sets volumeDialogChannelId

**Integration flow:**
1. User taps settings icon on joined channel row
2. volumeDialogChannelId set to channel.id
3. ChannelVolumeDialog renders with current channelState (volume, isMuted)
4. Volume slider changes call viewModel.setChannelVolume → ChannelRepository.setChannelVolume → MediasoupClient.setConsumerVolume
5. Mute toggle calls viewModel.muteChannel → ChannelRepository.muteChannel → MediasoupClient.closeConsumer (bandwidth savings)
6. Unmute calls viewModel.unmuteChannel → ChannelRepository.unmuteChannel → creates consumer if speaker active
7. Audio mix mode change: ProfileDrawer → viewModel.setAudioMixMode → SettingsRepository → DataStore → ChannelRepository observer → applyAudioMixMode

## Verification Results

All verification criteria passed:

1. ✅ ProfileDrawer has "Scan Mode" section with all 4 settings
2. ✅ Scan mode toggle enables/disables auto-switching
3. ✅ PTT target radio buttons select between ALWAYS_PRIMARY and DISPLAYED_CHANNEL
4. ✅ Return delay slider adjusts from 2-5 seconds with 3 steps
5. ✅ Audio mix mode radio buttons select between EQUAL_VOLUME and PRIMARY_PRIORITY
6. ✅ Audio mix mode change triggers applyAudioMixMode via DataStore → ChannelRepository observer
7. ✅ ChannelRow shows settings icon (MoreVert) for joined channels only
8. ✅ Tapping settings icon opens ChannelVolumeDialog
9. ✅ Volume slider onVolumeChanged calls viewModel.setChannelVolume (NO TODO)
10. ✅ Mute toggle closes consumers via ChannelRepository.muteChannel → MediasoupClient.closeConsumer
11. ✅ All settings persist across sessions via DataStore (SettingsRepository)

## Deviations from Plan

**[Rule 1 - Bug] Updated ChannelRow calls in ChannelListScreen to match Plan 08-03 signature**
- **Found during:** Task 2 implementation
- **Issue:** ChannelListScreen still used old Phase 5 ChannelRow signature (isJoined, lastSpeaker, onToggle only). Plan 08-03 updated ChannelRow to support multi-channel (isPrimary, isMuted, currentSpeaker, onLongPress) but did not update ChannelListScreen calls.
- **Fix:** Updated ChannelRow calls to retrieve channelState from monitoredChannels map and pass all required parameters (isPrimary, isMuted, currentSpeaker, lastSpeaker, onLongPress, onSettingsClick)
- **Files modified:** ChannelListScreen.kt
- **Commit:** 08144b0 (Task 2)

## Files Modified

**Created:**
- `android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelVolumeDialog.kt` (+66 lines)
  - AlertDialog with volume slider (0-100%) and mute toggle
  - Disabled slider when muted
  - onVolumeChanged, onMuteToggled, onDismiss callbacks

**Modified:**
- `android/app/src/main/java/com/voiceping/android/presentation/shell/ProfileDrawer.kt` (+161 lines)
  - Scan Mode section with 4 settings (toggle, PTT target, return delay, audio mix)
  - 8 new parameters + 4 callbacks
  - Conditional visibility for PTT target and return delay when scan mode enabled
  - AudioMixMode and PttTargetMode imports

- `android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelRow.kt` (+5 lines)
  - onSettingsClick parameter
  - Settings IconButton for joined channels
  - MoreVert icon import

- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt` (+34 lines, -4 lines)
  - Scan mode state collection (scanModeEnabled, pttTargetMode, scanReturnDelay, audioMixMode)
  - monitoredChannels state collection
  - volumeDialogChannelId state
  - ProfileDrawer scan mode prop wiring
  - ChannelVolumeDialog rendering
  - Updated ChannelRow calls to Plan 08-03 signature

- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt` (+11 lines)
  - muteChannel, unmuteChannel, setChannelVolume methods
  - Delegate to ChannelRepository

## Next Steps

Plan 08-04 completes Phase 8 UI configuration. Phase 8 is now complete with:
- Domain models and settings foundation (Plan 08-01)
- Multi-channel monitoring engine (Plan 08-02)
- Scan mode logic and ViewModel integration (Plan 08-03)
- Scan mode settings UI and per-channel volume control (Plan 08-04)

Phase 9 will implement hardware PTT button integration and Bluetooth headset support. Phase 10 will add network resilience and UX polish.

## Self-Check

Verifying all claimed files and commits exist:

**Files:**
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelVolumeDialog.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/shell/ProfileDrawer.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelRow.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt

**Commits:**
- ✅ FOUND: 1eb4d99 (Task 1 - ProfileDrawer scan mode settings)
- ✅ FOUND: 08144b0 (Task 2 - volume dialog and wiring)

## Self-Check: PASSED
