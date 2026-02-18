# VoicePing PTT Communications Platform

## What This Is

An enterprise-grade push-to-talk (PTT) communications system enabling distributed teams to coordinate during large-scale events. Field workers carry Android devices as two-way radios with real WebRTC audio via mediasoup, hardware PTT buttons, multi-channel scan mode, and adaptive location tracking. Dispatch users monitor channels and field worker positions from a browser-based console with a real-time interactive satellite map showing live markers, motion state, battery telemetry, and configurable status popups. The platform is production-hardened with TLS enforcement, security auditing, permission management, and adaptive power optimization. Role-based access (Admin, Dispatch, General) and hierarchical organization (Events → Teams → Channels) provide structure for coordinating 1000+ team members.

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
- ✓ libmediasoup-android 0.21.0 with real WebRTC audio (PeerConnectionFactory, AEC, NS) — v3.0
- ✓ RecvTransport per-channel with Consumer lifecycle and audio playback — v3.0
- ✓ SendTransport with Producer lifecycle for PTT microphone transmission — v3.0
- ✓ Mutex-protected transport lifecycle with error recovery — v3.0
- ✓ ProGuard/R8 rules for JNI class preservation in release builds — v3.0
- ✓ Physical device testing: end-to-end audio verified on Samsung Galaxy S21 (Android 16) — v3.0
- ✓ Battery profiling: 5%/hour with screen off and foreground service — v3.0
- ✓ Permission education screen on first launch with contextual rationale dialogs — v4.0
- ✓ Graceful degradation on permission revocation (error state, not crash) — v4.0
- ✓ Settings redirect after 2 permission denials — v4.0
- ✓ PTT producer retry with exponential backoff (3 total attempts) — v4.0
- ✓ Server ACK for transmission confirmation with confirmation tone — v4.0
- ✓ Transport health monitoring: 2s grace period, 15s orphan cleanup, auto-rejoin (5 attempts) — v4.0
- ✓ Opus FEC always enabled with packetLossPercentage=10 — v4.0
- ✓ Adaptive location tracking with motion-aware throttling (STILL/WALKING/DRIVING) — v4.0
- ✓ Server location storage (SQLite) with dispatch broadcast and API documentation — v4.0
- ✓ Offline location queue (50 max) with batch flush on reconnect — v4.0
- ✓ Android 14+ foreground service type (mediaPlayback|location) — v4.0
- ✓ TLS/WSS enforcement on all signaling, cleartext blocking in release builds — v4.0
- ✓ All API endpoints authenticated, DTLS encryption verified — v4.0
- ✓ Code quality tooling: ktlint, detekt, prettier, Husky pre-commit hooks — v4.0
- ✓ Wake lock timeout (300s configurable) with instant reacquisition on audio — v4.0
- ✓ Adaptive per-channel network polling (5s active, 15s idle) — v4.0
- ✓ Location power multipliers (2x wake lock release, 4x battery saver) — v4.0
- ✓ Battery telemetry pipeline: Android → Server → Web with backward-compatible protocol extension — v5.0
- ✓ Server-side low-battery alerting with hysteresis (fires below 20%, resets above) — v5.0
- ✓ Split dispatch layout with CSS Grid (channels left, map right) and collapsible panel — v5.0
- ✓ Responsive mobile tabs with CSS-only visibility control (audio continues in both views) — v5.0
- ✓ Leaflet map with Esri satellite tiles, OSM street layer, proper React cleanup — v5.0
- ✓ Real-time location markers via WebSocket with DivIcon radio icons and username labels — v5.0
- ✓ Initial position loading via LOCATION_QUERY with staggered batch updates — v5.0
- ✓ Motion state icons (STILL/WALKING/DRIVING) with staleness treatment (>5 min faded) — v5.0
- ✓ Marker clustering via leaflet.markercluster for 200+ markers without performance collapse — v5.0
- ✓ Interactive status popups with location, motion, channel, PTT, battery, connection data — v5.0
- ✓ Glassmorphic toolbar with auto-fit bounds and toast notifications — v5.0
- ✓ User/channel search with autocomplete, keyboard navigation, and fly-to — v5.0
- ✓ Configurable popup field settings with localStorage persistence and live preview — v5.0

### Active

(No active requirements — planning next milestone)

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
- 3D map view — complexity, no clear dispatch value
- Real-time geofencing alerts — server-side polygon evaluation complex, defer to future
- Offline maps — tile storage licensing complexity, dispatch consoles have reliable internet

## Context

### Current State

Five milestones shipped. Full-stack PTT communications platform with server-side mediasoup SFU, React web UI with dispatch map, native Android client with real WebRTC audio, adaptive location tracking, battery telemetry, TLS security, and power optimization. 26 phases, 87 plans executed across all milestones.

**Server:** ~14,200+ LOC TypeScript — Node.js v24, mediasoup 3.19, Redis, PostgreSQL/Prisma, SQLite (location), Docker deployment
**Android:** ~12,900+ LOC Kotlin — Jetpack Compose, Hilt DI, Room database, Media3, libmediasoup-android 0.21.0, ~100 source files
**Web:** ~9,900 LOC JSX/CSS/JS — React 18, Vite, mediasoup-client, Leaflet 1.9.4, leaflet.markercluster 1.5.3

### Known Issues

