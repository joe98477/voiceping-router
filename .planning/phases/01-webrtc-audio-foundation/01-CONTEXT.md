# Phase 1: WebRTC Audio Foundation - Context

**Gathered:** 2026-02-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Prove that WebRTC-based audio transmission works reliably for PTT use case, replacing the broken Opus-over-WebSocket implementation. This phase validates the technology choice (mediasoup + WebRTC) with a single channel, demonstrating <300ms latency and cross-browser compatibility before investing in multi-channel architecture.

</domain>

<decisions>
## Implementation Decisions

### Architecture & Integration

- **Container replacement:** Replace entire vp-router container with new audio routing/streaming stack (mediasoup-based)
- **State management:** Hybrid approach
  - Redis: Real-time state (busy status, active channels, current speakers)
  - Control-plane: Auth, permissions, user/event/channel metadata
- **WebRTC signaling:** Dedicated WebSocket channel for WebRTC signaling (offer/answer/ICE candidates)
- **Deployment strategy:** Clean replacement (no parallel operation with old vp-router)

### PTT Button Behavior

- **Visual feedback:**
  - Button state change while pressed (color/style like walkie-talkie)
  - Transmission indicator ("Transmitting..." or audio wave animation)
- **Audio feedback:**
  - Audio confirmation tone when transmission starts
  - Audio confirmation tone when transmission stops
  - Audio prompts configurable via audio files in specific folder/naming structure
- **Busy state handling:**
  - Block transmission attempt with busy tone
  - Visual message showing "[username] is speaking"
  - No queueing - user must retry when channel is free
- **Interaction modes:**
  - Support both hold-to-talk (press and hold) and toggle (click on/off)
  - User preference setting to choose mode
  - Mouse/touch only for Phase 1 (no keyboard shortcuts)

### Claude's Discretion

- Testing strategy: latency measurement approach, cross-browser test matrix
- Browser compatibility priorities: which browsers to test first, mobile testing scope
- Error handling and reconnection UX
- Logging and debugging instrumentation
- STUN/TURN server selection and configuration

</decisions>

<specifics>
## Specific Ideas

- Audio prompts (transmission ready, busy channel, etc.) should use admin-uploadable audio files with specific folder/naming convention for easy customization per event or client
- Button visual feedback should feel like a physical walkie-talkie (tactile, immediate response)
- Busy tone should be distinctive and immediately recognizable

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-webrtc-audio-foundation*
*Context gathered: 2026-02-06*
