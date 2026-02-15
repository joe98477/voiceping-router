# Phase 17: Audio Reliability - Context

**Gathered:** 2026-02-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix intermittent PTT silence and harden audio stream timing. This phase covers producer retry logic, WebRTC jitter buffer tuning, Opus FEC configuration, transport health monitoring with auto-cleanup, and server ACK for transmission confirmation. New audio features (noise cancellation, echo suppression, etc.) are out of scope.

</domain>

<decisions>
## Implementation Decisions

### Retry Feedback
- PTT auto-releases on producer creation failure (no hold-during-retry)
- Toast notification "Unable to transmit. Check your connection." during retry — user-friendly only, no technical detail
- 2 retry attempts with exponential backoff before giving up
- Retry applies to all PTT sources equally (on-screen, volume keys, Bluetooth)
- Audio captured during retry window is buffered and sent once producer succeeds
- No cooldown after failure — user can re-press PTT immediately
- Distinct haptic pattern (double-buzz) on failed PTT release vs normal release
- New PttState.Error state (separate from PttState.Denied) with failure reason
- All retry attempts, backoff timing, and outcomes logged to Logcat at debug level

### Transmission Confirmation
- Green flash (300ms) on PTT button after successful server ACK — always, regardless of PTT source
- Red flash (300ms) on PTT button when server ACK not received within 2 seconds
- Separate "Confirmation tone" toggle in settings (independent of existing roger beep toggle)
- Confirmation tone is a variation of the existing roger beep: normal pitch = success, lower pitch = failure
- Server ACK level: server acknowledges receipt of audio stream (not end-to-end listener confirmation)

### Failure Behavior
- Mid-transmission transport failure: hold PTT state for ~2 seconds attempting transport reconnect, then release if fails
- Orphaned transport cleanup (15s disconnect): silent, no user-facing indicator
- Incoming audio (RX) stream failure: silent auto-recovery in background
- Full disconnect (both transports fail): auto-rejoin with exponential backoff
- 5 auto-rejoin attempts max, then persistent "Unable to connect" banner with manual "Retry" button
- PTT disabled (grayed out) during auto-rejoin attempts
- Partial operation supported: if only send transport fails, user can still hear others
- Amber PTT button during partial failure (send broken, receive working)
- Tapping amber PTT shows toast: "Transmit unavailable — reconnecting..."
- PTT auto-recovers to normal state when send transport reconnects (no toast, silent transition)

### Audio Quality Tuning
- Balanced approach: default to low latency, adapt toward clarity on poor networks
- Target end-to-end latency: under 500ms (standard two-way radio feel)
- Audio quality settings fixed by app — no user-facing controls
- Graceful degradation on poor networks: reduce bitrate, increase FEC redundancy (choppy audio better than no audio)
- Opus DTX disabled — continuous stream during PTT hold (comfort noise, smoother listening)
- Opus FEC always enabled — proactive protection, no gap when loss starts
- Adaptive jitter buffer: start small, grow on packet loss/jitter, shrink when network improves
- Audio quality metrics (jitter, packet loss, RTT) logged to Logcat AND exposed via hidden developer stats screen in settings

</decisions>

<specifics>
## Specific Ideas

- Confirmation tone is useful when screen is off and user is using a headset — can't see visual flash
- Roger beep pitch variation for confirmation: same sound, different pitch to indicate success/failure
- Amber PTT state is a new visual state distinct from both normal (transmit-ready) and disabled (no mic permission)
- Field workers need comms at all costs — degrade quality rather than drop connection on poor networks

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 17-audio-reliability*
*Context gathered: 2026-02-15*
