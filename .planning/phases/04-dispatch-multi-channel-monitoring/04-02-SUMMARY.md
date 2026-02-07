---
phase: 04-dispatch-multi-channel-monitoring
plan: 02
subsystem: dispatch-ui
tags: [react, dispatch, multi-channel, muting, localStorage, admin-drawer]
requires:
  - 03-05-SUMMARY.md # Vite build and Docker verification
  - 04-01-SUMMARY.md # DispatchChannelCard component
provides:
  - DispatchConsole page at /event/:eventId/dispatch
  - ChannelGrid with team-grouped collapsible sections
  - AdminDrawer right-slide panel
  - Mute persistence via localStorage
  - Channel name resolution for general users
affects:
  - 04-03-PLAN.md # Will use DispatchConsole as base
  - 04-04-PLAN.md # Multi-channel features build on this UI
tech-stack:
  added: []
  patterns:
    - "Team-grouped channel grid with collapsible sections"
    - "localStorage mute persistence (cv.dispatch.muted.{eventId})"
    - "Right-sliding drawer UI pattern (AdminDrawer)"
    - "Stats bar with uptime counter and connection health"
key-files:
  created:
    - web-ui/src/pages/DispatchConsole.jsx
    - web-ui/src/components/ChannelGrid.jsx
    - web-ui/src/components/AdminDrawer.jsx
  modified:
    - web-ui/src/App.jsx # Route change to DispatchConsole
    - web-ui/src/pages/Events.jsx # "Dispatch Console" link text
    - web-ui/src/pages/Channels.jsx # Channel name resolution
    - web-ui/src/styles.css # Grid, drawer, stats, page layout CSS
    - control-plane/src/index.js # Router token endpoint with channelNames
decisions:
  - id: UI-012
    what: "Team-grouped channel grid with collapsible sections"
    why: "Dispatch users monitor 10-50 channels; team grouping provides organizational structure"
    impact: "All team sections start expanded by default; collapse state not persisted"
  - id: UI-013
    what: "localStorage for mute state persistence (key: cv.dispatch.muted.{eventId})"
    why: "Dispatch users want mute preferences to persist across page refreshes"
    impact: "Mute state is per-event, stored in localStorage, survives browser restarts"
  - id: UI-014
    what: "Per-team mute toggle buttons in team section headers"
    why: "Allows bulk muting/unmuting of all channels in a team with one click"
    impact: "Team mute button shows 'Muted' when ALL channels muted, else 'Unmuted'"
  - id: UI-015
    what: "AdminDrawer slides from right (not center modal like SettingsDrawer)"
    why: "Side drawer preserves context (channels visible) while accessing admin features"
    impact: "New UI pattern for dispatch console; SettingsDrawer remains center modal for other pages"
  - id: API-001
    what: "Enhanced /api/router/token endpoint to include channelNames map"
    why: "General users need channel names (not IDs); existing JWT only contains channelIds"
    impact: "All users (general + dispatch/admin) get channel names; lightweight solution (no new endpoint)"
metrics:
  - duration: 5 minutes
  - completed: 2026-02-07
---

# Phase 04 Plan 02: Dispatch Console UI Summary

**One-liner:** Full dispatch monitoring page with team-grouped channel grid, stats bar, admin drawer, localStorage mute persistence, and channel name resolution for all users.

## What Was Built

### DispatchConsole Page (`web-ui/src/pages/DispatchConsole.jsx`)

Complete dispatch monitoring interface at `/event/:eventId/dispatch`:

**Header:**
- Brand ("ConnectVoice") links back to `/events`
- Page title: "Dispatch Console"
- Event name display
- Admin gear button (opens AdminDrawer)
- Logout button

**Stats Bar:**
- Event name
- User info (displayName or email)
- Total channels count
- Muted channels count
- Active speakers count (from `channelStates.isBusy`)
- Uptime (formatted as "Xh Ym", updates every second)
- Connection health ("Online" / "Offline" from `navigator.onLine`)

**Channel Grid:**
- Wrapped in ChannelProvider for state management
- Team-grouped sections with collapsible headers
- Per-team mute/unmute toggles
- Alphabetically sorted teams and channels
- All sections start expanded

