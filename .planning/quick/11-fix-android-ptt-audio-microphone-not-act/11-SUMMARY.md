---
phase: quick-11
plan: 01
subsystem: client-session-recovery, server-signaling
tags: [bug-fix, reconnection, ptt, speaker-changed, redis-pubsub, microphone]
dependency_graph:
  requires: []
  provides: [web-client-session-recovery-fix, speaker-changed-dedup]
  affects: [src/client/connectionManager.ts, src/server/signaling/handlers.ts]
tech_stack:
  added: []
  patterns: [track-readyState-guard, intentionally-empty-pubsub-callback]
key_files:
  created: []
  modified:
    - src/client/connectionManager.ts
    - src/server/signaling/handlers.ts
decisions:
  - "Refresh microphone track unconditionally when readyState !== 'live' before re-produce on reconnect"
  - "Empty subscribeToChannel callback — all SPEAKER_CHANGED broadcasts handled by explicit handlers with correct producerId and excludeUserId"
metrics:
  duration: ~2.5 min
  completed: 2026-02-20
  tasks_completed: 2/2
  files_modified: 2
---

# Quick Task 11: Fix Web Session Recovery and SPEAKER_CHANGED Duplication

**One-liner:** Fixed web client "InvalidStateError: track ended" reconnect loop by refreshing the microphone track on session recovery, and eliminated duplicate SPEAKER_CHANGED broadcasts by emptying the Redis pub/sub callback that was firing alongside explicit handler broadcasts.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Fix web client session recovery to get fresh microphone track | 96a54f5 |
| 2 | Eliminate SPEAKER_CHANGED double-broadcast from Redis pub/sub | 8e56f91 |

## What Was Done

### Task 1: Fresh Microphone Track on Session Recovery

**File:** `src/client/connectionManager.ts`

**Problem:** `handleReconnection()` reused `this.audioTrack` — the original `MediaStreamTrack` from `connect()`. When the WebSocket disconnects, `transportClient.closeAll()` closes the send transport and producer, transitioning the underlying track to `"ended"` state. Calling `produceAudio(endedTrack)` threw `InvalidStateError: track ended`, causing an infinite reconnect loop.

**Fix:**
1. Removed `!this.audioTrack` from the component initialization guard (line 149) — the track can be re-acquired below if missing.
2. Added Step 4.5 before `produceAudio()`: checks `this.audioTrack.readyState !== 'live'`, and if so, releases the old `MicrophoneManager`, creates a fresh one, gets a new track, and mutes it by default.
3. Wrapped the mic re-acquisition in a try-catch that throws a descriptive error instead of a cryptic WebRTC error.

### Task 2: SPEAKER_CHANGED Deduplication

**File:** `src/server/signaling/handlers.ts`

**Problem:** Every PTT start/stop produced TWO `SPEAKER_CHANGED` broadcasts:
1. `startPtt()`/`stopPtt()` published to Redis pub/sub
2. The Redis subscriber callback in `handleJoinChannel` received it and called `broadcastToChannel()` — WITHOUT producerId, WITHOUT excludeUserId
3. The explicit handler (`handlePttStart`, `handlePttStop`, etc.) ALSO called `broadcastToChannel()` — WITH producerId, WITH excludeUserId

Result: 2x events per PTT action. The pub/sub copy (missing producerId) confused web clients watching Android PTT — they saw `SPEAKER_CHANGED { userId, producerId: null }` as a separate event that looked like speaker release, causing "null null" state confusion.

**Fix:** Made the `subscribeToChannel` callback empty with a detailed comment explaining:
- Why it's empty (deduplication)
- Which handlers own the broadcast responsibility
- How to implement proper multi-server forwarding in the future (serverId field in pub/sub payload)

The explicit handlers already broadcast with correct producerId and excludeUserId — they are the authoritative source.

## Verification

- `npx tsc --noEmit` passes (clean compilation, no errors)
- `npm run build` passes (server + client + test bundles)
- Grep confirms `SPEAKER_CHANGED` broadcast only in: handleProduce (line ~488), handlePttStart (lines ~622/630), handlePttStop (line ~731), handleDisconnect (line ~1248) — none in subscribeToChannel callback

## Decisions Made

1. **Empty pub/sub callback (not removed):** Kept the `subscribeToChannel` call because the subscription is still needed for Redis state synchronization. The callback body is empty so the subscription maintains channel state awareness without triggering duplicate broadcasts.

2. **Unconditional track guard on `readyState`:** Checking `readyState !== 'live'` is more robust than checking specifically for `'ended'` — it also handles `'muted'` state and any future state values that mean the track is unusable.

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check

- [x] `src/client/connectionManager.ts` modified with track readyState guard
- [x] `src/server/signaling/handlers.ts` modified with empty subscribeToChannel callback
- [x] Commit 96a54f5 exists (Task 1)
- [x] Commit 8e56f91 exists (Task 2)
- [x] `npx tsc --noEmit` passes
- [x] `npm run build` passes
