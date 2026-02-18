---
phase: 26-map-controls-and-polish
plan: 02
subsystem: web-ui-map-search
tags: [search, autocomplete, keyboard-navigation, fly-to, ui]
completed: 2026-02-17

dependency_graph:
  requires:
    - "26-01-PLAN (MapToolbar structure)"
    - "25-02-PLAN (Location enrichment with teamName/channelNames)"
    - "24-01-PLAN (LocationContext)"
  provides:
    - "MapSearch component with autocomplete and keyboard navigation"
    - "User/channel search with fly-to-marker behavior"
    - "Channel member expansion accordion"
  affects:
    - "MapToolbar (renders MapSearch)"
    - "MapView (handleSelectUser callback)"
    - "DispatchConsole (channels prop plumbing)"

tech_stack:
  added:
    - "ARIA combobox pattern for accessibility"
    - "Three-tier relevance sorting (exact > starts with > contains)"
    - "Keyboard navigation with ArrowUp/Down/Enter/Escape"
  patterns:
    - "useMemo for filtered results (performance)"
    - "useCallback for handleSelectUser (stable reference)"
    - "Flat results array for keyboard navigation index tracking"
    - "Click-away handler with refs"
    - "Scroll-into-view for focused items"

key_files:
  created:
    - path: web-ui/src/components/MapSearch.jsx
      lines: 368
      exports: [MapSearch (default)]
      purpose: "Autocomplete search with user/channel filtering, keyboard nav, channel expansion"
  modified:
    - path: web-ui/src/components/MapToolbar.jsx
      changes: "Import and render MapSearch, accept channels/onSelectUser props"
    - path: web-ui/src/components/MapView.jsx
      changes: "Accept channels prop, add handleSelectUser callback with flyTo(zoom=16)"
    - path: web-ui/src/pages/DispatchConsole.jsx
      changes: "Pass channels={overview?.channels || []} to MapView"
    - path: web-ui/src/styles.css
      changes: "Add MapSearch CSS (dropdown, results, headers, status indicators), remove .map-toolbar__search-slot"

decisions:
  - id: SEARCH-01
    summary: "Search input always visible (not icon-that-expands)"
    rationale: "Dispatchers need immediate access to search for 100+ field workers without extra click"
    impact: "Toolbar width grows but search is primary action"
  - id: SEARCH-02
    summary: "Search term stays after selection (dropdown stays open)"
    rationale: "Allows quick sequential searches without re-typing after each selection"
    impact: "User must explicitly clear search term or press Escape to close"
  - id: SEARCH-03
    summary: "No popup auto-open on fly-to (marker only)"
    rationale: "Dispatcher may want to see marker location without popup obscuring view"
    impact: "User must click marker to see full details"
  - id: SEARCH-04
    summary: "Zoom level 16 for individual user fly-to"
    rationale: "Detail level shows street context without excessive zoom (balance between overview and detail)"
    impact: "Consistent zoom behavior, not too close/far"
  - id: SEARCH-05
    summary: "Max 8 visible items before scrolling (320px dropdown height)"
    rationale: "Enough results visible without dropdown dominating screen (40px per item)"
    impact: "Long result lists require scrolling"
  - id: SEARCH-06
    summary: "No debounce on search input"
    rationale: "Direct filtering performs fine at dispatch scale (<500 users), debounce adds perceived lag"
    impact: "Instant results on every keystroke"
  - id: SEARCH-07
    summary: "Channel expansion toggle on click/Enter (not auto-expand on match)"
    rationale: "Gives user control, prevents overwhelming dropdown with all channel members at once"
    impact: "Two-step interaction to reach channel members"
  - id: SEARCH-08
    summary: "Users without location grayed out and not clickable"
    rationale: "Prevents fly-to on users without coordinates (frustrating no-op)"
    impact: "Visual feedback that user is offline or has no GPS data"

metrics:
  duration: 209
  tasks_completed: 2
  files_created: 1
  files_modified: 4
  commits: 2
  verifications_passed: 8
  build_status: pass

---

# Phase 26 Plan 02: Map Search Component Summary

**One-liner:** User/channel autocomplete search with keyboard navigation, channel member expansion, and fly-to-marker behavior for quick field worker location.

## Objective Achievement

Built MapSearch.jsx component with full autocomplete functionality integrated into the glassmorphic toolbar. Dispatchers can now instantly search for any user or channel across 100+ field workers, with smart filtering, keyboard navigation, and smooth fly-to behavior.

## Tasks Completed

### Task 1: Create MapSearch component with autocomplete, keyboard navigation, and channel expansion

**Commit:** c1c5c6b

**Implementation:**
- Autocomplete search component with searchTerm, isOpen, focusedIndex, expandedChannels state
- Case-insensitive partial matching against users (from locations Map) and channels (from overview)
- Three-tier relevance sorting: exact match > starts with > contains (alphabetical within tier)
- Flat results array for keyboard navigation (users + channels with expanded members)
- Keyboard handlers: ArrowDown/Up skip headers, Enter selects user/toggles channel, Escape closes dropdown
- Auto-open dropdown when search term exists and results found
- Click-away handler closes dropdown when clicking outside
- Scroll-into-view for focused items (smooth keyboard navigation experience)
- Match highlighting with `<strong>` tags on matched substring
- User items show online/offline dot (green/gray), "No location" label for users without coordinates
- Channel items show member count badge and expand/collapse chevron
- Expanded channels reveal individual member sub-items (indented, same format as users)
- Full ARIA attributes: role="combobox", aria-expanded, aria-haspopup, aria-autocomplete, aria-activedescendant

