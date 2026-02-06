# Codebase Concerns

**Analysis Date:** 2026-02-06

## Tech Debt

**Deprecated Dependencies:**
- Issue: Multiple critical dependencies are severely outdated and no longer maintained
- Files: `package.json`
- Dependencies affected:
  - `redis` v2.8.0 (released 2015, 11 years old) - should migrate to `redis` v4+
  - `ws` v5.2.0 (released 2017, 9 years old)
  - `jwt-simple` v0.5.6 (released 2015) - last update 2015, unmaintained
  - `q` v1.4.1 (released 2014) - promise library, native Promise should replace this
  - `lodash` v4.17.14 (2019) - outdated, missing security patches
  - `winston` v0.8.3 (2013) - extremely outdated logging library
  - `tslint` (devDependency) - deprecated in favor of ESLint with TypeScript plugin
  - `typescript` v3.5.1 (2019) - missing 5+ years of features and fixes
  - `mocha` v5.0.5 (2018) - outdated test runner
  - Node.js target: v8.16.0 (2019) - EOL, should be v16+
- Impact: Security vulnerabilities in production, lack of modern features, unsupported when bugs occur, incompatibility with modern tools
- Fix approach: Create a comprehensive dependency update plan. Start with Node.js version bump to 18+ LTS. Upgrade dependencies in groups: (1) testing framework, (2) logging, (3) redis client, (4) promise library, (5) jwt handling. Add security scanning (npm audit) to CI/CD.

**Deprecated Node.js APIs:**
- Issue: Using deprecated `fs.exists()` in recorder
- Files: `src/lib/recorder.ts` (line 273)
- Impact: `fs.exists()` was deprecated in Node.js v4 and removed in v10+. Will fail on modern Node.js
- Fix approach: Replace with `fs.stat()` or use Promise-based `fs.promises.stat()`. Wrap in try-catch for better error handling.

**Callback Hell Pattern:**
- Issue: Excessive callback nesting throughout codebase instead of Promises
- Files:
  - `src/lib/client.ts` (lines 157-165, 452-554)
  - `src/lib/connection.ts`
  - `src/lib/redis.ts` (entire file)
  - `src/lib/states.ts` (entire file)
  - `src/lib/recorder.ts` (lines 256-269)
- Impact: Reduced readability, difficult to handle errors consistently, harder to debug. Q.Promise usage on line 483 is also mixing callback and promise styles
- Fix approach: Systematically convert to async/await. Start with utility functions, then work up to higher-level orchestrations.

**Mixed TypeScript/JavaScript Modules:**
- Issue: Mixing TypeScript (`.ts`) and JavaScript (`.js`) in same codebase
- Files: Test files use `.js`, source uses `.ts`, config uses mixed patterns
- Impact: Inconsistent type safety, harder to maintain, build complexity
- Fix approach: Migrate all JavaScript test files to TypeScript. Use consistent file extensions and module system throughout.

**Class Naming Typo:**
- Issue: Logger class misspelled as "Lgger" instead of "Logger"
- Files: `src/lib/logger.ts` (line 5)
- Impact: Confusing for developers, appears to be accidental. Does not affect functionality since it's exported as default
- Fix approach: Rename class to `Logger` for clarity and convention

**Manual Memory Management:**
- Issue: Global state objects used to track connections and streams without cleanup lifecycle
- Files:
  - `src/lib/states.ts` (lines 21-24): `usersInsideGroupsSet`, `usersCurrentMessagesSet`, `groupsOfUsersSet`, `groupsCurrentMessagesSet`
  - `src/lib/recorder.ts` (lines 26-27): `recordStartTimesSet`, `recordStreamsSet`
- Impact: Potential memory leaks if connections disconnect ungracefully. No automatic cleanup on connection errors
- Fix approach: Implement cleanup in connection close handlers. Add size limits and TTL-based expiration. Monitor with memory profiling.

---

## Known Bugs

**Invalid Conditional Logic in Recorder.save():**
- Symptoms: Text message saving condition is inverted - messages never get saved properly
- Files: `src/lib/recorder.ts` (line 148-151)
- Code:
  ```typescript
  if (!(msg.messageType !== MessageType.TEXT ||
        msg.messageType !== MessageType.IMAGE ||
        msg.messageType !== MessageType.INTERACTIVE)) {
    return;
  }
  ```
