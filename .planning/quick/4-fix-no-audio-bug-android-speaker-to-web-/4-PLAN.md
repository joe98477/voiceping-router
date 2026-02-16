---
phase: quick-4
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/client/connectionManager.ts
  - src/server/signaling/handlers.ts
autonomous: true
must_haves:
  truths:
    - "When Android user presses PTT, web listener hears audio"
    - "When web user presses PTT, web listener still hears audio (no regression)"
    - "When speaker releases PTT, web listener stops hearing audio"
  artifacts:
    - path: "src/client/connectionManager.ts"
      provides: "Fixed SPEAKER_CHANGED handler that tolerates missing producerId"
    - path: "src/server/signaling/handlers.ts"
      provides: "Fixed handlePttStart that skips broadcast when producerId is missing"
  key_links:
    - from: "src/server/signaling/handlers.ts"
      to: "src/client/connectionManager.ts"
      via: "SPEAKER_CHANGED WebSocket message"
      pattern: "SPEAKER_CHANGED.*producerId"
---

<objective>
Fix the no-audio bug where Android speaker's audio doesn't reach web listeners.

Purpose: Android creates its producer AFTER PTT_START (unlike web which pre-creates it). This causes a race condition where SPEAKER_CHANGED messages without a producerId arrive at the web client and trigger stopConsuming(), cancelling audio consumption that was started by a later SPEAKER_CHANGED that DID have a valid producerId.

Root cause analysis:
1. Android sends PTT_START -> server acquires speaker lock
2. Server broadcasts SPEAKER_CHANGED with producerId=undefined (producer doesn't exist yet)
3. Redis pub/sub also fires SPEAKER_CHANGED without producerId field
4. Android creates SendTransport + Producer -> server handleProduce auto-resumes and re-broadcasts SPEAKER_CHANGED with real producerId
5. Web client receives message from step 4 and starts consuming (CORRECT)
6. BUT: Redis pub/sub message from step 3 can arrive AFTER step 4's message (race condition), causing web client to call stopConsuming() because producerId is missing

Two fixes needed:
A) Web client: Don't stopConsuming on SPEAKER_CHANGED without producerId when channel is still busy
B) Server: Skip the explicit SPEAKER_CHANGED broadcast in handlePttStart when producerId is missing (let handleProduce do the authoritative broadcast)

Output: Fixed server and web client code, deployed via Docker rebuild
</objective>

<execution_context>
@/home/earthworm/.claude/get-shit-done/workflows/execute-plan.md
@/home/earthworm/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@src/client/connectionManager.ts
@src/server/signaling/handlers.ts
@src/server/mediasoup/producerConsumerManager.ts
@src/shared/protocol.ts
@src/shared/types.ts
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix web client SPEAKER_CHANGED handler to tolerate missing producerId</name>
  <files>src/client/connectionManager.ts</files>
  <action>
In `handleSpeakerChanged()` method (around line 227), change the audio consumption logic from:

```typescript
if (isBusy && producerId && userId) {
    this.startConsuming(producerId);
} else {
    this.stopConsuming();
}
```

To:

```typescript
if (isBusy && producerId && userId) {
    this.startConsuming(producerId);
} else if (!isBusy) {
    // Only stop consuming when speaker explicitly releases (isBusy=false).
    // Do NOT stop on SPEAKER_CHANGED with missing producerId -- this happens
    // when Android client's PTT_START arrives before PRODUCE (producer doesn't
    // exist yet). The follow-up SPEAKER_CHANGED from handleProduce will have
    // the real producerId and trigger startConsuming().
    this.stopConsuming();
}
// else: isBusy=true but no producerId -- Android PTT flow, producer not yet
// created. Skip and wait for the re-broadcast from handleProduce with real producerId.
```

This is the critical defensive fix. Without it, the Redis pub/sub SPEAKER_CHANGED (which never has producerId) can race with the handleProduce re-broadcast and cancel active audio consumption.
  </action>
  <verify>Run `npx tsc --noEmit` from project root to verify TypeScript compilation. Grep the changed file to confirm the `else if (!isBusy)` pattern exists.</verify>
  <done>Web client only calls stopConsuming() when isBusy is false (speaker released), never when isBusy is true but producerId is missing.</done>
