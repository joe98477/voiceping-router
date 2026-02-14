# Roadmap: VoicePing PTT Communications Platform

## Milestones

- ✅ **v1.0 WebRTC Audio Rebuild + Web UI** - Phases 1-4 (shipped 2026-02-07)
- ✅ **v2.0 Android Client App** - Phases 5-10 (shipped 2026-02-13)
- ✅ **v3.0 mediasoup Library Integration** - Phases 11-15 (shipped 2026-02-15)

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

## Progress

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

---
*Roadmap created: 2026-02-06*
*Last updated: 2026-02-15 after v3.0 milestone shipped*
