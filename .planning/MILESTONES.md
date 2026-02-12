# Milestones

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
