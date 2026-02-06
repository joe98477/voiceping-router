# Project Research Summary

**Project:** VoicePing Router - WebRTC PTT Audio Subsystem Rebuild
**Domain:** Enterprise Push-to-Talk over Cellular (PoC) Communications Platform
**Researched:** 2026-02-06
**Confidence:** HIGH

## Executive Summary

The VoicePing platform is an enterprise-grade push-to-talk (PTT) communications system for large event coordination (1000+ concurrent users). The current audio implementation using raw Opus packets over WebSocket is fundamentally broken and cannot be salvaged—browsers encode Opus for RTP transport within WebRTC, not raw frame transmission. The research conclusively points to **mediasoup 3.x as the SFU (Selective Forwarding Unit)** as the correct architecture, integrating cleanly with existing WebSocket signaling infrastructure while providing production-grade performance.

The recommended approach centers on a hybrid architecture: preserve all existing infrastructure (WebSocket server, Redis state management, Express REST API, PostgreSQL database, JWT authentication) while adding mediasoup as a dedicated media routing layer. WebSocket becomes the signaling channel for WebRTC offer/answer/ICE exchange, while actual audio flows via properly encapsulated WebRTC connections. This SFU architecture is the only viable option for PTT at scale—MCU adds unnecessary mixing overhead, and P2P mesh fails beyond 4 users.

Critical risks include: (1) Opus codec misconfiguration leading to latency >300ms, (2) memory leaks in long-running 8-12 hour event sessions, (3) TURN server costs exploding with dispatch users monitoring 10-50 channels, and (4) state synchronization complexity at 1000+ users. All risks have clear mitigation strategies documented in research and must be addressed during specific phases—codec configuration in Phase 1, architecture decisions in Phase 2, memory management in Phase 3.

## Key Findings

### Recommended Stack

The research identifies **mediasoup v3.19.16+** as the clear choice for WebRTC media routing. It's a mature, Node.js-native SFU with proven 1000+ user scalability, averaging 120ms latency under load. The existing tech stack remains largely intact but requires critical upgrades: Node.js must upgrade from EOL v8.16.0 to v20 LTS (mediasoup requires Node 16+), TypeScript from 3.5.1 to 5.x, and the router's Redis client from deprecated 2.8.0 to async-first 4.7.0.

