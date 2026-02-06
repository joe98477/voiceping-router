# Architecture

**Analysis Date:** 2026-02-06

## Pattern Overview

**Overall:** Real-time message routing and broadcasting server using WebSocket gateway pattern with in-memory state management and Redis backing store.

**Key Characteristics:**
- Distributed WebSocket server handling multiple concurrent client connections
- Message packing/unpacking abstraction layer for protocol translation
- Dual-layer state management: in-memory cache with Redis persistence
- Token-based JWT authentication for connection verification
- Support for both private (1:1) and group (1:many) messaging channels
- Event-driven architecture using Node.js EventEmitter pattern

## Layers

**Transport Layer:**
- Purpose: Handle WebSocket connections and socket-level communication
- Location: `src/lib/connection.ts`, `src/lib/server.ts`
- Contains: WebSocket lifecycle management, ping/pong keepalive, raw message receive/send
- Depends on: `ws` (WebSocket library), debug logging
- Used by: Client registration and message dispatch

**Message Processing Layer:**
- Purpose: Serialize/deserialize messages and normalize binary payloads
- Location: `src/lib/packer.ts`, `src/lib/types.ts`
- Contains: `IMessage` interface, pack/unpack operations, binary normalization
- Depends on: `notepack` (binary serialization)
- Used by: Connection class for encoding/decoding, Server for message routing

**Routing & Business Logic Layer:**
- Purpose: Route messages to correct recipients, enforce channel rules, manage group access
- Location: `src/lib/client.ts`, `src/lib/server.ts`
- Contains: Client registration, message type switching (private vs group), authorization checks
- Depends on: States, Redis, Logger, MessageType enums
- Used by: Entry point, Connection handlers

**State Management Layer:**
- Purpose: Track user group membership, message state, group busy status, audio timing
- Location: `src/lib/states.ts`
- Contains: In-memory maps (usersInsideGroupsSet, groupsCurrentMessagesSet, etc.), fallback to memored storage
- Depends on: Keys utility for Redis key generation
- Used by: Client for group operations, connection message handlers

**Persistence Layer:**
- Purpose: Durable group memberships, message history, device tokens, message metadata
- Location: `src/lib/redis.ts`
- Contains: Redis client operations, subscription handlers for membership updates
- Depends on: Redis client library
- Used by: States for fallback reads, Client for device token management

**Configuration & Support:**
- Purpose: Environment-based configuration, logging, JWT handling
- Location: `src/lib/config.ts`, `src/lib/logger.ts`
- Contains: Env var parsing, Winston logger setup
- Depends on: dotenv, winston
- Used by: All other modules

## Data Flow

**User Connection Flow:**

1. WebSocket connection initiated by client with JWT token in headers/protocols
2. `Server.verifyClient()` decodes token using `jwt-simple` against ROUTER_JWT_SECRET
3. Token valid → `Server.handleWssConnection()` creates `Connection` object
4. `Connection` wrapped in `Client` object, registered in `Server.clients` map
5. `Client.registerSocket()` assigns connection to socket key, detects duplicate login
6. User's channel list loaded from Redis → `States.addUserToGroup()` for each channel
7. Periodic `Client.ping()` keeps connection alive, `Connection.handleSocketPong()` updates timestamp

**Message Reception Flow:**

1. Client sends message via WebSocket (binary via notepack)
2. `Connection.handleSocketMessage()` unpacks data to `IMessage`
3. Message routed to `Client.handleConnectionMessage()` (event emission)
4. Type-based routing: `handlePrivateMessage()` or `handleGroupMessage()`
5. Message type-specific handler (TEXT, AUDIO, START, STOP, etc.)

**Message Sending (Private 1:1):**

1. `Server.sendMessageToUser()` called with destination userId
2. Looks up `Client` in `Server.clients[toId]`
3. Message packed via `packer.pack()`
4. All connections of Client sent data via `Connection.send()`

**Message Sending (Group):**

1. `Server.sendMessageToGroup()` called with groupId
2. Fetches user list from both Redis and States (combined)
3. Authorization check via `States.getUsersInsideGroup()` - sender must be in group
4. All users in group sent message via `sendDataToRecipients()`

**State Management (Busy Status Example):**

1. Group START message received → `Client.handleGroupStartMessage()`
2. Check `States.getBusyStateOfGroup()` - is group already in active conversation?
3. If busy and not sender, respond START_FAILED
4. If not busy, set `States.setCurrentMessageOfGroup()` + Redis backup
5. Periodic `States.periodicInspect()` clears busy state after `GROUP_BUSY_TIMEOUT`

