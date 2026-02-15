---
phase: 18-location-tracking
plan: 02
subsystem: server
tags:
  - location-tracking
  - sqlite
  - real-time-broadcast
  - dispatch-integration
  - websocket-api
dependency_graph:
  requires:
    - better-sqlite3@^11.10.0 (native module)
    - SignalingHandlers message routing
    - SignalingServer WebSocket infrastructure
    - UserRole.DISPATCH permission model
  provides:
    - LocationStore SQLite persistence
    - LocationBroadcaster dispatch notification
    - location-update WebSocket handler
    - location-batch WebSocket handler
    - location-query WebSocket handler (dispatch-only)
    - location-broadcast server→dispatch message
  affects:
    - src/shared/protocol.ts (4 new SignalingType entries)
    - src/server/signaling/handlers.ts (3 new handlers)
    - src/server/signaling/websocketServer.ts (routing + sendToAllDispatchUsers)
    - src/server/index.ts (location service wiring + cleanup interval)
tech_stack:
  added:
    - better-sqlite3: ^11.10.0 (SQLite3 with native binding)
    - @types/better-sqlite3: ^7.6.13
  patterns:
    - SQLite WAL mode for concurrent read/write
    - CHECK constraints for lat/lng validation at DB level
    - In-memory cache for latest positions (LocationBroadcaster)
    - Fire-and-forget protocol for location updates (no response)
    - Batch transaction for location-batch atomic insert
    - Hourly cleanup interval with setInterval
key_files:
  created:
    - src/server/location/types.ts (LocationData, LocationPosition, validation)
    - src/server/location/LocationStore.ts (SQLite wrapper with WAL mode)
    - src/server/location/LocationBroadcaster.ts (dispatch broadcast + stale detection)
    - src/server/location/LOCATION_API.md (WebSocket protocol documentation)
  modified:
    - package.json (better-sqlite3 dependencies)
    - package-lock.json (lockfile with native module)
    - src/shared/protocol.ts (LOCATION_UPDATE, LOCATION_BATCH, LOCATION_QUERY, LOCATION_BROADCAST)
    - src/server/signaling/handlers.ts (setLocationServices, 3 handlers)
    - src/server/signaling/websocketServer.ts (sendToAllDispatchUsers, routing)
    - src/server/index.ts (location service init, hourly cleanup, graceful shutdown)
decisions:
  - Install better-sqlite3@^11.10.0 with --ignore-scripts to avoid mediasoup postinstall error
  - Build better-sqlite3 native module separately via npm run build-release
  - Use SQLite CHECK constraints for lat/lng validation (server + DB layer)
  - Fire-and-forget protocol for location-update/batch (no response for performance)
  - Dispatch-only permission for location-query (matches LOC-05 design decision)
  - 5-minute stale threshold for dispatch UI indicator
  - 24-hour retention with hourly cleanup (not per-update cleanup)
  - In-memory cache in LocationBroadcaster for deduplication (skip older timestamps)
  - Broadcast only latest update from location-batch (not all updates)
  - WAL journal mode for SQLite (better concurrent read performance)
metrics:
  duration: 783s
  tasks_completed: 2
  commits: 2
  files_created: 4
  files_modified: 6
  completed: 2026-02-15
---

# Phase 18 Plan 02: Server-Side Location Infrastructure Summary

Server-side location tracking foundation with SQLite storage, real-time dispatch broadcast, and WebSocket API for Android clients and future dispatch web UI integration.

## One-Liner

SQLite location storage with better-sqlite3, real-time broadcast to dispatch users, lat/lng validation (-90/90, -180/180), 24-hour retention with hourly cleanup, and WebSocket API documentation for future dispatch web UI (LOC-06).

## Accomplishments

### Task 1: Install better-sqlite3, create location types, and update shared protocol

**Duration:** ~210s (including native module build)
**Commit:** `a95af9d`

- Installed better-sqlite3@^11.10.0 with native module (used --ignore-scripts to avoid mediasoup postinstall conflict)
- Built native module separately via npm run build-release in node_modules/better-sqlite3
- Installed @types/better-sqlite3@^7.6.13
- Created src/server/location/types.ts:
  - LocationData interface (userId, lat, lng, accuracy, speed, heading, motionState, timestamp)
  - LocationPosition extends LocationData with isStale boolean
  - validateLocation(lat, lng): validates -90/90 and -180/180 ranges, returns error string or null
  - isValidMotionState(state): type guard for 'still' | 'walking' | 'driving' | 'unknown'