- Problem: Due to De Morgan's law, this condition always evaluates to true and returns early. Should be AND (`&&`) not OR (`||`)
- Trigger: Send any TEXT, IMAGE, or INTERACTIVE message
- Fix: Change to:
  ```typescript
  if (!(msg.messageType === MessageType.TEXT ||
        msg.messageType === MessageType.IMAGE ||
        msg.messageType === MessageType.INTERACTIVE)) {
    return;
  }
  ```

**Invalid Conditional Logic in Recorder.upload():**
- Symptoms: Upload function never executes for valid message types
- Files: `src/lib/recorder.ts` (line 201-204)
- Code:
  ```typescript
  if (!(msg.messageType !== MessageType.STOP ||
        msg.messageType !== MessageType.TEXT ||
        msg.messageType !== MessageType.IMAGE ||
        msg.messageType !== MessageType.INTERACTIVE)) { return; }
  ```
- Problem: Same De Morgan's law issue - always returns true and exits
- Trigger: Send STOP, TEXT, IMAGE, or INTERACTIVE message
- Fix: Change to:
  ```typescript
  if (!(msg.messageType === MessageType.STOP ||
        msg.messageType === MessageType.TEXT ||
        msg.messageType === MessageType.IMAGE ||
        msg.messageType === MessageType.INTERACTIVE)) { return; }
  ```

**Stream Not Closed on Resume Error:**
- Symptoms: If stream is unavailable during resume, the error callback is invoked but no stream cleanup occurs
- Files: `src/lib/recorder.ts` (lines 64-71)
- Impact: Orphaned file handles may accumulate
- Trigger: Message arrives for stream that was never created
- Workaround: Verify START message is sent before AUDIO messages

**Unhandled Promise Rejection in Recorder.upload():**
- Symptoms: File rename errors are silently caught but not propagated back to caller
- Files: `src/lib/recorder.ts` (lines 256-269)
- Issue: Q.Promise error handling at line 267 logs error but callback already resolved at line 232
- Impact: Caller never knows if file was successfully renamed. Next client request for same file may fail
- Trigger: Disk permission issues or concurrent file operations during rename
- Fix approach: Track rename status and retry or provide error callback

---

## Security Considerations

**Hardcoded Fallback JWT Secret:**
- Risk: Production systems may fall back to default secret if env vars not set
- Files: `src/lib/config.ts` (lines 14, 38)
- Current code:
  ```typescript
  routerJwtSecret: process.env.ROUTER_JWT_SECRET || process.env.SECRET_KEY || "awesomevoiceping"
  toId: secretKey: process.env.SECRET_KEY || "awesomevoiceping"
  ```
- Current mitigation: Throws error in production at startup if both missing (line 4-6)
- Recommendation: (1) Remove fallback secrets entirely, fail fast if missing. (2) Add startup validation warning. (3) Monitor for hardcoded secret usage in logs.

**Legacy Authentication Mode:**
- Risk: `legacyJoinEnabled` allows joining with plaintext token as user ID if JWT validation fails
- Files: `src/lib/server.ts` (lines 187-189)
- Current code:
  ```typescript
  if (config.auth.legacyJoinEnabled) {
    deferred.resolve({ uid: token, legacy: true });
  }
  ```
- Current mitigation: Must be explicitly enabled via env var, disabled by default
- Recommendation: (1) Add audit logging when legacy auth is used. (2) Schedule deprecation timeline. (3) Add warning on every legacy login. (4) Consider removing if not needed.

**JWT Expiry Validation Missing Edge Cases:**
- Risk: Token expiry check uses `Date.now() / 1000 > user.exp` which can be off by seconds due to clock skew
- Files: `src/lib/server.ts` (lines 182-183)
- Impact: Tokens may be rejected prematurely or accepted past expiry depending on server clock drift
- Recommendation: (1) Add 30-60 second grace period for clock skew. (2) Log when tokens are close to expiry. (3) Require clock synchronization in deployment docs.

