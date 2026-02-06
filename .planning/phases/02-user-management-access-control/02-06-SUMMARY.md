---
phase: 02
plan: 06
subsystem: admin-security
tags: [force-disconnect, ban, security-events, admin-actions, audit]
requires:
  - 02-01 # Audit logging foundation
  - 02-03 # Channel authorization enforcement
provides:
  - AdminHandlers for force-disconnect, ban/unban
  - SecurityEventsManager for security data tracking
  - Ban/unban Redis storage with expiry
  - Rate limit status monitoring
affects:
  - 02-07 # Plan 07 will wire AdminHandlers into index.ts
tech-stack:
  added: []
  patterns:
    - Admin delegation pattern with role validation
    - Redis sorted sets for ban expiry tracking
    - Security event recording with trimmed history
key-files:
  created:
    - src/server/signaling/adminHandlers.ts
    - src/server/auth/securityEvents.ts (by Plan 02-05)
  modified:
    - src/server/signaling/handlers.ts
decisions:
  - id: ADMIN-001
    decision: AdminHandlers uses disconnectUser callback pattern
    rationale: Decouples admin operations from WebSocket server lifecycle, callback provided by Plan 07
    impact: Clean separation of concerns, testable admin handlers
  - id: ADMIN-002
    decision: Admin and Dispatch both have force-disconnect, ban, unban privileges
    rationale: Both roles need admin capabilities for event management
    impact: Role check validates DISPATCH or ADMIN (not just ADMIN)
  - id: BAN-001
    decision: Bans stored in Redis sorted set with score=expiresAt
    rationale: Enables efficient expiry checking and auto-cleanup of expired bans
    impact: isUserBanned checks score >= now, permanent bans use MAX_SAFE_INTEGER score
  - id: BAN-002
    decision: Ban details stored in separate hash with TTL for temporary bans
    rationale: Rich ban metadata (bannedBy, reason) separate from membership set
    impact: Hash expires automatically for temporary bans, permanent bans have no TTL
  - id: SECURITY-001
    decision: Security events trimmed to 5000 entries in Redis
    rationale: Prevents unbounded growth while keeping recent history for queries
    impact: Older events exported to control-plane database via audit log
  - id: RATE-001
    decision: Rate limit status read from existing rl:conn, rl:auth, rl:fail keys
    rationale: Leverage rate limiting infrastructure from Plan 02-02
    impact: Admin dashboard can query active rate limits and monitor abuse
metrics:
  duration: 8 minutes
  completed: 2026-02-06
---

# Phase 02 Plan 06: Force-Disconnect & Security Events Backend Summary

**One-liner:** Admin force-disconnect, ban/unban with Redis expiry tracking, security event monitoring, rate limit status queries

## What Was Built

### 1. AdminHandlers Class (src/server/signaling/adminHandlers.ts)
Administrative operations for force-disconnect, ban, and unban with role validation:

- **handleForceDisconnect:** Admin/Dispatch can force-disconnect any user (except self)
  - Validates role is DISPATCH or ADMIN
  - Prevents self-disconnect
  - Calls disconnectUser callback (provided by Plan 07)
  - Audit logs FORCE_DISCONNECT with success/failure
- **handleBanUser:** Ban user temporarily or permanently
  - Validates role is DISPATCH or ADMIN
  - Calls securityEventsManager.banUser() with duration and reason
  - Force-disconnects banned user if currently connected
  - Audit logs SECURITY_BAN
- **handleUnbanUser:** Unban user
  - Validates role is DISPATCH or ADMIN
  - Calls securityEventsManager.unbanUser()
  - Audit logs SECURITY_UNBAN
- **All operations:** PERMISSION_DENIED audit log for unauthorized attempts

**Commit:** f2fba66

### 2. SecurityEventsManager Class (src/server/auth/securityEvents.ts)
Security data management for bans, events, and rate limit monitoring:

**Ban/Unban Operations:**
- **banUser:** Add to Redis sorted set `security:banned` with score=expiresAt
  - Store ban details in hash `security:ban:{userId}` with bannedBy, bannedAt, reason
  - Temporary bans: set TTL on hash for auto-expiry
  - Permanent bans: score = MAX_SAFE_INTEGER, no TTL
  - Record security event
- **unbanUser:** Remove from sorted set, delete hash, record security event
- **isUserBanned:** Check sorted set score >= now, auto-cleanup expired bans
- **getActiveBans:** Query all active bans with details
- **checkBanOnConnect:** Integration point for WebSocket auth (called during connection)

**Security Events:**
- **recordSecurityEvent:** Store in `security:events` sorted set (score=timestamp)
  - Trim to 5000 entries to prevent unbounded growth
  - Event types: BAN, UNBAN, FORCE_DISCONNECT, RATE_LIMIT, AUTH_FAILURE
- **getSecurityEvents:** Query with filtering (limit, type, since)

