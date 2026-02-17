# Phase 22: Web Layout Split - Context

**Gathered:** 2026-02-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Create split-panel dispatch console layout with channels on left and map panel on right using CSS Grid. Layout optimized for desktop (1200px+), with responsive tab-switching below breakpoint. Existing channel monitoring functionality works unchanged. Map panel div exists and is sized correctly for Leaflet integration in Phase 23.

</domain>

<decisions>
## Implementation Decisions

### Channel Panel Sizing
- Fixed width ~500px — comfortable 2-column fit for channel cards
- Shrink channel card min-width to ~180px (from 220px) to fit 2 columns in the narrower panel
- Channels panel scrolls independently — map stays fixed at full height
- Header and stats bar span full width across both panels at the top; the split starts below them
- Stats bar items spread across full page width (not left-aligned)
- Team section headers (chevron + name + mute button) stay as-is — they fit at 500px
- No visible divider between channels and map panels — background difference is sufficient

### Map Panel Layout
- Map aspect ratio is 4:3 (landscape) — not full-height
- Map aligned to bottom of the right panel
- Empty placeholder div above the map for future controls (defined min-height, no background — invisible)
- Map area has sharp edges (no rounded corners) — ready for Leaflet edge-to-edge rendering

### Map Panel Placeholder (Pre-Phase 23)
- Dark empty area — no text, no icons, no visual content
- Slightly different background shade (subtle rgba surface) to distinguish the map panel boundary
- CSS transition-ready for fade-in when Leaflet mounts in Phase 23

### Panel Collapse/Expand
- Channels panel is collapsible via chevron button on the divider edge
- Fixed width + collapse only — no draggable resizer
- Collapse animation: smooth slide (~200-300ms)
- Collapsed state: ~40px narrow strip with team initials/icons and activity dots
- Audio monitoring continues when panel is collapsed — collapse is purely visual
- Collapse/expand state: Claude's discretion on localStorage persistence

### Below-Breakpoint Behavior (< 1200px)
- Switch from split-panel to tab mode at 1200px breakpoint
- Fixed bottom tab bar with Channels and Map tab icons (mobile-app feel)
- Default tab: Channels (primary dispatch tool)
- Audio monitoring always active regardless of which tab is shown

### Claude's Discretion
- Exact pixel width tuning around ~500px
- Collapse chevron button styling and positioning
- Collapsed strip team icon design
- Smooth slide animation timing and easing
- Stats bar item spacing in full-width mode
- Bottom tab bar icon and styling choices
- Placeholder div min-height for above-map area
- Map panel exact background shade

</decisions>

<specifics>
## Specific Ideas

- Map should be 4:3 ratio bottom-aligned, with reserved space above for future quick-access controls
- Collapsed strip should show team initials/icons with colored activity dots — enough for quick-glance monitoring without expanding
- The layout is a working dispatch tool — audio never pauses due to panel state changes

</specifics>

<deferred>
## Deferred Ideas

- Quick-access controls above the map panel — future phase (space reserved with placeholder div)
- Collapse state persistence in localStorage — may implement in Phase 26 (Map Controls and Polish) with other settings persistence

</deferred>

---

*Phase: 22-web-layout-split*
*Context gathered: 2026-02-17*
