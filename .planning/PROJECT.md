# VoicePing PTT Communications Platform

## What This Is

An enterprise-grade push-to-talk (PTT) communications system enabling distributed teams to coordinate during large-scale events using browser-based real-time audio. Users operate Android/iOS devices or desktop browsers as two-way radios with role-based access (Admin, Dispatch, General) and hierarchical organization (Events → Teams → Channels).

## Core Value

Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical.

## Requirements

### Validated

<!-- Existing capabilities from current voiceping-router system -->

- ✓ WebSocket-based real-time messaging infrastructure — existing
- ✓ JWT authentication for secure connections — existing
- ✓ Multi-device support (users can connect from multiple devices) — existing
- ✓ User role management (Admin, Dispatch, General) — existing
- ✓ Event → Team → Channel hierarchical organization — existing
- ✓ Group messaging with authorization (users must be in group to send) — existing
- ✓ Busy state management (prevent simultaneous talkers in same channel) — existing
- ✓ State persistence with Redis backing store — existing
- ✓ Control-plane REST API for user/event management — existing
- ✓ React web UI for dispatch console — existing
- ✓ PostgreSQL database with Prisma ORM — existing
- ✓ User registration, authentication, session management — existing
- ✓ Email integration (invites, password resets) — existing
- ✓ Docker deployment support — existing

### Active

<!-- v1 rebuild requirements -->

- [ ] **Browser-based PTT audio that actually works** (replace broken Opus implementation)
- [ ] WebRTC or modern audio codec implementation with reliable packet delivery
- [ ] Feature parity: All current working features continue to work with new audio system
- [ ] Dispatch users can talk and listen to multiple channels simultaneously
- [ ] Dispatch users can selectively mute/unmute channels for monitoring
- [ ] Admin users can create events and assign users to roles
- [ ] Dispatch users can create teams and channels within assigned events
- [ ] General users can PTT on assigned channels
- [ ] Low latency audio (100-300ms target)
- [ ] Architecture supports future AES-256 encryption (not validated in v1)
- [ ] Architecture supports future recording (not implemented in v1)
- [ ] Architecture scales to 1000+ concurrent users (not stress-tested in v1)

### Out of Scope

- Native mobile apps (Android/iOS) — web-first strategy, mobile apps in v2
- Recording functionality — planned for future, architecture should support it
- End-to-end encryption — server-side decryption acceptable for recording/compliance
- Scale validation beyond 100 users — architect for 1000+, validate incrementally
- Multi-tenant SaaS deployment — single-tenant instances only
- Security certifications (SOC2, etc.) — planned but not v1 blocker

## Context

### Current State

The existing voiceping-router system has a working user management, event/team/channel structure, and web UI. However, the audio subsystem is **broken** — Opus packets fail to decode properly in browser-to-browser communication, causing "invalid packet" errors on the receiving end. This makes the core PTT functionality unreliable.

### Technical Environment

- **Existing Stack:** Node.js 8.16.0 (outdated), TypeScript 3.5.1, React 18.3.1, Express, WebSockets (ws library), Redis, PostgreSQL
- **Current Audio:** Raw Opus packets via WebSocket (broken implementation)
- **Deployment:** Docker containers, single-tenant instances
- **Architecture:** WebSocket gateway pattern with in-memory + Redis state management

### Target Environment

- **Users:** 1000+ concurrent users per event (not all talking simultaneously)
- **Distribution:** Variable - some events local (single venue), others global (multi-region)
- **Deployment Models:**
  - Single-tenant instance per client (on-premise or client cloud)
  - Event-based ephemeral instances (spin up for event duration, tear down after)
- **Security:** Hosted in trusted environments (on-prem or client-controlled cloud), encryption in transit/at rest, server can decrypt for recording/compliance

### Use Cases

- **Large-scale event coordination:** Concert security, stadium operations, festival logistics
- **High-profile events:** Events requiring security certifications and encryption
- **Distributed teams:** Team leaders coordinating across multiple locations/venues
- **Dispatch monitoring:** Dispatch users monitoring 10-50 channels, selectively listening/muting

## Constraints

- **Platform:** Browser-first (Chrome, Firefox, Safari) — must work without native app installation
- **Deployment:** Single-tenant architecture — each client gets isolated instance
- **Latency:** 100-300ms target for PTT audio (press-to-talk to hearing audio)
- **Tech Stack:** Node.js + TypeScript ecosystem (can upgrade versions, but stay in JavaScript world)
- **Infrastructure:** Must support on-premise and cloud deployment (containerized)
- **Security:** Must support AES-256 encryption in architecture (validation can be later)
- **Legacy Preservation:** Must maintain existing user management, event structure, database schema

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Rebuild audio subsystem, keep everything else | Current audio is broken; user management and structure work well | — Pending |
| Web-first, mobile apps later | Faster to market, WebRTC works in browsers, native apps can use same backend | — Pending |
| Server-side decryption acceptable | Enables recording, compliance; servers in trusted environment | — Pending |
| Single-tenant deployment model | Security isolation, client-specific customization, on-premise support | — Pending |
| Target 100-300ms latency | Good enough for PTT use case, achievable with WebRTC without extreme optimization | — Pending |

---
*Last updated: 2026-02-06 after initialization*
