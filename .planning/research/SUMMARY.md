# Project Research Summary

**Project:** VoicePing Router - Dispatch Map View
**Domain:** Real-time dispatch map for enterprise PTT communications
**Researched:** 2026-02-16
**Confidence:** HIGH

## Executive Summary

This research covers adding a real-time location tracking dispatch map to a production PTT system. The map will display field workers' positions on satellite imagery with battery telemetry and motion state indicators. Based on research findings, **Leaflet + react-leaflet 4.x** is the optimal choice (react-leaflet 5.x requires React 19, project uses React 18.3.1). Esri World Imagery tiles work without API key using direct TileLayer. No new Android dependencies are needed for battery telemetry (BatteryManager is built-in SDK).

The recommended approach is **backend-first, incremental integration**: extend the protocol with optional battery field, implement server-side broadcasting, then build web map with proper cleanup patterns from the start. The biggest risks are Leaflet memory leaks (React 18 Strict Mode conflicts), marker rendering performance collapse above 200+ markers, and Android background location draining battery. All three have well-documented mitigations: proper useEffect cleanup, canvas-based rendering, and motion-aware location intervals.

Critical success factors: (1) establish correct Leaflet cleanup pattern in Phase 1 to prevent browser crashes, (2) implement canvas markers or clustering in Phase 2 before loading production scale, (3) use optional protocol fields to maintain backward compatibility, (4) split LocationContext from ChannelContext to avoid re-render storms, (5) implement motion-aware location intervals to prevent battery drain complaints.

## Key Findings

### Recommended Stack

The stack leverages existing infrastructure (React 18.3.1, WebSocket signaling, mediasoup PTT) and adds minimal dependencies. **Core additions:** react-leaflet 4.2.1 (React 18 compatible), leaflet 1.9.4 (mature, lightweight 42KB), Esri World Imagery tiles via direct TileLayer (no plugin needed). **Android requires zero new dependencies** - BatteryManager is built-in since API 21, project minSdk=26. Server extends existing LocationData interface with optional batteryPercent field (backward compatible).

**Core technologies:**
- **react-leaflet 4.2.1**: React bindings for Leaflet maps, supports React 18 (v5.x requires React 19 migration)
- **leaflet 1.9.4**: Industry-standard open-source map library, lightweight (42KB), mobile-friendly, extensive plugin ecosystem
- **Esri World Imagery (TileLayer)**: Satellite imagery tiles, free for non-revenue apps, no API key required for raster tiles
- **android.os.BatteryManager**: Built-in battery monitoring (API 21+), no permissions required for getIntProperty()

**Key recommendation:** Use react-leaflet 4.x (NOT 5.x) until React 19 migration. Use direct L.divIcon with SVG for custom markers (consistent with existing @mdi/js icons). Avoid react-leaflet 5.x, Leaflet.awesome-markers, continuous BatteryManager polling.

### Expected Features

Research identified 8 table stakes features (users expect these), 8 differentiators (competitive advantage), and 6 anti-features (commonly requested but problematic). MVP focuses on table stakes + staleness indicator, defer PTT activity indicator and channel overlay to v1.x after validation.

**Must have (table stakes):**
- Real-time location markers - Core value proposition, data already exists from existing location tracking
- Satellite imagery base layer - Industry standard for dispatch/fleet tracking, field context critical
- Click-to-view user details popup - Users prefer click over hover (67%), works on mobile
- Map controls (zoom, pan) - Universal UX expectation, native Leaflet controls
- Auto-refresh location data - WebSocket LOCATION_BROADCAST already implemented
- Username labels on markers - Essential for quick scanning without clicking
- Map layer switcher - Low effort via Leaflet plugin, high user value
- Location staleness indicator - Uses existing isStale flag, prevents confusion about old data

