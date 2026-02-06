# Architecture Research: WebRTC PTT Integration

**Domain:** WebRTC-based Push-to-Talk Communications Platform
**Researched:** 2026-02-06
**Confidence:** HIGH

## Executive Summary

WebRTC PTT systems typically employ a **Selective Forwarding Unit (SFU) architecture** that integrates with existing WebSocket infrastructure for signaling while adding a separate media server for audio routing. For the VoicePing use case (1000+ users, 10-50 channels, dispatch monitoring), the SFU model is the clear choice - it provides scalable audio routing without the CPU overhead of MCU mixing, while enabling selective subscription patterns critical for dispatch users monitoring multiple channels.

The architecture cleanly separates three concerns:
1. **Signaling plane** - WebSocket server handles WebRTC offer/answer/ICE exchange (existing infrastructure)
2. **Media plane** - SFU server routes audio streams between participants (new component)
3. **Control plane** - REST API + Redis manage event/team/channel state (existing infrastructure)

Integration with existing VoicePing infrastructure is straightforward: WebRTC signaling messages flow over the existing WebSocket connections, Redis continues to manage room membership and authorization, and the SFU subscribes to Redis state updates to make routing decisions.

## Standard WebRTC PTT Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │   Browser    │  │   Browser    │  │   Browser    │             │
│  │  (General)   │  │  (Dispatch)  │  │   (Admin)    │             │
│  │              │  │              │  │              │             │
│  │ WebSocket ───┼──┼─ WebSocket ──┼──┼─ WebSocket ──┤             │
│  │ WebRTC    ───┼──┼─ WebRTC   ───┼──┼─ WebRTC   ───┤             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└────────┬────────────────────┬─────────────────┬─────────────────────┘
         │                    │                 │
         │ Signaling          │ Media           │ API
         │ (WSS)              │ (WebRTC)        │ (HTTPS)
         ↓                    ↓                 ↓
┌────────────────────────────────────────────────────────────────────┐
│                        SIGNALING PLANE                             │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │            WebSocket Server (existing)                       │  │
│  │  - SDP offer/answer exchange                                 │  │
│  │  - ICE candidate exchange                                    │  │
│  │  - PTT floor control messages                                │  │
│  │  - Channel join/leave signaling                              │  │
│  └─────────────────────────┬────────────────────────────────────┘  │
└────────────────────────────┼───────────────────────────────────────┘
                             │
                             │ State sync
                             ↓
┌────────────────────────────────────────────────────────────────────┐
│                         MEDIA PLANE                                │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              SFU Media Server (new)                          │  │
│  │  ┌───────────┐  ┌───────────┐  ┌──────────────┐            │  │
│  │  │  Router   │  │ Transport │  │   Worker     │            │  │
│  │  │  Manager  │  │  Manager  │  │   Thread     │            │  │
│  │  └─────┬─────┘  └─────┬─────┘  └──────┬───────┘            │  │
│  │        │              │                │                     │  │
│  │        │   Selective Forwarding (no transcode)               │  │
│  │        └──────────────┴────────────────┘                     │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
└────────────────────────────┼───────────────────────────────────────┘
                             │
                             │ State queries
                             ↓
┌────────────────────────────────────────────────────────────────────┐
│                       CONTROL PLANE                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │   REST API   │  │    Redis     │  │  PostgreSQL  │            │
│  │  (Express)   │  │  (State)     │  │  (Durable)   │            │
│  └──────────────┘  └──────────────┘  └──────────────┘            │
└────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **WebSocket Server** | WebRTC signaling transport, PTT floor control, session management | Node.js `ws` library (existing) |
| **SFU Media Server** | Audio stream routing, selective forwarding, bandwidth adaptation | mediasoup, LiveKit, or Janus |
| **TURN/STUN Server** | NAT traversal, ICE candidate discovery, media relay fallback | coturn, Twilio STUN/TURN |
| **Redis** | Room membership, routing tables, pub/sub for state sync | Redis 7+ (existing) |
| **Control Plane API** | Event/team/channel CRUD, user management, JWT issuance | Express + Prisma (existing) |
| **PostgreSQL** | Durable user/event/channel data, audit logs | PostgreSQL + Prisma (existing) |

