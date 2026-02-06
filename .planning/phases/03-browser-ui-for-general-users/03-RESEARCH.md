# Phase 3: Browser UI for General Users - Research

**Researched:** 2026-02-06
**Domain:** React web application for multi-channel PTT UI
**Confidence:** HIGH

## Summary

Phase 3 builds a React-based web UI that displays user-assigned channels and enables PTT communication. The research focused on integrating existing vanilla TypeScript client modules (PttController, ConnectionManager) into React components, multi-channel UI patterns, and session management for SEC-04 (persist across refresh).

The project already has a web-ui directory with React 18.3.1, React Router 6.26.2, and Vite 5.4.8 configured. The standard approach is to wrap the existing PttController in React components using useEffect for lifecycle management, useRef for DOM integration, and Context API for channel state sharing across the UI.

**Key architectural decisions from prior phases** that constrain this implementation:
- UX-002: Framework-agnostic vanilla TypeScript components (PttButton will be wrapped in React)
- DEPLOY-004: esbuild for bundling (web-ui already uses Vite with esbuild under the hood)
- SIG-001: WebSocket at /ws with JWT auth (Authorization header, query param, or sec-websocket-protocol)
- Server provides PERMISSION_UPDATE messages for real-time channel list sync

**Primary recommendation:** Use React 18.3 functional components with hooks, wrap PttController instances in ChannelCard components using useEffect cleanup, manage JWT in sessionStorage (persist across refresh), and use Context API for global auth/channel state.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| React | 18.3.1+ (19.2.4 available) | UI framework | Already in web-ui, stable hooks API, excellent TypeScript support |
| React Router | 6.26.2+ (v7 available) | Client-side routing | Already installed, protected routes pattern for auth |
| Vite | 5.4.8+ | Build tool | Already configured, fast HMR, uses esbuild internally |
| TypeScript | 5.4.0+ | Type safety | Already in project, essential for React + vanilla module integration |

**Note:** React 19.2.4 is the latest (as of Jan 2026) but React 18.3.1 is already installed and sufficient. Upgrade not required for Phase 3. React 19 adds Activity API and useEffectEvent, but not needed for this phase.

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| None required | - | Project uses vanilla CSS | Existing web-ui has styles.css, no UI library needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Context API | Zustand 4.x | Zustand is lighter and avoids Context re-render issues, but Context API is built-in and sufficient for ~10-20 channels |
| sessionStorage | localStorage | sessionStorage clears on tab close, localStorage persists forever. SEC-04 requires "session persists across refresh" not "forever", so sessionStorage is correct |
| Vite | Create React App | CRA is deprecated/unmaintained, Vite is the modern standard |

**Installation:**
```bash
# Already installed in web-ui/package.json
cd web-ui
npm install
```

**No new dependencies required** - existing React 18.3.1, React Router 6.26.2, and Vite 5.4.8 are sufficient.

## Architecture Patterns

### Recommended Project Structure
```
web-ui/src/
├── pages/
│   └── Dispatch.jsx          # Main page for general users (Phase 3 focus)
├── components/
│   ├── ChannelList.jsx        # List of assigned channels
│   ├── ChannelCard.jsx        # Single channel with PTT button, status
│   └── PttButtonWrapper.jsx   # React wrapper for vanilla PttButton
├── hooks/
│   ├── useAuth.js             # JWT session management
│   ├── usePttChannel.js       # Single channel PTT lifecycle
│   └── useWebSocket.js        # WebSocket connection wrapper
├── context/
│   └── ChannelContext.jsx     # Global channel state (list, permissions)
├── utils/
│   ├── tokenStorage.js        # sessionStorage helpers for JWT
│   └── channelHelpers.js      # Channel filtering, sorting
└── styles.css                 # Existing global styles
```

### Pattern 1: Wrapping Vanilla TypeScript Components in React

**What:** Integrate framework-agnostic PttController into React component lifecycle.

**When to use:** When existing vanilla modules (PttButton, PttController) must be reused in React without modification (UX-002 decision).