**Should have (competitive advantages):**
- Motion state visualization - Show STILL/WALKING/DRIVING via icon color, data already tracked
- PTT activity indicator - Flash marker when user transmits, unique voice+location integration
- Battery level warnings - Proactive alerts when <20%, prevents lost workers
- Marker clustering - When 100+ workers in same area, prevent visual clutter

**Defer (v2+):**
- Historical trail tracking - Performance disaster with 1000s of polyline points, requires server-side simplification
- Geofencing alerts - Requires complex polygon UI, server-side evaluation, webhook system
- Offline map capability - Tile caching = gigabytes, licensing issues, accept online-only for dispatch console

### Architecture Approach

The architecture uses **separate LocationContext from ChannelContext** to prevent high-frequency location updates (1-10 Hz) from triggering re-renders of channel cards. Location state is batched with requestAnimationFrame (max 60 renders/sec instead of N updates/sec). Map and channels are parallel siblings in CSS Grid split layout, not parent-child. Protocol extension uses optional fields (batteryPercent?) for backward compatibility with staged deployment: server first (accepts new field), Android second (sends new field), web UI third (displays new field).

**Major components:**
1. **LocationProvider** - Manages Map<userId, LocationData>, batches updates with requestAnimationFrame, exposes useLocation hook
2. **DispatchMap** - Leaflet MapContainer with TileLayer, renders UserMarker[] from positions Map
3. **UserMarker** - Leaflet Marker with L.divIcon, CSS classes for motion state/stale/battery, Popup with user details
4. **MapController** - useMap consumer for side effects: auto-fit bounds, handle query/broadcast
5. **LocationBroadcaster** (server) - Broadcasts location updates to dispatch users only, includes batteryPercent in payload
6. **LocationManager.kt** (Android) - Includes getBatteryLevel() in LOCATION_UPDATE, motion-aware intervals

**Key pattern:** Backend-first dependency order: Server extends protocol → Android sends battery % → Web layout split → Map foundation → LocationContext + WebSocket → Marker rendering → Polish/performance. This prevents breaking existing UI and enables incremental testing.

### Critical Pitfalls

Research identified 12 pitfalls across 3 severity levels. Top 5 risks (HIGH impact × HIGH likelihood) are Leaflet memory leak, marker performance collapse, protocol breaking changes, React Context re-render storm, and Android battery drain. All have well-documented mitigations but require deliberate design.

1. **Leaflet memory leak on React unmount** - Map instances remain in memory after unmount, browser crashes after repeated navigation. FIX: Proper cleanup in useEffect (remove all layers, then map.remove()), use ref guard to prevent double-init, test with React 18 Strict Mode enabled.

2. **Marker rendering performance collapse at scale** - DOM-based markers degrade to 5-15 FPS above 200-300 markers. At 1000+ markers, map becomes unusable. FIX: Use canvas-based markers (Leaflet.Canvas-Markers handles 10K+ smoothly) or clustering (Leaflet.markercluster handles 50K on load), implement viewport culling, batch marker updates.

3. **Protocol extension breaking existing clients** - Adding batteryPercent field breaks old Android clients if not optional. FIX: Use optional fields only (batteryPercent?: number), server validates and provides defaults, deploy server first (backward compatible), then clients (forward compatible), test compatibility matrix.

4. **React Context re-renders drowning performance** - Storing 1000+ locations in Context causes every consumer to re-render on every update. At 10 updates/sec, triggers 10,000+ re-renders/sec. FIX: Split LocationContext from ChannelContext, batch updates with requestAnimationFrame, use React.memo() on leaf components, consider Zustand for high-frequency data.

5. **Android background location draining battery** - Continuous GPS at 5-second intervals drains 30-50% battery over 8 hours, users disable location or uninstall. FIX: Motion-aware intervals (STILL=5min, WALKING=30s, DRIVING=10s), use PRIORITY_BALANCED_POWER_ACCURACY not HIGH_ACCURACY, foreground-only default with opt-in background, battery profiling before deploy.

## Implications for Roadmap

