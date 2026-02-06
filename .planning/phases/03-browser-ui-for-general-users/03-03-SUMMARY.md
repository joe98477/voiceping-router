---
phase: 03-browser-ui-for-general-users
plan: 03
subsystem: channels-page
tags: [react, channels-page, routing, css-styling, responsive-design]
requires: [03-01, 03-02]
provides:
  - Complete Channels page at /event/:eventId/channels
  - ChannelList component rendering ChannelCard grid
  - Events page navigation link to My Channels
  - Responsive CSS styling for channel cards and grid layout
affects: [03-04, 03-05]
tech-stack:
  added: []
  patterns: [Responsive grid layout, optimistic UI patterns]
key-files:
  created:
    - web-ui/src/components/ChannelList.jsx
    - web-ui/src/pages/Channels.jsx
  modified:
    - web-ui/src/App.jsx
    - web-ui/src/pages/Events.jsx
    - web-ui/src/styles.css
decisions: []
metrics:
  duration: 3 minutes
  tasks: 2
  commits: 2
completed: 2026-02-06
---

# Phase 03 Plan 03: Channels Page Summary

**One-liner:** Complete Channels page with responsive grid, PTT controls, and navigation integration for general users.

## What Was Built

### 1. ChannelList Component
Created `web-ui/src/components/ChannelList.jsx` - Grid component that renders ChannelCard for each assigned channel.

**Features:**
- Reads `channels` array from ChannelContext via `useChannels()` hook
- Maps channels to ChannelCard components with `channel` prop as `{id, name}` objects
- Passes `wsUrl` and `token` props down from Channels page
- Empty state message when user has no assigned channels: "No channels assigned. Contact your dispatch operator to get channel access."

**Integration:**
- Works with ChannelContext from 03-01 for channel list
- Renders ChannelCard from 03-02 for each channel
- Responsive grid layout via CSS (1/2/3 columns based on screen width)

### 2. Channels Page
Created `web-ui/src/pages/Channels.jsx` - Main page for general users to view and interact with their assigned channels.

**Architecture:**
- Accepts `{ user, onLogout }` props (same pattern as Console.jsx and Admin pages)
- Uses `useParams()` to get `eventId` from route
- Uses `useAuth()` hook to manage router JWT token

**Token Fetching Flow:**
1. On mount (useEffect with [eventId]):
   - Calls `apiPost("/api/router/token", { eventId })` to get router JWT
   - Stores token via `login()` method from useAuth (sessionStorage persistence)
   - Sets local token state for passing to ChannelList
2. Error handling:
   - 403 → "You are not active in this event. Contact an administrator for access."
   - Other errors → Generic error message with Retry button
3. Wraps ChannelList in ChannelProvider with decoded JWT user (contains channelIds)

**WebSocket URL Construction (SIG-001):**
- Checks `import.meta.env.VITE_ROUTER_WS` env variable for development override
- Normalizes env URL to ensure it ends with `/ws`
- Falls back to `ws(s)://${window.location.host}/ws` in production
- Matches SIG-001 decision: WebSocket server at dedicated `/ws` path

**Page Layout:**
- Header: "ConnectVoice" brand + "My Channels" heading + Log out button
- Error alert with retry button (if token fetch fails)
- Loading state: "Loading channels..."
- Channel list grid (when loaded successfully)

### 3. Route Wiring
Updated `web-ui/src/App.jsx` to add route for general users:

**New Route:**
```jsx
<Route
  path="/event/:eventId/channels"
  element={
    user ? (
      needsSetup ? (
        <Navigate to="/first-run" replace />
      ) : (
        <Channels user={user} onLogout={handleLogout} />
      )
    ) : (
      <Navigate to="/login" replace />
    )
  }
/>
```

**Route Protection:**
- Requires authenticated user (cookie-based control-plane auth)
- Redirects to `/login` if not authenticated
- Redirects to `/first-run` if user needs setup (mustChangePassword, no displayName, no email)
- Same authentication pattern as existing `/event/:eventId/dispatch` and `/event/:eventId/admin` routes

### 4. Events Page Navigation
Updated `web-ui/src/pages/Events.jsx` to add "My Channels" link for all users:

**Link Placement:**
- Added before "Dispatch View" link in event card actions
- Visible to ALL users (not restricted to ADMIN/DISPATCH roles)
- Links to `/event/${event.id}/channels`

**Button Styling:**
- Uses existing `btn btn--secondary` classes
- Consistent with "Dispatch View" button styling

### 5. CSS Styling
Updated `web-ui/src/styles.css` with comprehensive styling for Channels page:

**Channels Page Layout:**
- `.channels-page` - Full viewport page (same padding as `.screen`)
- `.channels-page__topbar` - Header bar (same pattern as `.control-plane__topbar`)
- `.channels-page__brand` - Brand text with ConnectVoice + My Channels heading
- `.channels-page__actions` - Right-aligned action buttons

**Channel List Grid (Responsive):**
- `.channel-list` - CSS grid with responsive columns:
  - Mobile (<600px): 1 column
  - Medium (600-900px): 2 columns
  - Large (>900px): 3 columns
  - Gap: 16px
- `.channel-list__empty` - Centered empty state message with muted color

