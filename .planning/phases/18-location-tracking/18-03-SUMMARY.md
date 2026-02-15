---
phase: 18-location-tracking
plan: 03
subsystem: android-integration
tags:
  - location-tracking
  - websocket
  - offline-queue
  - foreground-service
  - debug-ui
dependency_graph:
  requires:
    - phase-18-01 (LocationManager with adaptive tracking)
    - phase-18-02 (Server location storage and broadcast)
    - SignalingClient WebSocket infrastructure
    - ChannelMonitoringService foreground service
  provides:
    - End-to-end location tracking pipeline (Android → WebSocket → Server)
    - Offline queue with reconnect flush (50-update capacity)
    - PTT-triggered location sends (contextual, battery-aware)
    - Android 14+ foreground service location type compliance
    - DevStatsScreen location display for self-verification
  affects:
    - SignalingClient (added send(JsonObject) overload)
    - ChannelRepository (auto-start/stop location tracking)
    - PttManager (PTT-triggered location on grant)
    - DevStatsScreen (location tracking section)
tech_stack:
  added: []
  patterns:
    - Fire-and-forget WebSocket protocol (location-update, location-batch)
    - Offline queue with FIFO eviction (ArrayDeque)
    - Reconnect observer for queue flush
    - StateFlow for debug UI reactivity
    - Foreground service type bitwise OR (mediaPlayback | location)
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/location/LocationManager.kt
    - android/app/src/main/java/com/voiceping/android/data/network/dto/SignalingMessage.kt
    - android/app/src/main/java/com/voiceping/android/di/AppModule.kt
    - android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
    - android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
    - android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt
    - android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt
    - android/app/src/main/java/com/voiceping/android/presentation/settings/DevStatsScreen.kt
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt
    - android/app/src/main/java/com/voiceping/android/presentation/navigation/NavGraph.kt
decisions:
  - Wire location auto-start to ChannelRepository.joinChannel (first channel only, if permission granted)
  - Wire location stop to ChannelRepository.disconnectAll (cleanup on logout/disconnect)
  - PTT-triggered location wrapped in try/catch to prevent location errors from breaking PTT flow
  - DevStatsScreen receives LocationManager via MainActivity/NavGraph dependency injection (matches existing SignalingClient pattern)
  - Offline queue uses FIFO eviction (removeFirst) when capacity exceeded (50 updates)
  - Reconnect flush sends location-batch (not individual location-update messages)
  - currentLocation StateFlow updated before deduplication (shows ALL received locations, not just sent ones)
metrics:
  duration: 349s
  tasks_completed: 2
  commits: 2
  files_created: 0
  files_modified: 11
  completed: 2026-02-15
---

# Phase 18 Plan 03: Android Location Service Integration Summary

Wire Android location tracking to server via WebSocket with offline queue, PTT-triggered sends, auto-start on login, Android 14+ foreground service compliance, and debug screen self-verification.

## One-Liner

WebSocket transmission with offline queue (50-update capacity, reconnect flush as batch), PTT-triggered sends (120s interval, battery-aware), auto-start on first channel join, Android 14+ foreground service location type, and DevStatsScreen location display (lat/lng/accuracy/motion/timestamp).

## Accomplishments

### Task 1: WebSocket transmission, offline queue, protocol types, and auto-start wiring

**Duration:** 175s
**Commit:** `565ec9a`

#### 1.1. Protocol Types (SignalingMessage.kt, AppModule.kt)

Added 4 new SignalingType enum entries with wire format mappings:
- `LOCATION_UPDATE` → "location-update" (individual location send)
- `LOCATION_BATCH` → "location-batch" (reconnect queue flush)
- `LOCATION_QUERY` → "location-query" (dispatch query for latest positions)
- `LOCATION_BROADCAST` → "location-broadcast" (server→dispatch real-time broadcast)

**Server protocol compatibility:** Matches src/shared/protocol.ts and plan 18-02 server implementation.