Based on research, suggested phase structure follows dependency-driven build order with critical pitfalls addressed in designated phases. Backend-first approach ensures data is available before web consumes it, layout split validates CSS structure before adding Leaflet complexity, state management before markers prevents performance issues.

### Phase 1: Server + Android Protocol Extension
**Rationale:** Extend protocol with optional battery field before web consumes it. Optional fields enable staged deployment (server accepts new field, old clients omit it, new clients send it). Tests backward compatibility before breaking changes.

**Delivers:** LocationData interface extended with batteryPercent?: number, server broadcasts battery % when present, Android sends battery % in LOCATION_UPDATE.

**Addresses:** Protocol breaking changes (Pitfall 3), enables battery level warnings feature (differentiator).

**Avoids:** Deploying server that requires battery field (breaks old clients), deploying web UI that expects battery field (breaks with old clients).

**Stack:** No new dependencies (BatteryManager built-in), TypeScript interface extension, Kotlin data class extension.

### Phase 2: Web Layout Split
**Rationale:** Create CSS Grid split layout (channels | map) before adding Leaflet. Validates CSS structure without breaking existing channel grid. Empty map panel prevents regressions in channel monitoring.

**Delivers:** CSS Grid .dispatch-console--split with 50/50 columns, responsive breakpoints (stack vertically on <1200px), existing ChannelGrid wrapped in .channels-panel, empty .map-panel placeholder.

**Addresses:** Layout foundation for map integration, ensures existing dispatch console works unchanged.

**Avoids:** CSS Grid thrashing (Pitfall 6) by isolating panels early.

**Stack:** Pure CSS, no new dependencies.

### Phase 3: Map Foundation (Leaflet Integration)
**Rationale:** Render basic Leaflet map with OpenStreetMap tiles before adding location data. Tests Leaflet integration in isolation, establishes correct cleanup pattern (prevents memory leak), validates map displays alongside channels.

**Delivers:** DispatchMap component with MapContainer + TileLayer (OpenStreetMap for testing), proper useEffect cleanup with map.remove(), ref guard to prevent double-init, map visible in split layout.

**Addresses:** Leaflet memory leak (Pitfall 1 - CRITICAL), Esri tile CORS (Pitfall 7), offline tile handling (Pitfall 10).

**Avoids:** Browser crashes from memory leak by establishing cleanup pattern from start, React Strict Mode conflicts by testing double-mount early.

**Stack:** react-leaflet 4.2.1, leaflet 1.9.4, Leaflet CSS in index.html.

**Research flag:** NO - Leaflet integration is well-documented with official react-leaflet docs.

### Phase 4: Location State Management
**Rationale:** Create separate LocationContext (not extending ChannelContext) before rendering markers. Prevents high-frequency location updates from re-rendering channel cards. Batching with requestAnimationFrame reduces re-renders from N/sec to 60/sec.

**Delivers:** LocationProvider with positions Map<userId, LocationData>, queueLocationUpdate with rAF batching, useLocationUpdates hook listening to LOCATION_BROADCAST WebSocket, LOCATION_QUERY on mount with LOCATION_SNAPSHOT response.

**Addresses:** React Context re-render storm (Pitfall 4 - CRITICAL), WebSocket message queue blocking (Pitfall 8).

**Avoids:** Channel card re-renders on location updates (separate contexts), excessive re-renders (rAF batching), WebSocket saturation (server filters to dispatch users only).

**Stack:** React Context API, existing WebSocket connection reused.

**Research flag:** NO - React Context patterns well-documented, batching with rAF is standard optimization.

### Phase 5: Marker Rendering + Styling
**Rationale:** Display user markers with L.divIcon (CSS control) and motion state indicators. Uses canvas-based rendering or clustering from start to avoid performance collapse at production scale (200+ markers). Custom SVG icons consistent with existing @mdi/js usage.

**Delivers:** UserMarker component with L.divIcon, CSS classes for motion state (--driving, --walking, --still), stale indicator (opacity), battery % badge, popup with user details, render markers from LocationContext.positions.

