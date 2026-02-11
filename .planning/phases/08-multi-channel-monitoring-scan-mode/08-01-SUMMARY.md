---
phase: 08-multi-channel-monitoring-scan-mode
plan: 01
subsystem: domain-models-settings
tags: [foundation, data-models, persistence, scan-mode]
dependency_graph:
  requires: []
  provides: [ChannelMonitoringState, ScanModeState, PttTargetMode, AudioMixMode, scan-settings-persistence]
  affects: [SettingsRepository]
tech_stack:
  added: [stringSetPreferencesKey]
  patterns: [enum-persistence-via-name, datastore-flow-api]
key_files:
  created:
    - android/app/src/main/java/com/voiceping/android/domain/model/ChannelMonitoringState.kt
    - android/app/src/main/java/com/voiceping/android/domain/model/ScanModeState.kt
    - android/app/src/main/java/com/voiceping/android/domain/model/PttTargetMode.kt
    - android/app/src/main/java/com/voiceping/android/domain/model/AudioMixMode.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt
decisions: []
metrics:
  duration_seconds: 127
  tasks_completed: 2
  files_created: 4
  files_modified: 1
  commits: 2
  completed_at: "2026-02-11T08:52:12Z"
---

# Phase 08 Plan 01: Domain Models & Settings Foundation Summary

Domain models for multi-channel monitoring (ChannelMonitoringState, ScanModeState, PttTargetMode, AudioMixMode) created and scan mode settings persistence added to SettingsRepository using DataStore.

## Overview

Created the foundational domain models and settings persistence layer required by all subsequent multi-channel monitoring and scan mode plans. This provides shared data structures for channel state tracking, scan mode configuration, and user preferences for PTT target selection and audio mixing.

## Tasks Completed

### Task 1: Create domain models for multi-channel monitoring and scan mode
**Commit:** fd79b23

Created 4 new domain model files in the domain/model package:

1. **ChannelMonitoringState.kt** - Per-channel monitoring state tracking:
   - channelId, channelName, teamName, isPrimary flags
   - isMuted, currentSpeaker, lastSpeaker references
   - speakerStartTime (critical for "most recently started transmission" logic)
   - consumerId (mediasoup consumer ID for management)
   - volume (0.0-1.0 range for per-channel mixing)

2. **ScanModeState.kt** - Scan mode UI display state:
   - enabled (defaults true per user decision: ON when 2+ channels)
   - displayedChannelId (current bottom bar channel)
   - isLocked (manual lock via bottom bar tap)
   - returnDelaySeconds (defaults 2s)

3. **PttTargetMode.kt** - PTT target selection enum:
   - ALWAYS_PRIMARY (default)
   - DISPLAYED_CHANNEL

4. **AudioMixMode.kt** - Audio mix strategy enum:
   - EQUAL_VOLUME (default)
   - PRIMARY_PRIORITY (primary louder, secondaries ducked)

All models match user decisions from 08-RESEARCH.md regarding defaults and behavior.

### Task 2: Extend SettingsRepository with scan mode and multi-channel persistence
**Commit:** 9d55bda

Extended SettingsRepository with 6 new DataStore settings and persistence methods:

**New Keys:**
- MONITORED_CHANNEL_IDS (stringSetPreferencesKey for Set<String>)
- PRIMARY_CHANNEL_ID (stringPreferencesKey)
- SCAN_MODE_ENABLED (booleanPreferencesKey)
- SCAN_RETURN_DELAY (intPreferencesKey)
- PTT_TARGET_MODE (stringPreferencesKey)
- AUDIO_MIX_MODE (stringPreferencesKey)

**Getter/Setter Pairs (6 pairs):**
1. setMonitoredChannels/getMonitoredChannels - Set<String>, default emptySet()
2. setPrimaryChannel/getPrimaryChannel - String?, default null
3. setScanModeEnabled/getScanModeEnabled - Boolean, default true
4. setScanReturnDelay/getScanReturnDelay - Int, default 2 seconds
5. setPttTargetMode/getPttTargetMode - PttTargetMode, default ALWAYS_PRIMARY
6. setAudioMixMode/getAudioMixMode - AudioMixMode, default EQUAL_VOLUME

**Additional Method:**
- clearMonitoredChannels() - Removes both monitored channel IDs and primary channel (used on logout/disconnect)

**Implementation Details:**
- Enums use name/valueOf pattern matching existing PttMode persistence
- IllegalArgumentException handling with fallback to defaults
- Flow<T> return types for reactive observation
- All defaults align with user decisions from research phase

## Verification Results

All verification criteria passed:

1. ✅ All 4 domain model files exist in domain/model package
2. ✅ SettingsRepository has 6 new DataStore keys and 6 getter/setter pairs
3. ✅ stringSetPreferencesKey used for monitored channel IDs (Set<String>)
4. ✅ Default values: scan mode ON, return delay 2s, PTT target ALWAYS_PRIMARY, audio mix EQUAL_VOLUME
5. ✅ Enums use name/valueOf pattern matching existing PTT mode persistence
6. ✅ All existing PTT settings preserved (no breaking changes)
7. ✅ ChannelMonitoringState has all critical fields (speakerStartTime, consumerId)

## Deviations from Plan

None - plan executed exactly as written.

## Files Modified

**Created:**
- `android/app/src/main/java/com/voiceping/android/domain/model/ChannelMonitoringState.kt` (800 bytes)
- `android/app/src/main/java/com/voiceping/android/domain/model/ScanModeState.kt` (433 bytes)
- `android/app/src/main/java/com/voiceping/android/domain/model/PttTargetMode.kt` (369 bytes)
- `android/app/src/main/java/com/voiceping/android/domain/model/AudioMixMode.kt` (354 bytes)

**Modified:**
- `android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt` (+104 lines)

## Next Steps

Plan 08-02 can now proceed with ChannelRepository refactor to use ChannelMonitoringState and manage multiple concurrent audio consumers. Plan 08-03 will implement scan mode logic in ChannelListViewModel using ScanModeState and persisted settings.

## Self-Check

Verifying all claimed files and commits exist:

**Files:**
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/domain/model/ChannelMonitoringState.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/domain/model/ScanModeState.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/domain/model/PttTargetMode.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/domain/model/AudioMixMode.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt

**Commits:**
- ✅ FOUND: fd79b23 (Task 1 - domain models)
- ✅ FOUND: 9d55bda (Task 2 - SettingsRepository)

## Self-Check: PASSED
