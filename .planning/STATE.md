# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-06)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** Phase 2 - User Management & Access Control

## Current Position

Phase: 2 of 4 (User Management & Access Control)
Plan: 02 of 07 complete
Status: In progress
Last activity: 2026-02-06 — Completed 02-02-PLAN.md (Rate Limiting & Worker Optimization)

Progress: [██░░░░░░░░] 29% (1 phase complete + 2 of 7 plans in phase 2)

## Performance Metrics

**Velocity:**
- Total plans completed: 10
- Average duration: 10.3 minutes
- Total execution time: 1.7 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 8 | 93 min | 11.6 min |
| 02 | 2 | 12 min | 6.0 min |

**Recent Trend:**
- Last 5 plans: 01-06 (9 min), 01-07 (5 min), 01-08 (37 min), 02-01 (6 min), 02-02 (6 min)
- Trend: Fast execution for focused technical tasks, longer for integration/testing tasks

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Rebuild audio subsystem, keep everything else (Current audio is broken; user management and structure work well)
- Web-first, mobile apps later (Faster to market, WebRTC works in browsers, native apps can use same backend)
- Server-side decryption acceptable (Enables recording, compliance; servers in trusted environment)

**From 01-01 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| DEP-001 | Upgraded Node.js from v8.16.0 to v20 LTS | mediasoup requires Node.js 18+ for C++ worker compilation | Breaking change, but necessary for modern WebRTC libraries |
| DEP-002 | Replaced all legacy dependencies with modern equivalents | Node 8 dependencies are unmaintained and have security vulnerabilities | Complete dependency refresh, removed 7 legacy packages, added 10 modern ones |
| ARCH-001 | Created src/shared/ directory for types shared between server and client | Signaling protocol must be identical on both sides | Establishes pattern for all future shared code |
| CONFIG-001 | Configured Opus with 48kHz, 20ms ptime, usedtx=0 | Per research recommendations for real-time voice (can optimize to 10ms if needed) | Sets audio quality baseline for all channels |
| BUILD-001 | Excluded legacy src/lib from TypeScript compilation | Legacy code uses Node 8 patterns incompatible with strict TypeScript 5 | Old code preserved but not compiled; new code in src/server and src/shared |

**From 01-02 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| DEP-003 | Added Winston logger module for structured logging | mediasoup operations need module-specific logging for debugging worker/router/transport lifecycle | All mediasoup modules use child loggers with labels |
| ARCH-002 | Round-robin worker selection instead of random | Round-robin provides more even load distribution across workers | Worker selection is deterministic and balanced |
| CONFIG-002 | Opus codec requires channels: 2 in codec capabilities | mediasoup's supported Opus format requires channels: 2, rtcpFeedback arrays | Mono configuration applied at producer level, not codec capability level |
| PTT-001 | Producers and consumers start paused by default | PTT button not pressed = no audio transmission | Resume/pause methods control PTT state |

**From 01-03 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| STATE-001 | Redis v4 async/await API | Modern promise-based API replaces legacy callback pattern | All state operations use async/await, cleaner code |
| STATE-002 | Dedicated pub/sub clients | Redis v4 requires separate client instances for pub/sub | ChannelStateManager creates dedicated clients for pub/sub notifications |
| LOCK-001 | Fail-safe lock denial on Redis errors | When Redis operations fail, deny lock acquisition | Prevents multiple speakers if state management fails |
| LOCK-002 | Speaker lock TTL of 30s | Balances preventing deadlocks vs allowing reasonable PTT hold | Auto-expiry prevents stuck locks on client crashes |
| SESSION-001 | Session auto-expiry with 1-hour TTL | Automatic cleanup of stale sessions | Redis handles session cleanup without manual intervention |

**From 01-04 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| DEP-004 | Installed @types/jsonwebtoken for TypeScript JWT type definitions | jsonwebtoken package lacks bundled types | Enables proper type checking for JWT verification and payload extraction |
| SIG-001 | WebSocket server at dedicated /ws path | Per user decision for dedicated WebSocket channel for WebRTC signaling | Clear separation from HTTP endpoints, explicit signaling path |
| SIG-002 | Three JWT token locations: Authorization header, query param, sec-websocket-protocol | Authorization header is standard, query param for convenience, sec-websocket-protocol for legacy client compatibility | Flexible authentication supports multiple client implementations |
| SIG-003 | 30-second heartbeat interval with ping/pong | Detects dead connections from network failures or client crashes | Automatic cleanup of stale connections prevents resource leaks |
| PTT-002 | PTT_DENIED message sent to blocked clients with current speaker info | Per plan requirement to show "visual message showing [username] is speaking" when PTT denied | Clients receive denial reason with speaker identity for user feedback |
| SHUTDOWN-001 | Graceful shutdown closes resources in reverse initialization order | WebSocket → HTTP → ChannelState → Workers → Redis ensures clean teardown | SIGTERM/SIGINT handled gracefully, proper cleanup on deployment/restart |

