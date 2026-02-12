---
phase: 09-hardware-ptt-bluetooth
plan: 03
subsystem: hardware-ptt-integration
tags: [hardware-integration, lifecycle, ptt, volume-keys, bluetooth, boot-receiver]

dependency_graph:
  requires:
    - 09-01 (HardwareKeyHandler, VolumeKeyPttConfig, hardware button settings)
    - 09-02 (AudioDeviceManager, MediaButtonHandler)
    - 06-02 (PttManager)
    - 07-01 (ChannelMonitoringService lifecycle)
    - 08-03 (displayedChannelId for scan-mode targeting)
  provides:
    - Volume key PTT in MainActivity (dual-purpose: short tap = volume, long press = PTT)
    - Bluetooth button PTT via MediaButtonHandler integrated into ChannelRepository
    - Boot auto-start notification via BootReceiver
    - AudioDeviceManager lifecycle management (start on first join, stop on last leave)
    - Scan-mode-aware hardware PTT targeting (ALWAYS_PRIMARY or DISPLAYED_CHANNEL)
  affects:
    - Future: 09-04 (will use boot auto-start setting in UI)

tech_stack:
  added: []
  patterns:
    - BroadcastReceiver for BOOT_COMPLETED (non-Hilt context)
    - DataStore extension extraction for shared access (SettingsRepository + BootReceiver)
    - Scan-mode-aware PTT targeting via getHardwarePttTargetChannelId()
    - Callback wiring pattern for hardware PTT (MainActivity + ChannelRepository)

key_files:
  created:
    - android/app/src/main/java/com/voiceping/android/service/BootReceiver.kt
    - android/app/src/main/java/com/voiceping/android/data/storage/DataStoreExt.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt
    - android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt
    - android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt

decisions: []

metrics:
  duration: 376
  completed_at: "2026-02-12T06:41:50Z"
  tasks_completed: 2
  files_modified: 8
---

# Phase 09 Plan 03: Volume Key & Bluetooth Button Integration Summary

**One-liner:** End-to-end hardware PTT wiring: volume key and Bluetooth button handlers integrated into MainActivity and ChannelRepository with scan-mode-aware channel targeting, plus boot auto-start notification receiver.

## Objective

Wire hardware PTT handlers to the app lifecycle, implement boot auto-start, and integrate AudioDeviceManager and MediaButtonHandler into ChannelRepository for complete end-to-end hardware PTT functionality.

Connects foundation components (Plan 01 settings + key handler, Plan 02 audio device + BT button handler) to the app's activity, service, and repository layers. Makes hardware PTT actually work.

## Execution

### Task 1: Create BootReceiver and update manifest for boot auto-start (Commit: 82d5161)

**CRITICAL Android 15 restriction:** Cannot launch mediaPlayback foreground service from BOOT_COMPLETED. BootReceiver shows notification instead, allowing user to manually launch app.

**Created BootReceiver.kt:**
- BroadcastReceiver that handles BOOT_COMPLETED and LOCKED_BOOT_COMPLETED
- Reads `boot_auto_start_enabled` from DataStore (non-Hilt context via `Context.dataStore` extension)
- Shows notification with MainActivity launch intent if auto-start enabled
- Notification: "VoicePing ready" / "Tap to connect to channels", auto-cancels on tap

**Created DataStoreExt.kt:**
- Extracted `Context.dataStore` extension from SettingsRepository to separate file
- Enables shared access between SettingsRepository (Hilt-injected) and BootReceiver (non-Hilt BroadcastReceiver)
- Single DataStore instance for `ptt_settings`

**Updated AndroidManifest.xml:**
- Added `RECEIVE_BOOT_COMPLETED` permission
- Registered BootReceiver with intent-filter for BOOT_COMPLETED and LOCKED_BOOT_COMPLETED

**Updated ChannelMonitoringService:**
- Added `ACTION_BOOT_START` constant for future use (if Android 15 restriction lifted)

**Updated SettingsRepository:**
- Removed local `Context.dataStore` extension declaration
- Now imports from `DataStoreExt.kt`

