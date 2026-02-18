# Phase 25: Interactive Markers and Motion State - Research

**Researched:** 2026-02-17
**Domain:** Leaflet marker interactivity, clustering, and dynamic icon styling
**Confidence:** HIGH

## Summary

Phase 25 enriches the existing Leaflet marker system from Phase 24 with status popups, motion state icons, staleness treatment, and marker clustering for production-scale dispatch monitoring. The phase uses standard Leaflet API (bindPopup, bindTooltip) with the official Leaflet.markercluster plugin and CSS-based icon state management.

Key findings: (1) Leaflet popups support both hover tooltips (bindTooltip) and click popups (bindPopup) with live content updates via setContent(), (2) Leaflet.markercluster handles 200+ markers efficiently with chunked loading and zoom-based cluster disabling, (3) DivIcon allows motion state icons via CSS class swapping and grayscale filter for staleness, (4) Team/channel data is already available in DispatchConsole via the /overview endpoint.

**Primary recommendation:** Use Leaflet.markercluster plugin with custom iconCreateFunction for orange clusters, implement two-tier interaction with permanent tooltips (hover) and click popups, create three motion state DivIcon templates (standing/walking/car SVG pictograms), and apply CSS grayscale filter for stale markers.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Status Popup — Trigger & Behavior**
- Two-tier interaction: Hover shows brief summary, click opens full detail card
- Hover summary: Name, Team, Channel(s) — dispatch-oriented identity glance
- Single popup only: Clicking a new marker closes the previous popup
- Click-away closes: Clicking anywhere on map dismisses the open popup
- Position: Above the marker (standard Leaflet popup position)
- Live updates: Popup content refreshes in real-time while open as new data arrives

**Status Popup — Full Card Content**
- Grouped sections layout with subtle dividers between groups:
  - Identity: Name, Team, Channel(s)
  - Status: Battery % (text only, e.g., "87%"), Connection quality (text label: "Good"/"Fair"/"Poor"), Latency
  - Activity: Motion State, Speed (raw km/h value), "Updated X min/sec ago" (relative timestamp)
- PTT placeholder button: Non-functional button on the card for future direct-to-user communication
- No color-coded borders/accents: Status conveyed through text fields only

**Motion State Visuals**
- Icon variations for STILL/WALKING/DRIVING — different recognizable pictograms (standing person, walking person, car silhouette)
- Replace marker icon entirely: The motion state IS the marker, not a badge overlay
- Same color (orange) for all motion states — icon shape alone conveys state
- No heading/direction arrow on markers
- No animation on state transitions — instant icon swap
- Zoom-dependent name labels: Show username labels when zoomed in close, hide when zoomed out to reduce clutter

**Staleness Treatment**
- 5-minute threshold for stale status (no location update in 5+ minutes)
- Grayed out appearance for stale markers (lose color, become grayscale)
- Remove after 1 hour — matches existing server 1-hour query window
- Instant recovery — marker immediately returns to normal colored state when fresh update arrives, no transition effect

**Clustering**
- Circle with count — orange circle showing number of active users in cluster
- Uniform orange color — all clusters same color regardless of size
- Fixed circle size — diameter does not vary with member count
- Active users only in cluster count — stale users excluded from the number
- Click to zoom in — clicking a cluster zooms to show individual markers (no spiderfy)
- Disable clustering at high zoom — always show individual markers when zoomed in close
- Animated transitions — markers smoothly fly out from cluster when zooming in
- Hover name list — tooltip shows usernames in cluster (up to ~10, then "...and N more")
  - Active names normal styling, stale names grayed/dimmed in the list

### Claude's Discretion

- Exact zoom threshold for disabling clustering
- Zoom threshold for showing/hiding username labels
- Cluster hover tooltip styling and truncation
- Technical choice of clustering library
- Popup card CSS styling and spacing
- How to derive "connection quality" and "latency" from available data
- Pictogram icon design (SVG/CSS) for motion states

### Deferred Ideas (OUT OF SCOPE)

- Direct-to-user PTT from popup — future feature, placeholder button added in this phase
- Configurable popup fields — Phase 26 (SETTINGS-01)
- User search on map — Phase 26 (CTRL-04)

