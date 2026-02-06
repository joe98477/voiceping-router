---
phase: 03-browser-ui-for-general-users
plan: 01
subsystem: web-ui-foundation
tags: [vite, react, jwt, session-management, context-api]
requires: [02-08]
provides:
  - Vite config with TypeScript path aliases (@client, @shared)
  - sessionStorage-based JWT persistence (router token)
  - useAuth hook for router authentication state
  - ChannelContext for global channel/speaker state
affects: [03-02, 03-03, 03-04, 03-05]
tech-stack:
  added: []
  patterns: [React Context API, sessionStorage for session persistence]
key-files:
  created:
    - web-ui/vite.config.js
    - web-ui/src/utils/tokenStorage.js
    - web-ui/src/hooks/useAuth.js
    - web-ui/src/context/ChannelContext.jsx
  modified: []
decisions:
  - UI-01: Channel names default to channelId for MVP (no lightweight name API for general users)
  - UI-02: sessionStorage (not localStorage) for router token (clears on tab close)
  - UI-03: useAuth manages router JWT, not control-plane cookie auth
metrics:
  duration: 4 minutes
  tasks: 2
  commits: 2
completed: 2026-02-06
---

# Phase 03 Plan 01: Session Management Foundation Summary

**One-liner:** sessionStorage JWT persistence, useAuth hook, and ChannelContext provider - the foundation for all Phase 3 UI components.

## What Was Built

### 1. Vite Configuration for TypeScript Imports
Updated `web-ui/vite.config.js` to support importing TypeScript modules from outside the web-ui directory:
- Added `@client` alias → `src/client/` (ConnectionManager, PttController, etc.)
- Added `@shared` alias → `src/shared/` (protocol types, shared types)
- Enables downstream plans (02-05) to import TypeScript client modules

**WebSocket URL format documented:**
- Development: `ws://localhost:3000/ws`
- Production: `ws(s)://${window.location.host}/ws` (behind nginx proxy)
- Token passed as WebSocket sub-protocol: `['voiceping', token]`

### 2. Token Storage Utility
Created `web-ui/src/utils/tokenStorage.js` with sessionStorage helpers:
- `TOKEN_KEY` constant: 'voiceping_session_token'
- `saveToken(token)` - stores JWT in sessionStorage
- `getToken()` - retrieves JWT from sessionStorage
- `removeToken()` - clears JWT from sessionStorage

**Why sessionStorage:** SEC-04 requires session persistence across page refresh but automatic clear on tab close (not localStorage).

### 3. Authentication Hook
Created `web-ui/src/hooks/useAuth.js` for router JWT management:
- On mount: restores session from sessionStorage
- Decodes JWT payload manually (no external dependencies)
- Validates token expiry (`exp` claim)
- Exports: `{ user, token, login(token), logout(), isAuthenticated }`

**Important:** This hook manages the ROUTER JWT (from `/api/router/token`), NOT the control-plane cookie auth. The router JWT contains `channelIds` and is used for WebSocket authentication to the mediasoup signaling server.

### 4. Channel Context Provider
Created `web-ui/src/context/ChannelContext.jsx` for global channel state:
- Initializes `channels` from `user.channelIds` as `[{ id, name }]` objects
- Maintains `channelStates` map: `{ [channelId]: { isBusy, speakerId, speakerName } }`
- `updateChannelState(channelId, stateUpdate)` - merges partial state update
- `setChannels(channelList)` - replaces entire channel list (for PERMISSION_UPDATE)

**Channel name handling (UI-01):**
Channel names default to channelId for MVP. The `/api/router/token` JWT only contains channelIds (no names). The `/api/events/:eventId/overview` endpoint has channel names but requires DISPATCH/ADMIN role. For MVP, general users see channelId as display name. Future enhancement: lightweight `/api/events/:eventId/my-channels` endpoint.