**No Input Validation on User IDs and Group IDs:**
- Risk: User and group IDs are passed directly from headers/JWT without sanitization
- Files:
  - `src/lib/server.ts` (lines 226-227): User ID comes from JWT without validation
  - `src/lib/server.ts` (line 173): deviceId from headers without validation
- Impact: Could allow injection of special characters into Redis keys if Redis is accessible
- Recommendation: (1) Validate user/group IDs are numeric or safe alphanumeric. (2) Normalize before use. (3) Add type guards.

**Unencrypted WebSocket Messages:**
- Risk: All messages transmitted over WebSocket in binary format via notepack, but not encrypted
- Files: `src/lib/packer.ts`, `src/lib/connection.ts`
- Current mitigation: None
- Recommendation: (1) Use WSS (WebSocket Secure) in production. (2) Document in deployment guide. (3) Consider application-level encryption for sensitive payloads (audio data).

**Redis Connection Not Authenticated at Startup:**
- Risk: Redis connection may fail silently if credentials are wrong
- Files: `src/lib/redis.ts` (lines 8-14)
- Issue: Error handlers registered but no explicit authentication check on startup
- Current mitigation: Error callback logs message
- Recommendation: (1) Add explicit PING/PONG authentication test on startup. (2) Fail fast if Redis connection not established. (3) Add connection retry logic with backoff.

**Device Token Storage Without Encryption:**
- Risk: Device tokens (push notification credentials) stored in plaintext in Redis
- Files: `src/lib/redis.ts` (multiple device token methods), `src/lib/client.ts` (line 63)
- Impact: If Redis is compromised, attacker can impersonate push notifications
- Recommendation: (1) Encrypt device tokens at rest. (2) Use separate Redis instance for sensitive data. (3) Add audit logging for device token access.

---

## Performance Bottlenecks

**Inefficient Group User Lookup with Dual Queries:**
- Problem: Group message broadcast queries both Redis and States, then merges results
- Files: `src/lib/server.ts` (lines 111-120)
- Code:
  ```typescript
  Redis.getUsersInsideGroup(msg.toId, (err, userIds) => {
    return States.getUsersInsideGroup(msg.toId, (err1, stateUserIds) => {
      const redisUsers = userIds && userIds instanceof Array ? userIds : [];
      const stateUsers = stateUserIds && stateUserIds instanceof Array ? stateUserIds : [];
      const combined = Array.from(new Set([...redisUsers, ...stateUsers]));
      // ...
      return this.broadcastToGroupWithCheck(msg, combined);
    });
  });
  ```
- Impact: Every group message requires two sequential database lookups. With large groups, Set creation adds overhead
- Fix: Implement single source of truth. Cache group membership with TTL to reduce queries. Use Set as primary data structure.

**Synchronous User-Group Membership Updates on Every Message:**
- Problem: States.addUserToGroup and States.removeUserFromGroup perform nested callback chains
- Files: `src/lib/states.ts` (lines 58-108)
- Impact: Every connection triggers multiple callbacks, potential N+1 pattern in group operations
- Improvement path: Batch operations. Use atomic Redis transactions for multi-step updates.

**Inefficient String Conversion in Message Type Comparisons:**
- Problem: Group IDs and user IDs converted to strings repeatedly for comparison
- Files: `src/lib/states.ts` (lines 63-64, 90-91, 114-115, etc.)
- Impact: Creates temporary string objects in tight loops, especially in group message paths
- Improvement path: Normalize IDs to consistent type at entry point. Use === instead of string comparison.

**File System Calls Without Batching:**
- Problem: `fs.exists()` followed by `fs.mkdir()` in sequential callback chain during startup
- Files: `src/lib/recorder.ts` (lines 32-38)
- Impact: Startup blocks on multiple I/O operations. Takes longer to become ready for connections
- Improvement path: Use Promise.all to check/create directories in parallel. Use modern fs/promises API.

**No Connection Pooling or Reuse:**
- Problem: Creates separate Redis client instance for command operations and separate subscriber for pub/sub
- Files: `src/lib/redis.ts` (lines 8-14)
- Impact: Double the Redis connections required. No connection pooling as traffic scales
- Improvement path: Use connection pooling library. Reuse client for multiple operations. Monitor connection count in production.