**Files:** web-ui/src/components/MapSearch.jsx (368 lines)

**Verification:**
- ARIA combobox present
- aria-activedescendant for keyboard navigation
- ArrowDown/Up/Enter/Escape handling
- expandedChannels Set for accordion state
- hasLocation check for disabled items
- toLowerCase().includes() for case-insensitive matching

### Task 2: Wire MapSearch into toolbar, add fly-to handler, and add search CSS

**Commit:** 44dc994

**Implementation:**

**MapToolbar.jsx:**
- Import MapSearch from './MapSearch.jsx'
- Accept channels and onSelectUser props
- Replace `.map-toolbar__search-slot` placeholder with `<MapSearch />` component
- Pass locations, channels, onSelectUser to MapSearch

**MapView.jsx:**
- Import useCallback from React
- Accept channels prop (overview.channels array)
- Create handleSelectUser(userId) callback:
  - Look up position from locations Map
  - If position has lat/lng: flyTo([lat, lng], 16, { animate: true, duration: 0.8 })
  - Zoom level 16 for individual user detail
  - No popup auto-open (per decision SEARCH-03)
- Wrap in useCallback with [locations] dependency
- Pass channels and onSelectUser to MapToolbar

**DispatchConsole.jsx:**
- Pass channels={overview?.channels || []} to MapView component
- Plumbs overview.channels through to MapSearch

**styles.css:**
- Remove .map-toolbar__search-slot placeholder (replaced by MapSearch component)
- Add complete MapSearch CSS section:
  - `.map-search` container: relative positioning, flex: 1, 200-300px width
  - `.map-search__input`: glassmorphic style matching toolbar, orange focus border
  - `.map-search__dropdown`: absolute positioning, 320px max-height, z-index 1001
  - `.map-search__header`: uppercase section headers (Users, Channels)
  - `.map-search__result`: base item style with hover, focused (orange tint), disabled (grayed)
  - `.map-search__result--channel`: bold channel items
  - `.map-search__result--member`: indented member sub-items
  - `.map-search__name`: flex: 1, text-overflow: ellipsis, highlight with `strong`
  - `.map-search__status`: green/gray dots for online/offline
  - `.map-search__no-location`: right-aligned gray label
  - `.map-search__member-count`: badge with member count
  - `.map-search__chevron`: rotate 90deg when expanded

**Files:** MapToolbar.jsx, MapView.jsx, DispatchConsole.jsx, styles.css

**Verification:**
- Vite build passes cleanly (10s, zero errors)
- MapSearch imported and rendered in MapToolbar
- channels prop accepted in MapView
- handleSelectUser uses flyTo with zoom level 16
- channels passed from DispatchConsole to MapView
- Dropdown CSS present (.map-search__dropdown)
- Result item CSS present (.map-search__result)
- Section header CSS present (.map-search__header)

## Deviations from Plan

None - plan executed exactly as written.

## Technical Highlights

**Performance optimizations:**
- useMemo for filtered results (only recalculates when searchTerm/locations/channels change)
- No debounce needed (direct filtering performs fine at dispatch scale)
- Flat results array for O(1) keyboard navigation index access

**Accessibility:**
- Full ARIA combobox pattern with aria-activedescendant
- Keyboard navigation skips non-focusable headers
- Focused items scroll into view automatically
- role="option" with aria-selected for each item

**User experience polish:**
- Search term persists after selection (allows sequential searches)
- Escape closes dropdown without clearing text
- Matched substring highlighted in bold
- Online/offline visual feedback (green/gray dot)
- Disabled state for users without location (prevents frustrating no-op clicks)
- Channel accordion toggle on Enter key (keyboard-accessible)

**Integration:**
- Channels prop plumbed from DispatchConsole → MapView → MapToolbar → MapSearch
- handleSelectUser callback chain: MapSearch → MapToolbar → MapView.flyTo
- Consistent with LocationContext pattern (Map<userId, position>)
- Reuses overview.channels structure from existing API

## Success Criteria Verification

- [x] Search input visible in glassmorphic toolbar
- [x] Typing filters users and channels case-insensitively
- [x] Dropdown shows Users and Channels sections with headers
- [x] Users display online/offline dot and "No location" label when appropriate
- [x] Selecting a user smoothly flies map to their marker without opening popup
- [x] Channel results expand to show individual members
- [x] Full keyboard navigation (arrow keys, Enter, Escape)
- [x] Search term stays after selection
- [x] Max 8 visible items before scrolling (320px / 40px per item)
- [x] Vite build passes with zero errors

## Known Issues / Future Work

None identified. Component fully functional and ready for Phase 26 Plan 03 (map settings modal).

## Self-Check: PASSED

**Created files exist:**
```
FOUND: web-ui/src/components/MapSearch.jsx
```

**Commits exist:**
```
FOUND: c1c5c6b (Task 1: MapSearch component)
FOUND: 44dc994 (Task 2: Integration and CSS)
```

**Build verification:**
```
Vite build passed in 10.00s with zero errors
```

All files created, all commits present, build passes cleanly.
