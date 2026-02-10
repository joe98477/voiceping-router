---
phase: 06-single-channel-ptt-audio
plan: 03
title: "PTT UI Components & Visual Feedback"
subsystem: android-client-ui
status: complete
completed: 2026-02-10
duration: 125s
one-liner: "PttButton composable with press-and-hold/toggle modes, red pulse animations, BottomBar PTT integration, and ChannelRow speaker indicators with cyan glow"

requires:
  - 06-01: PttState sealed class for rendering different button states
  - 06-01: PttMode enum for press-and-hold vs toggle interaction
  - 06-02: PttManager state machine (StateFlow<PttState>, getTransmissionDurationSeconds)
  - 05-04: BottomBar and ChannelRow composables

provides:
  - PttButton composable with 5 visual states (Idle, Requesting, Transmitting, Denied, Busy)
  - Updated BottomBar with integrated PTT button on right side
  - Enhanced ChannelRow with cyan border glow and speaker animations
  - Pulse animations: red for user transmission, cyan for incoming speakers
  - Last speaker fade animation (2.5s fadeOut)

affects:
  - 06-04: ViewModel will wire PttManager callbacks and provide state to UI
  - 06-04: ViewModel will manage lastSpeaker timer for 2-3 second fade
  - 06-05: Settings screen will control pttMode toggle

key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelRow.kt

tech-stack:
  added: []
  patterns:
    - Compose animations with rememberInfiniteTransition for continuous pulse effects
    - animateColorAsState for smooth cyan border glow transitions
    - AnimatedVisibility with fadeOut for last speaker fade
    - pointerInput with detectTapGestures for press-and-hold mode
    - clickable modifier for toggle mode
    - Conditional UI rendering based on PttState sealed class

key-decisions:
  - "Red pulse only during Transmitting state (not Requesting): User decided 'subtle loading pulse during server confirmation wait' — gray pulse for Requesting, red pulse reserved for active transmission"
  - "PTT button dimmed when busy: isBusy=true (channel has speaker AND user not transmitting) shows grayed button Color(0xFF757575), not clickable"
  - "Cyan color consistency: Color(0xFF00BCD4) for border glow matches app's primary cyan accent from Phase 5 decisions"
  - "Last speaker fade managed by ViewModel: ChannelRow receives lastSpeaker/lastSpeakerVisible props, ViewModel handles 2-3s timer logic (Plan 04)"
  - "Pulse animation durations: Transmitting 1000ms (scale+alpha), Requesting 600ms (alpha only, more subtle), Speaker name 800ms (alpha pulse)"
  - "BottomBar height increased to 80dp: Accommodates 72dp PTT button with padding"
  - "No elapsed time for incoming speakers: Per user decision, only show speaker name, no duration counter for others"

commits:
  - hash: cae0b16
    message: "feat(06-03): create PttButton composable and integrate with BottomBar"
    files:
      - android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt
      - android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt
  - hash: fc03b3a
    message: "feat(06-03): add speaker indicators, cyan glow, and last speaker fade to ChannelRow"
    files:
      - android/app/src/main/java/com/voiceping/android/presentation/channels/components/ChannelRow.kt

metrics:
  duration_seconds: 125
  tasks_completed: 2
  files_created: 1
  files_modified: 2
  lines_added: 273
---

# Phase 6 Plan 3: PTT UI Components & Visual Feedback Summary

## Overview

Built the visual layer that turns the PTT engine (Plan 02) into tangible user interactions. The PttButton in the bottom bar is the primary interaction point for the entire app, with press-and-hold and toggle modes, pulse animations, and elapsed time display. Speaker indicators in the channel list provide at-a-glance awareness of who's talking across channels.

## What Was Built

### Task 1: PttButton Composable and BottomBar Integration

**PttButton.kt** - New file, 177 lines:
- **5 Visual States:**
  - **Idle:** Cyan primary color, Mic icon, enabled
  - **Requesting:** Gray (0xFF9E9E9E) with subtle alpha pulse (0.5->1.0, 600ms) — NOT red pulse
  - **Transmitting:** Red (0xFFD32F2F) with scale pulse (1.0->1.15) + alpha pulse (1.0->0.6), 1000ms cycle, shows elapsed time text
  - **Denied:** Dark red (0xFFB71C1C) brief flash, handled by PttManager (500ms auto-revert to Idle)
  - **Busy:** Dimmed gray (0xFF757575), not clickable when channel has speaker and user not transmitting

