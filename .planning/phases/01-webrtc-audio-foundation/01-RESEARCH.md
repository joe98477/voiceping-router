# Phase 1: WebRTC Audio Foundation - Research

**Researched:** 2026-02-06
**Domain:** WebRTC Audio Transmission (mediasoup SFU, Opus codec, PTT)
**Confidence:** MEDIUM-HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Architecture & Integration:**
- **Container replacement:** Replace entire vp-router container with new audio routing/streaming stack (mediasoup-based)
- **State management:** Hybrid approach
  - Redis: Real-time state (busy status, active channels, current speakers)
  - Control-plane: Auth, permissions, user/event/channel metadata
- **WebRTC signaling:** Dedicated WebSocket channel for WebRTC signaling (offer/answer/ICE candidates)
- **Deployment strategy:** Clean replacement (no parallel operation with old vp-router)

**PTT Button Behavior:**
- **Visual feedback:**
  - Button state change while pressed (color/style like walkie-talkie)
  - Transmission indicator ("Transmitting..." or audio wave animation)
- **Audio feedback:**
  - Audio confirmation tone when transmission starts
  - Audio confirmation tone when transmission stops
  - Audio prompts configurable via audio files in specific folder/naming structure
- **Busy state handling:**
  - Block transmission attempt with busy tone
  - Visual message showing "[username] is speaking"
  - No queueing - user must retry when channel is free
- **Interaction modes:**
  - Support both hold-to-talk (press and hold) and toggle (click on/off)
  - User preference setting to choose mode
  - Mouse/touch only for Phase 1 (no keyboard shortcuts)

### Claude's Discretion

- Testing strategy: latency measurement approach, cross-browser test matrix
- Browser compatibility priorities: which browsers to test first, mobile testing scope
- Error handling and reconnection UX
- Logging and debugging instrumentation
- STUN/TURN server selection and configuration

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

## Summary

WebRTC audio transmission for push-to-talk (PTT) applications requires mediasoup v3 as the SFU (Selective Forwarding Unit), WebSocket-based signaling for offer/answer/ICE candidate exchange, and Opus codec configured for low-latency audio. The standard architecture uses mediasoup workers on the server side with mediasoup-client (v3.18.6) in the browser, Redis for distributed state management (speaker locks, channel busy state), and STUN/TURN servers for NAT traversal.

The PTT use case requires special attention to three critical areas: (1) Opus codec configuration with 10-20ms frame sizes, CBR mode, and DTX disabled for predictable latency, (2) Exclusive speaker access enforced via Redis locks with automatic expiration, and (3) Sub-300ms end-to-end latency achieved through proper transport configuration and minimal buffering.

Browser compatibility is strong across Chrome, Firefox, and Safari desktop (all support WebRTC), but Safari's H.264-only limitation and lack of newer APIs require testing. The standard pattern uses getUserMedia() with echoCancellation and noiseSuppression enabled, HTML5 Audio API for feedback tones, and WebSocket reconnection logic with exponential backoff.

**Primary recommendation:** Use mediasoup v3.19.16 server + mediasoup-client v3.18.6, ws library for WebSocket signaling, Redis for speaker locks, and Opus at 48kHz/10-20ms frames in CBR mode with DTX disabled.

## Standard Stack

The established libraries/tools for WebRTC PTT with mediasoup:

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| mediasoup | 3.19.16 | Server-side SFU for media routing | Industry-standard open-source SFU with C++ performance, low latency, multi-core support |
| mediasoup-client | 3.18.6 | Browser-side WebRTC client library | Official client for mediasoup, handles RTP capabilities and WebRTC transport |
| ws | Latest | WebSocket server/client for Node.js | Fastest, most tested WebSocket implementation for Node.js, passes Autobahn test suite |
| Redis | 7.x+ | Real-time state management | Fast distributed locks for speaker exclusivity, pub/sub for signaling coordination |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Coturn | Latest | TURN/STUN server | Production NAT traversal for restrictive firewalls (~10-15% of users need TURN) |
| HTML5 Audio API | Native | Play audio feedback tones | Simple tone playback without dependencies |
| getUserMedia API | Native | Capture microphone input | Standard browser API for audio capture with constraints |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| mediasoup | Janus Gateway | Janus requires more complex signaling, mediasoup has better Node.js integration |
| mediasoup | LiveKit | LiveKit is higher-level but less flexible for custom PTT logic |
| ws | Socket.IO | Socket.IO adds overhead and automatic reconnection complexity; ws is faster and simpler |
| Redis locks | In-memory locks | Redis provides distributed locks across multiple server instances |

