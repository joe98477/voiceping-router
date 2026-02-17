# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-16)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** v5.0 Dispatch Map View - Phase 23

## Current Position

Phase: 26 of 26 (Map Controls and Polish)
Plan: 2 of 3 (PLAN 02 COMPLETE)
Status: Phase 26 in progress
Last activity: 2026-02-17 — Completed 26-02 map search autocomplete

Progress: [███████████████████████████████████████████████████████████████████████████████] 85/85 plans (100.0%)

## Performance Metrics

**Velocity:**
- Total plans completed: 85 (v1.0: 24, v2.0: 26, v3.0: 10, v4.0: 13, v5.0: 12)
- Average duration: v1.0 ~10.5 min, v2.0 ~8.2 min, v3.0 ~4.0 min, v4.0 ~6.2 min, v5.0 ~3.2 min
- Total execution time: v1.0 ~4.2 hours, v2.0 ~3.5 hours, v3.0 ~0.67 hours, v4.0 ~1.28 hours, v5.0 ~0.58 hours

**By Milestone:**

| Milestone | Phases | Plans | Status |
|-----------|--------|-------|--------|
| v1.0 WebRTC Rebuild | 4 | 24/24 | Complete (2026-02-07) |
| v2.0 Android Client | 6 | 26/26 | Complete (2026-02-13) |
| v3.0 mediasoup Integration | 5 | 10/10 | Complete (2026-02-15) |
| v4.0 Production Hardening | 5 | 13/13 | Complete (2026-02-16) |
| v5.0 Dispatch Map View | 6 | 12/12 | In progress |

**Recent Plan:**
| Phase | Plan | Duration | Tasks | Files |
|-------|------|----------|-------|-------|
| Phase 22 P01 | 146 sec | 2 tasks | 3 files | CSS Grid split-panel layout |
| Phase 22 P02 | 210 sec | 2 tasks | 4 files | Panel collapse and responsive layout |
| Phase 23 P01 | 110 sec | 2 tasks | 3 files | MapView component with Leaflet |
| Phase 23 P02 | ~5 min | 2 tasks | 4 files | Mount MapView in DispatchConsole |
| Phase 24 P01 | 258 sec | 2 tasks | 5 files | userName and LocationContext |
| Phase 24 P02 | 229 sec | 2 tasks | 3 files | WebSocket integration and marker rendering |
| Phase 25 P02 | 178 sec | 2 tasks | 2 files | Location enrichment and popup content |
| Phase 25 P01 | 269 sec | 2 tasks | 2 files | Motion state icons and staleness |
| Phase 25 P03 | 198 sec | 2 tasks | 4 files | Marker clustering with tooltips and popups |
| Phase 26 P01 | 155 sec | 2 tasks | 4 files | Toolbar, auto-fit, toast, useLocalStorage |
| Phase 26 P02 | 209 sec | 2 tasks | 5 files | MapSearch autocomplete with keyboard nav |

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md Key Decisions table (43 entries across 4 milestones).