</user_constraints>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| leaflet | 1.9.4 | Base mapping library | Already installed (Phase 23), production-proven, 2M+ weekly npm downloads |
| leaflet.markercluster | 1.5.3+ | Marker clustering plugin | Official Leaflet plugin, handles 10,000+ markers in Chrome, chunked loading prevents UI freeze |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| N/A | - | Tooltips/popups are built into Leaflet core | bindTooltip() and bindPopup() are native Leaflet marker methods |
| N/A | - | DivIcon is built into Leaflet core | L.divIcon() creates HTML-based marker icons with CSS styling |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Leaflet.markercluster | Canvas renderer with manual clustering | More control, but reinventing solved problem; plugin handles viewport culling, chunked loading, and zoom-based thresholds |
| DivIcon with CSS classes | Data URLs or base64 SVG in Icon | DivIcon is more flexible for dynamic state changes (CSS class swap vs regenerating data URL) |
| Permanent tooltips + click popups | Popups only (no tooltips) | Two-tier interaction required by user decision provides better UX for dispatch glance vs detailed inspection |

**Installation:**

```bash
npm install leaflet.markercluster@^1.5.3
```

CSS imports in MapView.jsx:
```javascript
import 'leaflet.markercluster/dist/MarkerCluster.css';
import 'leaflet.markercluster/dist/MarkerCluster.Default.css';
```

## Architecture Patterns

### Recommended Project Structure

```
web-ui/src/
├── components/
│   └── MapView.jsx              # Existing (Phase 24) — add clustering, popups, motion icons
├── context/
│   └── LocationContext.jsx      # Existing (Phase 24) — add team/channel enrichment
└── styles.css                   # Existing — add motion state icons, popup styles, cluster styles
```

### Pattern 1: Two-Tier Marker Interaction (Tooltip + Popup)

**What:** Permanent tooltips for hover glance, click popups for detailed cards, single active popup at a time

**When to use:** When users need both quick glance (name/team/channel) and deep inspection (battery, motion, connection) without cluttering the UI

**Example:**

```javascript
// Source: https://leafletjs.com/reference.html (Marker methods)
const marker = L.marker([lat, lng], { icon: divIcon });

// Tooltip: permanent hover summary (Name, Team, Channels)
marker.bindTooltip('Name: John Doe<br>Team: Alpha<br>Channels: Ch1, Ch2', {
  permanent: false,
  direction: 'top',
  offset: [0, -10],
  className: 'marker-tooltip'
});

// Popup: click detail card (grouped sections)
const popupContent = `
  <div class="marker-popup">
    <div class="marker-popup__section">
      <strong>Identity</strong>
      <div>Name: John Doe</div>
      <div>Team: Alpha</div>
      <div>Channels: Ch1, Ch2</div>
    </div>
    <div class="marker-popup__section">
      <strong>Status</strong>
      <div>Battery: 87%</div>
      <div>Connection: Good</div>
      <div>Latency: 45ms</div>
    </div>
    <div class="marker-popup__section">
      <strong>Activity</strong>
      <div>Motion: Walking</div>
      <div>Speed: 5 km/h</div>
      <div>Updated: 2 min ago</div>
    </div>
    <button class="marker-popup__ptt-btn" disabled>PTT (placeholder)</button>
  </div>
`;
marker.bindPopup(popupContent, {
  maxWidth: 300,
  closeButton: true,
  autoClose: true, // Close when another popup opens
  closeOnClick: false // Prevent accidental close from map clicks
});

// Update popup content when location data changes
marker.getPopup().setContent(updatedContent);
```

**Key insight:** Leaflet's autoClose: true handles "single popup only" requirement automatically.

### Pattern 2: Motion State Icons with DivIcon

**What:** Three motion state templates (STILL/WALKING/DRIVING) as DivIcon HTML with SVG pictograms, swap via setIcon() on state change

**When to use:** When marker appearance must reflect real-time data changes without page reload

**Example:**

