# Requirements: VoicePing PTT Communications Platform

**Defined:** 2026-02-06
**Core Value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events

## v1 Requirements

Requirements for audio subsystem rebuild and feature parity with current system.

### WebRTC Audio Infrastructure

- [x] **AUDIO-01**: mediasoup v3 SFU server integrated with existing Node.js backend
- [x] **AUDIO-02**: WebRTC signaling flow (offer/answer/ICE candidates) via existing WebSocket connections
- [x] **AUDIO-03**: Opus audio codec configured for PTT use case (2.5-10ms frames, CBR mode, DTX disabled)
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

### User Roles & Permissions

- [ ] **AUTH-01**: Admin user can create events
- [ ] **AUTH-02**: Admin user can assign users to Admin/Dispatch/General roles
- [ ] **AUTH-03**: Admin user can access all system settings
- [ ] **AUTH-04**: Dispatch user can create teams and channels within assigned events
- [ ] **AUTH-05**: Dispatch user can assign users to channels
- [ ] **AUTH-06**: Dispatch user cannot access admin-only settings
- [ ] **AUTH-07**: Dispatch user can only see assigned events
- [ ] **AUTH-08**: General user can PTT on assigned channels
- [ ] **AUTH-09**: General user cannot create channels or assign users

### Dispatch Multi-Channel Features

- [ ] **DISP-01**: Dispatch user can monitor multiple channels simultaneously (10-50 channels)
- [ ] **DISP-02**: Dispatch user can selectively mute individual channels
- [ ] **DISP-03**: Dispatch user can selectively unmute individual channels
- [ ] **DISP-04**: Dispatch user can talk on any monitored channel
- [ ] **DISP-05**: Dispatch user sees visual indicators for active channels (who is talking)
- [ ] **DISP-06**: Dispatch user hears audio from all unmuted channels

### Event/Team/Channel Management

- [ ] **EVNT-01**: Admin creates events with name and metadata
- [ ] **EVNT-02**: Dispatch creates teams within assigned events
- [ ] **EVNT-03**: Dispatch creates channels within teams
- [ ] **EVNT-04**: Dispatch assigns users to channels
- [ ] **EVNT-05**: Dispatch removes users from channels
- [ ] **EVNT-06**: Users see only channels they are assigned to

### Security & Access Control

- [x] **SEC-01**: User authenticates with JWT token
- [x] **SEC-02**: WebSocket connections use WSS (TLS/SSL)
- [ ] **SEC-03**: User authorization checked before channel access
- [ ] **SEC-04**: Session persists across page refresh
- [ ] **SEC-05**: Architecture supports future AES-256 media encryption (SRTP)

### Browser UI

- [x] **UI-01**: Web UI displays user's assigned channels
- [x] **UI-02**: Web UI shows PTT button for each channel
- [x] **UI-03**: Web UI shows channel busy state
- [x] **UI-04**: Web UI shows active speaker in channel
- [ ] **UI-05**: Dispatch UI shows all monitored channels with mute toggles
- [ ] **UI-06**: Dispatch UI indicates which channels have activity

### System Reliability

- [x] **SYS-01**: User can reconnect after temporary network loss
- [x] **SYS-02**: User's channel memberships restored on reconnection
- [ ] **SYS-03**: System handles 100 concurrent users in testing
- [ ] **SYS-04**: Architecture supports scaling to 1000+ concurrent users (not stress-tested in v1)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Advanced Audio Features

- **AUDIO-V2-01**: Automatic gain control (AGC) for consistent volume
- **AUDIO-V2-02**: Noise suppression for background noise reduction
- **AUDIO-V2-03**: Voice activity detection (VOD) for optimized transmission
- **AUDIO-V2-04**: Audio quality indicators (signal strength, bitrate)
- **AUDIO-V2-05**: Emergency/priority calling (interrupt busy channels)

### Recording & Compliance

- **REC-01**: Admin can enable recording for channels
- **REC-02**: System records all audio on recorded channels
- **REC-03**: Admin can replay recorded audio (7-day retention)
- **REC-04**: System generates audit logs for compliance
- **REC-05**: Recorded audio stored with encryption at rest

### Enhanced Dispatch Features

