---
phase: 09
plan: 02
subsystem: audio-hardware
tags:
  - bluetooth
  - audio-routing
  - media-session
  - hardware-integration
dependency_graph:
  requires:
    - 09-01 (hardware button settings infrastructure)
    - 06-02 (PttManager callbacks pattern)
    - 07-01 (ChannelMonitoringService lifecycle)
  provides:
    - Automatic Bluetooth/wired headset audio routing
    - Bluetooth headset button interception via MediaSession
    - Audio device detection and state tracking
  affects:
    - Future: 09-03 (will wire MediaButtonHandler into PttManager)
    - Future: 09-04 (will wire AudioDeviceManager into ChannelMonitoringService)
tech_stack:
  added:
    - androidx.media3:media3-session:1.5.1 (MediaSession API for BT button events)
    - androidx.media3:media3-exoplayer:1.5.1 (Player stub required by MediaSession)
  patterns:
    - AudioDeviceCallback for hardware device detection
    - StateFlow for current device state exposure
    - Callback delegation pattern (avoids circular dependencies)
    - Modern setCommunicationDevice API (API 31+) with legacy SCO fallback
key_files:
  created:
    - android/app/src/main/java/com/voiceping/android/data/audio/AudioDeviceManager.kt (automatic audio routing manager)
    - android/app/src/main/java/com/voiceping/android/data/hardware/MediaButtonHandler.kt (Bluetooth button interception)
  modified:
    - android/app/build.gradle.kts (added Media3 dependencies)
    - android/app/src/main/java/com/voiceping/android/data/audio/AudioRouter.kt (added Bluetooth/wired routing methods)
decisions:
  - decision: "Use Media3 MediaSession (not legacy MediaSessionCompat)"
    rationale: "Modern API, better Android 12+ support, actively maintained"
  - decision: "Only activate MediaSession when service running"
    rationale: "Avoids stealing media buttons from music apps (research pitfall #5)"
  - decision: "Last connected device wins priority"
    rationale: "User expectation: plugging wired headset after BT should switch to wired"
  - decision: "Minimal ExoPlayer stub for MediaSession"
    rationale: "Media3 MediaSession requires Player instance, but we don't need playback"
  - decision: "API 31+ uses setCommunicationDevice, legacy uses startBluetoothSco"
    rationale: "Modern API is more reliable, but must support Android 8-11 (26-30)"
metrics:
  duration: 420
  completed_date: 2026-02-12
  tasks_completed: 2
  files_modified: 4
---

# Phase 09 Plan 02: Bluetooth & Wired Audio Device Auto-Routing with Button Interception Summary

**One-liner:** Automatic Bluetooth/wired headset audio routing with "last connected wins" priority and MediaSession-based Bluetooth button interception for PTT.

## Objective

Implement Bluetooth/wired audio device detection with automatic routing and MediaSession-based Bluetooth headset button interception to enable seamless audio routing and hardware PTT control.

## Tasks Completed

### Task 1: AudioDeviceManager for Bluetooth/Wired Headset Detection (Commit: 5b73b03)

**Created AudioDeviceManager** (`android/app/src/main/java/com/voiceping/android/data/audio/AudioDeviceManager.kt`):
- Registers `AudioDeviceCallback` to detect Bluetooth A2DP/SCO/BLE and wired headset/headphones
- Implements "last connected device wins" priority (e.g., wired after BT replaces BT)
- Falls back to previous device on disconnect (BT unpairs → returns to speaker/earpiece)
- Exposes `currentOutputDevice: StateFlow<AudioOutputDevice>` for UI indicator
- Invokes `onBluetoothDisconnected` callback for PTT auto-release (Phase 9 requirement)
- Scans already-connected devices on `start()` to detect pre-existing headsets
- Lifecycle methods: `start()` when service starts, `stop()` when service stops

**Extended AudioRouter** (`android/app/src/main/java/com/voiceping/android/data/audio/AudioRouter.kt`):
- Added `setBluetoothMode(device: AudioDeviceInfo)`: API 31+ uses `setCommunicationDevice`, legacy uses `startBluetoothSco`
- Added `setWiredHeadsetMode()`: Routes to wired headset (system auto-selects when connected)
- Added `clearCommunicationDevice()`: Cleanup on disconnect (stops BT SCO if needed)
- Added `getAudioManager()`: Accessor for AudioDeviceCallback registration