#### 1.2. SignalingClient Enhancement

Added `send(SignalingType, JsonObject?)` overload for nested JSON structures:
- Preserves JsonObject structure (LocationUpdate.toJsonObject)
- Fire-and-forget pattern (no response expected)
- Matches existing `send(Map<String, Any>)` pattern

#### 1.3. LocationManager WebSocket Transmission

**Offline Queue:**
- `ArrayDeque<LocationUpdate>(50)` with FIFO eviction (removeFirst when full)
- `sendLocationUpdate()`: send if connected, queue if offline
- `flushOfflineQueue()`: batch send as location-batch on reconnect
- Reconnect observer: collects `signalingClient.connectionState`, flushes when CONNECTED

**WebSocket Transmission:**
- On location update: call `sendLocationUpdate()` after emitting to SharedFlow
- If connected: flush queue first (if not empty), then send individual update
- If disconnected: add to queue (drop oldest if full)

**Debug Screen Support:**
- Added `currentLocation: StateFlow<LocationUpdate?>` for DevStatsScreen
- Updated before deduplication (shows ALL received locations, not just sent)

#### 1.4. Auto-Start Wiring (ChannelRepository)

**ChannelRepository.joinChannel():**
- After starting foreground service (first channel only):
  ```kotlin
  try {
      locationManager.startTracking()
      Log.d(TAG, "Started location tracking")
  } catch (e: SecurityException) {
      Log.w(TAG, "Location permission not granted, skipping location tracking")
  }
  ```
- SecurityException caught gracefully (no crash if permission denied)
- Idempotent: LocationManager.startTracking() checks `isTracking` flag

**ChannelRepository.disconnectAll():**
- Added `locationManager.stopTracking()` before stopping service
- Cleanup on logout/disconnect

#### 1.5. PTT-Triggered Location (PttManager)

**PttManager.requestPtt():**
- After successful PTT grant (producer created):
  ```kotlin
  try {
      locationManager.requestPttTriggeredLocation()
  } catch (e: Exception) {
      Log.w(TAG, "PTT-triggered location failed (non-blocking): ${e.message}")
  }
  ```
- Wrapped in try/catch to prevent location errors from breaking PTT flow
- Fire-and-forget (does not affect PTT grant)
- LocationManager enforces 120s interval + battery >= 20% checks

**Verification:** `compileDebugKotlin` passed with only cosmetic warnings (KT-73255).

---

### Task 2: Foreground service location type and settings/debug display

**Duration:** 174s
**Commit:** `60e4852`

#### 2.1. Foreground Service Location Type

**AndroidManifest.xml:**
```xml
<service
    android:name=".service.ChannelMonitoringService"
    android:foregroundServiceType="mediaPlayback|location"
    android:exported="false" />
```

