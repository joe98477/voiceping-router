# Milestones

## v3.0 mediasoup Library Integration (Shipped: 2026-02-15)

**Delivered:** Real WebRTC audio on Android — replaced MediasoupClient stubs with libmediasoup-android 0.21.0 for bidirectional voice communication, validated on physical hardware.

**Stats:** 5 phases (11-15), 10 plans, 38 commits, 16 files, +1,102/-526 LOC Kotlin
**Timeline:** 3 days (2026-02-13 → 2026-02-15)
**Git range:** `docs(11)` → `fix(15-02)`
**Requirements:** 18/18 satisfied (100%)
**Device tested:** Samsung Galaxy S21 (SM-S906E, Android 16 Beta BP2A.250605.031)

**Key accomplishments:**
- WebRTC foundation with libmediasoup-android 0.21.0, PeerConnectionFactory with hardware AEC/NS, Device RTP capabilities
- RecvTransport per-channel with Consumer lifecycle, resume for audio playback, volume control (0-10 range)
- SendTransport singleton with Producer lifecycle using WebRTC AudioSource for PTT, Opus config (mono, DTX, FEC, 48kHz, 20ms)
- Mutex-protected transport lifecycle with error recovery (disconnected vs failed state differentiation)
- Release build validation with comprehensive R8 keep rules for JNI libraries (42.8 MB APK)
- Physical device testing: 3 bugs fixed (SecurityException crash, producer race condition, wrong channel routing), all 8 E2E tests passed, battery 5%/hour

**Tech debt resolved from v2.0:**
- On-device verification completed (was deferred from v2.0)
- MediasoupClient TODO placeholders replaced with real library calls

**Tech debt remaining:**
- Consumer.getStats() returns stub quality (crow-misia API undocumented)
- No automated integration tests for mediasoup audio pipeline
- HW-02 rugged phone dedicated PTT (hardware unavailable)

---

## v2.0 Android Client App (Shipped: 2026-02-13)

**Delivered:** Native Android PTT client app — pocket two-way radio with hardware button support, multi-channel scan mode, and network resilience.

**Stats:** 6 phases (5-10), 26 plans, 70 commits, 99 files, 9,233 LOC Kotlin
**Timeline:** 5 days (2026-02-08 → 2026-02-13)
**Git range:** `feat(05-01)` → `feat(10-05)`
**Requirements:** 37/38 satisfied (HW-02 rugged phone PTT deferred)

**Key accomplishments:**
- Android app foundation with Kotlin/Jetpack Compose/Hilt, JWT login, event picker, channel list with team grouping, WebSocket + mediasoup audio
- Full PTT transmission flow with press-and-hold/toggle modes, busy state management, audio feedback tones, haptic feedback, earpiece/speaker routing
- Foreground service pocket radio mode — WebSocket and audio alive with screen off, persistent notification with PTT controls, phone call interruption handling
- Multi-channel scan mode monitoring up to 5 channels with auto-switch to active speaker, configurable return delay, per-channel volume control
- Hardware PTT integration — volume key mapping, Bluetooth headset button via MediaSession, audio device auto-routing, boot auto-start
- Network resilience with auto-reconnect, WiFi/cellular handoff, offline caching via Room database, network quality indicator, transmission history

**Tech debt:**
- On-device verification not yet performed (no physical Android device during development)
- MediasoupClient TODO placeholders for libmediasoup-android library integration
- HW-02 rugged phone PTT deferred (hardware unavailable)

---

## v1.0 WebRTC Audio Rebuild + Web UI (Shipped: 2026-02-07)

**Delivered:** WebRTC audio subsystem rebuilt with mediasoup SFU, browser UI for general and dispatch users, role-based permissions, Docker deployment.

**Stats:** 4 phases (1-4), 24 plans, ~4.2 hours execution time
**Timeline:** 2026-02-06 → 2026-02-07
**Requirements:** All satisfied

**Key accomplishments:**
- mediasoup SFU with WebRTC audio, Opus codec, <300ms latency
- JWT authentication with role-based permissions (Admin, Dispatch, General)
- Dispatch priority PTT, emergency broadcast, force-disconnect
- React web UI for general users (channel list, PTT buttons)
- Dispatch console with multi-channel monitoring and mute toggles
- Docker deployment with nginx TLS termination

---

## v4.0 Production Hardening & Location (Shipped: 2026-02-16)

**Delivered:** Production-ready audio reliability, adaptive location tracking, full-stack security hardening, and power optimization for the Android PTT client.

**Stats:** 5 phases (16-20), 13 plans, 61 commits, 87 source files, +5,516/-1,315 LOC
**Timeline:** 2 days (2026-02-15 → 2026-02-16)
**Git range:** `feat(16-01)` → `docs(phase-20)`
**Requirements:** 27/28 satisfied (PWR-04 battery profiling deferred by user choice)
**LOC totals:** 12,877 LOC Kotlin + 14,187 LOC TypeScript

**Key accomplishments:**
- Permission education screen on first launch with contextual rationale dialogs and Settings redirect after 2 denials
- PTT audio reliability: producer retry with exponential backoff, server ACK confirmation tone, transport health monitoring with 2s grace period, 15s orphan cleanup, and auto-rejoin (5 attempts)
- Adaptive location tracking with motion-aware throttling (STILL/WALKING/DRIVING), 50m deduplication, offline queue, server SQLite storage, and real-time dispatch broadcast
- Full-stack security audit: TLS/WSS enforcement, cleartext blocking, API authentication audit, DTLS verification, vulnerability scanning with documented findings
- Code quality tooling: ktlint, detekt, prettier, Husky pre-commit hooks with automated formatting on every commit
- Adaptive power management: wake lock timeout (300s configurable), per-channel polling (5s/15s), location power multipliers (2x/4x cascade with battery saver)

**Tech debt:**
- PWR-04 battery profiling deferred (all optimizations implemented, validation skipped per user decision)
- detekt maxIssues: -1 (566 weighted issues, security rules active)
- Consumer.getStats() stub quality (crow-misia API undocumented)
- No automated integration tests for audio pipeline

---