**Added Media3 dependencies** (`android/app/build.gradle.kts`):
- `androidx.media3:media3-session:1.5.1` (MediaSession API for BT button events)
- `androidx.media3:media3-exoplayer:1.5.1` (Player stub required by MediaSession)

**Verification:** `./gradlew compileDebugKotlin` passed.

### Task 2: MediaButtonHandler for Bluetooth Button Interception (Commit: d51402d)

**Created MediaButtonHandler** (`android/app/src/main/java/com/voiceping/android/data/hardware/MediaButtonHandler.kt`):
- Uses Media3 `MediaSession` to intercept Bluetooth headset button events
- Creates minimal `ExoPlayer` stub (required by MediaSession API, no actual playback)
- Handles common BT button keycodes: `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_NEXT/PREVIOUS`, `KEYCODE_MEDIA_PLAY/PAUSE`
- Configurable button keycode via `setConfiguredKeyCode(keyCode: Int)` (user selects via press-to-detect)
- Detection mode for button learning: `startDetectionMode()` → any button press reports keyCode via `onButtonDetected` callback
- Lifecycle management: `setActive(true/false)` only active when service running (avoids stealing media buttons from music apps)
- Callbacks: `onPttPress`, `onPttRelease` for PttManager wiring, `onButtonDetected` for detection screen
- Methods:
  - `initialize()`: Creates ExoPlayer + MediaSession with `onMediaButtonEvent` callback
  - `handleMediaButton(event: KeyEvent)`: Routes to PTT or detection based on mode
  - `setActive(active: Boolean)`: Only activates when service running (research pitfall #5)
  - `startDetectionMode()/stopDetectionMode()`: Button learning flow
  - `release()`: Cleanup when service stops

**Verification:** `./gradlew compileDebugKotlin` passed.

## Deviations from Plan

None - plan executed exactly as written. All code compiled successfully on first attempt after clean build.

## Verification Results

**Overall compilation:** PASSED
- `./gradlew compileDebugKotlin` succeeded with no errors
- Warnings are cosmetic (KT-73255 annotation deprecation, known Kotlin 2.2 issue per MEMORY.md)

**Must-have truths verified:**
1. ✓ Audio routing methods implemented for Bluetooth/wired headsets
2. ✓ AudioDeviceCallback detects device connections/disconnections
3. ✓ "Last connected device wins" priority implemented in `onAudioDevicesAdded`
4. ✓ Fallback to previous device implemented in `onAudioDevicesRemoved`
5. ✓ MediaSession intercepts Bluetooth button events via `onMediaButtonEvent`
6. ✓ Configurable button keycode with detection mode support
7. ✓ MediaSession only active when service running (lifecycle management)

**Artifacts verified:**
- AudioDeviceManager.kt: Contains `AudioDeviceCallback`, device priority logic, state tracking
- MediaButtonHandler.kt: Contains `MediaSession`, `handleMediaButton`, detection mode
- AudioRouter.kt: Contains `setBluetoothMode`, `setWiredHeadsetMode`, `clearCommunicationDevice`
- build.gradle.kts: Contains Media3 dependencies

**Key links verified:**
- AudioDeviceManager → AudioRouter: Delegates routing via `setBluetoothMode`, `setWiredHeadsetMode`, etc.
- MediaButtonHandler: Exposes `onPttPress`/`onPttRelease` callbacks (will be wired in 09-03)

## Next Steps

**Plan 09-03** will wire these components together:
- Integrate MediaButtonHandler into PttManager (wire `onPttPress`/`onPttRelease` callbacks)
- Integrate AudioDeviceManager into ChannelMonitoringService (wire `onBluetoothDisconnected` callback)
- Wire settings to MediaButtonHandler (configured keyCode from SettingsRepository)

**Plan 09-04** will add volume key PTT and UI screens for hardware button configuration.

## Self-Check: PASSED

**Created files exist:**
- FOUND: android/app/src/main/java/com/voiceping/android/data/audio/AudioDeviceManager.kt
- FOUND: android/app/src/main/java/com/voiceping/android/data/hardware/MediaButtonHandler.kt

**Modified files exist:**
- FOUND: android/app/build.gradle.kts
- FOUND: android/app/src/main/java/com/voiceping/android/data/audio/AudioRouter.kt

**Commits exist:**
- FOUND: 5b73b03 (Task 1: AudioDeviceManager)
- FOUND: d51402d (Task 2: MediaButtonHandler)

**Compilation test:**
- PASSED: `./gradlew compileDebugKotlin` succeeded with no errors
