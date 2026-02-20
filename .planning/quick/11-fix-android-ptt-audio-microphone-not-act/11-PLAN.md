---
phase: quick-11
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/client/connectionManager.ts
  - src/server/signaling/handlers.ts
autonomous: true
requirements: [Q11-01, Q11-02]

must_haves:
  truths:
    - "Web client session recovery succeeds without 'track ended' error"
    - "SPEAKER_CHANGED events are not duplicated (one per PTT action, not two)"
    - "Android PTT press shows userId and producerId in SPEAKER_CHANGED after produce completes"
  artifacts:
    - path: "src/client/connectionManager.ts"
      provides: "Session recovery with fresh microphone track"
    - path: "src/server/signaling/handlers.ts"
      provides: "Single-broadcast SPEAKER_CHANGED (no Redis pub/sub duplication)"
  key_links:
    - from: "src/client/connectionManager.ts"
      to: "src/client/audio/microphone.ts"
      via: "getAudioTrack() call in handleReconnection"
      pattern: "microphoneManager.*getAudioTrack"
    - from: "src/server/signaling/handlers.ts"
      to: "src/server/state/channelState.ts"
      via: "subscribeToChannel callback suppressed"
      pattern: "subscribeToChannel"
---

<objective>
Fix two bugs: (1) web client session recovery fails with "InvalidStateError: track ended" on every reconnect, looping forever; (2) SPEAKER_CHANGED events are double-broadcast causing duplicate messages and confusion about Android PTT state.

Purpose: Restore reliable web client reconnection and clean up SPEAKER_CHANGED broadcast duplication that makes Android PTT appear broken to web observers.
Output: Fixed connectionManager.ts and handlers.ts
</objective>

<execution_context>
@.planning/quick/11-fix-android-ptt-audio-microphone-not-act/11-PLAN.md
</execution_context>

<context>
@src/client/connectionManager.ts
@src/client/audio/microphone.ts
@src/client/mediasoup/transportClient.ts
@src/server/signaling/handlers.ts
@src/server/state/channelState.ts
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix web client session recovery to get fresh microphone track</name>
  <files>src/client/connectionManager.ts</files>
  <action>
In `handleReconnection()` (around line 144), the code reuses `this.audioTrack` which is the original MediaStreamTrack from the initial `connect()` call. When the WebSocket disconnects and transports are closed via `this.transportClient.closeAll()`, the send transport closes, the producer closes, and the underlying MediaStreamTrack transitions to "ended" state. Attempting to call `transportClient.produceAudio(this.audioTrack, ...)` with an ended track throws `InvalidStateError: track ended`.

Fix: Before Step 5 ("Re-producing audio..."), check if the existing track has ended. If so (or unconditionally for robustness), get a fresh microphone track:

1. Before the "Re-produce audio" step (line ~177), add a check and refresh:
```typescript
// Step 4.5: Get fresh microphone track if current one ended
if (!this.audioTrack || this.audioTrack.readyState !== 'live') {
  console.log('Audio track ended, requesting fresh microphone access...');
  if (this.microphoneManager) {
    // Release the old dead track first
    this.microphoneManager.release();
  }
  this.microphoneManager = new MicrophoneManager();
  this.audioTrack = await this.microphoneManager.getAudioTrack();
  this.microphoneManager.muteTrack(); // Muted by default (PTT not pressed)
}
```

2. Also add the same guard at the top of `handleReconnection()` after the null check (line ~149) -- change the condition from:
```typescript
if (!this.signalingClient || !this.device || !this.transportClient || !this.audioTrack) {
```
to:
```typescript
if (!this.signalingClient || !this.device || !this.transportClient) {
```
(Remove `!this.audioTrack` from the guard since we will re-acquire it below if needed.)

3. Wrap the re-acquisition in a try-catch so if microphone access fails, it falls through to the outer error handler with a clear message.
  </action>
  <verify>
Run `npx tsc --noEmit` from the project root (or equivalent TypeScript check) to verify no compilation errors. Read the modified file to confirm the track-ended guard is in place before `produceAudio()`.
  </verify>
  <done>
`handleReconnection()` gets a fresh microphone track when the existing one has ended, preventing the "InvalidStateError: track ended" loop. Session recovery completes successfully after WebSocket reconnection.
  </done>
</task>

<task type="auto">
  <name>Task 2: Eliminate SPEAKER_CHANGED double-broadcast from Redis pub/sub</name>
  <files>src/server/signaling/handlers.ts</files>
  <action>
