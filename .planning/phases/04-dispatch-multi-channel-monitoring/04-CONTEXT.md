# Phase 4: Dispatch Multi-Channel Monitoring - Context

**Gathered:** 2026-02-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Dispatch users can monitor and communicate on multiple channels simultaneously (10-50 channels). This phase delivers a dispatch console with channel grid, selective muting, per-channel PTT, and visual activity indicators. The dispatch console replaces the current Dispatch page, integrating admin controls into a collapsible side drawer. Also includes channel name resolution for all user roles.

</domain>

<decisions>
## Implementation Decisions

### Grid layout & density
- Compact card layout: channel name, status pill, mute toggle, small PTT button, speaker name when active
- PTT button always visible on each card (no hover/focus reveal)
- ~220px minimum card width (fits ~8 columns on 1920px)
- Channels grouped by team in collapsible sections (team name as header)
- All team sections start expanded by default
- Active channels stay in alphabetical order within their team (no floating to top)
- Optimized for large monitors (1080p+), minimal mobile support
- Detailed stats bar at top: total channels, muted count, active speakers, event name, dispatch user info, uptime, connection health

### Channel names
- ALL users should see channel names, not channel IDs
- Channel IDs are background data for identification, visible in channel info
- Dispatch monitoring fetches from /api/events/:eventId/overview API (returns teams with channel names)
- General users also get channel name resolution in this phase (lightweight API or endpoint)

### Data source
- Dispatch monitoring uses /api/events/:eventId/overview to get team structure and channel names
- Team grouping comes from this API response

### Mute/unmute behavior
- Dimmed card when muted (opacity ~0.6), still shows activity indicators (pulsing dot + speaker name visible)
- Per-team mute toggle on each team section header (first click mutes all in team, second click unmutes all)
- Team toggle shows as "unmuted" unless ALL channels in team are muted
- Mute preferences persist across sessions via localStorage (full persistence)
- PTT still works on muted channels (mute only affects incoming audio, not outgoing)
- No global Mute All / Unmute All buttons (per-team toggles are sufficient)

### Activity indicators
- Both pulsing dot + speaker name AND card border glow when active (maximum visibility)
- Team headers are static (no activity count badges)
- Binary active/idle indicators only (no real-time audio level meters)
- Activity indicators still visible on muted (dimmed) cards

### Navigation & access
- Monitoring REPLACES the current Dispatch page at /event/:eventId/dispatch (same URL)
- Both Dispatch and Admin roles can access the monitoring view
- Events page link text changes from "Dispatch View" to "Dispatch Console"
- Admin control-plane features (user management, channel management) in a collapsible side drawer (slides from right, hidden by default)
- Logo/brand in header links back to Events page (home navigation)

### Claude's Discretion
- Exact CSS animations and transition timing
- Loading skeleton/spinner design
- Error state handling and retry UX
- Side drawer width and animation style
- Exact stats bar layout and spacing
- How to implement lightweight channel name API for general users

</decisions>

<specifics>
## Specific Ideas

- Dispatch console should feel like a control room — all channels visible at once, instant visual feedback on who's talking
- Team grouping is essential because dispatch operators think in terms of teams, not flat channel lists
- Side drawer for admin controls keeps monitoring grid unobstructed during normal operations

</specifics>

<deferred>
## Deferred Ideas

- Keyboard shortcuts for mute operations (e.g., M to toggle mute on focused card) — future enhancement
- Real-time audio level meters per channel — future enhancement if operators request it
- Audio ducking (auto-mute lower-priority channels when high-priority becomes active) — future phase

</deferred>

---

*Phase: 04-dispatch-multi-channel-monitoring*
*Context gathered: 2026-02-07*
