# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-13)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** Phase 12: Device and RecvTransport Integration

## Current Position

Phase: 13-send-transport-producer-integration
Plan: 2 of 2 in phase 13
Status: Phase 13 complete
Last activity: 2026-02-13 — Plan 13-02 complete (PttManager Producer lifecycle wiring)

Progress: [████████░░░░░░░░░░░░] 40/TBD plans complete (v1.0: 24, v2.0: 26, v3.0: 8)

## Performance Metrics

**Velocity:**
- Total plans completed: 40 (v1.0: 24, v2.0: 26, v3.0: 8)
- Average duration: v1.0 ~10.5 min, v2.0 ~8.2 min, v3.0 ~3.9 min (8 plans)
- Total execution time: v1.0 ~4.2 hours, v2.0 ~3.5 hours, v3.0 ~0.52 hours

**By Milestone:**

| Milestone | Phases | Plans | Status |
|-----------|--------|-------|--------|
| v1.0 WebRTC Rebuild | 4 | 24 | Complete |
| v2.0 Android Client | 6 | 26 | Complete |
| v3.0 mediasoup Integration | 5 | TBD | In progress |

**Recent Trend:**
- v3.0 in progress: 8 plans complete, 12 commits, Phase 13 complete (SendTransport, Producer integrated)
- v2.0 shipped 6 phases, 26 plans, 70 commits, 9,233 LOC Kotlin

*Updated after each plan completion*

| Plan | Duration (s) | Tasks | Files |
|------|--------------|-------|-------|
| Phase 11 P01 | 313 | 2 tasks | 3 files |
| Phase 11 P02 | 234 | 2 tasks | 1 files |
| Phase 12 P01 | 218 | 2 tasks | 2 files |
| Phase 12 P02 | 228 | 2 tasks | 3 files |
| Phase 13 P01 | 362 | 2 tasks | 2 files |
| Phase 13 P02 | 117 | 2 tasks | 2 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [v2.0]: Kotlin native for Android — Best performance, platform integration for background services, hardware buttons, audio routing
- [v2.0]: Hilt DI with @Singleton providers — 22 singletons, clean dependency graph, testable architecture
- [v2.0]: No server changes for Android — Existing WebSocket/mediasoup protocol is client-agnostic
- [v3.0]: Replace MediasoupClient stubs with libmediasoup-android 0.21.0 — Real WebRTC audio vs web-only approach
- [11-01]: Use PeerConnectionFactory.initialize() not MediasoupClient.initialize() — crow-misia API differs from haiyangwu wrapper
- [11-01]: Default modeControlEnabled=true in AudioRouter — Backward compatible until WebRTC takes over in Plan 02
- [11-02]: Device(peerConnectionFactory) constructor pattern — crow-misia 0.21.0 requires factory parameter
- [11-02]: String-based Opus validation — Device.rtpCapabilities returns JSON string, not object
- [12-01]: runBlocking bridge for Transport callbacks — Native JNI threads need blocking bridge to suspend functions
- [12-01]: Per-channel RecvTransport map — Multi-channel monitoring requires independent transport lifecycle
- [12-01]: AudioTrack volume 0-10 range — WebRTC uses 0-10 not 0-1, convert via multiplication
- [12-02]: Stub getConsumerStats() implementation — Consumer.stats API undocumented, stub returns "Good" until on-device testing
- [12-02]: 5-second network quality polling — Balances responsiveness with battery/CPU efficiency for VoIP monitoring
- [13-01]: SendTransport singleton (no channelId) — PTT is mutually exclusive, one transport per device vs RecvTransport per channel
- [13-01]: WebRTC AudioSource for PTT capture — Replaces AudioCaptureManager callback pattern, AudioSource captures mic internally
- [13-02]: Delete AudioCaptureManager entirely — WebRTC AudioSource handles all microphone capture, no manual buffer forwarding needed

### Pending Todos

None yet.

### Blockers/Concerns

**From v2.0 Tech Debt:**
- On-device testing not yet performed (no physical Android device during development) — Phase 15 will address

**Phase 13 Complete:**
- SendTransport singleton with onConnect/onProduce callbacks ✓
- Producer lifecycle with WebRTC AudioSource/AudioTrack ✓
- PttManager wired to Producer lifecycle (no AudioCaptureManager) ✓
- AudioCaptureManager.kt deleted (168 LOC removed) ✓
- PTT flow: requestPtt -> createSendTransport + startProducing, releasePtt -> stopProducing
- Opus PTT config: mono, DTX, FEC, 48kHz, 20ms ptime
- Native resource disposal order validated (AudioTrack before AudioSource)

**Phase 12 Complete:**
- RecvTransport per-channel pattern — Map-based storage for multi-channel monitoring
- Consumer.resume() critical — Consumers start paused, must resume for audio playback
- runBlocking bridge validated — Native JNI thread → runBlocking → suspend signaling works for one-time DTLS handshake
- Network quality polling — 5-second intervals with Good/Fair/Poor indicators, stub stats until API confirmed

**Carried forward:**
- Multi-server state consistency strategy needs research for distributed Redis pub/sub pattern
- Replace self-signed certificates with real TLS certificates for production deployment

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 1 | Build debug APK for testing | 2026-02-12 | 1855ebc | [1-build-debug-apk-for-testing](./quick/1-build-debug-apk-for-testing/) |
| 2 | Fix settings drawer hardcoded user info and iceParameters error | 2026-02-12 | 1028b03 | [2-fix-settings-drawer-hardcoded-user-info-](./quick/2-fix-settings-drawer-hardcoded-user-info-/) |

## Session Continuity

Last session: 2026-02-13 (plan execution)
Stopped at: Completed 13-02-PLAN.md — Phase 13 complete (PttManager Producer lifecycle wiring)
Resume file: None

Next step: Plan Phase 14 (error recovery and reconnection logic)

**Milestone 1 (WebRTC Audio Rebuild + Web UI) SHIPPED 2026-02-07:**
- 4 phases, 24 plans
- See: .planning/milestones/v1.0-ROADMAP.md

**Milestone 2 (Android Client App) SHIPPED 2026-02-13:**
- 6 phases, 26 plans, 9,233 LOC Kotlin
- See: .planning/milestones/v2.0-ROADMAP.md

---
*Last updated: 2026-02-13 after completing 13-02-PLAN.md (PttManager Producer lifecycle wiring)*
