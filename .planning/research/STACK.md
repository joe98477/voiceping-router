# Technology Stack Research

**Domain:** WebRTC-based Push-to-Talk Communications Platform
**Researched:** 2026-02-06
**Overall Confidence:** MEDIUM-HIGH

## Executive Summary

For adding WebRTC PTT audio to your existing Node.js/WebSocket platform, the recommended stack centers around **mediasoup 3.x** as the WebRTC SFU (Selective Forwarding Unit), which integrates cleanly with your current architecture while providing production-grade performance for 1000+ concurrent users. The existing WebSocket infrastructure becomes the signaling layer, Redis continues handling distributed state, and Opus codec (already attempted in your broken implementation) remains the correct choice but delivered via proper WebRTC transport instead of raw packets.

**Key Decision:** Use mediasoup v3 SFU architecture, NOT mesh topology or MCU. Server-side media routing is essential for PTT scalability.

---

## Recommended Stack

### Core WebRTC Media Server

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **mediasoup** | 3.19.16+ | WebRTC SFU (Selective Forwarding Unit) for server-side media routing | Industry standard for Node.js WebRTC with proven 1000+ user scalability. Native Node.js integration, low-latency architecture (120ms avg), and active maintenance. Handles media routing without re-encoding (unlike MCU). |
| **mediasoup-client** | 3.16.0+ | Browser-side WebRTC client library | Official client library for mediasoup. Supports Chrome 111+, Firefox 120+, Safari 12+, meeting your browser-first requirement. Handles device detection and compatibility automatically. |

**Confidence:** HIGH - mediasoup is the de facto standard for Node.js WebRTC at scale, with 19,744 weekly npm downloads and widespread production use.

### Audio Codec Stack

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Opus** | (built into WebRTC) | Primary audio codec for PTT | Mandatory WebRTC codec (RFC 7874). Variable bitrate 6-510 kbps, algorithmic delay 5-26.5ms configurable, adaptive quality for poor networks. Superior to G.711 for bandwidth efficiency while maintaining PTT-grade latency. |

