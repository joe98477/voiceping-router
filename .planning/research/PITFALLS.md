# Domain Pitfalls: Adding Real-Time Dispatch Map to Production PTT System

**Domain:** Real-time location tracking and interactive maps in PTT communications
**Researched:** 2026-02-16
**Context:** Adding Leaflet map with WebSocket-driven markers to stable production system (4 milestones shipped, 73 plans executed)

## Critical Pitfalls

Mistakes that cause rewrites, production outages, or major performance degradation.

### Pitfall 1: Leaflet Memory Leaks on React Unmount

**What goes wrong:** Leaflet map instances and event listeners remain in memory after component unmount, causing browser memory to grow from ~11MB to 1.3GB+ over repeated mount/unmount cycles, eventually crashing the browser.

**Why it happens:**
- Leaflet directly manipulates the DOM while React manages the virtual DOM, creating a fundamental conflict
- Event listeners are not properly removed in the teardown code for Map objects
- React 18 Strict Mode simulates unmount+remount cycles, exposing cleanup issues (throws "Map container is already initialized" error)
- Layers added to MapContainer are not removed from the DOM during cleanup

**Consequences:**
- Browser tab crashes after repeated navigation in/out of dispatch view
- Memory consumption grows unbounded in long-running dispatch console sessions
- Strict Mode development builds fail with cryptic Leaflet initialization errors

**Prevention:**
```typescript
// CORRECT: Proper cleanup in useEffect
useEffect(() => {
  const mapInstance = L.map('map-container', options);

  return () => {
    // Critical: remove all layers first
    mapInstance.eachLayer((layer) => {
      mapInstance.removeLayer(layer);
    });
    // Then destroy the map instance
    mapInstance.remove();
  };
}, []);
```

**Additional prevention:**
- Use `useRef` to store map instance and ensure single initialization
- Never recreate map instance on prop changes—update existing instance instead
- Test with React 18 Strict Mode enabled to catch double-mount issues early
- Use ref check pattern: `if (mapRef.current) return;` before initializing map
- Monitor memory usage in DevTools during development navigation cycles

**Detection:**
- Chrome DevTools Memory Profiler shows growing "Detached DOM tree" count
- Repeated map container initialization errors in console
- Browser tab becomes sluggish after 5-10 navigation cycles
- Heap snapshots show multiple Leaflet map instances retained

**Phase to address:** Phase 1 (Map Component Foundation) — Must establish correct cleanup pattern from the start

