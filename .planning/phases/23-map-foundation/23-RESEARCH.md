# Phase 23: Map Foundation - Research

**Researched:** 2026-02-17
**Domain:** Leaflet map library integration with React
**Confidence:** HIGH

## Summary

Phase 23 integrates the Leaflet JavaScript mapping library into the dispatch console's right panel, providing an interactive satellite map with layer switching and proper React lifecycle management. The research reveals that vanilla Leaflet with React (using useRef + useEffect) is the recommended approach over react-leaflet for this use case, as it provides better control over cleanup and avoids React 18 Strict Mode double-mount issues.

The standard stack is Leaflet 1.9.4 (stable) with Esri World Imagery tiles (free, no API key required) and OpenStreetMap standard tiles. Critical success factors include: (1) proper map.remove() cleanup in useEffect return function, (2) geolocation API with timeout and fallback, (3) localStorage persistence for map state, and (4) edge-to-edge rendering in the existing map-container div.

**Primary recommendation:** Use vanilla Leaflet 1.9.4 with useRef pattern in a dedicated MapView.jsx component, initialize map in useEffect with cleanup via map.remove(), use Esri tile URLs directly (no esri-leaflet dependency), and implement localStorage save/restore for center, zoom, and active layer.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Tile layers & appearance:**
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

**Layer switching UI:**
- **Style:** Leaflet default layers control (icon expands on hover)
- **Position:** Top-right corner (Leaflet default)
- **Content:** Base layers only (Satellite, Street) — no overlay layers for now
- **Transition:** Instant layer swap, no crossfade animation

**Initial map view:**
- **Center priority:** (1) Browser geolocation, (2) Sydney, Australia fallback (-33.8688, 151.2093)
- **Geolocation UX:** Silent request — no loading indicator, no extra UI
- **Geolocation timeout:** Fixed sensible value (e.g., 5 seconds) — not configurable
- **If timeout/denied:** Fall back to Sydney immediately
- **Default zoom:** City level (~12)
- **Empty state:** Just the map — no "waiting for workers" message
- **No "locate me" button** — map is for tracking workers, not the dispatch operator

**View persistence (localStorage):**
- **Remember position:** Save center + zoom to localStorage, restore on reload
- **Remember layer:** Save last-used layer choice (satellite/street), restore on reload

**Map controls placement:**
- **Zoom buttons (+/-):** Bottom-right corner
- **Scroll-wheel zoom:** Enabled (always, no Ctrl required)
- **Double-click zoom:** Enabled (zooms in one level)
- **Keyboard navigation:** Disabled — no arrow key panning or +/- zoom to avoid conflicts with app shortcuts
- **Attribution:** Minimal/collapsed — collapsed by default, expands on hover

### Claude's Discretion

- Exact geolocation timeout duration (recommend: 5000ms based on research)
- Scale bar position on map (recommend: bottom-left per Leaflet defaults)
- Leaflet plugin choices for minimap and coordinates display
- React component structure and cleanup pattern
- localStorage key naming (recommend: `cv.dispatch.map.{eventId}`)

### Deferred Ideas (OUT OF SCOPE)

- Per-event map center configuration (admin sets event location) — future feature
- Minimap toggle in settings UI — Phase 26 (SETTINGS-01)
- Overlay layers in layer switcher (heat maps, clusters) — Phase 25+
- Configurable geolocation timeout — unnecessary complexity for now