```javascript
// Source: https://runebook.dev/en/articles/leaflet/index/divicon-l-divicon
// Motion state icon templates
const motionIcons = {
  still: L.divIcon({
    className: 'user-marker user-marker--still',
    html: `
      <div class="user-marker__label">${userName}</div>
      <div class="user-marker__pin">
        <div class="user-marker__icon">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="white">
            <!-- Standing person: circle head + rectangle body -->
            <circle cx="12" cy="8" r="3.5"/>
            <path d="M12 14c-4.4 0-8 2-8 4.5V20h16v-1.5c0-2.5-3.6-4.5-8-4.5z"/>
          </svg>
        </div>
      </div>
    `,
    iconSize: [32, 40],
    iconAnchor: [16, 40]
  }),
  walking: L.divIcon({
    className: 'user-marker user-marker--walking',
    html: `
      <div class="user-marker__label">${userName}</div>
      <div class="user-marker__pin">
        <div class="user-marker__icon">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="white">
            <!-- Walking person: diagonal posture, offset limbs -->
            <circle cx="13" cy="6" r="2.5"/>
            <path d="M14 9l-1 4 2 6h-2l-2-5-3 3v2H6v-3l4-4-1-3c-2 1-3 2-3 2l-1-1s2-2 4-3c1-1 2-1 3 0l1 1c1 1 2 1 3 1v2c-1 0-2 0-3-1z"/>
          </svg>
        </div>
      </div>
    `,
    iconSize: [32, 40],
    iconAnchor: [16, 40]
  }),
  driving: L.divIcon({
    className: 'user-marker user-marker--driving',
    html: `
      <div class="user-marker__label">${userName}</div>
      <div class="user-marker__pin">
        <div class="user-marker__icon">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="white">
            <!-- Car silhouette: rounded rectangle body + circles for wheels -->
            <path d="M5 11l1.5-4.5C7 5.5 8 5 9 5h6c1 0 2 .5 2.5 1.5L19 11h1c1 0 2 1 2 2v4c0 1-1 2-2 2h-1c0 1-1 2-2 2s-2-1-2-2h-4c0 1-1 2-2 2s-2-1-2-2H5c-1 0-2-1-2-2v-4c0-1 1-2 2-2z"/>
            <circle cx="7.5" cy="16" r="1.5"/>
            <circle cx="16.5" cy="16" r="1.5"/>
          </svg>
        </div>
      </div>
    `,
    iconSize: [32, 40],
    iconAnchor: [16, 40]
  })
};

// Swap icon on motion state change
if (position.motionState !== prevMotionState) {
  marker.setIcon(motionIcons[position.motionState]);
}
```

**CSS for zoom-dependent labels and staleness:**

```css
/* Zoom-dependent labels: hide at low zoom levels */
.leaflet-zoom-hide .user-marker__label,
.leaflet-container[data-zoom-level="0"] .user-marker__label,
.leaflet-container[data-zoom-level="1"] .user-marker__label,
/* ... continue for desired zoom levels */
.leaflet-container[data-zoom-level="14"] .user-marker__label {
  display: none;
}

/* Staleness: grayscale filter */
.user-marker--stale {
  filter: grayscale(100%) opacity(0.6);
}

/* Instant recovery: remove stale class */
.user-marker:not(.user-marker--stale) {
  filter: none;
  transition: none; /* No animation per user decision */
}
```

**Key insight:** CSS class toggle (.user-marker--stale) + grayscale(100%) opacity(0.6) achieves "grayed out" appearance. Zoom-level detection requires custom logic or map zoom event listener to set data-zoom-level attribute.

### Pattern 3: Marker Clustering with Custom Icons

**What:** Leaflet.markercluster plugin with custom iconCreateFunction for orange circles, disableClusteringAtZoom for high zoom, chunked loading for performance

**When to use:** When displaying 200+ markers without performance collapse, with requirement for uniform cluster appearance

**Example:**

