---
phase: 25-interactive-markers-and-motion-state
plan: 01
subsystem: web-ui
tags: [markers, motion-state, staleness, visual-treatment, zoom-labels]

dependency_graph:
  requires:
    - phase: 24
      plan: 02
      artifact: "MapView marker rendering with LocationContext"
  provides:
    - "Motion-state-aware marker icons (STILL/WALKING/DRIVING)"
    - "Staleness visual treatment (5+ min grayscale)"
    - "Zoom-dependent username label visibility"
  affects:
    - component: MapView
      change: "Marker icon generation with motion state and staleness"
    - component: styles.css
      change: "Motion state classes and staleness filters"

tech_stack:
  added: []
  patterns:
    - "SVG pictograms in DivIcon HTML"
    - "CSS class-based state visualization"
    - "Zoom event listener for UI element visibility"

key_files:
  created: []
  modified:
    - path: "web-ui/src/styles.css"
      lines_added: 42
      purpose: "Motion state CSS classes, staleness filters, zoom-label visibility"
    - path: "web-ui/src/components/MapView.jsx"
      lines_added: 82
      lines_removed: 39
      purpose: "createMarkerIcon function, staleness computation, zoomend listener"

decisions:
  - id: "MOTION-ICON-01"
    summary: "Same orange color (#FF9800) for all motion states — icon shape alone conveys state"
    rationale: "Maintains visual consistency while allowing clear state differentiation"
  - id: "STALE-THRESHOLD-01"
    summary: "5-minute threshold for stale status (no location update in 5+ minutes)"
    rationale: "Balances responsiveness with avoiding false positives from brief network issues"
  - id: "STALE-RECOVERY-01"
    summary: "Instant recovery with no transition effect when fresh update arrives"
    rationale: "Immediate visual feedback that field worker is active again"
  - id: "ZOOM-LABEL-01"
    summary: "Zoom level >= 15 shows username labels, hidden below that threshold"
    rationale: "Reduces clutter at overview zoom levels while providing detail when zoomed in"

metrics:
  duration_seconds: 269
  tasks_completed: 2
  files_modified: 2
  commits: 2
  deviations: 0
  test_runs: 3
  completed_at: "2026-02-17T10:09:09Z"
---

# Phase 25 Plan 01: Motion State Icons and Staleness Visual Treatment Summary

**One-liner:** Motion-state-aware markers (STILL/WALKING/DRIVING pictograms) with 5-minute staleness grayscale treatment and zoom-controlled username labels.

## What Was Built

Added visual richness to map markers to convey field worker activity state at a glance:

1. **Three distinct motion state pictograms** — Standing person (STILL), walking person (WALKING), car silhouette (DRIVING)
2. **Staleness visual treatment** — Markers older than 5 minutes become grayscale with reduced opacity
3. **Zoom-dependent label visibility** — Username labels auto-hide when zoomed out (< 15), show when zoomed in close