- **DISP-V2-01**: Channel scanning (auto-switch to active channels)
- **DISP-V2-02**: Priority channel highlighting
- **DISP-V2-03**: Channel grouping/favorites
- **DISP-V2-04**: Broadcast to multiple channels simultaneously

### Analytics & Monitoring

- **ANLY-01**: System health dashboard (CPU, memory, active users)
- **ANLY-02**: User activity metrics (talk time, channels used)
- **ANLY-03**: Channel usage statistics
- **ANLY-04**: Performance monitoring (latency, packet loss)

### Mobile Native Apps

- **MOB-01**: Android native app with PTT functionality
- **MOB-02**: iOS native app with PTT functionality
- **MOB-03**: Mobile app supports background audio
- **MOB-04**: Mobile app uses same backend API as web

### Enhanced Communication

- **COMM-01**: Text messaging in channels
- **COMM-02**: Photo/image sharing in channels
- **COMM-03**: GPS location tracking for users
- **COMM-04**: Map view showing user locations

## Out of Scope

Explicitly excluded features with reasoning.

| Feature | Reason |
|---------|--------|
| End-to-end encryption (E2E) | Server-side decryption needed for recording/compliance; encryption in transit (WSS) + at rest sufficient |
| Multi-tenant SaaS | Single-tenant architecture for security isolation and client customization |
| Video calls | PTT audio is core value; video adds significant complexity |
| File sharing (documents) | Not needed for real-time event coordination |
| Integration with external systems | Custom per client; defer to post-v1 services layer |
| Native desktop apps | Browser-first strategy sufficient; Electron wrapper if needed later |
| Offline mode | Real-time coordination requires connectivity |
| Third-party OAuth providers | Email/password sufficient; can add later if needed |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUDIO-01 | Phase 1 | Pending |
| AUDIO-02 | Phase 1 | Pending |
| AUDIO-03 | Phase 1 | Pending |
| AUDIO-04 | Phase 1 | Pending |
| AUDIO-05 | Phase 1 | Pending |
| AUDIO-06 | Phase 1 | Pending |
| AUDIO-07 | Phase 1 | Pending |
| PTT-01 | Phase 1 | Pending |
| PTT-02 | Phase 1 | Pending |
| PTT-03 | Phase 1 | Pending |
| PTT-04 | Phase 1 | Pending |
| PTT-05 | Phase 1 | Pending |
| SEC-01 | Phase 1 | Pending |
| SEC-02 | Phase 1 | Pending |
| SYS-01 | Phase 1 | Pending |
| SYS-02 | Phase 1 | Pending |
| AUTH-01 | Phase 2 | Pending |
| AUTH-02 | Phase 2 | Pending |
| AUTH-03 | Phase 2 | Pending |
| AUTH-04 | Phase 2 | Pending |
| AUTH-05 | Phase 2 | Pending |
| AUTH-06 | Phase 2 | Pending |
| AUTH-07 | Phase 2 | Pending |
| AUTH-08 | Phase 2 | Pending |
| AUTH-09 | Phase 2 | Pending |
| EVNT-01 | Phase 2 | Pending |
| EVNT-02 | Phase 2 | Pending |
| EVNT-03 | Phase 2 | Pending |
| EVNT-04 | Phase 2 | Pending |
| EVNT-05 | Phase 2 | Pending |
| EVNT-06 | Phase 2 | Pending |
| SEC-03 | Phase 2 | Pending |
| SEC-04 | Phase 2 | Pending |
| SEC-05 | Phase 2 | Pending |
| SYS-03 | Phase 2 | Pending |
| SYS-04 | Phase 2 | Pending |
| UI-01 | Phase 3 | Complete |
| UI-02 | Phase 3 | Complete |
| UI-03 | Phase 3 | Complete |
| UI-04 | Phase 3 | Complete |
| DISP-01 | Phase 4 | Pending |
| DISP-02 | Phase 4 | Pending |
| DISP-03 | Phase 4 | Pending |
| DISP-04 | Phase 4 | Pending |
| DISP-05 | Phase 4 | Pending |
| DISP-06 | Phase 4 | Pending |
| UI-05 | Phase 4 | Pending |
| UI-06 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 48 total
- Mapped to phases: 48 (100%)
- Unmapped: 0

---
*Requirements defined: 2026-02-06*
*Last updated: 2026-02-06 after roadmap creation*
