---
phase: quick-4
plan: 01
subsystem: mediasoup-audio
tags:
  - bug-fix
  - audio
  - android
  - race-condition
  - ptt
dependency-graph:
  requires:
    - "PTT flow (web and Android clients)"
    - "mediasoup producer/consumer lifecycle"
    - "Redis pub/sub for channel state"
  provides:
    - "Fixed Android speaker to web listener audio path"
    - "Race condition prevention for SPEAKER_CHANGED messages"
  affects:
    - "Web client audio consumption logic"
    - "Server PTT broadcast behavior"
tech-stack:
  added: []
  patterns:
    - "Defensive message handling (tolerate missing fields)"
    - "Conditional broadcast based on client type"
key-files:
  created: []
  modified:
    - path: "src/client/connectionManager.ts"
      loc-delta: +8
      description: "Fixed handleSpeakerChanged to only stop consuming when isBusy=false"
    - path: "src/server/signaling/handlers.ts"
      loc-delta: +19
      description: "Conditional SPEAKER_CHANGED broadcast in handlePttStart, added Redis pub/sub comment"
decisions:
  - decision: "Web client tolerates SPEAKER_CHANGED without producerId when channel busy"
    rationale: "Android sends PTT_START before PRODUCE, causing race with Redis pub/sub messages"
    alternatives:
      - "Ignore Redis pub/sub messages (breaks multi-server)"
      - "Force Android to pre-create producer (breaks Android battery optimization)"
    chosen: "Defensive client-side handling"
  - decision: "Server conditionally broadcasts SPEAKER_CHANGED with/without producerId"
    rationale: "Clarifies intent: without producerId for UI update, with producerId for audio consumption"
    alternatives:
      - "Always broadcast with undefined producerId (confusing)"
      - "Skip broadcast entirely (breaks UI updates)"
    chosen: "Two broadcast patterns based on producer existence"
metrics:
  duration-minutes: 2.8
  tasks-completed: 3
  files-modified: 2
  loc-added: 27
  loc-removed: 6
  commits: 2
  completed-date: "2026-02-16"
---

# Quick Task 4: Fix No-Audio Bug (Android Speaker to Web Listener)

**One-liner:** Web client now tolerates SPEAKER_CHANGED without producerId during Android PTT flow, preventing race condition that cancelled active audio consumption.

## Objective

Fix critical bug where Android speaker's audio doesn't reach web listeners due to race condition between PTT_START and PRODUCE message timing.

**Root cause:** Android creates producer AFTER PTT_START (unlike web which pre-creates). Redis pub/sub broadcasts SPEAKER_CHANGED without producerId, which can arrive AFTER handleProduce's re-broadcast (which has producerId), causing web client to call stopConsuming() and cancel valid audio.

**Impact:** Android to web audio path completely broken before this fix.

## What Was Built

### 1. Web Client Defensive Handling (Task 1)

**File:** `src/client/connectionManager.ts`

Changed `handleSpeakerChanged()` audio consumption logic from unconditional `else` to conditional `else if`:

```typescript
// OLD (broken):
if (isBusy && producerId && userId) {
  this.startConsuming(producerId);
} else {
  this.stopConsuming(); // BAD: stops even when isBusy=true but producerId missing
}

// NEW (fixed):
if (isBusy && producerId && userId) {
  this.startConsuming(producerId);
} else if (!isBusy) {
  // Only stop when speaker releases (isBusy=false)
  this.stopConsuming();
}
// else: isBusy=true but no producerId -- wait for re-broadcast with producerId
```

**Effect:** Web client now correctly waits for authoritative SPEAKER_CHANGED with producerId instead of prematurely cancelling consumption.

### 2. Server Conditional Broadcast (Task 2)

**File:** `src/server/signaling/handlers.ts`

Split `handlePttStart()` broadcast into two cases:

```typescript
// If producer exists (web client flow):
if (producerId) {
  this.broadcastToChannel(
    channelId,
    createMessage(SignalingType.SPEAKER_CHANGED, { ...result.state, producerId } as any),
    ctx.userId,
  );
} else {
  // Producer doesn't exist yet (Android flow):
  // Broadcast without producerId for UI update only
  this.broadcastToChannel(
    channelId,
    createMessage(SignalingType.SPEAKER_CHANGED, result.state as any),
    ctx.userId,
  );
}
```

**Effect:** Server explicitly distinguishes between "channel busy for UI" and "channel busy WITH audio producerId for consumption".

**Also added:** Comment to Redis pub/sub callback in `handleJoinChannel` explaining why it broadcasts without excludeUserId and may send messages without producerId (multi-server scenarios).

### 3. Build Verification (Task 3)

- TypeScript compilation: **PASSED**
- Build artifacts generated: `dist/client/connectionManager.js`, `dist/server/signaling/handlers.js`
- Remote deployment: **SKIPPED** (SSH to connectvoice not available in this environment)

**Deployment instructions for user:**

```bash
ssh connectvoice
cd /path/to/voiceping-router
git pull
docker compose down && docker compose up -d --build
```