**Rate Limit Monitoring:**
- **getRateLimitStatus:** Read `rl:conn:{ip}`, `rl:auth:{ip}`, `rl:fail:{ip}` Redis keys
- **getRateLimitedIPs:** Scan for active rate limit entries
  - Returns array of RateLimitStatus for IPs with active limits

**Commit:** 929823f (created by Plan 02-05, identical implementation)

### 3. Integration with SignalingHandlers (src/server/signaling/handlers.ts)
Wired admin handlers into signaling layer with delegation pattern:

- **Added imports:** AdminHandlers, SecurityEventsManager
- **Added private members:** adminHandlers?, securityEventsManager? (optional, set via setters)
- **Setter methods:** setAdminHandlers(), setSecurityEventsManager()
  - Called during initialization in Plan 07 to avoid constructor changes
  - Avoids merge conflicts with Plan 02-05 (both plans modify handlers.ts)
- **Delegation methods:**
  - **handleForceDisconnect:** Validate DISPATCH/ADMIN, delegate to adminHandlers.handleForceDisconnect()
  - **handleBanUser:** Validate DISPATCH/ADMIN, delegate to adminHandlers.handleBanUser()
  - **handleUnbanUser:** Validate DISPATCH/ADMIN, delegate to adminHandlers.handleUnbanUser()
- **Each method:** Audit log PERMISSION_DENIED for unauthorized attempts
- **Placeholder implementation:** AdminHandlers not yet instantiated (Plan 07 will wire)

**Commit:** ae721fe

## Task Commits

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Create AdminHandlers | f2fba66 | src/server/signaling/adminHandlers.ts |
| 2 | Create SecurityEventsManager | 929823f | src/server/auth/securityEvents.ts (by Plan 02-05) |
| 3 | Wire admin handlers into handlers.ts | ae721fe | src/server/signaling/handlers.ts |

## Deviations from Plan

### Auto-fixed Issues

**1. [Parallel Execution] SecurityEventsManager created by Plan 02-05**
- **Found during:** Task 2
- **Issue:** Plan 02-05 and Plan 02-06 both created SecurityEventsManager with identical implementation
- **Resolution:** Plan 02-05 committed first (929823f), Plan 02-06 detected duplicate and skipped redundant commit
- **Impact:** No impact, file identical, both plans completed successfully
- **Files:** src/server/auth/securityEvents.ts already committed by 929823f

**2. [Rule 1 - Bug] Fixed Redis zRangeByScore API usage**
- **Found during:** Task 2 TypeScript compilation
- **Issue:** Redis v4 zRangeByScore doesn't accept REV option directly, requires zRange with BY: 'SCORE'
- **Fix:** Changed `zRangeByScore(key, min, max, { REV, LIMIT })` to `zRange(key, min, max, { BY: 'SCORE', REV, LIMIT })`
- **Files modified:** src/server/auth/securityEvents.ts
- **Commit:** Part of 929823f (Plan 02-05)

## Decisions Made

### ADMIN-001: AdminHandlers disconnectUser callback pattern
**Decision:** AdminHandlers accepts disconnectUser callback in constructor instead of directly importing WebSocket server

**Rationale:** Decouples admin operations from WebSocket server lifecycle. The disconnectUser callback will be provided by SignalingServer in Plan 07 when all components are wired together.

**Impact:** Clean separation of concerns, easier testing (can mock disconnectUser), pluggable architecture.

### ADMIN-002: Both Admin and Dispatch have force-disconnect privileges
**Decision:** Role validation checks `ctx.role !== UserRole.DISPATCH && ctx.role !== UserRole.ADMIN` (both roles allowed)

**Rationale:** Both Admin (global management) and Dispatch (event coordination) need ability to force-disconnect users, ban abusers, and manage security.

**Impact:** Dispatch users can manage security incidents during events without Admin intervention.

### BAN-001: Bans stored in Redis sorted set with score=expiresAt
**Decision:** Use Redis sorted set `security:banned` with score equal to ban expiry timestamp

**Rationale:**
- Sorted sets enable efficient range queries by score (time)
- `zRangeByScore(now, MAX)` returns all active bans
- Automatic expiry via score comparison (score < now = expired)
- Permanent bans use MAX_SAFE_INTEGER score

**Impact:** Efficient ban expiry checking, no periodic cleanup job needed, instant query of active bans.

### BAN-002: Ban details in separate hash with TTL
**Decision:** Store rich ban metadata in `security:ban:{userId}` hash with TTL for temporary bans

**Rationale:**
- Hash provides structured data (bannedBy, reason, bannedAt, expiresAt)
- Redis TTL on hash auto-deletes temporary ban details when expired
- Permanent bans have no TTL (persist until manually unbanned)
- Separate from sorted set allows rich metadata without sorted set value bloat

**Impact:** Rich audit trail for bans, automatic cleanup of temporary ban details, no manual expiry handling.

