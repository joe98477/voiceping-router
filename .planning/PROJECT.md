# VoicePing PTT Communications Platform

## What This Is

An enterprise-grade push-to-talk (PTT) communications system enabling distributed teams to coordinate during large-scale events. Field workers carry Android devices as two-way radios with headset PTT buttons, while dispatch users monitor channels from a browser-based console. Role-based access (Admin, Dispatch, General) and hierarchical organization (Events → Teams → Channels) provide structure for coordinating 1000+ team members.

## Core Value

Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical.

## Current Milestone: v2.0 Android Client App

**Goal:** Native Android app that turns a phone into a pocket two-way radio for general users — screen off, device in pocket, triggered by headset PTT button, audio through headset.

**Target features:**
- Kotlin native Android app (API 26+, Material 3)
- Login → Event picker → Channel list with team grouping
- Monitor up to 5 channels simultaneously with mixed audio playback
- Scan mode bottom bar: primary channel by default, auto-switches to active channel for quick response, drops back to primary
- Configurable hardware PTT: volume keys, dedicated PTT buttons, Bluetooth/headset PTT
- Foreground service keeps audio alive with screen off (pocket radio mode)
- Unmonitored/muted channels unsubscribe from audio to save bandwidth
- Settings: scan mode behavior, hardware button mapping, primary channel, audio output, per-channel volume, earpiece vs speaker

## Requirements

### Validated

<!-- Milestone 1: WebRTC audio rebuild + web UI (completed 2026-02-07) -->

- ✓ WebRTC audio via mediasoup SFU with <300ms latency — Milestone 1
- ✓ WebSocket signaling for WebRTC offer/answer/ICE exchange — Milestone 1
- ✓ Opus codec configured for PTT (CBR, DTX disabled, FEC enabled) — Milestone 1
- ✓ PTT with busy state management (one speaker per channel) — Milestone 1
- ✓ JWT authentication with 1-hour TTL + 30s heartbeat refresh — Milestone 1
- ✓ Role-based permissions (Admin, Dispatch, General) — Milestone 1
- ✓ Progressive rate limiting (never hard lockout) — Milestone 1
- ✓ Dispatch priority PTT and emergency broadcast — Milestone 1
- ✓ Admin force-disconnect, ban/unban — Milestone 1
- ✓ React web UI for general users (channel list, PTT) — Milestone 1
- ✓ React dispatch console (multi-channel monitoring, mute toggles) — Milestone 1
- ✓ Real-time permission sync via Redis pub/sub — Milestone 1
- ✓ Reconnection with exponential backoff and session recovery — Milestone 1
- ✓ Docker deployment with nginx TLS termination — Milestone 1
- ✓ Control-plane REST API for user/event management — existing
- ✓ PostgreSQL database with Prisma ORM — existing
- ✓ User registration, authentication, session management — existing
- ✓ Email integration (invites, password resets) — existing
- ✓ Event → Team → Channel hierarchical organization — existing

### Active

<!-- v2.0 Android client app -->

- [ ] Kotlin native Android app with Material 3 design (API 26+)
- [ ] Login screen with email/password authentication
- [ ] Event picker screen for selecting active event
- [ ] Channel list grouped by team with per-channel PTT buttons
- [ ] Monitor up to 5 channels simultaneously with mixed audio playback
- [ ] Primary/default channel concept with persistent bottom bar
- [ ] Scan mode: auto-switch bottom bar to active channel, drop back to primary
- [ ] Configurable scan mode behavior in settings
- [ ] Hardware PTT support: volume keys, dedicated PTT buttons, Bluetooth/headset PTT
- [ ] Configurable hardware button mapping in settings
- [ ] Foreground service for background audio (screen off, pocket radio mode)
- [ ] Partial wake lock to keep WebSocket/audio alive when screen locked
- [ ] Audio focus management (pause PTT during phone calls, resume after)
- [ ] Silent auto-reconnect on network loss
- [ ] Speaker name + pulse animation for active channel indicators
- [ ] Unmonitored/muted channels unsubscribe from audio (bandwidth savings)
- [ ] Settings: audio output device, per-channel volume, earpiece vs speaker
- [ ] Consumes existing WebSocket signaling protocol and mediasoup SFU (no server changes)