**Example:**
```typescript
// Source: Verified from project's src/client/pttController.ts + React best practices
import { useEffect, useRef } from 'react';
import { PttController } from '../../client/pttController';
import { SignalingClient } from '../../client/signaling/signalingClient';

function ChannelCard({ channelId, signalingClient, transportClient, microphoneManager, audioFeedback }) {
  const containerRef = useRef(null);
  const controllerRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current) return;

    // Create controller instance
    const controller = new PttController(
      signalingClient,
      transportClient,
      microphoneManager,
      audioFeedback,
      {
        channelId,
        pttMode: 'hold',
        buttonContainer: containerRef.current,
      }
    );

    // Initialize
    controller.init().catch(console.error);
    controllerRef.current = controller;

    // Cleanup on unmount
    return () => {
      if (controllerRef.current) {
        controllerRef.current.destroy();
        controllerRef.current = null;
      }
    };
  }, [channelId, signalingClient, transportClient, microphoneManager, audioFeedback]);

  return (
    <div className="channel-card">
      <h3>{channelId}</h3>
      <div ref={containerRef}></div> {/* PttButton renders here */}
    </div>
  );
}
```

**Key points:**
- Use `useRef` for DOM container and controller instance
- Controller lifecycle in `useEffect` with cleanup function
- Dependency array includes all reactive values (prevents stale closures)
- Cleanup function MUST call `controller.destroy()` to prevent memory leaks

### Pattern 2: JWT Session Management with sessionStorage

**What:** Store JWT in sessionStorage to persist across page refresh but not across tab close.

**When to use:** SEC-04 requirement: "Session persists across page refresh"

**Example:**
```javascript
// Source: Security best practices from https://cybersierra.co/blog/react-jwt-storage-guide/
// web-ui/src/utils/tokenStorage.js
export const TOKEN_KEY = 'voiceping_session_token';

export function saveToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function removeToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

// web-ui/src/hooks/useAuth.js
import { useState, useEffect } from 'react';
import { getToken, saveToken, removeToken } from '../utils/tokenStorage';
import { jwtDecode } from 'jwt-decode'; // Add as dependency

export function useAuth() {
  const [user, setUser] = useState(null);

  useEffect(() => {
    const token = getToken();
    if (token) {
      try {
        const decoded = jwtDecode(token);
        // Check expiration
        if (decoded.exp * 1000 > Date.now()) {
          setUser(decoded);
        } else {
          removeToken();
        }
      } catch (err) {
        removeToken();
      }
    }
  }, []);

  const login = (token) => {
    saveToken(token);
    const decoded = jwtDecode(token);
    setUser(decoded);
  };

  const logout = () => {
    removeToken();
    setUser(null);
  };

  return { user, login, logout };
}
```

**Security note:** sessionStorage is vulnerable to XSS. Server must sanitize all outputs. Never use `dangerouslySetInnerHTML` without sanitization.

### Pattern 3: Context API for Channel State

**What:** Share channel list and active speaker state across multiple ChannelCard components.

**When to use:** When multiple components need read/write access to channel state without prop drilling.

**Example:**
```javascript
// Source: React Context API official docs
// web-ui/src/context/ChannelContext.jsx
import { createContext, useContext, useState, useEffect } from 'react';

const ChannelContext = createContext(null);

export function ChannelProvider({ user, children }) {
  const [channels, setChannels] = useState([]);
  const [channelStates, setChannelStates] = useState({}); // channelId -> ChannelState

  useEffect(() => {
    if (user) {
      // Extract channels from JWT
      setChannels(user.channelIds || []);
    }
  }, [user]);

  const updateChannelState = (channelId, state) => {
    setChannelStates(prev => ({ ...prev, [channelId]: state }));
  };

  return (
    <ChannelContext.Provider value={{ channels, channelStates, updateChannelState }}>
      {children}
    </ChannelContext.Provider>
  );
}

export function useChannels() {
  const context = useContext(ChannelContext);
  if (!context) {
    throw new Error('useChannels must be used within ChannelProvider');
  }
  return context;
}
```

### Pattern 4: Protected Routes for Authentication

**What:** Redirect unauthenticated users to login page.

**When to use:** All pages except /login.

**Example:**
```javascript
// Source: React Router 6 protected routes pattern
// web-ui/src/App.jsx (already has this pattern, extend it)
import { Navigate } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';

function ProtectedRoute({ children }) {
  const { user } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return children;
}

// Usage in Routes:
<Route
  path="/dispatch"
  element={
    <ProtectedRoute>
      <Dispatch />
    </ProtectedRoute>
  }
/>
```

**Note:** web-ui/src/App.jsx already implements this pattern inline. Reuse existing approach.

### Anti-Patterns to Avoid