- Consumer.getStats() returns stub "Good" quality (crow-misia API undocumented)
- No automated integration tests for mediasoup audio pipeline (requires physical device + server)
- Multi-server state consistency needs research for distributed Redis pub/sub
- Self-signed certificates need replacement with real TLS for production
- HW-02 rugged phone PTT deferred (hardware unavailable)
- PWR-04 battery profiling not validated (implementation complete, profiling deferred)
- detekt maxIssues: -1 (566 weighted issues from noisy rules, security rules active)
- Hardcoded JWT secret default (production MUST override via ROUTER_JWT_SECRET env var)
- LOW_BATTERY_ALERT server-side only — no web client listener for dispatcher toast (intentional deferral)
- PTT placeholder button in map popup ("Coming soon" — reserved for future feature)

## Constraints

- **Platform:** Android 8+ (API 26) — covers ~95% of devices
- **Stack:** Server: Node.js/TypeScript/mediasoup. Android: Kotlin/Compose/Hilt. Web: React/Vite/Leaflet
- **Backend:** Clients consume existing WebSocket protocol; protocol extensions must be backward-compatible (additive fields only)
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
| Kotlin native for Android | Best performance, platform integration for background services, hardware buttons, audio routing | ✓ Good — v2.0 shipped |
| Scan mode bottom bar pattern | Emulates two-way radio scan — familiar to field workers | ✓ Good |
| Max 5 simultaneous channels | Bandwidth constraint on mobile, sufficient for field worker use case | ✓ Good |
| No server changes for Android | Existing WebSocket/mediasoup protocol is client-agnostic | ✓ Good — zero server modifications needed |
| Hilt DI with @Singleton providers | 22 singletons, clean dependency graph, testable architecture | ✓ Good |
| DataStore for settings persistence | Type-safe Flow API, async by default, modern Kotlin-first | ✓ Good |
| Media3 MediaSession for Bluetooth PTT | Modern API, only active when service running | ✓ Good |
| Exponential backoff with 30s cap, 5min max | Prevents server storms while staying responsive | ✓ Good |
| Room database for offline caching | Cache-first loading, 3 entities | ✓ Good |
| crow-misia libmediasoup-android 0.21.0 | Best maintained fork, Kotlin-first, WebRTC M130 | ✓ Good |
| PeerConnectionFactory.initialize() pattern | crow-misia API differs from haiyangwu wrapper | ✓ Good |
| runBlocking bridge for Transport callbacks | Native JNI threads need blocking bridge to suspend signaling | ✓ Good |
| Per-channel RecvTransport map | Multi-channel monitoring requires independent transport lifecycle | ✓ Good |
| SendTransport singleton | PTT is mutually exclusive, one transport per device | ✓ Good |
| WebRTC AudioSource for PTT capture | Library handles audio capture internally | ✓ Good |
| Kotlin Mutex for transport lifecycle | Suspend functions can't use synchronized blocks | ✓ Good |
| @Volatile flag for produce/stop race | JNI blocking call prevents Mutex/cancel solutions | ✓ Good |
| Full R8 optimization (no -dontobfuscate) | Production builds need code shrinking and obfuscation | ✓ Good |
| In-memory permission denial tracking | Resets on app restart, no persistent tracking | ✓ Good |
| Settings redirect after 2 denials | Prevents infinite prompt loops | ✓ Good |
| Producer retry: 2 retries, exponential backoff | Recovers from transient WebRTC failures | ✓ Good |
| Opus DTX disabled, FEC always enabled | Continuous stream with comfort noise, packet loss recovery | ✓ Good |
| 2s grace period for mid-transmission failures | Reduces false failures from transient disconnects | ✓ Good |
| 15s orphan transport cleanup | Aligns with WebRTC auto-recovery window | ✓ Good |
| 5 max auto-rejoin attempts | Balances recovery with user control | ✓ Good |
| ActivityTransition API for motion detection | Hardware-backed, battery-efficient | ✓ Good |
| SQLite for location storage (not PostgreSQL) | Lightweight, embedded, 24h retention | ✓ Good |
| 50m deduplication threshold | Reduces redundant location sends | ✓ Good |
| Network security config (cleartext blocking) | OS-level TLS enforcement for all connections | ✓ Good |
| detekt maxIssues: -1 for initial run | Noisy rules disabled, security rules active | ⚠️ Revisit |
| Wake lock 300s timeout, server-configurable | Balances battery vs responsiveness | ✓ Good |
| Location multiplier cascade (2x/4x) | Coordinated power reduction across subsystems | ✓ Good |
| Leaflet + Esri World Imagery for dispatch map | Free, no API key, high quality satellite tiles, lightweight | ✓ Good — v5.0 shipped |
| OpenStreetMap over Google Maps | No API key costs, free unlimited usage, good enough satellite via Esri | ✓ Good — v5.0 shipped |
| Battery % as additive protocol field | Backward-compatible — old clients omit field, server/web handle missing gracefully | ✓ Good — v5.0 shipped |
| Cross-component stability constraint | v5.0 touches server, Android, web — all protocol changes backward-compatible | ✓ Good — v5.0 shipped |
| Vanilla Leaflet with useRef (not react-leaflet) | Better cleanup control, React Strict Mode compatible | ✓ Good — zero memory leaks |
| Separate LocationContext from ChannelContext | High-frequency location updates don't re-render channel components | ✓ Good — performance isolated |
| leaflet.markercluster for 200+ markers | Canvas-based clustering prevents DOM explosion | ✓ Good — production scale |
| Glassmorphic toolbar with @supports fallback | Modern UI with graceful degradation for older browsers | ✓ Good |
| localStorage per-event map state, global popup settings | Map position event-scoped, popup preferences user-scoped | ✓ Good |

---
*Last updated: 2026-02-18 after v5.0 milestone*
