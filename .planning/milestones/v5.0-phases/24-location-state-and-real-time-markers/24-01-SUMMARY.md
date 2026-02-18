---
phase: 24-location-state-and-real-time-markers
plan: 01
subsystem: location-protocol-and-ui-context
tags: [location, protocol, react-context, state-management]
dependency_graph:
  requires: [Phase 18 SQLite location storage, Phase 21 telemetry fields, Phase 23 MapView component]
  provides: [userName in location protocol, LocationContext with Map state, 1-hour query window]
  affects: [Plan 24-02 WebSocket integration, future marker rendering]
tech_stack:
  added: [LocationContext.jsx]
  patterns: [React Context API, Map-based state, eager stale cleanup, event-scoped clearing]
key_files:
  created:
    - web-ui/src/context/LocationContext.jsx
  modified:
    - src/server/location/types.ts
    - src/server/location/LocationBroadcaster.ts
    - src/server/location/LocationStore.ts
    - src/server/signaling/handlers.ts
decisions:
  - decision: "userName required field on LocationData (not optional)"
    rationale: "Server always has userName from JWT ClientContext, dispatch map needs display names"
    alternatives: "Optional userName with fallback to userId"
    outcome: "Cleaner type safety, guaranteed userName availability in broadcasts"
  - decision: "userName not persisted in SQLite"
    rationale: "Location storage is archival, userName only needed for real-time broadcasts/queries"
    alternatives: "Add user_name column to locations table"
    outcome: "Simpler schema, userName injected from in-memory session context"
  - decision: "1-hour window filter in getAllLatestPositions()"
    rationale: "Dispatch map shows recent positions only, markers auto-remove after 1 hour"
    alternatives: "Client-side filtering, configurable threshold"
    outcome: "Server-side filtering reduces payload, consistent with 24h cleanup"
  - decision: "Eager stale cleanup in LocationContext.updateLocation()"
    rationale: "Prevents unbounded Map growth, ensures UI only shows current markers"
    alternatives: "Interval-based cleanup, manual cleanup on query"
    outcome: "O(N) iteration per update acceptable for dispatch scale (100s of users)"
  - decision: "LocationContext separate from ChannelContext"
    rationale: "High-frequency location updates (every 15s) must not re-render channel components"
    alternatives: "Single unified context, location state in Redux"
    outcome: "Render isolation prevents channel card flicker from location updates"
metrics:
  duration: 258 seconds (~4.3 minutes)
  completed_date: 2026-02-17
  tasks_completed: 2
  files_created: 1
  files_modified: 4
---

# Phase 24 Plan 01: Location Protocol userName and LocationContext Summary

**One-liner:** Added userName to server location protocol (broadcasts, query responses, 1-hour window) and created LocationContext with Map-based state management for web UI.

## What Was Built

**Server-side location protocol extension:**
- Added `userName: string` field to `LocationData` interface (required)
- Updated `LocationBroadcaster.broadcastLocation()` to include `userName` in LOCATION_BROADCAST JSON
- Updated `LOW_BATTERY_ALERT` to use `location.userName` instead of placeholder `location.userId`
- Modified `getAllLatestPositions()` to filter positions older than 1 hour (dispatch map view requirement)
- Updated `handleLocationUpdate` and `handleLocationBatch` to pass `ctx.userName` from ClientContext
- Added `userName` placeholder in `LocationStore.getLatestPositions()` (userName not persisted in SQLite)

**Client-side LocationContext:**
- Created `web-ui/src/context/LocationContext.jsx` following ChannelContext pattern
- Uses `Map<userId, LocationPosition>` for O(1) lookups (not array)
- Provides `updateLocation(userId, position)` with eager stale cleanup (removes entries older than 1 hour)
- Provides `setAllLocations(positions)` for bulk set from LOCATION_QUERY response
- Provides `mergeLocations(positions)` for reconnect scenarios (preserves existing, merges fresh data)
- Provides `removeLocation(userId)` and `clearLocations()`
- Auto-clears locations when `eventId` changes (event-scoped state)
- All update functions wrapped in `useCallback` for stable references
- Exports `LocationProvider` and `useLocations` hook

## Key Decisions

**userName as required field on LocationData:**
Server always has userName from JWT ClientContext during WebSocket authentication. Making it required (not optional) provides cleaner type safety and guarantees userName availability in broadcasts for dispatch map marker labels.

**userName not persisted in SQLite:**
Location storage in SQLite is archival (24h retention). userName is only needed for real-time broadcasts and query responses. Simpler to inject userName from in-memory session context than maintain userName in database schema. LocationStore.getLatestPositions() uses userId placeholder (method not currently used in codebase).

**1-hour window filter in getAllLatestPositions():**
Dispatch map shows recent positions only. Filtering server-side reduces LOCATION_QUERY payload and aligns with user decision that markers auto-remove after 1 hour. Consistent with existing 24h cleanup threshold.

