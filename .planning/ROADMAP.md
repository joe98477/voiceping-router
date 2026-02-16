# Roadmap: VoicePing PTT Communications Platform

## Milestones

- ✅ **v1.0 WebRTC Audio Rebuild + Web UI** - Phases 1-4 (shipped 2026-02-07)
- ✅ **v2.0 Android Client App** - Phases 5-10 (shipped 2026-02-13)
- ✅ **v3.0 mediasoup Library Integration** - Phases 11-15 (shipped 2026-02-15)
- ✅ **v4.0 Production Hardening & Location** - Phases 16-20 (shipped 2026-02-16)
- 🚧 **v5.0 Dispatch Map View** - Phases 21-26 (in progress)

## Phases

<details>
<summary>✅ v1.0 WebRTC Audio Rebuild + Web UI (Phases 1-4) - SHIPPED 2026-02-07</summary>

**Delivered:** WebRTC audio subsystem rebuilt with mediasoup SFU, browser UI for general and dispatch users, role-based permissions, Docker deployment.

**Stats:** 4 phases (1-4), 24 plans, ~4.2 hours execution time

### Phase 1: WebRTC Audio Foundation
**Goal**: mediasoup SFU with WebRTC audio infrastructure
**Plans**: 8 plans (complete)

### Phase 2: User Management & Access Control
**Goal**: JWT authentication with role-based access control
**Plans**: 8 plans (complete)

### Phase 3: Browser UI for General Users
**Goal**: React web UI for channel participation
**Plans**: 5 plans (complete)

### Phase 4: Dispatch Multi-Channel Monitoring
**Goal**: Multi-channel monitoring for dispatch role
**Plans**: 3 plans (complete)

See: `.planning/milestones/v1.0-ROADMAP.md` for full details.

</details>

<details>
<summary>✅ v2.0 Android Client App (Phases 5-10) - SHIPPED 2026-02-13</summary>

**Delivered:** Native Android PTT client app — pocket two-way radio with hardware button support, multi-channel scan mode, and network resilience.

**Stats:** 6 phases (5-10), 26 plans, 70 commits, 99 files, 9,233 LOC Kotlin

### Phase 5: Android Project Setup & WebRTC Foundation
**Goal**: Kotlin app with login, event picker, channel list
**Plans**: 5 plans (complete)

### Phase 6: Single-Channel PTT & Audio Transmission
**Goal**: Press-and-hold PTT with busy state, audio feedback, haptics
**Plans**: 5 plans (complete)

### Phase 7: Foreground Service & Background Audio
**Goal**: Screen-off operation with persistent notification
**Plans**: 3 plans (complete)

### Phase 8: Multi-Channel Monitoring & Scan Mode
**Goal**: Monitor up to 5 channels with auto-switch
**Plans**: 4 plans (complete)

### Phase 9: Hardware PTT & Bluetooth Integration
**Goal**: Volume keys and Bluetooth headset button support
**Plans**: 4 plans (complete)

### Phase 10: Network Resilience & UX Polish
**Goal**: Auto-reconnect, WiFi/cellular handoff, offline caching
**Plans**: 5 plans (complete)

See: `.planning/milestones/v2.0-ROADMAP.md` for full details.

</details>

<details>
<summary>✅ v3.0 mediasoup Library Integration (Phases 11-15) - SHIPPED 2026-02-15</summary>

**Delivered:** Real WebRTC audio on Android — replaced MediasoupClient stubs with libmediasoup-android 0.21.0 for bidirectional voice communication, validated on physical hardware.

**Stats:** 5 phases (11-15), 10 plans, 38 commits, +1,102/-526 LOC Kotlin

### Phase 11: Library Upgrade and WebRTC Foundation
**Goal**: Establish WebRTC subsystem and resolve AudioManager ownership
**Plans**: 2 plans (complete)

### Phase 12: Device and RecvTransport Integration
**Goal**: Wire RecvTransport and Consumer creation for receiving remote audio
**Plans**: 2 plans (complete)

### Phase 13: SendTransport and Producer Integration
**Goal**: Wire SendTransport and Producer creation for PTT audio transmission
**Plans**: 2 plans (complete)

### Phase 14: Cleanup Lifecycle and Reconnection Resilience
**Goal**: Ordered disposal and Mutex state machine for production-ready lifecycle
**Plans**: 2 plans (complete)

### Phase 15: Release Build Validation and Device Testing
**Goal**: ProGuard rules and physical device end-to-end audio validation
**Plans**: 2 plans (complete)

See: `.planning/milestones/v3.0-ROADMAP.md` for full details.

</details>

<details>
<summary>✅ v4.0 Production Hardening & Location (Phases 16-20) - SHIPPED 2026-02-16</summary>

