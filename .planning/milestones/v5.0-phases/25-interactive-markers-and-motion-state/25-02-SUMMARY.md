---
phase: 25-interactive-markers-and-motion-state
plan: 02
subsystem: web-ui/dispatch-map
tags: [location-enrichment, popup-content, tooltip, telemetry-display]

dependency_graph:
  requires:
    - "24-02: LocationContext with Map-based storage"
    - "23-02: MapView Leaflet integration"
    - "DispatchConsole overview data structure"
  provides:
    - "Enriched location positions with teamName/channelNames"
    - "Popup content generators for marker clicks"
    - "Tooltip content generators for marker hovers"
    - "Telemetry formatting utilities (connection quality, latency, relative time)"
  affects:
    - "25-03: Will consume popup/tooltip utilities for interactive markers"

tech_stack:
  added: []
  patterns:
    - "useMemo for overview-derived user lookup Map"
    - "Position enrichment at all update entry points (single/bulk/merge)"
    - "Pure utility functions for HTML content generation"
    - "Heuristic-based connection quality from networkType"
    - "Speed unit conversion (m/s to km/h) at display time"

key_files:
  created: []
  modified:
    - path: "web-ui/src/context/LocationContext.jsx"
      changes: "Added overview prop, userLookup memoization, position enrichment, 5 utility exports"
    - path: "web-ui/src/pages/DispatchConsole.jsx"
      changes: "Pass overview={overview} to LocationProvider"

decisions:
  - summary: "Member userId extraction handles both string IDs and object with .userId/.id"
    rationale: "Overview endpoint returns user objects, not plain strings — defensive coding"
    commit: "329f70f"
  - summary: "Speed displayed in km/h (server sends m/s) via 3.6 multiplier"
    rationale: "Phase 25 spec requires raw km/h value — matches Android UI convention"
    commit: "d2f7896"
  - summary: "Battery as text percentage only (no color coding or bars)"
    rationale: "Phase 25 locked decision — simple text display in popup"
    commit: "d2f7896"
  - summary: "PTT button disabled placeholder in popup"
    rationale: "Phase 25 spec — non-functional button for future direct-to-user communication"
    commit: "d2f7896"
  - summary: "Connection quality heuristic: wifi > cellular, power save mode degrades"
    rationale: "No latency telemetry available — networkType is best proxy for quality"
    commit: "d2f7896"

metrics:
  duration: 178
  tasks_completed: 2
  files_modified: 2
  commits: 2
  completed_at: "2026-02-17"
---

# Phase 25 Plan 02: Location Data Enrichment and Popup Content Summary

**One-liner:** Team/channel lookup via overview data with grouped popup sections (Identity, Status, Activity) and heuristic-based connection quality derivation.

## Overview

This plan enriches raw location positions with team and channel assignments from the overview endpoint, enabling informative marker popups. It provides pure utility functions for generating tooltip (hover) and popup (click) HTML content with grouped sections showing identity, telemetry status, and activity metrics.

## Implementation Details

### Task 1: Overview Data Integration and Position Enrichment

**Problem:** LOCATION_BROADCAST sends userId/userName but no team or channel assignments. Dispatch operators need to see which team and channels each user belongs to when inspecting markers.

**Solution:** Pass overview data to LocationProvider, build a memoized userLookup Map (userId -> {teamName, channelNames[]}), and enrich positions at all update entry points:

1. **DispatchConsole changes:**
   - Pass `overview={overview}` prop to LocationProvider

2. **LocationContext changes:**
   - Accept `overview` prop in LocationProvider signature
   - Build `userLookup` Map via useMemo (depends on overview)
   - Team name lookup: `overview.teams` -> Map<teamId, teamName>
   - User assignment: iterate `overview.channels`, extract members, build userId -> {teamName, channelNames[]}
   - Handle member format variations (string userId vs object with .userId/.id)
   - Enrich positions in `updateLocation`, `setAllLocations`, `mergeLocations`

**Commit:** 329f70f

**Files:** web-ui/src/context/LocationContext.jsx, web-ui/src/pages/DispatchConsole.jsx

### Task 2: Popup and Tooltip Content Generation Utilities

**Problem:** Plan 03 (interactive markers) needs to display rich telemetry in popups and quick glances in tooltips. Content must follow locked UI spec (grouped sections, no color coding, disabled PTT button).