## Recommended Project Architecture

### Integration with Existing VoicePing Stack

The key architectural insight: **WebRTC augments rather than replaces the existing infrastructure.**

**What stays the same:**
- WebSocket server continues handling all signaling
- Redis continues managing room membership and state
- Express API continues managing users/events/channels
- PostgreSQL continues as source of truth for durable data
- JWT authentication flow unchanged

**What's new:**
- SFU media server added as separate process/container
- WebRTC signaling messages flow over existing WebSocket connections
- Media streams flow directly between clients and SFU (not through WebSocket)
- TURN/STUN servers added for NAT traversal

### Component Boundaries

```
Existing Infrastructure:
├── WebSocket Server (router)
│   ├── Handles: WebRTC signaling, PTT floor control
│   ├── Communicates with: Clients (WSS), Redis (state), SFU (HTTP API)
│   └── Integration: Add WebRTC message handlers, forward to SFU API
│
├── Redis
│   ├── Handles: Room membership, routing decisions, state sync
│   ├── Communicates with: WebSocket server, SFU, Control plane
│   └── Integration: Existing keys work, add SFU subscription
│
└── Control Plane API
    ├── Handles: User/event/channel CRUD, JWT tokens
    ├── Communicates with: Dispatch UI, PostgreSQL, Redis
    └── Integration: No changes needed

New Infrastructure:
├── SFU Media Server
│   ├── Handles: WebRTC audio routing, selective forwarding
│   ├── Communicates with: Clients (WebRTC), WebSocket server (HTTP), Redis (state)
│   └── Deployment: Separate Docker container, C++ worker threads
│
└── TURN/STUN Server
    ├── Handles: NAT traversal, ICE candidates, media relay
    ├── Communicates with: Clients (STUN/TURN protocols)
    └── Deployment: Separate container or cloud service (Twilio)
```

### Data Flow Patterns

#### 1. Channel Join Flow (WebRTC Session Establishment)

```
Client                WebSocket Server         SFU Server           Redis
  │                          │                      │                 │
  │ 1. JOIN channel          │                      │                 │
  ├─────────────────────────>│                      │                 │
  │                          │ 2. Check membership  │                 │
  │                          ├─────────────────────────────────────>  │
  │                          │                      │  3. Authorized  │
  │                          │<─────────────────────────────────────  │
  │                          │                      │                 │
  │                          │ 4. Create router     │                 │
  │                          ├─────────────────────>│                 │
  │                          │                      │                 │
  │                          │ 5. Router created    │                 │
  │ 6. ROUTER_RTP_CAPS       │<─────────────────────┤                 │
  │<─────────────────────────┤                      │                 │
  │                          │                      │                 │
  │ 7. Create send transport │                      │                 │
  ├─────────────────────────>├─────────────────────>│                 │
  │                          │                      │                 │
  │ 8. Transport params      │                      │                 │
  │<─────────────────────────┤<─────────────────────┤                 │
  │                          │                      │                 │
  │ 9. WebRTC offer (SDP)    │                      │                 │
  ├─────────────────────────>├─────────────────────>│                 │
  │                          │                      │                 │
  │ 10. WebRTC answer        │                      │                 │
  │<─────────────────────────┤<─────────────────────┤                 │
  │                          │                      │                 │
  │ 11. ICE candidates       │                      │                 │
  │<────────────────────────>│<────────────────────>│                 │
  │                          │                      │                 │
  │ 12. DTLS handshake       │                      │                 │
  │<═════════════════════════════════════════════════>                │
  │                    (media path established)                       │
```

#### 2. PTT Audio Transmission Flow

