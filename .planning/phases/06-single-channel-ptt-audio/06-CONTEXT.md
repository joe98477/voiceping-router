# Phase 6: Single-Channel PTT & Audio Transmission - Context

**Gathered:** 2026-02-10
**Status:** Ready for planning

<domain>
## Phase Boundary

User can transmit and receive PTT audio in a single channel with full bidirectional flow. Includes mic capture, transmission to server, busy state management, speaker indicators, audio output routing, and radio-style audio feedback tones. Multi-channel monitoring, scan mode, foreground service, and hardware PTT buttons are separate phases.

</domain>

<decisions>
## Implementation Decisions

### PTT button interaction
- Both press-and-hold and toggle modes, configurable in settings (default: press-and-hold)
- PTT button lives in the bottom bar, integrated with channel info
- No minimum hold duration — transmission starts instantly on press
- Toggle mode has configurable max transmission duration (default 60s), adjustable in settings
- Transmitting state: red pulsing button with elapsed time counter
- Visual + haptic + audio tone feedback on PTT press
- Distinct error tone + buzz pattern when PTT is denied (channel busy)
- No text toast for denied — tone + haptic is sufficient (speaker name already visible in channel)

### Busy state & speaker indicators
- Channel list: speaker name replaces idle text, with pulsing/glowing animation
- Channel list: active channel gets highlighted cyan border/glow when someone is speaking
- Bottom bar when busy: PTT button dimmed/grayed out, shows current speaker name
- Pulse color distinction: cyan for others speaking, red for your own transmission
- After speaker finishes: brief 2-3 second "last speaker" fade showing name, then transition to idle
- No elapsed time for incoming speakers — just name display

### PTT feedback & radio sounds
- Wait for server confirmation before showing transmitting state (not optimistic)
- Subtle loading pulse on PTT button during brief server confirmation wait
- PTT start tone: short chirp on press (configurable, separate toggle)
- Roger beep: short chirp on TX end (configurable, separate toggle, default on)
- Incoming RX squelch: brief radio open squelch sound when someone starts speaking (configurable, separate toggle)
- Closing squelch: brief squelch tail when incoming transmission ends (tied to RX squelch toggle)
- Three separate sound toggles in settings: PTT start tone, roger beep, RX squelch (open + close)

### Audio output routing
- Default output: loudspeaker (no headset/Bluetooth connected)
- Earpiece/speaker toggle located in settings drawer/side panel (not in main UI)
- Bluetooth auto-route: audio automatically switches to Bluetooth headset when connected
- Bluetooth disconnect: falls back to previous output setting (not always speaker)

### Claude's Discretion
- Exact tone/chirp sound design (frequency, duration)
- Pulse animation timing and easing curves
- Button sizing and padding within bottom bar
- Exact red shade for transmitting state
- Mic permission flow and error handling

</decisions>

<specifics>
## Specific Ideas

- Radio squelch sound for incoming transmissions — authentic two-way radio feel
- Red for transmitting matches classic radio "on-air" convention
- Cyan border glow on active channels provides at-a-glance awareness in channel list
- Three separate sound toggles gives users fine-grained control over their radio experience

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 06-single-channel-ptt-audio*
*Context gathered: 2026-02-10*
