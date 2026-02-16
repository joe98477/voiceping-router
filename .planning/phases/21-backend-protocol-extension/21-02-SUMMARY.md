---
phase: 21-backend-protocol-extension
plan: 02
subsystem: android-location
tags: [telemetry, battery, network, protocol-extension]
dependency_graph:
  requires: [21-01-backend-telemetry-protocol]
  provides: [android-telemetry-collection]
  affects: [location-tracking, websocket-protocol]
tech_stack:
  added: []
  patterns: [android-system-services, nullable-telemetry, backward-compatible-json]
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/location/LocationUpdate.kt
    - android/app/src/main/java/com/voiceping/android/data/location/LocationManager.kt
decisions:
  - title: "Nullable telemetry with null omission in JSON"
    rationale: "Backward compatibility - old servers ignore unknown fields, emulators return null gracefully"
  - title: "Conservative null fallback (assume 100% battery)"
    rationale: "Don't skip location sends when battery unavailable - safer for field coordination"
  - title: "Binary network type (wifi/cellular only)"
    rationale: "Simplified per user decision - no 3G/4G/5G distinction needed for dispatch awareness"
metrics:
  duration_minutes: 3
  tasks_completed: 2
  files_modified: 2
  commits: 2
  completed_date: 2026-02-16
---

# Phase 21 Plan 02: Android Telemetry Collection Summary

**One-liner:** Android client collects battery percentage, power-save mode, and network type via system services and includes them in LOCATION_UPDATE messages.

## What Was Built

Added battery telemetry collection to the Android client that piggybacks on existing LOCATION_UPDATE messages. The implementation collects three telemetry fields at each location send:

1. **Battery Percentage** (0-100 integer) - via BatteryManager
2. **Power Save Mode** (boolean) - via PowerManager.isPowerSaveMode
3. **Network Type** (wifi/cellular) - via ConnectivityManager/NetworkCapabilities

All values are nullable and gracefully handle unavailability (emulator, permissions, no network). When values are null, they're omitted from the JSON wire format entirely, ensuring backward compatibility with servers that don't expect these fields.

## Tasks Completed

### Task 1: Extend LocationUpdate with telemetry fields (Commit: 26627a6)

Extended the LocationUpdate data class with three new nullable fields: `batteryPercentage`, `powerSaveMode`, and `networkType`. Updated the `toJsonObject()` method to include these fields when non-null using conditional checks (same pattern as existing speed/heading fields).

Field names in JSON use camelCase to match the server's TypeScript interface from Plan 21-01.

**Files modified:**
- `android/app/src/main/java/com/voiceping/android/data/location/LocationUpdate.kt`

### Task 2: Collect telemetry in LocationManager (Commit: d022911)

Added three private telemetry collection methods to LocationManager:

- `getBatteryLevel()` - Updated to return `Int?` with try-catch for graceful failure handling
- `getPowerSaveMode()` - Uses PowerManager.isPowerSaveMode (API 21+, no permissions required)
- `getNetworkType()` - Uses ConnectivityManager/NetworkCapabilities to detect wifi vs cellular

Updated `emitLocationUpdate()` to collect all three telemetry values at send time and pass them to the LocationUpdate constructor. Updated existing `getBatteryLevel()` callers in `requestPttTriggeredLocation()` and `startTrackingWithAdaptiveInterval()` to handle nullable return with conservative fallback (assume 100% battery if unavailable).

**Files modified:**
- `android/app/src/main/java/com/voiceping/android/data/location/LocationManager.kt`

**Imports added:**
- `android.net.ConnectivityManager`
- `android.net.NetworkCapabilities`
- `android.os.PowerManager`

## Deviations from Plan

None - plan executed exactly as written.

## Verification Results

- ✅ `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileDebugKotlin` passed
- ✅ LocationUpdate data class has batteryPercentage, powerSaveMode, networkType fields
- ✅ toJsonObject() includes telemetry fields when non-null, omits when null
- ✅ LocationManager.emitLocationUpdate() reads all 3 telemetry values at send time
- ✅ getBatteryLevel() returns null on failure (doesn't crash)
- ✅ getPowerSaveMode() returns Boolean? with null on failure
- ✅ getNetworkType() returns "wifi", "cellular", or null
- ✅ No new permissions required (all APIs available without permissions on API 21+)
- ✅ Existing location intervals, deduplication, offline queue unchanged

## Technical Notes

### Backward Compatibility Mechanism

The null-omission pattern in JSON serialization ensures backward compatibility:
- When telemetry values are available, they're included in the JSON
- When values are null (emulator, failure), they're omitted entirely
- Old servers that don't expect these fields simply ignore unknown JSON properties
- New servers that expect these fields get null-safe types (optional fields in TypeScript)

### System Service API Usage

All telemetry collection uses standard Android system services:
- **BatteryManager** - No permissions required, returns battery percentage or null on failure
- **PowerManager.isPowerSaveMode** - Available since API 21, no permissions required
- **ConnectivityManager.getNetworkCapabilities()** - No permissions required for reading transport type (wifi/cellular)

### Conservative Null Handling

When battery level is unavailable (emulator, failure), the code assumes 100% battery using `(batteryLevel ?: 100)`. This is a conservative approach that ensures location sends aren't skipped when battery info is unavailable - important for field coordination where location reliability is critical.

### Performance Impact

Telemetry collection adds negligible overhead:
- Each collection method is a single system service query (no I/O, no network)
- Collection happens at send time (already low frequency - 30s-5min intervals)
- Try-catch blocks handle failures gracefully without crashing or blocking

## Success Criteria Met

✅ Android client includes battery percentage (0-100), power-save mode (boolean), and network type (wifi/cellular) in every LOCATION_UPDATE message

✅ Values are null when unavailable (graceful failure handling)

✅ Existing location tracking behavior completely unchanged (intervals, deduplication, offline queue)

✅ Kotlin compiles without errors

## Next Steps

The Android client now sends telemetry to the server via LOCATION_UPDATE messages. The server (Plan 21-01) already validates and broadcasts these fields to dispatch users. Next phase can focus on dispatch UI to display battery warnings and network status.

---

## Self-Check: PASSED

**Created files:** None (only modifications)

**Modified files:**
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/data/location/LocationUpdate.kt
- ✅ FOUND: android/app/src/main/java/com/voiceping/android/data/location/LocationManager.kt

**Commits:**
- ✅ FOUND: 26627a6 (Task 1: Extend LocationUpdate with telemetry fields)
- ✅ FOUND: d022911 (Task 2: Collect telemetry in LocationManager)