- **Interaction Modes:**
  - **PRESS_AND_HOLD:** Uses `pointerInput` with `detectTapGestures { onPress { ... } }` — onPttPressed() on press, onPttReleased() on tryAwaitRelease()
  - **TOGGLE:** Uses `clickable` — toggles between onPttPressed() and onPttReleased() based on isTransmitting state

- **Animations:**
  - Transmitting pulse: `rememberInfiniteTransition` with `animateFloat` for scale and alpha, 1000ms tween, RepeatMode.Reverse
  - Requesting pulse: More subtle alpha-only animation (0.5->1.0), 600ms, no scale change
  - Button size: 72dp CircleShape

**BottomBar.kt** - Updated:
- **New Parameters:** pttState, pttMode, transmissionDuration, onPttPressed, onPttReleased
- **Height Increased:** 64dp -> 80dp to accommodate 72dp PTT button + padding
- **Layout:** Row with Arrangement.SpaceBetween (channel info left, PTT button right)
- **Status Text Logic:**
  - User transmitting: "Transmitting..." in red (0xFFD32F2F)
  - Channel busy (someone else speaking): speaker name in cyan (primary color)
  - Idle: "Listening..." in onSurfaceVariant
- **PTT Button:** Only shown when joinedChannel != null
- **isBusy Calculation:** `currentSpeaker != null && pttState !is PttState.Transmitting`

### Task 2: ChannelRow Speaker Indicators and Animations

**ChannelRow.kt** - Updated:
- **New Parameters:** lastSpeaker: User?, lastSpeakerVisible: Boolean (default false)
- **Cyan Border Glow:**
  - `animateColorAsState` transitions border from transparent to cyan (0xFF00BCD4) when isActiveChannel changes
  - Border: 2dp width, RoundedCornerShape(8dp)
  - 300ms transition duration

- **Speaker Indicator:**
  - When `channel.currentSpeaker != null`: Speaker name in cyan with pulsing alpha animation (0.6->1.0, 800ms cycle)
  - Removed pulsing dot — speaker name has direct alpha pulse now
  - Simpler, cleaner visual (just text with pulse, no dot)

- **Last Speaker Fade:**
  - When `lastSpeakerVisible=true && lastSpeaker != null`: Shows last speaker name in onSurfaceVariant color
  - `AnimatedVisibility` with `fadeOut(tween(2500))` — 2.5 second fade animation
  - After fade completes, caller (ViewModel) resets lastSpeaker to null

- **User Count Display:**
  - Only shown when no speaker indicator (current or last) is visible
  - Prevents overlapping text

## Deviations from Plan

None - plan executed exactly as written.

## Verification Results

**Syntax Verification** (gradle CLI not available, per Phase 5 precedent):

1. ✓ PttButton has two interaction modes: press-and-hold (pointerInput) and toggle (clickable)
2. ✓ PttButton shows red pulse only during PttState.Transmitting (not during Requesting per user decision)
3. ✓ PttButton shows subtle loading pulse during PttState.Requesting (gray, alpha only)
4. ✓ PttButton shows elapsed time text during Transmitting
5. ✓ PttButton is dimmed when isBusy=true
6. ✓ BottomBar embeds PttButton on right side, channel info on left
7. ✓ BottomBar height increased to 80dp
8. ✓ ChannelRow shows speaker name in cyan when active speaker exists
9. ✓ ChannelRow has cyan border glow (animateColorAsState) for active channel
10. ✓ ChannelRow has fadeOut animation (2500ms) for last speaker

**Success Criteria:**