**Addresses:** Marker performance collapse (Pitfall 2 - CRITICAL), SVG icon performance (Pitfall 9).

**Avoids:** DOM marker performance issues by using canvas or clustering, inconsistent icon library by using @mdi/js SVG patterns.

**Stack:** Leaflet.Canvas-Markers (optional, for 200+ markers) or Leaflet.markercluster, CSS animations for pulse effect.

**Research flag:** NO - DivIcon and canvas markers are well-documented Leaflet patterns.

### Phase 6: Auto-Fit + Performance Tuning
**Rationale:** Polish UX after core functionality works. Auto-fit bounds on initial load, remember zoom/center in localStorage, optimize rendering with CSS containment. Performance testing validates frame rate with production scale (50+ simultaneous updates).

**Delivers:** MapController component with useMap hook, fitBounds on initial load, localStorage persistence for zoom/center, useMapBounds toggle, ResizeObserver for container resize, CSS containment for map panel.

**Addresses:** Map resize issues (Pitfall 11), CSS Grid thrashing (Pitfall 6), timezone mismatches (Pitfall 12).

**Avoids:** Disorienting auto-follow mode (anti-feature), layout thrashing from frequent updates.

**Stack:** ResizeObserver (native), localStorage (native).

**Research flag:** NO - Standard UX patterns for map interactions.

### Phase Ordering Rationale

- **Backend-first (Phase 1):** Server + Android protocol extension ensures data is available before web consumes it. Optional fields enable staged deployment without breaking existing clients.

- **Layout before map (Phase 2):** Validates CSS Grid structure without Leaflet complexity. Prevents breaking existing channel monitoring, enables incremental testing.

- **Map foundation before state (Phase 3):** Tests Leaflet integration in isolation, establishes critical cleanup pattern to prevent memory leak. React Strict Mode validation catches double-mount issues early.

- **State before markers (Phase 4):** LocationContext + WebSocket integration must work before rendering markers. Batching pattern prevents re-render storms at production scale.

- **Markers with optimization (Phase 5):** Canvas/clustering from start prevents performance collapse. DOM markers are easy but fail at 200+ markers (production scale).

- **Polish last (Phase 6):** UX improvements and performance tuning require full stack to test end-to-end. Auto-fit and resize handling are valuable but not blocking.

### Research Flags

**Phases needing deeper research during planning:**
- None - All phases use well-documented patterns with official library documentation.

**Phases with standard patterns (skip research-phase):**
- **Phase 1:** TypeScript interface extension, Kotlin data class, optional fields (standard backend patterns)
- **Phase 2:** CSS Grid split layout (standard responsive pattern)
- **Phase 3:** Leaflet map integration (official react-leaflet docs, extensive examples)
- **Phase 4:** React Context + useEffect WebSocket (standard React patterns)
- **Phase 5:** Leaflet markers + DivIcon (official Leaflet docs, canvas plugins documented)
- **Phase 6:** Map UX patterns (ResizeObserver, localStorage, fitBounds all standard)

**Why no research needed:** Leaflet is mature library (v1.9.4 stable), react-leaflet has official docs with React 18 examples, Android BatteryManager is official Android SDK, WebSocket patterns are established in codebase. Research identified standard solutions for all critical pitfalls (cleanup pattern, canvas rendering, optional fields, Context splitting, motion-aware intervals).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Official Leaflet docs, react-leaflet compatibility verified, Android BatteryManager official API reference, no npm access but versions confirmed via WebSearch |
| Features | HIGH | Industry analysis from fleet tracking leaders (Samsara, GPS Insight), field service platforms (Salesforce FSL), map UX research (Nielsen Norman Group), existing location data schema verified in codebase |
| Architecture | HIGH | React Leaflet official docs, React Context best practices (Kent C. Dodds), performance optimization sources (Medium articles with benchmarks), existing codebase analysis (protocol.ts, ChannelContext patterns) |
| Pitfalls | HIGH | All critical pitfalls backed by authoritative sources (GitHub issues from library maintainers, Android official docs, React documentation, performance benchmarks with numbers) |