**Installation:**

```bash
# Server-side
npm install mediasoup@3 ws redis

# Client-side (browser)
npm install mediasoup-client
```

## Architecture Patterns

### Recommended Project Structure

```
server/
├── mediasoup/
│   ├── worker.js         # Worker pool management
│   ├── router.js         # Router creation and codec config
│   ├── transport.js      # WebRTC transport handling
│   └── producer-consumer.js  # Media producer/consumer logic
├── signaling/
│   ├── websocket.js      # WebSocket server setup
│   ├── handlers.js       # Signaling message handlers
│   └── protocol.js       # Message type definitions
├── state/
│   ├── redis-client.js   # Redis connection
│   ├── channel-locks.js  # Speaker lock management
│   └── session-store.js  # User session state
└── server.js             # Main entry point

client/
├── mediasoup-client/
│   ├── device.js         # mediasoup Device initialization
│   ├── transport.js      # Send/recv transport management
│   └── producer-consumer.js  # Media production/consumption
├── signaling/
│   ├── websocket.js      # WebSocket client
│   └── handlers.js       # Message handlers
├── audio/
│   ├── microphone.js     # getUserMedia handling
│   ├── tones.js          # Audio feedback playback
│   └── constraints.js    # Audio constraint configuration
└── ui/
    ├── ptt-button.js     # PTT button component
    └── channel-status.js # Busy indicator UI
```

### Pattern 1: mediasoup Worker Pool

**What:** Initialize multiple mediasoup workers (one per CPU core) and distribute routers across them for load balancing.

**When to use:** Always in production. Single worker limits CPU usage to one core.

**Example:**

```javascript
// Source: https://mediasoup.org/documentation/v3/mediasoup/api/
import mediasoup from 'mediasoup';
import os from 'os';

const workers = [];
const numWorkers = os.cpus().length;

async function createWorkerPool() {
  for (let i = 0; i < numWorkers; i++) {
    const worker = await mediasoup.createWorker({
      logLevel: 'warn',
      rtcMinPort: 40000,
      rtcMaxPort: 49999,
    });

    worker.on('died', () => {
      console.error('mediasoup worker died, exiting in 2 seconds...');
      setTimeout(() => process.exit(1), 2000);
    });

    workers.push(worker);
  }
}

function getNextWorker() {
  return workers[Math.floor(Math.random() * workers.length)];
}
```

### Pattern 2: WebRTC Signaling Flow

**What:** Client and server exchange SDP offers/answers and ICE candidates via WebSocket. Use "trickle ICE" to send candidates incrementally.

**When to use:** Always for WebRTC connection establishment.

**Example:**

```javascript
// Source: https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API/Connectivity
// Client sends offer
const transport = await device.createSendTransport(transportOptions);
const offer = await transport.getStats(); // Simplified

ws.send(JSON.stringify({
  type: 'transport-connect',
  transportId: transport.id,
  dtlsParameters: transport.dtlsParameters
}));

// Client handles ICE candidates (trickle ICE)
transport.on('connect', async ({ dtlsParameters }, callback, errback) => {
  try {
    ws.send(JSON.stringify({
      type: 'transport-dtls',
      transportId: transport.id,
      dtlsParameters
    }));
    callback();
  } catch (error) {
    errback(error);
  }
});
```

### Pattern 3: Redis Distributed Speaker Lock

**What:** Use Redis SET with NX (not exists) and EX (expiration) to implement distributed locks for exclusive speaker access.