```
Speaker Client       WebSocket Server         SFU Server           Listener Clients
  │                          │                      │                      │
  │ 1. PTT button DOWN       │                      │                      │
  │ (START message)          │                      │                      │
  ├─────────────────────────>│                      │                      │
  │                          │ 2. Check busy state  │                      │
  │                          │ (Redis)              │                      │
  │                          │                      │                      │
  │                          │ 3. Grant floor       │                      │
  │ 4. START_SUCCESS         │                      │                      │
  │<─────────────────────────┤                      │                      │
  │                          │                      │                      │
  │                          │ 5. Broadcast START   │                      │
  │                          ├──────────────────────┼─────────────────────>│
  │                          │                      │                      │
  │ 6. Produce audio track   │                      │                      │
  ├─────────────────────────>├─────────────────────>│                      │
  │                          │                      │                      │
  │ 7. RTP audio packets     │                      │                      │
  ├══════════════════════════════════════════════════>                     │
  │                          │                      │ 8. Forward to        │
  │                          │                      │    subscribers       │
  │                          │                      ├═══════════════════════>
  │                          │                      │                      │
  │ 9. PTT button UP         │                      │                      │
  │ (STOP message)           │                      │                      │
  ├─────────────────────────>│                      │                      │
  │                          │ 10. Close producer   │                      │
  │                          ├─────────────────────>│                      │
  │                          │                      │                      │
  │                          │ 11. Broadcast STOP   │                      │
  │                          ├──────────────────────┼─────────────────────>│
  │                          │                      │                      │

Legend:
─────> Signaling (WebSocket)
═════> Media (WebRTC RTP)
```

#### 3. Dispatch Multi-Channel Monitoring Flow

```
Dispatch Client      WebSocket Server         SFU Server           Redis
  │                          │                      │                 │
  │ User assigned to         │                      │                 │
  │ channels: A, B, C        │                      │                 │
  │                          │                      │                 │
  │ 1. JOIN channels A,B,C   │                      │                 │
  ├─────────────────────────>│                      │                 │
  │                          │ 2. Verify membership │                 │
  │                          ├─────────────────────────────────────>  │
  │                          │                      │                 │
  │                          │ 3. Create consumers  │                 │
  │                          │    for A, B, C       │                 │
  │                          ├─────────────────────>│                 │
  │                          │                      │                 │
  │ 4. Subscribe to A,B,C    │                      │                 │
  │    (initially all muted) │                      │                 │
  │<─────────────────────────┤<─────────────────────┤                 │
  │                          │                      │                 │
  │ 5. UNMUTE channel A      │                      │                 │
  ├─────────────────────────>├─────────────────────>│                 │
  │                          │                      │                 │
  │                          │ 6. Resume consumer A │                 │
  │ 7. Audio from A          │                      │                 │
  │<═════════════════════════════════════════════════                 │
  │                          │                      │                 │
  │ 8. MUTE A, UNMUTE B      │                      │                 │
  ├─────────────────────────>├─────────────────────>│                 │
  │                          │                      │                 │
  │                          │ 9. Pause A, Resume B │                 │
  │ 10. Audio from B         │                      │                 │
  │<═════════════════════════════════════════════════                 │
  │                          │                      │                 │

Key concept: SFU creates consumer for each channel subscription,
but client controls which consumers are active (paused vs resumed).
This enables selective monitoring without server-side mixing.
```

#### 4. State Synchronization Flow

```
Control Plane API    Redis Pub/Sub        WebSocket Server      SFU Server
  │                          │                      │                 │
  │ Admin removes user       │                      │                 │
  │ from channel X           │                      │                 │
  │                          │                      │                 │
  │ 1. Update DB             │                      │                 │
  │                          │                      │                 │
  │ 2. Publish update        │                      │                 │
  ├─────────────────────────>│                      │                 │
  │                          │                      │                 │
  │                          │ 3. Notify subscribers│                 │
  │                          ├─────────────────────>│                 │
  │                          │                      │                 │
  │                          │                      │ 4. Notify SFU   │
  │                          ├──────────────────────┼────────────────>│
  │                          │                      │                 │
  │                          │                      │                 │
  │                          │ 5. Close user's      │                 │
  │                          │    channel X conn    │                 │
  │                          │<─────────────────────┤                 │
  │                          │                      │                 │
  │                          │                      │ 6. Close user's │
  │                          │                      │    consumers/   │
  │                          │                      │    producers    │
  │                          │                      │    for X        │
  │                          │<─────────────────────────────────────  │
```

