# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-06)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** Phase 1 - WebRTC Audio Foundation

## Current Position

Phase: 1 of 4 (WebRTC Audio Foundation)
Plan: 4 of TBD in current phase
Status: In progress
Last activity: 2026-02-06 — Completed 01-04-PLAN.md (WebSocket Signaling Server)

Progress: [████░░░░░░] 40% (estimated based on phase scope)

## Performance Metrics

**Velocity:**
- Total plans completed: 4
- Average duration: 10 minutes
- Total execution time: 0.7 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 4 | 39 min | 10 min |

**Recent Trend:**
- Last 5 plans: 01-01 (9 min), 01-02 (14 min), 01-03 (9 min), 01-04 (7 min)
- Trend: Improving (10 min average, last plan 7 min)

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

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

None yet.

### Blockers/Concerns

[Issues that affect future work]

- ~~Phase 1: Node.js upgrade from v8.16.0 to v20 LTS required for mediasoup compatibility (major dependency upgrade)~~ **RESOLVED in 01-01:** Successfully upgraded to Node.js v24.13.0, TypeScript 5.9.3, mediasoup 3.19.17
- Phase 1: Safari iOS-specific quirks need mobile testing beyond desktop Safari validation
- Phase 2: Multi-server state consistency strategy needs research for distributed Redis pub/sub pattern

## Session Continuity

Last session: 2026-02-06T07:06:25Z
Stopped at: Completed 01-04-PLAN.md execution (WebSocket Signaling Server)
Resume file: None