### Out of Scope

- iOS native app — Android first, iOS in future milestone
- Dispatch role in Android app — web dispatch console sufficient for now
- Admin role in Android app — web admin console sufficient for now
- Push notifications — user monitors channels directly, no notifications for unmonitored channels
- Recording functionality — planned for future milestone
- End-to-end encryption — server-side decryption acceptable for recording/compliance
- Multi-tenant SaaS deployment — single-tenant instances only
- Offline mode — real-time coordination requires connectivity
- Play Store submission — deliverable is compilable Android Studio project

## Context

### Current State

Milestone 1 complete: WebRTC audio subsystem rebuilt with mediasoup SFU, web UI for general and dispatch users, role-based permissions, Docker deployment. All 4 phases (26 plans) shipped and verified. The server-side infrastructure is stable and ready to accept native Android clients using the same WebSocket signaling protocol.

### Technical Environment

- **Server Stack:** Node.js v24, TypeScript 5.9, mediasoup 3.19, Redis v4, Express, PostgreSQL/Prisma
- **Web Client:** React 18, Vite, mediasoup-client
- **Audio:** WebRTC via mediasoup SFU, Opus codec (48kHz, CBR, DTX disabled, FEC enabled)
- **Signaling:** WebSocket at /ws path, JWT authentication, JSON message protocol
- **Deployment:** Docker containers with nginx TLS termination

### Target Environment

- **Users:** General role field workers carrying Android devices as pocket radios
- **Usage pattern:** Screen off, device in pocket, headset with PTT button, 8-12 hour shifts
- **Network:** Variable — WiFi at venues, cellular in field, may have spotty coverage
- **Devices:** Android 8+ (API 26), covering ~95% of Android devices
- **Accessories:** Bluetooth headsets with PTT buttons, wired headsets, ruggedized phone PTT buttons

### Use Cases

- **Field worker radio:** Security guard with earpiece, PTT button on belt clip, monitoring primary channel
- **Multi-channel monitoring:** Team lead listening to 3-5 channels, responding to active conversations via scan mode
- **Event coordination:** Parking, catering, security teams each on their own channels, team leads switching between

## Constraints

- **Platform:** Android 8+ (API 26) — covers ~95% of devices
- **Stack:** Kotlin native with Material 3 design
- **Backend:** No server changes — Android app is a pure client consuming existing protocol
- **Latency:** Same 100-300ms target as web client
- **Background:** Must function as pocket radio — foreground service, screen off, headset PTT
- **Battery:** Standard foreground service with partial wake lock — device must stay responsive
- **Audio:** Must handle audio focus (phone calls pause PTT), earpiece/speaker/Bluetooth routing
- **Channels:** Max 5 simultaneous monitored channels per user (bandwidth constraint)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Rebuild audio subsystem, keep everything else | Current audio is broken; user management and structure work well | ✓ Good — Milestone 1 |
| Web-first, mobile apps later | Faster to market, WebRTC works in browsers, native apps can use same backend | ✓ Good — web proven, now Android |
| Server-side decryption acceptable | Enables recording, compliance; servers in trusted environment | — Pending |
| Single-tenant deployment model | Security isolation, client-specific customization, on-premise support | ✓ Good |
| Target 100-300ms latency | Good enough for PTT use case, achievable with WebRTC without extreme optimization | ✓ Good — verified in Milestone 1 |
| Kotlin native for Android | Best performance, platform integration for background services, hardware button access, audio routing | — Pending |
| Scan mode bottom bar pattern | Emulates two-way radio scan behavior — familiar to field workers, efficient interaction | — Pending |
| Max 5 simultaneous channels for general users | Bandwidth constraint on mobile, sufficient for field worker use case | — Pending |
| No server changes for Android client | Existing WebSocket/mediasoup protocol is client-agnostic | — Pending |

---
*Last updated: 2026-02-08 after milestone v2.0 initialization*