**Sources:**
- [Heap memory build-up when MapContainer is removed from DOM](https://github.com/PaulLeCam/react-leaflet/issues/941)
- [Memory leak on simple implementation](https://github.com/Leaflet/Leaflet/issues/6784)
- [Add support for React 18 Strict Effects](https://github.com/PaulLeCam/react-leaflet/issues/963)

---

### Pitfall 2: Marker Rendering Performance Collapse at Scale

**What goes wrong:** DOM-based marker rendering degrades catastrophically above 200-300 markers. At 1000+ markers (realistic scale for large events), frame rate drops to 5-15 FPS, map panning becomes unusable, and browser becomes unresponsive.

**Why it happens:**
- Each Leaflet marker is a DOM element (SVG or HTML icon)
- 1000 markers = 1000+ DOM nodes to manage
- Every map pan/zoom triggers layout recalculation for all visible markers
- Browser rendering engine thrashes on synchronous DOM updates
- React re-renders cascade through marker components, triggering forced reflows

**Consequences:**
- Dispatch console becomes unusable during large events
- Map interactions feel "frozen" or delayed by seconds
- Browser tab can crash or trigger "page unresponsive" warnings
- User experience degradation forces dispatch to use separate mapping tools

**Prevention:**

**Strategy 1: Canvas-based markers (HIGH confidence recommendation)**
```typescript
// Use Leaflet.Canvas-Markers plugin for 1000+ markers
import 'leaflet.canvas-markers';

const markersLayer = L.canvasIconLayer({}).addTo(map);
// Canvas rendering handles 10,000-50,000 markers smoothly
```

**Strategy 2: Marker clustering (MEDIUM confidence—good for sparse data)**
```typescript
import L from 'leaflet';
import 'leaflet.markercluster';

const markers = L.markerClusterGroup({
  chunkedLoading: true, // Prevent browser lock during bulk add
  animateAddingMarkers: false, // Better performance for bulk operations
});
// Clustering can handle 50,000 markers on page load
```

**Strategy 3: Viewport culling (implement alongside canvas/clustering)**
```typescript
// Only render markers within current viewport + buffer
const bounds = map.getBounds();
const visibleMarkers = allMarkers.filter(m => bounds.contains(m.position));
```

**Optimization checklist:**
- Add all markers to cluster/canvas layer **before** adding layer to map
- Use static icon definitions (not dynamic React components per marker)
- Avoid per-marker event handlers—use layer delegation instead
- Batch marker updates (collect 100ms of location updates, apply once)
- Disable marker animations for bulk operations

**Detection:**
- Chrome DevTools Performance tab shows >50ms frame times during map interaction
- "Recalculate Style" and "Layout" dominate performance waterfall
- FPS counter drops below 20 FPS when panning map
- React Profiler shows cascading re-renders through marker list

**Phase to address:** Phase 2 (Marker Optimization) — Critical before loading production user counts

**Sources:**
- [Optimizing Leaflet Performance with a Large Number of Markers](https://medium.com/@silvajohnny777/optimizing-leaflet-performance-with-a-large-number-of-markers-0dea18c2ec99)
- [Performance issue with > 1000 markers and cluster](https://github.com/thedirtyfew/dash-leaflet/issues/24)
- [Leaflet.Canvas-Markers plugin](https://github.com/eJuke/Leaflet.Canvas-Markers)

---

### Pitfall 3: Protocol Extension Breaking Existing Clients

**What goes wrong:** Adding `batteryPercent` field to `LOCATION_UPDATE` message breaks Android clients that haven't been updated. Old clients either crash when receiving new messages, ignore location data entirely, or send malformed messages that the server rejects.

**Why it happens:**
- JSON-based WebSocket protocol is unversioned
- TypeScript interfaces are compile-time only (no runtime validation)
- Android Gson deserialization can fail on unknown fields depending on strictness
- No protocol negotiation handshake to communicate capabilities
- Server and clients deployed independently (can't guarantee synchronized updates)

**Consequences:**
- Production outage: Android users on v4.0 app can't receive location after server deploys v5.0
- Silent data loss: Server sends battery data, old clients ignore it, dispatch sees incomplete info
- Split-brain state: Some users see battery %, others don't, causing confusion in dispatch
- Rollback complexity: Must coordinate rollback across server + all client deployments

**Prevention:**

**Rule 1: Optional fields only**
```typescript
// BAD: Required field breaks old clients
interface LocationData {
  batteryPercent: number; // Old clients will omit this
}

// GOOD: Optional field with server-side default
interface LocationData {
  batteryPercent?: number; // Old clients omit, server fills with null
}
```

**Rule 2: Server validates and provides defaults**
```typescript
// Server handler for LOCATION_UPDATE
const batteryPercent = data.batteryPercent ?? null; // Default for old clients
const isCharging = data.isCharging ?? false;
```

**Rule 3: Client handles missing fields gracefully**
```typescript
// Web UI rendering
{location.batteryPercent != null
  ? `${location.batteryPercent}%`
  : 'Unknown'}
```

**Rule 4: Version negotiation for breaking changes**
```typescript
// If truly breaking change needed, use protocol versioning
enum SignalingType {
  // ...
  PROTOCOL_VERSION = 'protocol-version', // Add to connection handshake
}

// Server checks client version on connect
if (clientProtocolVersion < MIN_SUPPORTED_VERSION) {
  sendMessage({ type: 'ERROR', error: 'Client outdated, please upgrade' });
  ws.close();
}
```

**Deployment strategy:**
- Deploy server with optional fields first (backward compatible)
- Update Android app to send new fields (forward compatible with old server)
- Update web UI to display new fields (forward compatible)
- Wait 2 weeks for app store rollout before making fields required

**Testing checklist:**
- Test v5.0 server with v4.0 Android client (old client must still work)
- Test v5.0 Android client with v4.0 server (new client must not break old server)
- Test web UI with location data missing optional fields
- Verify Gson deserialization on Android handles unknown fields (set `serializeNulls = false`)

**Detection:**
- Monitor WebSocket disconnections spike after deployment
- Log warnings for messages with missing expected fields
- Alert on increased ERROR message frequency
- Test matrix: (old client + new server), (new client + old server), (new client + new server)

**Phase to address:** Phase 3 (Protocol Extension) — Must design protocol changes correctly before implementation

**Sources:**
- [RFC 6455: The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455)
- [WebSocket protocol versioning discussion](https://github.com/XRPLF/rippled/issues/219)
- Existing codebase: `src/shared/protocol.ts` shows unversioned message types

---

### Pitfall 4: React Context Re-renders Drowning Performance

**What goes wrong:** Storing 1000+ user locations in React Context causes every consuming component to re-render on every location update. At 10 location updates/second (realistic with motion), this triggers 10,000+ component re-renders/second, freezing the UI.

**Why it happens:**
- React Context broadcasts value changes to **all** consumers, regardless of which subset of data they use
- Location updates are frequent (1-10 Hz per user)
- Dispatch console has multiple components consuming location data (map markers, user list, selected user panel)
- Context value mutations don't notify React—must create new object reference every update
- No built-in mechanism for selective subscription to subset of context data

**Consequences:**
- Dispatch console UI becomes unresponsive during large events
- Typing in search box has 2-3 second lag
- Map marker animations stutter and lag
- Browser DevTools Profiler shows thousands of wasted re-renders
- User frustration forces abandonment of dispatch console

**Prevention:**

**Strategy 1: Split Context by concern (HIGH confidence)**
```typescript
// BAD: Monolithic context
interface AppContext {
  users: Map<string, User>;
  locations: Map<string, Location>;
  channels: Map<string, Channel>;
  selectedUserId: string;
}

// GOOD: Separate contexts
const LocationContext = createContext<Map<string, Location>>();
const ChannelContext = createContext<Map<string, Channel>>();
const SelectionContext = createContext<string>(); // selectedUserId only
```

**Strategy 2: Memoize context values and selectors**
```typescript
const locationContextValue = useMemo(
  () => ({ locations, updateLocation }),
  [locations] // Only recreate if locations Map reference changes
);

// Use selector hooks for component optimization
function useUserLocation(userId: string) {
  const locations = useContext(LocationContext);
  return useMemo(() => locations.get(userId), [locations, userId]);
}
```

**Strategy 3: Use specialized state management for high-frequency data**
```typescript
// Consider Zustand for location data (avoids Context re-render issues)
import create from 'zustand';

const useLocationStore = create((set) => ({
  locations: new Map(),
  updateLocation: (userId, location) =>
    set((state) => {
      const newLocations = new Map(state.locations);
      newLocations.set(userId, location);
      return { locations: newLocations };
    }),
}));

// Components subscribe to specific slices
const location = useLocationStore(state => state.locations.get(userId));
```

**Strategy 4: Virtualize large lists (CRITICAL for marker rendering)**
```typescript
import { FixedSizeList } from 'react-window';

// Only render visible user list items (not all 1000)
<FixedSizeList
  height={600}
  itemCount={users.length}
  itemSize={60}
>
  {UserRow}
</FixedSizeList>
```

**Strategy 5: Batch location updates**
```typescript
// Collect 100ms of location updates, apply in single state update
const locationBuffer = useRef<Map<string, Location>>(new Map());

useEffect(() => {
  const interval = setInterval(() => {
    if (locationBuffer.current.size > 0) {
      setLocations(prev => new Map([...prev, ...locationBuffer.current]));
      locationBuffer.current.clear();
    }
  }, 100); // Batch every 100ms

  return () => clearInterval(interval);
}, []);
```

**Optimization checklist:**
- Wrap leaf components in `React.memo()` to prevent unnecessary re-renders
- Use `useCallback` for event handlers passed as props
- Split UI into independently updating regions (map vs. user list vs. details panel)
- Monitor re-render count with React DevTools Profiler
- Target <16ms per frame (60 FPS) for smooth UI

**Detection:**
- React DevTools Profiler shows >100ms render times
- Flamegraph shows cascading re-renders through component tree
- Interaction lag >100ms in dispatch console
- CPU profiler shows >50% time in React reconciliation

**Phase to address:** Phase 4 (State Management Optimization) — Must address before loading production scale data

**Sources:**
- [One simple trick to optimize React re-renders](https://kentcdodds.com/blog/optimize-react-re-renders)
- [Optimizing React Context for Performance](https://www.tenxdeveloper.com/blog/optimizing-react-context-performance)
- [Performantly Render a Large List of Items with React Context](https://egghead.io/lessons/react-performantly-render-a-large-list-of-items-with-react-context)
- [How to Handle React Context Performance Issues](https://oneuptime.com/blog/post/2026-01-24-react-context-performance-issues/view)

---

### Pitfall 5: Android Background Location Draining Battery

**What goes wrong:** Continuous background location tracking at 5-second intervals drains battery by 30-50% over 8 hours, making the app unusable for full-day events. Users disable location permissions or uninstall the app.

**Why it happens:**
- GPS is the most power-hungry sensor on mobile devices
- Background location prevents the system from putting the device to sleep
- High accuracy mode uses GPS continuously (vs. low-power cell tower/WiFi triangulation)
- Foreground service keeps app process alive, preventing aggressive battery optimization
- Android system attributes battery drain to the app, damaging reputation

**Consequences:**
- User complaints and app store negative reviews
- Dispatch management mandates disabling location tracking
- Users manually disable location permissions, breaking dispatch visibility
- Milestone v5.0 feature becomes unusable in production
- Wasted development effort on feature that can't be deployed

**Prevention:**

**Strategy 1: Motion-aware location intervals (CRITICAL)**
```kotlin
// LocationTracker.kt — existing motion detection
when (motionState) {
  MotionState.STILL -> {
    // User stationary: update every 5 minutes
    locationRequest.interval = 5 * 60 * 1000L
  }
  MotionState.WALKING -> {
    // User walking: update every 30 seconds
    locationRequest.interval = 30 * 1000L
  }
  MotionState.DRIVING -> {
    // User driving: update every 10 seconds
    locationRequest.interval = 10 * 1000L
  }
}
```

**Strategy 2: Use largest possible interval (from Android official docs)**
```kotlin
// BAD: 5-second updates
LocationRequest.Builder(5_000L)

// GOOD: 30-second updates with motion-based adjustment
LocationRequest.Builder(30_000L)
  .setMinUpdateIntervalMillis(10_000L) // Fastest update if system has location
  .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY) // Not HIGH_ACCURACY
```

**Strategy 3: Foreground-only default with opt-in background**
```kotlin
// Default: Location only while app in foreground
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

// Require explicit user opt-in for background tracking
// Only request ACCESS_BACKGROUND_LOCATION if user enables "Track me always"
if (userOptedInToBackgroundTracking) {
  requestPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
}
```

**Strategy 4: Geofencing for event areas (advanced optimization)**
```kotlin
// Use geofencing API to wake up location tracking only near event venue
// Reduces GPS usage by 90% when user is off-duty
geofencingClient.addGeofences(
  GeofencingRequest.Builder()
    .addGeofence(eventVenueGeofence)
    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
    .build()
)
```

**Strategy 5: Battery profiling before deployment**
```bash
# Android Battery Historian analysis
adb bugreport > bugreport.zip
# Upload to https://bathist.ef.lc/
# Target: <5% battery drain per hour during normal event operations
```

**User-facing controls:**
- Settings toggle: "Update location frequency" (High/Normal/Low)
- Battery saver mode: Automatically reduces location updates when battery <20%
- Notification: "Location tracking paused (battery saver)" when throttled
- Show last update timestamp on map marker so dispatch knows data freshness

**Detection:**
- Android Battery Settings shows app consuming >10% per hour
- User reports of rapid battery drain in app store reviews
- Battery Historian shows continuous GPS sensor wakelock
- LocationManager logs show update frequency not adapting to motion state

**Phase to address:** Phase 5 (Location Tracking) — Must implement motion-aware intervals from the start

**Sources:**
- [About background location and battery life (Android official docs)](https://developer.android.com/develop/sensors-and-location/location/battery)
- [2026 Apps Like TikTok, Netflix Drain Batteries: Optimization Tips](https://www.webpronews.com/2026-apps-like-tiktok-netflix-drain-batteries-optimization-tips/)
- [Do Location Tracking Apps Drain Your Battery?](https://www.lystloc.com/blog/do-location-tracking-apps-drain-mobile-battery/)

---

## Moderate Pitfalls

Issues causing degraded UX or increased development complexity, but not production outages.

### Pitfall 6: CSS Grid Layout Thrashing on Frequent Updates

**What goes wrong:** Updating location marker positions at 10 Hz triggers CSS Grid recalculations across the entire dispatch console layout (split panel with map + user list). This causes visible jank and dropped frames even though the grid itself isn't changing.

**Why it happens:**
- CSS Grid recalculates layout for entire grid container when any child changes
- Browsers recalibrate and paint more rapidly when DOM complexity stays below 1000 nodes per view
- Layout thrashing: JavaScript reads layout property (e.g., `getBoundingClientRect`), then writes DOM, forcing synchronous reflow

**Prevention:**
- Isolate map and user list into separate CSS Grid cells with `contain: layout style paint`
- Use CSS `will-change: transform` on animated marker elements
- Batch DOM reads and writes (read all positions, then write all updates)
- Use `transform` for marker animations instead of `top`/`left` (avoids layout recalculation)
- Keep DOM complexity below 1000 nodes per view (use virtualization for user lists)

**Testing:**
- Chrome DevTools Performance tab: Look for "Recalculate Style" >10ms
- Enable "Paint flashing" to visualize unnecessary repaints
- Target <16ms frame time for 60 FPS

**Phase to address:** Phase 6 (Layout Optimization)

**Sources:**
- [CSS Grid vs Flexbox - Render Performance](https://moldstud.com/articles/p-css-grid-vs-flexbox-which-layout-method-offers-better-render-performance)
- [Performance analysis of Grid Layout](https://blogs.igalia.com/jfernandez/2015/06/24/performance-on-grid-layout/)
- Existing codebase: `web-ui/src/styles.css` uses CSS Grid for layout

---

### Pitfall 7: Esri Tile Service CORS and Rate Limiting

**What goes wrong:** Esri ArcGIS tile services require CORS configuration or ArcGIS proxy for cross-domain requests. Additionally, free tier has rate limits that can be exceeded during development/testing with frequent map pans. Map tiles fail to load, showing gray squares.

**Why it happens:**
- Esri services don't enable CORS by default for all domains
- Free tier rate limits are low (typically 1,000-2,000 requests/day)
- Development with hot-reload causes repeated tile fetches
- Multiple developers hitting same tile service can exhaust quota

**Prevention:**
- Use Esri Leaflet plugin's built-in CORS handling: `L.esri.tiledMapLayer()`
- Configure `errorTileUrl` fallback for missing tiles
- Implement tile caching in development (browser cache or service worker)
- Use OpenStreetMap tiles for development, Esri for production only
- Monitor tile request count in development
- Consider self-hosting tiles for high-traffic production use

**Detection:**
- Gray tiles or "CORS error" in browser console
- 429 "Too Many Requests" responses from tile server
- Tile loading slows or stops after N map interactions

**Phase to address:** Phase 1 (Map Component Foundation)

**Sources:**
- [Esri Leaflet and ArcGIS](https://developers.arcgis.com/esri-leaflet/)
- [Question about CORS enabled images support in ESRI Leaflet](https://github.com/Esri/esri-leaflet/issues/563)
- [Handling Tile Load Errors in Leaflet](https://runebook.dev/en/articles/leaflet/index/tilelayer-load)

---

### Pitfall 8: WebSocket Message Queue Blocking Real-Time Audio

**What goes wrong:** High-frequency location broadcasts (10 updates/sec × 1000 users = 10K messages/sec) saturate the WebSocket connection, causing PTT audio signaling messages to be delayed by seconds. Users experience lag when pressing PTT button or receiving speaker lock confirmation.

**Why it happens:**
- Single WebSocket connection carries both audio signaling (low volume, latency-sensitive) and location broadcasts (high volume, latency-tolerant)
- WebSocket is TCP-based: messages are delivered in order, so large queue of location updates blocks urgent signaling messages
- Server broadcasts all location updates to all dispatch users (N² problem)
- No message prioritization in WebSocket protocol

**Consequences:**
- PTT button press takes 2-3 seconds to acquire speaker lock
- Audio cuts in late, missing first words
- Dispatch users experience delayed UI updates when managing channels
- Core PTT functionality degraded by new location feature

**Prevention:**

**Strategy 1: Separate WebSocket connections (RECOMMENDED)**
```typescript
// High-priority connection for audio signaling
const signalingWs = new WebSocket('wss://server/signaling');

// Low-priority connection for location updates
const locationWs = new WebSocket('wss://server/location');
```

**Strategy 2: Location broadcast throttling (server-side)**
```typescript
// Server only broadcasts locations to dispatch users (not all users)
const dispatchUsers = sessionStore.getUsersByRole('DISPATCH');

// Throttle location broadcasts to 1 Hz per user (not real-time)
const LOCATION_BROADCAST_INTERVAL = 1000; // 1 second

// Batch location updates into single LOCATION_BATCH message
const locationBatch = {
  type: 'LOCATION_BATCH',
  data: { locations: [...recentUpdates] }
};
```

**Strategy 3: Selective location subscriptions**
```typescript
// Dispatch client subscribes to specific channels/users only
sendMessage({
  type: 'LOCATION_SUBSCRIBE',
  data: { channelIds: ['channel-1', 'channel-2'] }
});

// Server only sends locations for subscribed channels
```

**Strategy 4: Implement message prioritization**
```typescript
// Server-side message queue with priority
enum MessagePriority {
  CRITICAL = 0, // PTT control, speaker lock
  HIGH = 1,     // Channel state, permission updates
  NORMAL = 2,   // PING/PONG
  LOW = 3,      // Location broadcasts
}

// Process high-priority messages first
priorityQueue.sort((a, b) => a.priority - b.priority);
```

**Detection:**
- Monitor WebSocket send queue length (alert if >100 messages)
- Measure PTT button press to speaker lock latency (alert if >500ms)
- Server logs show LOCATION_BROADCAST message rate >1000/sec
- User complaints about PTT lag during large events

**Phase to address:** Phase 3 (Protocol Extension) — Plan for message volume when designing protocol

**Sources:**
- Existing codebase: `src/server/signaling/websocketServer.ts` uses single WebSocket
- User warning: "be aware of impacts of changes on one [component] that affect others"
- [WebSocket.org: The WebSocket Protocol](https://websocket.org/guides/websocket-protocol/)

---

### Pitfall 9: SVG Marker Icon Performance on Mobile

**What goes wrong:** Custom SVG marker icons with complex paths cause choppy rendering on mobile devices (Android dispatch tablets). Even with canvas-based rendering, SVG decoding becomes the bottleneck.

**Why it happens:**
- Mobile GPUs are slower than desktop
- SVG paths must be decoded and rasterized for each frame
- Data URI SVG icons (inline base64) are decoded repeatedly
- Complex gradients/filters in SVG are GPU-intensive

**Prevention:**
- Pre-render SVG markers to PNG sprites (single texture atlas)
- Use simple geometric shapes for markers (circles, triangles)
- Avoid gradients, shadows, filters in marker SVG
- Test on low-end Android devices (not just flagship phones)
- Use L.Canvas.Icon with pre-rasterized images, not inline SVG

**Phase to address:** Phase 2 (Marker Optimization)

**Sources:**
- [Leaflet icon custom marker SVG performance](https://github.com/eJuke/Leaflet.Canvas-Markers)
- [Creating dynamic text on icons in Leaflet using SVG](https://blog.pesky.moe/posts/2024-06-02-leaflet-dynamic-icon-text/)

---

## Minor Pitfalls

Minor UX issues or edge cases that are easily fixed.

### Pitfall 10: Offline Map Tile Handling

**What goes wrong:** When user's network drops or tile server is unreachable, map shows gray squares for missing tiles. No user feedback that tiles are loading vs. permanently unavailable.

**Prevention:**
- Implement `errorTileUrl` with placeholder image showing "Offline"
- Add event listener for `tileerror` to show user-facing notification
- Consider service worker caching for recently viewed map tiles

**Phase to address:** Phase 1 (Map Component Foundation)

**Sources:**
- [Handling Tile Load Errors in Leaflet](https://runebook.dev/en/articles/leaflet/index/tilelayer-load)
- [Leaflet with offline maps discussion](https://groups.google.com/g/leaflet-js/c/oX31C6KcXVw)

---

### Pitfall 11: Map Container Resize Issues

**What goes wrong:** When user resizes dispatch console split panel, Leaflet map doesn't recalculate its size automatically. Map appears cropped or has gray areas.

**Prevention:**
```typescript
// Call map.invalidateSize() after container resize
const handleResize = () => {
  map.invalidateSize();
};

// Use ResizeObserver to detect container size changes
const resizeObserver = new ResizeObserver(handleResize);
resizeObserver.observe(mapContainerRef.current);
```

**Phase to address:** Phase 6 (Layout Optimization)

**Sources:**
- Leaflet documentation: `map.invalidateSize()` method

---

### Pitfall 12: Location Timestamp Timezone Mismatches

**What goes wrong:** Android client sends location timestamp in device timezone, server stores in UTC, web UI displays in browser timezone. Dispatch sees "last updated 5 hours ago" when it was actually 5 seconds ago.

**Prevention:**
- Standardize on ISO8601 timestamps with UTC timezone (existing pattern in `LocationData.timestamp`)
- Android: Use `Instant.now().toString()` (always UTC)
- Server: Store as ISO string, don't convert timezone
- Web UI: Parse with `new Date(isoString)` and display relative time ("5 seconds ago")

**Phase to address:** Phase 5 (Location Tracking)

**Sources:**
- Existing codebase: `src/server/location/types.ts` already uses ISO8601 string

---

## Phase-Specific Warnings

Pitfalls mapped to implementation phases to guide planner.

| Phase Topic | Likely Pitfall | Mitigation | Priority |
|-------------|---------------|------------|----------|
| Phase 1: Map Component Foundation | Leaflet memory leak (Pitfall 1) | Implement proper cleanup pattern in initial component | CRITICAL |
| Phase 1: Map Component Foundation | Esri tile CORS (Pitfall 7) | Use Esri Leaflet plugin, configure errorTileUrl | HIGH |
| Phase 1: Map Component Foundation | React Strict Mode conflicts (Pitfall 1) | Test with Strict Mode enabled from start | HIGH |
| Phase 2: Marker Optimization | DOM marker performance (Pitfall 2) | Use Canvas-based markers or clustering from start | CRITICAL |
| Phase 2: Marker Optimization | SVG icon performance on mobile (Pitfall 9) | Pre-render markers to PNG, test on low-end devices | MEDIUM |
| Phase 3: Protocol Extension | Breaking existing clients (Pitfall 3) | Use optional fields, test backward compatibility matrix | CRITICAL |
| Phase 3: Protocol Extension | WebSocket message queue blocking (Pitfall 8) | Plan separate connection or throttling before implementation | HIGH |
| Phase 4: State Management | React Context re-render storm (Pitfall 4) | Split contexts, use Zustand for high-frequency data | CRITICAL |
| Phase 4: State Management | Virtualization missing (Pitfall 4) | Use react-window for user lists from start | HIGH |
| Phase 5: Location Tracking Android | Background battery drain (Pitfall 5) | Implement motion-aware intervals, profile before deploy | CRITICAL |
| Phase 5: Location Tracking Android | Timezone mismatches (Pitfall 12) | Use ISO8601 UTC timestamps consistently | LOW |
| Phase 6: Layout Optimization | CSS Grid thrashing (Pitfall 6) | Use CSS containment, profile layout performance | MEDIUM |
| Phase 6: Layout Optimization | Map resize issues (Pitfall 11) | Implement ResizeObserver pattern | LOW |

---

## Cross-Component Impact Analysis

User warning: "be aware of impacts of changes on one [component] that affect others."

### Server → Android Impact
- **Protocol change (battery field):** Android must handle optional field gracefully (Pitfall 3)
- **Location broadcast throttling:** Android won't see immediate feedback on location updates (acceptable trade-off)

### Server → Web Impact
- **WebSocket message volume:** Location broadcasts can block audio signaling (Pitfall 8)
- **Protocol change:** Web UI must handle missing optional fields (Pitfall 3)

### Android → Server Impact
- **High-frequency location updates:** Can saturate WebSocket connection (Pitfall 8)
- **Battery drain concerns:** May limit feature adoption, reducing value of server investment (Pitfall 5)

### Web → Server Impact
- **React re-render performance:** Doesn't affect server, but may require server throttling to fix client issue (Pitfall 8)
- **Map performance:** Client-only issue, no server impact

### Critical Cross-Component Risks
1. **Protocol versioning:** Changes require synchronized deployment across all components (Pitfall 3)
2. **WebSocket saturation:** High location update rate (Android) + broadcast to all dispatch (server) + render all markers (web) = compounding performance problem (Pitfalls 2, 4, 8)
3. **Battery drain → feature abandonment:** If Android battery drain is severe, dispatch disables feature, making web map useless (Pitfall 5)

---

## Testing Checklist

Must-verify items before production deployment.

### Performance Testing
- [ ] Load test: 1000 simulated users sending location updates at 1 Hz
- [ ] Measure map frame rate with 1000+ markers visible
- [ ] Profile React re-renders with 1000 location updates/sec
- [ ] Measure PTT button latency under location broadcast load
- [ ] Test map memory usage over 30-minute session with navigation in/out

### Compatibility Testing
- [ ] Deploy v5.0 server with v4.0 Android client (must not break)
- [ ] Deploy v5.0 Android client with v4.0 server (must degrade gracefully)
- [ ] Test location data with missing optional fields (battery, isCharging)
- [ ] Verify Gson deserialization on Android handles unknown fields

### Mobile Testing
- [ ] Android battery profiling: <5% drain per hour with location tracking
- [ ] Test on low-end Android device (not just flagship)
- [ ] Verify motion-aware location intervals adjust correctly
- [ ] Test background location tracking with app in background for 1 hour

### Browser Testing
- [ ] Chrome DevTools memory profiler: No detached DOM trees after navigation
- [ ] React 18 Strict Mode: No "Map container already initialized" errors
- [ ] Test map resize when adjusting split panel
- [ ] Verify tile error handling when network drops

### Integration Testing
- [ ] End-to-end: Android sends location → server broadcasts → web displays marker
- [ ] PTT latency remains <500ms during high location broadcast load
- [ ] Verify location timestamps display correctly across timezones
- [ ] Test graceful degradation when Esri tile service unavailable

---

## Summary of Top 5 Risks

**Risk priority:** Impact × Likelihood of occurrence if not addressed

1. **Leaflet memory leak (Pitfall 1)** — HIGH impact, HIGH likelihood
   - Causes browser crashes in production dispatch console
   - Easy to miss without explicit Strict Mode testing
   - **Must address in Phase 1**

2. **Marker performance collapse (Pitfall 2)** — HIGH impact, HIGH likelihood
   - Makes feature unusable at production scale (1000+ users)
   - DOM-based markers are default, canvas optimization not obvious
   - **Must address in Phase 2**

3. **Protocol breaking changes (Pitfall 3)** — HIGH impact, MEDIUM likelihood
   - Causes production outages if not handled carefully
   - Requires careful planning and deployment coordination
   - **Must address in Phase 3**

4. **React Context re-render storm (Pitfall 4)** — HIGH impact, MEDIUM likelihood
   - Degrades entire dispatch console UX, not just map
   - Easy to introduce with naive Context usage
   - **Must address in Phase 4**

5. **Android battery drain (Pitfall 5)** — MEDIUM impact, HIGH likelihood
   - Doesn't break app, but causes feature abandonment
   - Continuous GPS is default behavior, optimization requires deliberate design
   - **Must address in Phase 5**

---

## Confidence Assessment

| Pitfall | Confidence | Evidence |
|---------|------------|----------|
| 1. Leaflet memory leak | HIGH | Multiple GitHub issues, React 18 Strict Mode documentation |
| 2. Marker performance | HIGH | Benchmarks showing 10K+ marker capability with canvas, <300 with DOM |
| 3. Protocol breaking changes | HIGH | RFC 6455, existing codebase protocol.ts, standard practice |
| 4. React Context re-renders | HIGH | Multiple authoritative sources, React documentation |
| 5. Android battery drain | HIGH | Android official documentation, 2026 industry reports |
| 6. CSS Grid thrashing | MEDIUM | Performance analysis articles, general browser rendering knowledge |
| 7. Esri CORS/rate limits | MEDIUM | Esri Leaflet documentation, common development experience |
| 8. WebSocket message queue | MEDIUM | WebSocket protocol knowledge, existing codebase analysis |
| 9. SVG marker performance | MEDIUM | General mobile performance knowledge, plugin documentation |
| 10. Offline tile handling | MEDIUM | Leaflet documentation, standard practice |
| 11. Map resize issues | HIGH | Leaflet documentation, well-known requirement |
| 12. Timezone mismatches | HIGH | Existing codebase already uses ISO8601 correctly |

**Overall confidence:** HIGH

All critical pitfalls (1-5) backed by authoritative sources (official documentation, GitHub issues from library maintainers, or existing codebase analysis). Moderate pitfalls (6-9) based on established web performance principles and library documentation. Minor pitfalls (10-12) are well-documented requirements.

**Gaps identified:**
- No specific information on mediasoup + high-frequency WebSocket message interaction (Pitfall 8 is based on general WebSocket knowledge)
- Limited information on Esri tile service rate limits for production scale (Pitfall 7)

**Recommendation:** All critical pitfalls should be addressed in their designated phases. Moderate pitfalls can be addressed reactively if needed, but prevention is preferred. Minor pitfalls can be fixed during polish phase if not addressed earlier.