**Memory Accumulation in In-Memory State:**
- Problem: No TTL or cleanup on in-memory state objects tracking groups and users
- Files: `src/lib/states.ts` (lines 21-24)
- Impact: Long-running server accumulates state for every user/group ever seen, never cleaned up
- Improvement path: Implement periodic cleanup. Add size limits. Monitor with memory profiling.

---

## Fragile Areas

**Packer Unpack Error Recovery:**
- Files: `src/lib/packer.ts` (lines 106-202)
- Why fragile: Returns default error message objects on decode failures. Malformed messages don't cause crashes but silently create placeholder messages that may propagate downstream
- Safe modification: Test extensively with fuzzing. Add strict validation of decoded array length. Add unit tests for all error paths
- Test coverage: Basic error handling exists (lines 111-138), but edge cases in normalization (lines 30-86) are untested

**Connection Close Event Handler Ordering:**
- Files: `src/lib/connection.ts` (lines 34-38, 88-100)
- Why fragile: Removes listeners in same order they were added. If order changes, listeners may not be properly cleaned up
- Safe modification: Use a WeakMap to track listener references. Verify cleanup in disconnect tests
- Test coverage: No specific tests for connection cleanup

**Group Busy State Management with Race Conditions:**
- Files: `src/lib/client.ts` (lines 474-554)
- Why fragile: Complex state machine checking busy status, audio time, and idle duration with multiple callbacks. Race conditions possible if messages arrive out of order or Redis/States get out of sync
- Safe modification: Add version numbers to state objects. Validate state consistency in assertions. Add comprehensive integration tests
- Test coverage: Some group tests exist (`test/group.test.js`) but race condition scenarios not covered

**Message ID Generation Without Atomicity:**
- Files: `src/lib/recorder.ts` (line 219-230)
- Why fragile: Message ID includes timestamp with millisecond precision. If two messages created in same millisecond from same users, IDs may not be unique
- Safe modification: Add sequence number or UUID. Test with high-frequency message scenarios
- Test coverage: Not explicitly tested

**Redis/States Consistency:**
- Files: `src/lib/server.ts` (lines 73-89), `src/lib/client.ts` (lines 142-147, 150-155)
- Why fragile: Both Redis and States (in-memory) track group membership. Updates not atomic - can diverge
- Safe modification: Implement consistency checks. Log when discrepancies detected. Add replication/sync mechanism
- Test coverage: Tests pass group data but don't verify Redis/States consistency

---

## Scaling Limits

**Single-Worker Architecture:**
- Current capacity: Redis clean operations only run on worker 1 (line 58)
- Limit: When scaled horizontally, only one worker in cluster can perform cleanup
- Scaling path: Implement distributed cleanup with leader election or time-based partitioning

**In-Memory State Per Process:**
- Current capacity: Each process maintains duplicate copy of all group/user state
- Limit: Memory usage scales with total users/groups per server, not with request volume
- Scaling path: Move all state to Redis. Accept network latency tradeoff for distributed consistency

**Single Redis Instance:**
- Current capacity: No replication, single point of failure
- Limit: Redis becomes bottleneck at ~10-20k concurrent connections depending on message rate
- Scaling path: Implement Redis Sentinel for high availability. Consider Redis Cluster for horizontal scaling

**WebSocket Connections Per Process:**
- Current capacity: Limited by file descriptors and memory (typical: 10k-100k per process)
- Limit: Each connection uses memory for states, event listeners, Redis subscriptions
- Scaling path: Implement connection migration between processes. Use dedicated connection pool servers

**Binary Serialization Without Compression:**
- Current capacity: All messages use notepack encoding without compression
- Limit: Large audio payloads increase bandwidth. Audio frames sent uncompressed
- Scaling path: Add optional gzip/brotli compression for large payloads. Implement per-client compression preference

---

## Dependencies at Risk

**jwt-simple Unmaintained:**
- Risk: No updates since 2015, multiple CVE reports in JWT handling across ecosystem
- Impact: Vulnerability fix latency. Potential token validation bypass
- Migration plan: Migrate to `jsonwebtoken` (actively maintained) or use modern asymmetric key support with RS256 instead of HS256

