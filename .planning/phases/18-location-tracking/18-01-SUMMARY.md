---
phase: 18-location-tracking
plan: 01
subsystem: android-location
tags: [location-tracking, motion-detection, battery-optimization, gps]
dependency-graph:
  requires:
    - phase-16-01 (PermissionManager for location permission checks)
    - play-services-location API
    - ActivityRecognition API
  provides:
    - LocationManager.locationUpdates SharedFlow for server transmission
    - MotionState-aware adaptive tracking intervals
    - Battery-aware location tracking
  affects:
    - Future plan 18-02 (server location endpoint)
    - Future plan 18-03 (SignalingClient location transmission)
tech-stack:
  added:
    - com.google.android.gms:play-services-location:21.3.0
    - FusedLocationProviderClient API
    - ActivityTransition API
  patterns:
    - Hilt singleton injection for LocationTracker, MotionDetector, LocationManager
    - Companion object pattern for BroadcastReceiver → singleton callback
    - StateFlow for motion state observation
    - SharedFlow for location update emission
    - CoroutineScope for async operations
key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/domain/model/MotionState.kt
    - android/app/src/main/java/com/voiceping/android/data/location/LocationUpdate.kt
    - android/app/src/main/java/com/voiceping/android/data/location/LocationTracker.kt
    - android/app/src/main/java/com/voiceping/android/data/location/MotionDetector.kt
    - android/app/src/main/java/com/voiceping/android/data/location/LocationManager.kt
    - android/app/src/main/java/com/voiceping/android/service/ActivityTransitionReceiver.kt
  modified:
    - android/app/build.gradle.kts (added play-services-location dependency)
    - android/app/src/main/AndroidManifest.xml (added location permissions + receiver registration)
decisions: []
metrics:
  duration: 263s
  tasks-completed: 2
  files-created: 6
  files-modified: 2
  commits: 2
  lines-added: ~685
  completed: 2026-02-15
---

# Phase 18 Plan 01: Location Tracking Foundation Summary

**One-liner:** Motion-aware GPS tracking with FusedLocationProviderClient, ActivityTransition API motion detection, 50m deduplication, and battery adaptation (< 20% → LOW_POWER + 5min).

## What Was Built

Created the Android location tracking foundation with motion-aware adaptive intervals and battery optimization:

1. **Data Models:**
   - `MotionState` enum (STILL, WALKING, DRIVING, UNKNOWN) with wire format serialization
   - `LocationUpdate` data class with JSON serialization for WebSocket transmission

2. **Location Tracking Infrastructure:**
   - `LocationTracker`: FusedLocationProviderClient wrapper with configurable intervals and priority
   - `MotionDetector`: ActivityTransition API wrapper detecting user motion state with GPS displacement fallback
   - `LocationManager`: Coordinator singleton managing adaptive intervals, deduplication, and battery optimization
   - `ActivityTransitionReceiver`: BroadcastReceiver delivering motion state changes from Google Play Services

3. **Adaptive Intervals:**
   - STILL: 5 minutes (stationary users)
   - WALKING: 60 seconds (moderate movement)
   - DRIVING: 30 seconds (fast movement)
   - UNKNOWN: 60 seconds (default/fallback)
   - Battery < 20%: Override to LOW_POWER priority + 5 minute interval

4. **Smart Features:**
   - 50m deduplication: Skip location updates if displacement < 50m
   - GPS displacement fallback: If ActivityRecognition unavailable, infer motion from GPS (< 30m for 2 updates → STILL, >= 30m → WALKING)
   - PTT-triggered location: Force-send location on PTT if > 2 minutes since last send and battery >= 20%