All markers remain the same orange color (#FF9800) — only the icon shape conveys motion state per locked decision.

## Technical Approach

### CSS (styles.css)

Added new section after Phase 24 marker styles:

```css
/* Motion state variations */
.user-marker--still .user-marker__pin,
.user-marker--walking .user-marker__pin,
.user-marker--driving .user-marker__pin {
  background: #FF9800; /* Same color for all states */
}

/* Staleness treatment (5+ min) */
.user-marker--stale {
  filter: grayscale(100%) opacity(0.6);
  transition: none; /* Instant recovery */
}

/* Zoom-dependent label visibility */
.user-marker__label {
  display: none; /* Hidden by default */
}

.show-marker-labels .user-marker__label {
  display: block; /* Shown at zoom >= 15 */
}
```

### MapView.jsx Refactoring

**1. Created `createMarkerIcon` function:**

```javascript
function createMarkerIcon(position, isStale) {
  const motionState = (position.motionState || 'still').toLowerCase();
  const staleClass = isStale ? ' user-marker--stale' : '';
  const className = `user-marker user-marker--${motionState}${staleClass}`;

  const svgIcons = {
    still: `<svg>...</svg>`,      // Standing person
    walking: `<svg>...</svg>`,    // Walking person
    driving: `<svg>...</svg>`     // Car silhouette
  };

  return L.divIcon({ className, html: `...`, ... });
}
```

**2. Staleness computation:**

```javascript
const STALE_THRESHOLD = 5 * 60 * 1000; // 5 minutes
const isStale = (Date.now() - new Date(position.timestamp).getTime()) > STALE_THRESHOLD;
```

**3. Marker rendering updates:**
- For existing markers: only update icon if className changed (performance optimization)
- For new markers: create with motion-state-aware icon
- Store metadata: `marker.options.isStale`, `marker.options.userId`, `marker.options.title` for Plan 03 clustering

**4. Zoom listener for label visibility:**

```javascript
const handleZoomEnd = () => {
  const zoom = map.getZoom();
  const container = map.getContainer();
  if (zoom >= 15) {
    container.classList.add('show-marker-labels');
  } else {
    container.classList.remove('show-marker-labels');
  }
};
map.on('zoomend', handleZoomEnd);
handleZoomEnd(); // Trigger once on init
```

## Deviations from Plan

None — plan executed exactly as written.

## Key Decisions Made

| Decision | Choice | Impact |
|----------|--------|--------|
| Motion state color | Same orange for all states | Visual consistency, shape-based differentiation |
| Stale threshold | 5 minutes | Balances responsiveness with avoiding false positives |
| Stale recovery | Instant (no transition) | Immediate feedback when worker becomes active |
| Label zoom threshold | >= 15 | Reduces clutter at overview levels |
| Icon update trigger | className change only | Performance optimization (avoids unnecessary DOM updates) |

## Testing & Verification

**Build verification:**
```bash
cd web-ui && npx vite build --mode development
✓ built in 8.67s (zero errors)
```

**CSS verification:**
- ✓ `.user-marker--still`, `.user-marker--walking`, `.user-marker--driving` classes present
- ✓ `.user-marker--stale` with `grayscale(100%) opacity(0.6)` filter
- ✓ `.show-marker-labels .user-marker__label` with `display: block`

**MapView verification:**
- ✓ `createMarkerIcon` function with three SVG pictograms
- ✓ `STALE_THRESHOLD` constant (5 minutes)
- ✓ `handleZoomEnd` listener toggling `show-marker-labels` class
- ✓ Marker metadata storage (`isStale`, `userId`, `title`)

## Files Changed

**web-ui/src/styles.css** (+42 lines)
- Motion state classes (`.user-marker--still`, `.user-marker--walking`, `.user-marker--driving`)
- Staleness treatment (`.user-marker--stale`)
- Zoom-dependent label visibility (`.show-marker-labels`)
- Leaflet DivIcon overrides for motion state classes

**web-ui/src/components/MapView.jsx** (+82 lines, -39 lines)
- `createMarkerIcon` function with SVG pictograms
- `STALE_THRESHOLD` constant
- `handleZoomEnd` zoom listener
- Refactored marker rendering with staleness computation
- Marker metadata storage for clustering

## Commits

| Hash | Message |
|------|---------|
| f3f5cca | feat(25-01): add motion state icons, staleness, and zoom-label CSS |
| 496506d | feat(25-01): refactor MapView for motion-state-aware icons with staleness and zoom labels |

## Next Steps

**Plan 02:** Add status popups with hover summaries and click-to-open full detail cards (identity, status, activity sections).

**Plan 03:** Implement marker clustering with active user counts, click-to-zoom, and hover name lists.

## Self-Check

Verifying plan artifacts exist:

**Files modified:**
```bash
[ -f "web-ui/src/styles.css" ] && echo "FOUND: web-ui/src/styles.css" || echo "MISSING"
[ -f "web-ui/src/components/MapView.jsx" ] && echo "FOUND: web-ui/src/components/MapView.jsx" || echo "MISSING"
```

**Commits exist:**
```bash
git log --oneline --all | grep -q "f3f5cca" && echo "FOUND: f3f5cca" || echo "MISSING"
git log --oneline --all | grep -q "496506d" && echo "FOUND: 496506d" || echo "MISSING"
```

**Results:**

```
FOUND: web-ui/src/styles.css
FOUND: web-ui/src/components/MapView.jsx
FOUND: f3f5cca
FOUND: 496506d
```

## Self-Check: PASSED

All files and commits verified successfully.
