# Roadmap: VoicePing PTT Communications Platform

## Overview

This roadmap rebuilds the broken Opus-over-WebSocket audio subsystem with WebRTC and mediasoup SFU while preserving all existing user management and event structure. Phase 1 proves WebRTC audio works in a single channel, Phase 2 adds role-based access control and organizational structure, Phase 3 delivers a functional browser UI for general users, and Phase 4 implements advanced dispatch multi-channel monitoring. The journey progresses from technical validation to feature completeness, addressing critical audio reliability issues while maintaining all current working capabilities.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: WebRTC Audio Foundation** - Prove WebRTC + mediasoup works with PTT audio under 300ms latency
- [ ] **Phase 2: User Management & Access Control** - Role-based permissions and event/team/channel structure
- [ ] **Phase 3: Browser UI for General Users** - Web interface for single-channel PTT communication
- [ ] **Phase 4: Dispatch Multi-Channel Monitoring** - Advanced dispatch console with selective channel monitoring

## Phase Details

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
- [ ] 01-01-PLAN.md -- Project modernization: Node 20, TypeScript 5, mediasoup deps, shared types and protocol
- [ ] 01-02-PLAN.md -- mediasoup SFU core: worker pool, router, transports, producers/consumers
- [ ] 01-03-PLAN.md -- Redis state: speaker locks, channel state, session store
- [ ] 01-04-PLAN.md -- Signaling server: WebSocket auth, message handlers, server entry point wiring
- [ ] 01-05-PLAN.md -- Client audio pipeline: mediasoup-client device, transports, microphone, signaling client
- [ ] 01-06-PLAN.md -- PTT UX: button (hold/toggle), audio feedback tones, busy state, PTT controller
- [ ] 01-07-PLAN.md -- Reconnection: exponential backoff WebSocket, session recovery, connection manager
- [ ] 01-08-PLAN.md -- Integration: Docker deployment, test demo page, cross-browser verification

### Phase 2: User Management & Access Control
**Goal**: Role-based permissions and organizational hierarchy enable secure multi-user coordination
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-08, AUTH-09, EVNT-01, EVNT-02, EVNT-03, EVNT-04, EVNT-05, EVNT-06, SEC-03, SEC-04, SEC-05, SYS-03, SYS-04
**Success Criteria** (what must be TRUE):
  1. Admin user can create events and assign users to Admin, Dispatch, or General roles
  2. Dispatch user can create teams and channels within assigned events only
  3. Dispatch user can assign and remove users from channels
  4. General user can only PTT on channels they are assigned to
  5. User authorization is checked before granting channel access
  6. System successfully handles 100 concurrent users in testing environment
**Plans**: TBD

Plans:
- [ ] TBD - Will be created during plan-phase

### Phase 3: Browser UI for General Users
**Goal**: Web interface enables general users to see assigned channels and communicate via PTT
**Depends on**: Phase 2
**Requirements**: UI-01, UI-02, UI-03, UI-04
**Success Criteria** (what must be TRUE):
  1. User sees list of channels they are assigned to
  2. User can click PTT button on any assigned channel to transmit audio
  3. User sees visual indicator when channel is busy
  4. User sees which user is currently speaking on each channel
**Plans**: TBD

Plans:
- [ ] TBD - Will be created during plan-phase

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
**Plans**: TBD

Plans:
- [ ] TBD - Will be created during plan-phase

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. WebRTC Audio Foundation | 0/8 | Planned | - |
| 2. User Management & Access Control | 0/TBD | Not started | - |
| 3. Browser UI for General Users | 0/TBD | Not started | - |
| 4. Dispatch Multi-Channel Monitoring | 0/TBD | Not started | - |

---
*Roadmap created: 2026-02-06*
*Last updated: 2026-02-06*