**State Synchronization:**

- Redis as source of truth for group memberships
- `States` maintains in-memory cache for faster access
- Redis subscription listens for membership updates via `Redis.subscribeMembershipUpdates()`
- On membership change event, States synced via `addUserToGroup()` / `removeUserFromGroup()`
- Memored storage option for distributed deployments (not Redis-dependent)

## Key Abstractions

**Client:**
- Purpose: Represents a logged-in user, wraps multiple socket connections per device
- Examples: `src/lib/client.ts`
- Pattern: EventEmitter for message/close events, manages multiple Connection objects per key
- Tracks: User ID, current connections, ping interval

**Connection:**
- Purpose: Single WebSocket connection to a device/client
- Examples: `src/lib/connection.ts`
- Pattern: EventEmitter for message/close/pong events, stateful socket lifecycle
- Tracks: Device ID, socket key, timestamp for timeout detection

**Message:**
- Purpose: Standardized message format for all communication
- Examples: `src/lib/types.ts` - `IMessage` interface
- Pattern: channelType (GROUP=0, PRIVATE=1) + messageType enum + payload
- Supports: Text, audio (binary), images, metadata (messageId, fromId, toId)

**States:**
- Purpose: Centralized state store for group/user relationships and current activity
- Examples: `src/lib/states.ts`
- Pattern: Static methods with callback-based async operations
- Manages: User-group mappings, current message per group, busy states, audio timing

**Packer:**
- Purpose: Binary message serialization/deserialization
- Examples: `src/lib/packer.ts`
- Pattern: notepack for efficient binary packing, handles multiple buffer types (Uint8Array, ArrayBuffer, etc.)
- Feature: Normalizes incoming data to standard Buffer format

## Entry Points

**Main Server Entry:**
- Location: `src/app.ts`
- Triggers: Process startup via `npm run dev` or `npm start`
- Responsibilities: Load dotenv, create HTTP server, instantiate VoicePing.Server, listen on configured port

**VoicePing Module:**
- Location: `src/lib/voiceping.ts`
- Triggers: Required by app.ts
- Responsibilities: Export main entry points (Server, MessageType, ChannelType, packer, recorder)

**Server Startup:**
- Location: `src/lib/server.ts` constructor
- Triggers: `new VoicePing.Server({ server })` from app.ts
- Responsibilities:
  - Initialize WebSocket server (WSS)
  - Set up verifyClient handler
  - Initialize States periodic inspection
  - Subscribe to Redis membership updates
  - Start periodic Redis cleanup (on worker 1 only)

## Error Handling

**Strategy:** Callback-based error passing for async operations, console.error for connection errors, silent failures for non-critical operations.

**Patterns:**

- **Async Operations:** Error passed to callback first param, checked before proceeding
  - Example: `States.getBusyStateOfGroup(groupId, (err, busyState) => { if (err) {...} })`

- **Connection Errors:** Logged via `logger.error()`, caught in try-catch for send/ping operations
  - Example: `Connection.send()` wraps socket.send() in try-catch

- **Authorization Failures:** Respond with UNAUTHORIZED_GROUP message type (27) to sender
  - Example: `Server.broadcastToGroupWithCheck()` validates sender membership

- **Token Verification Failures:** Reject WebSocket connection in `verifyClient()` with 401 status
  - Example: Missing token → `verified(false, 401, "Unauthorized")`

- **Pack/Unpack Failures:** Log error, continue processing (ignore malformed messages)
  - Example: `Connection.handleSocketMessage()` debug logs on pack failure

## Cross-Cutting Concerns

**Logging:**
- Winston logger for important events (registration, unregistration, connection changes)
- Debug module for verbose tracing per connection, client, or server with namespace prefixes
- Structured: `logger.info()` for business events, `dbug()` for technical trace

**Validation:**
- Token validation: JWT decode against config.auth.routerJwtSecret, check expiry
- Message structure: Enum-based channel/message types, interface compliance checked at runtime
- Group membership: Required before sending group messages, enforced via lookup

**Authentication:**
- JWT token in WebSocket headers (token, voicepingtoken) or sec-websocket-protocol
- Token expiry checked: `Date.now() / 1000 > user.exp`
- Fallback to legacy mode: If LEGACY_JOIN_ENABLED, unverified tokens treated as user IDs

**Duplicate Prevention:**
- Device ID tracking: Redis stores last device ID per user
- Duplicate login detection: New device ID triggers LOGIN_DUPLICATED message to old session
- Connection closure: Old connections closed via `Client.closeConnectionsExceptKey()`
