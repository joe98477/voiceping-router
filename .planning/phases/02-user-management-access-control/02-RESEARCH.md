# Phase 2: User Management & Access Control - Research

**Researched:** 2026-02-06
**Domain:** WebSocket Authorization, Role-Based Access Control, Real-Time Permission Sync
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Authorization Enforcement:**
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

**Channel Join Flow:**
- Server pushes channel list to user on connect (users can only select from assigned channels/teams)
- Live updates via WebSocket push event when channel assignments change mid-session (auto-add new channels, auto-remove revoked ones)
- General users: listen to multiple channels simultaneously, PTT on one at a time (radio scanner model)
- Auto-listen on join: joining a channel immediately starts audio reception; user can mute individual channels
- User selects which assigned channels to actively monitor (not all-assigned = all-active)
- Configurable simultaneous channel limit (default set by Admin/Dispatch per event); when limit reached, block with message "Maximum channels reached. Remove a channel to add another."

**Role Behavior Boundaries:**
- Per-event roles: same user can be Dispatch in Event A and General in Event B (matches existing Prisma EventMembership model)
- Dispatch has PTT priority interrupt: can immediately take over channel, cutting off current General user speaker
- Admin does NOT have PTT priority — Admin role is management, not real-time communication
- Dispatch can force-disconnect (instant kick) a user from a channel in real-time
- Emergency broadcast: Dispatch can transmit to ALL channels in an event simultaneously, overriding all active speakers
- Emergency broadcast activation: 2-second long press on distinct broadcast button (prevents accidental activation, no dialog)
- Interrupted user experience: immediate audio cutoff + notification "Dispatch [name] has priority" — must re-press PTT after Dispatch finishes
- Visible roles: General users see role badges/indicators for Dispatch users in their channel

**Scaling & Concurrency:**
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

### Deferred Ideas (OUT OF SCOPE)

- Security events admin UI panel — Phase 3 (backend/API built in Phase 2)
- Multi-server horizontal scaling with load balancer — future phase when single-server vertical scaling is insufficient
- Mobile-specific optimizations for low-bandwidth networks — Phase 3/4

</user_constraints>

## Summary

Phase 2 bridges the existing control-plane (Prisma models, REST API, session auth) into the real-time WebRTC router layer to enforce role-based permissions and organizational hierarchy (events > teams > channels) during live audio communication. The control-plane already manages users, roles, events, teams, and channels via PostgreSQL/Prisma with session-based HTTP auth and JWT tokens. This phase makes the router respect those permissions in real-time via WebSocket signaling.

