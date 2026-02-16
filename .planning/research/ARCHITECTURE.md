# Architecture Research: Dispatch Map Integration

**Domain:** React 18 web UI with Leaflet map integration for real-time location tracking
**Researched:** 2026-02-16
**Confidence:** HIGH

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                       DISPATCH CONSOLE                               │
├─────────────────────┬───────────────────────────────────────────────┤
│   CHANNELS PANEL    │           MAP PANEL                            │
│   (existing)        │           (new)                                │
├─────────────────────┼───────────────────────────────────────────────┤
│ ChannelProvider     │  MapContainer (Leaflet)                        │
│   ├─ ChannelGrid    │    ├─ TileLayer                                │
│   └─ DispatchCards  │    ├─ UserMarker[] (DivIcon)                   │
│                     │    ├─ MapController (useEffect hooks)          │
│                     │    └─ MarkerPopup (selected user details)      │
├─────────────────────┼───────────────────────────────────────────────┤
│                     │  LocationProvider (separate context)           │
│   (uses existing)   │    ├─ positions: Map<userId, LocationData>     │
│                     │    ├─ WebSocket listener (LOCATION_BROADCAST)  │
│                     │    └─ batch update (requestAnimationFrame)     │
└─────────────────────┴───────────────────────────────────────────────┘
                                    ↑
                            WebSocket messages
                                    │
┌─────────────────────────────────────────────────────────────────────┐
│                          SERVER LAYER                                │
│  LocationBroadcaster → dispatch users (role filter)                  │
│  LocationStore (SQLite) → query history, initial load                │
└─────────────────────────────────────────────────────────────────────┘
                                    ↑
                            WebSocket messages
                                    │
┌─────────────────────────────────────────────────────────────────────┐
│                         ANDROID CLIENT                               │
│  LocationManager → LOCATION_UPDATE (with battery %)                  │
│  MotionDetector → adaptive intervals (STILL/WALKING/DRIVING)         │
└─────────────────────────────────────────────────────────────────────┘
```

## Recommended Project Structure

### New Files

```
web-ui/src/
├── context/
│   ├── ChannelContext.jsx          # [EXISTS] Channel state
│   └── LocationContext.jsx         # [NEW] Location state (separate from channels)
├── components/
│   ├── DispatchChannelCard.jsx     # [EXISTS] Channel monitoring
│   ├── ChannelGrid.jsx             # [EXISTS] Team-grouped channels
│   ├── DispatchMap.jsx             # [NEW] Leaflet map container
│   ├── UserMarker.jsx              # [NEW] Custom DivIcon marker with motion state
│   └── MapController.jsx           # [NEW] useMap hook consumer for side effects
├── hooks/
│   ├── useChannelConnection.js     # [EXISTS] WebRTC per channel
│   ├── useLocationUpdates.js       # [NEW] WebSocket location listener
│   └── useMapBounds.js             # [NEW] Auto-fit all markers or remember zoom
└── pages/
    └── DispatchConsole.jsx         # [MODIFIED] Split layout wrapper
