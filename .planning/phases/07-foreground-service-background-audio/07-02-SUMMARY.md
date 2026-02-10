---
phase: 07-foreground-service-background-audio
plan: 02
subsystem: audio
tags: [audio-focus, phone-call-detection, ptt, android, kotlin]

# Dependency graph
requires:
  - phase: 06-single-channel-ptt-audio
    provides: PttManager with releasePtt() and AudioRouter with audio focus management
provides:
  - AudioRouter phone call detection via OnAudioFocusChangeListener (no READ_PHONE_STATE permission)
  - PttManager forceReleasePtt() for phone call interruption with distinct callback
affects: [07-03, channel-audio-management, ptt-interruption]

# Tech tracking
tech-stack:
  added: []
  patterns: [audio-focus-listener-pattern, force-release-ptt-pattern, phone-call-detection-without-permissions]

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/voiceping/android/data/audio/AudioRouter.kt
    - android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt

key-decisions:
  - "Use AUDIOFOCUS_LOSS_TRANSIENT to detect phone calls without READ_PHONE_STATE permission"
  - "Separate forceReleasePtt() from releasePtt() for distinct audio feedback (double beep vs roger beep)"
  - "Enable automatic ducking with setWillPauseWhenDucked(false) for API 26+"

patterns-established:
  - "Audio focus listener pattern: detect phone calls via AUDIOFOCUS_LOSS_TRANSIENT, resume via AUDIOFOCUS_GAIN with isInPhoneCall guard"
  - "Force-release pattern: onPttInterrupted callback for phone call interruption, distinct from onPttReleased for intentional stop"

# Metrics
duration: 137s
completed: 2026-02-10
---

# Phase 07 Plan 02: Phone Call Detection & PTT Interruption

**Phone call detection via audio focus changes (no dangerous permissions) with distinct PTT force-release callback for call interruption double beep**

## Performance

- **Duration:** 2 min 17 sec
- **Started:** 2026-02-10T13:09:18Z
- **Completed:** 2026-02-10T13:11:35Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- AudioRouter detects phone calls via OnAudioFocusChangeListener without READ_PHONE_STATE permission
- Audio focus uses AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK with automatic ducking enabled
- PttManager can force-release PTT during phone call with distinct onPttInterrupted callback
- Two separate release paths ensure correct audio feedback: roger beep for intentional stop, double beep for call interruption

## Task Commits

Each task was committed atomically:

1. **Task 1: Add OnAudioFocusChangeListener to AudioRouter for phone call detection** - `21924b9` (feat)
2. **Task 2: Add forceReleasePtt() to PttManager for phone call interruption** - `63cbd1b` (feat)

## Files Created/Modified
- `android/app/src/main/java/com/voiceping/android/data/audio/AudioRouter.kt` - Added OnAudioFocusChangeListener, phone call callbacks (onPhoneCallStarted/onPhoneCallEnded), isInPhoneCall tracking, automatic ducking, isInPhoneCall() accessor
- `android/app/src/main/java/com/voiceping/android/data/ptt/PttManager.kt` - Added onPttInterrupted callback, forceReleasePtt() method for phone call interruption with distinct audio feedback

## Decisions Made

**Use AUDIOFOCUS_LOSS_TRANSIENT for phone call detection:**
- Avoids dangerous READ_PHONE_STATE permission
- Works for both incoming and outgoing calls
- Reliable signal for phone call start/end

**Separate forceReleasePtt() from releasePtt():**
- Normal release: onPttReleased callback plays roger beep (intentional stop)
- Force release: onPttInterrupted callback plays double beep (call interruption)
- Two code paths keep audio feedback distinct and clear to users

**Enable automatic ducking with setWillPauseWhenDucked(false):**
- API 26+ feature for automatic volume reduction
- System handles ducking for transient sounds (navigation, notifications)
- Radio audio continues playing during ducking events

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phone call detection infrastructure complete. Ready for Plan 03 to:
- Wire AudioRouter callbacks to ChannelRepository
- Implement TonePlayer.playCallInterruptionBeep() for double beep
- Wire PttManager.onPttInterrupted to TonePlayer
- Handle phone call pause/resume flow for channel audio playback

## Self-Check: PASSED

All SUMMARY.md claims verified:
- Files modified: AudioRouter.kt ✓, PttManager.kt ✓
- Commits: 21924b9 (Task 1) ✓, 63cbd1b (Task 2) ✓

---
*Phase: 07-foreground-service-background-audio*
*Completed: 2026-02-10*