**When to use:** Always for PTT busy state management across multiple server instances.

**Example:**

```javascript
// Source: https://redis.io/ + distributed lock pattern
async function acquireSpeakerLock(channelId, userId, ttlSeconds = 30) {
  const lockKey = `channel:${channelId}:speaker`;
  const lockValue = JSON.stringify({ userId, timestamp: Date.now() });

  // SET with NX (only if not exists) and EX (expiration)
  const result = await redis.set(
    lockKey,
    lockValue,
    'NX',  // Only set if key doesn't exist
    'EX', ttlSeconds  // Auto-expire after TTL
  );

  if (result === 'OK') {
    return { acquired: true, userId };
  }

  // Lock held by someone else
  const currentLock = await redis.get(lockKey);
  const currentSpeaker = JSON.parse(currentLock);
  return { acquired: false, currentSpeaker: currentSpeaker.userId };
}

async function releaseSpeakerLock(channelId, userId) {
  const lockKey = `channel:${channelId}:speaker`;
  const currentLock = await redis.get(lockKey);

  if (currentLock) {
    const { userId: lockHolder } = JSON.parse(currentLock);
    // Only delete if this user holds the lock
    if (lockHolder === userId) {
      await redis.del(lockKey);
      return true;
    }
  }
  return false;
}
```

### Pattern 4: getUserMedia with Constraints

**What:** Request microphone access with audio constraints optimized for PTT (echo cancellation, noise suppression, auto gain control).

**When to use:** Always when initializing audio capture.

**Example:**

```javascript
// Source: https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia
const audioConstraints = {
  audio: {
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
    sampleRate: 48000,  // Opus works best at 48kHz
    channelCount: 1     // Mono for PTT
  },
  video: false
};

try {
  const stream = await navigator.mediaDevices.getUserMedia(audioConstraints);
  const audioTrack = stream.getAudioTracks()[0];

  // Create mediasoup producer
  const producer = await sendTransport.produce({
    track: audioTrack,
    codecOptions: {
      opusStereo: false,
      opusDtx: false,  // Disable DTX for PTT (predictable latency)
      opusFec: true,   // Enable forward error correction
      opusMaxPlaybackRate: 48000
    }
  });

  return { stream, producer };
} catch (error) {
  if (error.name === 'NotAllowedError') {
    // User denied microphone permission
  } else if (error.name === 'NotFoundError') {
    // No microphone available
  }
  throw error;
}
```

### Pattern 5: HTML5 Audio Feedback Tones

**What:** Play short audio feedback tones using HTML5 Audio API for PTT start/stop/busy feedback.

**When to use:** Always for PTT audio feedback.

**Example:**

```javascript
// Source: https://developer.mozilla.org/en-US/docs/Web/API/HTMLAudioElement
class AudioFeedback {
  constructor() {
    this.tones = {
      transmitStart: new Audio('/audio/transmit-start.mp3'),
      transmitStop: new Audio('/audio/transmit-stop.mp3'),
      busy: new Audio('/audio/busy-tone.mp3')
    };

    // Preload all tones
    Object.values(this.tones).forEach(audio => {
      audio.load();
      audio.volume = 0.7;
    });
  }

  play(toneName) {
    const audio = this.tones[toneName];
    if (audio) {
      audio.currentTime = 0; // Reset to start
      audio.play().catch(err => console.error('Audio playback failed:', err));
    }
  }
}

// Usage
const feedback = new AudioFeedback();
feedback.play('transmitStart');
```

### Pattern 6: WebSocket Reconnection with Exponential Backoff

**What:** Automatically reconnect WebSocket with exponential backoff on connection loss, restore session state.

**When to use:** Always for production reliability.

**Example:**