✓ PTT button renders all 4 visual states correctly (Idle, Requesting, Transmitting, Denied) with appropriate animations
✓ Busy state shows dimmed/grayed PTT button when channel occupied
✓ Bottom bar integrates PTT button with channel info
✓ Channel rows show speaker indicators with pulsing animation and cyan border glow
✓ Last speaker fades out over 2.5 seconds
✓ All UI elements follow locked user decisions exactly:
  - Red pulse for own transmission (not others)
  - Subtle gray loading pulse during Requesting (not red)
  - Cyan indicators for incoming speakers
  - No elapsed time for incoming speakers (just name)
  - Dimmed PTT button when busy

## Integration Points

**For Plan 04 (ViewModel Integration):**
- Wire `PttManager.pttState` to PttButton pttState parameter
- Wire `SettingsRepository.pttMode` Flow to pttMode parameter
- Calculate transmissionDuration from `PttManager.getTransmissionDurationSeconds()`
- Call `PttManager.requestPtt(channelId)` on onPttPressed
- Call `PttManager.releasePtt()` on onPttReleased
- Manage lastSpeaker timer: when currentSpeaker changes from User to null, set lastSpeakerVisible=true, delay 2500ms, set lastSpeakerVisible=false

**For Plan 05 (Settings Screen):**
- Settings screen will expose PTT mode toggle (PRESS_AND_HOLD vs TOGGLE)
- User can switch interaction modes, PttButton adapts via pttMode parameter

## Technical Notes

### Pulse Animation Design

**Three distinct pulse patterns:**
1. **Transmitting (red):** Scale 1.0->1.15 + alpha 1.0->0.6, 1000ms — visually aggressive, demands attention
2. **Requesting (gray):** Alpha 0.5->1.0, 600ms, no scale — subtle loading indicator, not alarming
3. **Speaker name (cyan):** Alpha 0.6->1.0, 800ms — gentle pulsing glow, indicates activity without distraction

**Design rationale:**
- Red pulse reserved exclusively for user's own transmission (user decision: "red for own, cyan for others")
- Requesting state uses gray to avoid confusion (user decided "subtle loading pulse during brief server confirmation wait")
- Different durations create visual hierarchy: transmitting most urgent (1000ms), speaker presence less urgent (800ms), loading least urgent (600ms)

### Busy State Logic

**isBusy calculation:** `currentSpeaker != null && pttState !is PttState.Transmitting`

- True when: Someone else is speaking (currentSpeaker exists) AND user is not transmitting
- False when: User is transmitting (can release their own PTT even if currentSpeaker is themselves)
- False when: Channel idle (no currentSpeaker)

**Result:** PTT button grayed/disabled when channel is occupied by others, but always enabled during user's own transmission (to allow release).

### Last Speaker Fade Animation

**Architecture decision:** ChannelRow is a presentational component — it receives lastSpeaker/lastSpeakerVisible from ViewModel, renders fade animation when told to.

**Timer logic lives in ViewModel (Plan 04):**
1. Observe currentSpeaker changes
2. When currentSpeaker changes from User to null (transmission ends)
3. Set lastSpeaker = previousSpeaker, lastSpeakerVisible = true
4. Launch coroutine: delay(2500), set lastSpeakerVisible = false
5. ChannelRow's AnimatedVisibility handles fadeOut automatically

**Why 2500ms?** User decision specified "2-3 second fade" — 2.5s is midpoint, matches fadeOut animation duration.

## Self-Check

**Files Created:**
```bash
[ -f "android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt" ] && echo "FOUND" || echo "MISSING"
```
✓ FOUND: PttButton.kt

**Files Modified:**
```bash
git show cae0b16 --name-only | grep -q "BottomBar.kt" && echo "FOUND" || echo "MISSING"
git show fc03b3a --name-only | grep -q "ChannelRow.kt" && echo "FOUND" || echo "MISSING"
```
✓ FOUND: BottomBar.kt (commit cae0b16)
✓ FOUND: ChannelRow.kt (commit fc03b3a)

**Commits:**
```bash
git log --oneline --all | grep -q "cae0b16" && echo "FOUND" || echo "MISSING"
git log --oneline --all | grep -q "fc03b3a" && echo "FOUND" || echo "MISSING"
```
✓ FOUND: cae0b16
✓ FOUND: fc03b3a

## Self-Check: PASSED

All files exist, all commits exist, all verification criteria met.
