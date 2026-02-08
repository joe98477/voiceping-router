# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-08)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** Milestone v2.0 — Android Client App (Phase 5)

## Current Position

Phase: 5 of 10 (Android Project Setup & WebRTC Foundation)
Plan: 5 of 5 complete
Status: Phase complete
Last activity: 2026-02-08 — Completed 05-05-PLAN.md (Channel join & audio playback integration)

Progress: [█████████░] 50% (Milestone 1 complete: 4/10 phases shipped, Milestone 2: 5/5 Phase 5 plans complete)

## Performance Metrics

**Milestone 1 Velocity:**
- Total plans completed: 26
- Average duration: 9.6 minutes
- Total execution time: ~4.2 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1. WebRTC Audio Foundation | 8 | ~80 min | ~10 min |
| 2. User Management & Access Control | 8 | ~75 min | ~9.4 min |
| 3. Browser UI | 5 | ~45 min | ~9 min |
| 4. Dispatch Multi-Channel | 3 | ~30 min | ~10 min |

**Trend:** Stable (Milestone 1 complete)

*Updated after Milestone 1 completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Key decisions carrying forward to Milestone 2:

- Kotlin native for Android client (best performance, platform integration for background services, hardware button access, audio routing)
- Scan mode bottom bar pattern (emulates two-way radio scan behavior — familiar to field workers, efficient interaction)
- Max 5 simultaneous channels for general users (bandwidth constraint on mobile, sufficient for field worker use case)
- No server changes for Android client (existing WebSocket/mediasoup protocol is client-agnostic)

**Phase 5 Decisions:**

| Decision | Rationale | Phase | Plan |
|----------|-----------|-------|------|
| Use libmediasoup-android 0.7.0 (Maven Central) | Latest stable version, actively maintained, will validate compatibility in Plan 02 acceptance test | 05 | 01 |
| Dark theme cyan accent (#00BCD4) | Radio-app feel, high visibility against dark background, WCAG AAA compliance | 05 | 01 |
| Minimum SDK 26 (Android 8.0) | 89% device coverage, enables EncryptedSharedPreferences, stable WebRTC support | 05 | 01 |
| Store credentials (email/password) for silent refresh | Server requires email/password for token refresh. EncryptedSharedPreferences secure (hardware-backed Keystore). Matches "always persist session" decision. | 05 | 02 |
| Inline validation errors under fields | Per user decision. Errors stay visible, don't auto-dismiss like toasts. Better UX and accessibility. | 05 | 02 |
| BuildConfig.SERVER_URL default 10.0.2.2:3000 | Android emulator's host loopback. Works out-of-box for dev, configurable for production. | 05 | 02 |
| JWT via Sec-WebSocket-Protocol header | Server expects 'voiceping, <token>' format in handleProtocols callback | 05 | 03 |
| 10-second timeout for signaling requests | Prevents hanging on server failures, generous buffer for network latency | 05 | 03 |
| 25-second heartbeat interval | Balances disconnect detection (within 30s) with battery efficiency | 05 | 03 |
| MediasoupClient pattern skeleton with TODOs | Exact library API uncertain, pattern documents architecture for Plan 05 integration | 05 | 03 |
| Team grouping using groupBy in Composable | Channels.groupBy { it.teamName } provides simple in-memory grouping. Channel lists are small (<50 per event). | 05 | 04 |
| ProfileDrawer custom implementation (not ModalNavigationDrawer) | Material 3 ModalNavigationDrawer only supports left-to-right. User decision requires right-to-left slide. AnimatedVisibility provides correct behavior. | 05 | 04 |
| Connection status dot in TopAppBar actions | Separate dot + spacer before icon (simpler than overlay). Functional requirement met. | 05 | 04 |
| TODO markers for channel join/leave logic | ChannelListViewModel.toggleChannel() has TODOs for actual join/leave calls. Plan 05 will implement SignalingClient integration. | 05 | 04 |
| Store currentConsumerId in ChannelRepository | When speaker changes, previous consumer must be closed before creating new one. Tracking currentConsumerId enables closeConsumer(). Prevents memory leak. | 05 | 05 |
| LoadingViewModel auto-initiates connection in init block | Connection starts immediately when LoadingScreen appears. Early connection start reduces perceived latency. | 05 | 05 |
| Failed connection shows Retry and Logout buttons | Per user decision: retry 2-3 times silently, then show clear message with user control. Retry allows manual retry, Logout provides escape. | 05 | 05 |

### Pending Todos

None yet.

### Blockers/Concerns

Deferred to future milestones:
- Multi-server state consistency strategy needs research for distributed Redis pub/sub pattern
- Replace self-signed certificates with real TLS certificates for production deployment

## Session Continuity

Last session: 2026-02-08
Stopped at: Completed 05-05-PLAN.md (Channel join & audio playback integration) - Phase 5 complete, awaiting human verification
Resume file: None

**Milestone 1 (WebRTC Audio Rebuild + Web UI) COMPLETE:**
- Phase 1: WebRTC Audio Foundation (8 plans) ✓
- Phase 2: User Management & Access Control (8 plans) ✓
- Phase 3: Browser UI for General Users (5 plans) ✓
- Phase 4: Dispatch Multi-Channel Monitoring (3 plans) ✓
- Total: 26 plans, ~4.2 hours execution time

**Milestone 2 (Android Client App) IN PROGRESS:**
- Phase 5: Android Project Setup & WebRTC Foundation (5/5 plans complete) ✓
  - 05-01: Android project foundation ✓
  - 05-02: Authentication & login flow ✓
  - 05-03: Networking layer (WebSocket signaling, mediasoup client) ✓
  - 05-04: UI screens (Event picker, Channel list, App shell) ✓
  - 05-05: Channel join & audio playback integration ✓ (awaiting human verification)
- Phase 6: Single-Channel PTT & Audio Transmission (pending)
- Phase 7: Foreground Service & Background Audio (pending)
- Phase 8: Multi-Channel Monitoring & Scan Mode (pending)
- Phase 9: Hardware PTT & Bluetooth Integration (pending)
- Phase 10: Network Resilience & UX Polish (pending)
