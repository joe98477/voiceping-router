# Phase 24: Location State and Real-Time Markers - Context

**Gathered:** 2026-02-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Connect WebSocket location broadcasts to map markers showing real-time user positions. This phase delivers: a separate LocationContext (decoupled from ChannelContext), initial location query on map visibility, real-time marker updates from LOCATION_BROADCAST, and styled map markers with username labels. Status popups, motion indicators, clustering, and zoom-to-fit are separate phases (25, 26).

</domain>

<decisions>
## Implementation Decisions

### Marker Appearance
- Person/avatar pin style (generic person silhouette inside pin shape)
- Orange (#FF9800) pin color — high contrast on satellite imagery
- Pin anchor at bottom-center tip (traditional map pin)
- Medium size (28-32px)
- Subtle drop shadow to lift pin off the satellite map
- Username label positioned ABOVE the marker
- Full display name shown (not abbreviated or first-name-only)
- Label text in orange (same color as marker pin)
- Light rounded translucent pill background behind label text
- Label font size: 10px
- Uniform color for all markers (no channel-based color coding)

### Initial Load & Empty States
- No empty state message — just show the bare satellite map when no locations exist
- LOCATION_QUERY fires only when the map panel becomes visible (not on dispatch console mount)
- LocationContext always listens to LOCATION_BROADCAST even when map is hidden — keeps state fresh
- When map becomes visible after being hidden, render accumulated LocationContext state (no re-query)
- All markers appear at once from LOCATION_QUERY (no fade-in or stagger)
- No loading spinner — markers appear when data arrives
- No marker count indicator on the map
- Map stays at saved/default position on load (no auto-zoom to fit markers)
- Zoom-to-fit is a manual button — deferred to Phase 26 (CTRL-03)
- On reconnect: preserve existing markers and merge with fresh data (don't clear)

### Location Query Scope
- LOCATION_QUERY returns all users with location data within the last 1 hour (not just connected users)
- 1-hour window is fixed (not user-configurable)
- Query protocol design: Claude's discretion (new WS message type or reuse existing pattern)

### Marker Lifecycle
- On user disconnect: keep marker at last known position, mark as "disconnected" internally
- Visual fading for disconnected/stale markers — Phase 25 handles the visual treatment (MAP-05)
- Markers auto-removed after 1 hour with no location update (matches query window)
- Marker removal is instant (no fade-out animation)
- Users with no location data (e.g., denied permission) don't appear on the map
- On event switch: LocationContext clears completely (fresh slate for new event)
- On reconnect after previous disconnect: marker just reappears at full opacity (no fade-in)

### Update Visual Feedback
- Smooth slide animation (200ms) when marker position updates from LOCATION_BROADCAST
- Username label slides with the marker (everything moves together)
- No pulse or glow on position update — the slide movement is the indicator
- Batch updates after reconnect: stagger marker animations (not all simultaneous)
- Fixed z-index order (no z-reordering based on recency)
- New markers just appear instantly (no entrance animation)

### Claude's Discretion
- LOCATION_QUERY protocol implementation (new message type vs existing pattern)
- LocationContext internal data structure
- Stale marker cleanup timer implementation
- Stagger timing for batch updates
- Marker slide animation technique (CSS transitions vs requestAnimationFrame)

</decisions>

<specifics>
## Specific Ideas

- User wants the zoom-to-fit as a BUTTON (not automatic) — capture this preference for Phase 26
- Orange markers on satellite imagery — chosen for high visibility against green vegetation and grey buildings
- Label pill should be translucent (not opaque white) with orange text matching the pin color
- 1-hour marker timeout matches the 1-hour LOCATION_QUERY window — consistent mental model

</specifics>

<deferred>
## Deferred Ideas

- Zoom-to-fit button (CTRL-03) — Phase 26, user prefers manual button over auto-zoom
- Visual fading for stale/disconnected markers (MAP-05) — Phase 25
- Motion state indicators on markers (MAP-06) — Phase 25
- Marker clustering for density (MAP-07) — Phase 25
- Configurable time window for location query — future consideration

</deferred>

---

*Phase: 24-location-state-and-real-time-markers*
*Context gathered: 2026-02-17*