5. **Manifest & Permissions:**
   - Added `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `ACTIVITY_RECOGNITION` permissions
   - Registered `ActivityTransitionReceiver` in manifest

6. **Gradle Dependency:**
   - Added `com.google.android.gms:play-services-location:21.3.0`

## Deviations from Plan

None - plan executed exactly as written.

## Technical Decisions

**1. Companion Object Pattern for BroadcastReceiver → Singleton Callback**
- **Context:** ActivityTransitionReceiver is a BroadcastReceiver (short-lived) but MotionDetector is a Hilt singleton (long-lived)
- **Decision:** MotionDetector exposes `companion var instance: MotionDetector? = null`, set during construction. ActivityTransitionReceiver calls `MotionDetector.instance?.updateMotionState(state)`.
- **Rationale:** Avoids complex Hilt dependency injection in BroadcastReceiver. Simple, effective pattern for singleton callback.

**2. GPS Displacement Fallback (30m threshold, 2 consecutive checks)**
- **Context:** Activity Recognition API may be unavailable (permission denied, disabled by user, device limitation)
- **Decision:** If ActivityRecognition unavailable, fall back to GPS displacement: < 30m for 2 consecutive updates → STILL, >= 30m → WALKING
- **Rationale:** Provides degraded but functional motion detection when Google Play Services API unavailable. 30m threshold balances GPS accuracy noise vs real movement. 2 consecutive checks avoid false positives from GPS jitter.

**3. Battery Adaptation (< 20% → LOW_POWER + 5min)**
- **Context:** Location tracking drains battery; need to preserve battery when low
- **Decision:** When battery < 20%, override to `PRIORITY_LOW_POWER` and 5 minute interval regardless of motion state
- **Rationale:** Aligns with user decision to suppress PTT-triggered locations when battery < 20%. Consistent battery preservation strategy across location features.

**4. 50m Deduplication**
- **Context:** Need to balance server load with location update freshness
- **Decision:** Skip location updates if displacement < 50m from last sent position
- **Rationale:** 50m is meaningful movement distance for team coordination scenarios. Reduces server traffic while maintaining useful location granularity. Complements motion-based intervals (which control frequency, deduplication controls spatial resolution).

## Verification Results

✅ All verification criteria met:

1. ✅ `cd android && ./gradlew compileDebugKotlin` compiles without errors
2. ✅ AndroidManifest.xml contains 4 new permissions: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `ACTIVITY_RECOGNITION`
3. ✅ build.gradle.kts contains `play-services-location:21.3.0`
4. ✅ LocationManager exposes `locationUpdates: SharedFlow<LocationUpdate>` for server transmission (plan 03)
5. ✅ Motion intervals: STILL=5min (300s), WALKING=60s, DRIVING=30s
6. ✅ Battery < 20%: `PRIORITY_LOW_POWER` + 5min interval
7. ✅ Deduplication: skip if < 50m displacement

**Build warnings:** Only cosmetic `@ApplicationContext` annotation warnings (KT-73255, known Kotlin 2.2 deprecation) - functional, not blocking.

## Integration Points

**Downstream dependencies (for future plans):**

- **Plan 18-02 (Server location endpoint):** Will receive LocationUpdate JSON via WebSocket
- **Plan 18-03 (SignalingClient location transmission):** Will collect from `LocationManager.locationUpdates` SharedFlow and transmit via WebSocket

**Upstream dependencies (used):**

- **Phase 16-01 (PermissionManager):** `hasLocationPermission()` method exists but currently returns false (permissions now in manifest, future plan will add runtime permission request)
- **Hilt DI:** All location classes use `@Singleton` and `@Inject` constructor pattern
- **Existing patterns:** Follows established coroutine flow patterns from audio/PTT infrastructure

## Testing Notes

**Manual testing needed (future):**
1. Motion detection: Verify ActivityTransition events trigger motion state changes
2. GPS displacement fallback: Test with Activity Recognition disabled
3. Battery adaptation: Test with battery level < 20%
4. Deduplication: Verify 50m threshold (walk < 50m → no update, walk > 50m → update)
5. Adaptive intervals: Verify interval changes when motion state changes

**Known limitations:**
- Activity Recognition requires Google Play Services on device
- GPS displacement fallback is less accurate than ActivityTransition API
- Battery level check via BatteryManager (standard Android API)

## Self-Check: PASSED

✅ All created files exist:
- android/app/src/main/java/com/voiceping/android/domain/model/MotionState.kt
- android/app/src/main/java/com/voiceping/android/data/location/LocationUpdate.kt
- android/app/src/main/java/com/voiceping/android/data/location/LocationTracker.kt
- android/app/src/main/java/com/voiceping/android/data/location/MotionDetector.kt
- android/app/src/main/java/com/voiceping/android/data/location/LocationManager.kt
- android/app/src/main/java/com/voiceping/android/service/ActivityTransitionReceiver.kt

✅ All commits exist:
- 1dfa43b: feat(18-01): add location tracking data models and dependencies
- c9f17c8: feat(18-01): implement location tracking infrastructure

✅ Build verification: compileDebugKotlin successful