**Channel Card Styling:**
- `.channel-card` - White background card with shadow and border-radius
- `.channel-card--busy` - 4px left border in accent color when channel busy
- `.channel-card__header` - Flex row with channel name and status pill
- `.channel-card__name` - Channel name with ellipsis overflow for long names
- `.channel-card__status` - Connection status pill (ok/info/warn/error/muted)
- `.channel-card__speaker` - Speaker info row (subtle background, shown when busy)
- `.channel-card__speaker-name` - Bold speaker name
- `.channel-card__ptt` - PTT button container (centered, min-height 48px)
- `.channel-card__error` - Error message in red/accent color

**PTT Button States:**
- `.ptt-idle` - Blue gradient (default state)
- `.ptt-transmitting` - Green gradient with glow shadow
- `.ptt-blocked` - Red/salmon background when channel busy

**Responsive Design:**
- Mobile (<720px): Single column grid, vertical topbar
- Follows existing design language (CSS variables: --ink, --muted, --paper, --surface, --accent, --accent-2, --shadow, --border)

## Task Commits

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | ChannelList component and Channels page | 42ba2b6 | ChannelList.jsx, Channels.jsx |
| 2 | Route wiring, Events page link, CSS styling | 1f7a399 | App.jsx, Events.jsx, styles.css |

## Verification

- ✅ ChannelList renders ChannelCard with `{id, name}` objects
- ✅ Empty state displays when no channels assigned
- ✅ Channels page fetches router token from `/api/router/token`
- ✅ Channels page wraps content in ChannelProvider with decoded JWT user
- ✅ WebSocket URL constructed as `ws(s)://host/ws` per SIG-001
- ✅ Error handling with retry button for token fetch failures
- ✅ Route `/event/:eventId/channels` exists in App.jsx
- ✅ Events page has "My Channels" link visible to all users
- ✅ CSS uses existing design variables and patterns
- ✅ Responsive grid layout (1/2/3 columns at different widths)
- ✅ Build succeeds without errors: `npm run build`

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

No new architectural decisions. Plan follows patterns established in 03-01 and 03-02.

## Integration Points

**Upstream dependencies:**
- **03-01:** ChannelContext for channel state management, useAuth for JWT token
- **03-02:** ChannelCard component with PTT button, useChannelConnection hook
- **Phase 2:** `/api/router/token` endpoint for JWT generation
- **Phase 1:** WebSocket signaling at `/ws` path (SIG-001)

**Downstream dependencies:**
- **03-04:** Real-time permission updates will refresh channel list
- **03-05:** Multi-channel dashboard enhancements

**Component integration pattern:**
```
App.jsx
  └─ /event/:eventId/channels route
      └─ Channels.jsx (page)
          ├─ Fetches router token via apiPost
          ├─ Stores token via useAuth.login()
          └─ ChannelProvider (wraps with decoded JWT user)
              └─ ChannelList.jsx
                  └─ ChannelCard.jsx × N (per channel)
                      └─ useChannelConnection hook
                          └─ ConnectionManager (WebRTC/PTT)
```

## Technical Notes

### WebSocket URL Construction
The `getWsUrl()` function in Channels.jsx implements SIG-001 decision:
- Development: Checks `VITE_ROUTER_WS` env variable for override
- Production: Derives from `window.location` (behind nginx TLS-terminating proxy)
- Always ensures URL ends with `/ws` path

### Token vs User Distinction
Two separate authentication systems:
1. **Control-plane auth (cookie-based):** Used for App.jsx routing, passed as `user` prop
2. **Router JWT (sessionStorage):** Used for WebSocket authentication, managed by useAuth hook

Channels page requires BOTH:
- Cookie auth to access the page (App.jsx route protection)
- Router JWT to connect WebSocket (fetched on page mount)

### Responsive Grid Breakpoints
- **<600px:** 1 column (mobile portrait)
- **600-900px:** 2 columns (mobile landscape, tablets)
- **>900px:** 3 columns (desktop)

Matches common responsive design patterns, aligns with existing grid classes.

### CSS Class Naming
Follows BEM (Block Element Modifier) convention:
- Block: `.channel-card`
- Element: `.channel-card__header`, `.channel-card__ptt`
- Modifier: `.channel-card--busy`

Consistent with existing styles (`.control-plane__topbar`, `.dispatch-card__header`, etc.)

## Next Phase Readiness

**Phase 3 Plan 04 (Real-time Updates) can proceed:**
- ✅ Channels page exists and renders channel list
- ✅ ChannelContext provides `setChannels()` for PERMISSION_UPDATE handling
- ✅ ChannelCard updates when channel state changes
- ✅ No blockers

**Future enhancements:**
- Channel name resolution API (replace channelId defaults with real names)
- Filtering/sorting channels by name or status
- Channel search (for users with many assigned channels)
- Audio settings panel (microphone selection, output device)

## Self-Check: PASSED

**Created files verified:**
- ✅ web-ui/src/components/ChannelList.jsx exists
- ✅ web-ui/src/pages/Channels.jsx exists

**Modified files verified:**
- ✅ web-ui/src/App.jsx has /event/:eventId/channels route
- ✅ web-ui/src/pages/Events.jsx has "My Channels" link
- ✅ web-ui/src/styles.css has channel-card and channel-list styles

**Commits verified:**
- ✅ 42ba2b6 exists in git log
- ✅ 1f7a399 exists in git log