After deployment, verify in server logs:
- Look for "Auto-resumed producer" (handleProduce auto-resume path)
- Look for "Broadcast speaker-changed to N clients" (re-broadcast with producerId)

## Deviations from Plan

None - plan executed exactly as written.

## Testing & Verification

**Automated:**
- TypeScript compilation: PASSED (`npx tsc --noEmit` exit 0)
- Grep verification: `else if (!isBusy)` pattern exists in connectionManager.ts
- Grep verification: `if (producerId)` pattern exists in handlers.ts before broadcast

**Manual (requires deployment):**
1. Android user presses PTT -> web listener should hear audio
2. Web user presses PTT -> web listener should hear audio (no regression)
3. Speaker releases PTT -> web listener stops hearing audio

**Expected server logs (handleProduce path):**
```
PTT started for [userId] in channel [channelId]
Created producer [producerId] for [userId] in channel [channelId]
Auto-resumed producer [producerId] (PTT already active for [userId])
```

## Device Verification

**Status:** VERIFIED on physical device
**Device:** Samsung Galaxy S22 Ultra (SM-S906E), Android 16
**Date:** 2026-02-16
**Tested by:** User (physical device test)

**Results:**
- Android speaker presses PTT -> web listener hears audio: PASS
- Web-to-web PTT (regression check): PASS
- Speaker releases PTT -> audio stops: PASS

## Architecture Impact

**Before this fix:**
```
Android PTT_START -> Server acquires lock -> Broadcast SPEAKER_CHANGED (no producerId)
   -> Redis pub/sub -> Broadcast SPEAKER_CHANGED to all (no producerId)
Android PRODUCE -> Server creates producer -> Auto-resume -> Broadcast SPEAKER_CHANGED (with producerId)
   -> Web client starts consuming
   -> Redis pub/sub message arrives LATE -> Web client stops consuming (BUG!)
```

**After this fix:**
```
Android PTT_START -> Server acquires lock -> Broadcast SPEAKER_CHANGED (no producerId)
   -> Web client sees isBusy=true, no producerId -> SKIPS stopConsuming()
Android PRODUCE -> Server creates producer -> Auto-resume -> Broadcast SPEAKER_CHANGED (with producerId)
   -> Web client starts consuming (CORRECT)
   -> Redis pub/sub message arrives -> Web client sees isBusy=true, no producerId -> SKIPS stopConsuming() (SAFE)
```

**Key insight:** Redis pub/sub callback in `handleJoinChannel` fires for ALL channel state changes, including PTT start/stop. This is intentional for multi-server scenarios. The fix makes web clients resilient to message ordering, not removing the "redundant" broadcasts.

## Performance Metrics

- **Duration:** 2.8 minutes (166 seconds)
- **Tasks completed:** 3/3
- **Files modified:** 2
- **Lines added:** 27 (comments + logic)
- **Lines removed:** 6 (old unconditional else)
- **Commits:** 2
  - 8379b6a: fix(quick-4): web client tolerates SPEAKER_CHANGED without producerId
  - 4d995f8: fix(quick-4): server broadcasts SPEAKER_CHANGED conditionally in handlePttStart

## Risks & Mitigations

**Risk:** Web client might not hear audio if handleProduce fails to re-broadcast
**Mitigation:** handleProduce auto-resume logic is well-tested (introduced in previous fix for PTT flow)

**Risk:** UI might not update if Android client disconnects between PTT_START and PRODUCE
**Mitigation:** Server still broadcasts SPEAKER_CHANGED without producerId for UI state

**Risk:** Regression in web-to-web PTT
**Mitigation:** Web client pre-creates producer, so producerId always exists in handlePttStart broadcast (old flow unchanged)

## Next Steps

**Immediate:**
1. ~~Deploy to production Docker via `docker compose down && docker compose up -d --build`~~ DONE
2. ~~Test Android speaker -> web listener audio path~~ VERIFIED
3. ~~Verify no regression in web-to-web PTT~~ VERIFIED

**Future:**
- Consider end-to-end integration tests for Android PTT flow
- Add metrics for SPEAKER_CHANGED message timing (detect slow handleProduce path)

## Self-Check: PASSED

**Created files verification:**
- No files created (bug fix only)

**Modified files verification:**
```bash
[ -f "src/client/connectionManager.ts" ] && echo "FOUND: src/client/connectionManager.ts" || echo "MISSING: src/client/connectionManager.ts"
# FOUND: src/client/connectionManager.ts

[ -f "src/server/signaling/handlers.ts" ] && echo "FOUND: src/server/signaling/handlers.ts" || echo "MISSING: src/server/signaling/handlers.ts"
# FOUND: src/server/signaling/handlers.ts
```

**Commits verification:**
```bash
git log --oneline --all | grep -q "8379b6a" && echo "FOUND: 8379b6a" || echo "MISSING: 8379b6a"
# FOUND: 8379b6a

git log --oneline --all | grep -q "4d995f8" && echo "FOUND: 4d995f8" || echo "MISSING: 4d995f8"
# FOUND: 4d995f8
```

**All checks PASSED.**