```

### Modified Files

```
web-ui/src/
├── pages/
│   └── DispatchConsole.jsx         # Add CSS Grid: "channels map" layout
├── theme/
│   └── connectvoice.css            # Add .dispatch-console--split, .map-panel
└── package.json                    # Add react-leaflet, leaflet dependencies
```

### Server Files (Modified)

```
src/server/location/
├── LocationBroadcaster.ts          # [MODIFIED] Add batteryPercent field to broadcast
└── types.ts                        # [MODIFIED] Add batteryPercent?: number to LocationData
```

### Android Files (Modified)

```
android/app/src/main/java/com/voiceping/android/data/location/
├── LocationManager.kt              # [MODIFIED] Include battery % in LOCATION_UPDATE
└── LocationUpdate.kt               # [NEW or MODIFIED] Add batteryPercent field
```

### Structure Rationale

- **LocationContext separate from ChannelContext:** Channel state and location state update at different frequencies (PTT events vs. location updates). Separating them prevents unnecessary re-renders of channel cards when location updates arrive. This follows React best practices where "state more logically separated and located closer to where it matters" avoids Context performance problems.

- **Custom hooks (useLocationUpdates, useMapBounds):** Encapsulate WebSocket listener and map interaction logic for reusability and testing. React Leaflet requires useMap hook consumers to be descendants of MapContainer, so MapController component wraps these hooks.

- **Component splitting (DispatchMap, UserMarker, MapController):** DispatchMap contains MapContainer, UserMarker renders individual markers with DivIcon for CSS styling, MapController handles side effects (auto-fit, marker updates). This matches React Leaflet's component model where React renders a `<div>` and Leaflet renders layers imperatively.

## Architectural Patterns

### Pattern 1: Separate LocationContext (Not Extending ChannelContext)

**What:** Create a standalone LocationProvider with its own state Map for location data, parallel to ChannelProvider.

**When to use:** When two state domains update at different frequencies and are consumed by different components. Channel state updates on PTT events (low frequency), location updates on motion tracking (medium-high frequency).

**Trade-offs:**
- **Pros:** Prevents channel card re-renders on location updates, cleaner separation of concerns, easier testing
- **Cons:** Two providers to wrap, slightly more boilerplate

**Example:**
```typescript
// LocationContext.jsx
import React, { createContext, useContext, useState, useEffect, useRef } from 'react';

const LocationContext = createContext(null);

export const LocationProvider = ({ children }) => {
  // Map<userId, LocationPosition> for O(1) updates
  const [positions, setPositions] = useState(new Map());
  const batchQueueRef = useRef([]);
  const rafIdRef = useRef(null);

  // Batch location updates with requestAnimationFrame
  const queueLocationUpdate = (userId, locationData) => {
    batchQueueRef.current.push({ userId, locationData });

    if (!rafIdRef.current) {
      rafIdRef.current = requestAnimationFrame(() => {
        setPositions(prev => {
          const next = new Map(prev);
          batchQueueRef.current.forEach(({ userId, locationData }) => {
            next.set(userId, locationData);
          });
          return next;
        });
        batchQueueRef.current = [];
        rafIdRef.current = null;
      });
    }
  };

  const value = { positions, queueLocationUpdate };
  return <LocationContext.Provider value={value}>{children}</LocationContext.Provider>;
};

export const useLocation = () => {
  const context = useContext(LocationContext);
  if (!context) throw new Error('useLocation must be within LocationProvider');
  return context;
};
```

### Pattern 2: CSS Grid Split Layout

**What:** Use CSS Grid to create a two-column layout: channels panel (left), map panel (right), with responsive breakpoints.

**When to use:** When adding a persistent sidebar to an existing full-width page. Matches existing CSS Grid usage in DispatchConsole (header, stats bar, channel grid).

**Trade-offs:**
- **Pros:** Native responsive behavior, simple to implement, no JS layout calculation
- **Cons:** Limited to predefined breakpoints, less dynamic than flexbox for resizing

**Example:**
```css
/* connectvoice.css */
.dispatch-console--split {
  display: grid;
  grid-template-columns: 1fr 1fr; /* 50/50 split */
  gap: 16px;
  height: calc(100vh - 160px); /* Account for header + stats */
}

.dispatch-console__channels-panel {
  overflow-y: auto;
  border-right: 1px solid var(--cv-border);
}

.dispatch-console__map-panel {
  position: relative; /* For Leaflet absolute positioning */
  overflow: hidden;
}

