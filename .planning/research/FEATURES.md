# Feature Research: Dispatch Map View

**Domain:** Real-time personnel tracking dispatch map interface
**Researched:** 2026-02-16
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Real-time location markers | Core purpose of dispatch map - dispatchers expect to see where field workers are now | LOW | Data already available from Phase 18, just needs visualization via Leaflet markers |
| Satellite imagery base layer | Industry standard for dispatch/fleet tracking - field context is critical | LOW | Esri World Imagery already chosen, well-supported in Leaflet |
| Click-to-view user details popup | Users expect 67% prefer click over hover for accessing detailed information | LOW | Standard Leaflet popup API, populated from existing location data |
| Map controls (zoom, pan) | Universal map UX expectation across all map applications | LOW | Native Leaflet controls, zero custom code |
| User status indicators on markers | Dispatchers need to distinguish active/idle/offline workers at a glance | MEDIUM | Requires marker icon styling based on connection status + motion state |
| Username labels on markers | Identifying workers without clicking is essential for quick scanning | MEDIUM | Leaflet tooltip or DivIcon with text overlay |
| Auto-refresh location data | Static map is useless for real-time tracking - expect periodic updates | LOW | WebSocket LOCATION_BROADCAST already implemented, just connect to map updates |
| Map layer switcher | Users expect ability to toggle between satellite/street/terrain views | LOW | Leaflet layer control plugin, standard UI component |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Motion state visualization | Show STILL/WALKING/DRIVING via icon style - helps dispatchers understand worker activity without asking | MEDIUM | Motion state already tracked (Phase 18), apply different marker colors or icons per state |
| PTT activity indicator | Real-time visual of who's transmitting on radio - unique integration of voice + location | MEDIUM | Flash/pulse marker when SPEAKER_CHANGED event matches userId, leverages existing PTT state |
| Channel assignment overlay | Show which channel each worker is on without opening popup - critical for multi-team dispatch | MEDIUM | Color-code markers by channelId or add badge overlay, requires channel state integration |
| Battery level warnings | Proactive alerts when field workers' devices drop below threshold (e.g., <20%) - prevents lost workers | MEDIUM | Requires Android battery % in LOCATION_UPDATE payload, visual indicator on marker |
| Configurable popup content | Let dispatch customize what data shows in popups (battery, speed, heading, last update time) | MEDIUM | Settings UI for toggling popup fields, stored in localStorage or user preferences |
| Marker clustering for dense areas | When 50+ workers in same area, cluster markers to prevent visual clutter | MEDIUM | Leaflet.markercluster plugin handles 10K+ markers well, improves performance in crowded zones |
| Location staleness indicator | Fade or gray out markers when location >5 minutes old - dispatch knows who's lost GPS | LOW | isStale flag already exists in LocationPosition interface, apply CSS transparency |
| Accuracy circle visualization | Show GPS accuracy radius around marker - dispatchers know confidence of position | LOW | Leaflet circle layer, radius = accuracy in meters, useful for indoor/canyon scenarios |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Historical trail/breadcrumb tracking | "See where workers have been" for route analysis | Performance disaster - Leaflet.draw struggles with 1000s of polyline points on mobile, creates huge DOM and memory bloat; also privacy concerns | Defer to v2+ with server-side trail simplification (Douglas-Peucker) and time-windowed queries, or use heatmap for aggregate patterns |
| Hover-based popups | "Faster than clicking" perceived efficiency | Only 33% prefer hover, breaks on mobile/tablets (no hover state), accessibility nightmare for keyboard users, accidental triggers cause UI flicker | Use click-based popups per WCAG guidelines and mobile compatibility |
| Real-time geofencing alerts | "Notify when worker enters/exits zone" for compliance | Requires complex polygon drawing UI, client-side point-in-polygon is CPU-heavy at scale, alert fatigue if misconfigured | Defer to v2+ with server-side geofence evaluation and webhook-based alerts, not live map feature |
| Offline map capability | "Maps should work without internet" for resilience | Tile caching for 1000-user event area = gigabytes of storage, complex cache invalidation, licensing issues with Esri imagery | Accept online-only for dispatch console (workers carry phones with local offline needs), focus on fast tile loading instead |
| Auto-follow mode (track single user) | "Lock map to selected worker" convenience | Disorienting when map constantly re-centers, prevents manual exploration, accidental triggering frustrates users | Offer "Center on user" button instead of continuous tracking, one-time action preserves user control |
| Full street map as default | "Street maps are familiar" general preference | Field workers operate in non-street areas (forests, construction sites, event venues), satellite provides critical terrain context | Keep satellite default per domain requirements, offer street as secondary layer option |