</task>

<task type="auto">
  <name>Task 2: Fix server handlePttStart to skip broadcast when producerId is missing</name>
  <files>src/server/signaling/handlers.ts</files>
  <action>
In `handlePttStart()` method, the explicit SPEAKER_CHANGED broadcast (around line 600-605) sends `producerId` which is `undefined` for Android clients (they haven't created a producer yet). This produces a useless message that the web client must now ignore.

Change the broadcast to only include producerId when it exists:

```typescript
// Broadcast speaker change to channel (include producerId for audio consumption)
// Only broadcast with producerId if it exists. For Android clients that send
// PTT_START before PRODUCE, producerId is undefined here. handleProduce will
// send the authoritative SPEAKER_CHANGED with the real producerId after the
// producer is created.
if (producerId) {
    this.broadcastToChannel(
        channelId,
        createMessage(SignalingType.SPEAKER_CHANGED, { ...result.state, producerId } as any),
        ctx.userId,
    );
} else {
    // Broadcast without producerId so listeners know channel is busy
    // (UI shows speaker name) but don't trigger audio consumption yet
    this.broadcastToChannel(
        channelId,
        createMessage(SignalingType.SPEAKER_CHANGED, result.state as any),
        ctx.userId,
    );
}
```

This ensures web clients see the speaker change immediately (UI updates) but don't attempt to consume audio until a valid producerId arrives from handleProduce's re-broadcast.

Additionally, review the Redis pub/sub callback in `handleJoinChannel` (around line 226-229). The subscription callback broadcasts SPEAKER_CHANGED to ALL clients without excludeUserId. While the Task 1 fix makes this safe, it's still redundant with the explicit broadcasts in handlePttStart and handlePttStop. Add a comment noting this is intentional (for multi-server scenarios where pub/sub is needed for cross-server notification).
  </action>
  <verify>Run `npx tsc --noEmit` from project root to verify TypeScript compilation. Review server logs to confirm handleProduce auto-resume path still broadcasts with producerId.</verify>
  <done>Server handlePttStart broadcasts SPEAKER_CHANGED with or without producerId as appropriate. handleProduce's re-broadcast (which always has producerId) remains unchanged and is the authoritative source for audio consumption.</done>
</task>

<task type="auto">
  <name>Task 3: Deploy to Docker and verify with server logs</name>
  <files>docker-compose.yml</files>
  <action>
1. Build the TypeScript: `npx tsc` from project root
2. Rebuild and redeploy Docker containers: SSH to connectvoice remote and run `docker compose down && docker compose up -d --build`
3. Check server logs for the handleProduce auto-resume path: look for "Auto-resumed producer" log line to confirm the re-broadcast with producerId still works
4. Verify no TypeScript compilation errors in the Docker build output

NOTE: This task requires access to the remote deployment server. If SSH access is not available, skip this task and mark the plan as ready for manual deployment by the user.

If the user prefers to test locally first, run `npm run build` or `npx tsc` to verify compilation, then provide deployment instructions.
  </action>
  <verify>TypeScript compiles without errors. If deployed: server logs show "Auto-resumed producer" and "Broadcast speaker-changed to N clients" when Android PTT is pressed.</verify>
  <done>Code compiles clean. Either deployed to production Docker or ready for user to deploy manually.</done>
</task>

</tasks>

<verification>
1. TypeScript compilation passes: `npx tsc --noEmit` exits 0
2. Web client handleSpeakerChanged only calls stopConsuming when isBusy is false
3. Server handlePttStart broadcasts SPEAKER_CHANGED (with or without producerId)
4. Server handleProduce still auto-resumes and re-broadcasts with real producerId (unchanged)
5. No regression: web-to-web PTT still works (web client pre-creates producer, so producerId is always present in handlePttStart broadcast)
</verification>

<success_criteria>
- Web client tolerates SPEAKER_CHANGED messages without producerId when channel is busy
- Server explicitly handles the Android PTT flow (PTT_START before PRODUCE)
- TypeScript compiles without errors
- End-to-end: Android speaker -> web listener audio path works
</success_criteria>

<output>
After completion, create `.planning/quick/4-fix-no-audio-bug-android-speaker-to-web-/4-SUMMARY.md`
</output>
