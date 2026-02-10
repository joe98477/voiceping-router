# Roadmap: VoicePing PTT Communications Platform

## Milestones

- ✅ **v1.0 WebRTC Audio Rebuild + Web UI** - Phases 1-4 (shipped 2026-02-07)
- 🚧 **v2.0 Android Client App** - Phases 5-10 (in progress)

## Phases

<details>
<summary>✅ v1.0 WebRTC Audio Rebuild + Web UI (Phases 1-4) - SHIPPED 2026-02-07</summary>

### Phase 1: WebRTC Audio Foundation
**Goal**: Prove WebRTC audio transmission works reliably with target latency achieved for PTT use case
**Depends on**: Nothing (first phase)
**Requirements**: AUDIO-01, AUDIO-02, AUDIO-03, AUDIO-04, AUDIO-05, AUDIO-06, AUDIO-07, PTT-01, PTT-02, PTT-03, PTT-04, PTT-05, SEC-01, SEC-02, SYS-01, SYS-02
**Success Criteria** (what must be TRUE):
  1. User can press button to transmit audio and hear received audio in real-time
  2. Audio transmission latency measures under 300ms from button press to hearing audio
  3. Audio works across Chrome, Firefox, and Safari desktop browsers
  4. User receives visual feedback when PTT is blocked due to busy channel
  5. WebSocket connection automatically reconnects after temporary network loss
**Plans**: 8 plans

Plans:
- [x] 01-01-PLAN.md -- Project modernization: Node 20, TypeScript 5, mediasoup deps, shared types and protocol
- [x] 01-02-PLAN.md -- mediasoup SFU core: worker pool, router, transports, producers/consumers
- [x] 01-03-PLAN.md -- Redis state: speaker locks, channel state, session store
- [x] 01-04-PLAN.md -- Signaling server: WebSocket auth, message handlers, server entry point wiring
- [x] 01-05-PLAN.md -- Client audio pipeline: mediasoup-client device, transports, microphone, signaling client
- [x] 01-06-PLAN.md -- PTT UX: button (hold/toggle), audio feedback tones, busy state, PTT controller
- [x] 01-07-PLAN.md -- Reconnection: exponential backoff WebSocket, session recovery, connection manager
- [x] 01-08-PLAN.md -- Integration: Docker deployment, test demo page, cross-browser verification

### Phase 2: User Management & Access Control
**Goal**: Role-based permissions and organizational hierarchy enable secure multi-user coordination
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-08, AUTH-09, EVNT-01, EVNT-02, EVNT-03, EVNT-04, EVNT-05, EVNT-06, SEC-03, SEC-04, SEC-05, SYS-03, SYS-04
**Success Criteria** (what must be TRUE):
  1. Admin user can create events and assign users to Admin, Dispatch, or General roles
  2. Dispatch user can create teams and assign users to channels
  3. Dispatch user can assign and remove users from channels
  4. General user can only PTT on channels they are assigned to
  5. User authorization is checked before granting channel access
  6. System successfully handles 100 concurrent users in testing environment
**Plans**: 8 plans

Plans:
- [x] 02-01-PLAN.md -- Auth foundation: enhanced JWT claims, PermissionManager, AuditLogger, extended types/protocol
- [x] 02-02-PLAN.md -- Rate limiter (progressive slowdown), jitter buffer config, worker pool optimization
- [x] 02-03-PLAN.md -- Channel authorization enforcement: permission-checked join, heartbeat refresh, graceful revocation
- [x] 02-04-PLAN.md -- Real-time permission sync via Redis pub/sub, enhanced channel state with event association
- [x] 02-05-PLAN.md -- Dispatch PTT priority interrupt and emergency broadcast (all-channel override)
- [x] 02-06-PLAN.md -- Force-disconnect, security events backend, ban/unban management
- [x] 02-07-PLAN.md -- Integration: wire all Phase 2 modules, complete message routing, permission sync callback
- [x] 02-08-PLAN.md -- Load testing (100 concurrent users), E2E test page, Phase 2 verification

### Phase 3: Browser UI for General Users
**Goal**: Web interface enables general users to see assigned channels and communicate via PTT
**Depends on**: Phase 2
**Requirements**: UI-01, UI-02, UI-03, UI-04
**Success Criteria** (what must be TRUE):
  1. User sees list of channels they are assigned to
  2. User can click PTT button on any assigned channel to transmit audio
  3. User sees visual indicator when channel is busy
  4. User sees which user is currently speaking on each channel
**Plans**: 5 plans

Plans:
- [x] 03-01-PLAN.md -- Vite config for TS imports, auth/session foundation: tokenStorage, useAuth hook, ChannelContext provider
- [x] 03-02-PLAN.md -- Connection & PTT: useChannelConnection hook, ChannelCard with PttController wrapper
- [x] 03-03-PLAN.md -- Page assembly: ChannelList, Channels page, route wiring, Events link, CSS styling
- [x] 03-04-PLAN.md -- PERMISSION_UPDATE: global WebSocket for real-time channel list sync
- [x] 03-05-PLAN.md -- Build verification, integration fixes, and visual verification checkpoint

