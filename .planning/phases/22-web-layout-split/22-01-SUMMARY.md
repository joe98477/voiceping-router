---
phase: 22-web-layout-split
plan: 01
subsystem: web-ui
tags: [layout, css-grid, dispatch-console, ui-foundation]
dependency_graph:
  requires: []
  provides: [split-panel-layout, map-placeholder-4-3, channels-panel-500px]
  affects: [web-ui/src/pages/DispatchConsole.jsx, web-ui/src/styles.css, web-ui/src/theme/connectvoice.css]
tech_stack:
  added: [css-grid-split-layout, aspect-ratio-4-3]
  patterns: [fixed-width-left-panel, flexible-right-panel, bottom-aligned-map-container]
key_files:
  created: []
  modified:
    - web-ui/src/pages/DispatchConsole.jsx
    - web-ui/src/styles.css
    - web-ui/src/theme/connectvoice.css
decisions:
  - No visible divider between panels (gap: 0)
  - Sharp edges on map-container (no border-radius) for edge-to-edge Leaflet rendering
  - Stats bar items spread evenly with space-around (not gap)
  - Channel cards sized for 2-column fit in 500px panel (180px minmax)
  - Map placeholder invisible (no background) for future controls space
metrics:
  duration: 146 seconds
  tasks_completed: 2
  files_modified: 3
  commits: 2
completed: 2026-02-17
---

# Phase 22 Plan 01: CSS Grid Split-Panel Layout Summary

CSS Grid split-panel layout with fixed-width channels panel on left and flexible map panel on right, establishing foundation for Phase 23 Leaflet integration.

## Tasks Completed

### Task 1: Restructure DispatchConsole.jsx with split-panel layout
- **Commit:** a84abf3
- **Changes:**
  - Wrapped ChannelProvider in `channels-panel` div inside `dispatch-console__main-content` grid container
  - Added `map-panel` div with `map-placeholder` (invisible, 80px min-height for future controls) and `map-container` (empty dark placeholder)
  - Preserved all existing ChannelProvider/DispatchGridWithContext logic and props
  - All channel monitoring functionality works unchanged
- **Verification:** Vite build passes with zero errors

### Task 2: Add CSS Grid layout, map panel, and card sizing rules
- **Commit:** ed0c883
- **Changes to styles.css:**
  - Added `.dispatch-console__main-content` with `grid-template-columns: 500px 1fr`
  - Added `.channels-panel` with `overflow-y: auto` for independent scrolling
  - Added `.map-panel` with `grid-template-rows: 1fr auto` (placeholder flexes, map at bottom)
  - Added `.map-placeholder` with `min-height: 80px` (no background, invisible)
  - Added `.map-container` with `aspect-ratio: 4 / 3`, `width: 100%`, `transition: opacity 300ms ease`
  - Modified `.dispatch-stats` from `gap: 16px` to `justify-content: space-around`
  - Modified `.channel-grid__cards` from `minmax(220px, 1fr)` to `minmax(180px, 1fr)`
- **Changes to connectvoice.css:**
  - Added `.map-panel` background: `rgba(16, 24, 40, 0.4)` (subtle dark shade)
  - Added `.map-container` background: `rgba(16, 24, 40, 0.85)` (darker shade for placeholder)
- **Verification:** Vite build passes, all CSS rules present and correct

## Deviations from Plan

None - plan executed exactly as written.

## Key Technical Decisions

1. **No visible divider between panels** - Used `gap: 0` on main-content grid per user decision; map panel background shade provides subtle visual boundary
2. **Sharp edges on map-container** - No border-radius to allow edge-to-edge Leaflet rendering in Phase 23
3. **Stats bar spacing** - Changed from gap-based spacing to `justify-content: space-around` to spread items evenly across full page width
4. **Channel card sizing** - Reduced minmax from 220px to 180px to ensure 2-column fit in the narrower 500px channels panel
5. **Invisible map placeholder** - No background on map-placeholder div (reserves space for future controls without visual clutter)
6. **Opacity transition on map-container** - Ready for Phase 23 Leaflet fade-in animation (300ms ease)

## Output

**Layout structure:**
```
dispatch-console (grid: auto auto 1fr)
  header (spans full width)
  dispatch-stats (spans full width, space-around)
  dispatch-console__main-content (grid: 500px 1fr)
    channels-panel (overflow-y: auto)
      ChannelProvider > DispatchGridWithContext
    map-panel (grid: 1fr auto)
      map-placeholder (min-height 80px, invisible)
      map-container (4:3 aspect ratio, dark placeholder)
```

**Visual result:**
- Channels panel: Fixed 500px width on left, independently scrollable
- Map panel: Flexible width on right, fills remaining space
- Map container: 4:3 aspect ratio, bottom-aligned, dark placeholder
- Channel cards: 2-column layout fits in 500px panel
- Stats bar: Items spread evenly across full page width
- All existing channel monitoring functionality preserved

**Ready for Phase 23:**
- Map container has correct 4:3 aspect ratio for Leaflet tiles
- Opacity transition prepared for fade-in animation
- Sharp edges allow edge-to-edge map rendering
- Map placeholder space reserved for future controls (zoom, layer selector)

## Verification Results

- [x] Vite build passes with zero errors
- [x] DispatchConsole.jsx contains dispatch-console__main-content wrapper with channels-panel and map-panel children
- [x] styles.css has grid-template-columns: 500px 1fr
- [x] styles.css has aspect-ratio: 4 / 3 on map-container
- [x] styles.css has minmax(180px, 1fr) on channel-grid__cards (changed from 220px)
- [x] styles.css has justify-content: space-around on dispatch-stats
- [x] connectvoice.css has map-panel and map-container background shades
- [x] No existing ChannelProvider/ChannelGrid logic changed

## Self-Check: PASSED

**Created files verified:**
- SUMMARY exists: /home/earthworm/Github-repos/voiceping-router/.planning/phases/22-web-layout-split/22-01-SUMMARY.md

**Modified files verified:**
- web-ui/src/pages/DispatchConsole.jsx: Split-panel JSX structure present
- web-ui/src/styles.css: All CSS Grid layout rules added, existing rules modified
- web-ui/src/theme/connectvoice.css: Map panel background shades added

**Commits verified:**
- a84abf3: feat(22-01): restructure DispatchConsole with split-panel layout
- ed0c883: feat(22-01): add CSS Grid split layout and map panel styling

## Next Steps

Phase 22 Plan 02 will add:
- Collapse/expand functionality for channels panel
- Responsive layout for mobile (single-panel with tab switcher)
- State persistence for panel collapse preference

Phase 23 will mount Leaflet into the prepared map-container and integrate location data.