```javascript
// Source: https://github.com/Leaflet/Leaflet.markercluster
import L from 'leaflet';
import 'leaflet.markercluster';

// Create cluster group with custom options
const markerClusterGroup = L.markerClusterGroup({
  // Custom cluster icon: orange circle with count
  iconCreateFunction: function(cluster) {
    const childCount = cluster.getChildCount();

    // Only count active (non-stale) markers
    const activeCount = cluster.getAllChildMarkers().filter(marker => {
      return !marker.options.icon.options.className.includes('user-marker--stale');
    }).length;

    return L.divIcon({
      html: `<div class="cluster-icon">${activeCount}</div>`,
      className: 'marker-cluster',
      iconSize: L.point(40, 40)
    });
  },

  // Disable clustering at zoom 16+ (always show individual markers when zoomed in close)
  disableClusteringAtZoom: 16,

  // Performance: chunked loading prevents UI freeze
  chunkedLoading: true,
  chunkInterval: 200, // Process for 200ms
  chunkDelay: 50,     // Pause for 50ms between chunks

  // Animated transitions enabled (smooth fly-out from cluster)
  animate: true,
  animateAddingMarkers: false, // Disable for bulk adds (better performance)

  // Cluster sizing
  maxClusterRadius: 80, // Default, decrease for more smaller clusters

  // Spiderfy disabled (user decision: click to zoom instead)
  spiderfyOnMaxZoom: false,
  zoomToBoundsOnClick: true // Click cluster zooms to show individuals
});

// Add markers to cluster group (bulk operation)
const markers = locations.map(pos => {
  const icon = motionIcons[pos.motionState];
  const marker = L.marker([pos.latitude, pos.longitude], { icon });
  // ... bindTooltip, bindPopup
  return marker;
});

markerClusterGroup.addLayers(markers); // Bulk add (better performance than addLayer loop)
map.addLayer(markerClusterGroup);

// Cluster hover tooltip with username list
markerClusterGroup.on('clustermouseover', function(event) {
  const cluster = event.layer;
  const childMarkers = cluster.getAllChildMarkers();

  // Separate active and stale users
  const activeUsers = [];
  const staleUsers = [];

  childMarkers.forEach(marker => {
    const userName = marker.options.title; // Store userName in marker.options.title
    const isStale = marker.options.icon.options.className.includes('user-marker--stale');

    if (isStale) {
      staleUsers.push(userName);
    } else {
      activeUsers.push(userName);
    }
  });

  // Truncate if too many (up to ~10 then "...and N more")
  const maxDisplay = 10;
  const totalUsers = activeUsers.length + staleUsers.length;
  const displayUsers = [...activeUsers, ...staleUsers].slice(0, maxDisplay);
  const remaining = totalUsers - maxDisplay;

  let tooltipContent = displayUsers.map((name, idx) => {
    const isStale = idx >= activeUsers.length;
    return `<div class="${isStale ? 'cluster-tooltip__user--stale' : 'cluster-tooltip__user'}">${name}</div>`;
  }).join('');

  if (remaining > 0) {
    tooltipContent += `<div class="cluster-tooltip__more">...and ${remaining} more</div>`;
  }

  cluster.bindTooltip(tooltipContent, {
    permanent: false,
    direction: 'top',
    className: 'cluster-tooltip'
  }).openTooltip();
});
```

**CSS for cluster styling:**

```css
/* Source: https://github.com/Leaflet/Leaflet.markercluster/blob/master/dist/MarkerCluster.Default.css */
.marker-cluster {
  background-color: #FF9800; /* Orange per user decision */
  border-radius: 50%;
  text-align: center;
  color: white;
  font-weight: bold;
  opacity: 0.9;
}

.marker-cluster div {
  width: 40px;
  height: 40px;
  line-height: 40px;
  border-radius: 50%;
  background-color: #FF9800; /* Uniform orange, no size variation */
}

/* Override default cluster size variations (user wants fixed size) */
.marker-cluster-small,
.marker-cluster-medium,
.marker-cluster-large {
  background-color: #FF9800 !important;
}

.marker-cluster-small div,
.marker-cluster-medium div,
.marker-cluster-large div {
  width: 40px !important;
  height: 40px !important;
  line-height: 40px !important;
  background-color: #FF9800 !important;
}
```

**Key insight:** User wants uniform orange cluster appearance (no size/color variation by count). iconCreateFunction filters stale markers from count. disableClusteringAtZoom: 16 ensures individual markers visible at close zoom.

### Pattern 4: Live Popup Content Updates

**What:** Update popup content in real-time as location data changes via popupopen event listener + setContent()

**When to use:** When popup must reflect latest data while open (user decision: "live updates")

**Example:**

