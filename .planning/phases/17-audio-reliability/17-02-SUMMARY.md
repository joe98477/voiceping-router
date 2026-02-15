---
phase: 17-audio-reliability
plan: 02
subsystem: audio-reliability
tags: [server-ack, visual-feedback, confirmation-tone, dev-stats, audio-quality]
dependency-graph:
  requires:
    - 17-01 (TonePlayer.playConfirmationTone, HapticFeedback.vibrateErrorRelease, PttState.Error)
  provides:
    - PRODUCER_ACK signaling protocol (server confirms audio stream received)
    - ACK SharedFlow in PttManager (emits true/false after 2s timeout)
    - PTT button green/red flash animation (300ms)
    - Confirmation tone toggle (independent of roger beep)
    - DevStatsScreen (signaling RTT, consumer stats stubs)
  affects:
    - Server signaling protocol (new PRODUCER_ACK message type)
    - PTT button visual states (ACK flash overrides all, amber for send degradation)
    - Audio settings UI (new confirmation tone toggle)
    - Dev navigation (new dev-stats route)
tech-stack:
  added:
    - SignalingType.PRODUCER_ACK in protocol.ts and SignalingMessage.kt
    - DevStatsScreen.kt (Compose UI for audio quality metrics)
  patterns:
    - SharedFlow for ACK results (multi-collector pattern for tone + flash)
    - ViewModel ACK collector with delay for 300ms flash duration
    - Debug-only navigation (BuildConfig.DEBUG guard)
    - NavGraph dependency injection (SignalingClient passed from MainActivity)
key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/presentation/settings/DevStatsScreen.kt
  modified:
    - src/shared/protocol.ts (PRODUCER_ACK enum)
    - android/app/src/main/java/com/voiceping/android/data/network/dto/SignalingMessage.kt (PRODUCER_ACK enum)
    - src/server/signaling/handlers.ts (handleProducerAck method + routing)
    - src/server/signaling/websocketServer.ts (PRODUCER_ACK case routing)
    - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt (startProducing returns String, fixed IceConnectionState enums)
    - android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt (ACK SharedFlow, ACK request after producer creation)
    - android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt (ACK collector, onPttError wiring)
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/PttButton.kt (ackFlashColor, isAmber params)
    - android/app/src/main/java/com/voiceping/android/presentation/channels/components/BottomBar.kt (pass new params)
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt (ACK flash state, confirmation tone setting)
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt (collect and pass new params)
    - android/app/src/main/java/com/voiceping/android/presentation/settings/SettingsViewModel.kt (confirmationToneEnabled getter/setter)
    - android/app/src/main/java/com/voiceping/android/presentation/settings/SettingsScreen.kt (confirmation tone toggle, dev stats link)
    - android/app/src/main/java/com/voiceping/android/presentation/navigation/NavGraph.kt (dev-stats route)
    - android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt (inject SignalingClient)
decisions:
  - ACK flash overrides all other button colors (highest priority)
  - ACK flash duration: 300ms (green for success, red for failure)
  - ACK request timeout: 2 seconds (after timeout, emit false)
  - Amber PTT state shows when send transport degraded but receive still working
  - Confirmation tone independent toggle from roger beep (both can be enabled/disabled separately)
  - DevStatsScreen only accessible in debug builds via BuildConfig.DEBUG guard
  - Consumer stats stubbed pending device validation (crow-misia API undocumented)
  - NavController passed to SettingsScreen for dev-stats navigation (not ViewModel approach)
metrics:
  duration_seconds: 701
  tasks_completed: 4
  files_modified: 16
  files_created: 1
  commits: 4
  completed_at: 2026-02-15
---

# Phase 17 Plan 02: Server ACK + Visual/Audio Feedback Summary

**One-liner:** Server PRODUCER_ACK with green/red PTT button flash (300ms), confirmation tone toggle, and debug-only dev stats screen showing signaling RTT.

## What Was Built

### Server-Side ACK Protocol
- Added `PRODUCER_ACK` signaling message type to protocol.ts and SignalingMessage.kt
- Implemented `handleProducerAck` server handler in handlers.ts
- Server verifies producer ownership via `userProducers` map (userId:channelId -> producerId)
- Server responds with `success: true` if producer found, error message if not

