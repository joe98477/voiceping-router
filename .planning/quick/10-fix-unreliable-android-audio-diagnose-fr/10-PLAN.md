---
phase: quick-10
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
  - src/server/signaling/websocketServer.ts
  - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
autonomous: true
requirements: [AUDIO-RELIABILITY]

must_haves:
  truths:
    - "Android WebSocket survives NAT timeouts via OkHttp ping keepalive"
    - "Reconnecting Android client does not create duplicate WebSocket connections"
    - "Server evicts stale connections when same userId reconnects"
    - "PTT after reconnection uses fresh transport ID, not stale closure-captured ID"
  artifacts:
    - path: "android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt"
      provides: "OkHttp pingInterval keepalive + old WebSocket close-before-reconnect"
      contains: "pingInterval"
    - path: "src/server/signaling/websocketServer.ts"
      provides: "Server-side duplicate userId eviction on connect"
      contains: "existing connection"
    - path: "android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt"
      provides: "Stale transport guard in onConnect/onProduce callbacks"
      contains: "sendTransport?.id"
  key_links:
    - from: "SignalingClient.kt"
      to: "OkHttpClient"
      via: "pingInterval(20, TimeUnit.SECONDS)"
      pattern: "pingInterval.*20"
    - from: "websocketServer.ts handleConnection"
      to: "this.clients"
      via: "duplicate userId check and eviction"
      pattern: "ctx\\.userId.*existing"
---

<objective>
Fix four root causes of unreliable Android audio identified from production device logs.

Purpose: Android clients silently lose WebSocket connections due to NAT timeout, create duplicate connections on reconnect, and send stale transport IDs after reconnection -- all causing PTT audio to fail silently.

Output: Patched SignalingClient.kt, websocketServer.ts, and MediasoupClient.kt with fixes for all four root causes.
</objective>

<execution_context>
@/home/earthworm/.claude/get-shit-done/workflows/execute-plan.md
@/home/earthworm/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
@src/server/signaling/websocketServer.ts
@android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix Android WebSocket keepalive and duplicate connection on reconnect</name>
  <files>android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt</files>
  <action>
Two changes to SignalingClient.kt:

**1. Add OkHttp WebSocket-level ping keepalive (Root Cause 1):**

Change the OkHttpClient builder (line ~53-55) from:
```kotlin
private val client = OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .build()
```
to:
```kotlin
private val client = OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .build()
```

This sends WebSocket-level PING frames every 20 seconds, keeping NAT bindings alive independently of the application-level heartbeat. The 20s interval is safely below the typical 30-60s NAT timeout on mobile carriers. OkHttp handles pong tracking automatically.

**2. Close old WebSocket before creating new one on reconnect (Root Cause 2):**

In the `connect()` method, immediately after storing lastServerUrl/lastToken and before creating the new WebSocket, add cleanup of any existing connection:
```kotlin
// Close existing WebSocket to prevent duplicate connections
webSocket?.let { oldWs ->
    Log.d(TAG, "Closing existing WebSocket before reconnect")
    // Use code 4000 (custom) to signal intentional replacement, not 1000 (normal close)
    // This prevents the old socket's onClosing/onClosed from triggering reconnect logic
    try {
        oldWs.close(4000, "Replaced by new connection")
    } catch (e: Exception) {
        Log.w(TAG, "Failed to close old WebSocket gracefully, canceling", e)
        oldWs.cancel()
    }
    webSocket = null
}
```

Place this BEFORE the `_connectionState.value = ConnectionState.CONNECTING` line.

Also update the `onClosing` and `onClosed` handlers to treat code 4000 the same as 1000 (normal close, no reconnect):
- Change `if (code != 1000 && !intentionalDisconnect)` to `if (code != 1000 && code != 4000 && !intentionalDisconnect)` in both `onClosing` and `onClosed`.
  </action>
  <verify>
Run: `cd /home/earthworm/Github-repos/voiceping-router/android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileDebugKotlin`
Build must succeed with no new errors. Verify `pingInterval` appears in SignalingClient.kt. Verify old WebSocket close logic exists before `client.newWebSocket()`.
  </verify>
  <done>
OkHttpClient has `.pingInterval(20, TimeUnit.SECONDS)` configured. The `connect()` method closes any existing WebSocket (code 4000) before creating a new one. The `onClosing`/`onClosed` callbacks treat code 4000 as intentional (no reconnect trigger).
  </done>
</task>

<task type="auto">
  <name>Task 2: Server-side duplicate userId eviction and stale transport guard</name>
  <files>
    src/server/signaling/websocketServer.ts
    android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
  </files>
  <action>
Two changes across server and Android client:

**1. Server: Evict existing connections for same userId (Root Cause 3):**

In `websocketServer.ts` `handleConnection()` method, immediately after extracting userId/userName/etc from the request (around line 253) and BEFORE creating the new ClientContext, add duplicate eviction logic:

```typescript
// Evict existing connections for the same userId (prevent duplicates)
for (const [existingConnId, existingCtx] of this.clients.entries()) {
  if (existingCtx.userId === userId) {
    logger.warn(`Evicting stale connection ${existingConnId} for user ${userId} (new connection: ${connectionId})`);
    // Clean up the old connection's channels/transports via disconnect handler
    await this.handlers.handleDisconnect(existingCtx);
    existingCtx.ws.close(4001, 'Replaced by new connection');
    this.clients.delete(existingConnId);
  }
}
```

Note: `handleConnection` is currently synchronous. It needs to become `async` to support the `await` on `handleDisconnect`. Change the method signature from:
```typescript
private handleConnection(socket: ws.WebSocket, req: http.IncomingMessage): void {
```
to:
```typescript
private async handleConnection(socket: ws.WebSocket, req: http.IncomingMessage): Promise<void> {
```

The `wss.on('connection', ...)` callback doesn't check return values, so making it async is safe -- unhandled rejection is already caught by the existing error handler pattern. But wrap the entire body in try/catch for safety:
```typescript
private async handleConnection(socket: ws.WebSocket, req: http.IncomingMessage): Promise<void> {
  try {
    // ... existing body + new eviction logic ...
  } catch (err) {
    logger.error(`Error in handleConnection: ${err instanceof Error ? err.message : String(err)}`);
    socket.close(1011, 'Internal error');
  }
}
```

**2. Android: Guard against stale transport ID in callbacks (Root Cause 4):**

In `MediasoupClient.kt` `createSendTransport()`, the `onConnect` and `onProduce` callbacks capture `transportId` in their closure. After a reconnection + cleanup(), `sendTransport` is nulled, but if a race condition causes a stale callback to fire, it sends the old `transportId` to the server.

Add a staleness guard at the start of both `onConnect` and `onProduce` callbacks:

In `onConnect` (around line 742), at the very start of the override:
```kotlin
override fun onConnect(transport: Transport, dtlsParameters: String) {
    // Guard: verify this callback's transport matches current sendTransport
    if (sendTransport == null || sendTransport?.id != transportId) {
        Log.w(TAG, "SendTransport onConnect for stale transport $transportId (current: ${sendTransport?.id}), ignoring")
        return
    }
    Log.d(TAG, "SendTransport onConnect: $transportId")
    // ... rest unchanged ...
```

In `onProduce` (around line 773), at the very start of the override:
```kotlin
override fun onProduce(
    transport: Transport,
    kind: String,
    rtpParameters: String,
    appData: String?
): String {
    // Guard: verify this callback's transport matches current sendTransport
    if (sendTransport == null || sendTransport?.id != transportId) {
        Log.w(TAG, "SendTransport onProduce for stale transport $transportId (current: ${sendTransport?.id}), ignoring")
        throw IllegalStateException("Stale transport $transportId, current: ${sendTransport?.id}")
    }
    Log.d(TAG, "SendTransport onProduce: kind=$kind, transport=$transportId, channel=$sendTransportChannelId")
    // ... rest unchanged ...
```

Note: `onProduce` must return a String (producer ID), so throwing an exception is the correct way to abort -- it will be caught by the caller's try/catch which triggers PttManager error flow.
  </action>
  <verify>
Server: Run `cd /home/earthworm/Github-repos/voiceping-router && npx tsc --noEmit` to verify TypeScript compiles.
Android: Run `cd /home/earthworm/Github-repos/voiceping-router/android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileDebugKotlin` to verify Kotlin compiles.
Both must succeed with no new errors.
  </verify>
  <done>
Server evicts existing connections for same userId before registering new one. Android MediasoupClient guards onConnect/onProduce callbacks against stale transportId after reconnection. Both compile cleanly.
  </done>
</task>

</tasks>

<verification>
1. `npx tsc --noEmit` passes for server code
2. `./gradlew compileDebugKotlin` passes for Android code
3. SignalingClient.kt contains `.pingInterval(20, TimeUnit.SECONDS)`
4. SignalingClient.kt `connect()` closes old WebSocket before creating new one
5. websocketServer.ts `handleConnection` evicts stale same-userId connections
6. MediasoupClient.kt `onConnect`/`onProduce` guard against stale transport ID
</verification>

<success_criteria>
All four root causes addressed:
- RC1: OkHttp WebSocket keepalive prevents silent NAT timeout disconnections
- RC2: Old WebSocket explicitly closed before reconnect prevents duplicate connections
- RC3: Server evicts existing connections for same userId on new connect
- RC4: Stale transport ID callbacks detected and aborted instead of sending to server
Root Cause 5 (excessive speaker broadcasts) is cosmetic and deferred -- not addressed in this plan.
</success_criteria>

<output>
After completion, create `.planning/quick/10-fix-unreliable-android-audio-diagnose-fr/10-SUMMARY.md`
</output>