**Configuration for PTT:**
- Frame size: 10-20ms (balance latency vs packet overhead)
- Bitrate: 16-32 kbps for speech (PTT doesn't need music quality)
- FEC (Forward Error Correction): Enable for lossy networks
- DTX (Discontinuous Transmission): Enable to save bandwidth during silence

**Confidence:** HIGH - Opus is the industry standard for WebRTC audio, specifically designed for low-latency VoIP.

### Integration with Existing Stack

| Technology | Current Version | Target Version | Purpose | Integration Strategy |
|------------|-----------------|----------------|---------|----------------------|
| **Node.js** | 8.16.0 (2018) | **18.x LTS or 20.x LTS** | Runtime environment | **UPGRADE REQUIRED** - Node 8 EOL'd in 2019. Mediasoup requires Node 16+. Recommend Node 20 LTS for production. |
| **TypeScript** | 3.5.1 | **5.x** | Type safety | Upgrade alongside Node.js. Mediasoup has excellent TypeScript definitions. |
| **WebSocket (ws)** | 5.2.0 | **8.x** | Signaling channel (reuse existing) | Keep existing WebSocket infrastructure for signaling. WebRTC doesn't mandate signaling protocol - your current ws layer handles SDP/ICE exchange. |
| **Redis** | 2.8.0 / 4.7.0 (control-plane) | **4.7.0+** | Distributed state management | Continue using Redis for room/user state. Add mediasoup worker/router state for multi-server deployments. |
| **Express** | 4.18.2 | **4.x** (keep current) | REST API for control plane | No change needed. Control plane remains on Express. |

**Confidence:** HIGH - This integration strategy preserves your working infrastructure while adding WebRTC capabilities.

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **@types/mediasoup** | Latest | TypeScript definitions for mediasoup | Always (using TypeScript) |
| **@types/mediasoup-client** | Latest | TypeScript definitions for client library | Always (web UI in TypeScript/React) |
| **eventemitter3** | 5.x | Event handling (if not using Node.js EventEmitter) | Optional - mediasoup uses events heavily |
| **uuid** | 9.x | Generate unique IDs for peers/rooms | Always (for tracking WebRTC participants) |
| **debug** | Already installed | Logging for mediasoup (uses debug internally) | Already in your stack |

### Recording & Storage (Architecture Support)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **ffmpeg** | 6.x (binary) | Audio recording/transcoding | Phase 2+ when implementing recording. Mediasoup can pipe RTP to ffmpeg for recording. |
| **fluent-ffmpeg** | 2.x | Node.js wrapper for ffmpeg | Phase 2+ for programmatic recording control |
| **@aws-sdk/client-s3** or equivalent | 3.x | Store recorded audio files | Phase 2+ for cloud storage of recordings |

**Note:** Your requirement states "architecture supports future recording" - mediasoup's RTP stream access makes recording straightforward when needed. Don't implement in v1.

**Confidence:** MEDIUM - Recording architecture is well-understood, but implementation details depend on future requirements.

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **mediasoup-demo** | Reference implementation | Clone from GitHub to study production patterns. Shows signaling, router management, multi-worker setup. |
| **Chrome DevTools** | WebRTC debugging | chrome://webrtc-internals for diagnosing connection issues |
| **Wireshark** | Network packet analysis | Verify DTLS-SRTP encryption, diagnose packet loss |
| **Docker** | Deployment (already in use) | Mediasoup runs in Docker but requires UDP port range configuration |

---

## Installation

```bash
# Upgrade Node.js first (outside npm)
# Use nvm or download Node 20 LTS from nodejs.org

# Core dependencies (in main voiceping-router package.json)
npm install mediasoup@^3.19.16
npm install uuid@^9.0.0

# TypeScript definitions
npm install -D @types/mediasoup

# Client-side (in web-ui package.json)
npm install mediasoup-client@^3.16.0
npm install -D @types/mediasoup-client

# Upgrade existing dependencies
npm install ws@^8.17.0
npm install redis@^4.7.0
npm install typescript@^5.0.0

# Update devDependencies
npm install -D @types/node@^20.0.0
npm install -D @types/ws@^8.5.0
```

---

## Architecture Decision: SFU vs MCU vs Mesh

### Why SFU (Recommended)

**For PTT with 1000+ users, SFU is the ONLY viable architecture.**

| Criterion | SFU | MCU | Mesh |
|-----------|-----|-----|------|
| **Scalability** | ✅ 500-1000 per server, 10K+ in cluster | ❌ ~300 max per server | ❌ 2-4 users max |
| **Latency** | ✅ ~120ms average | ❌ ~280ms (re-encoding overhead) | ✅ ~50ms (but unusable at scale) |
| **Server CPU** | ✅ Low (forwarding only, no transcoding) | ❌ Very high (re-encodes all streams) | ✅ Minimal (no server routing) |
| **Client Bandwidth** | ✅ One upload stream | ✅ One upload stream | ❌ N-1 upload streams (kills mobile) |
| **Selective Listening** | ✅ Server drops streams for muted channels | ❌ Server must mix all audio | ❌ Client receives everything |
| **PTT Suitability** | ✅ Perfect - one speaker, many listeners | ❌ Overkill for PTT | ❌ Impossible at scale |

**SFU Architecture for PTT:**
- When user presses PTT, their audio goes to mediasoup router
- Router forwards to all participants in that channel
- Dispatch users subscribed to multiple channels receive multiple streams
- Muted channels = server stops forwarding those streams (bandwidth savings)
- No audio mixing, no transcoding = low latency + low CPU

**Confidence:** HIGH - This is industry consensus for 100+ user real-time audio.

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not Alternative |
|----------|-------------|-------------|---------------------|
| **Media Server** | mediasoup | **Janus Gateway** | Janus is C-based with plugin architecture. Requires more expertise, harder to integrate with Node.js. Good choice IF you need SIP/RTSP/other protocols, but PTT doesn't. |
| **Media Server** | mediasoup | **Kurento** | Inactive since Twilio acquisition. Avoid. |
| **Media Server** | mediasoup | **Jitsi** | Full conferencing app, not a library. Overkill for PTT. Use if you want turnkey solution, not custom integration. |
| **Node.js WebRTC Bindings** | mediasoup (pure Node API) | **node-webrtc** | Inactive/deprecated. Last update 2023. Alternative @roamhq/wrtc exists but immature. |
| **Node.js WebRTC Library** | mediasoup | **werift** | Pure TypeScript WebRTC implementation (0.22.2). Immature (29 dependents vs mediasoup's thousands). Consider for experimental projects, not production. |
| **Client Library** | mediasoup-client | **simple-peer / PeerJS** | Designed for P2P mesh, not SFU architecture. Good for 2-4 users, unusable for PTT at scale. |
| **Audio Codec** | Opus | **G.711** | Fixed 64kbps (vs Opus 6-510kbps adaptive). No FEC, no DTX. Use G.711 only for legacy telecom interop. |
| **Topology** | SFU | **Mesh (P2P)** | Bandwidth grows O(n²). With 10 users, speaker uploads 1.5Mbps × 9 = 13.5Mbps. Mobile networks can't handle this. Only viable for 2-4 users. |
| **Topology** | SFU | **MCU** | Server re-encodes/mixes all streams. High CPU cost, adds 100-150ms latency. Use only if clients are extremely low-power devices OR you need server-side layout composition. PTT doesn't need mixing. |

### When to Choose Alternatives

**Choose Janus if:**
- You need SIP integration (connecting to legacy phone systems)
- You want maximum flexibility with plugins
- You have C/C++ expertise on team

**Choose MCU if:**
- Target devices are extremely underpowered (can't decode multiple streams)
- You need server-side audio mixing (not typical for PTT)
- Latency is less important than client CPU savings

**Choose mesh if:**
- Literally only 2-4 concurrent users ever
- Zero server cost is critical
- You can tolerate complete failure as group grows

**Confidence:** HIGH - These alternatives are well-documented, the decision criteria are clear.

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **Raw Opus packets over WebSocket** | Your current broken implementation. Browser-native Opus encoding produces packets incompatible with direct WebSocket transport. Requires proper RTP/SRTP framing, which WebRTC provides. | WebRTC with mediasoup (Opus delivered via SRTP) |
| **node-webrtc** | Inactive project. Last real update 2020. Node version compatibility issues. | mediasoup (doesn't need native WebRTC bindings - it's a standalone SFU) |
| **Kurento** | Abandoned after Twilio acquisition. No meaningful updates since 2020. | mediasoup or Janus |
| **Node.js 8.x** | End-of-life since 2019. Security vulnerabilities, incompatible with modern libraries. | Node.js 20 LTS (current as of 2026) |
| **TypeScript 3.x** | Missing modern features, poor error messages. | TypeScript 5.x |
| **Mesh topology for >4 users** | Exponential bandwidth growth. 10 users = each speaker uploads to 9 peers. Mobile networks fail. | SFU (mediasoup) |
| **Custom WebRTC signaling (rolling your own SDP parser)** | Complex, error-prone. WebRTC SDP is 200+ line text format with strict semantics. | Use existing WebSocket for transport, but let mediasoup-client handle SDP generation/parsing |
| **Unencrypted RTP** | WebRTC mandates DTLS-SRTP. Browsers refuse unencrypted connections. | DTLS-SRTP (automatic in WebRTC/mediasoup) |
| **DTLS 1.0/1.1** | Deprecated. Modern browsers phasing out. | DTLS 1.2+ (mediasoup default) |
| **Synchronous Redis operations** | Blocks event loop in Node.js. Your control-plane uses redis@4.7.0 (async/await), but router uses redis@2.8.0 (callback hell). | Upgrade router to redis@4.x with async/await |

**Critical Avoidance: Don't try to fix raw Opus WebSocket approach.** Your instinct to move to WebRTC is correct. The broken Opus implementation isn't salvageable - browsers encode Opus for RTP transport, not raw frames.

**Confidence:** HIGH - These are known pitfalls with clear documentation.

---

## Encryption & Security

| Component | Technology | Details | Confidence |
|-----------|-----------|---------|------------|
| **Media Encryption** | DTLS-SRTP (automatic) | All WebRTC media is encrypted by default. DTLS 1.2+ for key exchange, SRTP for media packets. Browsers enforce this - no way to disable. | HIGH |
| **Signaling Encryption** | WSS (WebSocket Secure) | Your existing JWT authentication continues. Upgrade ws connections to wss:// in production. | HIGH |
| **Future E2E Encryption** | Insertable Streams API + AES-GCM | For post-v1 E2E encryption where server can't decrypt. Use Insertable Streams (browser API) to encrypt frames before WebRTC, decrypt after. Not in v1 scope. | MEDIUM |
| **AES-256 Requirement** | DTLS-SRTP cipher suite | SRTP uses AES-128 or AES-256 depending on negotiated cipher. Mediasoup supports both. For compliance, configure to require AES-256 suites. | MEDIUM |

**Note:** Your requirement states "architecture supports AES-256 encryption." DTLS-SRTP provides this, but it's transport encryption (server can decrypt for recording). True E2E encryption requires Insertable Streams API - defer to v2.

**WebRTC Security Updates (2025-2026):**
- Migration to DTLS 1.3 (RFC 9147-bis) in progress
- Post-quantum DTLS hybrids in research phase
- Browsers tightening cipher suites (deprecating weak ciphers)

**Sources:**
- [WebRTC Security Guide: Encryption, SRTP & DTLS Explained](https://antmedia.io/webrtc-security/)
- [WebRTC Encryption and Security - 2026](https://www.mirrorfly.com/blog/webrtc-encryption-and-security/)

**Confidence:** HIGH for DTLS-SRTP, MEDIUM for future E2E requirements.

---

## Integration Strategy with Existing Infrastructure

### WebSocket Signaling (Keep & Reuse)

Your existing WebSocket infrastructure (`ws@5.2.0` → upgrade to `ws@8.x`) becomes the WebRTC signaling channel. WebRTC doesn't mandate a signaling protocol - you're free to use your current architecture.

**How it works:**
1. Client authenticates via existing JWT WebSocket connection
2. Client requests to join channel (existing flow)
3. Server creates mediasoup router/producer/consumer
4. Server sends WebRTC offer (SDP) to client via WebSocket
5. Client responds with answer via WebSocket
6. ICE candidates exchanged via WebSocket
7. **WebRTC media flows directly via UDP (separate from WebSocket)**

**Key insight:** WebSocket is only for signaling (SDP/ICE exchange). Audio flows via WebRTC (UDP + DTLS-SRTP), not through WebSocket.

**Confidence:** HIGH - This is the standard pattern. Your existing WebSocket handles control plane, WebRTC handles media plane.

### Redis State Management (Extend)

Your existing Redis setup continues for user/channel/event state. Add WebRTC-specific state:

**Current Redis usage (keep):**
- User sessions
- Channel membership
- Busy/idle state
- Event/team/channel hierarchy

**Add for WebRTC:**
- Mediasoup worker assignments (which server handles which room)
- Router IDs for each channel
- Producer/consumer mappings
- Active speaker tracking per channel

**Multi-server deployment:**
- Each server runs mediasoup workers (one per CPU core)
- Redis tracks which server owns which channel
- New users joining channel get routed to correct server
- For 1000+ users, distribute channels across servers

**Pattern:**
```
Redis key structure:
  channel:{channelId}:server → server IP
  channel:{channelId}:router → mediasoup router ID
  channel:{channelId}:producers → set of producer IDs
  user:{userId}:consumers → set of consumer IDs (for dispatch multi-channel)
```

**Confidence:** MEDIUM - Pattern is clear, but implementation details depend on your specific scaling strategy.

### Docker Deployment (Adjust)

Mediasoup requires UDP port range for WebRTC. Your existing Docker setup needs port mapping.

**Docker configuration:**
```dockerfile
# In Dockerfile / docker-compose.yml
EXPOSE 3000          # Existing HTTPS/WSS
EXPOSE 40000-40100/udp   # Mediasoup RTP ports (100 concurrent connections)
```

**Port calculation:** ~1 port per WebRTC transport. For 1000 users, allocate 1000-2000 UDP ports.

**Network mode:** Bridge mode works, but host mode simpler for port mapping. Configure announced IP for container networking.

**Confidence:** MEDIUM - Mediasoup Docker deployment is well-documented, but requires network planning.

---

## Version Compatibility Matrix

| Package | Version | Compatible With | Notes |
|---------|---------|-----------------|-------|
| Node.js | 20.x LTS | mediasoup 3.x, TypeScript 5.x | Required minimum: Node 16+ |
| mediasoup | 3.19.16 | Node.js 16-20 | C++ worker prebuilt since 3.12.0 |
| mediasoup-client | 3.16.0 | Chrome 111+, Firefox 120+, Safari 12+ | Auto-detects browser capabilities |
| TypeScript | 5.x | All modern packages | Upgrade from 3.5.1 required |
| ws | 8.x | Node.js 16+ | Upgrade from 5.2.0 for security |
| redis | 4.7.0 | Node.js 16+ | Control-plane already on 4.x, router needs upgrade from 2.8.0 |

**Breaking changes when upgrading:**
- Redis 2.x → 4.x: Callbacks → Promises/async-await (significant refactor)
- ws 5.x → 8.x: Minor API changes, mostly compatible
- TypeScript 3.x → 5.x: Stricter type checking (will surface existing type errors)

**Migration strategy:** Upgrade Node.js first, then dependencies incrementally. Test after each upgrade.

**Confidence:** HIGH - Version compatibility is well-documented by package maintainers.

---

## Scalability Architecture

### Single Server Capacity

Based on mediasoup benchmarks and production deployments:

| Metric | Single Server (8 cores, 16GB RAM) |
|--------|-----------------------------------|
| **Concurrent users** | 500-1000 (assuming not all talking simultaneously) |
| **Concurrent speakers** | 50-100 (active audio streams) |
| **Bandwidth** | ~1 Gbps for 500 concurrent 1080p video streams (audio-only requires ~10-20% of this) |
| **Memory** | ~2GB for 1000 audio streams (per-peer buffers) |
| **Latency** | 120ms average with 1000 HD streams |

**PTT-specific:** In PTT, typically <5% of users speak simultaneously. With 1000 users, expect 10-50 concurrent speakers. Single mediasoup server handles this easily.

**Sources:**
- [WebRTC SFU architecture 1000 concurrent users scaling](https://antmedia.io/webrtc-network-topology/)
- [P2P, SFU, MCU, Hybrid: Which WebRTC Architecture Fits Your 2026 Roadmap?](https://www.forasoft.com/blog/article/webrtc-architecture-guide-for-business-2026)

### Multi-Server Architecture (1000+ Users)

**Horizontal scaling pattern:**

```
                    Load Balancer (WebSocket/HTTPS)
                           |
        +------------------+------------------+
        |                  |                  |
    Server 1           Server 2           Server 3
    (channels 1-10)   (channels 11-20)   (channels 21-30)
        |                  |                  |
        +------------------+------------------+
                           |
                       Redis Cluster
                   (state coordination)
```

**Strategy:**
1. Partition channels across servers (not users - channels are the routing unit)
2. Redis tracks channel→server mapping
3. Users connect to server owning their channel
4. Dispatch users in multiple channels maintain connections to multiple servers
5. Each server runs mediasoup workers (one per CPU core)

**Cost scaling:** For 1000 users in 50 channels, 2-3 mediasoup servers sufficient.

**Confidence:** MEDIUM-HIGH - Architecture is proven, but your specific deployment needs validation.

---

## Recording Architecture (Future Support)

Your requirement: "Architecture supports future recording (not implemented in v1)."

Mediasoup provides RTP stream access for recording:

**Recording pattern:**
1. When recording starts, create plain RTP transport in mediasoup
2. Pipe audio to ffmpeg via RTP
3. ffmpeg transcodes to WAV/MP3/OGG for storage
4. Store files in S3/local storage
5. Track recording metadata in PostgreSQL

**Code sketch (not for v1):**
```typescript
// Create plain RTP transport for recording
const rtpTransport = await router.createPlainTransport({
  listenIp: '127.0.0.1',
  rtcpMux: false,
  comedia: true,
});

// Pipe producer to recording transport
await rtpTransport.consume({
  producerId: producer.id,
  rtpCapabilities: router.rtpCapabilities,
});

// ffmpeg receives RTP on rtpTransport.tuple.localPort
// ffmpeg -protocol_whitelist file,udp,rtp -i recording.sdp -c copy output.wav
```

**Complexity:** Medium. Well-documented in mediasoup examples.

**Confidence:** MEDIUM - Pattern is clear, but implementation details need phase-specific research.

---

## Opus Configuration for PTT

Opus is already in your broken implementation - you had the right codec, wrong transport.

**Recommended Opus settings for PTT:**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| **Bitrate** | 16-24 kbps | Speech quality sufficient for PTT, saves bandwidth |
| **Complexity** | 8-10 | Higher = better quality at same bitrate (CPU cost minimal on modern devices) |
| **Frame size** | 20ms | Standard for VoIP (balance between latency and packet overhead) |
| **FEC** | Enabled | Forward Error Correction recovers from ~5% packet loss without retransmission |
| **DTX** | Enabled | Discontinuous Transmission - silence not transmitted (saves bandwidth on idle channels) |
| **Sample rate** | 48 kHz | Opus native rate (internally resamples if needed) |
| **Channels** | 1 (mono) | PTT doesn't need stereo |
| **Application** | `voip` | Opus has three modes: voip, audio (music), restricted_lowdelay. Use voip. |

**Expected latency budget:**
- Opus encoding: 20ms (frame size)
- Network transmission: 50-100ms (depends on geography)
- Jitter buffer: 20-40ms (mediasoup default)
- Decoding: 5ms
- **Total: 95-165ms** (well within 100-300ms target)

**Browser implementation:** Browsers handle Opus encoding/decoding automatically via WebRTC. You configure via SDP/mediasoup router parameters, not manual codec work.

**Sources:**
- [Opus Recommended Settings - XiphWiki](https://wiki.xiph.org/Opus_Recommended_Settings)
- [Best Audio Codec for Online Video Streaming in 2026](https://antmedia.io/best-audio-codec/)

**Confidence:** HIGH - Opus configuration for PTT is well-established.

---

## Migration Path from Current Stack

Your current broken implementation uses raw Opus over WebSocket. Here's the migration strategy:

### Phase 1: Parallel Implementation
1. Keep existing WebSocket infrastructure (signaling only)
2. Add mediasoup alongside current code
3. Implement WebRTC offer/answer via WebSocket messages
4. Test with small user group

### Phase 2: Cutover
1. Deploy mediasoup-client to web UI
2. Switch audio path from WebSocket binary frames to WebRTC
3. Deprecate old Opus packet handlers
4. Remove broken Opus code

### What to preserve:
- ✅ WebSocket connections (signaling)
- ✅ JWT authentication
- ✅ User/channel/event management
- ✅ Busy state logic
- ✅ Redis state storage
- ✅ PostgreSQL database
- ✅ REST API (control plane)

### What to replace:
- ❌ Raw Opus packet encoding/decoding
- ❌ Binary WebSocket frames for audio
- ❌ Manual jitter buffer (mediasoup handles this)
- ❌ Packet loss recovery hacks

### What to add:
- ➕ mediasoup server (routers, transports, producers, consumers)
- ➕ mediasoup-client in React UI
- ➕ WebRTC signaling protocol (SDP/ICE via existing WebSocket)
- ➕ UDP port configuration in Docker

**Confidence:** HIGH - Migration path preserves working components, replaces only broken audio subsystem.

---

## Development Workflow

### Local Development Setup

```bash
# 1. Install dependencies
npm install

# 2. Start Redis (Docker or local)
docker run -p 6379:6379 redis:7-alpine

# 3. Start PostgreSQL (existing setup)
# (already configured)

# 4. Set environment variables
export MEDIASOUP_ANNOUNCED_IP=127.0.0.1  # Local IP for development
export MEDIASOUP_MIN_PORT=40000
export MEDIASOUP_MAX_PORT=40100

# 5. Run development server
npm run dev
```

### Testing WebRTC Locally

**Challenge:** WebRTC requires HTTPS (browsers block getUserMedia on http://)

**Solutions:**
1. **localhost exception:** Browsers allow WebRTC on `localhost` without HTTPS
2. **Self-signed cert:** Generate cert for local IP, accept browser warning
3. **ngrok/tunneling:** Expose local server with HTTPS (easiest for mobile testing)

**Recommended:** Use `localhost` for desktop testing, ngrok for mobile testing.

### Browser DevTools

- **chrome://webrtc-internals** - Shows active connections, ICE candidates, packet stats, codec info
- **Firefox:** about:webrtc - Similar to Chrome
- **Safari:** Develop → WebRTC → Show Devices - Less detailed than Chrome

**Confidence:** HIGH - Development workflow is standard for WebRTC projects.

---

## Production Deployment Checklist

Before deploying mediasoup to production:

- [ ] **Node.js 18+ LTS** installed (verify `node --version`)
- [ ] **Build tools** installed (gcc, g++, make, python3 for mediasoup compilation)
- [ ] **UDP ports** allocated and mapped in Docker/firewall (40000-41000 recommended for 1000 users)
- [ ] **Announced IP** configured (public IP for cloud, internal IP for on-premise)
- [ ] **DTLS certificates** (mediasoup auto-generates, but verify they're not expired)
- [ ] **WSS (WebSocket Secure)** enabled for signaling
- [ ] **Redis cluster** (if multi-server deployment) for state synchronization
- [ ] **Monitoring** for CPU/bandwidth/active connections (mediasoup exposes metrics)
- [ ] **Fallback** plan if WebRTC fails (ICE failure, firewall blocking UDP)
- [ ] **TURN server** (for clients behind restrictive NAT/firewalls) - mediasoup doesn't include TURN, deploy separately

**TURN server recommendation:** coturn (open source) or Twilio TURN as service

**Why TURN matters:** ~5-10% of users are behind NAT that blocks direct UDP. TURN relays traffic through server. Without TURN, these users can't connect.

**Confidence:** MEDIUM - Checklist is based on mediasoup documentation, but production specifics vary.

---

## Cost Estimation (Infrastructure)

Approximate costs for 1000 concurrent users:

| Component | Specification | Monthly Cost (AWS) |
|-----------|---------------|-------------------|
| **Mediasoup servers** | 2x c5.2xlarge (8 vCPU, 16GB) | ~$500 |
| **Redis** | ElastiCache r5.large | ~$150 |
| **PostgreSQL** | RDS db.t3.medium | ~$100 |
| **Load balancer** | ALB | ~$25 |
| **Bandwidth** | ~1TB/month (audio-only PTT) | ~$90 |
| **TURN server** | 1x c5.large (for 10% of users) | ~$70 |
| **Total** | | **~$935/month** |

**Notes:**
- Assumes 1000 users, 5% speaking simultaneously
- Audio-only (much cheaper than video)
- Single-region deployment
- On-premise deployment eliminates cloud costs but requires hardware

**Confidence:** LOW-MEDIUM - Rough estimate, actual costs depend on usage patterns.

---

## Sources

### High Confidence (Official Documentation & Context7)

- [mediasoup official documentation](https://mediasoup.org/documentation/v3/)
- [mediasoup npm package](https://www.npmjs.com/package/mediasoup) - Version 3.19.16, last published 1 day ago
- [mediasoup GitHub repository](https://github.com/versatica/mediasoup) - 6,906 stars, active development
- [Opus Recommended Settings - XiphWiki](https://wiki.xiph.org/Opus_Recommended_Settings)
- [RFC 7874 - WebRTC Audio Codec Requirements](https://datatracker.ietf.org/doc/html/rfc7874)
- [RFC 8827 - WebRTC Security Architecture](https://datatracker.ietf.org/doc/html/rfc8827)

### Medium Confidence (Verified Web Search - 2025/2026 Sources)

- [Janus vs. MediaSoup Comparison](https://dzone.com/articles/janus-vs-mediasoup-the-ultimate-guide-to-choosing)
- [P2P, SFU, MCU, Hybrid: Which WebRTC Architecture Fits Your 2026 Roadmap?](https://www.forasoft.com/blog/article/webrtc-architecture-guide-for-business-2026)
- [WebRTC Tech Stack Guide 2026](https://webrtc.ventures/2026/01/webrtc-tech-stack-guide-architecture-for-scalable-real-time-applications/)
- [Best Audio Codec for Online Video Streaming in 2026](https://antmedia.io/best-audio-codec/)
- [WebRTC Security Guide: Encryption, SRTP & DTLS Explained](https://antmedia.io/webrtc-security/)
- [WebRTC Encryption and Security - 2026](https://www.mirrorfly.com/blog/webrtc-encryption-and-security/)
- [Mesh vs SFU vs MCU: Choosing the Right WebRTC Network Topology](https://antmedia.io/webrtc-network-topology/)
- [What is WebRTC P2P mesh and why it can't scale?](https://bloggeek.me/webrtc-p2p-mesh/)
- [WebRTC signaling with WebSocket and Node.js](https://blog.logrocket.com/webrtc-signaling-websocket-node-js/)
- [Comparative Study of WebRTC Open Source SFUs for Video Conferencing](https://mediasoup.org/resources/CoSMo_ComparativeStudyOfWebrtcOpenSourceSfusForVideoConferencing.pdf)

### Lower Confidence (Background/Context)

- Various WebRTC tutorial sites and blog posts (cross-referenced for accuracy)
- npm package download statistics (as of search date)
- Community forum discussions (mediasoup Discourse)

---

## Research Gaps & Future Validation Needed

**Items requiring phase-specific research when implemented:**

1. **Recording implementation details** - Deferred to Phase 2+, architecture validated but not implemented
2. **Multi-region deployment** - If events are globally distributed, need to research geographic load balancing
3. **TURN server configuration** - Coturn setup and capacity planning for NAT traversal
4. **End-to-end encryption with Insertable Streams** - If E2E becomes requirement, needs deep research
5. **Mobile native apps** - Out of scope for v1, but mediasoup supports native clients via C++ library
6. **Stress testing at 1000+ users** - Architecture supports it, but needs validation with actual load tests

**Confidence in recommendations:** MEDIUM-HIGH overall. Core stack (mediasoup + Opus + SFU) is HIGH confidence. Integration details and scaling specifics are MEDIUM confidence pending implementation.

---

**Research completed:** 2026-02-06
**Researcher:** GSD Project Research Agent
**Ready for roadmap creation:** Yes - stack recommendations are actionable for requirements definition and phase planning.