**Solution:** Export five pure utility functions from LocationContext:

1. **deriveConnectionQuality(position):**
   - Heuristic: wifi > cellular
   - Power save mode degrades quality by one tier
   - Returns: 'Good', 'Fair', 'Poor', 'Unknown'

2. **formatRelativeTime(timestamp):**
   - Human-readable format: "just now", "15s ago", "3 min ago", "2h 45m ago"
   - Handles both ISO strings and epoch milliseconds

3. **deriveLatency(position):**
   - Timestamp freshness proxy (no actual latency telemetry available)
   - Returns: '< 5s', '< 30s', '< 1m', '> 1m', 'N/A'

4. **generateTooltipContent(position):**
   - Hover glance: Name (bold), Team, Channels (comma-separated)
   - Plain HTML for Leaflet bindTooltip

5. **generatePopupContent(position):**
   - Three grouped sections with dividers:
     - **Identity:** Name, Team, Channels
     - **Status:** Battery (text %), Connection quality, Latency
     - **Activity:** Motion state, Speed (km/h), Updated (relative time)
   - Disabled PTT placeholder button
   - CSS classes: `.marker-popup`, `.marker-popup__section`, `.marker-popup__row`, `.marker-popup__label`

**Commit:** d2f7896

**Files:** web-ui/src/context/LocationContext.jsx

## Key Decisions

1. **Defensive member extraction:** Handle both `member` as string and as object with `.userId` or `.id` fields. The overview endpoint returns user objects (not plain strings), so this prevents runtime crashes.

2. **Speed unit conversion:** Server sends speed in m/s (from Android), display as km/h (multiply by 3.6) per phase 25 spec requirement for "raw km/h value."

3. **Connection quality heuristic:** Use networkType as proxy (wifi = better than cellular). Power save mode degrades by one tier. No actual ping/latency data available from Android location updates.

4. **No color coding:** Battery and connection quality are plain text only. Phase 25 locked decision — deferred to future phase if needed.

5. **PTT button placeholder:** Non-functional button in popup with "Coming soon" title. Reserved for future direct-to-user PTT feature.

## Deviations from Plan

None — plan executed exactly as written. All utility functions match the spec, enrichment logic handles edge cases defensively.

## Verification

1. Build: `npx vite build --mode development` passes with zero errors
2. Grep verification:
   - LocationContext.jsx contains: `teamName`, `channelNames`, `userLookup`, `overview`
   - DispatchConsole.jsx passes: `overview={overview}`
   - Utility exports: `generatePopupContent`, `generateTooltipContent`, `deriveConnectionQuality`, `formatRelativeTime`
   - Popup structure: `.marker-popup__section` class present

## Testing Notes

**Manual verification (Plan 03 dependency):**
- Plan 03 will consume these utilities when rendering markers
- Popup display testing requires physical map with markers
- Tooltip/popup HTML structure verified via grep (CSS classes present)

**Edge cases handled:**
- Missing overview data: userLookup returns empty Map, enrichment uses "Unknown" fallbacks
- Missing telemetry fields: formatters handle null/undefined gracefully (display "N/A")
- Speed/battery null checks: prevent NaN display
- Timestamp parsing: handles both ISO strings and epoch milliseconds

## Next Steps

**Plan 03 (interactive markers):**
- Import popup/tooltip utilities from LocationContext
- Bind generatePopupContent/generateTooltipContent to Leaflet markers
- Implement zoom-dependent label visibility
- Add CSS for `.marker-popup` and related classes

## Self-Check

Verified created files and commits:

```bash
# Check modified files exist
ls -lh web-ui/src/context/LocationContext.jsx
ls -lh web-ui/src/pages/DispatchConsole.jsx

# Check commits exist
git log --oneline | grep 329f70f
git log --oneline | grep d2f7896
```

**Result:** PASSED

Both files modified successfully:
- web-ui/src/context/LocationContext.jsx: +123 lines (utility functions)
- web-ui/src/pages/DispatchConsole.jsx: +1 line (overview prop)

Both commits present in git log:
- 329f70f: feat(25-02): enrich location data with team/channel info from overview
- d2f7896: feat(25-02): add popup and tooltip content generation utilities

All grep verifications passed (teamName, channelNames, popup utilities exported, DispatchConsole passes overview).
