# Requirements: VoicePing PTT Communications Platform

**Defined:** 2026-02-06 (v1.0), updated 2026-02-08 (v2.0)
**Core Value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events

## v1.0 Requirements (Milestone 1 — COMPLETE)

### WebRTC Audio Infrastructure

- [x] **AUDIO-01**: mediasoup v3 SFU server integrated with existing Node.js backend
- [x] **AUDIO-02**: WebRTC signaling flow (offer/answer/ICE candidates) via existing WebSocket connections
- [x] **AUDIO-03**: Opus audio codec configured for PTT use case (CBR mode, DTX disabled)
- [x] **AUDIO-04**: STUN server configured for NAT traversal
- [x] **AUDIO-05**: TURN server infrastructure for firewall-restricted clients
- [x] **AUDIO-06**: Cross-browser support (Chrome, Firefox, Safari desktop)
- [x] **AUDIO-07**: Audio stream quality below 300ms latency (press-to-talk to hearing)

### PTT Core Functionality

- [x] **PTT-01**: User can press-to-talk (activate audio transmission on button press)
- [x] **PTT-02**: User can release-to-stop (stop transmission on button release)
- [x] **PTT-03**: System prevents simultaneous talkers in same channel (busy state management)
- [x] **PTT-04**: User receives feedback when PTT is blocked (busy indicator)
- [x] **PTT-05**: Audio transmission starts within 100-300ms of PTT activation

### Security & Access Control

- [x] **SEC-01**: User authenticates with JWT token
- [x] **SEC-02**: WebSocket connections use WSS (TLS/SSL)
- [x] **SEC-03**: User authorization checked before channel access
- [x] **SEC-04**: Session persists across page refresh

### Browser UI

- [x] **UI-01**: Web UI displays user's assigned channels
- [x] **UI-02**: Web UI shows PTT button for each channel
- [x] **UI-03**: Web UI shows channel busy state
- [x] **UI-04**: Web UI shows active speaker in channel
- [x] **UI-05**: Dispatch UI shows all monitored channels with mute toggles
- [x] **UI-06**: Dispatch UI indicates which channels have activity

### System Reliability

- [x] **SYS-01**: User can reconnect after temporary network loss
- [x] **SYS-02**: User's channel memberships restored on reconnection

## v2.0 Requirements (Milestone 2 — Android Client App)

### App Foundation

- [ ] **APP-01**: User can install and launch Kotlin native Android app (API 26+, Material 3)
- [ ] **APP-02**: User can login with email and password (JWT from control plane)
- [ ] **APP-03**: User can select active event from event picker screen
- [ ] **APP-04**: User sees channel list grouped by team with activity indicators
- [ ] **APP-05**: App remembers last selected event on next launch
- [ ] **APP-06**: App auto-starts as foreground service on device boot (optional setting, off by default)

### PTT Core (Android)

- [ ] **APTT-01**: User can press and hold PTT button to transmit audio on a channel
- [ ] **APTT-02**: User can release PTT button to stop transmission
- [ ] **APTT-03**: User sees busy state when channel is occupied (speaker name + pulse)
- [ ] **APTT-04**: User hears received audio from monitored channels
- [ ] **APTT-05**: User sees speaker name and animated indicator for active transmissions
- [ ] **APTT-06**: User gets optimistic PTT feedback (instant visual response, revert if denied)

### Audio & Routing

- [ ] **AUD-01**: User can toggle between earpiece and speaker output
- [ ] **AUD-02**: Audio auto-routes to Bluetooth headset when connected
- [ ] **AUD-03**: User can adjust volume per channel
- [ ] **AUD-04**: Audio pauses during phone calls and resumes after
- [ ] **AUD-05**: Audio plays through foreground service with screen off

### Background & Lock Screen

- [ ] **BG-01**: Foreground service keeps WebSocket and audio alive with screen off
- [ ] **BG-02**: Persistent notification shows active channel and PTT controls
- [ ] **BG-03**: User can operate PTT from lock screen via hardware button
- [ ] **BG-04**: Partial wake lock keeps CPU alive for audio processing when screen locked

### Hardware PTT

- [ ] **HW-01**: User can map volume keys as PTT button
- [ ] **HW-02**: User can use dedicated PTT button on rugged phones (Sonim, Kyocera)
- [ ] **HW-03**: User can map Bluetooth headset PTT button
- [ ] **HW-04**: User can configure button mapping in settings
- [ ] **HW-05**: Hardware PTT targets current bottom-bar channel (scan mode aware)

### Multi-Channel & Scan Mode

- [ ] **SCAN-01**: User can monitor up to 5 channels simultaneously
- [ ] **SCAN-02**: User can set a primary/default channel
- [ ] **SCAN-03**: Bottom bar shows primary channel with PTT by default
- [ ] **SCAN-04**: Bottom bar auto-switches to active channel when someone talks
- [ ] **SCAN-05**: Bottom bar drops back to primary channel after transmission ends (configurable delay)
- [ ] **SCAN-06**: User can manually tap bottom bar to switch channels
- [ ] **SCAN-07**: Unmonitored/muted channels unsubscribe from audio (bandwidth savings)
- [ ] **SCAN-08**: User can configure scan mode behavior in settings