</user_constraints>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| leaflet | 1.9.4 | Interactive map rendering and controls | Industry standard open-source mapping library, 42kB gzipped, active maintenance, extensive plugin ecosystem |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| leaflet-minimap | 3.6.1 | Minimap control in corner | Required per user constraints (capability included, off by default) |
| leaflet-mouse-position | 1.0.4 | Mouse coordinate display | Coordinates on hover per user constraints (decimal degrees, bottom-left) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Vanilla Leaflet | react-leaflet 5.0.0 | React-leaflet has React 18 Strict Mode issues ([Issue #963](https://github.com/PaulLeCam/react-leaflet/issues/963)) and adds abstraction layer complexity; vanilla Leaflet gives better cleanup control |
| Esri tile URLs | esri-leaflet plugin | Esri-leaflet requires API key for modern vector basemaps; direct tile URLs work without authentication for raster tiles |
| Custom coordinates | leaflet-coordinates plugin | Leaflet.Coordinates allows coordinate editing (not needed); leaflet-mouse-position is simpler for read-only display |

**Installation:**
```bash
cd web-ui
npm install leaflet@1.9.4 leaflet-minimap@3.6.1 leaflet-mouse-position@1.0.4
```

**CSS imports required:**
```javascript
import 'leaflet/dist/leaflet.css';
import 'leaflet-minimap/dist/Control.MiniMap.min.css';
import 'leaflet-mouse-position/src/L.Control.MousePosition.css';
```

## Architecture Patterns

### Recommended Project Structure

```
web-ui/src/
├── components/
│   └── MapView.jsx         # Leaflet map component with useRef pattern
├── pages/
│   └── DispatchConsole.jsx # Already exists, mounts MapView in map-container
└── styles.css              # Map container styles already exist (Phase 22)
```

### Pattern 1: Vanilla Leaflet with React useRef + useEffect

**What:** Initialize Leaflet map in useEffect, store instance in useRef, cleanup with map.remove() on unmount

**When to use:** Always for Leaflet in React functional components — avoids react-leaflet Strict Mode issues and provides full control

**Example:**
```javascript
// Source: https://cherniavskii.com/using-leaflet-in-react-apps-with-react-hooks/
import React, { useRef, useEffect } from 'react';
import L from 'leaflet';

const MapView = () => {
  const mapRef = useRef(null); // Store map instance (not state — avoids re-renders)
  const mapContainerRef = useRef(null); // DOM element ref

  useEffect(() => {
    // Initialize map only once
    if (!mapRef.current && mapContainerRef.current) {
      const map = L.map(mapContainerRef.current, {
        center: [-33.8688, 151.2093],
        zoom: 12,
        keyboard: false, // Disable arrow key panning per user constraints
        scrollWheelZoom: true,
        doubleClickZoom: true
      });

      // Add tile layer
      L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
        attribution: 'Esri, Maxar, Earthstar Geographics'
      }).addTo(map);

      mapRef.current = map;
    }

    // Cleanup on unmount
    return () => {
      if (mapRef.current) {
        mapRef.current.remove(); // Destroys map and clears event listeners
        mapRef.current = null;
      }
    };
  }, []); // Empty deps — run once on mount

  return <div ref={mapContainerRef} style={{ width: '100%', height: '100%' }} />;
};
```

**Why useRef over useState:** Storing map in state would trigger re-render on every map mutation; useRef provides mutable container without re-renders ([React Hooks versatility](https://michalzalecki.com/versatility-and-use-cases-of-react-use-effect-hook/))

### Pattern 2: Geolocation with Timeout and Fallback

**What:** Attempt browser geolocation with timeout, fall back to default coordinates on failure

**When to use:** Initial map center determination (user constraint: silent, no UI)

**Example:**
```javascript
// Source: https://blog.logrocket.com/what-you-need-know-while-using-geolocation-api/
const DEFAULT_CENTER = [-33.8688, 151.2093]; // Sydney
const DEFAULT_ZOOM = 12;
const GEOLOCATION_TIMEOUT = 5000; // 5 seconds

useEffect(() => {
  if (!mapRef.current) return;

  // Try geolocation (silent)
  if ('geolocation' in navigator) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        mapRef.current.setView(
          [position.coords.latitude, position.coords.longitude],
          DEFAULT_ZOOM
        );
      },
      () => {
        // Error or timeout — use default
        mapRef.current.setView(DEFAULT_CENTER, DEFAULT_ZOOM);
      },
      {
        timeout: GEOLOCATION_TIMEOUT,
        enableHighAccuracy: false, // Faster response
        maximumAge: 300000 // 5 minutes
      }
    );
  } else {
    // Geolocation not supported — use default
    mapRef.current.setView(DEFAULT_CENTER, DEFAULT_ZOOM);
  }
}, []);
```

### Pattern 3: localStorage Save/Restore for Map State

**What:** Save center, zoom, and active layer to localStorage on map events; restore on mount

**When to use:** View persistence per user constraints

**Example:**
```javascript
// Source: https://github.com/makinacorpus/Leaflet.RestoreView
const STORAGE_KEY = (eventId) => `cv.dispatch.map.${eventId}`;

// Restore on mount
useEffect(() => {
  const stored = localStorage.getItem(STORAGE_KEY(eventId));
  if (stored) {
    const { center, zoom, layer } = JSON.parse(stored);
    map.setView(center, zoom);
    // Switch to saved layer...
  }
}, []);

// Save on change
useEffect(() => {
  if (!mapRef.current) return;

  const saveState = () => {
    const center = mapRef.current.getCenter();
    const zoom = mapRef.current.getZoom();
    const state = {
      center: [center.lat, center.lng],
      zoom,
      layer: activeLayer // Track in component state
    };
    localStorage.setItem(STORAGE_KEY(eventId), JSON.stringify(state));
  };

  mapRef.current.on('moveend', saveState);
  mapRef.current.on('zoomend', saveState);

  return () => {
    mapRef.current.off('moveend', saveState);
    mapRef.current.off('zoomend', saveState);
  };
}, [eventId, activeLayer]);
```

### Pattern 4: Layer Control with Base Layers

**What:** Use L.control.layers() for satellite/street switching, positioned top-right

**When to use:** Layer switching per user constraints (Leaflet default control)

**Example:**
```javascript
// Source: https://leafletjs.com/reference.html
const satelliteLayer = L.tileLayer(
  'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
  { attribution: 'Esri, Maxar' }
);

const streetLayer = L.tileLayer(
  'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  { attribution: '© OpenStreetMap contributors' }
);

// Add default layer
satelliteLayer.addTo(map);

// Create control
const baseLayers = {
  'Satellite': satelliteLayer,
  'Street': streetLayer
};

L.control.layers(baseLayers, null, { position: 'topright' }).addTo(map);
```

### Anti-Patterns to Avoid

- **Using react-leaflet:** Adds abstraction layer and has Strict Mode double-mount issues ([Issue #963](https://github.com/PaulLeCam/react-leaflet/issues/963)); vanilla Leaflet gives better control
- **Storing map in useState:** Triggers re-renders on every map mutation; use useRef instead
- **Skipping cleanup function:** Memory leaks in React Strict Mode; always call map.remove() in useEffect return
- **Multiple map instances:** Leaflet throws "Map container is already initialized" error; use single ref with proper cleanup
- **Forgetting Leaflet CSS import:** Map renders broken without `import 'leaflet/dist/leaflet.css'`

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coordinate display on hover | Custom mouse event handler with formatted lat/lng | `leaflet-mouse-position` plugin | Handles edge cases (projection, precision, position updates, resize events) |
| Minimap control | Custom minimap with canvas or second Leaflet instance | `leaflet-minimap` plugin | Manages sync, zoom offset, toggle state, events, and positioning |
| Map state persistence | Custom localStorage wrapper | Leaflet events (`moveend`, `zoomend`) + manual save/restore | Event-driven saves prevent race conditions; restore in useEffect ensures map is initialized |
| Layer switching UI | Custom button group with CSS | `L.control.layers()` | Built-in Leaflet control handles radio behavior, positioning, expand/collapse, and layer management |
| Tile caching | Custom service worker or IndexedDB | Browser HTTP cache | Browsers automatically cache tiles with appropriate headers; Esri and OSM tiles are cache-friendly |

**Key insight:** Leaflet's plugin ecosystem has mature, battle-tested solutions for common map UX patterns. Custom implementations miss edge cases (touch devices, retina displays, projection edge cases, browser quirks) that plugins handle.

## Common Pitfalls

### Pitfall 1: React 18 Strict Mode Double Mount Breaks Map Initialization

**What goes wrong:** React 18 Strict Mode simulates mount → unmount → remount to test cleanup; Leaflet throws "Map container is already initialized" error on remount if cleanup is missing

**Why it happens:** useEffect runs twice in development (Strict Mode), calling `L.map()` twice on same DOM element; Leaflet prevents multiple map instances per container

**How to avoid:** Always include cleanup function in useEffect that calls `map.remove()` and sets `mapRef.current = null`

**Warning signs:**
- Console error: "Map container is already initialized"
- Map renders in production but breaks in development
- Map disappears after Hot Module Reload

**Fix:**
```javascript
useEffect(() => {
  // Initialize map
  const map = L.map(containerRef.current, { /* options */ });
  mapRef.current = map;

  // CRITICAL: Cleanup on unmount
  return () => {
    if (mapRef.current) {
      mapRef.current.remove(); // Destroys map and clears listeners
      mapRef.current = null;
    }
  };
}, []); // Empty deps — run once
```

**Sources:**
- [React Strict Mode double mount](https://github.com/PaulLeCam/react-leaflet/issues/963)
- [Strict Mode Explained for 2026](https://javascript.plainenglish.io/react-strict-mode-explained-for-2026-5fca1c3fa786)

### Pitfall 2: Geolocation Hangs Indefinitely Without Timeout

**What goes wrong:** Browser geolocation API defaults to `timeout: Infinity`; if GPS hardware is unavailable or permission is denied, the app hangs forever with no fallback

**Why it happens:** Default geolocation options are optimistic; mobile browsers may wait indefinitely for GPS lock

**How to avoid:** Always set `timeout` (5-10 seconds) and provide fallback coordinates in error callback

**Warning signs:**
- Map never centers on user location
- No visible error or loading state
- Works on desktop (WiFi geolocation fast) but hangs on mobile (GPS slow)

**Fix:**
```javascript
navigator.geolocation.getCurrentPosition(
  successCallback,
  errorCallback,
  {
    timeout: 5000, // CRITICAL: Prevent infinite wait
    enableHighAccuracy: false, // Faster response
    maximumAge: 300000 // Allow 5-minute cached position
  }
);
```

**Sources:**
- [Geolocation API best practices](https://blog.logrocket.com/what-you-need-know-while-using-geolocation-api/)
- [Troubleshooting geolocation timeout](https://differ.blog/p/troubleshooting-common-issues-with-browser-geolocation-apis-38a2a7)

### Pitfall 3: Missing Leaflet CSS Breaks Map Rendering

**What goes wrong:** Map div renders but tiles are misaligned, controls invisible, or pane layers broken

**Why it happens:** Leaflet relies on CSS for z-index stacking, absolute positioning, and tile alignment; forgetting CSS import breaks visual layout

**How to avoid:** Always import `leaflet/dist/leaflet.css` at top of component or in main entry point

**Warning signs:**
- Map tiles visible but overlapping incorrectly
- Zoom controls missing or positioned wrong
- Attribution text invisible or cut off

**Fix:**
```javascript
// At top of MapView.jsx
import 'leaflet/dist/leaflet.css';
import 'leaflet-minimap/dist/Control.MiniMap.min.css';
import 'leaflet-mouse-position/src/L.Control.MousePosition.css';
```

### Pitfall 4: Esri Label Overlay Requires Separate Tile Layer

**What goes wrong:** Using only `World_Imagery` tile URL shows raw satellite imagery without road/place labels; user constraints require hybrid view

**Why it happens:** Esri separates imagery and labels into different tile services; labels are an overlay, not baked into satellite tiles

**How to avoid:** Add `World_Boundaries_and_Places` as second tile layer on top of `World_Imagery`

**Warning signs:**
- Satellite view shows terrain but no city names or road labels
- User testing feedback: "Can't read street names on satellite view"

**Fix:**
```javascript
// Base satellite layer
const satelliteLayer = L.tileLayer(
  'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
  { attribution: 'Esri, Maxar' }
);

// Label overlay (add after satellite)
const labelsLayer = L.tileLayer(
  'https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
  { attribution: '' } // Attribution already on satellite layer
);

// Add both to map
const satelliteWithLabels = L.layerGroup([satelliteLayer, labelsLayer]);
```

**Sources:**
- [Esri basemap with labels example](https://esri.github.io/esri-leaflet/examples/basemap-with-labels.html)
- [Leaflet with Esri satellite + CartoDB labels](https://gist.github.com/nitaku/b1f831256e6770f8a90f)

### Pitfall 5: localStorage Key Collision Between Events

**What goes wrong:** Multiple dispatch consoles (different events) share same localStorage key; switching events shows wrong map center/zoom

**Why it happens:** Using static key like `cv.dispatch.map` instead of event-scoped key

**How to avoid:** Include `eventId` in localStorage key: `cv.dispatch.map.${eventId}`

**Warning signs:**
- Map center jumps to wrong location when switching events
- Saved layer choice applies to all events incorrectly

**Fix:**
```javascript
const STORAGE_KEY = (eventId) => `cv.dispatch.map.${eventId}`;

const saveState = (eventId) => {
  localStorage.setItem(STORAGE_KEY(eventId), JSON.stringify(state));
};

const restoreState = (eventId) => {
  const stored = localStorage.getItem(STORAGE_KEY(eventId));
  // ...
};
```

## Code Examples

Verified patterns from official sources:

### Complete MapView Component Skeleton

```javascript
// web-ui/src/components/MapView.jsx
import React, { useRef, useEffect, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import 'leaflet-minimap/dist/Control.MiniMap.min.css';
import 'leaflet-mouse-position/src/L.Control.MousePosition.css';

const MapView = ({ eventId }) => {
  const mapRef = useRef(null);
  const containerRef = useRef(null);
  const [activeLayer, setActiveLayer] = useState('satellite'); // For localStorage

  // Constants
  const DEFAULT_CENTER = [-33.8688, 151.2093]; // Sydney
  const DEFAULT_ZOOM = 12;
  const GEOLOCATION_TIMEOUT = 5000;
  const STORAGE_KEY = `cv.dispatch.map.${eventId}`;

  // Initialize map (runs once)
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    // Create map
    const map = L.map(containerRef.current, {
      center: DEFAULT_CENTER,
      zoom: DEFAULT_ZOOM,
      keyboard: false, // No arrow key panning
      scrollWheelZoom: true,
      doubleClickZoom: true,
      zoomControl: false // Add custom at bottom-right later
    });

    // Tile layers
    const satelliteLayer = L.tileLayer(
      'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      { attribution: 'Esri, Maxar, Earthstar Geographics' }
    );

    const labelsLayer = L.tileLayer(
      'https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}',
      { attribution: '' }
    );

    const satelliteWithLabels = L.layerGroup([satelliteLayer, labelsLayer]);

    const streetLayer = L.tileLayer(
      'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      { attribution: '© OpenStreetMap contributors' }
    );

    // Add default layer (satellite)
    satelliteWithLabels.addTo(map);

    // Layer control (top-right)
    const baseLayers = {
      'Satellite': satelliteWithLabels,
      'Street': streetLayer
    };
    L.control.layers(baseLayers, null, { position: 'topright' }).addTo(map);

    // Zoom control (bottom-right)
    L.control.zoom({ position: 'bottomright' }).addTo(map);

    // Scale bar (bottom-left, metric only)
    L.control.scale({ position: 'bottomleft', imperial: false }).addTo(map);

    // TODO: Add mouse position control (bottom-left)
    // TODO: Add minimap control (off by default)
    // TODO: Restore saved state from localStorage
    // TODO: Try geolocation with timeout
    // TODO: Save state on moveend/zoomend

    mapRef.current = map;

    // Cleanup on unmount
    return () => {
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
    };
  }, []);

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: '100%' }}
    />
  );
};

export default MapView;
```

### Geolocation with Timeout Pattern

```javascript
// Source: https://blog.logrocket.com/what-you-need-know-while-using-geolocation-api/
useEffect(() => {
  if (!mapRef.current) return;

  // Try geolocation (silent, no UI)
  if ('geolocation' in navigator) {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        // Success — center on user location
        mapRef.current.setView(
          [position.coords.latitude, position.coords.longitude],
          DEFAULT_ZOOM
        );
      },
      (error) => {
        // Error or timeout — use default center
        console.warn('Geolocation failed, using default center:', error.message);
        mapRef.current.setView(DEFAULT_CENTER, DEFAULT_ZOOM);
      },
      {
        timeout: GEOLOCATION_TIMEOUT,
        enableHighAccuracy: false, // Faster response
        maximumAge: 300000 // Allow 5-minute cache
      }
    );
  } else {
    // Geolocation not supported — use default
    mapRef.current.setView(DEFAULT_CENTER, DEFAULT_ZOOM);
  }
}, []);
```

### localStorage Save/Restore Pattern

```javascript
// Source: https://github.com/makinacorpus/Leaflet.RestoreView
// Restore saved state on mount
useEffect(() => {
  if (!mapRef.current) return;

  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      const { center, zoom, layer } = JSON.parse(stored);
      mapRef.current.setView(center, zoom);
      setActiveLayer(layer);
      // Switch to saved layer (implementation depends on layer tracking)
    }
  } catch (err) {
    console.warn('Failed to restore map state:', err);
  }
}, []);

// Save state on map changes
useEffect(() => {
  if (!mapRef.current) return;

  const saveState = () => {
    const center = mapRef.current.getCenter();
    const zoom = mapRef.current.getZoom();
    const state = {
      center: [center.lat, center.lng],
      zoom,
      layer: activeLayer
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  };

  mapRef.current.on('moveend', saveState);
  mapRef.current.on('zoomend', saveState);

  return () => {
    mapRef.current.off('moveend', saveState);
    mapRef.current.off('zoomend', saveState);
  };
}, [activeLayer]);

// Save layer choice when changed
useEffect(() => {
  // Triggered when user switches layers via control
  // Update activeLayer state to trigger save
}, []);
```

### Mouse Position Control Integration

```javascript
// Source: https://github.com/ardhi/Leaflet.MousePosition
import 'leaflet-mouse-position';

// Add after map initialization
L.control.mousePosition({
  position: 'bottomleft',
  separator: ', ',
  lngFirst: false, // Lat, Lng order
  numDigits: 4, // Decimal places
  emptyString: 'Move mouse over map',
  prefix: ''
}).addTo(map);
```

### MiniMap Control Integration

```javascript
// Source: https://github.com/Norkart/Leaflet-MiniMap
import 'leaflet-minimap';

// Create minimap tile layer (same as main map or different)
const minimapLayer = L.tileLayer(
  'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  { attribution: '' }
);

// Add minimap control (off by default)
const minimap = new L.Control.MiniMap(minimapLayer, {
  position: 'bottomright',
  width: 150,
  height: 150,
  zoomLevelOffset: -5,
  toggleDisplay: true, // Show minimize/restore button
  autoToggleDisplay: false,
  minimized: true // Start collapsed (user constraint: off by default)
}).addTo(map);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Esri BasemapLayer plugin | Direct tile URLs or Vector Basemap Layer | 2023-2025 | Legacy ArcGIS Online tile services in mature status; new vector service requires API key; direct raster tile URLs still work without key |
| react-leaflet v4 | react-leaflet v5 or vanilla Leaflet | Feb 2025 | React v19 required for v5; removed LeafletProvider component; vanilla Leaflet preferred for better cleanup control |
| Leaflet 1.7.x | Leaflet 1.9.4 | Sept 2022 - present | 1.9 series added ESM support (reverted in 1.9.2), improved accessibility, bug fixes; 2.0.0-alpha released Aug 2025 but not production-ready |
| geolocation.watchPosition | geolocation.getCurrentPosition with timeout | Ongoing | watchPosition drains battery; getCurrentPosition with timeout preferred for one-time centering |

**Deprecated/outdated:**
- **L.esri.BasemapLayer:** Legacy raster basemap plugin — replaced by vector basemap layer service (requires API key); direct tile URLs still work
- **Leaflet 0.7.x:** Required for old leaflet-minimap versions — modern versions compatible with Leaflet 1.x
- **react-leaflet v3/v4:** Use v5 (requires React 19) or vanilla Leaflet (better cleanup, no version constraint)

## Open Questions

1. **Leaflet-minimap compatibility with Leaflet 1.9.4**
   - What we know: Plugin last updated March 2018, tested with Leaflet 1.0.0-beta2; npm shows 395 stars, active downloads
   - What's unclear: Whether plugin works correctly with Leaflet 1.9.4 without patches
   - Recommendation: Test during implementation; fallback is custom minimap div with second L.map() instance (simple but more code)

2. **Leaflet-mouse-position plugin maintenance status**
   - What we know: Multiple forks exist (ardhi, MrMufflon, FCOO); npm package exists but unclear which is canonical
   - What's unclear: Which fork is most actively maintained and compatible with Leaflet 1.9.4
   - Recommendation: Start with `leaflet-mouse-position` npm package; if broken, implement custom using map.on('mousemove') event (10-15 LOC)

3. **Esri tile URL rate limiting or usage restrictions**
   - What we know: Tiles work without API key; Esri states "not for commercial use" in terms
   - What's unclear: Whether VoicePing Router (internal dispatch tool) qualifies as commercial use; rate limits unclear
   - Recommendation: Proceed with direct tile URLs (user decision: Esri World Imagery); if rate-limited in production, switch to API key approach or alternative provider

## Sources

### Primary (HIGH confidence)

- [Leaflet 1.9.4 official documentation](https://leafletjs.com/reference.html) - Map API, controls, tile layers
- [Leaflet download page](https://leafletjs.com/download.html) - Current version, CDN links
- [React Leaflet v5 documentation](https://react-leaflet.js.org/) - React integration patterns, core architecture
- [Leaflet.MiniMap GitHub](https://github.com/Norkart/Leaflet-MiniMap) - Plugin API, configuration options
- [Leaflet.MousePosition GitHub](https://github.com/ardhi/Leaflet.MousePosition) - Plugin configuration
- [Esri World Boundaries and Places MapServer](https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer) - Label overlay tile service
- [Leaflet with Esri tiles example](https://gist.github.com/nitaku/047a77e256de17f25e72) - Satellite tile URL verification

### Secondary (MEDIUM confidence)

- [React Leaflet Strict Mode issue #963](https://github.com/PaulLeCam/react-leaflet/issues/963) - Double mount problem
- [Leaflet React Hooks pattern](https://cherniavskii.com/using-leaflet-in-react-apps-with-react-hooks/) - useRef + useEffect cleanup
- [Leaflet.RestoreView plugin](https://github.com/makinacorpus/Leaflet.RestoreView) - localStorage save/restore pattern
- [Geolocation API best practices](https://blog.logrocket.com/what-you-need-know-while-using-geolocation-api/) - Timeout and fallback handling
- [Esri basemap with labels example](https://esri.github.io/esri-leaflet/examples/basemap-with-labels.html) - Hybrid satellite view pattern
- [OpenStreetMap tile providers wiki](https://wiki.openstreetmap.org/wiki/Raster_tile_providers) - OSM tile URL verification
- [Leaflet plugins directory](https://leafletjs.com/plugins.html) - Official plugin list

### Tertiary (LOW confidence, validation needed)

- [React map library comparison](https://blog.logrocket.com/react-map-library-comparison/) - Ecosystem overview (2024 article)
- [Leaflet keyboard navigation GitHub issue #7479](https://github.com/Leaflet/Leaflet/issues/7479) - Keyboard accessibility discussion
- [Esri World Imagery in OpenStreetMap](https://www.esri.com/arcgis-blog/products/constituent-engagement/constituent-engagement/esri-world-imagery-in-openstreetmap) - Licensing clarification (2023 blog post)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Leaflet 1.9.4 verified as latest stable, direct tile URLs tested in examples
- Architecture: HIGH - useRef + useEffect pattern standard for React functional components with imperative APIs
- Pitfalls: HIGH - React Strict Mode double mount issue documented in react-leaflet GitHub, cleanup pattern verified in official docs

**Research date:** 2026-02-17
**Valid until:** ~60 days (Leaflet stable, slow-moving library; tile URLs unlikely to change)