## Feature Dependencies

```
[Real-time location markers]
    └──requires──> [Location data from Phase 18]
                       └──requires──> [WebSocket connection]

[PTT activity indicator] ──requires──> [Real-time location markers]
                        └──requires──> [SPEAKER_CHANGED event integration]

[Channel assignment overlay] ──requires──> [Real-time location markers]
                            └──requires──> [Channel state from server]

[Battery level warnings] ──requires──> [Android battery % in LOCATION_UPDATE]
                        └──requires──> [Real-time location markers]

[Marker clustering] ──enhances──> [Real-time location markers]
                   (prevents performance degradation at 100+ markers)

[Configurable popup content] ──enhances──> [Click-to-view user details popup]

[Location staleness indicator] ──enhances──> [Real-time location markers]
                               (uses existing isStale flag)

[Accuracy circle] ──enhances──> [Real-time location markers]
                 (visualizes existing accuracy field)
```

### Dependency Notes

- **Real-time location markers requires Location data from Phase 18:** All location data (lat/lng, accuracy, speed, heading, motionState, timestamp) already exists in LocationPosition interface and is broadcast via LOCATION_BROADCAST WebSocket events.

- **PTT activity indicator requires SPEAKER_CHANGED event integration:** PTT state changes are already broadcast server-side; map just needs to subscribe to same WebSocket events and apply visual marker pulse when userId matches.

- **Channel assignment overlay requires Channel state from server:** Users already have channelId associations via JOIN_CHANNEL events; dispatch console needs to track this mapping and apply to markers.

- **Battery level warnings requires Android battery % in LOCATION_UPDATE:** This is NEW data field - Android client must add battery percentage to location update payload; server schema update needed.

- **Marker clustering enhances Real-time location markers:** Leaflet.markercluster is a drop-in replacement for marker layer; no data changes, purely performance optimization for 100+ worker scenarios.

## MVP Definition

### Launch With (v1)

Minimum viable product - what's needed to validate the concept.

- [x] Real-time location markers - Core value proposition, shows field workers on map
- [x] Satellite imagery base layer - Table stakes for dispatch, already chosen (Esri World Imagery)
- [x] Click-to-view user details popup - Essential interaction pattern for viewing worker metadata
- [x] Map controls (zoom, pan) - Native Leaflet, zero effort
- [x] Auto-refresh location data - WebSocket integration for live updates
- [x] Username labels on markers - Quick identification without clicking
- [x] Map layer switcher - Low effort via Leaflet plugin, high user value
- [x] Location staleness indicator - Uses existing isStale flag, prevents confusion about old data

### Add After Validation (v1.x)

Features to add once core is working and usage patterns are understood.

- [ ] Motion state visualization - Add after confirming dispatchers use motion state data (STILL/WALKING/DRIVING)
- [ ] User status indicators on markers - Add once connection status patterns are clear (online/offline/reconnecting)
- [ ] PTT activity indicator - Add if dispatchers correlate radio activity with map position (validate use case first)
- [ ] Marker clustering - Add when field testing reveals 100+ concurrent workers or performance issues
- [ ] Accuracy circle visualization - Add if dispatchers report position confusion in GPS-challenged areas
- [ ] Configurable popup content - Add after feedback shows which fields are noise vs signal

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] Channel assignment overlay - Requires UI/UX research on color-coding vs badges vs filters
- [ ] Battery level warnings - Requires Android schema change + threshold configuration UI
- [ ] Historical trail tracking - Requires server-side polyline simplification and time-windowed queries to avoid performance collapse
- [ ] Geofencing alerts - Requires polygon drawing UI, server-side evaluation, webhook system
- [ ] Offline map capability - Licensing + storage + caching complexity not justified for dispatch console (field workers different story)

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Real-time location markers | HIGH | LOW | P1 |
| Satellite imagery base layer | HIGH | LOW | P1 |
| Click-to-view user details popup | HIGH | LOW | P1 |
| Auto-refresh location data | HIGH | LOW | P1 |
| Map controls (zoom, pan) | HIGH | LOW | P1 |
| Username labels on markers | HIGH | MEDIUM | P1 |
| Location staleness indicator | HIGH | LOW | P1 |
| Map layer switcher | HIGH | LOW | P1 |
| Motion state visualization | MEDIUM | MEDIUM | P2 |
| User status indicators | MEDIUM | MEDIUM | P2 |
| PTT activity indicator | MEDIUM | MEDIUM | P2 |
| Marker clustering | MEDIUM | MEDIUM | P2 |
| Accuracy circle visualization | MEDIUM | LOW | P2 |
| Configurable popup content | MEDIUM | MEDIUM | P2 |
| Channel assignment overlay | MEDIUM | MEDIUM | P3 |
| Battery level warnings | MEDIUM | HIGH | P3 |
| Historical trail tracking | LOW | HIGH | P3 |
| Geofencing alerts | LOW | HIGH | P3 |
| Offline map capability | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for launch (MVP - validate core value)
- P2: Should have, add when possible (v1.x - enhance after validation)
- P3: Nice to have, future consideration (v2+ - defer until PMF)