### Client-Side ACK Infrastructure
- MediasoupClient.startProducing() now returns producer ID string (needed for ACK request)
- PttManager sends PRODUCER_ACK after producer creation with 2s timeout
- PttManager emits ACK result via SharedFlow (true = success, false = timeout/error)
- ChannelRepository collects ACK results and plays confirmation tone via TonePlayer
- ChannelRepository wires onPttError to haptic double-buzz feedback

### Visual Feedback (PTT Button Flash)
- PttButton accepts `ackFlashColor: Color?` parameter (null when no flash active)
- PttButton accepts `isAmber: Boolean` parameter (send transport degraded, receive ok)
- ChannelListViewModel collects ACK results and manages 300ms flash state
- Flash color priority: ACK flash > mic permission > amber > denied/error > transmitting > requesting > busy > idle
- Green flash (0xFF4CAF50) for success, red flash (0xFFD32F2F) for failure
- PttState.Error renders as dark red (0xFFB71C1C, same as Denied)

### Confirmation Tone Settings UI
- SettingsViewModel exposes confirmationToneEnabled StateFlow with getter/setter
- SettingsScreen has "Confirmation tone" toggle in Audio section
- Toggle subtitle: "Play tone after transmission confirms delivery"
- Independent from roger beep toggle (both can be enabled/disabled separately)

### Developer Stats Screen
- Created DevStatsScreen.kt showing signaling RTT from SignalingClient.latency
- Consumer stats (jitter, packet loss, packets received) stubbed pending device validation
- Only accessible in debug builds via BuildConfig.DEBUG guard
- Added "Audio Stats" link in SettingsScreen Developer section
- NavGraph has "dev-stats" route wired to DevStatsScreen
- SignalingClient injected in MainActivity and passed to NavGraph

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed IceConnectionState enum comparisons in MediasoupClient**
- **Found during:** Task 2 compilation
- **Issue:** Plan 17-01 introduced string comparisons for IceConnectionState (e.g., `state == "disconnected"`), but transport.connectionState returns PeerConnection.IceConnectionState enum, not string
- **Fix:** Changed all string comparisons to enum comparisons (e.g., `state == PeerConnection.IceConnectionState.DISCONNECTED`)
- **Files modified:** android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
- **Commit:** 7d3e883 (included in Task 1 commit)

## Verification Results

All verification criteria passed:

✅ Server TypeScript compiles with PRODUCER_ACK message type
✅ MediasoupClient.startProducing() returns producer ID string
✅ PttManager sends PRODUCER_ACK and emits result via SharedFlow
✅ ChannelRepository collects ACK results and plays confirmation tone
✅ PttButton accepts ackFlashColor and isAmber parameters
✅ ChannelListViewModel collects ackResult and manages flash state
✅ SettingsScreen has confirmation tone toggle
✅ DevStatsScreen exists and shows RTT from SignalingClient.latency
✅ NavGraph has dev-stats route
✅ Android compiles successfully: `./gradlew compileDebugKotlin` passes

## Open Questions / Follow-up

1. **Consumer stats parsing:** crow-misia Consumer.stats API undocumented. DevStatsScreen stubs implemented, pending device validation (17-RESEARCH.md open question #1).
2. **Amber PTT state trigger:** isAmberPtt StateFlow exposed in ChannelListViewModel but not yet populated. Plan 17-03 will wire transport health monitoring to set this state when send transport degrades.

## Self-Check

Verifying key artifacts exist:

```bash
# Check created files
[ -f "android/app/src/main/java/com/voiceping/android/presentation/settings/DevStatsScreen.kt" ] && echo "FOUND: DevStatsScreen.kt" || echo "MISSING: DevStatsScreen.kt"

# Check commits
git log --oneline --all | grep -q "7d3e883" && echo "FOUND: 7d3e883" || echo "MISSING: 7d3e883"
git log --oneline --all | grep -q "b1a9b94" && echo "FOUND: b1a9b94" || echo "MISSING: b1a9b94"
git log --oneline --all | grep -q "5dceba7" && echo "FOUND: 5dceba7" || echo "MISSING: 5dceba7"
git log --oneline --all | grep -q "57f84cf" && echo "FOUND: 57f84cf" || echo "MISSING: 57f84cf"
```

## Self-Check: PASSED

All artifacts verified present.