**Q Promise Library Deprecated:**
- Risk: Q.js is deprecated in favor of native Promises and async/await
- Impact: Mixing callback and Promise styles makes codebase harder to maintain
- Migration plan: Replace with native Promise/async-await. 1:1 replacements: `Q.defer()` -> `new Promise()`, `Q.Promise()` -> direct `Promise` constructor, `.then()` chains -> async/await

**Redis 2.8.0 Missing Modern Features:**
- Risk: No support for modern Redis features like streams, sets optimization, improved cluster support
- Impact: Can't use newer Redis features for optimization. Vulnerable to known issues in old client
- Migration plan: Upgrade to redis v4 or v5. Use `ioredis` as alternative with better cluster/sentinel support

**tslint Deprecated:**
- Risk: tslint officially deprecated in 2019, maintainers recommend ESLint + TypeScript plugin
- Impact: Can't use modern linting rules. No updates or bug fixes
- Migration plan: Remove tslint, add ESLint with @typescript-eslint plugin. Update lint scripts.

---

## Missing Critical Features

**No Graceful Shutdown:**
- Problem: Server doesn't gracefully close connections on SIGTERM/SIGINT
- Blocks: Can't deploy new versions without interrupting active messages
- Impact: Lost audio in-flight during deployment
- Recommendation: Implement shutdown handler that (1) stops accepting new connections, (2) drains existing connections with timeout, (3) syncs final state to Redis before exit

**No Connection Heartbeat/Timeout Detection:**
- Problem: Stale connections may persist indefinitely if client crashes
- Blocks: Can't detect dead connections automatically
- Impact: Memory leaks from abandoned connections, receiver thinks sender is still connected
- Recommendation: Implement connection timeout (detect no messages for X seconds), force-close stale sockets

**No Rate Limiting:**
- Problem: Any authenticated user can send unlimited messages
- Blocks: Can't protect against abusive clients or DoS
- Impact: Single misbehaving client can overload server
- Recommendation: Implement per-user message rate limits, per-connection bandwidth limits

**No Message Ordering Guarantee:**
- Problem: Messages may arrive out of order due to broadcast pattern
- Blocks: Can't build sequencing-dependent features
- Impact: Audio frames may be played out of order if network varies
- Recommendation: Add sequence numbers to messages, implement reordering buffer on receiver

**No Dead Letter Queue:**
- Problem: Messages to offline users are discarded
- Blocks: Can't implement offline message storage feature
- Impact: Users miss messages while offline
- Recommendation: Store messages in Redis for offline users, deliver on reconnect

---

## Test Coverage Gaps

**No Unit Tests for Core Services:**
- What's not tested: `src/lib/packer.ts`, `src/lib/connection.ts`, `src/lib/client.ts` (individual methods), `src/lib/logger.ts`
- Files: No `.test.ts` or `.spec.ts` files for core modules
- Risk: Refactoring breaks core functionality silently. Message encoding bugs propagate to clients
- Priority: High - these modules handle message reliability

**Integration Test Coverage Incomplete:**
- What's not tested:
  - Duplicate login scenario (connection switching between devices)
  - Redis/States consistency during membership changes
  - Connection failure during group message send
  - Recorder stream cleanup on errors
  - Memory leaks during high-frequency reconnects
- Files: `test/` directory has basic tests but missing error path coverage
- Risk: Scalability issues and memory leaks only discovered in production
- Priority: High - affects reliability at scale

**No Property-Based Testing:**
- What's not tested: Edge cases in message ID generation, ID string normalization, concurrent state modifications
- Files: Tests use hardcoded scenarios, not generative testing
- Risk: Subtle bugs in edge cases only found after bugs reported
- Priority: Medium

**No Load/Stress Tests:**
- What's not tested: Performance under 10k+ concurrent connections, message throughput limits, memory behavior under sustained load
- Files: No stress test directory or load test scripts
- Risk: Performance problems and scaling limits unknown until production issues occur
- Priority: High - needed to determine scaling characteristics

**No Error Path Tests:**
- What's not tested: Redis connection failures, disk full during recording, network timeout handling, malformed message handling
- Files: Tests assume success paths
- Risk: Error scenarios crash or hang server in production
- Priority: High

---

*Concerns audit: 2026-02-06*
