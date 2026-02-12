# Phase 9: Hardware PTT & Bluetooth Integration - Context

**Gathered:** 2026-02-12
**Status:** Ready for planning

<domain>
## Phase Boundary

User can operate PTT via hardware buttons (volume keys, Bluetooth headset) and audio auto-routes to connected devices. Includes boot-start capability and settings UI for hardware configuration. Rugged phone dedicated PTT buttons (Sonim, Kyocera) are deferred. Network resilience and UX polish are Phase 10.

</domain>

<decisions>
## Implementation Decisions

### Volume key PTT mapping
- Press-and-hold only (no toggle mode for hardware keys)
- User-configurable which key: volume-up, volume-down, or both
- Quick tap adjusts volume normally, long-press (>300ms threshold) triggers PTT — dual purpose
- Works on lock screen only when foreground service is active (channel joined)

### Bluetooth headset PTT
- User-configurable button mapping: media button, call button, or dedicated PTT button
- Auto-release PTT on Bluetooth disconnect — play interrupted beep, fall back audio
- Auto-reconnect to last paired Bluetooth headset when in range
- Rugged phone dedicated PTT buttons deferred to Phase 10

### Audio routing priority
- Auto-switch audio to Bluetooth when connected (seamless, no prompt)
- Fallback to previous output on Bluetooth disconnect (speaker or earpiece, whatever was active before BT)
- Priority order: last connected device wins (BT connect after wired → BT gets audio; wired connect after BT → wired gets audio)
- Small icon in top bar showing current audio output device (speaker, earpiece, BT, or wired headset)

### Boot-start behavior
- Simple on/off toggle in settings, off by default
- When enabled, app starts as foreground service on device boot
- No onboarding prompt — user discovers in settings

### Settings layout
- Dedicated "Hardware Buttons" section in settings (separate from PTT mode/tones settings)
- Sub-items: Volume Key PTT config, Bluetooth PTT Button config
- "Press-to-detect" screen for button learning — "Press any button" shows detected key code, helps users find their headset's PTT button
- Per-channel volume stays in existing long-press channel dialog from Phase 8 (no change)

### Claude's Discretion
- Long-press threshold tuning (starting point: 300ms)
- MediaSession vs MediaButtonReceiver implementation for Bluetooth button interception
- Key event interception approach (onKeyDown/onKeyUp vs accessibility service)
- Boot receiver implementation details (BOOT_COMPLETED vs LOCKED_BOOT_COMPLETED)
- Audio routing icon design

</decisions>

<specifics>
## Specific Ideas

- Dual-purpose volume key: quick tap = volume, long hold = PTT. This avoids losing volume control entirely when PTT is mapped to volume keys.
- "Press any button" detection screen for setting up Bluetooth headset buttons — similar to game controller button mapping UX.
- Auto-reconnect to last Bluetooth headset mirrors standard music app behavior — should feel natural.

</specifics>

<deferred>
## Deferred Ideas

- Rugged phone dedicated PTT buttons (Sonim, Kyocera) — deferred to Phase 10 per user decision
- Centralized audio settings with all volume controls — keep per-channel volume in channel dialog for now

</deferred>

---

*Phase: 09-hardware-ptt-bluetooth*
*Context gathered: 2026-02-12*