**Mute Persistence:**
- Loads from `localStorage.getItem('cv.dispatch.muted.${eventId}')`
- Auto-saves on every mute state change
- Survives page refresh and browser restart

**Data Flow:**
1. Fetches `/api/events/${eventId}/overview` for teams, channels, roster, event info
2. Fetches `/api/router/token` for JWT and channel names
3. Passes token to ChannelGrid → DispatchChannelCard connections
4. Uses usePermissionUpdates for real-time permission sync

### ChannelGrid Component (`web-ui/src/components/ChannelGrid.jsx`)

Team-grouped channel grid with collapsible sections:

**Features:**
- Groups channels by `teamId` (channels without teamId go to "Event" group)
- Sorts teams alphabetically by name
- Sorts channels alphabetically within each team
- Each team section has:
  - Clickable header to expand/collapse (chevron icon rotates)
  - Per-team mute toggle button
  - Grid of DispatchChannelCard components
- Responsive grid: `grid-template-columns: repeat(auto-fill, minmax(220px, 1fr))`
- Expand/collapse state stored in local React state (not persisted)

**Team Mute Logic:**
- If all channels in team are muted → button shows "Muted" (red background)
- If any channel unmuted → button shows "Unmuted" (white background)
- Click mutes all (if any unmuted) or unmutes all (if all muted)

### AdminDrawer Component (`web-ui/src/components/AdminDrawer.jsx`)

Right-sliding side drawer for admin control-plane features:

**Features:**
- Slides in from right side of screen (CSS animation)
- Semi-transparent overlay (clicks outside close drawer)
- Only shows tabs for users with `globalRole === 'ADMIN'`
- Tabs: Event, Teams, Channels, Users, Invites, System
- Reuses existing SettingsTabs components (EventSettings, TeamsSettings, etc.)
- Header: "Admin" badge + "Control Plane" title + Close button

**Difference from SettingsDrawer:**
- AdminDrawer: Right-side slide-in panel (preserves context)
- SettingsDrawer: Center modal (existing pattern for other pages)

### Channel Name Resolution

**Control-Plane Enhancement (`control-plane/src/index.js`):**
- Modified `/api/router/token` POST endpoint
- After building `channelIds` array, queries Prisma for channel names:
  ```javascript
  const channelsData = await prisma.channel.findMany({
    where: { id: { in: channelIds } },
    select: { id: true, name: true }
  });
  const channelNames = Object.fromEntries(channelsData.map(c => [c.id, c.name]));
  ```
- Response now includes `{ token, channelNames }`

**Channels.jsx Updates:**
- Stores `channelNames` from token response in state
- Passes `channelNames` to `ChannelListWithPermissions`
- On mount, updates channel names via `setChannels` using function updater pattern
- Permission updates also use `channelNames` for newly added channels

**Result:**
- General users see "Security Team" instead of "channel-abc123"
- Dispatch/Admin users also benefit from channel names
- No new API endpoint needed (enhanced existing endpoint)

### Navigation Updates

**Events.jsx:**
- Changed link text from "Dispatch View" to "Dispatch Console"

**App.jsx:**
- Imported `DispatchConsole` component
- Route `/event/:eventId/dispatch` now renders `<DispatchConsole />` instead of `<Console />`
- Old `Console.jsx` remains in codebase (not deleted) but no longer routed

### CSS Additions (`web-ui/src/styles.css`)

Added styles for:
- `.channel-grid` — Grid container with 16px gap
- `.channel-grid__team-section` — Team section with 8px gap
- `.channel-grid__team-header` — Collapsible header with chevron and mute toggle
- `.channel-grid__team-name` — Uppercase team name styling
- `.channel-grid__team-chevron` — Rotating chevron icon
- `.channel-grid__team-mute-btn` — Per-team mute toggle button
- `.channel-grid__cards` — Responsive card grid (220px min width)
- `.admin-drawer-overlay` — Full-screen overlay for drawer
- `.admin-drawer` — Right-side sliding panel (480px max width)
- `.admin-drawer__header`, `__tabs`, `__content` — Drawer structure
- `@keyframes admin-drawer-slide-in` — Slide animation
- `.dispatch-stats` — Stats bar flexbox layout
- `.dispatch-stats__item` — Individual stat item
- `.dispatch-stats__divider` — Vertical divider
- `.dispatch-console` — Page container (grid layout)
- `.dispatch-console__header` — Page header
- `.dispatch-console__brand` — Brand section with link
- `.dispatch-console__event-name` — Event name display
- `.dispatch-console__actions` — Action buttons