**Root cause analysis:** Every PTT start/stop produces TWO SPEAKER_CHANGED broadcasts to channel members:
1. `channelStateManager.startPtt()`/`stopPtt()` calls `publishSpeakerChanged()` which publishes to Redis pub/sub
2. The Redis subscriber callback in `handleJoinChannel` (line 233) receives this and calls `broadcastToChannel()` WITHOUT excludeUserId and WITHOUT producerId
3. Then `handlePttStart`/`handlePttStop` ALSO calls `broadcastToChannel()` directly WITH excludeUserId and WITH producerId (when available)

This causes every SPEAKER_CHANGED to be sent twice -- once from pub/sub (missing producerId, sent to ALL including the requester) and once from the handler (with producerId, excluding the requester). The pub/sub broadcast is only useful in multi-server deployments; in the current single-server setup it causes pure duplication.

**Fix:** Change the `subscribeToChannel` callback in `handleJoinChannel` (line 226-234) to NOT broadcast SPEAKER_CHANGED. Instead, make it only update internal state or log. The handlers already broadcast SPEAKER_CHANGED explicitly with the correct producerId and excludeUserId.

Specifically, in `handleJoinChannel`, change the subscribe callback from:
```typescript
await this.channelStateManager.subscribeToChannel(channelId, (state: ChannelState) => {
  this.broadcastToChannel(channelId, createMessage(SignalingType.SPEAKER_CHANGED, state as any));
});
```
to:
```typescript
await this.channelStateManager.subscribeToChannel(channelId, (_state: ChannelState) => {
  // Redis pub/sub notifications are intentionally NOT broadcast here.
  // All SPEAKER_CHANGED broadcasts are handled explicitly by handlePttStart,
  // handlePttStop, handleProduce, and handleDisconnect with the correct
  // producerId and excludeUserId parameters.
  //
  // Broadcasting here caused duplicate SPEAKER_CHANGED events:
  // - This callback fires WITHOUT producerId and WITHOUT excludeUserId
  // - The handlers fire WITH producerId and WITH excludeUserId
  // Resulting in 2x events per PTT action, and the pub/sub one missing
  // producerId which confused web clients observing Android PTT.
  //
  // For multi-server support in the future, this callback should forward
  // the pub/sub notification to local clients only if the originating
  // server is different (requires adding a serverId to the pub/sub message).
});
```

Also update the comment above the subscribe call (lines 228-232) to explain why the callback is empty.

**Additionally**, in `handleDisconnect` (line 1239), the disconnect cleanup already broadcasts SPEAKER_CHANGED via `this.broadcastToChannel()`. Verify that `channelStateManager.stopPtt()` (called at line 1235) still publishes to Redis for multi-server state sync but the subscriber callback no longer re-broadcasts. This is correct because the disconnect handler's own broadcast (line 1239) handles local notification.
  </action>
  <verify>
Run `npx tsc --noEmit` to verify compilation. Grep for `broadcastToChannel` calls that include `SPEAKER_CHANGED` to verify there is exactly one broadcast per PTT action path (handlePttStart, handlePttStop, handleProduce, handleDisconnect, dispatchHandlers) and NONE from the subscribeToChannel callback.
  </verify>
  <done>
Each PTT start/stop produces exactly one SPEAKER_CHANGED broadcast (from the handler, with correct producerId and excludeUserId). No duplicate events from Redis pub/sub. Web client observing Android PTT sees: (1) SPEAKER_CHANGED with userId but no producerId (from handlePttStart, Android PTT_START before PRODUCE), then (2) SPEAKER_CHANGED with userId AND producerId (from handleProduce auto-resume re-broadcast). No more "null null" duplicate floods.
  </done>
</task>

</tasks>

<verification>
1. TypeScript compilation: `npx tsc --noEmit` passes for both server and client code
2. Build check: `npm run build` (or equivalent) succeeds
3. Code review: Verify no SPEAKER_CHANGED broadcast in subscribeToChannel callback
4. Code review: Verify handleReconnection gets fresh mic track when existing one is ended
</verification>

<success_criteria>
- Web client reconnection no longer fails with "track ended" error
- SPEAKER_CHANGED is broadcast exactly once per PTT action (no duplicates)
- Android PTT flow still works: PTT_START broadcasts speaker info, PRODUCE re-broadcasts with producerId
- No TypeScript compilation errors
</success_criteria>

<output>
After completion, create `.planning/quick/11-fix-android-ptt-audio-microphone-not-act/11-01-SUMMARY.md`
</output>
