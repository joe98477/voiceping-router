# Phase 26: Map Controls and Polish - Context

**Gathered:** 2026-02-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Add auto-fit bounds, user search, configurable popup settings, and UX polish to the dispatch map. This is the final phase of v5.0 Dispatch Map View. Does NOT add new data fields, new marker types, or new map layers — focuses on controls and configuration for the existing map.

</domain>

<decisions>
## Implementation Decisions

### Auto-fit behavior
- Auto-fit on initial load to show all markers from currently monitored channels
- "Fit All" button in a floating toolbar — also channel-filtered (matches initial behavior)
- Overview zoom cap when only one marker exists (don't zoom in too close)
- Smooth fly-to animation (not instant snap)
- Padding around markers (e.g., 50px) so markers aren't clipped at map edges
- If no markers to fit, show a brief toast/notification: "No locations to show"

### Floating toolbar
- Position: top center of the map
- Houses: Fit All button, search bar, and settings icon
- Semi-transparent glassmorphic style with backdrop blur — lets satellite map show through
- Always visible (no auto-hide)
- No layer toggle needed — Phase 23 already has Leaflet's built-in layer control

### User search UX
- Search input always expanded/visible in the toolbar (not icon-that-expands)
- Autocomplete dropdown with live filtering as user types
- Case-insensitive partial matching (matches anywhere in the name)
- Results ordered by closest match relevance
- Searches both user names and channel names
- Separate sections in dropdown: "Users" section and "Channels" section with headers
- Channel results expand to reveal individual members to select
- User results show name + online/offline status icon (online = location update within 5 minutes, consistent with staleness threshold)
- Users without location data shown in dropdown but grayed out with "No location" — not clickable
- On user selection: fly-to marker only (no auto-open popup)
- Search term stays after selecting a result (dropdown stays available for picking another)
- Escape closes dropdown only (text stays)
- Full keyboard navigation: arrow keys + Enter to select
- Max 8 visible items before scrolling
- Search always starts empty on page load (not persisted)

### Popup field settings
- Settings icon in toolbar opens a slide-out panel from the right edge of the map
- Toggle switches (modern on/off) for each popup field
- User name always shown (not toggleable); all other fields toggleable: location, motion, channel, PTT status, connection quality, battery
- All fields default to ON
- Controls popup content only (tooltip content stays unchanged)
- Live preview — changes apply instantly as toggles are flipped, no save button
- "Reset to defaults" button included
- Click-away dismisses the panel
- Settings panel always starts closed on page load

### State persistence
- Popup field preferences stored in global localStorage key: `cv.dispatch.popup.settings`
- Global scope (not event-scoped) — dispatcher preferences are personal, not per-event
- Phase 23 event-scoped zoom/center persistence left unchanged
- Silent fallback to all-fields-on defaults if localStorage corrupted or missing
- No version/migration handling — new fields get defaults, removed fields ignored
- Panel collapse state persistence still deferred (not in this phase)

### Claude's Discretion
- Toolbar icon style (icons-only vs icons+labels) — pick what fits
- Exact toolbar dimensions and spacing
- Toast notification style and duration for "No locations to show"
- Exact autocomplete debounce timing
- Search result highlighting of matched text
- Slide-out panel width and animation timing
- Settings panel visual layout and spacing

</decisions>

<specifics>
## Specific Ideas

- Toolbar at top center doubles as home for both search and action buttons — clean, unified control bar
- Channel search expanding to show members resembles a tree/accordion pattern
- Online/offline in search dropdown determined by 5-minute location staleness — reuses existing threshold, no new concept
- Glassmorphic toolbar style to blend with satellite imagery underneath

</specifics>

<deferred>
## Deferred Ideas

- Panel collapse state persistence — deferred from Phase 22, still not in scope
- Search term persistence across reloads — decided against for clean UX

</deferred>

---

*Phase: 26-map-controls-and-polish*
*Context gathered: 2026-02-17*