Recent decisions affecting v5.0:
- Phase 18 (v4.0): SQLite for location storage (not PostgreSQL) — lightweight, embedded, 24h retention
- Phase 21 (v5.0): Battery % as additive protocol field — backward-compatible, old clients omit field
- Phase 21 (v5.0): Leaflet + Esri World Imagery for dispatch map — free, no API key, satellite tiles
- Phase 21 (v5.0): Cross-component stability constraint — all protocol changes backward-compatible
- Phase 21-02 (v5.0): Nullable telemetry with null omission in JSON — backward compatible, emulators return null gracefully
- Phase 21-02 (v5.0): Conservative null fallback (assume 100% battery) — don't skip location sends when battery unavailable
- Phase 22-01 (v5.0): No visible divider between panels (gap: 0) — map panel background shade provides subtle boundary
- Phase 22-01 (v5.0): Sharp edges on map-container (no border-radius) — allows edge-to-edge Leaflet rendering
- Phase 22-01 (v5.0): Stats bar space-around — items spread evenly across full page width
- Phase 22-01 (v5.0): Channel cards 180px minmax — ensures 2-column fit in 500px channels panel
- Phase 22-02 (v5.0): No collapse state persistence — plain useState without localStorage (deferred)
- Phase 22-02 (v5.0): Default mobile tab is Channels — dispatch operators see channels first on mobile
- Phase 22-02 (v5.0): 40px collapsed strip width — maximizes map space while showing team indicators
- Phase 22-02 (v5.0): 1200px responsive breakpoint — clean split between desktop/mobile modes
- Phase 22-02 (v5.0): CSS-only visibility control — both panels stay mounted for continuous audio monitoring
- Phase 23-01 (v5.0): Vanilla Leaflet with useRef (not react-leaflet) — better cleanup control, React Strict Mode compatible
- Phase 23-01 (v5.0): Esri satellite + labels overlay — hybrid view with road/place names on satellite imagery
- Phase 23-01 (v5.0): 5-second geolocation timeout with Sydney fallback — prevents infinite hang
- Phase 23-01 (v5.0): Event-scoped localStorage key (cv.dispatch.map.{eventId}) — prevents key collision between events
- Phase 23-01 (v5.0): Keyboard navigation disabled on map — avoids conflicts with app shortcuts
- Phase 23-02 (v5.0): Leaflet control init order determines stacking — bottom-most controls added first
- Phase 23-02 (v5.0): Collapse chevron z-index 1001 — above Leaflet controls (z-index 1000)
- Phase 23-02 (v5.0): ResizeObserver + invalidateSize for panel collapse/expand handling
- Phase 24-01 (v5.0): userName required field on LocationData — server always has it from JWT ClientContext
- Phase 24-01 (v5.0): userName not persisted in SQLite — only needed for real-time broadcasts/queries
- Phase 24-01 (v5.0): 1-hour window filter in getAllLatestPositions() — dispatch map shows recent positions only
- Phase 24-01 (v5.0): LocationContext separate from ChannelContext — high-frequency location updates must not re-render channel components
- Phase 24-01 (v5.0): Eager stale cleanup in updateLocation() — O(N) iteration per update acceptable for dispatch scale
- Phase 24-02 (v5.0): Dedicated WebSocket for location updates — separate from channel connections for clean lifecycle
- Phase 24-02 (v5.0): LOCATION_QUERY correlation ID matching — server uses generic channel-state type, ID is reliable match
- Phase 24-02 (v5.0): Staggered batch updates for >5 positions — 50ms delay prevents UI freeze on bulk load
- Phase 24-02 (v5.0): isMapVisible from responsive breakpoint — desktop always true, mobile only when activeTab === 'map'
- Phase 24-02 (v5.0): Conditional marker icon update — only recreate DivIcon if userName changed (performance optimization)
- Phase 25-02 (v5.0): Member userId extraction handles string IDs and object with .userId/.id — defensive coding for overview endpoint
- Phase 25-02 (v5.0): Speed displayed in km/h (server sends m/s) via 3.6 multiplier — matches Android UI convention
- Phase 25-02 (v5.0): Battery as text percentage only (no color coding) — simple text display in popup per spec
- Phase 25-02 (v5.0): Connection quality heuristic from networkType — wifi > cellular, power save degrades quality
- Phase 25-02 (v5.0): PTT button disabled placeholder in popup — reserved for future direct-to-user communication
- [Phase 25-01]: Same orange color for all motion states — icon shape alone conveys state
- [Phase 25-01]: 5-minute threshold for stale status with instant recovery (no transition)
- [Phase 25-01]: Zoom level >= 15 shows username labels for detail when zoomed in
- Phase 26-01 (v5.0): useLocalStorage hook exports [value, setValue, reset] tuple — ergonomic state management
- Phase 26-01 (v5.0): Toast notification as module-level function (not React component) — simplicity for temporary DOM elements
- Phase 26-01 (v5.0): Auto-fit fires once on initial load with 300ms delay — prevents race condition with cluster group init
- Phase 26-01 (v5.0): Single marker zoom cap at 14 (not 18) — prevents excessive zoom-in for isolated positions
- Phase 26-01 (v5.0): Glassmorphic toolbar with backdrop-filter and @supports fallback — graceful degradation for older browsers
- Phase 26-01 (v5.0): Asymmetric padding [50, 80] for flyToBounds — 80px top clearance for toolbar visibility
- [Phase 26-02]: Search input always visible (not icon-that-expands) for immediate access
- [Phase 26-02]: Search term persists after selection for sequential searches
- [Phase 26-02]: Zoom level 16 for fly-to (detail without excessive zoom)
- [Phase 26-02]: No debounce on search input (direct filtering performs fine at dispatch scale)

### Pending Todos

None.

### Blockers/Concerns

**Carried forward:**
- Consumer.getStats() returns stub quality (crow-misia API undocumented) — low priority
- No automated integration tests for mediasoup audio pipeline — deferred
- HW-02 rugged phone PTT hardware unavailable — deferred
- PWR-04 battery profiling not validated — implementation complete, profiling deferred
- Hardcoded JWT secret default — production MUST override via ROUTER_JWT_SECRET

**v5.0 specific:**
- Marker performance collapse at 200+ markers — Phase 25 must use canvas or clustering, not DOM markers
- WebSocket message queue saturation — Phase 24 needs separate LocationContext from ChannelContext

## Session Continuity

Last session: 2026-02-17
Stopped at: Completed 26-02-PLAN.md
Resume file: .planning/phases/26-map-controls-and-polish/26-02-SUMMARY.md

**Next action:** Execute Plan 03 of Phase 26 (MapSettings modal - final plan)

**All Milestones:**
- v1.0 WebRTC Audio Rebuild + Web UI — SHIPPED 2026-02-07
- v2.0 Android Client App — SHIPPED 2026-02-13
- v3.0 mediasoup Library Integration — SHIPPED 2026-02-15
- v4.0 Production Hardening & Location — SHIPPED 2026-02-16
- v5.0 Dispatch Map View — IN PROGRESS (Phase 21-26)

---
*Last updated: 2026-02-17 after completing Phase 26 Plan 02*