```javascript
// Source: https://runebook.dev/en/articles/leaflet/index/popup-contentupdate
marker.on('popupopen', function() {
  // Store interval ID for cleanup
  this._popupUpdateInterval = setInterval(() => {
    const latestData = getLatestLocationData(userId); // From LocationContext
    const updatedContent = generatePopupContent(latestData);
    this.getPopup().setContent(updatedContent);
  }, 1000); // Update every 1 second while popup open
});

marker.on('popupclose', function() {
  // Cleanup interval
  if (this._popupUpdateInterval) {
    clearInterval(this._popupUpdateInterval);
    this._popupUpdateInterval = null;
  }
});
```

**Key insight:** setContent() updates popup DOM without closing/reopening. popupopen/popupclose events manage update interval lifecycle. Alternative: trigger updates only when LocationContext broadcasts new data for the specific userId.

### Anti-Patterns to Avoid

- **Recreating markers on every location update:** Use marker.setLatLng() and conditional setIcon() (Phase 24 pattern). Only recreate if marker doesn't exist.
- **Adding markers individually in a loop:** Use markerClusterGroup.addLayers([...markers]) for bulk operations (better performance per Leaflet.markercluster docs).
- **Inline popup content generation:** Extract to pure function for testability and reusability (generatePopupContent(locationData)).
- **Storing state in marker DOM:** Use marker.options for custom metadata (e.g., userName, isStale flag) instead of parsing HTML.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Marker clustering algorithm | Custom geohash/quadtree clustering | Leaflet.markercluster plugin | Handles viewport culling, zoom-based thresholds, chunked loading, animated transitions, and cluster click events. Edge cases: overlapping markers, zoom level transitions, bulk updates. |
| Relative timestamp formatting | Custom date diff logic | Built-in Intl.RelativeTimeFormat or small utility | "2 min ago" requires unit selection logic, pluralization, edge cases (just now, < 1 min, yesterday). |
| SVG icon management | Base64 data URLs regenerated on state change | DivIcon with CSS class swap | DivIcon allows dynamic HTML content changes via setIcon(). CSS classes separate state logic from rendering. |
| Popup single-instance enforcement | Manual popup tracking and close() calls | Leaflet's autoClose: true option | Built into popup options, automatically closes other popups when new one opens. |

**Key insight:** Leaflet.markercluster solves the "200+ markers performance collapse" blocker from STATE.md. Handles 10,000+ markers in Chrome per official docs. Chunked loading prevents UI freeze during bulk add.

## Common Pitfalls

### Pitfall 1: Zoom-Level Label Visibility Detection

**What goes wrong:** Hard-coded CSS zoom level selectors (.leaflet-container[data-zoom-level="14"]) don't work because Leaflet doesn't set data-zoom-level attribute by default.

**Why it happens:** Misunderstanding Leaflet's zoom event model. Leaflet provides map.on('zoomend') event but doesn't automatically update DOM attributes.

**How to avoid:** Listen to map zoomend event and manually toggle CSS class on map container or individual markers.

**Warning signs:** Labels don't hide/show when zooming in/out.

**Solution:**

```javascript
map.on('zoomend', function() {
  const zoom = map.getZoom();
  const container = map.getContainer();

  // Method 1: Set data attribute for CSS selectors
  container.setAttribute('data-zoom-level', zoom);

  // Method 2: Toggle class for label visibility
  if (zoom >= 15) {
    container.classList.add('show-marker-labels');
  } else {
    container.classList.remove('show-marker-labels');
  }
});
```

CSS:
```css
/* Default: hide labels */
.user-marker__label {
  display: none;
}

/* Show labels at zoom 15+ */
.show-marker-labels .user-marker__label {
  display: block;
}
```

### Pitfall 2: Stale Marker Filter in Cluster Count

**What goes wrong:** Cluster shows count of 10 but only 7 active users (includes 3 stale users).

**Why it happens:** cluster.getChildCount() returns total markers, doesn't filter by staleness. iconCreateFunction must manually filter.

**How to avoid:** Store staleness flag in marker.options and filter getAllChildMarkers() in iconCreateFunction.

**Warning signs:** Cluster count doesn't match visible active markers when zoomed in.