- **Wrapping controllers in useEffect without cleanup:** Causes memory leaks (WebSocket connections, audio tracks not released)
- **Storing JWT in localStorage:** Persists forever, violates SEC-04 ("session" not "permanent")
- **Creating new controller on every render:** Use `useRef` to persist instance across renders
- **Including objects/functions in dependency arrays without memoization:** Causes infinite re-render loops
- **Not handling PERMISSION_UPDATE messages:** User sees stale channel list after permissions change

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WebSocket reconnection | Custom reconnect logic | ReconnectingSignalingClient (already exists in project) | Handles exponential backoff, session recovery, already tested in Phase 1 |
| JWT decoding | Custom base64 + JSON.parse | jwt-decode npm package | Handles padding, validation, edge cases |
| PTT button UI | React button with state | Existing PttButton class | Already implements hold/toggle modes, touch events, accessibility (UX-002 decision) |
| Audio capture/playback | Navigator.mediaDevices directly | MicrophoneManager, AudioFeedback (already exist) | Handle permissions, track management, cleanup |
| Channel state updates | Manual state tracking | Use SignalingClient events (SPEAKER_CHANGED, CHANNEL_STATE) | Server is source of truth for channel state |

**Key insight:** Phase 1 already implemented the hard parts (WebSocket lifecycle, PTT state machine, audio management). Phase 3 is a thin React UI layer over existing modules.

## Common Pitfalls

### Pitfall 1: useEffect Dependency Array Errors

**What goes wrong:** Forgetting to include reactive values in dependency array causes stale closures. Including objects/functions causes infinite loops.

**Why it happens:** ESLint warning ignored, or developer doesn't understand React's comparison (uses Object.is).

**How to avoid:**
- Never suppress `react-hooks/exhaustive-deps` warning
- Move object/function creation INSIDE useEffect
- Use `useCallback` for functions, `useMemo` for objects if they must be external

**Warning signs:**
- PTT button doesn't respond after channel change
- Component shows outdated speaker name
- WebSocket sends messages for wrong channel

**Example fix:**
```javascript
// ❌ Bad: options recreated every render
const options = { channelId, pttMode: 'hold' };
useEffect(() => {
  const controller = new PttController(..., options);
  // ...
}, [options]); // Infinite loop!

// ✅ Good: move inside effect
useEffect(() => {
  const options = { channelId, pttMode: 'hold' };
  const controller = new PttController(..., options);
  // ...
}, [channelId]); // Only channelId is reactive
```

### Pitfall 2: WebSocket Cleanup Not Symmetric

**What goes wrong:** Component unmounts but WebSocket stays connected. Memory leak + orphaned server session.

**Why it happens:** useEffect cleanup function missing or doesn't mirror setup.

**How to avoid:**
- Every `controller.init()` must have corresponding `controller.destroy()` in cleanup
- Every `signalingClient.connect()` must have `signalingClient.disconnect()`
- Cleanup function should be exact opposite of setup

**Warning signs:**
- DevTools shows multiple WebSocket connections
- Server logs show duplicate sessions for same user
- Audio doesn't stop when navigating away from page

**Example fix:**
```javascript
// ✅ Proper cleanup
useEffect(() => {
  const controller = new PttController(...);
  controller.init();

  return () => {
    controller.destroy(); // Symmetric cleanup
  };
}, [dependencies]);
```

### Pitfall 3: Storing State in Wrong Place

**What goes wrong:** Storing JWT in component state (`useState`) means it's lost on refresh. Storing channel list locally instead of from JWT means it goes stale.

**Why it happens:** Developer doesn't understand React state vs browser storage, or doesn't read SEC-04 requirement.