**Delivered:** Production-ready audio reliability, adaptive location tracking, full-stack security hardening, and power optimization for the Android PTT client.

**Stats:** 5 phases (16-20), 13 plans, 61 commits, 87 source files, +5,516/-1,315 LOC

### Phase 16: Permission Management
**Goal**: Upfront permission education and graceful degradation
**Plans**: 2 plans (complete)

### Phase 17: Audio Reliability
**Goal**: Fix intermittent PTT silence and harden audio stream timing
**Plans**: 3 plans (complete)

### Phase 18: Location Tracking
**Goal**: Adaptive location tracking with motion-aware throttling for dispatch
**Plans**: 3 plans (complete)

### Phase 19: Security Hardening & Code Quality
**Goal**: Security audit full stack and optimize Android codebase
**Plans**: 3 plans (complete)

### Phase 20: Power Optimization & Validation
**Goal**: Battery profiling and adaptive power management with all v4.0 features active
**Plans**: 2 plans (complete)

See: `.planning/milestones/v4.0-ROADMAP.md` for full details.

</details>

### 🚧 v5.0 Dispatch Map View (In Progress)

**Milestone Goal:** Add real-time interactive satellite map to dispatch console showing field worker locations with battery telemetry and configurable status popups.

- [x] **Phase 21: Backend Protocol Extension** - Add battery telemetry to server and Android with backward-compatible protocol (completed 2026-02-16)
- [ ] **Phase 22: Web Layout Split** - Create CSS Grid split layout for channels and map panels
- [ ] **Phase 23: Map Foundation** - Integrate Leaflet with proper cleanup pattern and tile layers
- [ ] **Phase 24: Location State and Real-Time Markers** - Connect WebSocket location broadcasts to map markers
- [ ] **Phase 25: Interactive Markers and Motion State** - Add status popups, motion indicators, and clustering
- [ ] **Phase 26: Map Controls and Polish** - Add layer switching, auto-fit, search, and settings persistence

## Phase Details

### Phase 21: Backend Protocol Extension
**Goal**: Extend location protocol with optional battery percentage field for dispatch monitoring
**Depends on**: Phase 20 (v4.0 location tracking complete)
**Requirements**: TELEM-01, TELEM-02, TELEM-03
**Success Criteria** (what must be TRUE):
  1. Android client includes battery percentage in LOCATION_UPDATE messages
  2. Server stores battery percentage in location database when present
  3. Server broadcasts battery percentage in LOCATION_BROADCAST when available
  4. Old Android clients without battery field continue to work (backward compatibility validated)
  5. Old web clients ignore unknown battery field (forward compatibility validated)
**Plans**: 2 plans

Plans:
- [ ] 21-01-PLAN.md — Server-side telemetry extension (types, schema, handlers, broadcaster, LOW_BATTERY_ALERT)
- [ ] 21-02-PLAN.md — Android telemetry collection (battery %, power-save, network type in LocationUpdate)

### Phase 22: Web Layout Split
**Goal**: Create split-panel dispatch console layout with channels on left and map panel on right
**Depends on**: Phase 21
**Requirements**: LAYOUT-01
**Success Criteria** (what must be TRUE):
  1. Dispatch console displays channels panel on left and empty map panel on right using CSS Grid
  2. Layout optimized for desktop (1200px+ widescreen), channels panel fixed-width, map panel fills remaining space
  3. Existing channel monitoring functionality works unchanged in new layout
  4. Map panel div exists and is sized correctly for Leaflet integration
**Plans**: TBD

Plans:
- [ ] 22-01: TBD

### Phase 23: Map Foundation
**Goal**: Integrate Leaflet map library with proper cleanup pattern to prevent memory leaks
**Depends on**: Phase 22
**Requirements**: MAP-01, CTRL-02
**Success Criteria** (what must be TRUE):
  1. Dispatch user can view an interactive satellite map (Esri World Imagery tiles) in the map panel
  2. Map component properly cleans up on unmount (no memory leaks in React Strict Mode)
  3. Dispatch user can switch between satellite and street map layers
  4. Map controls (zoom, pan) work correctly alongside channel monitoring
**Plans**: TBD

Plans:
- [ ] 23-01: TBD
- [ ] 23-02: TBD

