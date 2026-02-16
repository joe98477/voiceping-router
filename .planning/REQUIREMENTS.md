# Requirements: VoicePing Dispatch Map View

**Defined:** 2026-02-16
**Core Value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical

## v5.0 Requirements

Requirements for dispatch map milestone. Each maps to roadmap phases.

### Map Visualization

- [ ] **MAP-01**: Dispatch user can view an interactive satellite map in the dispatch console
- [ ] **MAP-02**: Map displays real-time user positions as markers with radio icons
- [ ] **MAP-03**: Each marker shows the associated username as a label
- [ ] **MAP-04**: Markers update in real-time as location broadcasts arrive via WebSocket
- [ ] **MAP-05**: Markers for users with no location update >5 min appear visually faded/stale
- [ ] **MAP-06**: Markers display motion state visually (distinct treatment for STILL/WALKING/DRIVING)
- [ ] **MAP-07**: Nearby markers cluster when zoomed out, expand when zoomed in

### Map Controls

- [ ] **CTRL-01**: Dispatch user can zoom and pan the map interactively
- [ ] **CTRL-02**: Dispatch user can switch map layers (satellite, street, hybrid)
- [ ] **CTRL-03**: Map auto-fits bounds to show all visible user markers
- [ ] **CTRL-04**: Dispatch user can search for a user by name and center the map on them

### Status Popup

- [ ] **POPUP-01**: Hovering over a marker shows a status card with user details
- [ ] **POPUP-02**: Status card displays location data (lat/lng, accuracy, last update time)
- [ ] **POPUP-03**: Status card displays motion state and speed/heading when moving
- [ ] **POPUP-04**: Status card displays current channel and PTT status (speaking/idle)
- [ ] **POPUP-05**: Status card displays connection status
- [ ] **POPUP-06**: Status card displays battery percentage

### Layout

- [ ] **LAYOUT-01**: Dispatch console uses split layout (channel management left, map right)
- [ ] **LAYOUT-02**: Map loads all known user positions on initial connection via LOCATION_QUERY

### Battery Telemetry

- [ ] **TELEM-01**: Android client includes battery percentage in location updates
- [ ] **TELEM-02**: Server stores and broadcasts battery percentage with location data
- [ ] **TELEM-03**: Protocol extension is backward-compatible (old clients without battery field still work)

### Dispatch Settings

- [ ] **SETTINGS-01**: Dispatch user can configure which fields appear in the status popup
- [ ] **SETTINGS-02**: Popup field preferences persist across browser sessions

## Future Requirements

Deferred to future release. Tracked but not in current roadmap.

### Map Enhancements

- **MAP-F01**: Historical location trails showing user movement paths
- **MAP-F02**: Geofencing with zone definition and alerts
- **MAP-F03**: Offline map tile caching for unreliable network scenarios
- **MAP-F04**: Click-to-talk directly from map marker (PTT integration)

### Advanced Dispatch

- **DISP-F01**: Channel coverage overlay (color-code map areas by channel assignment)
- **DISP-F02**: Signal strength indicator in status popup
- **DISP-F03**: Auto-follow mode (map tracks selected user)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| 3D map view | Complexity, no clear dispatch value |
| Street-first default | Satellite is industry standard for dispatch/field operations |
| Real-time geofencing alerts | Server-side polygon evaluation is complex, defer to future |
| Offline maps | Tile storage licensing complexity, dispatch consoles have reliable internet |
| User-to-user messaging from map | PTT is the communication channel, not text messaging |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| MAP-01 | Phase 23 | Pending |
| MAP-02 | Phase 24 | Pending |
| MAP-03 | Phase 24 | Pending |
| MAP-04 | Phase 24 | Pending |
| MAP-05 | Phase 25 | Pending |
| MAP-06 | Phase 25 | Pending |
| MAP-07 | Phase 25 | Pending |
| CTRL-01 | Phase 25 | Pending |
| CTRL-02 | Phase 23 | Pending |
| CTRL-03 | Phase 26 | Pending |
| CTRL-04 | Phase 26 | Pending |
| POPUP-01 | Phase 25 | Pending |
| POPUP-02 | Phase 25 | Pending |
| POPUP-03 | Phase 25 | Pending |
| POPUP-04 | Phase 25 | Pending |
| POPUP-05 | Phase 25 | Pending |
| POPUP-06 | Phase 25 | Pending |
| LAYOUT-01 | Phase 22 | Pending |
| LAYOUT-02 | Phase 24 | Pending |
| TELEM-01 | Phase 21 | Pending |
| TELEM-02 | Phase 21 | Pending |
| TELEM-03 | Phase 21 | Pending |
| SETTINGS-01 | Phase 26 | Pending |
| SETTINGS-02 | Phase 26 | Pending |

**Coverage:**
- v5.0 requirements: 24 total
- Mapped to phases: 24/24 (100%)
- Unmapped: 0

**Phase distribution:**
- Phase 21 (Backend Protocol Extension): 3 requirements
- Phase 22 (Web Layout Split): 1 requirement
- Phase 23 (Map Foundation): 2 requirements
- Phase 24 (Location State and Real-Time Markers): 4 requirements
- Phase 25 (Interactive Markers and Motion State): 10 requirements
- Phase 26 (Map Controls and Polish): 4 requirements

---
*Requirements defined: 2026-02-16*
*Last updated: 2026-02-16 after v5.0 roadmap creation (100% coverage validated)*