/* Responsive: stack vertically on narrow screens */
@media (max-width: 1200px) {
  .dispatch-console--split {
    grid-template-columns: 1fr;
    grid-template-rows: 400px 1fr; /* Map on top, channels below */
  }

  .dispatch-console__channels-panel {
    border-right: none;
    border-top: 1px solid var(--cv-border);
  }
}
```

### Pattern 3: DivIcon with Motion State CSS Classes

**What:** Use Leaflet's DivIcon to render markers as HTML divs with CSS classes for motion state (STILL, WALKING, DRIVING) and stale indicator.

**When to use:** When markers need custom styling beyond what's possible with image icons. Allows CSS animations, pseudo-elements, and dynamic styling.

**Trade-offs:**
- **Pros:** Full CSS control, easy to add badges (battery %), can use pseudo-elements for pulses
- **Cons:** Slightly slower than canvas rendering for 1000+ markers (not a concern for dispatch use case)

**Example:**
```jsx
// UserMarker.jsx
import { Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import { useMemo } from 'react';

const UserMarker = ({ position }) => {
  const { userId, username, latitude, longitude, motionState, isStale, batteryPercent } = position;

  const icon = useMemo(() => {
    const motionClass = `marker--${motionState.toLowerCase()}`;
    const staleClass = isStale ? 'marker--stale' : '';
    const lowBatteryClass = batteryPercent < 20 ? 'marker--low-battery' : '';

    return L.divIcon({
      className: `user-marker ${motionClass} ${staleClass} ${lowBatteryClass}`,
      html: `
        <div class="marker-pin"></div>
        <div class="marker-label">${username}</div>
        ${batteryPercent ? `<div class="marker-battery">${batteryPercent}%</div>` : ''}
      `,
      iconSize: [40, 50],
      iconAnchor: [20, 50],
      popupAnchor: [0, -50]
    });
  }, [username, motionState, isStale, batteryPercent]);

  return (
    <Marker position={[latitude, longitude]} icon={icon}>
      <Popup>
        <strong>{username}</strong><br/>
        Motion: {motionState}<br/>
        Battery: {batteryPercent}%<br/>
        Last update: {new Date(position.timestamp).toLocaleTimeString()}
      </Popup>
    </Marker>
  );
};
```

```css
/* Map marker styles */
.user-marker {
  font-family: var(--cv-font);
  font-size: 11px;
  font-weight: 600;
  color: var(--cv-text);
  text-align: center;
}

.marker-pin {
  width: 24px;
  height: 24px;
  border-radius: 50% 50% 50% 0;
  background: var(--cv-ok);
  position: absolute;
  left: 50%;
  transform: translateX(-50%) rotate(-45deg);
  border: 2px solid var(--cv-surface);
  box-shadow: var(--cv-glow);
}

.marker--driving .marker-pin {
  background: var(--cv-accent);
  animation: pulse-driving 1.5s ease-in-out infinite;
}

.marker--walking .marker-pin {
  background: var(--cv-warn);
}

.marker--still .marker-pin {
  background: var(--cv-ok);
}

.marker--stale .marker-pin {
  background: var(--muted);
  opacity: 0.5;
  box-shadow: none;
}

.marker-label {
  position: absolute;
  top: -20px;
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  background: rgba(12, 18, 32, 0.9);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--cv-border);
}

.marker-battery {
  position: absolute;
  top: 28px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--cv-surface-2);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 9px;
  border: 1px solid var(--cv-border);
}

.marker--low-battery .marker-battery {
  background: var(--cv-alert);
  color: white;
}

@keyframes pulse-driving {
  0%, 100% { transform: translateX(-50%) rotate(-45deg) scale(1); }
  50% { transform: translateX(-50%) rotate(-45deg) scale(1.1); }
}
```

### Pattern 4: Batch Marker Updates with requestAnimationFrame

**What:** Queue incoming location updates and batch-apply them to React state on next animation frame to prevent layout thrashing.

**When to use:** When receiving high-frequency WebSocket updates (10-30 per second) that would trigger excessive re-renders. Essential for real-time location tracking.

**Trade-offs:**
- **Pros:** Reduces re-renders from N updates/sec to 60 updates/sec max, smooth 60fps rendering
- **Cons:** Adds ~16ms latency (one frame), slightly more complex than direct setState

**Example:**
```jsx
// useLocationUpdates.js
import { useEffect, useRef } from 'react';
import { useLocation } from '../context/LocationContext';