**Solution:**

```javascript
// When creating marker, store isStale flag
const marker = L.marker([lat, lng], {
  icon: divIcon,
  isStale: isStale // Custom option
});

// In iconCreateFunction
iconCreateFunction: function(cluster) {
  const activeCount = cluster.getAllChildMarkers().filter(m => !m.options.isStale).length;
  return L.divIcon({
    html: `<div class="cluster-icon">${activeCount}</div>`,
    className: 'marker-cluster',
    iconSize: L.point(40, 40)
  });
}
```

**Key insight:** Update marker.options.isStale when staleness changes, then call markerClusterGroup.refreshClusters() to recalculate counts.

### Pitfall 3: Team/Channel Data Missing from Location

**What goes wrong:** Popup shows "Team: undefined" because LocationData doesn't include team/channel fields.

**Why it happens:** Location protocol (Phase 21/24) only includes userId, userName, lat/lng, motion, battery. Team/channel data exists in separate DispatchConsole overview endpoint.

**How to avoid:** Enrich LocationContext with team/channel lookup from overview data.

**Warning signs:** Popup displays incomplete identity section.

**Solution:**

```javascript
// In DispatchConsole.jsx, pass overview data to LocationProvider
<LocationProvider eventId={eventId} overview={overview}>
  {/* ... */}
</LocationProvider>

// In LocationContext.jsx, enrich position data
const updateLocation = useCallback((userId, position) => {
  // Lookup user's team and channels from overview
  const userTeam = overview?.teams?.find(t =>
    overview.channels.some(c => c.teamId === t.id && c.members?.includes(userId))
  );

  const userChannels = overview?.channels?.filter(c =>
    c.members?.includes(userId)
  ).map(c => c.name);

  const enrichedPosition = {
    ...position,
    teamName: userTeam?.name || 'Unknown',
    channelNames: userChannels || []
  };

  setLocations(prev => {
    const newMap = new Map(prev);
    newMap.set(userId, enrichedPosition);
    return newMap;
  });
}, [overview]);
```

**Key insight:** DispatchConsole already fetches /api/events/${eventId}/overview which includes teams and channels. Pass this to LocationContext for enrichment.

### Pitfall 4: Popup Content Timing Race Condition

**What goes wrong:** Popup content shows stale data because generatePopupContent() reads from closed-over state instead of latest LocationContext.

**Why it happens:** React closures capture state at render time. setInterval in popupopen reads stale data.

**How to avoid:** Use useRef for latest location data or query LocationContext directly inside interval callback.

**Warning signs:** Popup shows outdated battery % or motion state even though marker icon updated.

**Solution:**

```javascript
// Store latest locations in ref for interval access
const locationsRef = useRef(locations);
useEffect(() => {
  locationsRef.current = locations;
}, [locations]);

marker.on('popupopen', function() {
  const userId = this.options.userId; // Store userId in marker.options

  this._popupUpdateInterval = setInterval(() => {
    const latestData = locationsRef.current.get(userId); // Access latest via ref
    if (latestData) {
      const updatedContent = generatePopupContent(latestData);
      this.getPopup().setContent(updatedContent);
    }
  }, 1000);
});
```

### Pitfall 5: Connection Quality and Latency Derivation

**What goes wrong:** "Connection: undefined" and "Latency: NaN ms" because LocationData doesn't include connection quality or latency fields.

**Why it happens:** Location protocol includes networkType ('wifi' | 'cellular' | null) but no quality/latency metrics. Server doesn't measure RTT for location updates.

**How to avoid:** Derive connection quality from available fields (networkType, powerSaveMode, timestamp freshness) or display "N/A" placeholders.

**Warning signs:** Popup shows undefined values in Status section.

**Solution:**

```javascript
function deriveConnectionQuality(position) {
  // Heuristic: wifi = "Good", cellular = "Fair", null = "Unknown"
  if (position.networkType === 'wifi') return 'Good';
  if (position.networkType === 'cellular') {
    // Degrade to "Fair" if power save mode enabled (radio throttled)
    return position.powerSaveMode ? 'Fair' : 'Good';
  }
  return 'Unknown';
}

function deriveLatency(position) {
  // No true latency available — use timestamp freshness as proxy
  const ageMs = Date.now() - new Date(position.timestamp).getTime();
  if (ageMs < 5000) return '< 5s'; // Fresh update
  if (ageMs < 30000) return '< 30s';
  return '> 30s';
}

const popupContent = `
  <div>Connection: ${deriveConnectionQuality(position)}</div>
  <div>Latency: ${deriveLatency(position)}</div>