**From 01-05 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| CODEC-001 | Disable Opus DTX (opusDtx: false) in audio production | Per research recommendations: DTX causes first-word cutoff in PTT scenarios by cutting silent packets | Critical for PTT audio quality; ensures all audio transmitted immediately on PTT press |
| CODEC-002 | Enable Opus FEC (opusFec: true) in audio production | Forward Error Correction improves audio quality over lossy networks | Better audio quality at cost of slightly higher bandwidth |
| CODEC-003 | Mono 48kHz audio with echo cancellation, noise suppression, AGC | PTT voice communication doesn't benefit from stereo; 48kHz is Opus native sample rate | Optimal quality/bandwidth balance for real-time voice; processing improves audio clarity |
| CLIENT-001 | Request-response correlation with 10-second timeout | WebSocket signaling needs to match responses to requests; 10s balances network delays vs UX | Enables async request/response pattern over WebSocket; prevents indefinite hangs |
| CLIENT-002 | Permission API with Safari fallback for microphone access | navigator.permissions.query not supported in Safari; check permission before getUserMedia for better UX | Graceful degradation across browsers; better UX by showing permission state early |
| CLIENT-003 | Track mute via track.enabled instead of stop() | PTT toggle needs fast enable/disable without re-requesting microphone permission | Efficient PTT toggle; no permission prompts on each press |

**From 01-06 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| UX-001 | Optimistic UI for instant PTT feedback | Physical walkie-talkie feel requires immediate visual/audio response | Button state changes before server confirms, reverts if denied |
| UX-002 | Framework-agnostic vanilla TypeScript components | PttButton will be wrapped in React for web-ui in Phase 3 | Components use plain DOM APIs, no framework dependencies |
| UX-003 | Configurable audio tones with folder/naming convention | Per user's specific idea for admin-uploadable event-specific audio prompts | Supports /audio/{tone}.mp3 and /audio/events/{eventId}/{tone}.mp3 paths |
| UX-004 | Busy state auto-reverts after 3 seconds | Prevent button from staying stuck in blocked state | User can retry PTT after brief cooldown |
| UX-005 | Controller creates button with bound callbacks | Simplifies initialization and ensures proper method binding | buttonContainer passed in options instead of pre-created button |

**From 01-07 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| RECONNECT-001 | Exponential backoff with 30s max delay and 0-500ms jitter | Fast recovery (1s initial) balanced with preventing server overload during outages; jitter prevents thundering herd when many clients reconnect simultaneously | Reconnection delays: 1s, 2s, 4s, 8s, 16s, 30s (cap); prevents coordinated reconnection storms |
| RECONNECT-002 | Message queue during reconnection (max 100 messages) | Ensures no lost PTT requests when users press button during brief network disruption | Seamless UX during reconnection; prevents memory issues with queue size limit |
| RECONNECT-003 | Stale PTT message filtering (>2s threshold) | PTT messages older than 2 seconds represent outdated button state (user likely released button) | Prevents replaying stale actions after reconnection; maintains button state accuracy |
| RECONNECT-004 | Clean disconnect does NOT trigger reconnection | User-initiated disconnect (e.g., leaving channel) should NOT auto-reconnect | Only unclean closes (network loss, server crash) trigger automatic reconnection |
| ARCH-003 | ISignalingClient interface for type compatibility | Enables ReconnectingSignalingClient to be drop-in replacement for SignalingClient in existing code | Type-safe wrapper pattern; no changes needed to MediasoupDevice or TransportClient consumers |
| RECONNECT-005 | Session recovery restores PTT lock if user was transmitting | If user was pressing PTT button when network disconnected, re-acquire speaker lock after reconnection | Maintains seamless UX; user doesn't need to release and re-press button after brief network blip |

