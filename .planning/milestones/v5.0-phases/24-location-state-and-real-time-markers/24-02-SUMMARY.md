---
phase: 24-location-state-and-real-time-markers
plan: 02
subsystem: location-websocket-and-marker-rendering
tags: [location, websocket, leaflet, markers, real-time-ui]
dependency_graph:
  requires: [Phase 24-01 LocationContext, Phase 23 MapView, Phase 22 split-panel layout]
  provides: [Real-time location markers, LOCATION_BROADCAST integration, LOCATION_QUERY on visibility, orange pin markers]
  affects: [Phase 25 marker clustering, Phase 26 location telemetry display]
tech_stack:
  added: [Leaflet DivIcon markers, WebSocket location stream]
  patterns: [Named WebSocket event listeners, correlation ID matching, staggered batch updates, marker Map ref, responsive visibility detection]
key_files:
  created: []
  modified:
    - web-ui/src/pages/DispatchConsole.jsx
    - web-ui/src/components/MapView.jsx
    - web-ui/src/styles.css
decisions:
  - decision: "Dedicated WebSocket for location updates (separate from channel connections)"
    rationale: "LocationContext needs location-broadcast messages but is scoped to map panel, not individual channels"
    alternatives: "Reuse channel WebSocket connections, single global WebSocket"
    outcome: "Clean separation of concerns, map panel has its own connection lifecycle"
  - decision: "LOCATION_QUERY sent on map visibility with correlation ID"
    rationale: "Server uses generic sendResponse (type: channel-state), correlation ID is only reliable match"
    alternatives: "Match on message type, hardcode expected response structure"
    outcome: "Robust query/response matching, future-proof for protocol changes"
  - decision: "Staggered batch updates for >5 positions (50ms delay per position)"
    rationale: "Prevents UI freeze when loading many markers at once, provides visual feedback"
    alternatives: "Instant bulk load, Web Worker for marker creation"
    outcome: "Smooth loading experience, acceptable performance for dispatch scale"
  - decision: "isMapVisible computed from responsive breakpoint (desktop always true, mobile activeTab)"
    rationale: "Map panel always mounted on desktop (CSS visibility control), only hidden on mobile tabs"
    alternatives: "CSS-only detection, IntersectionObserver"
    outcome: "Simple and correct, matches Phase 22 responsive behavior"
  - decision: "Marker HTML updated only if userName changed"
    rationale: "Avoids recreating icon on every position update (performance optimization)"
    alternatives: "Always recreate icon, separate userName change detector"
    outcome: "Efficient updates, CSS transition handles position animation smoothly"
  - decision: "5-minute interval for stale marker cleanup (1-hour threshold)"
    rationale: "Balance between cleanup frequency and performance, aligns with server-side 1-hour filter"
    alternatives: "Cleanup on every update, longer interval"
    outcome: "Consistent with server-side filtering, prevents unbounded marker growth"
metrics:
  duration: 229 seconds (~3.8 minutes)
  completed_date: 2026-02-17
  tasks_completed: 2
  files_created: 0
  files_modified: 3
---

# Phase 24 Plan 02: WebSocket Integration and Marker Rendering Summary

**One-liner:** Wired LocationContext to WebSocket location-broadcast messages, rendered orange pin DivIcon markers with username labels and smooth slide animation on dispatch map.

## What Was Built

**DispatchConsole.jsx — LocationProvider and dedicated WebSocket:**
- Added LocationProvider wrapping main content (both channels panel and map panel)
- Created dedicated WebSocket connection for location updates (separate from channel connections)
- WebSocket uses token as sec-websocket-protocol for authentication
- Computed isMapVisible based on responsive breakpoint: desktop (>=1200px) always true, mobile (<1200px) only when activeTab === 'map'
- Added media query listener to track desktop/mobile mode changes
- Passed ws (WebSocket ref) and isMapVisible to MapView component