### Network & Resilience

- [ ] **NET-01**: App auto-reconnects silently after network loss
- [ ] **NET-02**: App handles WiFi to cellular handoff gracefully
- [ ] **NET-03**: App shows offline state with cached channel list when disconnected
- [ ] **NET-04**: App shows small reconnecting indicator during reconnection

### UX Polish

- [ ] **UX-01**: User feels haptic feedback for PTT press, release, busy, and transmission events
- [ ] **UX-02**: User sees network quality indicator (latency, connection status)
- [ ] **UX-03**: User can view transmission history (last 10-20) when tapping into a channel
- [ ] **UX-04**: Settings screen for scan mode, button mapping, audio output, auto-start

## Future Requirements

Deferred to future milestones. Tracked but not in current roadmap.

### iOS Client

- **IOS-01**: iOS native app with PTT functionality
- **IOS-02**: iOS app supports background audio
- **IOS-03**: iOS app uses same backend API

### Recording & Compliance

- **REC-01**: Admin can enable recording for channels
- **REC-02**: System records all audio on recorded channels
- **REC-03**: Admin can replay recorded audio (7-day retention)

### Dispatch Mobile

- **DISM-01**: Dispatch role support in Android app
- **DISM-02**: Dispatch multi-channel monitoring on Android (10-50 channels)

### Enhanced Communication

- **COMM-01**: Text messaging in channels
- **COMM-02**: GPS location tracking for users

## Out of Scope

Explicitly excluded features with reasoning.

| Feature | Reason |
|---------|--------|
| Dispatch role in Android app | Web dispatch console sufficient for now |
| Admin role in Android app | Web admin console sufficient for now |
| Push notifications (FCM) | User monitors channels directly via foreground service |
| In-app messaging/chat | Voice-first product, not a messaging app |
| GPS tracking / location | Privacy/battery concerns, use separate MDM tool |
| Recording/playback on device | Server-side feature, future milestone |
| Persistent history across restarts | PTT is ephemeral by design |
| Play Store submission | Deliverable is compilable Android Studio project |
| Video streaming | Bandwidth/battery drain, not PTT use case |
| Custom notification sounds per channel | Cacophony in multi-channel; single clear PTT sound |
| Offline message queuing | PTT is real-time; queued messages break mental model |
| End-to-end encryption | Server-side decryption acceptable for recording/compliance |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| APP-01 | Phase 5 | Pending |
| APP-02 | Phase 5 | Pending |
| APP-03 | Phase 5 | Pending |
| APP-04 | Phase 5 | Pending |
| APP-05 | Phase 5 | Pending |
| APP-06 | Phase 9 | Pending |
| APTT-01 | Phase 6 | Pending |
| APTT-02 | Phase 6 | Pending |
| APTT-03 | Phase 6 | Pending |
| APTT-04 | Phase 6 | Pending |
| APTT-05 | Phase 6 | Pending |
| APTT-06 | Phase 6 | Pending |
| AUD-01 | Phase 6 | Pending |
| AUD-02 | Phase 9 | Pending |
| AUD-03 | Phase 9 | Pending |
| AUD-04 | Phase 7 | Pending |
| AUD-05 | Phase 7 | Pending |
| BG-01 | Phase 7 | Pending |
| BG-02 | Phase 7 | Pending |
| BG-03 | Phase 7 | Pending |
| BG-04 | Phase 7 | Pending |
| HW-01 | Phase 9 | Pending |
| HW-02 | Phase 9 | Pending |
| HW-03 | Phase 9 | Pending |
| HW-04 | Phase 9 | Pending |
| HW-05 | Phase 9 | Pending |
| SCAN-01 | Phase 8 | Pending |
| SCAN-02 | Phase 8 | Pending |
| SCAN-03 | Phase 8 | Pending |
| SCAN-04 | Phase 8 | Pending |
| SCAN-05 | Phase 8 | Pending |
| SCAN-06 | Phase 8 | Pending |
| SCAN-07 | Phase 8 | Pending |
| SCAN-08 | Phase 8 | Pending |
| NET-01 | Phase 10 | Pending |
| NET-02 | Phase 10 | Pending |
| NET-03 | Phase 10 | Pending |
| NET-04 | Phase 10 | Pending |
| UX-01 | Phase 10 | Pending |
| UX-02 | Phase 10 | Pending |
| UX-03 | Phase 10 | Pending |
| UX-04 | Phase 10 | Pending |

**Coverage:**
- v2.0 requirements: 38 total
- Mapped to phases: 38 (100%)
- Unmapped: 0

---
*Requirements defined: 2026-02-06 (v1.0)*
*Last updated: 2026-02-08 after milestone v2.0 definition*
