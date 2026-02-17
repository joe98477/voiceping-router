# Phase 23: Map Foundation - Context

**Gathered:** 2026-02-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Integrate Leaflet map library into the dispatch console's right panel with Esri satellite and OSM street tile layers, proper React cleanup to prevent memory leaks, and base map controls. This is the visual foundation — no markers, popups, or real-time data (those are Phases 24-26).

</domain>

<decisions>
## Implementation Decisions

### Tile layers & appearance
- **Satellite layer:** Esri World Imagery with Esri label overlay (hybrid satellite + road/place names)
- **Street layer:** OpenStreetMap standard tiles
- **Default layer:** Satellite (Esri) on first load
- **Tile loading:** Grey placeholder tiles while imagery loads
- **Tile brightness:** Use provider tiles as-is, no dimming or filters
- **Max zoom:** Provider default (no artificial cap)
- **Map sizing:** Edge-to-edge, fills 100% of right panel — no padding or borders
- **Scale bar:** Metric (km/m), displayed on map
- **Coordinates display:** Decimal degrees (e.g., -6.2088, 106.8456) shown on hover, positioned bottom-left corner
- **Minimap:** Include capability (off by default), togglable — settings UI deferred to Phase 26
- **No center indicator:** No crosshair or center dot when panning
- **Tile caching:** Browser caches recently viewed tiles for brief offline moments

### Layer switching UI
- **Style:** Leaflet default layers control (icon expands on hover)
- **Position:** Top-right corner (Leaflet default)
- **Content:** Base layers only (Satellite, Street) — no overlay layers for now
- **Transition:** Instant layer swap, no crossfade animation

### Initial map view
- **Center priority:** (1) Browser geolocation, (2) Sydney, Australia fallback (-33.8688, 151.2093)
- **Geolocation UX:** Silent request — no loading indicator, no extra UI
- **Geolocation timeout:** Fixed sensible value (e.g., 5 seconds) — not configurable
- **If timeout/denied:** Fall back to Sydney immediately
- **Default zoom:** City level (~12)
- **Empty state:** Just the map — no "waiting for workers" message
- **No "locate me" button** — map is for tracking workers, not the dispatch operator

### View persistence (localStorage)
- **Remember position:** Save center + zoom to localStorage, restore on reload
- **Remember layer:** Save last-used layer choice (satellite/street), restore on reload

### Map controls placement
- **Zoom buttons (+/-):** Bottom-right corner
- **Scroll-wheel zoom:** Enabled (always, no Ctrl required)
- **Double-click zoom:** Enabled (zooms in one level)
- **Keyboard navigation:** Disabled — no arrow key panning or +/- zoom to avoid conflicts with app shortcuts
- **Attribution:** Minimal/collapsed — collapsed by default, expands on hover

### Claude's Discretion
- Exact geolocation timeout duration
- Scale bar position on map
- Leaflet plugin choices for minimap and coordinates display
- React component structure and cleanup pattern
- localStorage key naming

</decisions>

<specifics>
## Specific Ideas

- Satellite view should have labels overlaid (hybrid), not raw satellite — dispatch operators need to read road names
- Custom per-event center is a future feature (not this phase) — for now browser geolocation → Sydney fallback
- Minimap as a toggleable option aligns with Phase 26 settings persistence — just include the capability now

</specifics>

<deferred>
## Deferred Ideas

- Per-event map center configuration (admin sets event location) — future feature
- Minimap toggle in settings UI — Phase 26 (SETTINGS-01)
- Overlay layers in layer switcher (heat maps, clusters) — Phase 25+
- Configurable geolocation timeout — unnecessary complexity for now

</deferred>

---

*Phase: 23-map-foundation*
*Context gathered: 2026-02-17*