```javascript
// Source: https://webrtc.ventures/2023/06/implementing-a-reconnection-mechanism-for-webrtc-mobile-applications/
class ReconnectingWebSocket {
  constructor(url, options = {}) {
    this.url = url;
    this.reconnectDelay = options.reconnectDelay || 1000;
    this.maxReconnectDelay = options.maxReconnectDelay || 30000;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = options.maxReconnectAttempts || Infinity;
    this.ws = null;
    this.connect();
  }

  connect() {
    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      console.log('WebSocket connected');
      this.reconnectAttempts = 0;
      this.reconnectDelay = 1000;
      // Restore session state here
      this.onReconnect?.();
    };

    this.ws.onclose = (event) => {
      if (!event.wasClean) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }

  scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached');
      return;
    }

    this.reconnectAttempts++;
    const delay = Math.min(
      this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1),
      this.maxReconnectDelay
    );

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);
    setTimeout(() => this.connect(), delay);
  }

  send(data) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(data);
    } else {
      console.warn('WebSocket not open, message queued');
      // Queue message for sending after reconnect
    }
  }
}
```

### Anti-Patterns to Avoid

- **Using DTX for PTT:** DTX (Discontinuous Transmission) introduces variable latency as codec re-engages on voice activity. PTT needs predictable latency, so disable DTX.
- **Not handling TURN fallback:** 10-15% of users behind restrictive firewalls/NATs need TURN. Test without STUN to verify TURN works.
- **Single mediasoup worker:** Limits CPU usage to one core. Always use worker pool pattern.
- **Synchronous Redis operations:** Use async Redis client to avoid blocking Node.js event loop.
- **No lock expiration:** Speaker locks must auto-expire (TTL 30s) to prevent deadlock if client crashes without releasing.
- **Ignoring ICE connection state:** Monitor `iceConnectionState` to detect disconnections and trigger reconnection flow.
- **Hard-coded STUN servers:** Never rely on public STUN servers (like stun.l.google.com) in production. Deploy your own STUN/TURN infrastructure.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WebSocket reconnection logic | Custom reconnect with timers | Exponential backoff pattern (see Pattern 6) | Edge cases: race conditions, duplicate connections, message ordering |
| Distributed locks | In-memory locks with sync | Redis SET NX EX pattern | Atomic operations, auto-expiration, multi-server support |
| ICE candidate exchange | Custom state machine | mediasoup-client Device API | Handles trickle ICE, candidate gathering, STUN/TURN selection automatically |
| Audio echo cancellation | Custom DSP filters | getUserMedia echoCancellation constraint | Browser-native AEC is hardware-accelerated and tested across devices |
| SDP negotiation | Manual SDP parsing/generation | mediasoup RTP capabilities exchange | mediasoup abstracts SDP complexity, handles codec negotiation |
| Latency measurement | Manual timestamping | WebRTC stats API (`getStats()`) + data channel timestamps | Built-in RTT metrics, standardized across browsers |

**Key insight:** WebRTC has massive hidden complexity in NAT traversal, codec negotiation, and network resilience. mediasoup and browser APIs abstract 90% of this. Custom solutions miss edge cases that take years to discover.

## Common Pitfalls

### Pitfall 1: STUN/TURN Misconfiguration

**What goes wrong:** Connections fail for users behind restrictive firewalls/NATs. App works in development (local network) but fails for 10-15% of users in production.