### Phase 24: Location State and Real-Time Markers
**Goal**: Connect WebSocket location broadcasts to map markers showing real-time user positions
**Depends on**: Phase 23
**Requirements**: MAP-02, MAP-03, MAP-04, LAYOUT-02
**Success Criteria** (what must be TRUE):
  1. Map displays user positions as markers with radio icons when location data arrives
  2. Each marker shows the associated username as a label
  3. Markers update in real-time as LOCATION_BROADCAST messages arrive via WebSocket
  4. Map loads all known user positions on initial connection via LOCATION_QUERY
  5. LocationContext is separate from ChannelContext (high-frequency location updates don't re-render channels)
**Plans**: TBD

Plans:
- [ ] 24-01: TBD
- [ ] 24-02: TBD

### Phase 25: Interactive Markers and Motion State
**Goal**: Add status popups, motion indicators, staleness treatment, and clustering for production scale
**Depends on**: Phase 24
**Requirements**: MAP-05, MAP-06, MAP-07, POPUP-01, POPUP-02, POPUP-03, POPUP-04, POPUP-05, POPUP-06, CTRL-01
**Success Criteria** (what must be TRUE):
  1. Hovering over a marker shows a status card with location, motion, channel, PTT status, connection, and battery data
  2. Markers display motion state visually (distinct icons or colors for STILL/WALKING/DRIVING)
  3. Markers for users with no update in 5+ minutes appear visually faded (stale indicator)
  4. Nearby markers cluster when zoomed out and expand when zoomed in (handles 200+ markers without performance collapse)
  5. Dispatch user can zoom and pan the map interactively without lag
**Plans**: TBD

Plans:
- [ ] 25-01: TBD
- [ ] 25-02: TBD
- [ ] 25-03: TBD

### Phase 26: Map Controls and Polish
**Goal**: Add auto-fit bounds, user search, configurable popup settings, and UX polish
**Depends on**: Phase 25
**Requirements**: CTRL-03, CTRL-04, SETTINGS-01, SETTINGS-02
**Success Criteria** (what must be TRUE):
  1. Map auto-fits bounds on initial load to show all visible user markers
  2. Dispatch user can search for a user by name and map centers on their marker
  3. Dispatch user can configure which fields appear in the status popup via settings
  4. Popup field preferences persist across browser sessions in localStorage
  5. Map remembers zoom and center position across page reloads
**Plans**: TBD

Plans:
- [ ] 26-01: TBD
- [ ] 26-02: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 21 → 22 → 23 → 24 → 25 → 26

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. WebRTC Audio Foundation | v1.0 | 8/8 | Complete | 2026-02-07 |
| 2. User Management & Access Control | v1.0 | 8/8 | Complete | 2026-02-07 |
| 3. Browser UI for General Users | v1.0 | 5/5 | Complete | 2026-02-07 |
| 4. Dispatch Multi-Channel Monitoring | v1.0 | 3/3 | Complete | 2026-02-07 |
| 5. Android Project Setup & WebRTC Foundation | v2.0 | 5/5 | Complete | 2026-02-09 |
| 6. Single-Channel PTT & Audio Transmission | v2.0 | 5/5 | Complete | 2026-02-10 |
| 7. Foreground Service & Background Audio | v2.0 | 3/3 | Complete | 2026-02-11 |
| 8. Multi-Channel Monitoring & Scan Mode | v2.0 | 4/4 | Complete | 2026-02-11 |
| 9. Hardware PTT & Bluetooth Integration | v2.0 | 4/4 | Complete | 2026-02-12 |
| 10. Network Resilience & UX Polish | v2.0 | 5/5 | Complete | 2026-02-13 |
| 11. Library Upgrade | v3.0 | 2/2 | Complete | 2026-02-13 |
| 12. RecvTransport | v3.0 | 2/2 | Complete | 2026-02-13 |
| 13. SendTransport | v3.0 | 2/2 | Complete | 2026-02-13 |
| 14. Lifecycle | v3.0 | 2/2 | Complete | 2026-02-13 |
| 15. Validation | v3.0 | 2/2 | Complete | 2026-02-15 |
| 16. Permission Management | v4.0 | 2/2 | Complete | 2026-02-15 |
| 17. Audio Reliability | v4.0 | 3/3 | Complete | 2026-02-15 |
| 18. Location Tracking | v4.0 | 3/3 | Complete | 2026-02-15 |
| 19. Security Hardening & Code Quality | v4.0 | 3/3 | Complete | 2026-02-15 |
| 20. Power Optimization & Validation | v4.0 | 2/2 | Complete | 2026-02-16 |
| 21. Backend Protocol Extension | v5.0 | Complete    | 2026-02-16 | - |
| 22. Web Layout Split | v5.0 | 0/1 | Not started | - |
| 23. Map Foundation | v5.0 | 0/2 | Not started | - |
| 24. Location State and Real-Time Markers | v5.0 | 0/2 | Not started | - |
| 25. Interactive Markers and Motion State | v5.0 | 0/3 | Not started | - |
| 26. Map Controls and Polish | v5.0 | 0/2 | Not started | - |

---
*Roadmap created: 2026-02-06*
*Last updated: 2026-02-16 after v5.0 roadmap creation*