export const useLocationUpdates = (ws) => {
  const { queueLocationUpdate } = useLocation();

  useEffect(() => {
    if (!ws) return;

    const handleMessage = (event) => {
      const msg = JSON.parse(event.data);

      if (msg.type === 'location-broadcast') {
        const { userId, latitude, longitude, accuracy, speed, heading,
                motionState, timestamp, batteryPercent } = msg.data;

        // Queue update (batched with requestAnimationFrame in LocationContext)
        queueLocationUpdate(userId, {
          userId,
          username: msg.data.username || userId, // Server should include username
          latitude,
          longitude,
          accuracy,
          speed,
          heading,
          motionState,
          timestamp,
          batteryPercent,
          isStale: false // Server calculates, or client computes from timestamp
        });
      }
    };

    ws.addEventListener('message', handleMessage);
    return () => ws.removeEventListener('message', handleMessage);
  }, [ws, queueLocationUpdate]);
};
```

### Pattern 5: Protocol Versioning with Optional Fields

**What:** Add new fields (batteryPercent) as optional to maintain backward compatibility with older clients/servers.

**When to use:** When extending WebSocket message schemas in production systems with staged rollouts.

**Trade-offs:**
- **Pros:** No breaking changes, graceful degradation, staged deployment possible
- **Cons:** Must handle undefined values, can't rely on field being present

**Example:**
```typescript
// types.ts (server)
export interface LocationData {
  userId: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  speed: number | null;
  heading: number | null;
  motionState: 'still' | 'walking' | 'driving' | 'unknown';
  timestamp: string; // ISO8601 from client
  batteryPercent?: number; // [NEW] Optional for backward compatibility
}
```

```kotlin
// LocationUpdate.kt (Android)
data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float?,
    val heading: Float?,
    val motionState: MotionState,
    val timestamp: String,
    val batteryPercent: Int? = null // [NEW] Optional, defaults to null
) {
    fun toJsonObject(): JsonObject {
        return JsonObject().apply {
            addProperty("latitude", latitude)
            addProperty("longitude", longitude)
            addProperty("accuracy", accuracy)
            if (speed != null) addProperty("speed", speed)
            if (heading != null) addProperty("heading", heading)
            addProperty("motionState", motionState.name.lowercase())
            addProperty("timestamp", timestamp)
            if (batteryPercent != null) addProperty("batteryPercent", batteryPercent) // Only add if present
        }
    }
}
```

## Data Flow

### Real-Time Location Broadcast Flow

```
[Android LocationManager]
    ↓ (every 30s-5min based on motion)
getBatteryLevel() → LOCATION_UPDATE with batteryPercent
    ↓ (WebSocket to server)
[Server SignalingHandler]
    ↓ (validate, store in LocationStore)
LocationBroadcaster.broadcastLocation()
    ↓ (filter to dispatch users only)
sendToDispatchUsers({ type: 'location-broadcast', data: { userId, lat, lng, battery%, ... }})
    ↓ (WebSocket to all dispatch console clients)
[Web DispatchConsole]
    ↓ (useLocationUpdates hook)
queueLocationUpdate(userId, locationData)
    ↓ (batched with requestAnimationFrame)
LocationContext.setPositions(Map<userId, LocationData>)
    ↓ (React re-render)
DispatchMap renders UserMarker[]
    ↓ (Leaflet updates DOM)
Map shows updated marker position with battery badge
```

### Initial Load Flow (Query Historical Positions)

```
[Web DispatchConsole mounts]
    ↓ (useEffect on mount)
WebSocket send({ type: 'location-query' })
    ↓
[Server LocationBroadcaster]
    ↓
