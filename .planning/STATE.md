# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-13)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** Phase 13 complete, ready for Phase 14

## Current Position

Phase: 15-release-build-validation-device-testing
Plan: 1 of 2 in phase 15
Status: Phase 15 in progress
Last activity: 2026-02-13 — Plan 15-01 complete (ProGuard/R8 rules and release build validation)

Progress: [████████░░░░░░░░░░░░] 43/TBD plans complete (v1.0: 24, v2.0: 26, v3.0: 11)

## Performance Metrics

**Velocity:**
- Total plans completed: 43 (v1.0: 24, v2.0: 26, v3.0: 11)
- Average duration: v1.0 ~10.5 min, v2.0 ~8.2 min, v3.0 ~3.3 min (11 plans)
- Total execution time: v1.0 ~4.2 hours, v2.0 ~3.5 hours, v3.0 ~0.61 hours

**By Milestone:**

| Milestone | Phases | Plans | Status |
|-----------|--------|-------|--------|
| v1.0 WebRTC Rebuild | 4 | 24 | Complete |
| v2.0 Android Client | 6 | 26 | Complete |
| v3.0 mediasoup Integration | 5 | TBD | In progress |

**Recent Trend:**
- v3.0 in progress: 11 plans complete, 17 commits, Phase 15 in progress (release build validation)
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
| Phase 14 P01 | 149 | 2 tasks | 1 files |
| Phase 14 P02 | 141 | 2 tasks | 2 files |
| Phase 15 P01 | 353 | 2 tasks | 1 files |

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
- [Phase 14-01]: Use Kotlin Mutex instead of synchronized for suspend functions in transport lifecycle
- [Phase 14-01]: Guard checks inside Mutex for idempotent transport/producer creation during network flapping
- [Phase 14-02]: Differentiate "disconnected" from "failed" in Transport connection state handlers for auto-recovery window
- [Phase 14-02]: Clean up mediasoup resources on signaling DISCONNECTED to prevent orphaned resources
- [Phase 15-01]: Preserve both io.github.crow_misia.mediasoup and org.mediasoup packages — Library may use either internally
- [Phase 15-01]: Enable full R8 optimization (no -dontobfuscate) — Production builds need code shrinking and obfuscation

### Pending Todos

None yet.

### Blockers/Concerns

**Phase 15 In Progress:**
- Plan 15-01 complete: ProGuard/R8 rules updated, release APK builds successfully (42.8 MB)
- Comprehensive R8 keep rules for WebRTC, crow-misia mediasoup, Hilt DI, native methods, @CalledByNative
- No R8 warnings about missing JNI classes, mapping.txt generated for crash deobfuscation
- Next: Plan 15-02 physical device testing (login, channel join, PTT, battery profiling)

**From v2.0 Tech Debt:**
- On-device testing not yet performed (no physical Android device during development) — Plan 15-02 will address

**Phase 14 Complete:**
- Mutex-protected transport lifecycle (createSendTransport, createRecvTransport, cleanupChannel)
- Guard checks prevent duplicate transport/producer creation during network flapping
- cleanupChannel() now suspend with Mutex protection for safe concurrent cleanup
- Idempotent transport operations enable resilient reconnection logic
- Transport error recovery with auto-recovery window (disconnected vs failed state differentiation)
- SendTransport failure cleanup: close producer, clean audio resources, null transport
- RecvTransport failure cleanup: remove transport from map (consumers cleaned via onTransportClose)
- Signaling DISCONNECTED cleanup: prevent orphaned resources before channel rejoin

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
Stopped at: Completed 15-01-PLAN.md — Phase 15 Plan 01 complete (ProGuard/R8 rules and release build validation)
Resume file: None

Next step: Execute Plan 15-02 (Physical device testing and battery profiling)

**Milestone 1 (WebRTC Audio Rebuild + Web UI) SHIPPED 2026-02-07:**
- 4 phases, 24 plans
- See: .planning/milestones/v1.0-ROADMAP.md

**Milestone 2 (Android Client App) SHIPPED 2026-02-13:**
- 6 phases, 26 plans, 9,233 LOC Kotlin
- See: .planning/milestones/v2.0-ROADMAP.md

---
*Last updated: 2026-02-13 after completing 15-01-PLAN.md (ProGuard/R8 rules and release build validation)*
