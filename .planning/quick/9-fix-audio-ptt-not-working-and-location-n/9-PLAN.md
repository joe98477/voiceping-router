---
phase: 9-fix-audio-ptt-not-working-and-location-n
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt
  - src/server/signaling/websocketServer.ts
autonomous: false

must_haves:
  truths:
    - "Android PTT button requests mic permission when pressed without permission"
    - "ADMIN users receive location broadcasts on dispatch map"
    - "DISPATCH users continue to receive location broadcasts (no regression)"
  artifacts:
    - path: "android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt"
      provides: "PTT button with permission-agnostic clickability"
      contains: "val isClickable = !isBusy || isTransmitting"
    - path: "src/server/signaling/websocketServer.ts"
      provides: "Location broadcast to ADMIN and DISPATCH roles"
      contains: "(ctx.role === UserRole.DISPATCH || ctx.role === UserRole.ADMIN)"
  key_links:
    - from: "PttButton.kt:isClickable"
      to: "PttButton.kt:onPttPressed callback"
      via: "modifier.clickable/pointerInput when isClickable=true"
      pattern: "if \\(isClickable\\)"
    - from: "websocketServer.ts:sendToAllDispatchUsers"
      to: "websocketServer.ts:role check"
      via: "role comparison in loop"
      pattern: "ctx\\.role === UserRole\\.(DISPATCH|ADMIN)"
---

<objective>
Fix two critical bugs preventing core functionality: (1) Android PTT button unresponsive when mic permission not granted, and (2) ADMIN users not receiving location data on dispatch map.

Purpose: Restore PTT permission flow and ensure all authorized dispatch map viewers (ADMIN + DISPATCH) receive real-time location updates.
Output: Working PTT permission request flow on Android, location markers visible for ADMIN users on dispatch map.
</objective>

<execution_context>
@/home/earthworm/.claude/get-shit-done/workflows/execute-plan.md
@/home/earthworm/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md

# Investigation results
Bug 1: PttButton.kt line 129 makes button non-clickable when micPermissionGranted=false, preventing ViewModel's permission request logic from ever running.
Bug 2: websocketServer.ts line 551 only sends location broadcasts to DISPATCH role, excluding ADMIN users who also view dispatch map.

# Relevant code
@android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt
@src/server/signaling/websocketServer.ts
@src/shared/types.ts
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix Android PTT button clickability to allow permission requests</name>
  <files>android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt</files>
  <action>
Modify line 129 to remove `micPermissionGranted` from the clickability check:

**Current (line 129):**
```kotlin
val isClickable = micPermissionGranted && (!isBusy || isTransmitting)
```

**Fixed:**
```kotlin
val isClickable = !isBusy || isTransmitting
```

**Rationale:** The button should always be pressable when not busy — the ViewModel's `onPttPressed()` already handles the full permission flow (rationale dialog at lines 472-486, permission request, settings redirect). By blocking clicks when `micPermissionGranted=false`, the button becomes completely dead and the permission flow is never triggered.

**Why this fix is safe:**
- Lines 79-85 already handle visual feedback (dimmed gray + MicOff icon when permission denied)
- Lines 140-167 already attach handlers only when `isClickable=true`
- ViewModel permission logic (lines 472-486 in ChannelsViewModel) is already defensive and handles all permission states
- No other changes needed — the existing visual states and handlers are correct
  </action>
  <verify>
1. Build Android app: `cd android && ./gradlew compileDebugKotlin`
2. Verify no compilation errors
3. Code inspection: confirm line 129 is `val isClickable = !isBusy || isTransmitting`
  </verify>
  <done>
PTT button is clickable when mic permission not granted, allowing ViewModel's permission request flow to execute. Button still shows correct visual state (dimmed gray, MicOff icon).
  </done>
</task>

<task type="auto">
  <name>Task 2: Fix server location broadcasts to include ADMIN users</name>
  <files>src/server/signaling/websocketServer.ts</files>
  <action>
Modify the `sendToAllDispatchUsers` method (line 547-560) to include ADMIN users in location broadcasts:

**Current (line 551):**
```typescript
if (ctx.role === UserRole.DISPATCH && ctx.ws.readyState === 1) {
```

**Fixed:**
```typescript
if ((ctx.role === UserRole.DISPATCH || ctx.role === UserRole.ADMIN) && ctx.ws.readyState === 1) {
```

**Rationale:** ADMIN users (globalRole ADMIN maps to UserRole.ADMIN in permissionManager.ts) also view the dispatch map and need real-time location data. The current code only sends to DISPATCH role, leaving ADMIN users with an empty map.

**Why this fix is safe:**
- UserRole.ADMIN is already defined in src/shared/types.ts
- ADMIN users already have higher privileges than DISPATCH (can ban users, force disconnect)
- No security issue — ADMIN already has broader access than DISPATCH
- Method name `sendToAllDispatchUsers` is legacy — it's actually "send to all dispatch map viewers" (both ADMIN and DISPATCH view the map)
  </action>
  <verify>
1. Code inspection: confirm line 551 includes ADMIN role check
2. Verify TypeScript compiles: `cd /home/earthworm/Github-repos/voiceping-router && npx tsc --noEmit`
3. Verify no linting errors: `npm run lint`
  </verify>
  <done>
ADMIN users receive location broadcasts via WebSocket. Both DISPATCH and ADMIN roles can see real-time location markers on dispatch map.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: Verify fixes on physical device and production server</name>
  <files></files>
  <action>
Test both fixes in actual deployment environment to ensure no regressions.
  </action>
  <verify>
**Test 1 - Android PTT Permission Flow:**
1. Build APK: `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug`
2. Install on Samsung Galaxy S22 Ultra: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Uninstall VoicePing app (or clear app data to reset mic permission)
4. Launch app, login, join a channel
5. Press PTT button WITHOUT granting mic permission
6. **Expected:** Permission rationale dialog appears OR Android system permission prompt appears
7. **Expected:** Button is pressable (not dead/unresponsive)

**Test 2 - ADMIN Location Visibility:**
1. SSH to connectvoice server: `ssh connectvoice`
2. Rebuild server: `cd voiceping-router && docker compose down && docker compose up -d --build`
3. Login to web UI as ADMIN user
4. Open dispatch map view
5. Have an Android field user move (with location permission granted and updates sending)
6. **Expected:** Location marker appears/updates on dispatch map for ADMIN user
7. **Expected:** Location marker also visible for DISPATCH user (no regression)

**Success criteria:**
- PTT button triggers permission flow when pressed without mic permission
- ADMIN users see location markers on dispatch map
- DISPATCH users still see location markers (no regression)
  </verify>
  <done>
Both fixes verified working in production-like environment with no regressions.
  </done>
</task>

</tasks>

<verification>
**Overall checks:**
- [ ] Android app compiles without errors
- [ ] TypeScript compiles without errors
- [ ] PTT button clickable when mic permission not granted
- [ ] Permission request dialog appears when PTT pressed without permission
- [ ] ADMIN users receive location WebSocket messages
- [ ] DISPATCH users still receive location WebSocket messages (no regression)
</verification>

<success_criteria>
1. Android PTT button is pressable regardless of mic permission state
2. Pressing PTT without permission triggers ViewModel's permission request flow
3. ADMIN users see real-time location markers on dispatch map
4. DISPATCH users continue to see location markers (no regression)
5. No compilation or linting errors introduced
</success_criteria>

<output>
After completion, create `.planning/quick/9-fix-audio-ptt-not-working-and-location-n/9-SUMMARY.md`
</output>