getAllLatestPositions() → LocationPosition[] with isStale flags
    ↓
sendToDispatchUser({ type: 'location-snapshot', positions: [...] })
    ↓
[Web LocationContext]
    ↓
setPositions(new Map(positions.map(p => [p.userId, p])))
    ↓
DispatchMap renders all markers (including stale ones with opacity)
```

### Split Layout Render Flow

```
[DispatchConsole.jsx]
    ↓
<div className="dispatch-console--split">
  <div className="dispatch-console__channels-panel">
    <ChannelProvider> {/* Existing context */}
      <ChannelGrid {...props} />
    </ChannelProvider>
  </div>
  <div className="dispatch-console__map-panel">
    <LocationProvider> {/* New separate context */}
      <DispatchMap />
    </LocationProvider>
  </div>
</div>
```

## Component Responsibilities

| Component | Responsibility | Integration Points |
|-----------|----------------|-------------------|
| **DispatchConsole** (MODIFIED) | Wrap both panels in CSS Grid split layout, manage shared WebSocket connection | Passes ws instance to both ChannelGrid and DispatchMap |
| **LocationProvider** (NEW) | Manage Map<userId, LocationData>, batch updates with rAF, expose useLocation hook | WebSocket listener for LOCATION_BROADCAST, LOCATION_SNAPSHOT |
| **DispatchMap** (NEW) | Leaflet MapContainer with TileLayer, render UserMarker[] from positions Map | Consumes LocationContext.positions |
| **UserMarker** (NEW) | Leaflet Marker with custom DivIcon, CSS classes for motion state/stale/battery | Receives LocationPosition props |
| **MapController** (NEW) | useMap consumer for side effects: auto-fit bounds, handle query/broadcast | Calls map.fitBounds() on initial load |
| **useLocationUpdates** (NEW) | WebSocket message listener hook, parse LOCATION_BROADCAST and queue updates | Calls LocationContext.queueLocationUpdate |
| **useMapBounds** (NEW) | Auto-fit all markers or remember last zoom/center in localStorage | Calls map.fitBounds() or map.setView() |
| **LocationBroadcaster** (MODIFIED) | Add batteryPercent to broadcast message schema | Server-side component, no React integration |
| **LocationManager.kt** (MODIFIED) | Include getBatteryLevel() in LOCATION_UPDATE message | Android client, sends to server WebSocket |

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 10-50 users | Current architecture sufficient. Real-time updates with rAF batching handles 50 updates/sec smoothly at 60fps. Map renders all markers (no clustering needed). |
| 50-200 users | Add viewport-based rendering: only render markers within map bounds + small buffer. Remove markers outside viewport to reduce DOM nodes. Still no clustering needed if using DivIcon efficiently. |
| 200-500 users | Implement marker clustering (react-leaflet-cluster or supercluster). Batch updates at server (LocationBroadcaster sends digest every 2-5 seconds instead of individual broadcasts). Consider Canvas rendering for marker icons (faster than DivIcon but less flexible styling). |
| 500+ users | Move to Canvas rendering with manual marker drawing (Leaflet.Canvas or custom layer). Server-side clustering (only send cluster centroids). Add map-based filters (show only DRIVING users, or by team). Consider WebSocket message compression (already using notepack.io for PTT). |

### Scaling Priorities

1. **First bottleneck (200+ markers):** DOM node count. Each DivIcon adds HTML to DOM. **Fix:** Add clustering with react-leaflet-cluster, shows count badge for nearby markers.

2. **Second bottleneck (500+ broadcasts/sec):** WebSocket message flood. With 500 users moving, dispatch console receives 500 updates every 30-60 seconds = ~10/sec average, but bursts higher. **Fix:** Server-side aggregation (LocationBroadcaster batches updates every 2 seconds, sends digest array).

## Anti-Patterns

### Anti-Pattern 1: Extending ChannelContext with Location State

**What people do:** Add location data to existing ChannelContext to avoid creating a second provider.

**Why it's wrong:** ChannelContext updates trigger re-renders of all DispatchChannelCards. Adding high-frequency location updates causes channel cards to re-render unnecessarily, degrading PTT UI responsiveness. "When a React Context.Provider gets a new value, all the components that consume that value are updated and have to render."

**Do this instead:** Separate LocationProvider consumed only by map components. Channels and locations are independent concerns with different update frequencies.

### Anti-Pattern 2: Individual setState Calls for Each Location Update

**What people do:** Call setPositions() immediately in WebSocket message handler for each LOCATION_BROADCAST.

**Why it's wrong:** Triggers React re-render for every WebSocket message. With 10-50 users sending updates, causes 10-50 re-renders/sec, layout thrashing, dropped frames, janky map panning.

**Do this instead:** Batch updates with requestAnimationFrame. Queue updates in array, apply all on next frame (max 60 renders/sec). Reduces re-renders by 10-50x with 16ms latency trade-off.

### Anti-Pattern 3: Using Image Icons for Motion State Indicators

**What people do:** Pre-create 12 PNG marker icons (STILL/WALKING/DRIVING × fresh/stale × low-battery/normal), load with L.icon().

**Why it's wrong:** Inflexible (CSS theme changes require regenerating PNGs), larger bundle size, no smooth animations, hard to add dynamic text (username, battery %), no pseudo-elements for pulse effects.

**Do this instead:** Use L.divIcon() with className. CSS handles motion state colors, stale opacity, battery badge, username label, pulse animations. Theme changes are pure CSS. Battery % is dynamic HTML.

### Anti-Pattern 4: Fetching All Historical Locations on Mount

**What people do:** Query LocationStore for all historical positions (24 hours of data) on DispatchConsole mount to show "trails."

**Why it's wrong:** Sends MB of data over WebSocket, slows initial load, clutters map with stale positions (users not currently active). LocationStore grows unbounded if not pruned.

**Do this instead:** Query only latest position per user (LocationBroadcaster.getAllLatestPositions()). Mark stale (> 5 minutes) with isStale flag and render with opacity. If trails are desired later, add as opt-in feature with time range selector.

### Anti-Pattern 5: Tight Coupling Map to Channel Selection

**What people do:** Click marker → auto-join that user's channel, or click channel card → center map on user.

**Why it's wrong:** Confuses dispatch monitoring (passive observation) with active participation. Dispatch users monitor all channels simultaneously; auto-joining breaks multi-channel monitoring. Tight coupling makes features harder to test independently.

**Do this instead:** Keep map and channels independent. Marker click shows popup with user details (battery, last update, motion state). Optional: highlight user's channel card with subtle border, but don't auto-join. Map is location awareness, channels are audio monitoring.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| **Leaflet** | React component wrappers via react-leaflet | MapContainer must be parent of all Leaflet components. useMap hook only works inside MapContainer descendants. |
| **OpenStreetMap Tiles** | TileLayer with attribution | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` — free tier, rate-limited. Consider Mapbox/Maptiler for production. |
| **WebSocket (existing)** | Shared instance passed to LocationProvider | Reuse existing ws from DispatchConsole, don't create second connection. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| **DispatchConsole ↔ LocationProvider** | WebSocket instance prop, no state coupling | DispatchConsole creates ws, passes to LocationProvider. Providers are siblings. |
| **LocationProvider ↔ DispatchMap** | React Context (useLocation hook) | DispatchMap consumes positions Map, doesn't write to it. |
| **DispatchMap ↔ UserMarker** | Props (LocationPosition) | UserMarker is pure component, receives position data via props. |
| **Server LocationBroadcaster ↔ Web LocationContext** | WebSocket messages (LOCATION_BROADCAST, LOCATION_SNAPSHOT) | Server pushes updates, client pulls initial snapshot. No REST API. |
| **Android LocationManager ↔ Server** | WebSocket LOCATION_UPDATE message | Android includes batteryPercent (optional field). Server validates, stores, broadcasts. |