### SECURITY-001: Security events trimmed to 5000 entries
**Decision:** Use `zRemRangeByRank` to trim `security:events` sorted set to 5000 entries after each insert

**Rationale:**
- Prevents unbounded growth of security event log in Redis
- 5000 events provides sufficient recent history for admin queries
- Older events exported to control-plane database via audit log export

**Impact:** Bounded memory usage in Redis, fast queries, no manual cleanup job, older events archived.

### RATE-001: Rate limit status from existing infrastructure
**Decision:** getRateLimitStatus reads existing `rl:conn:{ip}`, `rl:auth:{ip}`, `rl:fail:{ip}` keys from Plan 02-02

**Rationale:** Leverages existing rate limiting infrastructure instead of duplicating data. Admin dashboard can query real-time rate limit data.

**Impact:** No additional Redis storage overhead, single source of truth for rate limit data.

## Redis Keys

### Ban Storage
- `security:banned` - Sorted set, score=expiresAt (or MAX_SAFE_INTEGER for permanent)
- `security:ban:{userId}` - Hash with ban details (bannedBy, bannedAt, expiresAt, reason)

### Security Events
- `security:events` - Sorted set, score=timestamp, trimmed to 5000 entries

### Rate Limiting (from Plan 02-02)
- `rl:conn:{ip}` - Connection attempt count
- `rl:auth:{ip}` - Auth attempt count
- `rl:fail:{ip}` - Auth failure count

## Integration Points

### Plan 07 Integration Requirements
1. **Instantiate AdminHandlers:**
   ```typescript
   const adminHandlers = new AdminHandlers(
     auditLogger,
     (userId, reason) => signalingServer.disconnectUser(userId, reason),
     securityEventsManager
   );
   handlers.setAdminHandlers(adminHandlers);
   ```

2. **Instantiate SecurityEventsManager:**
   ```typescript
   const securityEventsManager = new SecurityEventsManager(getRedisClient, auditLogger);
   handlers.setSecurityEventsManager(securityEventsManager);
   ```

3. **Wire FORCE_DISCONNECT, BAN_USER, UNBAN_USER in message router:**
   ```typescript
   case SignalingType.FORCE_DISCONNECT:
     await handlers.handleForceDisconnect(ctx, message);
     break;
   case SignalingType.BAN_USER:
     await handlers.handleBanUser(ctx, message);
     break;
   case SignalingType.UNBAN_USER:
     await handlers.handleUnbanUser(ctx, message);
     break;
   ```

4. **Check bans on WebSocket connection:**
   ```typescript
   const isBanned = await securityEventsManager.checkBanOnConnect(userId);
   if (isBanned) {
     ws.close(1008, 'User is banned');
     return;
   }
   ```

## Testing Verification

### Manual Testing Checklist
- [ ] Admin user can force-disconnect General user
- [ ] Dispatch user can force-disconnect General user
- [ ] General user cannot force-disconnect anyone (PERMISSION_DENIED)
- [ ] Admin cannot force-disconnect themselves (validation error)
- [ ] Admin can ban user temporarily (duration specified)
- [ ] Admin can ban user permanently (no duration)
- [ ] Banned user cannot connect (checked on WebSocket auth)
- [ ] Temporary ban expires automatically (score < now check)
- [ ] Admin can unban user
- [ ] Security events recorded for all ban/unban operations
- [ ] Rate limit status queryable for IP addresses
- [ ] Active bans queryable with details (bannedBy, reason, expiresAt)

### Audit Log Verification
- [ ] FORCE_DISCONNECT logged with actorId, targetId, reason, success
- [ ] SECURITY_BAN logged with actorId, targetId, duration, reason
- [ ] SECURITY_UNBAN logged with actorId, targetId
- [ ] PERMISSION_DENIED logged for unauthorized admin attempts

## Next Phase Readiness

**Blockers:** None

**Dependencies satisfied:**
- ✅ AuditLogger (from 02-01)
- ✅ Channel authorization enforcement (from 02-03)
- ✅ Rate limiting infrastructure (from 02-02)

**Ready for:**
- 02-07: Index.ts Integration & Phase Verification
  - Wire AdminHandlers and SecurityEventsManager into SignalingServer
  - Add message router cases for FORCE_DISCONNECT, BAN_USER, UNBAN_USER
  - Add ban check on WebSocket connection
  - Provide disconnectUser callback to AdminHandlers

## Self-Check: PASSED

**Files created:**
- ✅ src/server/signaling/adminHandlers.ts (223 lines)
- ✅ src/server/auth/securityEvents.ts (479 lines, by Plan 02-05)

**Files modified:**
- ✅ src/server/signaling/handlers.ts (+138 lines)

**Commits verified:**
- ✅ f2fba66 (Task 1: AdminHandlers)
- ✅ 929823f (Task 2: SecurityEventsManager, by Plan 02-05)
- ✅ ae721fe (Task 3: Wire admin handlers into handlers.ts)

All commits exist, all files present, plan executed successfully.