`;
```

**Key insight:** True latency requires RTT measurement (not in current protocol). Use timestamp freshness as proxy or display "N/A" with note "Location update delay, not network latency".

## Code Examples

Verified patterns from official sources:

### Two-Tier Interaction Setup

```javascript
// Source: https://leafletjs.com/reference.html
const marker = L.marker([lat, lng], { icon: divIcon, userId: userId });

// Tooltip: hover summary (permanent: false = only on hover)
marker.bindTooltip(`
  <strong>${position.userName}</strong><br>
  Team: ${position.teamName}<br>
  Channels: ${position.channelNames.join(', ')}
`, {
  permanent: false,
  direction: 'top',
  offset: [0, -10],
  className: 'marker-tooltip'
});

// Popup: click detail card
marker.bindPopup(generatePopupContent(position), {
  maxWidth: 300,
  closeButton: true,
  autoClose: true, // Single popup only
  closeOnClick: false // Prevent map click from closing
});

// Live updates while popup open
marker.on('popupopen', function() {
  this._popupUpdateInterval = setInterval(() => {
    const latest = locationsRef.current.get(userId);
    if (latest) {
      this.getPopup().setContent(generatePopupContent(latest));
    }
  }, 1000);
});

marker.on('popupclose', function() {
  clearInterval(this._popupUpdateInterval);
});
```

### Staleness Detection and CSS Treatment

```javascript
// Compute staleness (5-minute threshold)
const now = Date.now();
const age = now - new Date(position.timestamp).getTime();
const isStale = age > 5 * 60 * 1000;

// Apply stale class to DivIcon
const divIcon = L.divIcon({
  className: `user-marker user-marker--${position.motionState} ${isStale ? 'user-marker--stale' : ''}`,
  html: generateMarkerHTML(position),
  iconSize: [32, 40],
  iconAnchor: [16, 40]
});

// Update existing marker
if (existingMarker) {
  existingMarker.setIcon(divIcon);
  existingMarker.options.isStale = isStale; // For cluster count filtering
}
```

```css
/* Source: https://developer.mozilla.org/en-US/docs/Web/CSS/filter (grayscale) */
.user-marker--stale {
  filter: grayscale(100%) opacity(0.6);
  transition: none; /* Instant recovery per user decision */
}
```

### Cluster Group with Active-Only Count