## Build Order (Dependency-Driven)

Integration should follow dependency order to avoid breaking changes and enable incremental testing:

### Phase 1: Server + Android (Backend-First)

**Goal:** Extend protocol with battery % field, validate backward compatibility.

1. **Server: Extend LocationData type** (types.ts)
   - Add `batteryPercent?: number` to LocationData interface
   - No breaking changes (optional field)

2. **Server: Modify LocationBroadcaster** (LocationBroadcaster.ts)
   - Include batteryPercent in broadcast message if present
   - Test: Old clients ignore unknown field, new clients receive it

3. **Android: Add battery % to LocationUpdate** (LocationManager.kt)
   - Call getBatteryLevel() in emitLocationUpdate()
   - Add batteryPercent to LOCATION_UPDATE JSON
   - Test: Verify server receives and stores battery %

**Deliverable:** Protocol extended, backward-compatible. Server broadcasts battery %.

### Phase 2: Web Dependencies (React Setup)

**Goal:** Install Leaflet, create split layout without breaking existing UI.

4. **Install dependencies** (package.json)
   - `npm install react-leaflet leaflet`
   - `npm install -D @types/leaflet` (if using TypeScript)

5. **Add CSS Grid split layout** (connectvoice.css, DispatchConsole.jsx)
   - Define `.dispatch-console--split` grid layout
   - Wrap existing ChannelGrid in `.dispatch-console__channels-panel`
   - Add empty `.dispatch-console__map-panel` div (placeholder)
   - Test: Existing channel grid still works, map panel shows as empty space