**From 01-08 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| DEPLOY-001 | Nginx reverse proxy terminates TLS and proxies WSS to WS on Node.js server | Standard production pattern for SEC-02 (WSS with TLS/SSL): nginx handles TLS/SSL, Node.js server operates with plain HTTP/WS internally | TLS termination at nginx layer is simpler, more secure, and more performant than handling TLS in Node.js; satisfies SEC-02 requirement |
| DEPLOY-002 | Multi-stage Docker build: builder stage compiles TypeScript, production stage copies only dist/ and production node_modules | Builder stage installs all deps (including devDependencies) and compiles TypeScript; production stage copies only runtime artifacts | Smaller final image (no TypeScript, no dev tools, no source code); faster deployments |
| DEPLOY-003 | Self-signed certificates for development with SAN for localhost and 127.0.0.1 | Modern browsers require SAN (Subject Alternative Name) for certificate validation; self-signed certs enable local HTTPS/WSS testing | Enables development testing of WSS without purchasing real certificates; browser security warnings expected |
| DEPLOY-004 | esbuild for browser bundling (simple, fast, no complex build system) | Test page needs browser-compatible bundle; esbuild is simple and fast for Phase 1 testing | No complex webpack/vite configuration needed for test page; Phase 3 (Browser UI) will use proper production build system |
| TEST-001 | Test page served only in development mode (NODE_ENV !== 'production') | Production deployments should not expose internal testing tools | Routes check NODE_ENV before serving test page; security best practice |
| TEST-002 | Two-user test panels for real-time PTT interaction testing | Simulates real PTT interaction without complex test harness; each panel has own ConnectionManager instance | Enables comprehensive Phase 1 verification: two users in same channel, PTT arbitration (busy state), speaker notifications, reconnection |
| TEST-003 | Latency measurement with performance.now() and audio element 'playing' event | Record timestamp when PTT button pressed, calculate difference when audio starts playing | Enables verification of <300ms latency requirement (Phase 1 Success Criterion #2) |

**From 02-01 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| AUTH-001 | Admin role does NOT have PTT priority | Per user decision: Admin role is management, not real-time communication | Admin can force-disconnect and manage channels, but cannot interrupt speakers |
| AUTH-002 | Permission checks at channel join time only | No per-PTT-action overhead per user decision; balances security with performance | Enables <300ms PTT latency; heartbeat refresh catches revocations |
| AUTH-003 | 30-second heartbeat-based permission refresh interval | Balances Redis load vs revocation delay; matches existing heartbeat infrastructure | Catches permission changes within 30s without per-action database queries |
| AUTH-004 | 1-hour JWT token TTL combined with heartbeat sync | Balances security (fresh permissions) vs authentication overhead | Users stay authenticated for session duration; heartbeat ensures fresh permissions |
| AUDIT-001 | Non-blocking audit logging with fire-and-forget pattern | Audit logging must never break core PTT functionality | log() method wrapped in try/catch, never throws; Redis failures logged but ignored |
| AUDIT-002 | Redis audit log capped at 10,000 entries with LTRIM | Prevents unbounded growth while keeping recent history | Rolling window of recent events; older events exported to control-plane database |
| CONFIG-003 | 40-80ms jitter buffer configuration range | Per user constraint for network reliability on degraded networks | Server-side buffering smooths packet arrival times; improves audio quality on cellular/high-jitter networks |
| REDIS-001 | Redis key patterns match control-plane conventions | u.{userId}.g for user channels, g.{channelId}.u for channel users | Seamless integration with existing control-plane syncUserChannelsToRedis |

**From 02-02 execution:**

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| RATE-001 | Progressive slowdown instead of hard lockout | Legitimate users should never be permanently blocked. Exponential backoff (1s, 2s, 4s, 8s, 16s, 30s) slows brute force while keeping legitimate users unblocked after brief delay | Rate limiting is lenient, security through slowdown not hard denial |
| RATE-002 | Fail-safe rate limiting (fail open on Redis errors) | Redis connection issues or errors should NOT block legitimate users. Rate limiting is security layer, not critical path | getSlowdownMs() returns 0 on Redis errors, allowing operation to proceed |
| WORKER-001 | Load-aware worker selection over round-robin | Workers can have uneven load (channels with different user counts). Selecting worker with fewest routers provides better load distribution | More even load distribution across workers, better scalability |
| WORKER-002 | 25% CPU headroom for system overhead | Single-server 1000+ user target needs system resources for Redis, nginx, OS overhead. Reserve 25% CPU | getOptimalWorkerCount() returns floor(cpuCount * 0.75), e.g., 6 workers on 8-core system |
| TRANSPORT-001 | 600kbps outgoing bitrate for voice | Opus audio typically uses 24-48kbps. 600kbps provides 10-20x headroom for multiple consumers and transport overhead without overallocation | Increased from 100kbps baseline, sufficient for voice, prevents bandwidth waste |

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

None yet.

### Blockers/Concerns

[Issues that affect future work]

- ~~Phase 1: Node.js upgrade from v8.16.0 to v20 LTS required for mediasoup compatibility (major dependency upgrade)~~ **RESOLVED in 01-01:** Successfully upgraded to Node.js v24.13.0, TypeScript 5.9.3, mediasoup 3.19.17
- ~~Phase 1: Safari iOS-specific quirks need mobile testing beyond desktop Safari validation~~ **RESOLVED in 01-08:** Desktop Safari verified working in cross-browser testing; mobile Safari testing deferred to Phase 3/4
- Phase 2: Multi-server state consistency strategy needs research for distributed Redis pub/sub pattern
- Phase 2: Replace self-signed certificates with real TLS certificates for production deployment

## Session Continuity

Last session: 2026-02-06T10:43:59Z
Stopped at: Completed 02-02-PLAN.md (Rate Limiting & Worker Optimization)
Resume file: None

**Phase 2 (User Management & Access Control) in progress.**
- ✓ 02-01: Authorization Foundation complete (audit logging, security events backend)
- ✓ 02-02: Rate Limiting & Worker Optimization complete (progressive rate limiting, load-aware worker pool)
- Next: 02-03 onwards (permissions enforcement, role-based access control)
