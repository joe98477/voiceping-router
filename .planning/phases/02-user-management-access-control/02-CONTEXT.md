# Phase 2: User Management & Access Control - Context

**Gathered:** 2026-02-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Role-based permissions and organizational hierarchy (events > teams > channels) enforced in the WebRTC router. The existing control-plane (Prisma models, REST API, session auth) already manages users, roles, events, teams, and channels. Phase 2 bridges that control-plane into the real-time signaling layer so the router enforces what the control-plane manages. The system must be hardened for reliability — critical audio must get through even on degraded, low-bandwidth, high-jitter networks.

</domain>

<decisions>
## Implementation Decisions

### Authorization Enforcement
- Claude's discretion on auth sync mechanism (JWT claims vs Redis sync vs hybrid), with hard constraints: world-class security, resilient to network issues, critical audio must never fail due to auth delays
- Permission checks at channel join time only — no per-PTT-action checks
- Regular heartbeat refreshes permissions between joins (catches revocations without per-action overhead)
- Graceful removal on permission revocation: if user is actively transmitting, let them finish current PTT, then remove
- Dispatch and Admin can force-disconnect a user immediately (bypasses graceful removal for malicious/disruptive transmissions)
- Single auth flow: user logs in once, gets a shared token that works for both control-plane UI and router WebSocket
- Medium token TTL (1 hour) combined with heartbeat-based permission sync
- Lenient rate limiting with progressive slowdown on repeated failures (not hard lockout)
- Full audit logging: all auth events including logins, permission checks, denials, force-disconnects, role changes
- Security events backend in Phase 2: rate limiting data storage, ban/unban API endpoints, security event logging — UI panel deferred to Phase 3

### Channel Join Flow
- Server pushes channel list to user on connect (users can only select from assigned channels/teams)
- Live updates via WebSocket push event when channel assignments change mid-session (auto-add new channels, auto-remove revoked ones)
- General users: listen to multiple channels simultaneously, PTT on one at a time (radio scanner model)
- Auto-listen on join: joining a channel immediately starts audio reception; user can mute individual channels
- User selects which assigned channels to actively monitor (not all-assigned = all-active)
- Configurable simultaneous channel limit (default set by Admin/Dispatch per event); when limit reached, block with message "Maximum channels reached. Remove a channel to add another."

### Role Behavior Boundaries
- Per-event roles: same user can be Dispatch in Event A and General in Event B (matches existing Prisma EventMembership model)
- Dispatch has PTT priority interrupt: can immediately take over channel, cutting off current General user speaker
- Admin does NOT have PTT priority — Admin role is management, not real-time communication
- Dispatch can force-disconnect (instant kick) a user from a channel in real-time
- Emergency broadcast: Dispatch can transmit to ALL channels in an event simultaneously, overriding all active speakers
- Emergency broadcast activation: 2-second long press on distinct broadcast button (prevents accidental activation, no dialog)
- Interrupted user experience: immediate audio cutoff + notification "Dispatch [name] has priority" — must re-press PTT after Dispatch finishes
- Visible roles: General users see role badges/indicators for Dispatch users in their channel

### Scaling & Concurrency
- Target: 1000+ distributed team members (100 concurrent is Phase 2 test milestone, not the ceiling)
- Single server, optimized — vertical scaling, optimize worker pool and memory (multi-server deferred)
- Configurable max users per channel (Admin/Dispatch sets per-channel cap when creating)
- Server-side jitter buffer (40-80ms) to smooth network jitter before forwarding audio — improves reliability on degraded networks at cost of small added latency

### Claude's Discretion
- Auth sync mechanism choice (JWT claims, Redis sync, or hybrid) — must meet security and reliability constraints
- Heartbeat interval and permission refresh strategy
- Jitter buffer size tuning (within 40-80ms range)
- Rate limiting thresholds and progressive slowdown curve
- Worker pool optimization strategy for single-server 1000+ target
- Security event data model design

</decisions>

<specifics>
## Specific Ideas

- "Critical audio must get through — the system needs to be hardened for jitter, interference, and low bandwidth/low speed networks"
- "Security events section in admin console — Dispatch and Admin should be able to review security incidents and easily un-ban non-threats" (backend in Phase 2, UI in Phase 3)
- Emergency broadcast with long-press guard modeled after real radio systems (prevent accidental all-channel transmit)
- Radio scanner model for General users: listen to many channels, talk on one — like monitoring multiple radio frequencies
- Existing control-plane already has full user/event/team/channel management — Phase 2 is about making the router respect those permissions in real-time

</specifics>

<deferred>
## Deferred Ideas

- Security events admin UI panel — Phase 3 (backend/API built in Phase 2)
- Multi-server horizontal scaling with load balancer — future phase when single-server vertical scaling is insufficient
- Mobile-specific optimizations for low-bandwidth networks — Phase 3/4

</deferred>

---

*Phase: 02-user-management-access-control*
*Context gathered: 2026-02-06*
