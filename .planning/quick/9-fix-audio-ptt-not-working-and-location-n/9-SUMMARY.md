# Quick Task 9 Summary: Fix Audio PTT + Location on Dispatch Map

## Task
Fix two critical bugs: (1) Android PTT button unresponsive when mic permission not granted, (2) ADMIN users not receiving location data on dispatch map.

## Changes

### Task 1: Fix Android PTT button clickability (commit 37c1381)
**File:** `android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt`

**Problem:** Line 129 gated button clickability on `micPermissionGranted`:
```kotlin
val isClickable = micPermissionGranted && (!isBusy || isTransmitting)
```
When mic permission wasn't granted, the button had no touch handler — completely dead. The ViewModel's permission-request flow (rationale dialog, settings redirect) was never triggered.

**Fix:** Removed `micPermissionGranted` from clickability check:
```kotlin
val isClickable = !isBusy || isTransmitting
```

Button is now always pressable when channel isn't busy. ViewModel's `onPttPressed()` handles the permission flow.

### Task 2: Fix server location broadcasts for ADMIN users (commit 880da38)
**File:** `src/server/signaling/websocketServer.ts`

**Problem:** `sendToAllDispatchUsers()` only sent to `UserRole.DISPATCH`:
```typescript
if (ctx.role === UserRole.DISPATCH && ctx.ws.readyState === 1) {
```
ADMIN users (globalRole ADMIN maps to UserRole.ADMIN) viewing the dispatch map received no location updates.

**Fix:** Added ADMIN to the role check:
```typescript
if ((ctx.role === UserRole.DISPATCH || ctx.role === UserRole.ADMIN) && ctx.ws.readyState === 1) {
```

### Task 3: Manual verification
Checkpoint reached — awaiting physical device and production testing.

## Verification
- Android `compileDebugKotlin`: PASSED
- TypeScript `tsc --noEmit`: PASSED
- Manual testing: PENDING (checkpoint)

## Commits
| Commit | Description |
|--------|-------------|
| 37c1381 | fix(quick-9): enable PTT button when mic permission not granted |
| 880da38 | fix(quick-9): send location broadcasts to ADMIN users |
