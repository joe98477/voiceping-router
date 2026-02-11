---
phase: 08-multi-channel-monitoring-scan-mode
plan: 02
subsystem: multi-channel-engine
tags: [repository-refactor, notification, multi-channel, consumer-management]
dependency_graph:
  requires: [08-01]
  provides: [multi-channel-monitoring, per-channel-consumer-tracking, audio-mix-mode, channel-volume-control]
  affects: [ChannelRepository, ChannelMonitoringService, MediasoupClient]
tech_stack:
  added: [multi-channel-state-flow, per-channel-job-tracking]
  patterns: [map-based-state-management, per-channel-observer, explicit-consumer-lifecycle]
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
    - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
    - android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt
decisions:
  - "Muting closes consumers via closeConsumer() (not volume 0) for bandwidth savings"
  - "Unmuting immediately creates consumer if channel has active speaker (not waiting for next SPEAKER_CHANGED)"
  - "Single RecvTransport shared across all channels (not per-channel transport)"
  - "Audio mix mode: EQUAL_VOLUME vs PRIMARY_PRIORITY (50% reduction for non-primary)"
  - "Max 5 channels enforced with descriptive error message"
metrics:
  duration_seconds: 181
  tasks_completed: 2
  files_created: 0
  files_modified: 3
  commits: 2
  completed_at: "2026-02-11T08:57:38Z"
---

# Phase 08 Plan 02: Multi-Channel Monitoring Engine Summary

ChannelRepository refactored from single-channel to multi-channel monitoring with per-channel consumer management, mute/unmute with explicit consumer lifecycle, volume control, and audio mix modes. Notification updated for multi-channel display.

## Overview

Converted ChannelRepository from managing a single channel to managing up to 5 concurrent channels with independent consumer lifecycles, speaker observation, and mute states. Each channel tracks its own speakers, consumers, and monitoring state. Added audio mixing capabilities (equal volume vs primary priority) and per-channel volume control. Notification now displays "Primary (monitoring N others)" format.

## Tasks Completed

### Task 1: Convert ChannelRepository to multi-channel monitoring
**Commit:** fe065b5

Completely rewrote ChannelRepository to support multi-channel monitoring:

**Multi-channel state management:**
- Replaced single-channel fields with `Map<String, ChannelMonitoringState>` for monitoring up to 5 channels
- Added `primaryChannelId: StateFlow<String?>` with auto-assignment on first join
- Added `channelConsumers: Map<String, Map<String, String>>` for per-channel consumer tracking (channelId -> producerId -> consumerId)
- Added `speakerObserverJobs: Map<String, Job>` for per-channel speaker observation
- Added `lastSpeakerFadeJobs: Map<String, Job>` for per-channel fade timers

**Core multi-channel methods:**
- `joinChannel(channelId, channelName, teamName)` - Max 5 channel guard, creates ChannelMonitoringState, starts per-channel speaker observation, persists state
- `leaveChannel(channelId)` - Closes per-channel consumers, removes channel state, reassigns primary if needed, stops service if last channel
- `observeSpeakerChangesForChannel(channelId)` - Per-channel speaker event listener with speakerStartTime tracking
- `setPrimaryChannel(channelId)` - Updates isPrimary flags across all channels, triggers audio mix mode recalculation, updates notification

**Mute/unmute with explicit consumer lifecycle:**
- `muteChannel(channelId)` - Closes ALL consumers for channel via `mediasoupClient.closeConsumer()` (bandwidth savings), clears currentSpeaker
- `unmuteChannel(channelId)` - Immediately creates consumer if channel has active speaker (not waiting for next SPEAKER_CHANGED event)
- Mute closes consumers (not just volume 0) per user decision for bandwidth optimization
- Unmute explicitly checks `currentSpeaker != null` and creates consumer immediately

**Audio control:**
- `setChannelVolume(channelId, volume)` - Sets per-channel volume (0.0-1.0), applies to active consumers via `mediasoupClient.setConsumerVolume()`
- `applyAudioMixMode(audioMixMode)` - EQUAL_VOLUME (all channels at stored volume) vs PRIMARY_PRIORITY (non-primary at 50% volume)
- Audio mix mode observer in init block: watches `settingsRepository.getAudioMixMode()`, applies on changes
- New consumers automatically get correct volume via `applyAudioMixMode()` call after creation

**Helper methods:**
- `muteAllExceptPrimary()` - Quick action for user workflow
- `unmuteAllChannels()` - Restore all channels
- `updateChannelState()` - Immutable state update helper
- `updateServiceNotification()` - Sends notification update with monitoring count

**Integration updates:**
- Phone call handling pauses ALL channels (closes all consumers across all channels)
- Notification mute observer applies to primary channel only
- SettingsRepository injected for state persistence
- State persisted: monitored channel IDs, primary channel ID