### Key Integration Points

| Integration Point | Current Mechanism | WebRTC Enhancement |
|-------------------|-------------------|-------------------|
| **Client → Server signaling** | WebSocket binary (notepack) | Add WebRTC message types (SDP, ICE, etc.) |
| **Room membership** | Redis `u.{userId}.g` and `g.{channelId}.u` | SFU queries Redis for routing decisions |
| **Floor control** | START/STOP messages set Redis busy state | Same mechanism, but actual audio via WebRTC |
| **Authentication** | JWT in WebSocket handshake | Same JWT used for SFU API authorization |
| **State updates** | Redis pub/sub `vp:membership_updates` | SFU subscribes to same channel |
| **Multi-device** | Multiple WebSocket connections per user | Multiple WebRTC transports, one per device |

## Architectural Patterns

### Pattern 1: Signaling-Media Separation

**What:** Signaling (control messages) flows over WebSocket; media (audio) flows over WebRTC peer connections.

**When to use:** Always in WebRTC applications. WebSocket is reliable ordered delivery (good for control); RTP/SRTP is UDP-based real-time (good for audio).

**Trade-offs:**
- **Pro:** Media and signaling can scale independently
- **Pro:** Media can take optimal network path (NAT traversal)
- **Pro:** SFU can run on dedicated media-optimized servers
- **Con:** Two connection types to manage (WebSocket + WebRTC)
- **Con:** Firewall configuration more complex (need TURN fallback)

**Example:**
```typescript
// Signaling over WebSocket
wsConnection.send({
  type: 'WEBRTC_OFFER',
  channelId: 'channel123',
  sdp: offer.sdp
});

// Media over WebRTC
const producer = await sendTransport.produce({
  track: audioTrack,
  codecOptions: {
    opusStereo: false,
    opusDtx: true
  }
});
```

### Pattern 2: SFU Selective Subscription

**What:** Clients subscribe to specific audio producers, SFU forwards only subscribed streams.

**When to use:** Multi-channel scenarios where users don't need all audio (dispatch monitoring).

**Trade-offs:**
- **Pro:** Efficient bandwidth use - only receive needed audio
- **Pro:** Enables selective mute/unmute without server-side mixing
- **Pro:** Client controls what they hear (user agency)
- **Con:** More complex client-side state management
- **Con:** Subscription changes require signaling round-trip

**Example:**
```typescript
// Dispatch user subscribes to channels A, B, C
const consumers = await Promise.all([
  createConsumer(channelA, { paused: true }),  // Initially muted
  createConsumer(channelB, { paused: true }),
  createConsumer(channelC, { paused: true })
]);

// User unmutes channel A
await consumers[0].resume();  // Audio starts flowing

// User mutes A, unmutes B
await consumers[0].pause();
await consumers[1].resume();
```

### Pattern 3: State Replication (Redis → SFU)

**What:** SFU maintains local routing state synced from Redis via pub/sub.

**When to use:** When SFU needs to make routing decisions based on application state (channel membership, permissions).

**Trade-offs:**
- **Pro:** SFU can reject unauthorized streams at media level
- **Pro:** Faster routing decisions (local cache vs Redis query)
- **Pro:** Consistent with existing VoicePing architecture
- **Con:** State consistency challenges (eventual consistency)
- **Con:** SFU becomes stateful (complicates scaling)

**Example:**
```typescript
// SFU subscribes to Redis membership updates
redisClient.subscribe('vp:membership_updates', (message) => {
  const { userId, channelId, action } = JSON.parse(message);

  if (action === 'ADD') {
    // Allow user to produce/consume for this channel
    authorizedChannels.set(userId, channelId);
  } else if (action === 'REMOVE') {
    // Close user's producers/consumers for this channel
    closeUserTransportsForChannel(userId, channelId);
  }
});
```

### Pattern 4: Floor Control via Signaling

**What:** PTT floor control (who can talk) managed via WebSocket signaling, not media negotiation.

**When to use:** Always in PTT systems. WebRTC producer/consumer model is separate from business logic of "who gets the floor."