**ChannelMonitoringService.kt:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    )
}
```

**Android 14+ compliance:** Declares intent to use location in foreground (required for location tracking while screen off).

**Notification text:** Unchanged (audio-focused "Monitoring" — does not mention location per user decision).

#### 2.2. DevStatsScreen Location Display

**Dependency Injection:**
- Added `LocationManager` injection to MainActivity
- Pass through NavGraph to DevStatsScreen (matches existing SignalingClient pattern)

**Location Tracking Section:**
- Latitude/Longitude: 6 decimal places (`%.6f` format)
- Accuracy: meters (1 decimal place)
- Speed: m/s or "N/A" if null
- Heading: degrees or "N/A" if null
- Motion State: capitalized (Still/Walking/Driving/Unknown)
- Last Update: HH:mm:ss format (system timezone)
- Tracking Status: "Active" or "Not available (waiting for first fix)"

**Reactive UI:**
- Collects `locationManager.currentLocation.collectAsStateWithLifecycle()`
- Updates in real-time as location changes
- Shows "Not available" when `currentLocation == null`

**Verification:** `compileDebugKotlin` passed without errors.

---

## Deviations from Plan

None - plan executed exactly as written.

---

## Technical Decisions

**1. Offline Queue Eviction Strategy**
- **Context:** ArrayDeque has 50-update capacity, need eviction when full
- **Decision:** FIFO eviction (`removeFirst()` when size >= maxQueueSize)
- **Rationale:** Prioritize fresh location data over stale data. Newest locations are most relevant for dispatch tracking.

**2. Reconnect Flush as Batch (not Individual)**
- **Context:** Offline queue may contain 1-50 updates after reconnection
- **Decision:** Send as single `location-batch` message, not multiple `location-update` messages
- **Rationale:** Reduces server message processing overhead. Server extracts and broadcasts only latest update per plan 18-02.

**3. PTT-Triggered Location Error Handling**
- **Context:** Location errors should not break PTT transmission flow
- **Decision:** Wrap `locationManager.requestPttTriggeredLocation()` in try/catch with warning log
- **Rationale:** PTT is critical user feature. Location tracking is supplementary. Failures logged but do not interrupt PTT.

**4. currentLocation StateFlow Update Timing**
- **Context:** LocationManager has deduplication (50m threshold), but debug screen should show all received locations
- **Decision:** Update `_currentLocation.value` before deduplication check
- **Rationale:** Provides accurate self-verification for users. Shows GPS fix quality even when updates aren't sent to server.

**5. DevStatsScreen Dependency Injection Pattern**
- **Context:** Need LocationManager in DevStatsScreen Composable
- **Decision:** Inject into MainActivity, pass through NavGraph (same pattern as SignalingClient)
- **Rationale:** Consistent with existing architecture. Avoids Hilt injection into Composables (which requires ViewModel layer).

---

## Verification Results

✅ All verification criteria met:

1. ✅ `cd android && ./gradlew compileDebugKotlin` compiles without errors
2. ✅ SignalingType enum has 4 new location entries (LOCATION_UPDATE, LOCATION_BATCH, LOCATION_QUERY, LOCATION_BROADCAST)
3. ✅ AppModule SignalingTypeAdapter has 4 new location wire mappings
4. ✅ LocationManager has offline queue (ArrayDeque), send/flush methods, reconnect observer
5. ✅ ChannelRepository calls locationManager.startTracking() on first channel join
6. ✅ ChannelRepository calls locationManager.stopTracking() in disconnectAll()
7. ✅ PttManager calls locationManager.requestPttTriggeredLocation() on PTT grant
8. ✅ AndroidManifest.xml ChannelMonitoringService has foregroundServiceType="mediaPlayback|location"
9. ✅ ChannelMonitoringService startForeground includes FOREGROUND_SERVICE_TYPE_LOCATION for Android 14+
10. ✅ DevStatsScreen has Location Tracking section with lat/lng/accuracy/motion state display

**Build warnings:** Only cosmetic `@ApplicationContext` annotation warnings (KT-73255) - functional, not blocking.

---

## Integration Points

### Upstream Dependencies (Plan 18-01, 18-02)

**Plan 18-01 (Location Tracking Foundation):**
- LocationManager.locationUpdates SharedFlow consumed for server transmission
- LocationManager.startTracking()/stopTracking() called by ChannelRepository
- LocationManager.requestPttTriggeredLocation() called by PttManager

**Plan 18-02 (Server-Side Location Infrastructure):**
- Server handles location-update (individual send)
- Server handles location-batch (reconnect queue flush)
- Server stores in SQLite, broadcasts to dispatch users

### Downstream Consumers

**Future Plan (LOC-06 - Dispatch Web UI):**
- Will receive location-broadcast messages from server (real-time updates)
- Will send location-query to get initial positions on load
- See src/server/location/LOCATION_API.md for integration guide

### Shared Infrastructure

- **SignalingClient:** Fire-and-forget send() pattern (no response expected)
- **ChannelRepository:** Auto-start on first channel join (login flow)
- **PttManager:** PTT grant callback chain (tone/haptic already wired)
- **DevStatsScreen:** Debug UI for self-verification (developer builds only)

---

## End-to-End Flow

**Happy Path (Connected):**
1. User logs in → ChannelRepository.joinChannel() → locationManager.startTracking()
2. LocationTracker emits GPS fix → LocationManager.onLocationUpdate()
3. Deduplication check (50m threshold) → pass
4. LocationManager.emitLocationUpdate() → sendLocationUpdate()
5. signalingClient.connectionState == CONNECTED → send(LOCATION_UPDATE, update.toJsonObject())
6. Server receives → stores in SQLite → broadcasts to dispatch
7. DevStatsScreen updates in real-time (currentLocation StateFlow)

**Offline Path (Disconnected):**
1. User logs in while offline → location tracking starts
2. GPS fixes queued in offlineQueue (up to 50, FIFO eviction)
3. WebSocket reconnects → reconnect observer triggers flushOfflineQueue()
4. Batch sent as location-batch → server processes → dispatches latest update

**PTT-Triggered Path:**
1. User presses PTT → PttManager.requestPtt()
2. Server grants → producer created → onPttGranted callback
3. locationManager.requestPttTriggeredLocation()
4. Check: time since last send >= 120s AND battery >= 20% → send current location
5. Otherwise: skip (logged, non-blocking)

---

## Testing Notes

**Manual testing needed (future):**
1. Offline queue: disconnect WiFi → walk around → reconnect → verify batch send in server logs
2. PTT-triggered location: press PTT multiple times → verify 120s throttle (only first send within 2 min)
3. Battery adaptation: drain battery below 20% → verify PTT location suppressed
4. Foreground service type: test on Android 14+ device → verify no permission crash
5. DevStatsScreen: navigate to Settings > Audio Stats → verify location display updates in real-time

**Known limitations:**
- No manual location permission request flow yet (relies on auto-grant or existing permission)
- No UI indicator for location tracking status (only visible in DevStatsScreen)
- No user control for enabling/disabling location tracking (always on when channels joined)

---

## Self-Check: PASSED

✅ All modified files verified:
```bash
FOUND: android/app/src/main/java/com/voiceping/android/data/location/LocationManager.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/network/dto/SignalingMessage.kt
FOUND: android/app/src/main/java/com/voiceping/android/di/AppModule.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
FOUND: android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt
FOUND: android/app/src/main/java/com/voiceping/android/service/ChannelMonitoringService.kt
FOUND: android/app/src/main/java/com/voiceping/android/presentation/settings/DevStatsScreen.kt
FOUND: android/app/src/main/AndroidManifest.xml
FOUND: android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt
FOUND: android/app/src/main/java/com/voiceping/android/presentation/navigation/NavGraph.kt
```

✅ All commits verified:
```bash
FOUND: 565ec9a (Task 1: WebSocket transmission, offline queue, protocol types, auto-start)
FOUND: 60e4852 (Task 2: Foreground service location type, debug screen display)
```

✅ Build verification: `compileDebugKotlin` successful (349s total)

---

## What's Next

**Phase 18 Complete:** All 3 plans executed.

**Location Tracking Pipeline Status:**
- ✅ Plan 01: Android location tracking foundation (motion-aware, battery-optimized)
- ✅ Plan 02: Server-side storage and dispatch broadcast (SQLite, WebSocket API)
- ✅ Plan 03: End-to-end integration (WebSocket transmission, offline queue, auto-start)

**Future Enhancements (not in current milestone):**
- LOC-06: Dispatch web UI with map visualization (consume location-broadcast)
- Permission flow: manual location permission request in permission education screen
- UI controls: user toggle for location tracking (settings screen)
- Battery optimization: exclude location tracking from doze whitelist prompt

**Server is ready. Android is ready. Dispatch UI can now consume location data via WebSocket API.**

---

**Execution Model:** autonomous
**Executor:** Claude Sonnet 4.5
**Duration:** 349 seconds (5.8 minutes)
**Date:** 2026-02-15