**MediasoupClient.setConsumerVolume:**
- Added `setConsumerVolume(consumerId: String, volume: Float)` method
- Matches `closeConsumer` pattern with TODO for library integration
- Used by `setChannelVolume` and `applyAudioMixMode`

**Transport architecture:**
- Single RecvTransport shared across all channels (created on first join, reused for all subsequent channels)
- Per-channel consumers created on same transport when speakers start

### Task 2: Update ChannelMonitoringService notification for multi-channel display
**Commit:** f771b76

Updated notification to display multi-channel monitoring information:

**New fields and constants:**
- Added `EXTRA_MONITORING_COUNT` intent extra constant
- Added `EXTRA_PTT_TARGET_CHANNEL_ID` intent extra constant (for future PTT notification action)
- Added `monitoringCount: Int` field (tracks number of non-primary channels)
- Added `pttTargetChannelId: String?` field (reserved for PTT action routing)

**Intent handler updates:**
- ACTION_START reads EXTRA_MONITORING_COUNT and EXTRA_PTT_TARGET_CHANNEL_ID
- ACTION_UPDATE_CHANNEL reads new extras, updates only if any value changed

**Notification display:**
- Title format: `"$channelName (monitoring $monitoringCount others)"` when `monitoringCount > 0`
- Falls back to channel name only for single channel
- Example: "Alpha (monitoring 3 others)" when monitoring 4 total channels

**Preserved functionality:**
- Mute/Disconnect actions unchanged
- Notification mute applies to primary channel (ChannelRepository handles routing)
- PTT target channel tracking prepared for future notification PTT action

## Verification Results

All verification criteria passed:

1. ✅ ChannelRepository has `monitoredChannels: StateFlow<Map<String, ChannelMonitoringState>>`
2. ✅ ChannelRepository has `primaryChannelId: StateFlow<String?>`
3. ✅ `channelConsumers` map tracks per-channel consumers
4. ✅ Max 5 channel guard in joinChannel with descriptive error
5. ✅ `setPrimaryChannel()`, `muteChannel()`, `unmuteChannel()`, `muteAllExceptPrimary()` methods exist
6. ✅ `setChannelVolume(channelId, volume)` calls `mediasoupClient.setConsumerVolume()`
7. ✅ `applyAudioMixMode(audioMixMode)` adjusts volumes based on isPrimary
8. ✅ `unmuteChannel` explicitly creates consumer if channel has active currentSpeaker
9. ✅ `muteChannel` explicitly calls `mediasoupClient.closeConsumer(consumerId)` for each consumer
10. ✅ Per-channel speaker observation via `observeSpeakerChangesForChannel()`
11. ✅ `speakerStartTime` set on speaker start in observeSpeakerChangesForChannel
12. ✅ settingsRepository persistence calls in joinChannel, leaveChannel, setPrimaryChannel
13. ✅ `updateServiceNotification` helper sends EXTRA_MONITORING_COUNT
14. ✅ audioMixMode observer in init block calling applyAudioMixMode on changes
15. ✅ MediasoupClient has `setConsumerVolume(consumerId: String, volume: Float)` method
16. ✅ ChannelMonitoringService has EXTRA_MONITORING_COUNT and EXTRA_PTT_TARGET_CHANNEL_ID constants
17. ✅ Notification shows "(monitoring N others)" format when multiple channels joined
18. ✅ Phone call handling closes consumers for ALL channels

## Deviations from Plan

None - plan executed exactly as written.

## Files Modified

**Modified:**
- `android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt` (+364 lines, -89 lines)
  - Complete refactor from single-channel to multi-channel management
  - Per-channel consumer tracking, speaker observation, mute/unmute, volume control
  - Audio mix mode implementation
  - SettingsRepository integration for persistence

- `android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt` (+11 lines)
  - Added `setConsumerVolume(consumerId, volume)` method

- `android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt` (+23 lines, -4 lines)
  - Multi-channel notification display
  - EXTRA_MONITORING_COUNT and EXTRA_PTT_TARGET_CHANNEL_ID support

## Next Steps

Plan 08-03 can now implement scan mode logic in ViewModel using the multi-channel monitoring state. ChannelRepository exposes `monitoredChannels` map with `speakerStartTime` for "most recently started transmission" scan logic. Plan 08-04 will wire scan mode UI to bottom bar controls.

## Self-Check

Verifying all claimed files and commits exist:

**Files:**
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt

**Commits:**
- ✅ FOUND: fe065b5 (Task 1 - multi-channel ChannelRepository)
- ✅ FOUND: f771b76 (Task 2 - notification update)

## Self-Check: PASSED