**Trade-offs:**
- **Pro:** Instant feedback (don't wait for media negotiation)
- **Pro:** Can deny floor before allocating media resources
- **Pro:** Integrates with existing VoicePing floor control
- **Con:** Client must coordinate signaling and media states

**Example:**
```typescript
// Client requests floor
wsConnection.send({ type: 'START', channelId });

// Server checks busy state, grants floor
if (!channelBusy) {
  wsConnection.send({ type: 'START_SUCCESS', channelId });

  // NOW client creates WebRTC producer
  const producer = await sendTransport.produce({ track });
} else {
  wsConnection.send({ type: 'START_FAILED', channelId });
  // No media resources allocated
}
```

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| **0-100 users** | Single SFU instance, shared TURN, minimal optimization |
| **100-1000 users** | Dedicated SFU server, managed TURN service (Twilio), Redis clustering |
| **1000-10000 users** | Multiple SFU instances (regional), Redis Sentinel, load balancer with sticky sessions |
| **10000+ users** | SFU mesh (peer-to-peer routing between SFUs), Redis Cluster, CDN for static assets |

### Scaling Priorities

**1. First bottleneck: SFU CPU (1000-2000 concurrent users)**

**Symptoms:**
- High CPU usage on SFU server (C++ worker threads maxed)
- Audio latency increases
- Packet loss rates increase

**How to fix:**
- **Horizontal scaling:** Add more SFU instances, route users by channel hash
- **Worker tuning:** Adjust mediasoup worker count to CPU cores
- **Codec optimization:** Enable Opus DTX (discontinuous transmission) to reduce silent packets

**Implementation:**
```typescript
// Hash-based routing to SFU instances
function getSfuInstanceForChannel(channelId: string): string {
  const hash = createHash('md5').update(channelId).digest('hex');
  const index = parseInt(hash.substring(0, 8), 16) % SFU_INSTANCES.length;
  return SFU_INSTANCES[index];
}
```

**2. Second bottleneck: Network bandwidth (aggregate ~2Mbps per 100 users)**

**Symptoms:**
- Clients report high bandwidth usage warnings
- ISPs throttle connections
- Mobile users drop connections frequently

**How to fix:**
- **Bandwidth estimation:** Enable client-side bandwidth probing, adjust bitrate
- **Simulcast:** Enable for future video, not needed for audio-only
- **Regional SFUs:** Deploy SFUs close to user concentrations (reduce latency and bandwidth)
- **TURN optimization:** Use TURN only as fallback (90% of connections should be direct or STUN)

**Implementation:**
```typescript
// Opus codec with bandwidth adaptation
const producerOptions = {
  codecOptions: {
    opusStereo: false,
    opusDtx: true,           // Reduce bandwidth during silence
    opusFec: true,           // Forward error correction
    opusMaxPlaybackRate: 16000  // Limit to 16kHz (narrowband sufficient for voice)
  }
};
```

**3. Third bottleneck: Redis pub/sub (state updates at scale)**

**Symptoms:**
- Membership update delays
- SFU state inconsistencies
- Redis CPU spikes during large events

**How to fix:**
- **Redis clustering:** Shard by event ID (events are isolated)
- **Local caching:** SFU maintains local state, queries Redis only on cache miss
- **Batch updates:** Coalesce rapid membership changes before publishing

**Implementation:**
```typescript
// Event-based Redis sharding
function getRedisClientForEvent(eventId: string): RedisClient {
  const shardIndex = eventId.hashCode() % REDIS_SHARDS.length;
  return REDIS_SHARDS[shardIndex];
}
```

## Anti-Patterns

### Anti-Pattern 1: Mixing Media and Signaling in Same Connection

**What people do:** Try to send RTP packets over WebSocket to simplify architecture.

**Why it's wrong:**
- WebSocket is TCP-based (reliable ordered delivery) - bad for real-time audio
- Head-of-line blocking causes audio stuttering when packets are lost
- Cannot leverage browser's native WebRTC optimizations (jitter buffer, bandwidth estimation)
- Much higher CPU usage for packing/unpacking

**Do this instead:** Use WebSocket for signaling only, WebRTC for media. Existing VoicePing Opus-over-WebSocket should be replaced with WebRTC audio.

**Evidence:** This is exactly the problem VoicePing currently has - "Opus packets fail to decode properly in browser-to-browser communication."

### Anti-Pattern 2: MCU for PTT (Server-Side Mixing)

**What people do:** Use MCU to mix all audio streams server-side, send single stream to each client.

**Why it's wrong:**
- PTT doesn't need mixing - only one speaker at a time per channel
- MCU CPU costs scale poorly (transcoding every stream)
- Dispatch users monitoring multiple channels don't need mixed audio - they need selective listening
- Adds latency (decode + mix + encode pipeline)

**Do this instead:** Use SFU for selective forwarding. Each speaker's audio forwarded as-is (no transcoding). Dispatch users subscribe to multiple producers, control which are active client-side.

### Anti-Pattern 3: P2P Mesh for Group Channels

**What people do:** Try to use peer-to-peer connections between all channel members.

**Why it's wrong:**
- Bandwidth scales O(n²) - each peer sends to all other peers
- Fails beyond ~4 participants (proven in research)
- Complex NAT traversal (every peer pair needs ICE negotiation)
- No server-side recording capability
- No selective subscription (dispatch monitoring impossible)

**Do this instead:** Always use SFU for PTT group channels. P2P only viable for private 1:1 calls, but even then SFU is simpler operationally.

### Anti-Pattern 4: Sharing WebRTC Transport Across Channels

**What people do:** Try to reuse single WebRTC transport for all channels user is in.

**Why it's wrong:**
- Complex producer/consumer lifecycle management
- Channel leave requires closing individual tracks, not transport
- Difficult to implement per-channel quality controls
- State machine becomes complex (which producers are active?)

**Do this instead:** Create separate transport per channel (or transport pair: send + receive). Clean lifecycle: join channel = create transport, leave = close transport. Simpler, more robust.

**Trade-off:** More transports = more connection overhead, but for PTT use case (10-50 channels, not all active), the simplicity wins.

### Anti-Pattern 5: SFU Without State Sync

**What people do:** SFU trusts client to only request authorized subscriptions.

**Why it's wrong:**
- Security vulnerability - client can subscribe to any channel
- No enforcement of VoicePing's event/team/channel permissions
- Authorization must be enforced at media level, not just signaling

**Do this instead:** SFU subscribes to Redis membership updates, validates all producer/consumer requests against current membership before allowing transport creation.

## Component Build Order & Dependencies

### Suggested Build Order

**Phase 1: Foundation (Signaling Integration)**
- Extend WebSocket server to handle WebRTC signaling messages
- Implement SDP offer/answer exchange over existing connections
- Implement ICE candidate exchange
- No media yet - just signaling infrastructure
- **Deliverable:** Clients can negotiate WebRTC, but no audio flows

**Phase 2: Single-Channel Media (SFU Integration)**
- Deploy SFU server (mediasoup recommended - Node.js, mature)
- Implement WebSocket → SFU HTTP API bridge
- Single channel producer/consumer creation
- Test with 2-person channel: one producer, one consumer
- **Deliverable:** Audio works for 1:1 channel communication

**Phase 3: Floor Control Integration**
- Integrate existing START/STOP messages with WebRTC producer lifecycle
- Implement busy state enforcement (deny producer creation when channel busy)
- Handle producer cleanup on STOP or disconnect
- **Deliverable:** PTT floor control works with WebRTC audio

**Phase 4: Multi-Channel Support**
- Multiple producers per SFU router (one per active channel)
- Multiple consumers per client (selective subscription)
- State management: track which transports belong to which channels
- **Deliverable:** Users can join and PTT in multiple channels

**Phase 5: Dispatch Selective Monitoring**
- Implement paused consumers (subscribed but muted)
- Client-side controls for resume/pause per channel
- UI for selective channel monitoring (already in dispatch UI)
- **Deliverable:** Dispatch users can monitor 10-50 channels selectively

**Phase 6: State Synchronization**
- SFU subscribes to Redis `vp:membership_updates`
- Enforce authorization at SFU level (validate membership before transport creation)
- Handle membership removals (close transports when user removed from channel)
- **Deliverable:** Authorization enforced at media level, state consistent

**Phase 7: TURN/STUN Infrastructure**
- Deploy TURN server (coturn or Twilio for simplicity)
- Configure ICE servers in client WebRTC configuration
- Test NAT traversal scenarios
- **Deliverable:** Works behind corporate firewalls and NAT

**Phase 8: Recording (Future)**
- SFU creates "recorder consumer" for each active producer
- Write Opus frames to disk (one file per transmission)
- Metadata: timestamp, userId, channelId
- **Deliverable:** Server-side recording of all PTT transmissions

### Dependency Graph

```
Phase 1: Signaling
    ↓
Phase 2: Single-Channel Media ← depends on Phase 1
    ↓
Phase 3: Floor Control ← depends on Phase 2
    ↓
Phase 4: Multi-Channel ← depends on Phase 3
    ↓
Phase 5: Dispatch Monitoring ← depends on Phase 4
    ↓
Phase 6: State Sync ← depends on Phase 5
    ↓
Phase 7: TURN/STUN ← can be parallel with Phase 6
    ↓
Phase 8: Recording ← depends on Phase 6 (authorization)
```

**Critical path:** Phases 1-6 are sequential dependencies. Phase 7 (TURN/STUN) can be developed in parallel after Phase 2, deployed when needed. Phase 8 (recording) is post-MVP.

## Technology Choices

### Recommended: mediasoup SFU

**Why mediasoup:**
- **Node.js native:** Integrates cleanly with existing VoicePing stack
- **Mature:** Production-grade, used by major platforms
- **Signaling agnostic:** Doesn't impose signaling protocol (we use existing WebSocket)
- **Performance:** C++ worker threads, minimal CPU overhead
- **Active development:** Regular updates, good community support

**Architecture fit:**
```typescript
// mediasoup integration with existing WebSocket server
import * as mediasoup from 'mediasoup';

// Create mediasoup worker (one per CPU core)
const worker = await mediasoup.createWorker({
  logLevel: 'warn',
  rtcMinPort: 40000,
  rtcMaxPort: 49999
});

// Handle WebRTC signaling over existing WebSocket
connection.on('message', async (data) => {
  const msg = packer.unpack(data);

  switch(msg.type) {
    case 'WEBRTC_CREATE_TRANSPORT':
      const transport = await router.createWebRtcTransport({...});
      respond({ transportParams: transport.params });
      break;

    case 'WEBRTC_PRODUCE':
      const producer = await transport.produce(msg.rtpParameters);
      // Forward to all consumers in channel
      break;
  }
});
```

**Alternatives considered:**
- **LiveKit:** Excellent, but includes its own signaling server - would require rearchitecting existing WebSocket server
- **Janus:** C-based, more complex integration with Node.js ecosystem
- **Kurento:** Java-based, heavy MCU architecture - wrong pattern for PTT

### TURN/STUN Deployment

**Recommended: Managed service (Twilio) for MVP, self-hosted (coturn) for production**

**Twilio STUN/TURN:**
- **Pro:** Zero operational overhead, global edge network
- **Pro:** Ephemeral credentials via API (secure)
- **Pro:** 99.99% uptime SLA
- **Con:** Cost scales with usage ($0.0004 per minute)
- **Con:** Vendor lock-in

**Self-hosted coturn:**
- **Pro:** No per-usage costs
- **Pro:** Full control, can deploy regionally
- **Con:** Operational overhead (monitoring, scaling, security updates)
- **Con:** Need to implement ephemeral credential issuance

**Recommendation:** Start with Twilio for MVP (faster to market), migrate to coturn post-validation when costs justify operational investment.

## Security Considerations

### Authentication & Authorization

**WebRTC security model:**
1. **Signaling authentication:** Existing JWT in WebSocket handshake authorizes WebRTC signaling messages
2. **Media authorization:** SFU validates channel membership before allowing transport creation
3. **Transport encryption:** DTLS-SRTP encrypts media end-to-end (client ↔ SFU)
4. **TURN authentication:** Ephemeral credentials (time-limited, signed) prevent unauthorized relay usage

**Implementation:**
```typescript
// WebSocket server validates JWT, then allows WebRTC signaling
if (!verifyJwt(token)) {
  return socket.close(401, 'Unauthorized');
}

// SFU validates membership before creating producer
const isMember = await redis.sismember(`g.${channelId}.u`, userId);
if (!isMember) {
  return respond({ error: 'NOT_AUTHORIZED' });
}

// TURN credentials are time-limited (ephemeral)
const turnCredentials = generateTurnCredentials(userId, ttl: 3600);
```

### Recording and Encryption

**Current plan:** Server-side decryption acceptable (VoicePing servers in trusted environment).

**WebRTC encryption:**
- **Client → SFU:** DTLS-SRTP (encrypted in transit)
- **SFU → Client:** DTLS-SRTP (encrypted in transit)
- **At SFU:** Media decrypted for routing and recording

**Future end-to-end encryption:** Would require client-side recording or SFrame (IETF draft, not widely supported in 2026).

**Compliance:** Server-side decryption enables recording for compliance/audit, which is acceptable per VoicePing requirements.

## Sources

**WebRTC Architecture:**
- [P2P, SFU, MCU, Hybrid: Which WebRTC Architecture Fits Your 2026 Roadmap?](https://www.forasoft.com/blog/article/webrtc-architecture-guide-for-business-2026) - HIGH confidence
- [WebRTC Architecture Basics: P2P, SFU, MCU, and Hybrid Approaches](https://medium.com/securemeeting/webrtc-architecture-basics-p2p-sfu-mcu-and-hybrid-approaches-6e7d77a46a66) - MEDIUM confidence

**Signaling Integration:**
- [WebRTC signaling with WebSocket and Node.js - LogRocket](https://blog.logrocket.com/webrtc-signaling-websocket-node-js/) - HIGH confidence
- [Signaling and video calling - MDN Web Docs](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API/Signaling_and_video_calling) - HIGH confidence

**SFU Implementation:**
- [mediasoup Documentation](https://mediasoup.org/documentation/overview/) - HIGH confidence (official docs)
- [LiveKit SFU Documentation](https://docs.livekit.io/reference/internals/livekit-sfu/) - HIGH confidence (official docs)
- [WebRTC SFU: the complete Guide](https://www.metered.ca/blog/webrtc-sfu-the-complete-guide/) - MEDIUM confidence

**State Management & Scaling:**
- [Building Zoom at Scale: SFU Architecture, WebRTC, and High-Performance .NET Design](https://developersvoice.com/blog/practical-design/scalable-webrtc-sfu-architecture-in-dotnet/) - MEDIUM confidence
- [WebRTC Tech Stack Guide: Architecture for Scalable Real-Time Applications](https://webrtc.ventures/2026/01/webrtc-tech-stack-guide-architecture-for-scalable-real-time-applications/) - HIGH confidence (January 2026)

**TURN/STUN:**
- [How to Set Up Self-Hosted STUN/TURN Servers for WebRTC Applications](https://webrtc.ventures/2025/01/how-to-set-up-self-hosted-stun-turn-servers-for-webrtc-applications/) - HIGH confidence
- [STUN and TURN Servers in WebRTC](https://www.digitalsamba.com/blog/stun-vs-turn) - MEDIUM confidence

**Selective Subscription:**
- [LiveKit Subscribing to tracks](https://docs.livekit.io/home/client/tracks/subscribe/) - HIGH confidence (official docs)
- [WebRTC Multitrack Playback](https://antmedia.io/antmediaserver-webrtc-multitrack-playing-feature/) - MEDIUM confidence

**Floor Control & PTT:**
- [Push to Talk App: Best PTT Solution 2026](https://www.mirrorfly.com/blog/push-to-talk-sdk-for-android-ios-app/) - LOW confidence (marketing)
- [Mastering WebRTC Control 2025](https://www.videosdk.live/developer-hub/webrtc/webrtc-control) - MEDIUM confidence

---
*Architecture research for: WebRTC PTT Communications Platform*
*Researched: 2026-02-06*
