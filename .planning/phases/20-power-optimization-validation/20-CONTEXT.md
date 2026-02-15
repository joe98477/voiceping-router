# Phase 20: Power Optimization & Validation - Context

**Gathered:** 2026-02-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Battery profiling and adaptive power management with all v4.0 features active (audio, location, monitoring). Optimize wake lock scoping, network polling, and location batching for field use. Validate battery consumption target of <6%/hour with screen off in idle monitoring scenario.

</domain>

<decisions>
## Implementation Decisions

### Wake lock behavior
- Release wake lock after configurable timeout of no audio activity (no transmit AND no incoming audio across all monitored channels)
- Timeout is server-configurable per user group, delivered via existing auth response payload
- Default timeout: 300 seconds (5 minutes) for both regular users and dispatch
- Client falls back to 300s default if server doesn't provide the config value
- Minimum hold: after reacquisition, hold wake lock for at least the full timeout period to prevent rapid toggling
- Wake lock reacquisition triggered by WebSocket events (server push) when audio activity resumes
- No PTT warmup delay: instant wake lock reacquisition on PTT press, accept any brief initial unreliability
- Wake lock management is invisible to users (no UI indicators)
- Coordinated location: when wake lock releases, location tracking interval doubles
- Location interval doubling is tied directly to wake lock release event (not a separate config)
- Location recovery uses gradual ramp-up over 1-2 cycles when audio resumes
- Location snap-back is immediate when battery saver is disabled (different from audio-idle recovery)

### Network polling strategy
- Poll WebRTC stats (jitter, packet loss, bitrate) AND channel/team membership (belt-and-suspenders for missed WebSocket events)
- Active channel polling: 5-second intervals
- Idle channel polling: 15-second intervals
- Activity window: 60 seconds — channel stays at 5s polling for 60s after last audio activity, then drops to 15s
- Fixed polling values (5s/15s) — not server-configurable
- Per-channel activity timers: each monitored channel independently tracks its own active/idle state
- Suspend polling on empty channels (zero consumers); resume via WebSocket when someone joins
- New channel join treated as activity: starts at 5s polling for 60-second window
- Continue 15s idle polling even when wake lock is released (maintain channel monitoring at all times)
- Dev stats screen shows current polling interval per channel

### Battery target & measurement
- Target: <6%/hour with screen off in idle monitoring scenario (joined channels, listening, location tracking, no active PTT)
- Measurement tool: Android Battery Historian for detailed power breakdown per subsystem
- Test duration: 2-hour profiling session on any available physical device
- Profiling timing: after optimizations only (v3.0 5%/hr baseline serves as pre-optimization reference)
- If target missed: document results and ship — optimizations being in place is sufficient for v4.0
- Results captured in dedicated .planning/BATTERY.md with historical data across milestones
- BATTERY.md includes v3.0 baseline and tracks before/after deltas per optimization

### Power mode awareness
- App detects and responds to Android battery saver mode
- Battery saver reduces location frequency further (4x multiplier instead of 2x) — audio and polling unchanged
- Toast notification shown every time app is opened while battery saver is active: "Battery saver active — location updates reduced"
- Toast shows on every app open with battery saver on (not just first detection)
- Immediate location snap-back when battery saver is disabled (no gradual ramp)
- No in-app low-power toggle — rely on system battery saver only
- Battery saver state and current location multiplier visible in dev stats screen

### Claude's Discretion
- Exact Battery Historian setup and report format
- Location multiplier implementation details (how to integrate with existing MotionDetector tiers)
- WebSocket event handling for wake lock reacquisition (which events trigger it)
- Dev stats screen layout for new power management fields
- BATTERY.md document structure and formatting

</decisions>

<specifics>
## Specific Ideas

- Wake lock timeout is configurable per user group via server auth response — enables admins to tune power vs responsiveness per deployment
- Coordinated systems: wake lock, location, and polling are interlinked — wake lock release cascades to location doubling
- Belt-and-suspenders polling: both WebRTC stats and channel membership polled to catch missed WebSocket events
- Battery saver is location-only reduction (4x) — audio reliability is never compromised for power savings

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 20-power-optimization-validation*
*Context gathered: 2026-02-16*