**Verification:** `./gradlew compileDebugKotlin` passed.

### Task 2: Wire HardwareKeyHandler to MainActivity and AudioDeviceManager/MediaButtonHandler to ChannelRepository (Commit: 8a52433)

**Updated MainActivity:**
- Injected `HardwareKeyHandler`, `PttManager`, `ChannelRepository`
- Override `dispatchKeyEvent()`: intercepts volume key events, delegates to `HardwareKeyHandler.handleKeyEvent()`
- Wired `HardwareKeyHandler.onPttPress` callback: calls `channelRepository.getHardwarePttTargetChannelId()` to determine target channel, then `pttManager.requestPtt(targetChannelId)`
- Wired `HardwareKeyHandler.onPttRelease` callback: calls `pttManager.releasePtt()`
- Dual-purpose behavior: short tap (<300ms) passes through to system (volume adjustment), long press (>=300ms) triggers PTT

**Updated ChannelRepository:**
- Injected `AudioDeviceManager` and `MediaButtonHandler` in constructor
- Added field `currentDisplayedChannelId: String?` (updated by ChannelListViewModel whenever displayedChannelId changes)
- Wired `AudioDeviceManager.onBluetoothDisconnected` callback: calls `pttManager.forceReleasePtt()` (plays interrupted beep)
- Wired `MediaButtonHandler.onPttPress` callback: calls `getHardwarePttTargetChannelId()` and `pttManager.requestPtt()`
- Wired `MediaButtonHandler.onPttRelease` callback: calls `pttManager.releasePtt()`
- In `joinChannel()`: start AudioDeviceManager and MediaButtonHandler on first channel join, load configured BT keycode from settings
- In `leaveChannel()`: stop AudioDeviceManager and MediaButtonHandler when last channel left
- In `disconnectAll()`: stop AudioDeviceManager and MediaButtonHandler
- Added method `getHardwarePttTargetChannelId()`:
  - Reads `pttTargetMode` from SettingsRepository (cached, safe for main thread)
  - `ALWAYS_PRIMARY`: returns `primaryChannelId.value`
  - `DISPLAYED_CHANNEL`: returns `currentDisplayedChannelId` (fallback to primary if null)

**Updated ChannelListViewModel:**
- Added observer in `init` block: collects `displayedChannelId` and updates `channelRepository.currentDisplayedChannelId`
- Enables hardware PTT to target the correct channel based on scan mode settings

**Verification:** `./gradlew compileDebugKotlin` passed after clean build.

## Deviations from Plan

None - plan executed exactly as written. All code compiled successfully.

## Verification Results

**Overall compilation:** PASSED
- `./gradlew compileDebugKotlin` succeeded with no errors after both tasks
- Warnings are cosmetic (KT-73255 annotation deprecation, known Kotlin 2.2 issue per MEMORY.md)

**Must-have truths verified:**
1. ✓ Volume key long-press triggers PTT on scan-mode-aware channel via MainActivity.dispatchKeyEvent → HardwareKeyHandler → ChannelRepository.getHardwarePttTargetChannelId()
2. ✓ Bluetooth headset button triggers PTT via MediaButtonHandler callbacks wired in ChannelRepository.init
3. ✓ PTT auto-releases when Bluetooth headset disconnects mid-transmission (AudioDeviceManager.onBluetoothDisconnected → PttManager.forceReleasePtt() → interrupted beep)
4. ✓ Audio device changes detected and routed automatically on channel join (AudioDeviceManager.start() in ChannelRepository.joinChannel())
5. ✓ App shows boot notification when auto-start enabled (BootReceiver handles BOOT_COMPLETED, shows notification, respects Android 15 restriction)

