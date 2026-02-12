---
phase: 10-network-resilience-ux-polish
plan: 01
subsystem: network
tags: [websocket, connectivity, reconnection, exponential-backoff, network-monitoring, latency-measurement]

# Dependency graph
requires:
  - phase: 05-android-foundation
    provides: SignalingClient WebSocket infrastructure with connection state management
provides:
  - NetworkMonitor for connectivity detection (WIFI, CELLULAR, NONE, OTHER)
  - Enhanced ConnectionState with RECONNECTING
  - SignalingClient auto-reconnection with exponential backoff (1s-30s cap, 5-minute max)
  - Network-aware retry (resets backoff on network restore)
  - Latency measurement via heartbeat PING round-trip time
  - Manual retry capability for FAILED state
affects: [10-02-reconnection-ui, 10-03-offline-behavior, network-resilience, connection-state-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Exponential backoff reconnection pattern (1s, 2s, 4s, 8s... capped at 30s)
    - Network-aware retry (NetworkMonitor observation resets backoff on network restore)
    - State-driven reconnection (RECONNECTING distinct from FAILED)
    - Latency measurement via heartbeat PING request-response RTT

key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/data/network/NetworkMonitor.kt
    - android/app/src/main/java/com/voiceping/android/domain/model/NetworkType.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
    - android/app/src/main/java/com/voiceping/android/domain/model/ConnectionState.kt

key-decisions:
  - "Exponential backoff with 30-second cap (prevents excessive delay while maintaining server protection)"
  - "5-minute max retry window before FAILED state (balances persistence with UX)"
  - "Network restore resets backoff to immediate retry (responsive to WiFi-cellular handoff)"
  - "RECONNECTING state distinct from FAILED (enables UI to show 'reconnecting...' vs 'connection lost')"
  - "Latency measurement via heartbeat PING request-response instead of fire-and-forget (provides real-time network quality metric)"

patterns-established:
  - "NetworkMonitor singleton pattern: start() in Application, observe in clients"
  - "Reconnection state tracking: intentionalDisconnect flag prevents reconnection after explicit disconnect()"
  - "Close code handling: 1000 (normal close) vs non-1000 (unexpected disconnect requiring reconnection)"

# Metrics
duration: 11min
completed: 2026-02-12
---

# Phase 10 Plan 01: Network Resilience Foundation Summary

**NetworkMonitor for connectivity detection and SignalingClient auto-reconnection with exponential backoff (1s-30s cap, 5-minute max), network-aware retry reset, and latency measurement via heartbeat PING RTT**

## Performance

- **Duration:** 11 min 28 sec
- **Started:** 2026-02-12T20:16:13Z
- **Completed:** 2026-02-12T20:27:41Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- NetworkMonitor wraps ConnectivityManager with NetworkCallback, exposes network availability and type (WIFI, CELLULAR, NONE, OTHER) as StateFlows
- Enhanced ConnectionState with RECONNECTING value for UI state transitions
- SignalingClient auto-reconnects with exponential backoff (1s, 2s, 4s, 8s... capped at 30s)
- Network restore (WiFi-cellular handoff) resets backoff and retries immediately
- Latency measurement via heartbeat PING request-response round-trip time, exposed as StateFlow
- Manual retry capability (manualRetry()) for Retry button after FAILED state

## Task Commits

Each task was committed atomically:

1. **Task 1: Create NetworkMonitor and NetworkType model** - `a6ead3d` (feat)
2. **Task 2: Enhance SignalingClient with reconnection and latency measurement** - `279008d` (feat)

## Files Created/Modified
- `android/app/src/main/java/com/voiceping/android/data/network/NetworkMonitor.kt` - ConnectivityManager wrapper with NetworkCallback, exposes isNetworkAvailable and networkType StateFlows
- `android/app/src/main/java/com/voiceping/android/domain/model/NetworkType.kt` - Enum for network type (WIFI, CELLULAR, NONE, OTHER)
- `android/app/src/main/java/com/voiceping/android/domain/model/ConnectionState.kt` - Added RECONNECTING state between CONNECTED and FAILED
- `android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt` - Enhanced with NetworkMonitor dependency, exponential backoff reconnection, latency measurement, manualRetry()

## Decisions Made
- **Exponential backoff with 30-second cap:** Prevents excessive delay (maintains UX) while protecting server from reconnection storms. Formula: 2^attempt * 1000ms, capped at 30s per user decision.
- **5-minute max retry window:** Balances persistence (gives network time to recover) with UX (prevents indefinite spinning). After 5 minutes, transitions to FAILED state with Retry button.
- **Network restore resets backoff:** NetworkMonitor observation in init block detects network availability and immediately resets reconnectAttempt to 0, enabling fast recovery from WiFi-cellular handoff.
- **RECONNECTING state distinct from FAILED:** Enables UI to show "reconnecting..." (transient, still trying) vs "connection lost" (gave up, manual retry needed).
- **Latency measurement via heartbeat PING request-response:** Changed heartbeat from send() (fire-and-forget) to request() (round-trip), measuring RTT and exposing via latency StateFlow for network quality indicators.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed Kotlin compilation cache corruption**
- **Found during:** Task 1 verification
- **Issue:** Initial compile failed with "Unresolved reference 'eventId'" despite Team.kt having eventId property - Kotlin cache corruption
- **Fix:** Ran `./gradlew clean compileDebugKotlin` to clear cache
- **Files modified:** None (cache only)
- **Verification:** Clean build passed
- **Committed in:** N/A (cache only)

**2. [Rule 3 - Blocking] Fixed KSP cache directory issue**
- **Found during:** Task 2 verification
- **Issue:** KSP failing with "unexpected jvm signature V" and cache directory errors after multiple builds
- **Fix:** Stopped Gradle daemon (`./gradlew --stop`), cleared .gradle and build directories, rebuilt
- **Files modified:** None (daemon/cache only)
- **Verification:** Clean build successful
- **Committed in:** N/A (daemon/cache only)

---

**Total deviations:** 2 auto-fixed (2 blocking build issues)
**Impact on plan:** All fixes were build system cache issues unrelated to code changes. No scope creep. Plan executed exactly as specified.

## Issues Encountered
- **Kotlin/KSP cache corruption:** Multiple Kotlin daemon sessions caused stale cache. Resolution: Stop daemon, clean build directories, rebuild. This is a known issue with Kotlin 2.2 + Gradle 9.3.1 combination.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- NetworkMonitor and SignalingClient reconnection foundation complete
- Ready for Plan 02: Reconnection UI (connection indicator, network quality display)
- Ready for Plan 03: Offline behavior (queue outgoing messages, sync on reconnect)
- ConnectionState.RECONNECTING enables proper UI state transitions
- Latency StateFlow ready for network quality indicators

## Self-Check

**Files:**
- FOUND: android/app/src/main/java/com/voiceping/android/data/network/NetworkMonitor.kt
- FOUND: android/app/src/main/java/com/voiceping/android/domain/model/NetworkType.kt

**Commits:**
- FOUND: a6ead3d (Task 1: Create NetworkMonitor and NetworkType model)
- FOUND: 279008d (Task 2: Enhance SignalingClient with reconnection and latency measurement)

**Result:** PASSED