**MapView.jsx — WebSocket listeners and marker rendering:**
- Added useLocations hook to access LocationContext state
- Added markersRef (Map<userId, L.Marker>) for efficient marker updates
- Added hasQueriedRef to prevent duplicate LOCATION_QUERY sends
- Implemented LOCATION_BROADCAST listener: parses JSON, checks type, calls updateLocation
- Implemented LOCATION_QUERY with correlation ID: sends on map visibility, matches response by message.id
- Staggered batch updates for >5 positions (50ms delay per position) to prevent UI freeze
- Marker rendering with DivIcon: creates new markers, updates existing markers (setLatLng + conditional icon update)
- Removes markers from map when removed from LocationContext
- Stale marker cleanup timer: 5-minute interval, removes markers older than 1 hour
- Named event listener functions (not inline arrows) for proper cleanup

**styles.css — Orange pin marker styling:**
- Added smooth marker slide animation (200ms transition on .leaflet-marker-pane)
- Added .user-marker container (position: relative)
- Added .user-marker__label: 10px orange text (#FF9800), translucent white pill (rgba(255,255,255,0.85)), positioned above pin
- Added .user-marker__pin: 30px orange teardrop (#FF9800), rotated -45deg, drop shadow
- Added .user-marker__icon: counter-rotated +45deg to keep person icon upright
- Overrode Leaflet DivIcon defaults: transparent background, no border

## Key Decisions

**Dedicated WebSocket for location updates:**
LocationContext needs location-broadcast messages but is scoped to the map panel, not individual channels. Using a separate WebSocket connection provides clean separation of concerns and independent lifecycle management.

**LOCATION_QUERY correlation ID matching:**
Server's sendResponse uses generic type: 'channel-state', so correlation ID (message.id) is the only reliable way to match query response. Future-proof for protocol changes.

**Staggered batch updates for large position sets:**
When loading >5 positions from LOCATION_QUERY, stagger updates with 50ms delay per position. Prevents UI freeze and provides visual feedback. Acceptable performance for dispatch scale (hundreds of users).

**isMapVisible computed from responsive breakpoint:**
Map panel is always mounted on desktop (CSS visibility control per Phase 22), only hidden on mobile when channels tab is active. Media query detection ensures LOCATION_QUERY fires correctly in both modes.

**Conditional marker icon update:**
Only recreate DivIcon if userName changed. Position updates use setLatLng which triggers CSS transition for smooth animation. Avoids unnecessary DOM manipulation.

**5-minute stale marker cleanup interval:**
Balance between cleanup frequency and performance. 1-hour threshold aligns with server-side filtering in getAllLatestPositions(). Prevents unbounded marker growth.

## Deviations from Plan

None — plan executed exactly as written.

## Implementation Notes

**WebSocket connection lifecycle:**
- Created in useEffect with [token, wsUrl] dependencies
- Stored in locationWsRef for access in MapView
- Cleaned up on token change or unmount

**LOCATION_BROADCAST listener:**
- Guards: returns early if !ws || ws.readyState !== WebSocket.OPEN
- Named handleMessage function for proper cleanup
- JSON.parse with try/catch for error handling
- Checks message.type === 'location-broadcast'
- Calls updateLocation(data.userId, data)

**LOCATION_QUERY flow:**
- Guards: hasQueriedRef, isMapVisible, WebSocket ready
- Generates correlation ID: 'loc-query-' + Date.now()
- Named handleQueryResponse function
- Matches response by message.id === queryId
- Checks message.data?.positions to confirm location query response
- Staggered updates for >5 positions, mergeLocations for smaller sets
- Sets hasQueriedRef.current = true to prevent duplicate queries
- Removes listener after successful match

**Marker rendering logic:**
- Iterates locations Map
- For existing markers: setLatLng, conditional icon update if userName changed
- For new markers: creates L.divIcon with HTML template, adds to map, stores in markersRef
- For removed markers: map.removeLayer, delete from markersRef
- Cleanup on unmount: removes all markers from map, clears markersRef

**DivIcon structure:**
```html
<div class="user-marker__label">${userName}</div>
<div class="user-marker__pin">
  <div class="user-marker__icon">
    <svg viewBox="0 0 24 24" width="14" height="14" fill="white">
      <circle cx="12" cy="8" r="3.5"/>
      <path d="M12 14c-4.4 0-8 2-8 4.5V20h16v-1.5c0-2.5-3.6-4.5-8-4.5z"/>
    </svg>
  </div>
</div>
```

**CSS marker styling:**
- Orange pin: #FF9800, 30px, teardrop shape (border-radius: 50% 50% 50% 0, transform: rotate(-45deg))
- Drop shadow: 0 3px 5px rgba(0,0,0,0.4)
- Label: above pin (bottom: 100%), translucent pill, 10px orange text
- Smooth slide: 200ms linear transition on .leaflet-marker-pane
- Person icon: counter-rotated to stay upright

## Testing Performed

**Web-ui build:**
- `npx vite build --mode development` passes with zero errors
- CSS bundle size increased from 48.12 kB to 48.95 kB (marker styles added)

**Code verification:**
- MapView uses useLocations() hook
- DispatchConsole wraps content with LocationProvider
- MapView creates DivIcon markers
- LOCATION_QUERY sent with correlation ID
- LOCATION_BROADCAST listener implemented
- All CSS classes present with correct properties

## Files Changed

**Modified:**
- `web-ui/src/pages/DispatchConsole.jsx` — Added LocationProvider, dedicated WebSocket, isMapVisible computation
- `web-ui/src/components/MapView.jsx` — Added WebSocket listeners, marker rendering, stale cleanup
- `web-ui/src/styles.css` — Added marker CSS (orange pin, translucent label, smooth animation)

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | ef4a688 | feat(24-02): wire LocationProvider and WebSocket, render DivIcon markers |
| Task 2 | a39e474 | feat(24-02): add marker CSS with orange pin, translucent label, smooth animation |

## Next Steps (Phase 25)

**Marker clustering and performance optimization:**
- Current implementation uses DOM markers (will collapse at 200+ markers per STATE.md blocker)
- Phase 25 must implement canvas rendering or marker clustering
- Options: Leaflet.markercluster plugin, custom canvas renderer, geohash-based aggregation
- Performance target: handle 500+ simultaneous field users

**Location telemetry display (Phase 26):**
- Show battery percentage on marker popup/tooltip
- Show motion state indicator (stationary/moving/high-speed)
- Show connection quality (network type, signal strength)
- Show last update timestamp
- Power save mode indicator

## Self-Check

**Verifying modified files:**
```bash
[ -f "web-ui/src/pages/DispatchConsole.jsx" ] && echo "FOUND: DispatchConsole.jsx" || echo "MISSING"
[ -f "web-ui/src/components/MapView.jsx" ] && echo "FOUND: MapView.jsx" || echo "MISSING"
[ -f "web-ui/src/styles.css" ] && echo "FOUND: styles.css" || echo "MISSING"
```

**Verifying commits:**
```bash
git log --oneline --all | grep -q "ef4a688" && echo "FOUND: ef4a688 (Task 1)" || echo "MISSING"
git log --oneline --all | grep -q "a39e474" && echo "FOUND: a39e474 (Task 2)" || echo "MISSING"
```

**Result:**
```
FOUND: DispatchConsole.jsx
FOUND: MapView.jsx
FOUND: styles.css
FOUND: ef4a688 (Task 1)
FOUND: a39e474 (Task 2)
VERIFIED: LocationProvider wraps main content
VERIFIED: WebSocket connection created
VERIFIED: LOCATION_BROADCAST listener implemented
VERIFIED: LOCATION_QUERY with correlation ID
VERIFIED: DivIcon markers rendered from LocationContext
VERIFIED: Orange pin CSS (#FF9800)
VERIFIED: Smooth animation (200ms transition)
VERIFIED: Translucent label (rgba(255,255,255,0.85))
```

## Self-Check: PASSED