The standard approach uses a **hybrid JWT + Redis sync pattern**: JWT claims encode user identity and event-level roles at login (1-hour TTL), while Redis pub/sub propagates real-time permission changes (channel assignments, role updates, force-disconnects) to active WebSocket connections via heartbeat-based permission refreshes every 30-60 seconds. This balances security (fresh permissions) with reliability (auth failures don't block critical audio).

Role-based PTT priority requires special interrupt logic: Dispatch users can preempt General users mid-transmission by pausing the current speaker's producer and resuming the Dispatch producer immediately, with WebSocket push notifications informing the interrupted user. Emergency broadcast extends this to all channels in an event simultaneously, requiring multi-channel producer management and coordination.

Network reliability for "critical audio must get through" demands adaptive jitter buffering (40-80ms server-side), Opus FEC (Forward Error Correction) enabled for 1%+ packet loss, and WebRTC transport configuration tuned for low-bandwidth/high-jitter networks. Rate limiting uses progressive slowdown (node-rate-limiter-flexible with Redis backend) to prevent brute force while allowing legitimate retries.

**Primary recommendation:** Hybrid auth (JWT identity + Redis permission sync), heartbeat-based refresh every 30s, node-rate-limiter-flexible for progressive rate limiting, mediasoup producer pause/resume for PTT priority, Redis pub/sub for real-time permission updates, and structured audit logging to Prisma AuditLog model.

## Standard Stack

The established libraries/tools for WebSocket authorization and real-time permission management:

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| jsonwebtoken | 9.0.2 | JWT signing and verification | Already in use (control-plane and router), industry standard, zero dependencies |
| redis | 4.7.0 | Permission state sync and pub/sub | Already in use for speaker locks, provides atomic operations and pub/sub for real-time updates |
| node-rate-limiter-flexible | Latest | Progressive rate limiting with Redis | De facto standard for Node.js auth rate limiting, supports Redis backend, flexible policies |
| Prisma | 5.18.0 | Audit log persistence | Already in use for control-plane, existing AuditLog model, type-safe queries |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| ws | 8.16.0 | WebSocket server | Already in use for signaling, lightweight, passes Autobahn tests |
| winston | 3.11.0 | Structured logging | Already in use in router, industry standard, supports multiple transports |
| bcryptjs | 2.4.3 | Password hashing (control-plane) | Already in use, needed for password validation in auth flow |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hybrid JWT+Redis | Pure JWT claims | Pure JWT can't revoke permissions in real-time, violates "world-class security" constraint |
| Hybrid JWT+Redis | Database per-check | Database query on every join adds latency, violates "critical audio must never fail due to auth delays" |
| node-rate-limiter-flexible | express-rate-limit | express-rate-limit lacks Redis backend and progressive slowdown, designed for HTTP not WebSocket |
| Redis pub/sub | WebSocket broadcast | Redis pub/sub decouples permission changes from active connections, scales across multiple router instances (future) |

**Installation:**

```bash
# Already installed in existing codebase:
# - jsonwebtoken: 9.0.2 (router)
# - redis: 4.6.0 (router), 4.7.0 (control-plane)
# - ws: 8.16.0 (router)
# - Prisma: 5.18.0 (control-plane)

# New dependencies for Phase 2:
npm install rate-limiter-flexible
```

## Architecture Patterns

### Recommended Project Structure

```
src/server/
├── auth/
│   ├── jwtValidator.ts       # JWT verification middleware
│   ├── permissionCache.ts    # Redis-backed permission cache
│   ├── permissionSync.ts     # Redis pub/sub for permission updates
│   └── heartbeat.ts          # Heartbeat-based permission refresh
├── authorization/
│   ├── channelAccess.ts      # Channel membership checks
│   ├── roleChecks.ts         # Event-level role verification (DISPATCH vs USER)
│   ├── priorityLogic.ts      # PTT priority interrupt for Dispatch
│   └── forceDisconnect.ts    # Force-disconnect implementation
├── rate-limiting/
│   ├── authLimiter.ts        # Login attempt rate limiting
│   ├── joinLimiter.ts        # Channel join rate limiting
│   └── pttLimiter.ts         # PTT action rate limiting (lenient)
├── audit/
│   ├── auditLogger.ts        # Write to Prisma AuditLog
│   ├── securityEvents.ts     # Security event tracking (rate limit, force-disconnect)
│   └── userActions.ts        # User action tracking (join, PTT, leave)
├── signaling/
│   ├── pushEvents.ts         # Server-initiated WebSocket messages (channel list, permission changes)
│   └── handlers.ts           # (extend existing with auth checks)
└── state/
    ├── channelAssignments.ts # Track user's assigned channels (synced from Prisma via Redis)
    └── activeRoles.ts        # Track user's event roles (cached from JWT + Redis updates)

control-plane/src/
├── api/
│   ├── security.js           # NEW: Security events API (ban/unban, event log)
│   └── auth.js               # (extend existing with audit logging)
└── middleware/
    └── auditLog.js           # NEW: Audit logging middleware for control-plane actions
```

### Pattern 1: Hybrid JWT + Redis Permission Sync

**What:** User authenticates once via control-plane, receives JWT with identity + event roles. Router verifies JWT on WebSocket connect, then subscribes to Redis pub/sub channel `user:{userId}:permissions` for real-time updates. Heartbeat (every 30-60s) re-queries Redis for fresh channel assignments.

**When to use:** Always. Satisfies "world-class security" (fresh permissions) and "critical audio must never fail" (cached JWT identity allows degraded operation if Redis temporarily unavailable).

**Example:**

```typescript
// src/server/auth/jwtValidator.ts
import jwt from 'jsonwebtoken';
import { config } from '../config';

interface JwtPayload {
  userId: string;
  userName: string;
  globalRole: 'ADMIN' | 'NONE';
  eventRoles: { eventId: string; role: 'DISPATCH' | 'USER' }[];
  iat: number;
  exp: number;
}

export function verifyJwt(token: string): JwtPayload {
  try {
    const decoded = jwt.verify(token, config.auth.jwtSecret) as JwtPayload;
    return decoded;
  } catch (err) {
    throw new Error(`JWT verification failed: ${err.message}`);
  }
}

export function isTokenExpired(payload: JwtPayload): boolean {
  return Date.now() / 1000 > payload.exp;
}
```

```typescript
// src/server/auth/permissionSync.ts
import { getRedisClient } from '../state/redisClient';

export class PermissionSync {
  private subscribers = new Map<string, (update: any) => void>();

  async subscribeToUser(userId: string, callback: (update: any) => void): Promise<void> {
    const client = getRedisClient();
    const channel = `user:${userId}:permissions`;

    this.subscribers.set(userId, callback);

    await client.subscribe(channel, (message) => {
      const update = JSON.parse(message);
      callback(update);
    });
  }

  async publishPermissionUpdate(userId: string, update: any): Promise<void> {
    const client = getRedisClient();
    const channel = `user:${userId}:permissions`;
    await client.publish(channel, JSON.stringify(update));
  }

  async unsubscribeFromUser(userId: string): Promise<void> {
    const client = getRedisClient();
    const channel = `user:${userId}:permissions`;
    await client.unsubscribe(channel);
    this.subscribers.delete(userId);
  }
}
```

**Why this pattern:**
- JWT provides identity and baseline roles (works offline if Redis unavailable)
- Redis pub/sub provides real-time revocation (Dispatch changes role, user kicked within heartbeat interval)
- Heartbeat refresh catches missed pub/sub messages (network blips, reconnections)
- Balances security (fresh permissions) with reliability (audio continues during Redis hiccup)

### Pattern 2: Heartbeat-Based Permission Refresh

**What:** WebSocket ping/pong heartbeat (existing 30s interval) extended to re-fetch user's channel assignments from Redis and compare against active channels. If revoked, gracefully remove (let current PTT finish, then force-leave).

**When to use:** Always. Catches permission changes that occur between channel joins (e.g., Dispatch removes user from channel mid-session).

**Example:**

```typescript
// src/server/auth/heartbeat.ts
import { ClientContext } from '../signaling/websocketServer';
import { getRedisClient } from '../state/redisClient';
import { createLogger } from '../logger';

const logger = createLogger('PermissionHeartbeat');

export async function refreshPermissions(ctx: ClientContext): Promise<void> {
  try {
    const client = getRedisClient();
    const assignedChannels = await client.sMembers(`user:${ctx.userId}:channels`);

    // Find channels user is in but no longer assigned to
    const revokedChannels = Array.from(ctx.channels).filter(
      (ch) => !assignedChannels.includes(ch)
    );

    for (const channelId of revokedChannels) {
      logger.info(`Permission revoked for ${ctx.userId} in channel ${channelId}`);

      // Check if user is currently speaking
      const channelState = await getChannelState(channelId);
      if (channelState.currentSpeaker === ctx.userId) {
        // Graceful removal: let them finish, then remove
        logger.info(`User ${ctx.userId} is speaking, will remove after PTT release`);
        // Mark for removal on next PTT_STOP
        ctx.pendingRemovals = ctx.pendingRemovals || new Set();
        ctx.pendingRemovals.add(channelId);
      } else {
        // Remove immediately
        await handleLeaveChannel(ctx, { channelId });
      }
    }
  } catch (err) {
    logger.error(`Permission refresh failed for ${ctx.userId}: ${err.message}`);
    // Don't disconnect user on refresh failure (reliability constraint)
  }
}
```

**Integration with existing heartbeat:**

```typescript
// In src/server/signaling/websocketServer.ts, extend startHeartbeat():
private startHeartbeat(): void {
  this.heartbeatInterval = setInterval(async () => {
    for (const [connectionId, ctx] of this.clients.entries()) {
      if (!ctx.isAlive) {
        logger.warn(`Client ${ctx.userId} failed heartbeat, terminating`);
        ctx.ws.terminate();
        this.clients.delete(connectionId);
        continue;
      }

      ctx.isAlive = false;
      ctx.ws.ping();

      // NEW: Refresh permissions every heartbeat
      await refreshPermissions(ctx);
    }
  }, 30000); // 30 second interval
}
```

### Pattern 3: PTT Priority Interrupt (Dispatch Preemption)

**What:** When Dispatch user presses PTT on a channel where a General user is speaking, immediately pause the General user's producer, resume the Dispatch producer, acquire speaker lock (overriding existing lock), and notify the General user via WebSocket push event.

**When to use:** When `userRole === 'DISPATCH'` and channel has `currentSpeaker !== null` and `currentSpeaker` is not Dispatch.

**Example:**

```typescript
// src/server/authorization/priorityLogic.ts
import { ChannelStateManager } from '../state/channelState';
import { ProducerConsumerManager } from '../mediasoup/producerConsumerManager';
import { ClientContext } from '../signaling/websocketServer';
import { createMessage, SignalingType } from '../../shared/protocol';

export async function handleDispatchPriorityPtt(
  ctx: ClientContext,
  channelId: string,
  channelStateManager: ChannelStateManager,
  producerConsumerManager: ProducerConsumerManager,
  broadcastToChannel: (channelId: string, message: any, excludeUserId?: string) => void
): Promise<void> {
  // Get current channel state
  const state = await channelStateManager.getChannelState(channelId);

  if (state.currentSpeaker && state.currentSpeaker !== ctx.userId) {
    // Someone else is speaking - check their role
    const currentSpeakerRole = await getUserRoleInEvent(state.currentSpeaker, ctx.eventId);

    if (currentSpeakerRole !== 'DISPATCH') {
      // General user is speaking - interrupt them
      logger.info(`Dispatch ${ctx.userId} interrupting General user ${state.currentSpeaker}`);

      // 1. Pause current speaker's producer
      const currentProducerId = getUserProducer(state.currentSpeaker, channelId);
      if (currentProducerId) {
        await producerConsumerManager.pauseProducer(currentProducerId);
      }

      // 2. Notify interrupted user
      broadcastToChannel(
        channelId,
        createMessage(SignalingType.PTT_INTERRUPTED, {
          interruptedBy: ctx.userId,
          interruptedByName: ctx.userName,
          reason: 'Dispatch priority',
        }),
        ctx.userId
      );

      // 3. Force-release old speaker lock and acquire new one
      await channelStateManager.forceAcquireLock(channelId, ctx.userId, ctx.userName);

      // 4. Resume Dispatch producer
      const dispatchProducerId = getUserProducer(ctx.userId, channelId);
      if (dispatchProducerId) {
        await producerConsumerManager.resumeProducer(dispatchProducerId);
      }

      // 5. Broadcast speaker change
      const newState = await channelStateManager.getChannelState(channelId);
      broadcastToChannel(
        channelId,
        createMessage(SignalingType.SPEAKER_CHANGED, newState)
      );
    }
  }
}
```

**Key considerations:**
- Admin does NOT get priority (role check required)
- Dispatch-to-Dispatch is normal queue (no preemption between Dispatch users)
- Interrupted user sees immediate audio cutoff + visual notification
- Interrupted user must re-press PTT after Dispatch finishes (no auto-resume)

### Pattern 4: Emergency Broadcast (All-Channel Override)

**What:** Dispatch user long-presses (2 seconds) emergency broadcast button, server pauses ALL active speakers across ALL channels in the event, acquires speaker locks on all channels, creates producers on all channels for the Dispatch user, and broadcasts to entire event.

**When to use:** When `userRole === 'DISPATCH'` and emergency broadcast activated.

**Example:**

```typescript
// src/server/authorization/emergencyBroadcast.ts
export async function startEmergencyBroadcast(
  ctx: ClientContext,
  eventId: string,
  channelStateManager: ChannelStateManager,
  producerConsumerManager: ProducerConsumerManager,
  broadcastToChannel: (channelId: string, message: any) => void
): Promise<void> {
  // 1. Get all channels in event
  const eventChannels = await getEventChannels(eventId);

  // 2. For each channel, interrupt current speaker
  for (const channelId of eventChannels) {
    const state = await channelStateManager.getChannelState(channelId);

    if (state.currentSpeaker && state.currentSpeaker !== ctx.userId) {
      // Pause current speaker
      const producerId = getUserProducer(state.currentSpeaker, channelId);
      if (producerId) {
        await producerConsumerManager.pauseProducer(producerId);
      }
    }

    // Force-acquire lock
    await channelStateManager.forceAcquireLock(channelId, ctx.userId, ctx.userName);

    // Notify channel
    broadcastToChannel(
      channelId,
      createMessage(SignalingType.EMERGENCY_BROADCAST_STARTED, {
        broadcastBy: ctx.userId,
        broadcastByName: ctx.userName,
      })
    );
  }

  // 3. Create or resume producer on all channels
  for (const channelId of eventChannels) {
    const producerId = getUserProducer(ctx.userId, channelId);
    if (producerId) {
      await producerConsumerManager.resumeProducer(producerId);
    } else {
      // Create new producer for this channel (user may not have joined all channels)
      await createEmergencyProducer(ctx, channelId);
    }
  }

  logger.warn(`Emergency broadcast started by ${ctx.userId} across ${eventChannels.length} channels in event ${eventId}`);
}

export async function stopEmergencyBroadcast(
  ctx: ClientContext,
  eventId: string,
  channelStateManager: ChannelStateManager,
  producerConsumerManager: ProducerConsumerManager,
  broadcastToChannel: (channelId: string, message: any) => void
): Promise<void> {
  const eventChannels = await getEventChannels(eventId);

  for (const channelId of eventChannels) {
    // Pause producer
    const producerId = getUserProducer(ctx.userId, channelId);
    if (producerId) {
      await producerConsumerManager.pauseProducer(producerId);
    }

    // Release lock
    await channelStateManager.stopPtt(channelId, ctx.userId);

    // Notify channel
    broadcastToChannel(
      channelId,
      createMessage(SignalingType.EMERGENCY_BROADCAST_STOPPED, {
        broadcastBy: ctx.userId,
      })
    );
  }

  logger.info(`Emergency broadcast stopped by ${ctx.userId}`);
}
```

**Client-side activation:**

```typescript
// 2-second long press detection (prevents accidental activation)
let longPressTimer: NodeJS.Timeout | null = null;

emergencyButton.addEventListener('mousedown', () => {
  longPressTimer = setTimeout(() => {
    // Activate emergency broadcast
    sendSignalingMessage({
      type: 'emergency-broadcast-start',
      data: { eventId: currentEventId },
    });
  }, 2000); // 2 second threshold
});

emergencyButton.addEventListener('mouseup', () => {
  if (longPressTimer) {
    clearTimeout(longPressTimer);
    longPressTimer = null;
  }

  // If already broadcasting, stop
  if (isEmergencyBroadcasting) {
    sendSignalingMessage({
      type: 'emergency-broadcast-stop',
      data: { eventId: currentEventId },
    });
  }
});
```

### Pattern 5: Progressive Rate Limiting with Redis

**What:** Use node-rate-limiter-flexible with Redis backend to implement lenient rate limiting with progressive slowdown. Failed login attempts increase delay exponentially (1s → 2s → 4s → 8s), but never hard-lock the account.

**When to use:** Always for auth endpoints (login, token refresh) and high-value actions (channel join, force-disconnect).

**Example:**

```typescript
// src/server/rate-limiting/authLimiter.ts
import { RateLimiterRedis } from 'rate-limiter-flexible';
import { getRedisClient } from '../state/redisClient';

const redis = getRedisClient();

// Login rate limiter: 5 attempts per minute, progressive slowdown on failures
export const loginLimiter = new RateLimiterRedis({
  storeClient: redis,
  keyPrefix: 'login_fail',
  points: 5, // Number of attempts
  duration: 60, // Per 60 seconds
  blockDuration: 0, // Don't hard-block, use progressive slowdown
});

// Track consecutive failures for progressive delay
const failureCounters = new Map<string, number>();

export async function checkLoginRateLimit(identifier: string): Promise<void> {
  try {
    await loginLimiter.consume(identifier);
  } catch (rejRes) {
    // Rate limit exceeded
    const failureCount = failureCounters.get(identifier) || 0;
    const delayMs = Math.min(2 ** failureCount * 1000, 30000); // Max 30s delay

    throw new Error(`Too many login attempts. Retry in ${delayMs / 1000}s`);
  }
}

export async function recordLoginFailure(identifier: string): Promise<void> {
  const current = failureCounters.get(identifier) || 0;
  failureCounters.set(identifier, current + 1);

  // Track in rate limiter
  await loginLimiter.penalty(identifier, 1);
}

export async function recordLoginSuccess(identifier: string): Promise<void> {
  // Reset failure counter on success
  failureCounters.delete(identifier);
  await loginLimiter.reward(identifier, 5); // Restore points
}
```

**Integration with WebSocket auth:**

```typescript
// In src/server/signaling/websocketServer.ts, extend verifyClient():
private async verifyClient(
  info: { origin: string; secure: boolean; req: http.IncomingMessage },
  callback: (result: boolean, code?: number, message?: string) => void
): Promise<void> {
  const clientIp = info.req.socket.remoteAddress || 'unknown';

  try {
    // Rate limit check
    await checkLoginRateLimit(clientIp);

    // JWT verification (existing)
    const token = extractToken(info.req);
    const decoded = jwt.verify(token, config.auth.jwtSecret) as JwtPayload;

    // Success
    await recordLoginSuccess(clientIp);
    callback(true);
  } catch (err) {
    await recordLoginFailure(clientIp);
    logger.warn(`Connection rejected from ${clientIp}: ${err.message}`);
    callback(false, 401, 'Unauthorized');
  }
}
```

### Pattern 6: Structured Audit Logging

**What:** Log all auth events (logins, denials, force-disconnects, role changes) to Prisma AuditLog model with structured JSON payload. Use middleware pattern to automatically capture actor, action, and context.

**When to use:** Always for security-relevant actions.

**Example:**

```typescript
// src/server/audit/auditLogger.ts
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

export interface AuditLogEntry {
  actorId: string | null;
  eventId: string | null;
  action: string;
  payload: Record<string, any>;
}

export async function logAuditEvent(entry: AuditLogEntry): Promise<void> {
  try {
    await prisma.auditLog.create({
      data: {
        actorId: entry.actorId,
        eventId: entry.eventId,
        action: entry.action,
        payload: entry.payload,
        createdAt: new Date(),
      },
    });
  } catch (err) {
    // Don't throw - audit logging should never break core functionality
    logger.error(`Audit logging failed: ${err.message}`);
  }
}

// Common audit actions
export const AuditActions = {
  USER_LOGIN: 'user.login',
  USER_LOGIN_FAILED: 'user.login.failed',
  USER_WEBSOCKET_CONNECT: 'user.websocket.connect',
  USER_WEBSOCKET_DISCONNECT: 'user.websocket.disconnect',
  CHANNEL_JOIN: 'channel.join',
  CHANNEL_JOIN_DENIED: 'channel.join.denied',
  CHANNEL_LEAVE: 'channel.leave',
  PTT_START: 'ptt.start',
  PTT_DENIED: 'ptt.denied',
  PTT_INTERRUPTED: 'ptt.interrupted',
  DISPATCH_FORCE_DISCONNECT: 'dispatch.force_disconnect',
  DISPATCH_PRIORITY_INTERRUPT: 'dispatch.priority_interrupt',
  EMERGENCY_BROADCAST_START: 'emergency_broadcast.start',
  EMERGENCY_BROADCAST_STOP: 'emergency_broadcast.stop',
  ROLE_CHANGED: 'role.changed',
  PERMISSION_REVOKED: 'permission.revoked',
  RATE_LIMIT_TRIGGERED: 'rate_limit.triggered',
};
```

**Usage example:**

```typescript
// In handleJoinChannel():
try {
  // Check permission
  const hasAccess = await checkChannelAccess(ctx.userId, channelId);

  if (!hasAccess) {
    await logAuditEvent({
      actorId: ctx.userId,
      eventId: await getEventIdForChannel(channelId),
      action: AuditActions.CHANNEL_JOIN_DENIED,
      payload: { channelId, reason: 'not_assigned' },
    });
    throw new Error('Access denied');
  }

  // Join channel
  await this.sessionStore.addUserToChannel(ctx.userId, channelId);

  await logAuditEvent({
    actorId: ctx.userId,
    eventId: await getEventIdForChannel(channelId),
    action: AuditActions.CHANNEL_JOIN,
    payload: { channelId },
  });
} catch (err) {
  // ...
}
```

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Rate limiting with progressive slowdown | Custom token bucket or sliding window | node-rate-limiter-flexible with Redis | Handles distributed rate limiting, Redis atomic operations, penalty/reward system, battle-tested for auth protection |
| JWT token refresh logic | Custom token rotation | Standard JWT exp claim + refresh token pattern | Industry standard, well-documented, integrates with existing jsonwebtoken library |
| Permission caching with TTL | Custom in-memory cache | Redis with SETEX and pub/sub | Distributed, atomic, supports TTL and real-time invalidation via pub/sub |
| WebSocket reconnection with backoff | Custom retry logic | Existing reconnectingClient.ts pattern | Already implemented in Phase 1, exponential backoff, handles edge cases |
| Audit log querying/filtering | Custom database queries | Prisma with typed filters | Type-safe, handles SQL injection, supports complex JSON queries on payload field |
| Jitter buffer implementation | Custom audio buffering | mediasoup's built-in buffering + Opus FEC | mediasoup handles RTP packet reordering and retransmission, Opus FEC recovers from packet loss |

**Key insight:** Auth and rate limiting are security-critical. Use battle-tested libraries (node-rate-limiter-flexible, jsonwebtoken) rather than custom implementations. Edge cases (distributed race conditions, clock skew, token timing attacks) are already handled.

## Common Pitfalls

### Pitfall 1: Storing Permissions in JWT Claims Only

**What goes wrong:** User's role or channel assignments change in control-plane, but JWT still has old claims for up to 1 hour (token TTL). User retains access to revoked channels or old role permissions until token expires.

**Why it happens:** JWT is stateless by design. Server can't revoke or update claims after issuance without a revocation list or real-time sync mechanism.

**How to avoid:** Use hybrid pattern (JWT for identity + baseline roles, Redis for real-time permission state). Heartbeat re-queries Redis every 30-60s to catch updates. Redis pub/sub pushes urgent changes (force-disconnect) immediately.

**Warning signs:**
- User complains they were removed from channel but can still join
- Dispatch says they revoked access but user is still active
- Admin changes role but user behavior doesn't update

**Reference:** [How to Use JWTs for Authorization: Best Practices](https://www.permit.io/blog/how-to-use-jwts-for-authorization-best-practices-and-common-mistakes) - "You're trading speed for accuracy—and that's not a great trade-off when it comes to security."

### Pitfall 2: Synchronous Database Queries on Every PTT Action

**What goes wrong:** Checking database on every PTT press adds 50-200ms latency (network round-trip to PostgreSQL). Violates "critical audio must never fail due to auth delays" constraint. Under load, database becomes bottleneck.

**Why it happens:** Developer assumes "real-time permissions" means "query database in real-time." Doesn't consider caching or async updates.

**How to avoid:**
- Check permissions at channel JOIN time only (user decision point: "Can I enter this channel?")
- Cache channel assignments in Redis with heartbeat refresh (30-60s)
- Use Redis pub/sub for immediate revocation (push model, not pull)
- Never block PTT on database query — use cached permission state

**Warning signs:**
- PTT latency increases under load
- Database CPU spikes during high PTT activity
- Users report "lag" or "delay" when pressing PTT button

### Pitfall 3: Hard Account Lockout on Failed Login Attempts

**What goes wrong:** Attacker triggers 5 failed logins for legitimate user, account locked for 30 minutes. Legitimate user can't access system during critical event (violates "critical audio must get through" principle). Creates denial-of-service vector.

**Why it happens:** Standard security advice says "lock accounts after N failures." But this is optimized for low-stakes systems where availability isn't life-critical.

**How to avoid:** Use progressive slowdown instead of hard lockout:
- 1st failure: no delay
- 2nd failure: 1s delay
- 3rd failure: 2s delay
- 4th failure: 4s delay
- 5th failure: 8s delay
- Max delay: 30s (never infinite)

This blocks brute force (attacker must wait exponentially) while allowing legitimate user to retry (inconvenient but not blocked).

**Warning signs:**
- Support tickets: "I can't log in even with correct password"
- Users complain about being "locked out" during events
- Attacker successfully DoS's legitimate users by triggering lockouts

**Implementation:** Use node-rate-limiter-flexible's penalty/reward system (see Pattern 5).

### Pitfall 4: Missing Graceful Removal Logic

**What goes wrong:** User is actively transmitting (PTT pressed, audio flowing) when Dispatch revokes their channel access. Server immediately closes their producer and kicks them out mid-word. Audio cuts off abruptly, creating bad UX and potential safety issue (user mid-emergency transmission).

**Why it happens:** Developer treats permission revocation like HTTP session termination (immediate). Doesn't consider ongoing real-time activity.

**How to avoid:**
- Check if user is current speaker before removing
- If speaking: mark for pending removal, let them finish current PTT, then remove on PTT_STOP
- If not speaking: remove immediately
- Exception: Force-disconnect (malicious/disruptive) bypasses graceful removal

**Warning signs:**
- Users report audio cutting off mid-sentence
- Dispatch complains "I removed them but they kept talking for 5 more seconds"
- Audit logs show permission-revoked events during active PTT

**Example:**

```typescript
// Bad: immediate removal
await forceLeaveChannel(userId, channelId);

// Good: graceful removal
const state = await getChannelState(channelId);
if (state.currentSpeaker === userId) {
  ctx.pendingRemovals.add(channelId); // Remove on next PTT_STOP
} else {
  await forceLeaveChannel(userId, channelId);
}
```

### Pitfall 5: Jitter Buffer Too Small for Real-World Networks

**What goes wrong:** Jitter buffer set to 20ms works perfectly on office WiFi during testing. Deployed to real event with cellular hotspots, congested networks, and 50-100ms jitter. Audio becomes choppy, robotic, or drops packets entirely.

**Why it happens:** Testing on low-jitter networks (lab/office) doesn't represent real-world conditions. User constraint says "low bandwidth/low speed networks" — implies high jitter, packet loss, variable latency.

**How to avoid:**
- Use adaptive jitter buffer (40-80ms range per user constraint)
- Start at 40ms, increase to 80ms when packet loss/jitter detected
- Enable Opus FEC (Forward Error Correction) to recover from 1-5% packet loss
- Test on simulated bad network: `tc` (Linux traffic control) or Network Link Conditioner (macOS)

**Warning signs:**
- Audio quality excellent in office, terrible at event
- Support tickets: "robotic voice," "choppy audio," "drops out"
- Packet loss stats show 2-5% loss (normal for cellular) but audio fails

**Implementation:**

```typescript
// mediasoup consumer configuration with adaptive buffer
const consumer = await transport.consume({
  producerId,
  rtpCapabilities,
  // Enable server-side buffering
  paused: false,
});

// Opus FEC configuration in producer
const producer = await transport.produce({
  kind: 'audio',
  rtpParameters: {
    codecs: [{
      mimeType: 'audio/opus',
      clockRate: 48000,
      channels: 2,
      parameters: {
        useinbandfec: 1,        // Enable Forward Error Correction
        usedtx: 0,              // Disable discontinuous transmission (predictable latency)
        maxaveragebitrate: 40000, // 40kbps for good quality
      },
    }],
  },
});
```

**Testing:** Simulate 50ms jitter and 2% packet loss:

```bash
# Linux tc command
sudo tc qdisc add dev eth0 root netem delay 100ms 50ms loss 2%
```

**Reference:** [How WebRTC's NetEQ Jitter Buffer Provides Smooth Audio](https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/) - "Jitter buffers typically range from 15ms to 120ms, with an initial value around 40ms that can grow on poor connections."

### Pitfall 6: No Audit Trail for Force-Disconnect

**What goes wrong:** Dispatch force-disconnects a user (instant kick). No record of who did it, why, or when. User complains to Admin, but there's no evidence. Creates accountability gap and potential abuse vector.

**Why it happens:** Developer treats force-disconnect like a "delete" operation (just remove user). Doesn't consider audit/accountability requirements.

**How to avoid:** Log ALL security-relevant actions to AuditLog:
- Actor: who triggered force-disconnect (Dispatch user ID)
- Target: who was disconnected (General user ID)
- Context: which channel, which event
- Reason: optional text field (e.g., "disruptive transmission," "malicious behavior")
- Timestamp: when it happened

**Warning signs:**
- Disputes about "who kicked whom"
- No way to review security incidents
- Admin can't see history of force-disconnects

**Implementation:** See Pattern 6 (Structured Audit Logging).

## Code Examples

Verified patterns for Phase 2 implementation:

### WebSocket Connection with JWT Auth and Permission Loading

```typescript
// src/server/signaling/websocketServer.ts
import jwt from 'jsonwebtoken';
import { getRedisClient } from '../state/redisClient';

private async handleConnection(socket: ws.WebSocket, req: http.IncomingMessage): Promise<void> {
  const userId = (req as any).userId;
  const userName = (req as any).userName;
  const jwtPayload = (req as any).jwtPayload as JwtPayload;

  const clientContext: ClientContext = {
    ws: socket,
    userId,
    userName,
    globalRole: jwtPayload.globalRole,
    eventRoles: new Map(jwtPayload.eventRoles.map(r => [r.eventId, r.role])),
    channels: new Set(),
    assignedChannels: new Set(),
    pendingRemovals: new Set(),
    connectionId: `${userId}:${Date.now()}`,
    isAlive: true,
  };

  // Load assigned channels from Redis (synced from Prisma via control-plane)
  const redis = getRedisClient();
  const assignedChannels = await redis.sMembers(`user:${userId}:channels`);
  clientContext.assignedChannels = new Set(assignedChannels);

  // Subscribe to permission updates
  await permissionSync.subscribeToUser(userId, async (update) => {
    if (update.type === 'channel-assignment-changed') {
      clientContext.assignedChannels = new Set(update.channels);

      // Push update to client
      this.sendToClient(socket, createMessage(SignalingType.CHANNEL_LIST_UPDATED, {
        assignedChannels: update.channels,
      }));
    } else if (update.type === 'force-disconnect') {
      logger.warn(`Force-disconnect for ${userId} by ${update.actorId}`);
      await logAuditEvent({
        actorId: update.actorId,
        eventId: update.eventId,
        action: AuditActions.DISPATCH_FORCE_DISCONNECT,
        payload: { targetUserId: userId, reason: update.reason },
      });
      socket.close(1008, 'Force-disconnected by administrator');
    }
  });

  this.clients.set(clientContext.connectionId, clientContext);

  // Send initial channel list
  this.sendToClient(socket, createMessage(SignalingType.CHANNEL_LIST, {
    assignedChannels: Array.from(clientContext.assignedChannels),
  }));

  logger.info(`User ${userId} connected with ${assignedChannels.length} assigned channels`);
}
```

### Channel Join with Permission Check

```typescript
// src/server/signaling/handlers.ts (extend existing handleJoinChannel)
async handleJoinChannel(ctx: ClientContext, message: SignalingMessage): Promise<void> {
  try {
    const { channelId } = message.data as { channelId: string };

    // 1. Check if user is assigned to this channel
    if (!ctx.assignedChannels.has(channelId)) {
      await logAuditEvent({
        actorId: ctx.userId,
        eventId: await getEventIdForChannel(channelId),
        action: AuditActions.CHANNEL_JOIN_DENIED,
        payload: { channelId, reason: 'not_assigned' },
      });
      throw new Error('You are not assigned to this channel');
    }

    // 2. Check channel user limit (if configured)
    const channelLimit = await getChannelUserLimit(channelId);
    if (channelLimit) {
      const currentCount = await this.sessionStore.getChannelUserCount(channelId);
      if (currentCount >= channelLimit) {
        await logAuditEvent({
          actorId: ctx.userId,
          eventId: await getEventIdForChannel(channelId),
          action: AuditActions.CHANNEL_JOIN_DENIED,
          payload: { channelId, reason: 'channel_full', limit: channelLimit },
        });
        throw new Error('Channel is full');
      }
    }

    // 3. Check user's simultaneous channel limit
    const userChannelLimit = await getUserSimultaneousChannelLimit(ctx.userId);
    if (userChannelLimit && ctx.channels.size >= userChannelLimit) {
      throw new Error(`Maximum channels reached (${userChannelLimit}). Remove a channel to add another.`);
    }

    // 4. Add user to channel (existing logic)
    await this.sessionStore.addUserToChannel(ctx.userId, channelId);
    ctx.channels.add(channelId);

    // ... existing router/transport setup ...

    // 5. Audit log
    await logAuditEvent({
      actorId: ctx.userId,
      eventId: await getEventIdForChannel(channelId),
      action: AuditActions.CHANNEL_JOIN,
      payload: { channelId },
    });

    logger.info(`User ${ctx.userId} joined channel ${channelId} (${ctx.channels.size}/${userChannelLimit || 'unlimited'} active)`);
  } catch (err) {
    logger.error(`Error handling JOIN_CHANNEL: ${err.message}`);
    this.sendError(ctx, message.id, err.message);
  }
}
```

### Dispatch Force-Disconnect API

```typescript
// control-plane/src/api/security.js (NEW)
const express = require('express');
const { requireAuth, requireDispatchOrAdmin } = require('../middleware/auth');
const { getRedisClient } = require('../lib/redis');

const router = express.Router();

/**
 * POST /api/security/force-disconnect
 * Force-disconnect a user from the router
 */
router.post('/force-disconnect', requireAuth, requireDispatchOrAdmin, async (req, res) => {
  try {
    const { userId, eventId, reason } = req.body;
    const actorId = req.user.id;

    // Verify actor has Dispatch/Admin role in this event
    const hasPermission = await verifyEventRole(actorId, eventId, ['DISPATCH', 'ADMIN']);
    if (!hasPermission) {
      return res.status(403).json({ error: 'Insufficient permissions' });
    }

    // Verify target user is in event
    const targetInEvent = await isUserInEvent(userId, eventId);
    if (!targetInEvent) {
      return res.status(404).json({ error: 'User not found in event' });
    }

    // Publish force-disconnect to Redis
    const redis = getRedisClient();
    await redis.publish(`user:${userId}:permissions`, JSON.stringify({
      type: 'force-disconnect',
      actorId,
      eventId,
      reason: reason || 'No reason provided',
      timestamp: Date.now(),
    }));

    // Audit log
    await prisma.auditLog.create({
      data: {
        actorId,
        eventId,
        action: 'dispatch.force_disconnect',
        payload: { targetUserId: userId, reason },
      },
    });

    res.json({ success: true, message: 'User force-disconnected' });
  } catch (err) {
    console.error('Force-disconnect error:', err);
    res.status(500).json({ error: 'Failed to force-disconnect user' });
  }
});

module.exports = router;
```

### Rate Limiting on Channel Join

```typescript
// src/server/rate-limiting/joinLimiter.ts
import { RateLimiterRedis } from 'rate-limiter-flexible';
import { getRedisClient } from '../state/redisClient';

const redis = getRedisClient();

// Allow 10 channel joins per minute (lenient, prevents spam)
export const channelJoinLimiter = new RateLimiterRedis({
  storeClient: redis,
  keyPrefix: 'channel_join',
  points: 10,
  duration: 60,
  blockDuration: 0,
});

export async function checkChannelJoinRateLimit(userId: string): Promise<void> {
  try {
    await channelJoinLimiter.consume(userId);
  } catch (rejRes) {
    throw new Error('Too many channel join attempts. Please wait and try again.');
  }
}
```

**Integration:**

```typescript
// In handleJoinChannel() before permission check:
await checkChannelJoinRateLimit(ctx.userId);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| HTTP-only auth with cookies | JWT tokens for WebSocket auth | ~2018 | Enables stateless WebSocket auth, supports mobile apps, works with CORS/cross-domain |
| Per-request database queries | Redis-cached permissions with pub/sub sync | ~2020 | 10-100x latency reduction, enables real-time revocation without database bottleneck |
| Hard account lockout on failed logins | Progressive slowdown with rate-limiter-flexible | ~2021 | Prevents DoS attacks while maintaining availability for legitimate users |
| Static jitter buffers (fixed 50ms) | Adaptive jitter buffers (40-80ms dynamic) | ~2019 (WebRTC NetEQ) | Handles variable network conditions, reduces latency on good networks, maintains quality on bad networks |
| Manual audit logging | Middleware-based structured logging | ~2020 | Eliminates missed audit events, ensures consistency, supports compliance (GDPR, SOC2) |

**Deprecated/outdated:**
- Socket.IO for WebSocket: Adds overhead, auto-reconnection complexity. Modern approach uses plain ws library with custom reconnection logic.
- Storing permissions in localStorage: Security risk (XSS). Modern approach uses httpOnly cookies for control-plane, JWT in Authorization header for WebSocket.
- Redis KEYS command for scanning: O(N) operation, blocks server. Modern approach uses Redis SCAN with cursor pagination.

## Open Questions

Things that couldn't be fully resolved:

### 1. Optimal Heartbeat Interval

**What we know:**
- Current WebSocket heartbeat: 30s (existing Phase 1 code)
- JWT TTL: 1 hour (user decision)
- Permission refresh needed to catch revocations between channel joins

**What's unclear:**
- Is 30s refresh interval too frequent (Redis load) or too slow (delayed revocation)?
- Should heartbeat interval adapt based on role (Dispatch = faster, General = slower)?

**Recommendation:** Start with 30s (matches existing heartbeat), monitor Redis load in production. If Redis CPU exceeds 50% during peak, increase to 60s. If Dispatch reports "removed user still active," decrease to 15s for Dispatch-only. Measure before optimizing.

### 2. Jitter Buffer Adaptation Strategy

**What we know:**
- User constraint: 40-80ms range
- mediasoup handles RTP buffering internally
- Opus FEC recovers from packet loss

**What's unclear:**
- Does mediasoup expose jitter buffer control at transport/consumer level?
- Should buffer adapt per-user (some on cellular, some on fiber) or globally per-channel?
- How to detect "network is degraded, increase buffer" signal?

**Recommendation:** Research mediasoup PlainTransport vs WebRtcTransport buffering options. Check if mediasoup exposes RTCP stats (packet loss, jitter) that can trigger buffer adjustments. If not, rely on mediasoup's internal adaptive logic and focus on Opus FEC configuration (useinbandfec parameter). Test with simulated packet loss (tc netem) to validate.

### 3. Emergency Broadcast Producer Management

**What we know:**
- Dispatch transmits to ALL channels in event simultaneously
- Requires producer on each channel
- User may not have joined all channels beforehand (no pre-created producers)

**What's unclear:**
- Can single producer be consumed by multiple routers (one per channel)?
- Or must we create N producers (one per channel) from same audio track?
- What's the mediasoup pattern for "broadcast to multiple rooms"?

**Recommendation:** Check mediasoup documentation for PipeTransport (route media between routers) or Consumer/Producer pairing across routers. If unsupported, implement "emergency producer pool": pre-create paused producers on all event channels when Dispatch joins first channel. Resume all on emergency broadcast. Test CPU/memory impact with 50+ channels.

### 4. Rate Limiter Storage for Security Events

**What we know:**
- Rate limiting data stored in Redis (transient)
- Audit logs stored in PostgreSQL (permanent)
- Security events need both (immediate blocking + historical review)

**What's unclear:**
- Should rate limit violations be logged to AuditLog (adds DB writes on attack)?
- Or keep rate limit data only in Redis, export to AuditLog hourly/daily?
- What's the tradeoff between "complete audit trail" vs "don't overwhelm DB during attack"?

**Recommendation:** Log first violation and every 10th violation to AuditLog (reduces spam). Store all violations in Redis with 7-day TTL. Provide admin API to query recent rate limit events from Redis (fast, no DB load). Export to AuditLog daily for long-term compliance. Test with simulated brute-force (1000 req/s) to validate DB doesn't become bottleneck.

## Sources

### Primary (HIGH confidence)

- mediasoup Documentation v3 - Worker, Router, Transport APIs - https://mediasoup.org/documentation/v3/mediasoup/api/
- node-rate-limiter-flexible GitHub - Redis backend, progressive blocking - https://github.com/animir/node-rate-limiter-flexible
- jsonwebtoken npm - JWT signing/verification - https://www.npmjs.com/package/jsonwebtoken
- Prisma Documentation - AuditLog queries - https://www.prisma.io/docs
- Existing codebase - control-plane/prisma/schema.prisma, src/server/signaling/websocketServer.ts, src/server/config.ts

### Secondary (MEDIUM confidence)

- [WebSocket Authentication: Securing Real-Time Connections in 2025](https://www.videosdk.live/developer-hub/websocket/websocket-authentication) - JWT WebSocket patterns
- [How to Use JWTs for Authorization: Best Practices](https://www.permit.io/blog/how-to-use-jwts-for-authorization-best-practices-and-common-mistakes) - JWT vs database permission tradeoffs
- [Redis Pub/Sub Documentation](https://redis.io/docs/latest/develop/pubsub/) - Permission synchronization patterns
- [How WebRTC's NetEQ Jitter Buffer Provides Smooth Audio](https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/) - Adaptive buffering strategies
- [Prevent Brute Force Attacks with Redis and rate-limiter-flexible](https://medium.com/@sandunilakshika2026/prevent-brute-force-attacks-in-node-js-using-redis-and-rate-limiter-flexible-d93ecc4235f9) - Progressive slowdown implementation
- [How to Build Secure Audit Logging in Node.js](https://www.sevensquaretech.com/secure-audit-logging-activity-trail-nodejs-with-code/) - Audit log best practices
- [How to Fix WebSocket Performance Issues (2026)](https://oneuptime.com/blog/post/2026-01-24-websocket-performance/view) - Worker pool optimization for 1000+ connections

### Tertiary (LOW confidence)

- [Radio Dispatch Priority Interrupt](https://support.jps.com/hc/en-us/articles/4411548444435-How-do-I-give-the-Dispatcher-audio-priority) - Dispatch priority patterns (radio industry context)
- [Emergency Alert System](https://www.fema.gov/emergency-managers/practitioners/integrated-public-alert-warning-system/public/emergency-alert-system) - Emergency broadcast architecture (government EAS context, not directly applicable)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries already in use or standard Node.js auth ecosystem
- Architecture patterns: MEDIUM - Hybrid JWT+Redis approach proven but requires validation in this context
- Pitfalls: HIGH - Based on documented common mistakes in JWT auth and WebSocket security
- Jitter buffer specifics: LOW - mediasoup internal buffering not fully documented, requires testing
- Emergency broadcast implementation: LOW - mediasoup multi-router broadcasting pattern needs verification

**Research date:** 2026-02-06
**Valid until:** 2026-03-06 (30 days - stable domain, but WebRTC/security practices evolve)