**Deliverable:** Layout structure ready, no map yet. Channels work as before.

### Phase 3: Map Foundation (Leaflet Integration)

**Goal:** Render basic Leaflet map with OpenStreetMap tiles.

6. **Create DispatchMap component** (DispatchMap.jsx)
   - MapContainer with default center/zoom
   - TileLayer with OpenStreetMap
   - Test: Map renders, can pan/zoom

7. **Add to DispatchConsole** (DispatchConsole.jsx)
   - Render DispatchMap in map panel
   - Test: Map visible alongside channel grid

**Deliverable:** Static map displays, no markers yet.

### Phase 4: Location State (Context + WebSocket)

**Goal:** Manage location state separately from channel state.

8. **Create LocationContext** (LocationContext.jsx)
   - LocationProvider with positions Map state
   - queueLocationUpdate with requestAnimationFrame batching
   - useLocation hook
   - Test: Context mounts, no errors

9. **Create useLocationUpdates hook** (useLocationUpdates.js)
   - Listen for LOCATION_BROADCAST WebSocket messages
   - Parse and queue updates via LocationContext
   - Test: Mock WebSocket messages update positions Map

10. **Implement LOCATION_QUERY on mount** (MapController.jsx)
    - Send LOCATION_QUERY on DispatchMap mount
    - Handle LOCATION_SNAPSHOT response
    - Populate initial positions Map
    - Test: Initial positions load on page refresh

**Deliverable:** Location state management works, WebSocket integration complete.

### Phase 5: Marker Rendering (DivIcon + Styling)

**Goal:** Display user markers on map with motion state indicators.

11. **Create UserMarker component** (UserMarker.jsx)
    - Leaflet Marker with DivIcon
    - Motion state CSS classes
    - Stale indicator (opacity)
    - Battery % badge
    - Popup with user details
    - Test: Markers render, click shows popup

12. **Add marker CSS** (connectvoice.css)
    - `.user-marker`, `.marker-pin` base styles
    - `.marker--driving`, `.marker--walking`, `.marker--still` colors
    - `.marker--stale` opacity
    - `.marker--low-battery` red badge
    - `@keyframes pulse-driving` animation
    - Test: Markers styled correctly, animations smooth

