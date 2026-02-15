# Phase 18: Location Tracking - Context

**Gathered:** 2026-02-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Adaptive location tracking with motion-aware throttling for dispatch coordination. Android clients collect and send location data to the server via WebSocket. Server stores, broadcasts to dispatch, and provides initial-load queries. The dispatch map UI itself is a separate future phase.

</domain>

<decisions>
## Implementation Decisions

### Location precision & modes
- Balanced accuracy (40-100m) via FusedLocationProviderClient — not high-accuracy GPS
- Moving mode: location updates every 60 seconds
- Stationary mode: low-frequency updates every 5 minutes (not stopped entirely)
- Vehicle mode: 30s updates (detected via Activity Recognition)
- Cycling treated as walking (60s interval)
- Network/WiFi fallback when GPS unavailable (cell tower positioning ~100-500m)
- Skip sending if user moved < 50m since last send (deduplication)
- PTT-triggered location send only if no update in last 120 seconds
- Auto-start location tracking on login (when permission granted)
- Location tracking only active when app is open or foreground service is running
- No location tracking on boot auto-start — waits until user opens the app
- All roles track equally (no role-based differentiation)
- Lat/lng only (no altitude) — sufficient for 2D map
- Include accuracy radius, speed, heading, and motion state in each update

### Battery adaptation
- Below 20% battery: switch to low-frequency mode (stationary intervals) AND network-only accuracy (no GPS)
- Below 20% battery: suppress PTT-triggered location sends
- Normal battery: full location behavior as configured

### Transport & offline handling
- Send location updates via existing WebSocket signaling connection (not separate HTTP)
- Send each update individually (no batching)
- Queue up to 50 location updates locally when WebSocket disconnected
- Flush all queued updates at once on reconnect (single bulk message)
- Server infers event context from WebSocket session (no event ID in location message)

### User privacy & visibility
- No in-app location tracking indicator — rely on Android system location icon
- Location shown in settings/debug screen for self-verification
- No pause/opt-out toggle — mandatory if permission granted (organization policy)
- Silent degradation if location permission denied (app works, no location sent)
- Don't modify Phase 16 permission education screen — use standard system dialog for location
- Keep existing audio-focused foreground notification text (don't mention location)

### Stationary detection
- Primary: Google ActivityRecognitionClient for still/walk/vehicle detection
- Fallback: GPS displacement if Activity Recognition unavailable
- Switch to stationary mode immediately when "still" detected (no delay)
- Resume moving mode immediately when motion detected
- GPS fallback threshold: < 30m displacement = stationary
- GPS fallback confirmation: 2 consecutive "no movement" fixes before switching
- Include motion state (still/walking/driving) in server updates

### Server data & dispatch
- Store location data in SQLite database (persists across server restarts)
- 24-hour retention with scheduled hourly cleanup of old entries
- Real-time broadcast of all location updates to all connected dispatch users (not channel-scoped)
- Provide "get all latest positions" WebSocket query for dispatch initial load
- Show last known position with stale indicator for offline users (5-minute stale timeout)
- Broadcast all fields: lat, lng, accuracy, speed, heading, motion state, timestamp
- WebSocket-only API (no REST endpoint for now)
- Trust authenticated WebSocket session (no per-message auth checks)
- Basic server-side validation: reject invalid lat/lng range
- Dispatch-only visibility for now (general users cannot see others' locations)
- Add location foreground service type to existing service (not separate service)

### Claude's Discretion
- Exact FusedLocationProviderClient priority settings for each mode
- SQLite schema design and indexing strategy
- WebSocket message format/naming for location events
- Activity Recognition confidence threshold tuning
- Offline queue implementation details (in-memory list vs persistent)
- Stale indicator implementation approach for dispatch broadcast

</decisions>

<specifics>
## Specific Ideas

- PTT location tagging: dispatch sees where someone is when they transmit, but only if no recent update (120s threshold to save battery)
- Queue and resend on reconnect: no gaps in location history even during connectivity loss
- Prepare for future: general users seeing each other's locations (managed per-team by dispatch/admin) — keep the data model extensible

</specifics>

<deferred>
## Deferred Ideas

- Dispatch map UI (web) — separate future phase, this phase provides the data infrastructure
- Per-team location visibility configuration by dispatch/admin — future phase
- General user location view on Android app — future phase (data model should support)
- REST API for location queries — future integration need
- Altitude/elevation tracking — not needed for 2D map

</deferred>

---

*Phase: 18-location-tracking*
*Context gathered: 2026-02-15*