**Overall confidence:** HIGH

### Gaps to Address

Research was comprehensive with authoritative sources for all critical areas. Two minor gaps identified:

- **Esri tile service rate limits for production scale:** Documentation shows free tier limits exist but exact numbers not confirmed. WORKAROUND: Use OpenStreetMap tiles for development (unlimited), Esri only for production, monitor tile request count, self-host if rate limited. LOW PRIORITY - free developer tier supports reasonable dispatch console usage.

- **mediasoup + high-frequency WebSocket interaction:** Pitfall 8 (WebSocket message queue blocking) is based on general WebSocket knowledge, not specific mediasoup testing. VALIDATION NEEDED: Load test with 1000 simulated users sending location updates while PTT active. Measure PTT button latency. If >500ms, implement separate WebSocket connection for location updates. MEDIUM PRIORITY - likely needed at production scale.

**Recommendations for implementation:**
1. Test Esri tile loading in dev environment first, fall back to OSM if CORS issues
2. Load test PTT latency under location broadcast load in Phase 4 (before adding 1000 markers)
3. Monitor browser memory in Phase 3 with React DevTools (validate cleanup pattern works)
4. Profile Android battery in Phase 5 with Battery Historian (target <5% drain/hour)
5. All gaps are testable during implementation, no blocking unknowns

## Sources

### Primary (HIGH confidence - official documentation)
- [React Leaflet Official Docs](https://react-leaflet.js.org/) - Installation, React 18 compatibility, API reference
- [Leaflet Official Docs](https://leafletjs.com/) - DivIcon, TileLayer, map.remove(), plugin ecosystem
- [Android BatteryManager API](https://developer.android.com/reference/kotlin/android/os/BatteryManager) - Battery monitoring best practices
- [Android Battery Optimization](https://developer.android.com/develop/sensors-and-location/location/battery) - Motion-aware intervals, priority levels
- [React Context Best Practices](https://kentcdodds.com/blog/application-state-management-with-react) - Kent C. Dodds
- [RFC 6455 WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455) - Protocol versioning patterns
- Existing codebase: `src/shared/protocol.ts`, `web-ui/src/context/ChannelContext.jsx`, `android/app/src/main/java/com/voiceping/android/data/location/`

### Secondary (MEDIUM confidence - verified community sources)
- [Optimizing Leaflet Performance with Large Markers](https://medium.com/@silvajohnny777/optimizing-leaflet-performance-with-a-large-number-of-markers-0dea18c2ec99) - Benchmarks: 10K+ canvas vs <300 DOM
- [React Context Performance Issues](https://www.tenxdeveloper.com/blog/optimizing-react-context-performance) - Re-render patterns, splitting contexts
- [Leaflet Memory Leak GitHub Issue](https://github.com/PaulLeCam/react-leaflet/issues/941) - Cleanup pattern from maintainer
- [React 18 Strict Mode Support](https://github.com/PaulLeCam/react-leaflet/issues/963) - Double-mount handling
- [5 Must-Have Fleet Dispatch Features](https://www.elevatecodedigital.com/2025/12/5-must-have-features-in-fleet-dispatch.html) - Industry standards
- [Map UI Design Patterns](https://mapuipatterns.com/) - Click vs hover interaction research
- [Tooltip Guidelines - Nielsen Norman Group](https://www.nngroup.com/articles/tooltip-guidelines/) - 67% prefer click for details

### Tertiary (LOW confidence - needs validation)
- WebSocket saturation with mediasoup (inference from general WebSocket knowledge, needs load testing)
- Esri free tier rate limits (mentioned but exact numbers not confirmed)

---
*Research completed: 2026-02-16*
*Ready for roadmap: yes*
