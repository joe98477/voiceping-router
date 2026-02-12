# VoicePing PTT Communications Platform

## What This Is

An enterprise-grade push-to-talk (PTT) communications system enabling distributed teams to coordinate during large-scale events. Field workers carry Android devices as two-way radios with hardware PTT buttons and multi-channel scan mode, while dispatch users monitor channels from a browser-based console. Role-based access (Admin, Dispatch, General) and hierarchical organization (Events → Teams → Channels) provide structure for coordinating 1000+ team members.

## Core Value

Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical.

## Requirements

### Validated

- ✓ WebRTC audio via mediasoup SFU with <300ms latency — v1.0
- ✓ WebSocket signaling for WebRTC offer/answer/ICE exchange — v1.0
- ✓ Opus codec configured for PTT (CBR, DTX disabled, FEC enabled) — v1.0
- ✓ PTT with busy state management (one speaker per channel) — v1.0
- ✓ JWT authentication with 1-hour TTL + 30s heartbeat refresh — v1.0
- ✓ Role-based permissions (Admin, Dispatch, General) — v1.0
- ✓ Progressive rate limiting (never hard lockout) — v1.0
- ✓ Dispatch priority PTT and emergency broadcast — v1.0
- ✓ Admin force-disconnect, ban/unban — v1.0
- ✓ React web UI for general users (channel list, PTT) — v1.0
- ✓ React dispatch console (multi-channel monitoring, mute toggles) — v1.0
- ✓ Real-time permission sync via Redis pub/sub — v1.0
- ✓ Reconnection with exponential backoff and session recovery — v1.0
- ✓ Docker deployment with nginx TLS termination — v1.0
- ✓ Control-plane REST API for user/event management — existing
- ✓ PostgreSQL database with Prisma ORM — existing
- ✓ User registration, authentication, session management — existing
- ✓ Email integration (invites, password resets) — existing
- ✓ Event → Team → Channel hierarchical organization — existing
- ✓ Kotlin native Android app with Material 3 design (API 26+) — v2.0
- ✓ Login with email/password, event picker, channel list with team grouping — v2.0
- ✓ PTT press-and-hold/toggle with busy state, audio feedback, haptics — v2.0
- ✓ Earpiece/speaker/Bluetooth audio routing — v2.0
- ✓ Monitor up to 5 channels with scan mode auto-switch — v2.0
- ✓ Primary channel concept with configurable return delay — v2.0
- ✓ Foreground service pocket radio mode (screen off, wake lock) — v2.0
- ✓ Persistent notification with PTT controls — v2.0
- ✓ Phone call interruption handling (pause/resume) — v2.0
- ✓ Hardware PTT: volume keys, Bluetooth headset via MediaSession — v2.0
- ✓ Configurable button mapping in settings — v2.0
- ✓ Boot auto-start (optional) — v2.0
- ✓ Auto-reconnect with exponential backoff (30s cap, 5min max) — v2.0
- ✓ WiFi/cellular handoff with immediate retry on network restore — v2.0
- ✓ Offline caching via Room database — v2.0
- ✓ Network quality indicator and transmission history — v2.0
- ✓ Consolidated settings screen — v2.0
- ✓ Per-channel volume control — v2.0

### Active

(None — define with `/gsd:new-milestone`)

### Out of Scope

- iOS native app — Android first, iOS in future milestone
- Dispatch role in Android app — web dispatch console sufficient for now
- Admin role in Android app — web admin console sufficient for now
- Push notifications — user monitors channels directly via foreground service
- Recording functionality — planned for future milestone
- End-to-end encryption — server-side decryption acceptable for recording/compliance
- Multi-tenant SaaS deployment — single-tenant instances only
- Play Store submission — deliverable is compilable Android Studio project
- Rugged phone dedicated PTT (HW-02) — deferred, hardware unavailable for testing
- Video streaming — bandwidth/battery drain, not PTT use case

## Context

### Current State

Two milestones shipped. Server-side WebRTC audio subsystem with mediasoup SFU, React web UI for general and dispatch users, and native Android PTT client app. 10 phases, 50 plans executed across both milestones.

**Server:** ~8,000 LOC TypeScript — Node.js v24, mediasoup 3.19, Redis, PostgreSQL/Prisma, Docker deployment
**Android:** ~9,200 LOC Kotlin — Jetpack Compose, Hilt DI, Room database, Media3, 88 source files
**Web:** React 18, Vite, mediasoup-client

### Known Issues

- On-device testing not yet performed for Android app (development done without physical device)
- MediasoupClient contains TODO placeholders for libmediasoup-android library integration
- Multi-server state consistency needs research for distributed Redis pub/sub
- Self-signed certificates need replacement with real TLS for production

## Constraints

- **Platform:** Android 8+ (API 26) — covers ~95% of devices
- **Stack:** Server: Node.js/TypeScript/mediasoup. Android: Kotlin/Compose/Hilt. Web: React/Vite
- **Backend:** Android app is a pure client consuming existing WebSocket protocol (no server changes)
- **Latency:** 100-300ms target for PTT activation to audio
- **Background:** Foreground service with partial wake lock for pocket radio mode
- **Channels:** Max 5 simultaneous monitored channels per general user (bandwidth constraint)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Rebuild audio subsystem, keep everything else | Current audio is broken; user management and structure work well | ✓ Good — v1.0 shipped |
| Web-first, mobile apps later | Faster to market, WebRTC works in browsers, native apps can use same backend | ✓ Good — web proven, Android shipped |
| Server-side decryption acceptable | Enables recording, compliance; servers in trusted environment | — Pending |
| Single-tenant deployment model | Security isolation, client-specific customization, on-premise support | ✓ Good |
| Target 100-300ms latency | Good enough for PTT use case, achievable with WebRTC | ✓ Good — verified v1.0 |
| Kotlin native for Android | Best performance, platform integration for background services, hardware buttons, audio routing | ✓ Good — v2.0 shipped, 88 files clean architecture |
| Scan mode bottom bar pattern | Emulates two-way radio scan — familiar to field workers, efficient interaction | ✓ Good — clean implementation with configurable behavior |
| Max 5 simultaneous channels | Bandwidth constraint on mobile, sufficient for field worker use case | ✓ Good — covers target use cases |
| No server changes for Android | Existing WebSocket/mediasoup protocol is client-agnostic | ✓ Good — zero server modifications needed |
| Hilt DI with @Singleton providers | 22 singletons, clean dependency graph, testable architecture | ✓ Good — no circular dependencies |
| DataStore for settings persistence | Type-safe Flow API, async by default, modern Kotlin-first | ✓ Good — 6 preference groups |
| Media3 MediaSession for Bluetooth PTT | Modern API, only active when service running (doesn't steal media buttons) | ✓ Good — clean integration |
| Exponential backoff with 30s cap, 5min max | Prevents server storms while staying responsive to network changes | ✓ Good — handles WiFi/cellular handoff |
| Room database for offline caching | Cache-first loading pattern, 3 entities (Event, Channel, Team) | ✓ Good — seamless offline experience |

---
*Last updated: 2026-02-13 after v2.0 milestone*
