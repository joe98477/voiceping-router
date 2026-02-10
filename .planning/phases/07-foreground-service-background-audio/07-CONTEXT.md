# Phase 7: Foreground Service & Background Audio - Context

**Gathered:** 2026-02-10
**Status:** Ready for planning

<domain>
## Phase Boundary

App functions as a pocket radio — foreground service keeps WebSocket and audio alive with screen off. Persistent notification provides status and controls. Audio pauses during phone calls and resumes after. App survives Doze mode with wake locks. Hardware PTT button mapping and Bluetooth integration are separate phases (Phase 9).

</domain>

<decisions>
## Implementation Decisions

### Notification controls
- Persistent notification shows active channel name and current speaker (minimal, like a music player showing song title)
- No PTT button in notification — PTT happens via hardware button or by opening the app
- Notification includes Mute button (toggle incoming audio) and Disconnect button (end service)
- Minimal updates only — update on channel change or service state change, NOT on every speaker change (saves battery)

### Screen-off feedback
- Incoming audio: squelch tone (from Phase 6) plays before audio starts, no vibration — audio itself is the feedback
- PTT feedback with screen off: same tones as Phase 6 (TX confirm tone on success, error tone on deny), no additional vibration
- Audio focus: duck other audio apps (music, podcasts) when channel audio plays, restore volume after — radio takes priority but doesn't kill music
- Volume: respect standard Android device volume controls, no minimum floor enforcement

### Phone call handling
- Incoming call detected: immediate pause of all channel audio (no fade)
- If user was transmitting during call: force-release PTT with a distinct double beep (different from normal roger beep) to signal call interruption to other users
- After call ends: auto-resume channel audio immediately, no delay
- Outgoing calls: same behavior as incoming — pause audio, force-release PTT, auto-resume after call ends

### Service lifecycle
- Service starts: on first channel join (not on login or app launch — no service if just browsing)
- Service stops: when user taps Disconnect in notification, logs out, OR leaves all monitored channels
- Force-kill (swipe from recents): service stays dead, does NOT auto-restart. User opens app again when ready
- Battery optimization: request exemption (ignore battery optimizations) but NOT on first launch — prompt when user first joins a channel (when service starts)

### Claude's Discretion
- Notification channel priority and importance level
- Wake lock strategy for Doze mode survival
- Heartbeat interval adjustments for background operation
- Service restart strategy (START_STICKY vs START_NOT_STICKY given no auto-restart on force-kill)
- Exact audio focus request type and ducking implementation

</decisions>

<specifics>
## Specific Ideas

- Double beep for call interruption should be distinct from normal roger beep — signals to other channel users that the speaker was interrupted by a phone call, not that they intentionally stopped
- "Pocket radio" metaphor: the app should behave like a physical radio you clip to your belt — always listening, minimal interaction needed

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-foreground-service-background-audio*
*Context gathered: 2026-02-10*
