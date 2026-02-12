# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-08)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** Milestone v2.0 — Android Client App (Phase 9 next)

## Current Position

Phase: 9 of 10 (Hardware PTT & Bluetooth Integration)
Plan: 1 of 4 complete
Status: In Progress
Last activity: 2026-02-12 — Completed 09-01: Hardware Button Settings Foundation & Volume Key PTT Handler

Progress: [███████████████] 82% (Milestone 1 complete: 4/10 phases shipped, Milestone 2: Phase 5-6-7-8 complete, Phase 9: 1/4 plans)

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

**Milestone 2 Phase 6:**

| Plan | Duration (s) | Tasks | Files |
|------|--------------|-------|-------|
| 06-01 | 200 | 2 | 7 |
| 06-02 | 444 | 2 | 5 |
| 06-03 | 125 | 2 | 3 |
| 06-04 | 332 | 3 | 7 |

**Milestone 2 Phase 7:**

| Plan | Duration (s) | Tasks | Files |
|------|--------------|-------|-------|
| 07-01 | 255 | 2 | 4 |
| 07-02 | 137 | 2 | 2 |
| 07-03 | 168 | 2 | 3 |

**Milestone 2 Phase 8:**

| Plan | Duration (s) | Tasks | Files |
|------|--------------|-------|-------|
| 08-01 | 127 | 2 | 5 |
| 08-02 | 181 | 2 | 3 |
| 08-03 | 329 | 2 | 4 |
| 08-04 | 257 | 2 | 5 |

**Milestone 2 Phase 9:**

| Plan | Duration (s) | Tasks | Files |
|------|--------------|-------|-------|
| 09-01 | 391 | 2 | 4 |
| Phase 09 P01 | 391 | 2 tasks | 4 files |

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

**Phase 6 Decisions:**

| Decision | Rationale | Phase | Plan |
|----------|-----------|-------|------|
| Use DataStore over SharedPreferences | Type-safe Flow API, async by default, better error handling, modern Kotlin-first API | 06 | 01 |
| PTT defaults: PRESS_AND_HOLD mode, SPEAKER output, roger beep ON | PRESS_AND_HOLD more intuitive for new users, SPEAKER matches walkie-talkie UX, roger beep is classic radio feedback | 06 | 01 |
| Cached sync accessors for audio thread | DataStore caches after first read, runBlocking safe in this context, required for audio thread where Flow collection impractical | 06 | 01 |
| DTMF tones for PTT/roger beep | Already built into ToneGenerator, familiar to radio users, distinct frequencies | 06 | 01 |
| Buzz-pause-buzz error vibration pattern | Distinct from press confirmation (single pulse), immediately recognizable as error | 06 | 01 |
| Error tone always plays (no toggle) | User must know PTT was denied, non-negotiable UX requirement | 06 | 01 |
| Callback pattern for tone/haptic integration | PttManager exposes callbacks wired in ChannelRepository init. Avoids circular deps between Wave 1 components | 06 | 04 |
| RX squelch only for incoming speakers | Guard check: do not play squelch if user is transmitting. Matches radio UX where squelch indicates OTHER people | 06 | 04 |
| Mic permission on first PTT press | Better UX - only request permission when user needs it, not on app launch | 06 | 04 |
| Toggle mode auto-release enforcement | Max duration (30-120s configurable) prevents accidental long transmissions in toggle mode | 06 | 04 |
| Settings UI in ProfileDrawer | Per user decision: settings drawer/side panel. All PTT settings grouped: mode, audio output, tone toggles | 06 | 04 |

**Phase 7 Decisions:**

| Decision | Rationale | Phase | Plan |
|----------|-----------|-------|------|
| Use AUDIOFOCUS_LOSS_TRANSIENT to detect phone calls without READ_PHONE_STATE permission | Avoids dangerous permission, works for incoming/outgoing calls, reliable signal for phone call start/end | 07 | 02 |
| Separate forceReleasePtt() from releasePtt() for distinct audio feedback | Normal release uses onPttReleased (roger beep), force release uses onPttInterrupted (double beep). Two code paths keep audio feedback distinct. | 07 | 02 |
| Enable automatic ducking with setWillPauseWhenDucked(false) | API 26+ feature for automatic volume reduction during transient sounds (navigation, notifications). Radio audio continues during ducking events. | 07 | 02 |
- [Phase 07]: IMPORTANCE_LOW notification channel for ChannelMonitoringService (unobtrusive like music player)
- [Phase 07]: START_NOT_STICKY for ChannelMonitoringService (no auto-restart after force-kill)
- [Phase 07]: DTMF_A double beep for call interruption (distinct from roger beep DTMF_0 and error PROP_BEEP2)
- [Phase 07]: Service starts ONLY on first channel join (not on login/launch), stops on leave/disconnect
- [Phase 07]: Phone call pause uses consumer close pattern (immediate, no fade)
- [Phase 07]: Battery optimization prompt triggered after first successful join (when service starts)

### Pending Todos

None yet.

### Blockers/Concerns

Deferred to future milestones:
- Multi-server state consistency strategy needs research for distributed Redis pub/sub pattern
- Replace self-signed certificates with real TLS certificates for production deployment

## Session Continuity

Last session: 2026-02-12
Stopped at: Completed 09-01-PLAN.md (Hardware Button Settings Foundation & Volume Key PTT Handler)
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
  - 05-05: Channel join & audio playback integration ✓
- Phase 6: Single-Channel PTT & Audio Transmission (5/5 code plans complete, awaiting device verification)
  - 06-01: PTT domain models & settings foundation ✓
  - 06-02: PTT core engine (PttManager, AudioCapture, send transport) ✓
  - 06-03: PTT UI (PttButton, BottomBar, ChannelRow speaker indicators) ✓
  - 06-04: PTT Integration: Wire Components End-to-End ✓
  - 06-05: Human verification on device (deferred - no Android Studio)
  - Static analysis audit: 5 errors + 2 warnings fixed ✓
- Phase 7: Foreground Service & Background Audio (3/3 plans complete) ✓
  - 07-01: Foreground service & notification controls ✓
  - 07-02: Phone call detection & PTT interruption ✓
  - 07-03: Service lifecycle & phone call integration ✓
- Phase 8: Multi-Channel Monitoring & Scan Mode (4/4 plans complete) ✓
  - 08-01: Domain Models & Settings Foundation ✓
  - 08-02: Multi-Channel Monitoring Engine ✓
  - 08-03: Scan Mode Logic & ViewModel Integration ✓
  - 08-04: Scan Mode Settings UI & Per-Channel Volume Control ✓
- Phase 9: Hardware PTT & Bluetooth Integration (1/4 plans complete, in progress)
  - 09-01: Hardware Button Settings Foundation & Volume Key PTT Handler ✓
  - 09-02: Volume Key & Bluetooth Button Integration (pending)
  - 09-03: Boot Auto-Start & PTT Auto-Release (pending)
  - 09-04: Settings UI for Hardware Buttons (pending)
- Phase 10: Network Resilience & UX Polish (pending)