13. **Render markers in DispatchMap** (DispatchMap.jsx)
    - Map over positions from LocationContext
    - Render UserMarker for each position
    - Test: Multiple markers display, update in real-time

**Deliverable:** Markers render with motion state, battery %, stale indicators.

### Phase 6: Polish + UX (Auto-Fit, Performance)

**Goal:** Improve map UX, optimize performance.

14. **Create MapController for auto-fit** (MapController.jsx)
    - useMap hook to access Leaflet map instance
    - fitBounds on initial load (all markers)
    - Remember zoom/center in localStorage
    - Test: Map auto-fits markers, remembers zoom on refresh

15. **Add useMapBounds hook** (useMapBounds.js)
    - Toggle between auto-fit and manual control
    - Persist preference in localStorage
    - Test: User can disable auto-fit, manually zoom/pan

16. **Performance testing** (existing components)
    - Simulate 50+ users sending updates
    - Verify rAF batching keeps 60fps
    - Check React DevTools profiler for unnecessary re-renders
    - Test: Map smooth with 50 simultaneous updates

**Deliverable:** Production-ready map with good UX and performance.

## Dependencies Summary

```
Server batteryPercent (Phase 1)
    ↓
Android batteryPercent (Phase 1)
    ↓
Web layout split (Phase 2)
    ↓
Web map foundation (Phase 3)
    ↓
Web LocationContext + WebSocket (Phase 4)
    ↓
Web markers + styling (Phase 5)
    ↓
Web auto-fit + performance (Phase 6)
```

**Why this order:**
- Server/Android first: Ensures data is available before web consumes it
- Layout split before map: Prevents breaking existing UI, validates CSS structure
- Map foundation before state: Tests Leaflet integration in isolation
- State before markers: Markers need positions to render
- Polish last: Performance tuning requires full stack to test end-to-end

## Sources

- [React Leaflet Documentation](https://react-leaflet.js.org/)
- [React Leaflet Introduction](https://react-leaflet.js.org/docs/start-introduction/)
- [Optimizing Leaflet Performance with Large Number of Markers](https://medium.com/@silvajohnny777/optimizing-leaflet-performance-with-a-large-number-of-markers-0dea18c2ec99)
- [Leaflet Realtime Plugin](https://github.com/perliedman/leaflet-realtime)
- [Leaflet Developer's Guide to High-Performance Map Visualizations in React](https://andrejgajdos.com/leaflet-developer-guide-to-high-performance-map-visualizations-in-react/)
- [React Context is Not a State Management Tool](https://blog.isquaredsoftware.com/2021/01/context-redux-differences/)
- [Application State Management with React](https://kentcdodds.com/blog/application-state-management-with-react)
- [React State Management: Redux vs Context API](https://codeparrot.ai/blogs/react-state-management-redux-vs-context-api)
- [MDN: Realizing Common Layouts Using Grids](https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Grid_layout/Common_grid_layouts)
- [CSS Grid Complete Guide](https://css-tricks.com/snippets/css/complete-guide-grid/)
- [Creating Custom Leaflet Marker Icon with DivIcon](https://www.drupal.org/node/2554137)
- [Leaflet Custom Icons Example](https://leafletjs.com/examples/custom-icons/)
- [Geoapify: Map Marker Icon with HTML and CSS](https://www.geoapify.com/create-custom-map-marker-icon/)
- [Optimizing Real-Time Performance: WebSockets and React.js Part II](https://medium.com/@SanchezAllanManuel/optimizing-real-time-performance-websockets-and-react-js-integration-part-ii-4a3ada319630)
- [Mastering requestAnimationFrame in React](https://medium.com/@mohantaankit2002/mastering-requestanimationframe-and-cancelanimationframe-in-react-31bbee576137)
- [How React 18 Improves Application Performance](https://vercel.com/blog/how-react-18-improves-application-performance)

---
*Architecture research for: Dispatch Map Integration*
*Researched: 2026-02-16*