**Why it happens:**
- Relying only on STUN (can't traverse symmetric NAT)
- Using free public STUN/TURN servers that rate-limit or go offline
- Not testing TURN fallback path
- TURN server not opening required ports (UDP 3478, TCP 443)

**How to avoid:**
- Deploy own Coturn server with authentication
- Configure mediasoup with both STUN and TURN
- Test with `chrome://webrtc-internals` to verify ICE candidates
- Set `iceTransportPolicy: 'relay'` to force TURN and verify it works

**Warning signs:**
- `iceConnectionState` stuck at "checking" or goes to "failed"
- Works on same LAN but fails across internet
- ICE candidate gathering timeout

### Pitfall 2: Not Disabling DTX for PTT

**What goes wrong:** First 100-300ms of speech gets cut off when user presses PTT button. Users complain "first word is missing."

**Why it happens:** Opus DTX (Discontinuous Transmission) stops encoding during silence to save bandwidth. On PTT press, codec needs 100-300ms to re-engage, cutting off speech start.

**How to avoid:**
- Set `opusDtx: false` in mediasoup producer codec options
- Set `usedtx: 0` in Opus encoder config
- Verify with packet capture that audio packets sent immediately on PTT

**Warning signs:**
- Audio starts mid-word
- First syllable consistently missing
- Works fine after first second

### Pitfall 3: Ignoring Browser Permissions Flow

**What goes wrong:** App silently fails or shows confusing error when user denies microphone permission. getUserMedia() never resolves on Safari if permissions not granted via HTTPS.

**Why it happens:**
- Not handling `NotAllowedError` exception
- Not checking `navigator.permissions` before getUserMedia
- Not serving over HTTPS (required for getUserMedia on Safari)
- No UI to prompt permission grant

**How to avoid:**
- Check permission state before getUserMedia: `navigator.permissions.query({ name: 'microphone' })`
- Handle `NotAllowedError` with clear UI message
- Provide button to re-trigger permission prompt
- Serve over HTTPS in all environments (not just production)

**Warning signs:**
- getUserMedia promise never resolves
- Console error "NotAllowedError: Permission denied"
- Works in Chrome, fails in Safari
- Works on localhost, fails on deployed domain

### Pitfall 4: Single Point of Failure (No Worker Recovery)

**What goes wrong:** mediasoup worker crashes (out of memory, segfault, etc.), taking down all active media sessions. Entire server becomes unavailable.

**Why it happens:**
- Not monitoring worker `died` event
- No graceful restart mechanism
- Not distributing routers across workers
- No health checks or circuit breakers

**How to avoid:**
- Listen to worker `died` event and exit process (let PM2/systemd restart)
- Use multiple workers (one per CPU core) and distribute load
- Implement health check endpoint that verifies worker pool health
- Use process manager (PM2, systemd) for automatic restart

**Warning signs:**
- Mediasoup logs "worker died" before crash
- All connections drop simultaneously
- Server becomes unresponsive
- Memory usage grows unbounded

### Pitfall 5: Opus Frame Size Mismatch

**What goes wrong:** Latency exceeds 300ms target despite low network RTT. Audio feels "laggy" even with good connection.

**Why it happens:**
- Using default 20ms Opus frames (acceptable but not optimal)
- Not configuring `ptime` parameter in RTP
- Buffering multiple frames before sending
- Receiver buffering frames for jitter reduction

**How to avoid:**
- Configure Opus with 10ms or 20ms frame size (not 60ms default)
- Set `ptime=10` or `ptime=20` in SDP
- Disable jitter buffer or minimize size
- Use CBR (constant bitrate) mode to avoid frame size variation
- Measure end-to-end latency with data channel timestamps

**Warning signs:**
- Latency measured at 400-600ms despite 50ms RTT
- Audio packets arrive in bursts, not steady stream
- Network stats show low RTT but high end-to-end delay

### Pitfall 6: Race Condition in Speaker Lock

**What goes wrong:** Two users simultaneously press PTT and both start transmitting, or lock gets stuck "busy" when no one is speaking.

**Why it happens:**
- Using non-atomic check-then-set for lock acquisition
- Not setting lock expiration (TTL)
- Not validating lock ownership before release
- Network delay between lock check and set

**How to avoid:**
- Use Redis `SET key value NX EX seconds` for atomic lock acquire
- Always set TTL (30s) to auto-release stuck locks
- Verify lock ownership (user ID) before deleting
- Use Redis transactions or Lua script for complex lock operations

**Warning signs:**
- Multiple users transmitting simultaneously
- Channel stuck "busy" after user disconnects
- Lock state doesn't match actual speaker
- "Busy tone" plays when channel is free

### Pitfall 7: Not Testing Browser WebRTC Differences

**What goes wrong:** Works perfectly in Chrome, broken in Firefox or Safari. Codec negotiation fails, audio quality differs, or connection never establishes.

**Why it happens:**
- Safari only supports H.264 video (audio generally OK, but API differences)
- Firefox uses different WebRTC implementation (different ICE behavior)
- Chrome has newest WebRTC features not in other browsers
- Not testing actual browsers, only using Chrome

**How to avoid:**
- Test on real Chrome, Firefox, Safari (not just Chrome DevTools)
- Use mediasoup RTP capabilities detection (handles codec differences)
- Check `navigator.mediaDevices` exists (Safari requires user gesture)
- Use feature detection, not browser detection
- Test on macOS Safari (most restrictive)

**Warning signs:**
- "Works on my machine" (Chrome) but not user's browser
- Firefox console shows different errors than Chrome
- Safari requires user interaction to call getUserMedia
- Codec negotiation fails in Safari

### Pitfall 8: Inadequate Logging and Debugging

**What goes wrong:** Production issues are impossible to debug. Users report "audio not working" but no logs capture the failure. Can't reproduce in development.

**Why it happens:**
- Not logging ICE connection state changes
- Not capturing WebRTC stats for failed connections
- No correlation ID between client and server logs
- Not logging mediasoup events (transport creation, producer start, etc.)

**How to avoid:**
- Log all ICE state transitions (`iceConnectionState`, `connectionState`)
- Capture `getStats()` when connection fails, send to server
- Use correlation IDs (session ID, user ID) in all logs
- Log mediasoup producer/consumer lifecycle events
- Send client-side errors to server logging system
- Integrate structured logging (Winston, Pino)

**Warning signs:**
- "It worked yesterday, now it's broken" with no clue why
- Can't reproduce user-reported issues
- No visibility into connection failures
- Metrics dashboard shows errors but no context

## Code Examples

Verified patterns from official sources:

### mediasoup Router Creation with Opus Config

```javascript
// Source: https://mediasoup.org/documentation/v3/mediasoup/api/
async function createRouter(worker) {
  const mediaCodecs = [
    {
      kind: 'audio',
      mimeType: 'audio/opus',
      clockRate: 48000,
      channels: 1,  // Mono for PTT
      parameters: {
        'sprop-stereo': 0,
        'usedtx': 0,  // Disable DTX for PTT
        'maxplaybackrate': 48000,
        'ptime': 10   // 10ms frame size for low latency
      }
    }
  ];

  const router = await worker.createRouter({ mediaCodecs });
  return router;
}
```

### WebRTC Transport Creation

```javascript
// Source: https://mediasoup.org/documentation/v3/communication-between-client-and-server/
// Server-side
async function createWebRtcTransport(router) {
  const transport = await router.createWebRtcTransport({
    listenIps: [
      { ip: '0.0.0.0', announcedIp: 'YOUR_PUBLIC_IP' }
    ],
    enableUdp: true,
    enableTcp: true,
    preferUdp: true,
    initialAvailableOutgoingBitrate: 100000,
    iceServers: [
      { urls: 'stun:YOUR_STUN_SERVER:3478' },
      {
        urls: 'turn:YOUR_TURN_SERVER:3478',
        username: 'username',
        credential: 'password'
      }
    ]
  });

  return {
    id: transport.id,
    iceParameters: transport.iceParameters,
    iceCandidates: transport.iceCandidates,
    dtlsParameters: transport.dtlsParameters
  };
}

// Client-side
const transportOptions = await sendRequest('create-transport');
const sendTransport = device.createSendTransport(transportOptions);

sendTransport.on('connect', async ({ dtlsParameters }, callback, errback) => {
  try {
    await sendRequest('transport-connect', {
      transportId: sendTransport.id,
      dtlsParameters
    });
    callback();
  } catch (error) {
    errback(error);
  }
});

sendTransport.on('produce', async ({ kind, rtpParameters }, callback, errback) => {
  try {
    const { id } = await sendRequest('produce', {
      transportId: sendTransport.id,
      kind,
      rtpParameters
    });
    callback({ id });
  } catch (error) {
    errback(error);
  }
});
```

### Latency Measurement with Data Channel

```javascript
// Source: https://webrtchacks.com/calculate-true-end-to-end-rtt/
class LatencyMonitor {
  constructor(dataChannel) {
    this.dataChannel = dataChannel;
    this.pendingPings = new Map();

    this.dataChannel.addEventListener('message', (event) => {
      const message = JSON.parse(event.data);
      if (message.type === 'pong') {
        this.handlePong(message);
      } else if (message.type === 'ping') {
        this.sendPong(message.timestamp);
      }
    });
  }

  measureLatency() {
    const pingId = Math.random().toString(36);
    const timestamp = performance.now();

    this.pendingPings.set(pingId, timestamp);
    this.dataChannel.send(JSON.stringify({
      type: 'ping',
      id: pingId,
      timestamp
    }));

    // Cleanup after 5 seconds
    setTimeout(() => this.pendingPings.delete(pingId), 5000);
  }

  handlePong({ id, timestamp }) {
    if (this.pendingPings.has(id)) {
      const sentTime = this.pendingPings.get(id);
      const rtt = performance.now() - sentTime;
      const oneWayLatency = rtt / 2;

      console.log(`RTT: ${rtt.toFixed(2)}ms, One-way: ${oneWayLatency.toFixed(2)}ms`);
      this.pendingPings.delete(id);

      return { rtt, oneWayLatency };
    }
  }

  sendPong(originalTimestamp) {
    this.dataChannel.send(JSON.stringify({
      type: 'pong',
      id: originalTimestamp,
      timestamp: performance.now()
    }));
  }
}

// Usage
const monitor = new LatencyMonitor(dataChannel);
setInterval(() => monitor.measureLatency(), 1000);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Peer-to-peer WebRTC (mesh) | SFU architecture (mediasoup) | 2018+ | Scalable to 10+ participants, reduced bandwidth per client |
| Manual SDP exchange | mediasoup RTP capabilities | mediasoup v3 (2020) | Simpler signaling, no SDP parsing needed |
| ICE complete before connect | Trickle ICE | WebRTC 1.0 (2021) | Faster connection establishment (save 1-2 seconds) |
| VBR (variable bitrate) Opus | CBR for PTT, VBR for calls | Industry practice | Predictable latency for PTT, better quality for normal calls |
| navigator.getUserMedia | navigator.mediaDevices.getUserMedia | Deprecated 2017 | Promise-based API, better error handling |
| Public STUN servers | Self-hosted STUN/TURN | Always best practice | Reliability, no rate limiting, privacy |

**Deprecated/outdated:**
- **RTCPeerConnection legacy APIs:** Use unified plan SDP (default since Chrome 72, Firefox 63)
- **navigator.getUserMedia():** Deprecated, use `navigator.mediaDevices.getUserMedia()`
- **Google Public STUN:** Never production-ready, rate-limited, no SLA
- **mediasoup v2:** End of life, use v3 (major API changes)

## Open Questions

Things that couldn't be fully resolved:

1. **Optimal Opus frame size for PTT**
   - What we know: 10ms and 20ms are both common, 10ms gives lower latency
   - What's unclear: Exact latency difference in production (depends on network, browser, jitter buffer)
   - Recommendation: Start with 20ms (more compatible), test with 10ms if latency exceeds target

2. **Mobile browser support (iOS Safari)**
   - What we know: iOS Safari supports WebRTC, but all iOS browsers use WebKit (same limitations)
   - What's unclear: Whether H.264-only codec limitation affects audio (Opus should work)
   - Recommendation: Test on actual iOS device early, Phase 1 is desktop-first so defer mobile testing

3. **Redis vs. in-memory state for small deployments**
   - What we know: Redis provides distributed locks, but adds operational complexity
   - What's unclear: Whether single-server deployment needs Redis immediately
   - Recommendation: Use Redis from start (easier to scale later), but it's technically optional for single server

4. **TURN server bandwidth costs**
   - What we know: 10-15% of users need TURN, audio uses ~100 kbps
   - What's unclear: Exact cost/user/month for typical PTT usage patterns
   - Recommendation: Monitor TURN usage in production, optimize by testing ICE gathering to minimize TURN usage

5. **Browser differences in echo cancellation**
   - What we know: Chrome, Firefox, Safari all support echoCancellation constraint
   - What's unclear: Quality differences and whether PTT needs AEC (half-duplex, no speaker output)
   - Recommendation: Enable by default, allow users to disable if issues reported

## Sources

### Primary (HIGH confidence)

- [mediasoup v3 Documentation](https://mediasoup.org/documentation/v3/) - Official mediasoup docs (installation, API, patterns)
- [mediasoup Installation](https://mediasoup.org/documentation/v3/mediasoup/installation/) - Version requirements, dependencies
- [mediasoup Communication Patterns](https://mediasoup.org/documentation/v3/communication-between-client-and-server/) - Signaling recommendations
- [mediasoup Client API](https://mediasoup.org/documentation/v3/mediasoup-client/) - Browser client library
- [MDN: WebRTC API](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API) - Browser WebRTC APIs
- [MDN: getUserMedia()](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia) - Microphone capture API
- [MDN: WebRTC Connectivity](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API/Connectivity) - ICE, STUN, TURN, signaling
- [MDN: Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API) - Audio playback for feedback tones
- [RFC 6716: Opus Codec](https://datatracker.ietf.org/doc/html/rfc6716) - Opus codec specification
- [RFC 7587: RTP Opus](https://datatracker.ietf.org/doc/html/rfc7587) - Opus over RTP specification

### Secondary (MEDIUM confidence)

- [mediasoup NPM](https://www.npmjs.com/package/mediasoup) - Version 3.19.16 confirmed (published 1 day ago)
- [mediasoup-client NPM](https://www.npmjs.com/package/mediasoup-client) - Version 3.18.6 confirmed (published 5 days ago)
- [ws NPM](https://www.npmjs.com/package/ws) - WebSocket library for Node.js
- [Redis.io](https://redis.io/) - Redis documentation for state management
- [WebRTC Tech Stack Guide 2026](https://webrtc.ventures/2026/01/webrtc-tech-stack-guide-architecture-for-scalable-real-time-applications/) - Modern WebRTC architecture patterns
- [WebRTC Browser Support 2025](https://antmedia.io/webrtc-browser-support/) - Chrome, Firefox, Safari compatibility
- [How to Set Up STUN/TURN Servers 2025](https://webrtc.ventures/2025/01/how-to-set-up-self-hosted-stun-turn-servers-for-webrtc-applications/) - Coturn setup guide
- [WebRTC Common Mistakes](https://bloggeek.me/common-beginner-mistakes-in-webrtc/) - Pitfalls from industry expert
- [Implementing WebRTC Reconnection](https://webrtc.ventures/2023/06/implementing-a-reconnection-mechanism-for-webrtc-mobile-applications/) - Reconnection patterns
- [Calculating True End-to-End RTT](https://webrtchacks.com/calculate-true-end-to-end-rtt/) - Latency measurement techniques

### Tertiary (LOW confidence - needs validation)

- [Janus vs MediaSoup comparison](https://trembit.com/blog/choosing-the-right-sfu-janus-vs-mediasoup-vs-livekit-for-telemedicine-platforms/) - SFU comparison (biased source)
- [WebRTC Latency Guide](https://www.nanocosmos.net/blog/webrtc-latency/) - General latency discussion (not PTT-specific)
- [Push-to-Talk Wikipedia](https://en.wikipedia.org/wiki/Push-to-talk) - General PTT background

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - mediasoup and versions verified from NPM, official docs current
- Architecture: HIGH - Patterns verified from official mediasoup docs and MDN
- Opus configuration: MEDIUM - RFC specifications confirmed, but PTT-specific settings from community practice
- Browser compatibility: MEDIUM - MDN + community sources agree, but iOS testing recommended
- Pitfalls: MEDIUM - Sourced from expert blogs and community, need production validation
- STUN/TURN: MEDIUM - Best practices from multiple sources, but cost/bandwidth needs monitoring

**Research date:** 2026-02-06
**Valid until:** 2026-03-06 (30 days - mediasoup is stable, WebRTC standards mature)