```javascript
// Source: https://github.com/Leaflet/Leaflet.markercluster
const markerClusterGroup = L.markerClusterGroup({
  iconCreateFunction: function(cluster) {
    // Count only active (non-stale) markers
    const activeCount = cluster.getAllChildMarkers().filter(m => !m.options.isStale).length;

    return L.divIcon({
      html: `<div class="cluster-icon">${activeCount}</div>`,
      className: 'marker-cluster',
      iconSize: L.point(40, 40)
    });
  },

  disableClusteringAtZoom: 16, // Show individuals at zoom 16+
  chunkedLoading: true,
  chunkInterval: 200,
  chunkDelay: 50,
  animate: true,
  animateAddingMarkers: false,
  spiderfyOnMaxZoom: false,
  zoomToBoundsOnClick: true,
  maxClusterRadius: 80
});

// Bulk add markers (better performance)
markerClusterGroup.addLayers(markersArray);
map.addLayer(markerClusterGroup);

// Refresh clusters when staleness changes
// (Call after updating marker.options.isStale)
markerClusterGroup.refreshClusters();
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Canvas renderer for 200+ markers | Leaflet.markercluster plugin with chunked loading | Leaflet.markercluster v1.0 (2016) | Plugin handles viewport culling, zoom thresholds, animated transitions — no need for custom canvas rendering |
| Image icons (PNG/SVG files) | DivIcon with inline SVG | Leaflet 1.0+ (2016) | DivIcon allows dynamic HTML content, CSS styling, no HTTP requests for icon files |
| Manual popup close tracking | autoClose: true option | Leaflet 1.0+ | Built-in single-popup enforcement |
| Data URLs for dynamic icons | CSS class swap with DivIcon | Modern practice | Avoids regenerating data URLs on state change |

**Deprecated/outdated:**
- Leaflet 0.7.x API: marker.bindPopup(content, options) signature unchanged, but Popup class gained autoClose option in 1.0
- Marker.cluster plugin (old name): Renamed to Leaflet.markercluster, npm package is `leaflet.markercluster`

## Open Questions

1. **Exact zoom threshold for label visibility**
   - What we know: User wants "zoom-dependent name labels" hidden when zoomed out, shown when zoomed in close
   - What's unclear: Specific zoom level (13? 14? 15?)
   - Recommendation: Start with zoom 15 (street-level detail), make configurable via MapView prop for user testing

2. **Exact zoom threshold for cluster disabling**
   - What we know: User wants "disable clustering at high zoom — always show individual markers when zoomed in close"
   - What's unclear: Specific zoom level for disableClusteringAtZoom
   - Recommendation: Use zoom 16 (building-level detail, Leaflet.markercluster default is 18), aligns with label visibility threshold

3. **Team/channel data availability in LocationContext**
   - What we know: DispatchConsole fetches /api/events/${eventId}/overview with teams and channels arrays
   - What's unclear: Whether overview includes user-to-team and user-to-channel mappings
   - Recommendation: Inspect overview response structure in Phase 25 planning. If mappings missing, add server-side enrichment to LOCATION_QUERY response.

4. **Connection quality and latency fields**
   - What we know: LocationData includes networkType ('wifi' | 'cellular' | null) but no connection quality or latency
   - What's unclear: Whether to derive quality from networkType or add new protocol fields
   - Recommendation: Derive quality heuristically (wifi=Good, cellular=Fair, null=Unknown) with note in popup "Estimated from network type". Future phase can add RTT measurement.

5. **Cluster hover tooltip truncation limit**
   - What we know: User wants "up to ~10, then '...and N more'"
   - What's unclear: Exact limit (10? 12? 8?)
   - Recommendation: Use maxDisplay = 10 as stated, test with real data

## Sources

### Primary (HIGH confidence)

- [Leaflet Reference Documentation](https://leafletjs.com/reference.html) - bindPopup(), bindTooltip(), DivIcon, Popup/Tooltip options
- [Leaflet.markercluster GitHub](https://github.com/Leaflet/Leaflet.markercluster) - Installation, options, iconCreateFunction, performance recommendations
- [MDN CSS filter: grayscale()](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Values/filter-function/grayscale) - Staleness visual treatment
- Phase 24 implementation files - MapView.jsx, LocationContext.jsx, existing marker rendering patterns

### Secondary (MEDIUM confidence)

- [Leaflet Marker Clustering Guide - Leigh Halliday](https://www.leighhalliday.com/leaflet-clustering) - Practical clustering examples
- [Optimizing Leaflet Performance with Large Markers - Medium](https://medium.com/@silvajohnny777/optimizing-leaflet-performance-with-a-large-number-of-markers-0dea18c2ec99) - Performance best practices verified against official docs
- [Handling Dynamic Leaflet Popup Content - runebook.dev](https://runebook.dev/en/articles/leaflet/index/popup-contentupdate) - setContent() and popupopen event patterns

### Tertiary (LOW confidence)

- [Icons8 Person Walking Icons](https://icons8.com/icons/set/person-walking) - SVG pictogram references (need custom design to match orange theme)
- [SVG Repo Person Walking](https://www.svgrepo.com/svg/362953/person-simple-walk-bold) - SVG pictogram examples

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Leaflet.markercluster is official plugin, well-documented, production-proven for 200+ markers
- Architecture: HIGH - Patterns verified from official Leaflet docs and plugin README
- Pitfalls: MEDIUM - Team/channel enrichment and connection quality derivation need validation during implementation

**Research date:** 2026-02-17
**Valid until:** 2026-03-17 (30 days for stable libraries, Leaflet 1.9.4 and markercluster 1.5.3 are mature)