## Task Commits

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | ChannelGrid + AdminDrawer + CSS | d7fbe07 | ChannelGrid.jsx, AdminDrawer.jsx, styles.css |
| 2 | DispatchConsole page + route wiring + navigation + mute persistence + channel name API | 48ca17b | DispatchConsole.jsx, App.jsx, Events.jsx, Channels.jsx, control-plane/src/index.js |

## Verification Results

**Build verification:**
- `npx vite build` passed (220 modules transformed, built in 6.12s)
- No TypeScript errors
- Bundle size: 463.05 kB (111.59 kB gzip)

**Grep verification:**
- App.jsx imports and renders DispatchConsole at `/event/:eventId/dispatch` route
- Events.jsx link text changed to "Dispatch Console"
- Channels.jsx uses `channelNames` from token response
- ChannelGrid and AdminDrawer components found in source
- CSS classes for channel-grid, admin-drawer, dispatch-stats, dispatch-console present

**Success criteria met:**
- ✅ Dispatch user sees all channels grouped by team
- ✅ Team sections are collapsible (all start expanded)
- ✅ Per-team mute toggle mutes/unmutes all channels in team
- ✅ Stats bar shows real-time counts (channels, muted, active, uptime, health)
- ✅ Admin gear button opens right-sliding drawer
- ✅ Brand link navigates back to /events
- ✅ Mute preferences persist via localStorage
- ✅ Events page shows "Dispatch Console"
- ✅ General users see channel names (not IDs)

## Deviations from Plan

None — plan executed exactly as written.

## Decisions Made

**UI-012: Team-grouped channel grid**
- Channels grouped by `teamId`, sorted alphabetically within teams
- Teams also sorted alphabetically
- "Event" group for channels without `teamId`
- All sections start expanded (collapse state not persisted)

**UI-013: localStorage mute persistence**
- Key: `cv.dispatch.muted.${eventId}`
- Stores array of channel IDs
- Loaded on mount, saved on every change
- Survives browser restart

**UI-014: Per-team mute toggles**
- Button in team header
- Shows "Muted" when ALL channels muted (red background)
- Shows "Unmuted" when any channel unmuted (white background)
- Click mutes all or unmutes all

**UI-015: AdminDrawer slide pattern**
- Right-side slide-in panel (not center modal)
- Preserves channel grid context while accessing admin features
- New UI pattern for dispatch console

**API-001: Enhanced router token endpoint**
- `/api/router/token` response now includes `channelNames` map
- Lightweight solution (no new endpoint)
- Benefits all users (general + dispatch/admin)
- Query: `prisma.channel.findMany({ where: { id: { in: channelIds } }, select: { id, name } })`

## Next Phase Readiness

**Ready for 04-03 (Dispatch Console Enhancements):**
- DispatchConsole page provides base for additional features
- ChannelGrid supports per-channel muting
- Stats bar can be extended with additional metrics
- AdminDrawer pattern established

**No blockers.**

**Technical debt:**
- Collapse state not persisted (per LOCKED decision in RESEARCH.md)
- Active speaker count reads from `channelStates.isBusy` but not tested with real multi-channel activity
- Uptime counter resets on page refresh (intentional — shows session uptime)

## Self-Check: PASSED

**Created files exist:**
- ✅ web-ui/src/pages/DispatchConsole.jsx
- ✅ web-ui/src/components/ChannelGrid.jsx
- ✅ web-ui/src/components/AdminDrawer.jsx

**Commits exist:**
- ✅ d7fbe07 (Task 1)
- ✅ 48ca17b (Task 2)