## Competitor Feature Analysis

| Feature | Fleet GPS Trackers (Samsara, GPS Insight) | Field Service Platforms (Salesforce FSL, Facilio) | Our Approach |
|---------|-------------------------------------------|---------------------------------------------------|--------------|
| Real-time markers | Standard - 5-10s update intervals | Standard - worker location on map | Match standard - WebSocket LOCATION_BROADCAST for <5s latency |
| Satellite imagery | Optional layer, street default | Optional layer, street default | Satellite default (event/field context > street navigation) |
| Motion state | Via telematics (speed-based) | Not standard | Differentiator - Android motion detection (STILL/WALKING/DRIVING) already in Phase 18 |
| Geofencing | Core feature with zone drawing | Core feature for service territories | Defer - focus on comms first, location visualization second |
| Historical trails | Standard with playback controls | Standard for route analysis | Defer - performance and privacy concerns require v2 architecture |
| Clustering | Standard for 100+ fleet | Less common (smaller teams) | P2 feature - add when testing confirms need |
| Battery warnings | Standard for vehicle/device power | Standard for device management | P3 - requires schema change, validate demand first |
| PTT integration | Not applicable (fleet focus) | Not applicable (no PTT comms) | Unique differentiator - real-time voice + location correlation |
| Popup customization | Minimal (fleet metrics fixed) | Moderate (field data configurable) | P2 - start simple, add configurability based on feedback |
| Offline maps | Standard for drivers | Standard for field workers | Defer - dispatch console is online environment, different from field app |

## Implementation Notes

### Existing Infrastructure (Available)

- Location data schema: `LocationPosition` interface with lat/lng, accuracy, speed, heading, motionState, timestamp, isStale
- WebSocket broadcast: `LOCATION_BROADCAST` event already implemented in Phase 18
- User identification: userId in location data correlates to roster/channel state
- Motion state detection: Android client sends 'still' | 'walking' | 'driving' | 'unknown'
- Staleness calculation: Server marks isStale=true when timestamp >5 minutes old
- Dispatch console: Existing channel grid UI in browser, role-based access established

### Required New Work

- Leaflet map component integration in dispatch console UI
- Marker layer creation from LOCATION_BROADCAST WebSocket events
- Popup template with location metadata fields
- Username label rendering (Leaflet tooltip or DivIcon)
- Esri World Imagery tile layer configuration
- Layer switcher UI component (satellite/street/terrain)
- Staleness visualization (CSS opacity or icon style)
- Map container responsive layout in dispatch console

### Deferred/Complex Work

- Battery percentage field in LOCATION_UPDATE payload (requires Android + server schema change)
- Channel assignment visualization (requires channel state integration + UI/UX design)
- Marker clustering plugin integration (add when performance testing reveals need)
- Geofencing polygon drawing UI and server-side evaluation
- Historical trail polyline rendering with time-window queries
- Offline tile caching and license management

## Sources

