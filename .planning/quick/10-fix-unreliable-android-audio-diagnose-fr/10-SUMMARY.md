---
phase: quick-10
plan: 01
subsystem: android-signaling, server-websocket, android-mediasoup
tags: [android, websocket, mediasoup, reliability, ptt, reconnection]
dependency_graph:
  requires: []
  provides: [reliable-android-ptt-after-reconnect]
  affects: [SignalingClient, websocketServer, MediasoupClient]
tech_stack:
  added: []
  patterns: [okhttp-ping-keepalive, server-eviction-on-reconnect, stale-callback-guard]
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
    - src/server/signaling/websocketServer.ts
    - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
decisions:
  - "Use WS close code 4000 for client-side replacement to prevent reconnect loop in old socket callbacks"
  - "Use WS close code 4001 for server-side eviction (distinct from 4000 client-side)"
  - "Throw IllegalStateException in stale onProduce to abort PTT flow (return value required; throw is the only valid abort)"
  - "20s OkHttp ping interval chosen as safely below 30-60s NAT timeout on mobile carriers"
metrics:
  duration: 3 minutes 10 seconds
  completed: 2026-02-20
  tasks_completed: 2
  files_modified: 3
---

# Phase quick-10 Plan 01: Fix Unreliable Android Audio Summary

**One-liner:** OkHttp WebSocket keepalive + close-before-reconnect + server userId eviction + stale transport ID guard in mediasoup callbacks.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Fix Android WebSocket keepalive and duplicate connection on reconnect | f165f9d | SignalingClient.kt |
| 2 | Server-side duplicate userId eviction and stale transport guard | b653c9c | websocketServer.ts, MediasoupClient.kt |

## What Was Done

### Task 1: SignalingClient.kt

**RC1 - NAT timeout keepalive:**
Added `.pingInterval(20, TimeUnit.SECONDS)` to the OkHttpClient builder. This sends WebSocket-level PING frames every 20 seconds, keeping NAT bindings alive independently of the application-level heartbeat. The 20s interval is safely below the typical 30-60s NAT timeout on mobile carriers. OkHttp handles pong tracking automatically — if 3 consecutive pings go unanswered, OkHttp closes the connection with `onFailure`, triggering the existing reconnect logic.

**RC2 - Duplicate connections on reconnect:**
In `connect()`, immediately before creating a new WebSocket, added cleanup of any existing connection using close code 4000 ("Replaced by new connection"). The `onClosing` and `onClosed` handlers were updated to treat code 4000 the same as 1000 (no reconnect triggered), preventing the old socket from spawning an additional reconnect loop.

### Task 2: websocketServer.ts + MediasoupClient.kt

**RC3 - Server-side duplicate userId eviction:**
Made `handleConnection` async and wrapped the body in try/catch. Before registering the new client, the method now iterates `this.clients` and evicts any existing connection with the same `userId`: calls `handleDisconnect` for proper channel/transport cleanup, closes the old socket with code 4001, and removes it from the map. This ensures the server never holds two connections for the same user.

**RC4 - Stale transport ID guard:**
Added staleness guards at the top of `onConnect` and `onProduce` callbacks in `createSendTransport`. Both compare the closure-captured `transportId` against `sendTransport?.id` (the current live transport). If they differ (indicating the callback was fired for a transport that has since been replaced/nulled), `onConnect` returns early and `onProduce` throws `IllegalStateException` (which aborts the blocking runBlocking call, propagates up to `startProducing`, and triggers the existing PTT error flow).

## Success Criteria Verification

- [x] RC1: `.pingInterval(20, TimeUnit.SECONDS)` present in OkHttpClient builder
- [x] RC2: `connect()` closes old WebSocket (code 4000) before creating new one; `onClosing`/`onClosed` treat 4000 as intentional
- [x] RC3: `handleConnection` async, evicts same-userId connections before registering new one
- [x] RC4: `onConnect`/`onProduce` guard against stale transport ID
- [x] `npx tsc --noEmit` passes (0 errors)
- [x] `./gradlew compileDebugKotlin` passes (0 errors, 1 pre-existing KT-73255 warning)

## Deviations from Plan

None - plan executed exactly as written.

## Self-Check: PASSED

Files confirmed to exist:
- android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt - FOUND
- src/server/signaling/websocketServer.ts - FOUND
- android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt - FOUND

Commits confirmed:
- f165f9d - FOUND (Task 1)
- b653c9c - FOUND (Task 2)
