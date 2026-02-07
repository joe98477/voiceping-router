# Phase 5: Android Project Setup & WebRTC Foundation - Context

**Gathered:** 2026-02-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Native Android app (Kotlin, API 26+) that connects to the existing mediasoup server. User can login, select an event, see channels grouped by team, join a single channel, and receive audio playback. No PTT transmission — receive-only. No multi-channel monitoring.

</domain>

<decisions>
## Implementation Decisions

### Login & Session Flow
- Branded splash screen with dark background, logo, fields slide up — shown only when login is required (not on auto-login)
- Email/password authentication only (no SSO, no PIN, no alternative methods)
- Always persist session — no "Remember me" toggle. JWT stored in Android Keystore (hardware-backed)
- Silent token refresh in background using stored credentials when JWT expires (1-hour TTL)
- If silent refresh fails: retry 2-3 times silently, then force logout with clear message explaining why
- Login validation errors shown inline under fields (not toast/snackbar)
- Brief loading/connecting screen after login while WebSocket connection establishes
- No "Remember me" toggle — field workers should never think about session persistence
- Logout accessible from profile/menu slide-out (not buried deep, not prominently on main screen)

### Event Picker & Channel List
- Event picker: simple flat list of events user has access to, tap to select
- Auto-skip event picker on launch if a saved event exists — go straight to channel list
- Event switching available from profile slide-out menu ("Switch Event" option)
- Channels grouped by team with team header labels (e.g., "Security Team" > Channel A, Channel B)
- Each channel row shows: channel name, active speaker indicator (pulsing), user count

### Channel Join & Audio Playback
- Toggle/checkbox pattern — user toggles channels on/off from the list itself, no separate channel screen
- Phase 5 limits to single channel only (one toggle active at a time, selecting another deselects previous)
- When someone transmits: show speaker name with pulsing animation on the channel's row
- Default audio output: earpiece (quiet/private mode) — user can switch to speaker later (Phase 6 adds toggle)

### App Shell & Navigation
- Profile icon at top-right of header — tapping slides out a side panel from the right
- Menu items: user name/email display, Switch Event, Settings, Logout, app version/about
- Persistent bottom bar (mini-player style) showing current/default joined channel
- Bottom bar shows: channel name, and speaker name when someone is transmitting
- Full dark theme across all screens — consistent radio-app feel
- Connection status: small colored dot overlaid on bottom-left of profile icon (green=connected, etc.)
- Disconnection: banner pops up at top of screen saying "Connecting..." — auto-closes on reconnect, changes to error message if connection fails

### Claude's Discretion
- Exact accent color for dark theme (suggestion: something visible but not garish)
- Loading screen design between login and channel list
- Team header styling in channel list
- Exact animation style for speaker pulse
- Bottom bar height and layout proportions
- Profile slide-out panel width and animation

</decisions>

<specifics>
## Specific Ideas

- Bottom bar is a "mini-player" pattern like music apps — always visible, shows what's active
- Profile icon with overlaid status dot is similar to Slack/Discord online status indicators
- Disconnection banner behavior: appears → auto-closes on success → shows error on failure (not persistent when healthy)
- Single-channel toggle means tapping a new channel automatically un-toggles the previous one
- Skip splash on auto-login — get to channels as fast as possible for field workers

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-android-setup-webrtc*
*Context gathered: 2026-02-08*