**Fleet Tracking & Dispatch Features:**
- [5 Must-Have Features in Fleet Dispatch Software](https://www.elevatecodedigital.com/2025/12/5-must-have-features-in-fleet-dispatch.html)
- [GPS Fleet Tracking Complete Guide](https://www.simplyfleet.app/blog/complete-guide-gps-fleet-tracking)
- [Dispatcher Console Map Interface - Salesforce Trailhead](https://trailhead.salesforce.com/content/learn/modules/field-service-dispatcher-console-for-dispatchers/explore-the-dispatcher-console)
- [Radio Dispatch Consoles Guide](https://www.linbis.com/general/radio-dispatch-consoles-the-backbone-of-modern-communication-systems/)

**Map UX Patterns:**
- [5 Map UI Design Patterns That Elevate UX](https://bricxlabs.com/blogs/map-ui-design-patterns-examples)
- [Map UI Patterns](https://mapuipatterns.com/)
- [Map UI Design Best Practices](https://www.eleken.co/blog-posts/map-ui-design)
- [Marker Design Pattern](https://mapuipatterns.com/marker/)

**Leaflet Performance & Real-Time Tracking:**
- [Optimizing Leaflet Performance with Large Number of Markers](https://medium.com/@silvajohnny777/optimizing-leaflet-performance-with-a-large-number-of-markers-0dea18c2ec99)
- [Leaflet Real-Time Plugin](https://github.com/perliedman/leaflet-realtime)
- [Building Real-Time Multi-User Location Tracker with Leaflet](https://medium.com/@itsdavidmandal/building-a-real-time-multi-user-location-tracker-with-node-js-socket-io-and-leaflet-8d39e908f94a)
- [Leaflet MarkerCluster Plugin](https://github.com/Leaflet/Leaflet.markercluster)
- [PruneCluster - Fast Realtime Clustering](https://github.com/SINTEF-9012/PruneCluster)

**Hover vs Click Interaction:**
- [Tooltip Guidelines - Nielsen Norman Group](https://www.nngroup.com/articles/tooltip-guidelines/)
- [Popups, Dialogs, Tooltips UX Patterns](https://medium.com/design-bootcamp/popups-dialogs-tooltips-and-popovers-ux-patterns-2-939da7a1ddcd)
- [Breaking Up With Hover Navigation](https://toward.studio/latest/breaking-up-with-hover-navigation-menus-for-websites)
- [Hover vs Click Navigation](https://medium.com/ashleycrutcher/hover-vs-click-navigation-a260a8d51d81)

**Geofencing & Alerts:**
- [Geofencing in ArcGIS Field Maps](https://www.esri.com/arcgis-blog/products/field-maps/field-mobility/get-started-with-geofences-in-arcgis-field-maps)
- [Mapbox Geofencing for iOS & Android](https://www.mapbox.com/blog/mapbox-geofencing-drives-operational-efficiency-and-revenue-growth)
- [Geofencing Offline Notifications](https://community.esri.com/t5/arcgis-field-maps-questions/geofencing-notifications-while-offline/td-p/1091126)
- [What is Geofencing - Radar](https://radar.com/blog/geofencing-explained-what-is-geofencing)

**Battery Monitoring & Fleet Alerts:**
- [Fleet Activity Alerts - FTS GPS](https://www.ftsgps.com/solutions/fleet-activity-alerts/)
- [Strategies for Using Alerts - GPS Insight](https://help.gpsinsight.com/best-practice/strategies-for-using-alerts/)
- [Benefits of Real-Time Alert System in Fleets](https://www.biz4intellia.com/blog/benefits-of-real-time-alert-system-in-fleets/)

**Satellite Imagery & Map Layers:**
- [Mapbox Satellite Global Base Map](https://www.mapbox.com/maps/satellite)
- [World Imagery - Esri](https://www.arcgis.com/home/item.html?id=10df2279f9684e4a9f6a7f08febac2a9)
- [Best Practices for Layering Satellite Images](https://www.maplibrary.org/11173/7-best-practices-for-layering-satellite-images-from-different-years/)

**Online/Offline Indicators:**
- [Designing Online/Offline Indicator](https://medium.com/@7shivamsrivastava/designing-online-offline-indicator-994555eeec13)
- [Offline States - Material Design](https://www.mdui.org/en/design/1/patterns/offline-states.html)
- [Real-World Online/Offline Indicator Design](https://medium.com/@vaasubisht/is-anyone-there-designing-a-real-world-online-offline-indicator-5a2582181d9b)

**Historical Trail Tracking:**
- [Leaflet GPX Track Plugin](https://github.com/mpetazzoni/leaflet-gpx)
- [Leaflet Playback Plugin](https://github.com/hallahan/LeafletPlayback)
- [Leaflet TrackPlayBack](https://github.com/linghuam/Leaflet.TrackPlayBack)

---
*Feature research for: VoicePing Router Dispatch Map View*
*Researched: 2026-02-16*