### Phase 4: Dispatch Multi-Channel Monitoring
**Goal**: Dispatch users can monitor and communicate on multiple channels simultaneously
**Depends on**: Phase 3
**Requirements**: DISP-01, DISP-02, DISP-03, DISP-04, DISP-05, DISP-06, UI-05, UI-06
**Success Criteria** (what must be TRUE):
  1. Dispatch user can subscribe to and monitor 10-50 channels simultaneously
  2. Dispatch user can selectively mute or unmute individual channels
  3. Dispatch user can transmit on any monitored channel using PTT
  4. Dispatch user sees visual indicators showing which channels have active speakers
  5. Dispatch user hears audio from all unmuted channels without audio mixing artifacts
**Plans**: 3 plans

Plans:
- [x] 04-01-PLAN.md -- Server-side dispatch channel limit bypass, compact DispatchChannelCard with mute toggle and activity indicators
- [x] 04-02-PLAN.md -- DispatchConsole page, ChannelGrid with team grouping, AdminDrawer, route wiring, navigation, mute persistence, channel names for general users
- [x] 04-03-PLAN.md -- Build verification, integration fixes, and visual verification checkpoint

</details>

### 🚧 v2.0 Android Client App (In Progress)

**Milestone Goal:** Native Android app that turns a phone into a pocket two-way radio for general users — screen off, device in pocket, triggered by headset PTT button, audio through headset.

#### Phase 5: Android Project Setup & WebRTC Foundation
**Goal**: Android app connects to existing mediasoup server and receives audio
**Depends on**: Phase 4 (v1.0 shipped)
**Requirements**: APP-01, APP-02, APP-03, APP-04, APP-05
**Success Criteria** (what must be TRUE):
  1. User can install and launch native Android app on device (API 26+)
  2. User can login with email/password and receive JWT token
  3. User can select active event from event picker screen
  4. User sees channel list grouped by team with team labels
  5. App remembers last selected event on next launch
  6. App successfully connects to mediasoup server via WebSocket and joins a channel
  7. App receives audio from server through mediasoup Consumer (device speaker plays audio)
**Plans**: 5 plans

Plans:
- [ ] 05-01-PLAN.md -- Android project scaffolding: Gradle, Hilt, Material 3 dark theme, domain models, app shell
- [ ] 05-02-PLAN.md -- Auth & Login: TokenManager, AuthApi, AuthRepository, LoginScreen, auto-login, loading screen
- [ ] 05-03-PLAN.md -- Networking: SignalingClient (WebSocket), MediasoupClient, AudioRouter, signaling DTOs
- [ ] 05-04-PLAN.md -- Event picker & Channel list: EventPickerScreen, ChannelListScreen, BottomBar, ProfileDrawer, ConnectionBanner
- [ ] 05-05-PLAN.md -- Channel join & audio receive: ChannelRepository, join/leave use cases, speaker changes, end-to-end integration + verification

#### Phase 6: Single-Channel PTT & Audio Transmission
**Goal**: User can transmit and receive PTT audio in a single channel with full bidirectional flow
**Depends on**: Phase 5
**Requirements**: APTT-01, APTT-02, APTT-03, APTT-04, APTT-05, APTT-06, AUD-01
**Success Criteria** (what must be TRUE):
  1. User can press and hold PTT button in channel to transmit audio (mic to server)
  2. User can release PTT button to stop transmission
  3. User sees busy state when channel is occupied (speaker name + pulse animation)
  4. User hears received audio from monitored channel through device speaker
  5. User sees speaker name and animated indicator for active transmissions
  6. User gets PTT feedback (server-confirmed visual response on press, error tone + haptic if denied)
  7. User can toggle between earpiece and speaker audio output
**Plans**: 5 plans

Plans:
- [ ] 06-01-PLAN.md -- PTT domain models, DataStore settings, TonePlayer, HapticFeedback
- [ ] 06-02-PLAN.md -- PTT engine: PttManager state machine, AudioCaptureManager, send transport, AudioRouter enhancement
- [ ] 06-03-PLAN.md -- PTT UI: PttButton composable, BottomBar update, ChannelRow speaker indicators
- [ ] 06-04-PLAN.md -- Integration: wire PttManager to ChannelRepository/ViewModel, settings in ProfileDrawer, toggle mode enforcement
- [ ] 06-05-PLAN.md -- Human verification: all 7 success criteria on device

#### Phase 7: Foreground Service & Background Audio
**Goal**: App functions as pocket radio with screen off and lock screen PTT operation
**Depends on**: Phase 6
**Requirements**: BG-01, BG-02, BG-03, BG-04, AUD-04, AUD-05
**Success Criteria** (what must be TRUE):
  1. App runs as foreground service keeping WebSocket and audio alive when screen is off
  2. Persistent notification shows active channel name and provides PTT controls
  3. User can operate PTT from lock screen via hardware button (intent broadcast)
  4. Partial wake lock keeps CPU alive for audio processing when screen is locked
  5. Audio playback continues through foreground service with screen off (pocket radio mode)
  6. Audio pauses during incoming phone calls and automatically resumes after call ends
  7. App survives Android Doze mode with wake lock and heartbeat management
