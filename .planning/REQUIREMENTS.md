# Requirements: VoicePing PTT Communications Platform

**Defined:** 2026-02-13
**Core Value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical

## v3.0 Requirements

Requirements for mediasoup library integration. Replaces MediasoupClient.kt stub code with real libmediasoup-android library calls for bidirectional WebRTC audio.

### WebRTC Foundation

- [ ] **WEBRTC-01**: App adds libmediasoup-android 0.21.0 dependency and compiles successfully
- [ ] **WEBRTC-02**: PeerConnectionFactory initializes with AudioDeviceModule configuration (echo cancellation, noise suppression)
- [ ] **WEBRTC-03**: AudioRouter refactored to coordinate with WebRTC's AudioManager ownership (no dual control conflicts)
- [ ] **WEBRTC-04**: Device loads server's RTP capabilities and exposes its own RTP capabilities for consume requests

### Receive Audio

- [ ] **RECV-01**: RecvTransport created with server parameters and onConnect callback bridges DTLS to signaling
- [ ] **RECV-02**: Consumer created from remote producer and audio playback begins on resume
- [ ] **RECV-03**: Per-consumer volume control adjusts playback level (0.0-1.0)
- [ ] **RECV-04**: Consumer closed cleanly when leaving channel or on transportclose event
- [ ] **RECV-05**: Consumer statistics available for network quality indicator (packet loss, jitter)

### Transmit Audio

- [ ] **SEND-01**: SendTransport created with server parameters, onConnect and onProduce callbacks bridge to signaling
- [ ] **SEND-02**: AudioTrack created via PeerConnectionFactory for microphone capture
- [ ] **SEND-03**: Producer created with Opus PTT config (mono, DTX, FEC, 48kHz, 20ms ptime) when PTT pressed
- [ ] **SEND-04**: Producer closed and audio capture stopped when PTT released
- [ ] **SEND-05**: AudioCaptureManager removed — library handles audio capture internally

### Lifecycle

- [ ] **LIFE-01**: Resources disposed in correct order (producers -> consumers -> transports) on disconnect
- [ ] **LIFE-02**: transportclose events handled for both send and recv transports
- [ ] **LIFE-03**: Reconnection uses Mutex-based state machine to prevent race conditions
- [ ] **LIFE-04**: Rapid PTT press/release doesn't cause duplicate producers or orphaned resources

### Validation

- [ ] **VALID-01**: ProGuard/R8 rules verified — release build doesn't strip JNI classes
- [ ] **VALID-02**: Release APK tested on physical Android device with end-to-end audio
- [ ] **VALID-03**: Battery and wake lock profiling shows no excessive drain from WebRTC threads

## Future Requirements

### Advanced Audio

- **ADV-01**: Simulcast for bandwidth adaptation (multi-bitrate streams)
- **ADV-02**: Data channel support for text chat or metadata
- **ADV-03**: Consumer statistics dashboard in settings screen

## Out of Scope

| Feature | Reason |
|---------|--------|
| Video track support | Bandwidth/battery drain, not PTT use case |
| Custom AudioRecord management | Library handles audio capture internally |
| Server-side changes | Existing WebSocket/mediasoup protocol is client-agnostic |
| iOS implementation | Android first, iOS in future milestone |
| Simulcast | High complexity, mono PTT audio doesn't benefit |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| WEBRTC-01 | Phase 11 | Pending |
| WEBRTC-02 | Phase 11 | Pending |
| WEBRTC-03 | Phase 11 | Pending |
| WEBRTC-04 | Phase 11 | Pending |
| RECV-01 | Phase 12 | Pending |
| RECV-02 | Phase 12 | Pending |
| RECV-03 | Phase 12 | Pending |
| RECV-04 | Phase 12 | Pending |
| RECV-05 | Phase 12 | Pending |
| SEND-01 | Phase 13 | Pending |
| SEND-02 | Phase 13 | Pending |
| SEND-03 | Phase 13 | Pending |
| SEND-04 | Phase 13 | Pending |
| SEND-05 | Phase 13 | Pending |
| LIFE-01 | Phase 14 | Pending |
| LIFE-02 | Phase 14 | Pending |
| LIFE-03 | Phase 14 | Pending |
| LIFE-04 | Phase 14 | Pending |
| VALID-01 | Phase 15 | Pending |
| VALID-02 | Phase 15 | Pending |
| VALID-03 | Phase 15 | Pending |

**Coverage:**
- v3.0 requirements: 18 total
- Mapped to phases: 18 (100% coverage)
- Unmapped: 0

---
*Requirements defined: 2026-02-13*
*Last updated: 2026-02-13 after v3.0 roadmap creation*
