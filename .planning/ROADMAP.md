# Roadmap: VoicePing PTT Communications Platform

## Milestones

- ✅ **v1.0 WebRTC Audio Rebuild + Web UI** - Phases 1-4 (shipped 2026-02-07)
- ✅ **v2.0 Android Client App** - Phases 5-10 (shipped 2026-02-13)
- 🚧 **v3.0 mediasoup Library Integration** - Phases 11-15 (in progress)

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

### 🚧 v3.0 mediasoup Library Integration (In Progress)

**Milestone Goal:** Wire the actual libmediasoup-android library into the existing MediasoupClient skeleton to enable real bidirectional WebRTC voice audio on Android.

#### Phase 11: Library Upgrade and WebRTC Foundation
**Goal**: Establish WebRTC subsystem and resolve AudioManager ownership before audio integration
**Depends on**: Phase 10 (v2.0 complete)
**Requirements**: WEBRTC-01, WEBRTC-02, WEBRTC-03, WEBRTC-04
**Success Criteria** (what must be TRUE):
  1. App compiles with libmediasoup-android 0.21.0 dependency
  2. PeerConnectionFactory initializes with echo cancellation and noise suppression enabled
  3. AudioRouter coordinates with WebRTC's AudioDeviceModule without MODE_IN_COMMUNICATION conflicts
  4. Device loads server RTP capabilities and returns its own RTP capabilities
**Plans**: 2 plans

Plans:
- [x] 11-01-PLAN.md -- Dependency upgrade, WebRTC init, AudioRouter coordination flag
- [x] 11-02-PLAN.md -- PeerConnectionFactory with AudioDeviceModule, Device RTP capabilities

#### Phase 12: Device and RecvTransport Integration
**Goal**: Wire RecvTransport and Consumer creation for receiving remote audio producers
**Depends on**: Phase 11 (WebRTC foundation established)
**Requirements**: RECV-01, RECV-02, RECV-03, RECV-04, RECV-05
**Success Criteria** (what must be TRUE):
  1. RecvTransport created with server parameters and onConnect callback bridges DTLS to signaling
  2. Consumer created from remote producer and audio playback begins automatically
  3. Per-consumer volume control adjusts playback level (user can change channel volume, audio level changes)
  4. Consumer closes cleanly when user leaves channel (no orphaned resources)
  5. Consumer statistics available for network quality indicator (packet loss and jitter displayed)
**Plans**: 2 plans

Plans:
- [ ] 12-01-PLAN.md -- RecvTransport creation, Consumer lifecycle, volume control, cleanup
- [ ] 12-02-PLAN.md -- Consumer statistics for network quality indicator

#### Phase 13: SendTransport and Producer Integration
**Goal**: Wire SendTransport and Producer creation for transmitting local microphone audio via PTT
**Depends on**: Phase 12 (RecvTransport validated)
**Requirements**: SEND-01, SEND-02, SEND-03, SEND-04, SEND-05
**Success Criteria** (what must be TRUE):
  1. SendTransport created with server parameters, onConnect and onProduce callbacks bridge to signaling
  2. AudioTrack created via PeerConnectionFactory for microphone capture
  3. Producer created with Opus PTT config when user presses PTT button (audio transmitted)
  4. Producer closed and audio capture stopped when user releases PTT button (audio stops)
  5. AudioCaptureManager removed from codebase (library handles audio capture)
**Plans**: TBD

Plans:
- [ ] 13-01: TBD (awaiting planning)

#### Phase 14: Cleanup Lifecycle and Reconnection Resilience
**Goal**: Implement ordered disposal and state machine for production-ready lifecycle management
**Depends on**: Phase 13 (Producer/Consumer working)
**Requirements**: LIFE-01, LIFE-02, LIFE-03, LIFE-04
**Success Criteria** (what must be TRUE):
  1. Resources disposed in correct order on disconnect (producers → consumers → transports, no crashes)
  2. transportclose events handled for both send and recv transports (reconnection triggered)
  3. Reconnection uses Mutex-based state machine (no duplicate transports during network flapping)
  4. Rapid PTT press/release doesn't cause duplicate producers (state transitions atomic)
**Plans**: TBD

Plans:
- [ ] 14-01: TBD (awaiting planning)

#### Phase 15: Release Build Validation and Device Testing
**Goal**: Verify ProGuard rules and validate end-to-end audio on physical Android device
**Depends on**: Phase 14 (Lifecycle complete)
**Requirements**: VALID-01, VALID-02, VALID-03
**Success Criteria** (what must be TRUE):
  1. ProGuard/R8 rules verified — release APK built successfully without JNI class stripping errors
  2. Release APK tested on physical Android device with end-to-end audio (user can transmit and receive)
  3. Battery profiling shows no excessive drain from WebRTC threads (under 10%/hour with screen off)
**Plans**: TBD

Plans:
- [ ] 15-01: TBD (awaiting planning)

## Progress

**Execution Order:**
Phases execute in numeric order: 11 → 12 → 13 → 14 → 15

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
| 12. RecvTransport | v3.0 | 0/TBD | Not started | - |
| 13. SendTransport | v3.0 | 0/TBD | Not started | - |
| 14. Lifecycle | v3.0 | 0/TBD | Not started | - |
| 15. Validation | v3.0 | 0/TBD | Not started | - |

---
*Roadmap created: 2026-02-06*
*Last updated: 2026-02-13 after v3.0 roadmap creation*