**Core technologies:**
- **mediasoup 3.19.16+**: WebRTC SFU for server-side media routing—industry standard with 19,744 weekly downloads, handles 500-1000 users per server without transcoding overhead
- **Opus codec (built into WebRTC)**: Mandatory WebRTC codec (RFC 7874) with 16-32kbps for PTT speech, 5-26.5ms algorithmic delay, FEC for packet loss recovery
- **Node.js 20 LTS**: Runtime upgrade required (current v8 EOL'd in 2019)—mediasoup needs modern native bindings
- **WebSocket (ws 8.x)**: Existing signaling infrastructure reused for WebRTC SDP/ICE exchange—no architectural change needed
- **Redis 4.7.0+**: Continue using for distributed state with mediasoup worker/router state added for multi-server coordination

**Critical decision:** SFU architecture is non-negotiable. PTT characteristics (one speaker → many listeners, dispatch monitoring 10-50 channels) require selective forwarding without mixing. MCU adds 100-150ms latency for unnecessary mixing; P2P mesh scales O(n²) and fails beyond 4 users.

### Expected Features

Research into enterprise PTT systems (Motorola WAVE PTX, Zello Enterprise) and MCPTT standards reveals clear table stakes vs differentiators.

**Must have (table stakes):**
- Press-to-talk audio transmission with floor control—core functionality currently broken
- Low latency audio (100-300ms mouth-to-ear)—3GPP/ETSI define 200ms as critical threshold
- Opus codec (16-32kbps) with noise suppression and echo cancellation—industrial environments demand this
- Multi-channel monitoring with selective mute—dispatch users monitor 10-50 channels simultaneously
- Jitter buffer management—critical for quality in variable networks
- Role-based access control (Admin/Dispatch/General)—already implemented
- Presence status (Available/Busy/Offline)—prevents failed calls
- Architecture supports future recording and AES-256 encryption—design for it now even if not implemented in v1

**Should have (competitive):**
- GPS location tracking and mapping—situational awareness for distributed teams at large venues
- Emergency alert button with priority calling—life-safety feature for high-stakes events
- Text messaging and photo sharing—async communication when voice is inappropriate
- Playback/replay (7-day message history)—Zello provides this; valuable for shift changes
- Usage analytics and reporting—post-event analysis and staffing optimization
- Broadcast/all-call to 500-3000 users—mass announcements for evacuations/security alerts

**Defer (v2+):**
- Native mobile apps (Android/iOS)—web-first strategy, validate before platform investment
- Video clip sharing—bandwidth-intensive, assess demand after launch
- Message transcription (voice-to-text)—AI feature, nice-to-have for accessibility
- LMR radio interoperability—niche requirement, complex integration
- End-to-end encryption implementation—architecture ready, defer actual implementation

**Anti-features identified:** End-to-end encryption in v1 (breaks recording/compliance), native apps in v1 (delays market), full video conferencing (scope creep, bandwidth explosion), custom codec implementation (maintenance burden).

### Architecture Approach

The standard WebRTC PTT architecture employs clean separation of concerns: **signaling plane** (WebSocket for SDP/ICE exchange), **media plane** (SFU for audio routing), and **control plane** (REST API + Redis for state). This maps perfectly to VoicePing's existing infrastructure.

**Major components:**
1. **WebSocket Server (existing)** — Handles WebRTC signaling transport, PTT floor control, session management; add WebRTC message handlers to existing implementation
2. **SFU Media Server (new - mediasoup)** — Audio stream routing via selective forwarding; runs as separate process/container with C++ worker threads
3. **Redis (existing)** — Room membership, routing tables, pub/sub for state sync; SFU subscribes to `vp:membership_updates` channel
4. **Control Plane API (existing)** — Event/team/channel CRUD unchanged; REST API continues managing users/authorization
5. **TURN/STUN Server (new)** — NAT traversal for 15-20% of connections (corporate firewalls); start with Twilio managed service for MVP

**Key integration pattern:** WebSocket becomes WebRTC signaling channel while continuing current control flow. Client authenticates via JWT, requests channel join, server creates mediasoup router/producer/consumer, sends WebRTC offer via WebSocket, client responds with answer, ICE candidates exchanged, then media flows directly via UDP (DTLS-SRTP), not through WebSocket. Existing floor control (START/STOP messages) integrates with WebRTC producer lifecycle.

**Data flow for dispatch multi-channel monitoring:** Single WebRTC connection to SFU with multiple paused consumers (one per subscribed channel). User selectively resumes/pauses consumers for mute/unmute without server-side mixing—this is the critical insight enabling 10-50 channel monitoring without bandwidth explosion.

### Critical Pitfalls

Research identifies 8 critical pitfalls with phase-specific prevention strategies. Top 5 by impact:

1. **Opus codec misconfiguration for PTT workloads** — Default WebRTC settings optimize for conversational audio (20ms frames, adaptive bitrate), but PTT needs lower latency. Configure 2.5-10ms frames, CBR at 32-64kbps, VOIP mode not AUDIO mode, disable DTX (causes transmission start delays), enable FEC for packet loss. Current "invalid packet" errors indicate raw Opus without proper RTP encapsulation. **Address in Phase 1—foundational.**

2. **SFU selection without PTT-specific requirements** — Most WebRTC servers optimize for video conferencing. Single server limits: 200-500 users typical, 1000+ requires clustering. Wrong choice (MCU, mesh topology) makes scaling impossible. Use SFU for forwarding without transcoding, plan multi-server architecture from day one, test with PTT patterns (1 speaker → N listeners). **Address in Phase 2—changing media servers mid-project requires rewrite.**

3. **Memory leaks in long-running sessions** — WebRTC connections and MediaRecorder instances accumulate over 8-12 hour events, causing browser crashes. Explicitly close RTCPeerConnection, stop MediaStream tracks, avoid MediaRecorder timeslice, use WeakMap for references, test with realistic 8-12 hour durations. Dispatch users monitoring 50 channels are most affected. **Address in Phase 3—during dispatch multi-channel implementation.**

4. **TURN server cost explosion at scale** — 15-20% of connections typically require TURN relay (corporate NATs), but dispatch multi-channel can push this to 50%+. At 1000 users with 15% TURN: ~2.71TB per session. Use SFU to minimize connection count (1 to SFU vs N peer connections), configure ICE to prioritize STUN-first, deploy regional TURN servers. **Address in Phase 2—architecture must minimize TURN dependency.**

5. **Jitter buffer misconfiguration for PTT latency** — Default adaptive jitter buffer (40-120ms) creates unacceptable latency for PTT. Configure `setJitterBufferMinDelay(15)` for 15ms minimum, reduce max packets to 25 from default 50, rely on Opus FEC instead of large buffers. Total PTT latency target: 100-300ms includes jitter buffer. **Address in Phase 1—impacts latency validation.**

**Other critical pitfalls:** Browser compatibility (Safari requires adapter.js, stricter HTTPS enforcement)—Phase 1; state synchronization complexity at 1000+ users (Redis distributed state with eventual consistency)—Phase 2; audio feedback loops in dispatch monitoring (require headphones, detect feedback patterns)—Phase 3.

## Implications for Roadmap

Based on research findings, the natural phase structure follows technical dependencies and risk mitigation priorities. The broken audio subsystem must be proven in isolation (Phase 1) before scaling architecture (Phase 2), and architecture must be solid before adding complex features like dispatch multi-channel (Phase 3).

### Phase 1: Proof of Concept (WebRTC Audio Foundation)
**Rationale:** Validate core technology decisions before investing in architecture. Current Opus-over-WebSocket is fundamentally broken and unfixable—WebRTC with mediasoup is the only viable path. Must prove latency targets achievable and cross-browser compatibility before building on this foundation.

**Delivers:** Working WebRTC audio in single channel between 2 users, latency measurement <300ms, cross-browser validation (Chrome, Firefox, Safari desktop + iOS).

**Addresses:**
- Press-to-talk audio transmission (currently broken)
- Opus codec with proper RTP encapsulation
- Jitter buffer configuration for PTT latency
- Noise suppression and echo cancellation

**Avoids:**
- Pitfall 1: Opus codec misconfiguration—configure frame size 2.5-10ms, CBR mode, disable DTX
- Pitfall 5: Jitter buffer latency—configure minimum delay 15ms, test under network jitter
- Pitfall 3 (partial): Browser compatibility—test Safari early with adapter.js

**Features:** Core PTT audio only. No multi-channel, no dispatch monitoring, no advanced features. Simple 2-person channel proves technology.

### Phase 2: Architecture & Scaling Foundation
**Rationale:** Single-channel PoC from Phase 1 cannot scale to 1000 users. Must implement distributed architecture, multi-channel support, and state synchronization before adding complex features. Architecture changes are expensive to retrofit—get it right before feature development.

**Delivers:** Multi-channel support, distributed state management via Redis, SFU clustering architecture (tested to 500+ simulated users), TURN/STUN infrastructure.

**Uses:**
- mediasoup SFU with worker pool (one per CPU core)
- Redis pub/sub for state synchronization across servers
- TURN server (Twilio managed service for MVP)

**Implements:**
- SFU selective subscription pattern (foundation for dispatch monitoring)
- State replication (Redis → SFU) with eventual consistency
- Floor control via signaling integrated with WebRTC producer lifecycle
- Authorization enforcement at media level (SFU validates membership)

**Avoids:**
- Pitfall 2: Wrong SFU architecture—validate mediasoup handles 500+ users, plan horizontal scaling
- Pitfall 4: TURN cost explosion—SFU minimizes connections, configure ICE for STUN-first
- Pitfall 7: State synchronization complexity—Redis distributed state with atomic operations, test race conditions

**Features:** Users can join multiple channels, PTT in any channel, floor control enforced. Dispatch monitoring architecture in place but not full UI (Phase 3).

### Phase 3: Dispatch Multi-Channel & Production Hardening
**Rationale:** Architecture from Phase 2 enables selective subscription for dispatch monitoring. Now implement full dispatch features (10-50 channel monitoring with selective mute) and harden for production 8-12 hour sessions.

**Delivers:** Dispatch console with 10-50 channel selective monitoring, memory leak testing/fixes, production monitoring, graceful reconnection.

**Addresses:**
- Multi-channel monitoring with selective mute/unmute
- Channel scanning with priority
- Production-grade connection lifecycle management
- Memory profiling and leak prevention

**Avoids:**
- Pitfall 6: Memory leaks in long sessions—explicit cleanup, 8-12 hour session testing
- Pitfall 8: Audio feedback in dispatch mode—headphone enforcement, feedback detection

**Features:** Full dispatch UI, connection quality indicators, presence status, graceful error recovery, production monitoring dashboard.

### Phase 4: Enhanced Communication & Location (Post-MVP)
**Rationale:** Core PTT is production-ready after Phase 3. Now add competitive differentiators that require solid foundation: GPS tracking, emergency alerts, text/photo sharing.

**Delivers:** GPS location tracking and mapping, emergency alert button with priority calling, text messaging, photo sharing, usage analytics.

**Features:** Location-based coordination, emergency/priority features, multimedia communication, post-event analytics.

### Phase 5: Recording, Compliance & Advanced Features (v2)
**Rationale:** Architecture supports recording from Phase 1 design decisions. Now implement actual recording pipeline, playback/replay, audit logs, broadcast/all-call.

**Delivers:** Call recording with retention policies, 7-day replay, audit logs, broadcast to 500-3000 users, message transcription (AI).

**Features:** Compliance features, historical playback, mass announcements, voice-to-text.

### Phase Ordering Rationale

**Why PoC before Architecture:** Cannot validate technology choice (WebRTC + mediasoup) without working prototype. PoC proves Opus latency achievable, Safari works, WebRTC integration viable. Discovering fundamental issues in Phase 2 wastes architecture work.

**Why Architecture before Features:** Dispatch multi-channel monitoring (Phase 3) depends on selective subscription pattern (Phase 2). Adding channels to PoC architecture causes redesign—1 connection per channel instead of 1 connection with N subscriptions. Recording (Phase 5) depends on SFU architecture decisions (Phase 2).

**Why defer GPS/Emergency to Phase 4:** GPS tracking is independent of audio subsystem rebuild. Adding in Phase 1-3 distracts from core audio risk. Emergency alerts need priority calling (complex audio preemption), which needs stable multi-channel foundation (Phase 2-3).

**Why defer Recording to Phase 5:** Architecture supports recording (mediasoup RTP stream access), but implementation is complex (ffmpeg pipeline, storage, retention policies). Not needed for MVP validation. Can be added cleanly after audio/architecture proven.

### Research Flags

**Phases needing deeper research during planning:**
- **Phase 2:** mediasoup clustering architecture for 1000+ users across multiple servers—need to research specific deployment topology, load balancing strategies, and Redis cluster configuration
- **Phase 4:** GPS location tracking APIs and mapping libraries—need to research browser geolocation APIs, map rendering performance with 1000+ markers
- **Phase 5:** ffmpeg recording pipeline and storage architecture—need to research RTP → ffmpeg integration, storage formats, retention automation

**Phases with standard patterns (skip research-phase):**
- **Phase 1:** WebRTC basics and mediasoup setup—well-documented in official docs, demo projects available
- **Phase 3:** Dispatch UI patterns—established UX patterns from competitor analysis (Motorola WAVE, Zello)
- **Phase 4:** Text/photo messaging—standard web features, no domain-specific complexity

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | mediasoup is proven for Node.js WebRTC at scale (19,744 weekly downloads, production deployments documented); Opus codec is mandatory WebRTC standard (RFC 7874); integration strategy verified by multiple sources |
| Features | MEDIUM | Table stakes identified from MCPTT standards and competitor analysis (Motorola WAVE PTX, Zello), but VoicePing-specific priorities inferred from existing codebase and user roles |
| Architecture | HIGH | SFU pattern for PTT is industry consensus; signaling-media separation is WebRTC standard; selective subscription pattern documented in mediasoup/LiveKit official docs; integration with existing infrastructure is straightforward |
| Pitfalls | HIGH | Opus misconfiguration, memory leaks, TURN costs, jitter buffer issues are well-documented with clear solutions; pitfall-to-phase mapping is logical based on dependencies |

**Overall confidence:** HIGH

### Gaps to Address

**Node.js upgrade impact:** Upgrading from Node 8.16.0 to Node 20 LTS is significant—must validate all existing dependencies for compatibility. Redis client upgrade from 2.8.0 to 4.7.0 changes callback pattern to async/await (major refactor). This should be a dedicated task in Phase 1 or pre-phase dependency upgrade.

**TURN server cost validation:** Research provides estimates (2.71TB per 1000-user session at 15% TURN usage), but actual costs depend on dispatch user behavior (10-50 channels monitored). Should instrument TURN usage percentage in Phase 2 testing to validate cost projections before production.

**Safari iOS-specific quirks:** While Safari compatibility is flagged for Phase 1 testing, iOS Safari has additional constraints (memory limits, background tab suspension, permissions model). Needs specific mobile testing beyond desktop Safari—potentially add mobile testing task to Phase 1.

**Multi-server state consistency:** Redis pub/sub for state synchronization introduces eventual consistency challenges. Research mentions this but doesn't provide detailed conflict resolution strategies. Phase 2 should include specific research task for distributed state patterns (CRDTs, last-write-wins, etc.).

**Recording storage costs:** Phase 5 recording implementation will generate significant storage: 1000 users × 5% active × 32kbps × 8 hours = ~55GB per event. Storage strategy (S3, retention policies, compression) needs cost modeling before Phase 5.

## Sources

### Primary (HIGH confidence)
- [mediasoup official documentation](https://mediasoup.org/documentation/v3/) — Architecture patterns, API reference, production deployment guidance
- [mediasoup npm package](https://www.npmjs.com/package/mediasoup) — Version compatibility, download statistics (19,744 weekly), changelog
- [Opus Recommended Settings - XiphWiki](https://wiki.xiph.org/Opus_Recommended_Settings) — Codec configuration for VoIP/PTT use cases
- [RFC 7874 - WebRTC Audio Codec Requirements](https://datatracker.ietf.org/doc/html/rfc7874) — Opus mandate for WebRTC
- [RFC 8827 - WebRTC Security Architecture](https://datatracker.ietf.org/doc/html/rfc8827) — DTLS-SRTP encryption requirements

### Secondary (MEDIUM confidence)
- [P2P, SFU, MCU, Hybrid: Which WebRTC Architecture Fits Your 2026 Roadmap?](https://www.forasoft.com/blog/article/webrtc-architecture-guide-for-business-2026) — Architecture comparison and scaling analysis (January 2026 publication)
- [WebRTC Tech Stack Guide: Architecture for Scalable Real-Time Applications](https://webrtc.ventures/2026/01/webrtc-tech-stack-guide-architecture-for-scalable-real-time-applications/) — Scalability patterns and infrastructure recommendations
- [WebRTC signaling with WebSocket and Node.js - LogRocket](https://blog.logrocket.com/webrtc-signaling-websocket-node-js/) — WebSocket integration patterns
- [Motorola WAVE PTX product documentation](https://www.motorolasolutions.com/en_us/products/command-center-software/broadband-ptt-and-lmr-interoperability/wave.html) — Competitive feature analysis, enterprise PTT standards
- [Zello Enterprise Server documentation](https://zello.com/enterprise/) — Competitive feature analysis, 7-day replay patterns
- [MCPTT Standards: Mouth-to-Ear Latency](https://teraquant.com/measuring-mouth-to-ear-latency-as-required/) — 3GPP/ETSI latency requirements (200ms threshold)

### Tertiary (LOW confidence - needs validation)
- Various blog posts on WebRTC scaling and TURN costs — Cost estimates and scalability numbers vary widely; should validate in Phase 2 testing
- Community forum discussions on memory leaks — Anecdotal reports; should validate with profiling in Phase 3

---
*Research completed: 2026-02-06*
*Ready for roadmap: yes*