**Eager stale cleanup in LocationContext.updateLocation():**
Each location update triggers O(N) iteration to remove entries older than 1 hour. Prevents unbounded Map growth and ensures UI only shows current markers. Acceptable performance for dispatch scale (hundreds of users, not thousands).

**LocationContext separate from ChannelContext:**
High-frequency location updates (every 15 seconds from 100+ field users) must not trigger re-renders of channel components. Separate context provides render isolation, preventing channel card flicker from location updates.

## Deviations from Plan

**Auto-fixed Issues:**

**1. [Rule 3 - Blocking Issue] LocationStore.getLatestPositions() missing userName**
- **Found during:** Task 1, TypeScript compilation after adding userName to LocationData
- **Issue:** LocationStore.getLatestPositions() returns LocationData[] but doesn't retrieve userName from SQLite (userName not stored)
- **Fix:** Added `userName: row.user_id` placeholder in LocationStore.getLatestPositions() mapping (method not currently used, prevents type error)
- **Files modified:** src/server/location/LocationStore.ts
- **Commit:** 67d457c

## Implementation Notes

**Server-side protocol changes:**
- LocationData now requires userName field (TypeScript enforces it)
- LocationBroadcaster in-memory cache stores full LocationData objects (includes userName from handlers)
- 1-hour filter in getAllLatestPositions() skips positions where `(now - timestamp) > 60 * 60 * 1000`
- SQLite schema unchanged (userName not persisted, injected from ClientContext)

**LocationContext state management:**
- Map state initialized as `new Map()` (not `new Map<string, LocationPosition>()` to avoid JSDoc complexity)
- updateLocation creates new Map from prev, sets entry, iterates to remove stale entries
- setAllLocations creates fresh Map from positions array (replaces entire state)
- mergeLocations creates new Map from prev, sets each position from array (preserves existing)
- clearLocations called in useEffect with [eventId, clearLocations] deps (clears on event switch)

**Not wired yet:**
- LocationContext not yet integrated with WebSocket (Plan 02)
- LocationContext not yet provided in app component tree (Plan 02)
- LOCATION_BROADCAST messages not yet consumed by client (Plan 02)

## Testing Performed

**TypeScript compilation:**
- `npx tsc --noEmit` passes with zero errors
- LocationData interface includes `userName: string`
- LOCATION_BROADCAST JSON includes `userName` field
- getAllLatestPositions() includes 1-hour filter

**File verification:**
- LocationContext.jsx exists at web-ui/src/context/LocationContext.jsx
- Exports LocationProvider and useLocations
- Uses Map for state (not array)
- Has all required CRUD operations

## Files Changed

**Created:**
- `web-ui/src/context/LocationContext.jsx` (145 lines) — React Context with Map-based location state

**Modified:**
- `src/server/location/types.ts` — Added userName field to LocationData interface
- `src/server/location/LocationBroadcaster.ts` — Added userName to broadcasts, 1-hour filter in getAllLatestPositions()
- `src/server/location/LocationStore.ts` — Added userName placeholder in getLatestPositions() mapping
- `src/server/signaling/handlers.ts` — Pass ctx.userName in handleLocationUpdate and handleLocationBatch

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | 67d457c | feat(24-01): add userName to location protocol and 1-hour query window |
| Task 2 | f6e2d73 | feat(24-01): create LocationContext with Map-based state management |

## Next Steps (Plan 24-02)

**WebSocket integration and marker rendering:**
- Wire LocationContext into app component tree (wrap DispatchConsole with LocationProvider)
- Subscribe to LOCATION_BROADCAST in WebSocket message handler
- Send LOCATION_QUERY on dispatch user login
- Handle LOCATION_BROADCAST messages: call updateLocation()
- Render markers on MapView from LocationContext.locations Map
- Add marker icons with userName labels
- Add isConnected indicator (online/offline state)

## Self-Check

**Verifying created files:**
```bash
[ -f "web-ui/src/context/LocationContext.jsx" ] && echo "FOUND: web-ui/src/context/LocationContext.jsx" || echo "MISSING: web-ui/src/context/LocationContext.jsx"
```

**Verifying commits:**
```bash
git log --oneline --all | grep -q "67d457c" && echo "FOUND: 67d457c" || echo "MISSING: 67d457c"
git log --oneline --all | grep -q "f6e2d73" && echo "FOUND: f6e2d73" || echo "MISSING: f6e2d73"
```

**Result:**
```
FOUND: web-ui/src/context/LocationContext.jsx
FOUND: 67d457c (Task 1)
FOUND: f6e2d73 (Task 2)
VERIFIED: userName field exists in LocationData
VERIFIED: 1-hour filter exists in getAllLatestPositions()
```

## Self-Check: PASSED