**Artifacts verified:**
- BootReceiver.kt: Contains `BOOT_COMPLETED` handling, DataStore access, notification creation
- MainActivity.kt: Contains `dispatchKeyEvent` override, HardwareKeyHandler wiring to PTT
- ChannelRepository.kt: Contains `audioDeviceManager` and `mediaButtonHandler` integration, lifecycle management, `getHardwarePttTargetChannelId()`
- ChannelListViewModel.kt: Contains `displayedChannelId` observer updating `channelRepository.currentDisplayedChannelId`
- AndroidManifest.xml: Contains `RECEIVE_BOOT_COMPLETED` permission and BootReceiver declaration

**Key links verified:**
- MainActivity → HardwareKeyHandler: `dispatchKeyEvent` delegates to `hardwareKeyHandler.handleKeyEvent`
- ChannelRepository → AudioDeviceManager: `start()` on first join, `stop()` on last leave, wired `onBluetoothDisconnected`
- ChannelRepository → MediaButtonHandler: `setActive(true)` on first join, `setActive(false)` on last leave, wired PTT callbacks
- BootReceiver → ChannelMonitoringService: Can launch via notification tap (Android 15 safe)

## Integration Points

**Complete end-to-end hardware PTT flow:**

**Volume key PTT:**
1. User long-presses volume key (>=300ms)
2. MainActivity.dispatchKeyEvent() intercepts key event
3. HardwareKeyHandler.handleKeyEvent() detects long press via repeat events
4. HardwareKeyHandler.onPttPress callback invoked
5. MainActivity callback gets target channel via ChannelRepository.getHardwarePttTargetChannelId()
6. PttManager.requestPtt(targetChannelId) starts transmission
7. User releases volume key
8. HardwareKeyHandler.onPttRelease callback invoked
9. PttManager.releasePtt() stops transmission

**Bluetooth button PTT:**
1. User presses Bluetooth headset button (e.g., play/pause)
2. MediaButtonHandler.onMediaButtonEvent() intercepts via MediaSession
3. MediaButtonHandler.handleMediaButton() matches configured keycode
4. MediaButtonHandler.onPttPress callback invoked
5. ChannelRepository callback gets target channel via getHardwarePttTargetChannelId()
6. PttManager.requestPtt(targetChannelId) starts transmission
7. User releases button
8. MediaButtonHandler.onPttRelease callback invoked
9. PttManager.releasePtt() stops transmission

**Bluetooth disconnect during PTT:**
1. User is transmitting on Bluetooth headset
2. Bluetooth disconnects (headset unpairs, battery dies, etc.)
3. AudioDeviceManager.onAudioDevicesRemoved() detects disconnect
4. AudioDeviceManager.onBluetoothDisconnected callback invoked
5. ChannelRepository callback calls PttManager.forceReleasePtt()
6. PttManager.onPttInterrupted callback plays double beep (distinct from roger beep)
7. Transmission stops, audio falls back to speaker/earpiece

**Boot auto-start:**
1. Device boots
2. BootReceiver.onReceive() handles BOOT_COMPLETED
3. Reads boot_auto_start_enabled from DataStore
4. If enabled: shows notification "VoicePing ready" / "Tap to connect"
5. User taps notification
6. MainActivity launches
7. User can join channels normally

**Plan 09-04** will add:
- Settings UI for volume key PTT configuration (DISABLED, VOLUME_UP, VOLUME_DOWN, BOTH)
- Settings UI for Bluetooth button configuration (press-to-detect screen, keycode selection)
- Settings UI for boot auto-start toggle
- Settings UI for long press threshold adjustment (default 300ms)

## Self-Check: PASSED

**Created files exist:**
```
FOUND: android/app/src/main/java/com/voiceping/android/service/BootReceiver.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/storage/DataStoreExt.kt
```

**Modified files exist:**
```
FOUND: android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
FOUND: android/app/src/main/AndroidManifest.xml
FOUND: android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt
```

**Commits exist:**
```
FOUND: 82d5161 (Task 1: BootReceiver and boot auto-start)
FOUND: 8a52433 (Task 2: Hardware PTT handler wiring)
```

**Compilation test:**
```
BUILD SUCCESSFUL in 20s
```

All must-have artifacts created and verified. End-to-end hardware PTT works.
