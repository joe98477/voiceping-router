# Phase 25: Interactive Markers and Motion State - Context

**Gathered:** 2026-02-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Add status popups, motion indicators, staleness treatment, and clustering to map markers for production-scale dispatch monitoring (200+ field workers). This phase enriches the existing markers from Phase 24 — it does NOT add new protocol fields, map controls, search, or settings persistence (those are Phase 26).

</domain>

<decisions>
## Implementation Decisions

### Status Popup — Trigger & Behavior
- **Two-tier interaction:** Hover shows brief summary, click opens full detail card
- **Hover summary:** Name, Team, Channel(s) — dispatch-oriented identity glance
- **Single popup only:** Clicking a new marker closes the previous popup
- **Click-away closes:** Clicking anywhere on map dismisses the open popup
- **Position:** Above the marker (standard Leaflet popup position)
- **Live updates:** Popup content refreshes in real-time while open as new data arrives

### Status Popup — Full Card Content
- **Grouped sections layout** with subtle dividers between groups:
  - **Identity:** Name, Team, Channel(s)
  - **Status:** Battery % (text only, e.g., "87%"), Connection quality (text label: "Good"/"Fair"/"Poor"), Latency
  - **Activity:** Motion State, Speed (raw km/h value), "Updated X min/sec ago" (relative timestamp)
- **PTT placeholder button:** Non-functional button on the card for future direct-to-user communication
- **No color-coded borders/accents:** Status conveyed through text fields only

### Motion State Visuals
- **Icon variations** for STILL/WALKING/DRIVING — different recognizable pictograms (standing person, walking person, car silhouette)
- **Replace marker icon entirely:** The motion state IS the marker, not a badge overlay
- **Same color (orange)** for all motion states — icon shape alone conveys state
- **No heading/direction arrow** on markers
- **No animation** on state transitions — instant icon swap
- **Zoom-dependent name labels:** Show username labels when zoomed in close, hide when zoomed out to reduce clutter

### Staleness Treatment
- **5-minute threshold** for stale status (no location update in 5+ minutes)
- **Grayed out** appearance for stale markers (lose color, become grayscale)
- **Remove after 1 hour** — matches existing server 1-hour query window
- **Instant recovery** — marker immediately returns to normal colored state when fresh update arrives, no transition effect

### Clustering
- **Circle with count** — orange circle showing number of active users in cluster
- **Uniform orange color** — all clusters same color regardless of size
- **Fixed circle size** — diameter does not vary with member count
- **Active users only** in cluster count — stale users excluded from the number
- **Click to zoom in** — clicking a cluster zooms to show individual markers (no spiderfy)
- **Disable clustering at high zoom** — always show individual markers when zoomed in close
- **Animated transitions** — markers smoothly fly out from cluster when zooming in
- **Hover name list** — tooltip shows usernames in cluster (up to ~10, then "...and N more")
  - Active names normal styling, stale names grayed/dimmed in the list

### Claude's Discretion
- Exact zoom threshold for disabling clustering
- Zoom threshold for showing/hiding username labels
- Cluster hover tooltip styling and truncation
- Technical choice of clustering library
- Popup card CSS styling and spacing
- How to derive "connection quality" and "latency" from available data
- Pictogram icon design (SVG/CSS) for motion states

</decisions>

<specifics>
## Specific Ideas

- Event → Team → Channel hierarchy exists in the data model. Users belong to teams; channels are grouped within teams. Popup and hover should reflect this existing structure.
- PTT button on popup is a **placeholder only** — non-functional in this phase, wired up in a future feature for direct dispatch-to-user communication.
- Speed displayed as raw km/h (e.g., "12 km/h"), not qualitative labels.

</specifics>

<deferred>
## Deferred Ideas

- **Direct-to-user PTT from popup** — future feature, placeholder button added in this phase
- **Configurable popup fields** — Phase 26 (SETTINGS-01)
- **User search on map** — Phase 26 (CTRL-04)

</deferred>

---

*Phase: 25-interactive-markers-and-motion-state*
*Context gathered: 2026-02-17*