**How to avoid:**
- JWT: sessionStorage (persists across refresh, cleared on tab close)
- Channel list: Read from JWT on every mount, listen for PERMISSION_UPDATE
- Active speaker: Read from SignalingClient events (SPEAKER_CHANGED)
- PTT button state: Managed by PttButton class (don't duplicate in React state)

**Warning signs:**
- Login required after page refresh
- Channel list shows channels user no longer has access to
- UI shows wrong active speaker

### Pitfall 4: Not Handling PERMISSION_UPDATE Messages

**What goes wrong:** Admin adds user to new channel, but user's UI doesn't show it until they log out and back in.

**Why it happens:** UI only reads JWT on mount, doesn't listen for server push updates.

**How to avoid:**
- Subscribe to PERMISSION_UPDATE message type in useEffect
- When received, extract new channel list and update Context
- For Phase 3 (general users), this is a "nice to have" - users can refresh page

**Warning signs:**
- User reports "I can't see my new channel"
- Admin has to tell users to refresh page

**Example fix:**
```javascript
useEffect(() => {
  if (!signalingClient) return;

  const handlePermissionUpdate = (data) => {
    // data.added = [channelId, ...]
    // data.removed = [channelId, ...]
    setChannels(prev => {
      const updated = prev.filter(id => !data.removed.includes(id));
      return [...updated, ...data.added];
    });
  };

  signalingClient.on('permission-update', handlePermissionUpdate);

  return () => {
    signalingClient.off('permission-update', handlePermissionUpdate);
  };
}, [signalingClient]);
```

### Pitfall 5: React 18 StrictMode Double-Mount in Development

**What goes wrong:** In development, components mount twice (setup → cleanup → setup). If cleanup isn't proper, second mount fails or duplicates resources.

**Why it happens:** React 18 StrictMode intentionally double-mounts to stress-test cleanup logic.

**How to avoid:**
- Write proper cleanup functions (see Pitfall 2)
- Test in development mode (npm run dev)
- Don't disable StrictMode to "fix" the problem

**Warning signs:**
- Works in production, breaks in development
- Console shows "Already connected" errors
- Two PTT buttons render in same container

## Code Examples

Verified patterns from official sources:

### Multi-Channel List Rendering
```javascript
// Source: React list rendering best practices
// web-ui/src/components/ChannelList.jsx
import { useChannels } from '../context/ChannelContext';
import ChannelCard from './ChannelCard';

function ChannelList() {
  const { channels } = useChannels();

  if (channels.length === 0) {
    return <div className="empty-state">No channels assigned</div>;
  }

  return (
    <div className="channel-list">
      {channels.map(channelId => (
        <ChannelCard key={channelId} channelId={channelId} />
      ))}
    </div>
  );
}

export default ChannelList;
```

**Key points:**
- Use channel ID as key (stable, unique)
- Handle empty state (UI-01 requirement)
- Each ChannelCard manages its own PTT controller

### Channel Busy State Display
```javascript
// Source: Project's PttState enum + React conditional rendering
// web-ui/src/components/ChannelCard.jsx
import { useState, useEffect } from 'react';
import { useChannels } from '../context/ChannelContext';

function ChannelCard({ channelId }) {
  const { channelStates } = useChannels();
  const state = channelStates[channelId];

  const isBusy = state?.isBusy || false;
  const speakerName = state?.speakerName || null;

  return (
    <div className={`channel-card ${isBusy ? 'busy' : 'idle'}`}>
      <h3>{channelId}</h3>

      {/* UI-03: Show channel busy state */}
      {isBusy && (
        <div className="channel-status busy">
          <span className="status-indicator"></span>
          <span>Channel Busy</span>
        </div>
      )}

      {/* UI-04: Show active speaker */}
      {speakerName && (
        <div className="active-speaker">
          <span className="speaker-icon">🎤</span>
          <span>{speakerName}</span>
        </div>
      )}

      {/* UI-02: PTT button (rendered by PttController) */}
      <div ref={containerRef}></div>
    </div>
  );
}
```

### WebSocket Connection Hook
```javascript
// Source: React useEffect cleanup pattern for WebSockets
// web-ui/src/hooks/useWebSocket.js
import { useEffect, useState, useRef } from 'react';
import { ReconnectingSignalingClient } from '../../client/signaling/reconnectingClient';

export function useWebSocket(url, token) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!token) return;

    const client = new ReconnectingSignalingClient(url, token);

    client.on('stateChange', (state) => {
      setConnected(state === 'connected');
    });

    client.connect().catch(console.error);
    clientRef.current = client;

    return () => {
      if (clientRef.current) {
        clientRef.current.disconnect();
        clientRef.current = null;
      }
    };
  }, [url, token]);

  return { client: clientRef.current, connected };
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Class components | Functional components + hooks | React 16.8 (2019) | Simpler code, better TypeScript inference |
| localStorage for sessions | sessionStorage or memory | 2024+ security guidance | Prevents XSS token theft across tabs |
| Redux for all state | Context API for app state, React Query for server state | 2023+ | Less boilerplate, Context sufficient for small apps |
| Create React App | Vite | 2023+ (CRA archived) | 10x faster dev server, better defaults |
| React Router v5 | React Router v6 | Nov 2021 | Simpler API, better nested routes |
| Prop drilling | Context API + hooks | React 16.3+ | Cleaner component tree |

**Deprecated/outdated:**
- Class components: Use functional components with hooks
- componentWillMount/componentWillReceiveProps: Use useEffect
- Redux for simple apps: Use Context API (Zustand if Context re-renders are slow)
- react-window: TanStack Virtual is more modern (but not needed for ~10-20 channels)

**Note:** React 19.2 (latest as of Jan 2026) adds Activity API and useEffectEvent, but these are for advanced use cases. React 18.3 is sufficient for Phase 3.

## Open Questions

Things that couldn't be fully resolved:

1. **JWT Refresh Token Strategy**
   - What we know: SEC-04 requires session persistence across refresh. sessionStorage handles this.
   - What's unclear: Server doesn't appear to implement refresh tokens yet. JWT expiration handling?
   - Recommendation: Phase 3 assumes short-lived JWT (e.g., 8 hours). If JWT expires mid-session, show "Session expired, please log in" and redirect to /login. Refresh token mechanism is out of scope for Phase 3.

2. **Multi-Channel Audio Simultaneous Playback**
   - What we know: PttController consumes audio from one producerId at a time. User can be in multiple channels.
   - What's unclear: If two channels have active speakers simultaneously, does UI play both? Or only the channel user is focused on?
   - Recommendation: For Phase 3 (general users), assume user listens to all assigned channels simultaneously (realistic for ~2-5 channels). ConnectionManager in Phase 1 handles multiple consumers. If performance issues arise (many channels), defer to Phase 4.

3. **Dispatch vs General User UI Differences**
   - What we know: Existing web-ui has Console.jsx (dispatch) and Admin.jsx. Requirements mention "general users."
   - What's unclear: Is Phase 3 building a separate Dispatch.jsx page, or extending Console.jsx?
   - Recommendation: Create new Dispatch.jsx page for general users (simpler than Console.jsx). Route: /event/:eventId/dispatch. Console.jsx is for DISPATCH role (Phase 2), Dispatch.jsx is for GENERAL role (Phase 3).

4. **Channel List Virtualization**
   - What we know: TanStack Virtual is modern standard for large lists.
   - What's unclear: How many channels will typical general user have? 10? 100?
   - Recommendation: Implement simple map() for Phase 3. Add virtualization in later phase if users report 50+ channels causing slowdown.

## Sources

### Primary (HIGH confidence)
- React official documentation (https://react.dev) - useEffect cleanup, hooks best practices
- React Router official documentation (https://reactrouter.com) - v6 protected routes pattern
- Project source code (src/client/pttController.ts, src/client/connectionManager.ts) - Existing architecture
- Project package.json - React 18.3.1, React Router 6.26.2, Vite 5.4.8 already installed

### Secondary (MEDIUM confidence)
- [React 19.2 Release Notes](https://react.dev/blog/2025/10/01/react-19-2) - Latest React features (Activity API, useEffectEvent)
- [Redux vs Zustand vs Context API in 2026](https://medium.com/@sparklewebhelp/redux-vs-zustand-vs-context-api-in-2026-7f90a2dc3439) - State management comparison
- [JWT Storage Security Guide](https://cybersierra.co/blog/react-jwt-storage-guide/) - sessionStorage vs localStorage for JWT
- [React Router v7 Guide](https://blog.logrocket.com/react-router-v7-guide/) - Latest routing patterns
- [Complete Guide to Setting Up React with TypeScript and Vite (2026)](https://medium.com/@robinviktorsson/complete-guide-to-setting-up-react-with-typescript-and-vite-2025-468f6556aaf2) - Vite configuration

### Tertiary (LOW confidence)
- WebSearch results for "React best practices 2026" - General guidance, not project-specific

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - React, Vite, React Router already installed and configured in web-ui directory
- Architecture: HIGH - Patterns verified from project source code (PttController wrapping) and React official docs (useEffect cleanup)
- Pitfalls: HIGH - Derived from React official docs warnings and common issues in project's existing modules

**Research date:** 2026-02-06
**Valid until:** 2026-03-06 (30 days, React ecosystem is stable)

**Notes:**
- No new npm dependencies required for Phase 3
- Existing web-ui directory has React app scaffold with auth, routing, and styling
- UX-002 decision (framework-agnostic components) is core constraint - don't modify PttButton or PttController
- SEC-04 requirement drives sessionStorage choice (persist across refresh, not forever)
