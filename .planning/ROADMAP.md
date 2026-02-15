# Roadmap: VoicePing PTT Communications Platform

## Milestones

- ✅ **v1.0 WebRTC Audio Rebuild + Web UI** - Phases 1-4 (shipped 2026-02-07)
- ✅ **v2.0 Android Client App** - Phases 5-10 (shipped 2026-02-13)
- ✅ **v3.0 mediasoup Library Integration** - Phases 11-15 (shipped 2026-02-15)
- 🚧 **v4.0 Production Hardening & Location** - Phases 16-20 (in progress)

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

### 🚧 v4.0 Production Hardening & Location (In Progress)

**Milestone Goal:** Harden audio reliability, optimize power/bandwidth, add location tracking for dispatch, and security-audit the full stack.

#### Phase 16: Permission Management
**Goal**: Upfront permission education and graceful degradation
**Depends on**: Phase 15
**Requirements**: PERM-01, PERM-02, PERM-03, PERM-04
**Success Criteria** (what must be TRUE):
  1. User sees permission education screen on first launch explaining mic/location/notification needs
  2. User can tap action requiring denied permission and app shows rationale dialog re-prompting
  3. App gracefully degrades when permission revoked mid-use (shows error state, no crash)
  4. App redirects to system Settings after 2 denials to prevent infinite prompt loops
**Plans**: 2 plans

Plans:
- [x] 16-01: PermissionManager singleton with denial tracking and Settings redirect
- [x] 16-02: First-launch education flow and contextual rationale dialogs

#### Phase 17: Audio Reliability
**Goal**: Fix intermittent PTT silence and harden audio stream timing
**Depends on**: Phase 16 (permissions enable mic access)
**Requirements**: AUDIO-01, AUDIO-02, AUDIO-03, AUDIO-04, AUDIO-05, AUDIO-06
**Success Criteria** (what must be TRUE):
  1. User presses PTT and audio transmits reliably without intermittent silence failures
  2. Producer creation retries automatically on failure and user sees "retrying" feedback
  3. Orphaned/stale transports cleaned up after 15s disconnect detected
  4. User hears intelligible speech without excessive latency or packet loss artifacts
  5. User sees visual confirmation that transmission was received by listeners
**Plans**: 3 plans

Plans:
- [x] 17-01: Producer retry with exponential backoff, PttState.Error, Opus codec tuning, error feedback
- [x] 17-02: Server ACK for transmission confirmation, PTT button flash, confirmation tone, dev stats screen
- [x] 17-03: Transport health monitoring with auto-cleanup, auto-rejoin, amber PTT state

#### Phase 18: Location Tracking
**Goal**: Adaptive location tracking with motion-aware throttling for dispatch
**Depends on**: Phase 16 (permissions enable location access)
**Requirements**: LOC-01, LOC-02, LOC-03, LOC-04, LOC-05, LOC-06, LOC-07
**Success Criteria** (what must be TRUE):
  1. App collects precise GPS location every 5 minutes when user moving
  2. App collects general location every 60 seconds for battery efficiency
  3. App reduces location frequency when user stationary (detected via motion sensors)
  4. App skips redundant location sends if recent update already transmitted
  5. Server API receives and stores location updates from Android clients
  6. Location data documented for future dispatch web UI map integration
  7. Background location works via foreground service with Android 14+ compliance
**Plans**: 3 plans

Plans:
- [ ] 18-01: Android location tracking foundation (LocationTracker, MotionDetector, LocationManager, permissions)
- [ ] 18-02: Server location infrastructure (SQLite storage, dispatch broadcast, WebSocket handlers)
- [ ] 18-03: Android-server integration (WebSocket transmission, offline queue, foreground service type, debug display)

#### Phase 19: Security Hardening & Code Quality
**Goal**: Security audit full stack and optimize Android codebase
**Depends on**: Phase 17 (audio hardening complete), Phase 18 (location infrastructure in place)
**Requirements**: SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, CODE-01, CODE-02
**Success Criteria** (what must be TRUE):
  1. All signaling connections use WSS (TLS WebSocket) and app rejects WS connections
  2. All API endpoints verified as authenticated with no unauthenticated gaps
  3. Network security config blocks cleartext traffic in release builds
  4. Android codebase scanned for vulnerabilities and critical issues fixed
  5. WebRTC media streams verified as using DTLS encryption
  6. Android codebase cleaned up with unused code and dead imports removed
  7. Performance optimized with unnecessary allocations eliminated
**Plans**: 3 plans

Plans:
- [ ] 19-01: TLS/WSS enforcement and network security config hardening
- [ ] 19-02: API authentication audit and WebRTC DTLS verification
- [ ] 19-03: Android codebase cleanup and performance optimization

#### Phase 20: Power Optimization & Validation
**Goal**: Battery profiling and adaptive power management with all v4.0 features active
**Depends on**: Phase 17 (audio complete), Phase 18 (location complete)
**Requirements**: PWR-01, PWR-02, PWR-03, PWR-04
**Success Criteria** (what must be TRUE):
  1. Wake lock released after 30 seconds of audio inactivity and reacquired on speaker activity
  2. Network quality polling adjusts dynamically (15s idle channels, 5s active channels)
  3. Location updates batched efficiently for server transmission
  4. Battery consumption validated at less than 6%/hour with screen off and all features active
**Plans**: 2 plans

Plans:
- [ ] 20-01: Adaptive wake lock scoping and dynamic network polling
- [ ] 20-02: Location batching optimization and battery profiling validation

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → ... → 20

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
| 18. Location Tracking | v4.0 | 0/3 | Not started | - |
| 19. Security Hardening & Code Quality | v4.0 | 0/3 | Not started | - |
| 20. Power Optimization & Validation | v4.0 | 0/2 | Not started | - |

---
*Roadmap created: 2026-02-06*
*Last updated: 2026-02-15 after Phase 17 completion*