- Updated src/shared/protocol.ts SignalingType enum:
  - LOCATION_UPDATE = 'location-update'
  - LOCATION_BATCH = 'location-batch'
  - LOCATION_QUERY = 'location-query'
  - LOCATION_BROADCAST = 'location-broadcast'

**Verification:** `npx tsc --noEmit` passed without errors

---

### Task 2: LocationStore, LocationBroadcaster, handler integration, and server wiring

**Duration:** ~573s
**Commit:** `20446f4`

#### 2.1. LocationStore (src/server/location/LocationStore.ts)

- SQLite database with WAL journal mode for concurrent reads
- Constructor creates data/ directory if not exists, initializes schema
- Table: locations with:
  - CHECK constraints: lat (-90/90), lng (-180/180), accuracy >= 0, speed >= 0, heading (0-360)
  - Indexes: idx_locations_user_time (user_id, timestamp DESC), idx_locations_created (created_at)
- Methods:
  - insertLocation(data): single insert with prepared statement
  - insertBatch(updates): transaction-wrapped batch insert
  - getLatestPositions(): subquery to get latest per user (SQLite doesn't support DISTINCT ON)
  - cleanupOldLocations(): DELETE WHERE created_at < datetime('now', '-24 hours'), returns row count
  - close(): close database connection

#### 2.2. LocationBroadcaster (src/server/location/LocationBroadcaster.ts)

- In-memory cache: Map<userId, LocationData> for deduplication
- broadcastLocation(userId, location):
  - Skip if existing timestamp >= new timestamp (deduplication)
  - Update cache
  - Broadcast JSON to dispatch users via sendToDispatchUsers callback
- getAllLatestPositions():
  - Map all cached positions
  - Add isStale flag: true if timestamp > 5 minutes old
  - Return LocationPosition[]
- clearStaleCache(): remove entries > 24 hours old (optional periodic cleanup)

#### 2.3. Handler Integration (src/server/signaling/handlers.ts)

- Added imports: LocationStore, LocationBroadcaster, validateLocation, isValidMotionState
- Added fields: locationStore?, locationBroadcaster?
- Added setLocationServices(store, broadcaster): setter method
- Added handlers:
  - handleLocationUpdate(ctx, message):
    - Validate lat/lng with validateLocation()
    - Validate motionState with isValidMotionState()
    - Call locationStore.insertLocation()
    - Call locationBroadcaster.broadcastLocation()
    - Fire-and-forget (no response)
  - handleLocationBatch(ctx, message):
    - Validate each update individually (skip invalid, don't reject)
    - Add userId to each update
    - Call locationStore.insertBatch(validUpdates)
    - Broadcast only LATEST update (last in array)
    - Fire-and-forget (no response)
  - handleLocationQuery(ctx, message):
    - Check ctx.role === UserRole.DISPATCH (dispatch-only permission)
    - Call locationBroadcaster.getAllLatestPositions()
    - Send response with positions array and count

#### 2.4. WebSocket Routing (src/server/signaling/websocketServer.ts)

- Added sendToAllDispatchUsers(message: string):
  - Iterate clients, filter by ctx.role === UserRole.DISPATCH
  - Send raw JSON string (optimized for broadcast)
  - Log sent count
- Added routing in routeMessage():
  - SignalingType.LOCATION_UPDATE → handlers.handleLocationUpdate
  - SignalingType.LOCATION_BATCH → handlers.handleLocationBatch
  - SignalingType.LOCATION_QUERY → handlers.handleLocationQuery

#### 2.5. Server Wiring (src/server/index.ts)

- Added imports: LocationStore, LocationBroadcaster
- After PermissionSyncManager initialization (step 7.6):
  - Create locationStore = new LocationStore('./data/locations.db')
  - Create locationBroadcaster with sendToAllDispatchUsers callback
  - Call handlers.setLocationServices(locationStore, locationBroadcaster)
  - Start locationCleanupInterval = setInterval(() => locationStore.cleanupOldLocations(), 60 * 60 * 1000)
- In graceful shutdown:
  - clearInterval(locationCleanupInterval)
  - locationStore.close()

#### 2.6. Documentation (src/server/location/LOCATION_API.md)

Comprehensive WebSocket API documentation for future dispatch web UI integration (LOC-06):
- Message type specifications (location-update, location-batch, location-query, location-broadcast)
- Field constraints and validation rules
- SQLite schema with retention policy
- Integration guide with TypeScript examples:
  - Initial load with location-query
  - Real-time updates with location-broadcast listener
  - Client-side stale detection (5-minute timeout)
  - Motion state visualization
- Error handling patterns
- Performance considerations (fire-and-forget, WAL mode, deduplication)

**Verification:** `npx tsc --noEmit` passed without errors

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Native module build error with mediasoup postinstall**
- **Found during:** Task 1 npm install
- **Issue:** mediasoup@3.19.17 postinstall script fails on Node 18 with "Unexpected token 'with'" (ES2025 syntax)
- **Fix:** Used --ignore-scripts flag to skip postinstall, built better-sqlite3 native module separately via npm run build-release in node_modules/better-sqlite3 directory
- **Files modified:** None (installation workaround only)
- **Commit:** Included in a95af9d

---

## Self-Check: PASSED

**Created files verified:**
```bash
FOUND: src/server/location/types.ts
FOUND: src/server/location/LocationStore.ts
FOUND: src/server/location/LocationBroadcaster.ts
FOUND: src/server/location/LOCATION_API.md
```

**Modified files verified:**
```bash
FOUND: package.json (better-sqlite3: ^11.10.0)
FOUND: src/shared/protocol.ts (4 location SignalingType entries)
FOUND: src/server/signaling/handlers.ts (3 location handlers)
FOUND: src/server/signaling/websocketServer.ts (sendToAllDispatchUsers)
FOUND: src/server/index.ts (location service wiring)
```

**Commits verified:**
```bash
FOUND: a95af9d (Task 1: install better-sqlite3 and create location types)
FOUND: 20446f4 (Task 2: add location storage, broadcast, and handler integration)
```

All files created, modified as planned. All commits exist. TypeScript compilation passes.

---

## Integration Points

### Upstream Dependencies (Plan 01 - Android Location Tracking)
- Android client will send location-update messages via WebSocket
- Android client will queue updates offline and send location-batch on reconnect
- Android location service provides lat, lng, accuracy, speed, heading, motionState, timestamp

### Downstream Consumers (LOC-06 - Dispatch Web UI)
- Dispatch web UI will send location-query on load to get initial positions
- Dispatch web UI will listen for location-broadcast for real-time updates
- LOCATION_API.md provides full integration guide with TypeScript examples

### Shared Infrastructure
- SignalingHandlers message routing (existing pattern)
- WebSocket authentication and role-based permissions (UserRole.DISPATCH)
- Graceful shutdown pattern (existing in index.ts)

---

## Technical Highlights

1. **SQLite WAL Mode:** Better concurrent read performance for dispatch queries while Android clients write location updates
2. **CHECK Constraints:** Database-level validation for lat/lng ranges (defense in depth with server validation)
3. **Fire-and-Forget Protocol:** No response for location-update/batch reduces latency and server load
4. **In-Memory Cache:** LocationBroadcaster deduplicates broadcasts by comparing timestamps (skip older/equal)
5. **Batch Transactions:** location-batch uses SQLite transaction for atomic multi-insert (all or nothing)
6. **Stale Indicator:** 5-minute timeout for dispatch UI to show offline users (computed from timestamp age)
7. **Hourly Cleanup:** 24-hour retention enforced by setInterval (1 hour), not per-update (performance)

---

## What's Next

**Phase 18 Plan 03:** Android location service integration (send location-update via WebSocket, queue offline updates, send location-batch on reconnect)

**Phase 18 Plan 04:** Dispatch web UI location map (LOC-06) - consume location-query and location-broadcast via LOCATION_API.md

**Server is ready:** All WebSocket handlers, SQLite storage, and broadcast infrastructure complete. Android and web clients can now integrate.

---

**Execution Model:** autonomous
**Executor:** Claude Sonnet 4.5
**Duration:** 783 seconds (13.05 minutes)
**Date:** 2026-02-15
