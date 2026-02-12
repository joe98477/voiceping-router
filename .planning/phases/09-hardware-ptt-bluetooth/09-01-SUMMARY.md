---
phase: 09-hardware-ptt-bluetooth
plan: 01
subsystem: hardware-ptt-foundation
tags: [settings, hardware, ptt, volume-keys, data-layer]

dependency_graph:
  requires: []
  provides:
    - VolumeKeyPttConfig enum (DISABLED, VOLUME_UP, VOLUME_DOWN, BOTH)
    - AudioOutputDevice enum (SPEAKER, EARPIECE, BLUETOOTH, WIRED_HEADSET)
    - Hardware button settings persistence (volume key config, BT keycode, boot auto-start, long press threshold)
    - HardwareKeyHandler with dual-purpose volume key logic
  affects:
    - SettingsRepository (5 new hardware button keys)
    - Plan 09-02 (will wire HardwareKeyHandler into MainActivity.dispatchKeyEvent)
    - Plan 09-03 (will use AudioOutputDevice for UI indicator)

tech_stack:
  added: []
  patterns:
    - DataStore cached sync accessors for main thread access (getCachedVolumeKeyPttConfig, getCachedLongPressThresholdMs)
    - Press-and-hold detection via KeyEvent repeat events (not timers)
    - Callback pattern for PTT activation (onPttPress/onPttRelease)

key_files:
  created:
    - android/app/src/main/java/com/voiceping/android/domain/model/VolumeKeyPttConfig.kt
    - android/app/src/main/java/com/voiceping/android/domain/model/AudioOutputDevice.kt
    - android/app/src/main/java/com/voiceping/android/data/hardware/HardwareKeyHandler.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt

decisions: []

metrics:
  duration: 391
  completed_at: "2026-02-12T06:31:15Z"
---

# Phase 09 Plan 01: Hardware Button Settings Foundation & Volume Key PTT Handler Summary

**One-liner:** Settings persistence for hardware PTT (volume keys, BT buttons, boot auto-start) and dual-purpose volume key handler (short tap = volume, long press = PTT)

## Objective

Create the settings foundation for hardware button configuration and implement the core volume key PTT handler with dual-purpose behavior.

Establishes the data layer for all hardware button settings (required by subsequent Phase 9 plans) and implements the critical volume key interception logic that distinguishes between normal volume adjustment (<300ms) and PTT activation (>=300ms).

## Execution

### Task 1: Add hardware button settings to SettingsRepository and create domain models

**Files created:**
- `VolumeKeyPttConfig.kt` - Enum for volume key configuration (DISABLED, VOLUME_UP, VOLUME_DOWN, BOTH)
- `AudioOutputDevice.kt` - Enum for audio output device type (SPEAKER, EARPIECE, BLUETOOTH, WIRED_HEADSET)

**SettingsRepository extensions:**
Added 5 new DataStore keys with Flow getters, suspend setters, and cached sync accessors:
- `VOLUME_KEY_PTT_CONFIG` (stringPreferencesKey) - stores VolumeKeyPttConfig enum, default DISABLED
- `BLUETOOTH_PTT_BUTTON_KEYCODE` (intPreferencesKey) - stores BT button keyCode, default KEYCODE_MEDIA_PLAY_PAUSE (85)
- `BLUETOOTH_PTT_ENABLED` (booleanPreferencesKey) - BT PTT enabled toggle, default false
- `BOOT_AUTO_START_ENABLED` (booleanPreferencesKey) - boot auto-start toggle, default false
- `LONG_PRESS_THRESHOLD_MS` (intPreferencesKey) - long press threshold in ms, default 300

Cached sync accessors added for main thread access:
- `getCachedVolumeKeyPttConfig()` - for key event handler
- `getCachedLongPressThresholdMs()` - for key event handler

All follow existing DataStore patterns (runBlocking with first() on cached DataStore).

**Commit:** `5aa1c7a`

### Task 2: Create HardwareKeyHandler for volume key dual-purpose PTT logic

**Files created:**
- `HardwareKeyHandler.kt` - Volume key handler with dual-purpose behavior

**Implementation:**
- `isVolumeKeyPttEnabled(keyCode)` - checks user config against keyCode
- `handleKeyEvent(event)` - implements dual-purpose logic:
  - **ACTION_DOWN (repeatCount == 0):** Records timestamp, returns FALSE (allows volume adjustment)
  - **ACTION_DOWN (repeatCount > 0):** Checks duration against threshold, activates PTT if >= threshold, returns TRUE to consume
  - **ACTION_UP:** If was long press, releases PTT and returns TRUE. Else returns FALSE (short tap, allow volume).

**State tracking:**
- `keyDownTime: Long` - timestamp of initial key press
- `isLongPressActive: Boolean` - whether PTT has been activated
- `activeKeyCode: Int` - which key is currently tracked

**Callbacks:**
- `var onPttPress: (() -> Unit)?` - called when long press threshold reached (PTT start)
- `var onPttRelease: (() -> Unit)?` - called when key released after long press (PTT stop)

**CRITICAL per research:** Does NOT consume the initial ACTION_DOWN event. Volume adjustment must work for short taps. Only consumes events after long press threshold is confirmed via repeat events.

**Commit:** `017020c`

## Verification Results

All verification criteria passed:

1. `./gradlew compileDebugKotlin` - BUILD SUCCESSFUL
2. VolumeKeyPttConfig has DISABLED, VOLUME_UP, VOLUME_DOWN, BOTH values ✓
3. AudioOutputDevice has SPEAKER, EARPIECE, BLUETOOTH, WIRED_HEADSET values ✓
4. SettingsRepository has 5 new keys with getters/setters ✓
5. HardwareKeyHandler.handleKeyEvent returns false for initial ACTION_DOWN ✓
6. HardwareKeyHandler.handleKeyEvent returns true for repeat events after threshold ✓
7. HardwareKeyHandler.handleKeyEvent calls onPttPress on threshold crossing ✓
8. HardwareKeyHandler.handleKeyEvent calls onPttRelease on ACTION_UP after long press ✓

## Deviations from Plan

None - plan executed exactly as written.

## Integration Points

**For Plan 09-02:**
- Wire HardwareKeyHandler into MainActivity.dispatchKeyEvent()
- Connect onPttPress/onPttRelease callbacks to PttManager

**For Plan 09-03:**
- Use AudioOutputDevice enum for top bar indicator icon

**For Plan 09-04:**
- Use BLUETOOTH_PTT_BUTTON_KEYCODE and BLUETOOTH_PTT_ENABLED settings
- Use BOOT_AUTO_START_ENABLED for BroadcastReceiver

## Self-Check: PASSED

**Created files exist:**
```
FOUND: android/app/src/main/java/com/voiceping/android/domain/model/VolumeKeyPttConfig.kt
FOUND: android/app/src/main/java/com/voiceping/android/domain/model/AudioOutputDevice.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/hardware/HardwareKeyHandler.kt
```

**Commits exist:**
```
FOUND: 5aa1c7a (Task 1)
FOUND: 017020c (Task 2)
```

**Build verification:**
```
BUILD SUCCESSFUL in 2s
```

All must-have artifacts created and verified.
