---
phase: quick-14
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
autonomous: true

must_haves:
  truths:
    - "Server no longer logs 'Invalid message type: undefined' from Android client"
    - "Android does not send any message with a null or missing type field"
  artifacts:
    - path: "android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt"
      provides: "Guard against sending messages with undefined/null type"
  key_links:
    - from: "SignalingClient.kt:send()"
      to: "server:handleMessage validation"
      via: "message type guard"
      pattern: "message.type"
---

<objective>
Server logs: "Invalid message type from <Android-userId>: undefined"
This appears after PTT and produce sequences. Android is sending a JSON message with
type=undefined/null which the server rejects with a warning.

Investigate:
1. Find all places in Android SignalingClient.kt (and any callers) where messages are sent
2. Identify which message is being sent without a type field
3. Add a guard/log to catch and prevent null-type sends
4. Fix the root cause (likely a message object construction that omits the type field,
   or a server→client message being accidentally echoed back)

Server validation: message.type checked against SignalingType enum values.
Look for any place that sends a raw object or has an optional type field.

Purpose: Eliminate spurious server warnings; ensure all Android messages have valid types.
Output: Fixed Android SignalingClient.kt (or relevant message sender).
</objective>

<execution_context>
@.planning/quick/14-fix-android-undefined-message-type/14-PLAN.md
</execution_context>

<context>
@android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
@android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
@src/shared/protocol.ts
</context>