## Task Commits

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Vite config for TypeScript imports | 0def709 | web-ui/vite.config.js |
| 2 | Token storage, useAuth, ChannelContext | 511f6cb | tokenStorage.js, useAuth.js, ChannelContext.jsx |

## Verification

- ✅ All four files exist and export correctly
- ✅ Zero new npm dependencies added
- ✅ sessionStorage used (not localStorage)
- ✅ JWT decode handles base64url encoding without external library
- ✅ ChannelContext throws error when used outside provider
- ✅ Vite config has @client and @shared aliases
- ✅ `npx vite build --mode development` completes without errors

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

**UI-01: Channel name MVP limitation**
- **Decision:** Channel names default to channelId for general users
- **Rationale:** JWT from `/api/router/token` only contains channelIds. The `/api/events/:eventId/overview` endpoint has names but is role-restricted (DISPATCH/ADMIN only)
- **Impact:** General users see "channel-abc123" instead of "Security Team". Ready for enhancement when lightweight name API becomes available
- **Context objects ready:** ChannelContext stores `[{id, name}]` objects, not bare strings

**UI-02: sessionStorage over localStorage**
- **Decision:** Use sessionStorage for router JWT, not localStorage
- **Rationale:** SEC-04 requires "session persists across page refresh" but clears on tab close
- **Impact:** User stays logged in during page refresh but session auto-clears when browser tab closes

**UI-03: Router JWT vs control-plane auth**
- **Decision:** useAuth hook manages router JWT, NOT control-plane cookie auth
- **Rationale:** Existing app uses cookie-based auth (`/api/auth/login`). The router JWT is a SEPARATE token from `/api/router/token` that contains channelIds for WebSocket auth
- **Impact:** Cookie auth flow remains untouched. useAuth stores the router token for mediasoup signaling connections

## Integration Points

**Upstream dependencies:**
- Phase 2 complete (JWT token generation, WebSocket auth)
- Existing cookie-based control-plane auth (Login.jsx, App.jsx)

**Downstream dependencies:**
- **03-02:** Connection management hooks will consume useAuth.token
- **03-03:** Channels page will wrap app in ChannelProvider
- **03-04:** Dashboard will call updateChannelState on SPEAKER_CHANGED events
- **03-05:** PTT Button will use ChannelContext to check busy state

**WebSocket URL for downstream plans:**
```javascript
const wsUrl = import.meta.env.VITE_ROUTER_WS_URL ||
  (import.meta.env.MODE === 'development'
    ? 'ws://localhost:3000/ws'
    : `wss://${window.location.host}/ws`);
```

## Technical Notes

### JWT Decode Implementation
Manual base64url decode without external dependencies:
1. Split JWT on '.' to get [header, payload, signature]
2. Replace URL-safe chars: `-` → `+`, `_` → `/`
3. Pad with `=` to 4-byte boundary
4. `atob()` to decode base64
5. `JSON.parse()` to get payload object

### Context Error Handling
`useChannels()` throws descriptive error if used outside ChannelProvider:
```javascript
if (!context) {
  throw new Error('useChannels must be used within a ChannelProvider');
}
```

This fails fast during development, preventing silent bugs.

### useCallback for Performance
All callback functions use `useCallback` to prevent re-render cascades:
- `updateChannelState` - memoized, stable reference
- `setChannels` - memoized, stable reference

## Next Phase Readiness

**Phase 3 Plan 02 (Connection Hooks) can proceed:**
- ✅ Vite can import from @client/connectionManager
- ✅ useAuth provides token for WebSocket auth
- ✅ ChannelContext ready for connection state updates

**No blockers.** All foundation pieces in place.

## Self-Check: PASSED

**Created files verified:**
- ✅ web-ui/vite.config.js exists
- ✅ web-ui/src/utils/tokenStorage.js exists
- ✅ web-ui/src/hooks/useAuth.js exists
- ✅ web-ui/src/context/ChannelContext.jsx exists

**Commits verified:**
- ✅ 0def709 exists in git log
- ✅ 511f6cb exists in git log