**Plans**: 3 plans

Plans:
- [ ] 07-01-PLAN.md -- Service infrastructure: ChannelMonitoringService, NotificationActionReceiver, manifest, call interruption beep
- [ ] 07-02-PLAN.md -- Audio focus & phone call handling: AudioRouter focus listener, PttManager forceReleasePtt
- [ ] 07-03-PLAN.md -- Integration: service lifecycle in ChannelRepository, phone call wiring, mute state, battery optimization prompt

#### Phase 8: Multi-Channel Monitoring & Scan Mode
**Goal**: User can monitor up to 5 channels simultaneously with automatic scan mode switching
**Depends on**: Phase 7
**Requirements**: SCAN-01, SCAN-02, SCAN-03, SCAN-04, SCAN-05, SCAN-06, SCAN-07, SCAN-08
**Success Criteria** (what must be TRUE):
  1. User can monitor up to 5 channels simultaneously with mixed audio playback
  2. User can set a primary/default channel for monitoring
  3. Bottom bar shows primary channel with PTT button by default
  4. Bottom bar auto-switches to active channel when someone transmits (scan mode active)
  5. Bottom bar drops back to primary channel after transmission ends (configurable delay)
  6. User can manually tap bottom bar to switch between monitored channels
  7. Unmonitored/muted channels unsubscribe from audio to save bandwidth
  8. User can configure scan mode behavior in settings (auto-switch delay, enable/disable)
**Plans**: TBD

Plans:
- [ ] 08-01: TBD
- [ ] 08-02: TBD

#### Phase 9: Hardware PTT & Bluetooth Integration
**Goal**: User can operate PTT via hardware buttons and Bluetooth headset for hands-free operation
**Depends on**: Phase 8
**Requirements**: HW-01, HW-02, HW-03, HW-04, HW-05, AUD-02, AUD-03, APP-06
**Success Criteria** (what must be TRUE):
  1. User can map volume keys as PTT button (configurable in settings)
  2. User can use dedicated PTT button on rugged phones (Sonim, Kyocera)
  3. User can map Bluetooth headset PTT button to trigger transmission
  4. User can configure hardware button mapping in settings screen
  5. Hardware PTT button targets current bottom-bar channel (scan mode aware)
  6. Audio auto-routes to Bluetooth headset when Bluetooth device is connected
  7. User can adjust volume per channel independently
  8. App can auto-start as foreground service on device boot (optional setting, off by default)
**Plans**: TBD

Plans:
- [ ] 09-01: TBD
- [ ] 09-02: TBD

#### Phase 10: Network Resilience & UX Polish
**Goal**: Production-ready app with cellular network resilience and polished user experience
**Depends on**: Phase 9
**Requirements**: NET-01, NET-02, NET-03, NET-04, UX-01, UX-02, UX-03, UX-04
**Success Criteria** (what must be TRUE):
  1. App auto-reconnects silently after network loss without user intervention
  2. App handles WiFi to cellular handoff gracefully without dropping audio
  3. App shows offline state with cached channel list when disconnected
  4. App shows small reconnecting indicator during reconnection attempts
  5. User feels haptic feedback for PTT press, release, busy, and transmission events
  6. User sees network quality indicator (latency, connection status) in UI
  7. User can view transmission history (last 10-20 transmissions) when tapping into a channel
  8. Settings screen provides controls for scan mode, button mapping, audio output, and auto-start
**Plans**: TBD

Plans:
- [ ] 10-01: TBD
- [ ] 10-02: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. WebRTC Audio Foundation | v1.0 | 8/8 | Complete | 2026-02-07 |
| 2. User Management & Access Control | v1.0 | 8/8 | Complete | 2026-02-07 |
| 3. Browser UI for General Users | v1.0 | 5/5 | Complete | 2026-02-07 |
| 4. Dispatch Multi-Channel Monitoring | v1.0 | 3/3 | Complete | 2026-02-07 |
| 5. Android Project Setup & WebRTC Foundation | v2.0 | 0/5 | Not started | - |
| 6. Single-Channel PTT & Audio Transmission | v2.0 | 0/5 | Not started | - |
| 7. Foreground Service & Background Audio | v2.0 | 0/TBD | Not started | - |
| 8. Multi-Channel Monitoring & Scan Mode | v2.0 | 0/TBD | Not started | - |
| 9. Hardware PTT & Bluetooth Integration | v2.0 | 0/TBD | Not started | - |
| 10. Network Resilience & UX Polish | v2.0 | 0/TBD | Not started | - |

---
*Roadmap created: 2026-02-06*
*Last updated: 2026-02-10 after Phase 6 planning*
