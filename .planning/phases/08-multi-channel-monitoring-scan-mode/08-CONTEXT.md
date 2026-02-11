# Phase 8: Multi-Channel Monitoring & Scan Mode - Context

**Gathered:** 2026-02-11
**Status:** Ready for planning

<domain>
## Phase Boundary

User can monitor up to 5 channels simultaneously with mixed audio playback. Bottom bar auto-switches to show the active channel (scan mode), then returns to primary after transmission ends. User can manually switch channels and configure scan behavior. Muted/unmonitored channels unsubscribe from audio to save bandwidth.

</domain>

<decisions>
## Implementation Decisions

### Bottom bar scan behavior
- Instant swap (no animation) when a non-primary channel has an active speaker — bottom bar immediately shows that channel
- Audio continues on ALL monitored channels regardless of which channel the bottom bar shows — bottom bar is visual focus only, not an audio switch
- When multiple non-primary channels are active simultaneously, bottom bar shows the most recently started transmission
- 2-3 second pause after transmission ends before returning to primary channel
- Channel name color change to distinguish when showing a scanned (non-primary) channel vs primary
- Silent switch — no tone/beep when bottom bar changes channels; incoming audio itself is the signal
- Per-channel RX squelch still plays for each channel's speaker start — helps user distinguish which channel is active

### Manual channel lock
- User can tap bottom bar to manually switch to a specific monitored channel — this LOCKS the bottom bar to that channel
- When locked, scan mode auto-switching is paused — bottom bar stays on the locked channel
- Tap bottom bar again to unlock and return to scan mode / primary channel
- Audio from all monitored channels continues playing even when locked — locking only affects visual focus and PTT target

### PTT target
- Setting in profile drawer: "Always primary" vs "Displayed channel"
- When "Displayed channel": PTT targets whatever channel the bottom bar is currently showing (including scanned or locked channel)
- When "Always primary": PTT always targets primary regardless of bottom bar state
- Notification PTT follows the same setting as in-app PTT target

### Multi-channel audio mixing
- Setting in profile drawer: "Equal volume" vs "Primary priority"
- Equal volume: all active channels play at the same volume
- Primary priority: primary channel at full volume, non-primary channels play quieter
- Per-channel volume control (0-100% slider) accessible via small settings icon on each channel row — not in the foreground UI
- "Mute all except primary" quick action in the top bar

### Mute behavior
- Muting a channel triggers server-side unsubscribe — stops audio from server to save bandwidth
- Muted channels show NO visual activity indicators — fully silent, no speaker name or pulse
- Unmuting immediately re-subscribes to server — if someone is mid-transmission, user hears it (with slight rejoin delay)

### Channel monitoring management
- Tap channel to join (toggle join/leave) — same tap gesture as current single-channel
- First joined channel becomes primary by default
- Long-press any joined channel to set it as primary — flexible reassignment
- Joined/monitored channels have filled/solid background; unjoined channels are outlined or dimmed
- When user tries to join a 6th channel: toast message "Maximum 5 channels. Leave a channel to join another."

### Foreground notification
- Shows primary channel name + monitoring count: "Alpha (monitoring 3 others)"
- Notification PTT follows the same PTT target setting as in-app

### Scan mode settings (Profile drawer)
- Scan mode toggle: on/off (default: ON when 2+ channels joined)
- PTT target: "Always primary" / "Displayed channel"
- Return delay: slider 2-5 seconds (default: 2-3s)
- Audio mix mode: "Equal volume" / "Primary priority"
- All settings persist across sessions via DataStore
- Monitored channels also persist — same channels on next launch

### Claude's Discretion
- Exact return delay default value within the 2-3s range
- Per-channel settings icon design and placement details
- Primary channel visual indicator beyond filled background (e.g., star badge)
- Animation details for channel join/leave transitions
- DataStore schema for persisting monitored channels

</decisions>

<specifics>
## Specific Ideas

- Bottom bar is purely a visual focus indicator — it does NOT control which channels play audio
- Radio scan analogy: scan mode stops on whoever is transmitting, then returns to home channel
- Per-channel settings should be unobtrusive — small icon on channel row, not cluttering the main view
- "Mute all except primary" should be quick to reach in the top bar for noisy multi-channel situations

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-multi-channel-monitoring-scan-mode*
*Context gathered: 2026-02-11*
