---
phase: 03-browser-ui-for-general-users
plan: 02
subsystem: connection-management
tags: [react, hooks, connectionmanager, ptt-button, channel-card, websocket]
requires: [03-01, 01-07]
provides:
  - useChannelConnection hook for per-channel ConnectionManager lifecycle
  - ChannelCard component with PTT button and speaker status
  - React integration layer for Phase 1 mediasoup audio system
affects: [03-03, 03-04, 03-05]
tech-stack:
  added: []
  patterns: [React hooks for resource lifecycle, hold-to-talk PTT pattern, optimistic UI]
key-files:
  created:
    - web-ui/src/hooks/useChannelConnection.js
    - web-ui/src/components/ChannelCard.jsx
  modified: []
decisions:
  - ARCH-004: ConnectionManager built-in PTT methods replace PttController integration
  - UX-006: Hold-to-talk PTT via native React event handlers (not PttButton DOM injection)
  - UX-007: Optimistic PTT UI with error revert pattern
metrics:
  duration: 2 minutes
  tasks: 2
  commits: 2
completed: 2026-02-06
---

# Phase 03 Plan 02: Connection Management Hooks Summary

**One-liner:** React hooks wrapping ConnectionManager for per-channel PTT connections with hold-to-talk UI and speaker status display.

## What Was Built

### 1. useChannelConnection Hook
Created `web-ui/src/hooks/useChannelConnection.js` - React hook managing ConnectionManager lifecycle for a single channel.

**Architecture:**
- **One ConnectionManager per channel** - Multi-channel PTT requires separate ConnectionManager instance per channel
- Each instance creates its own WebSocket connection, MediasoupDevice, and TransportClient
- Automatic cleanup on unmount via useEffect return function (StrictMode compatible)

**Implementation:**
- Imports ConnectionManager from `@client/connectionManager` (TypeScript path alias from 03-01)
- Uses `useRef` to persist ConnectionManager instance across re-renders
- Uses `useState` for connectionState ('disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'error')
- Calls `updateChannelState()` from ChannelContext on speaker/channel state changes
- Exposes `connectionManager` ref for external PTT control

**Connection callbacks:**
- `onStateChange` → updates connectionState useState
- `onError` → updates error useState
- `onChannelStateUpdate` → calls `updateChannelState(channelId, { isBusy, speakerId, speakerName })`
- `onSpeakerChanged` → calls `updateChannelState(channelId, { isBusy: !!userId, speakerId, speakerName })`

**React 18 StrictMode compatibility:**
Cleanup function calls `manager.disconnect()` symmetrically with setup. StrictMode's mount-unmount-mount creates fresh ConnectionManager on second mount, no resource leaks.

### 2. ChannelCard Component
Created `web-ui/src/components/ChannelCard.jsx` - Visual card for single channel with PTT button, connection status, and speaker display.

**Props:**
- `channel` - `{ id, name }` object from ChannelContext
- `wsUrl` - Full WebSocket URL (e.g., `wss://host/ws`)
- `token` - JWT token from useAuth

**Features:**
1. **Connection status indicator** - Pill badge showing connecting/connected/reconnecting/error state
2. **PTT button** - Hold-to-talk interaction via native React event handlers
3. **Busy state indicator** - Visual "Channel Busy" state when someone else transmitting
4. **Active speaker display** - Shows speaker name when channel occupied
5. **Error display** - Connection errors and PTT denial messages (3-second auto-clear)

**PTT Implementation:**
- Uses ConnectionManager's built-in `startTransmitting()` and `stopTransmitting()` methods
- Hold-to-talk pattern: mousedown/touchstart → start, mouseup/touchend → stop
- Optimistic UI: button state updates immediately, reverts on error
- Auto-clear PTT errors after 3 seconds

**Why NOT PttController:**
Plan originally specified PttController integration, but analysis revealed:
- ConnectionManager already has complete PTT logic (startTransmitting/stopTransmitting)
- ConnectionManager doesn't expose `getSignalingClient()` getter (PttController needs it)
- PttController is a lower-level component replaced by ConnectionManager in architecture
- Simpler to use ConnectionManager's methods directly than create wrapper layer

## Task Commits

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | useChannelConnection hook | 2301952 | web-ui/src/hooks/useChannelConnection.js |
| 2 | ChannelCard component | a6b131b | web-ui/src/components/ChannelCard.jsx |

## Verification

- ✅ useChannelConnection imports from @client/connectionManager (not relative path)
- ✅ useEffect has proper cleanup calling disconnect()
- ✅ connectionState updates flow via onStateChange callback
- ✅ useRef used for ConnectionManager instance (not useState)
- ✅ updateChannelState called on SPEAKER_CHANGED and CHANNEL_STATE events
- ✅ ChannelCard accepts `channel` prop as `{ id, name }` object
- ✅ Renders channel.name (not channelId) in header
- ✅ PTT interaction via ConnectionManager methods (not PttController)
- ✅ Busy state and speaker name read from ChannelContext
- ✅ Touch and mouse events supported
- ✅ No modifications to existing src/client/ TypeScript modules

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Replaced PttController integration with ConnectionManager direct methods**

- **Found during:** Task 2 (ChannelCard component implementation)
- **Issue:** Plan specified PttController integration, but ConnectionManager doesn't expose `getSignalingClient()` getter required by PttController constructor. PttController needs `SignalingClient` but ConnectionManager only creates `ReconnectingSignalingClient` internally with no public accessor.
- **Root cause:** ConnectionManager evolved to include PTT logic that was previously in PttController. PttController is now a legacy lower-level component.
- **Fix:** Used ConnectionManager's built-in `startTransmitting()` and `stopTransmitting()` methods directly. Implemented PTT button via native React event handlers (mousedown/mouseup/touchstart/touchend) that call ConnectionManager methods.
- **Files modified:** web-ui/src/components/ChannelCard.jsx
- **Commit:** a6b131b
- **Rationale:** ConnectionManager provides complete PTT functionality. Creating PttController would require exposing internal signaling client (breaking encapsulation) or wrapping already-wrapped functionality (unnecessary layer). Direct ConnectionManager usage is simpler and architecturally correct.

## Decisions Made

**ARCH-004: ConnectionManager built-in PTT methods replace PttController integration**
- **Decision:** Use ConnectionManager's `startTransmitting()` and `stopTransmitting()` methods directly instead of creating PttController instance
- **Rationale:** ConnectionManager evolved to include complete PTT logic. PttController constructor requires `SignalingClient`, but ConnectionManager only exposes `getMicrophoneManager()` and `getTransportClient()` getters, not signaling client. Creating PttController would require breaking ConnectionManager encapsulation or adding unnecessary wrapper layer.
- **Impact:** Simpler architecture, fewer components, cleaner separation of concerns. ChannelCard manages UI and calls ConnectionManager for audio/PTT logic.
- **Trade-off:** Loses PttButton visual component (fancy CSS states, audio feedback). Acceptable for MVP - can enhance UI later with custom button states.

**UX-006: Hold-to-talk PTT via native React event handlers**
- **Decision:** Implement PTT button with native React mousedown/mouseup/touchstart/touchend handlers instead of PttButton DOM injection
- **Rationale:** PttButton was designed for PttController integration (creates button DOM element dynamically). Without PttController, cleaner to use React-native button with event handlers.
- **Impact:** Simpler React component, no DOM manipulation, easier to test. Button behavior fully managed in React component lifecycle.

**UX-007: Optimistic PTT UI with error revert pattern**
- **Decision:** Update button state immediately on press, revert to idle on error
- **Rationale:** Instant visual feedback (physical walkie-talkie feel) requires optimistic UI. If PTT denied (channel busy), button shows "Channel Busy" state for 3 seconds then reverts.
- **Impact:** Better UX (no perceived lag), simple error recovery (auto-revert), minimal code complexity.

## Integration Points

**Upstream dependencies:**
- **03-01:** Vite config with @client/@shared aliases, ChannelContext for updateChannelState
- **01-07:** ConnectionManager with automatic reconnection and session recovery
- **01-05:** MediasoupDevice, TransportClient for WebRTC audio
- **01-04:** ReconnectingSignalingClient for WebSocket signaling

**Downstream dependencies:**
- **03-03:** Channels page will render ChannelCard for each user channel
- **03-04:** Real-time permission updates will add/remove ChannelCard instances
- **03-05:** Multi-channel dashboard will display multiple ChannelCards in grid layout

**Component integration pattern:**
```jsx
import ChannelCard from '../components/ChannelCard';
import { useAuth } from '../hooks/useAuth';

const ChannelsPage = () => {
  const { token } = useAuth();
  const { channels } = useChannels();
  const wsUrl = `wss://${window.location.host}/ws`;

  return (
    <div className="channels-grid">
      {channels.map((channel) => (
        <ChannelCard
          key={channel.id}
          channel={channel}
          wsUrl={wsUrl}
          token={token}
        />
      ))}
    </div>
  );
};
```

## Technical Notes

### ConnectionManager vs PttController Architecture

**ConnectionManager (high-level orchestrator):**
- Manages complete PTT system lifecycle
- Creates: ReconnectingSignalingClient, MediasoupDevice, TransportClient, MicrophoneManager
- Handles: connection, reconnection, session recovery, PTT start/stop
- Public methods: `connect()`, `disconnect()`, `startTransmitting()`, `stopTransmitting()`
- Public getters: `getMicrophoneManager()`, `getTransportClient()`, `getConnectionState()`, `getChannelState()`

**PttController (legacy lower-level controller):**
- Requires pre-created components: SignalingClient, TransportClient, MicrophoneManager, AudioFeedback
- Creates: PttButton (DOM element with visual states)
- Handles: PTT button UI, audio feedback tones, consumer management
- **Issue:** Expects SignalingClient, but ConnectionManager creates ReconnectingSignalingClient (no public getter)

**Architectural evolution:**
Phase 1 initially had separate components (SignalingClient, PttController). ConnectionManager was added in 01-07 to orchestrate reconnection. ConnectionManager now provides complete PTT functionality, making PttController redundant for React integration.

### Multi-Channel Architecture

**One ConnectionManager per channel:**
- Each ChannelCard creates separate ConnectionManager instance
- Each ConnectionManager creates separate WebSocket connection to `/ws`
- Each WebSocket connection creates separate mediasoup transport pair (send/recv)

**Why separate connections per channel:**
- Simplifies state management (each channel independent)
- Automatic cleanup when user leaves channel (close one connection)
- Matches server architecture (one SignalingServer per WebSocket connection)

**Alternative (NOT used):**
Single global ConnectionManager + PttController per channel would require:
- Signaling protocol changes (channel-scoped messages on single WebSocket)
- Complex transport pooling (reuse transports across channels)
- Manual producer/consumer management (track which belongs to which channel)

Current approach (one ConnectionManager per channel) is simpler and matches existing server architecture.

### Hold-to-Talk Event Handling

**Mouse events:**
- `mousedown` → startTransmitting()
- `mouseup` → stopTransmitting()
- `mouseleave` → stopTransmitting() (prevents stuck PTT if mouse leaves button while pressed)

**Touch events:**
- `touchstart` → startTransmitting()
- `touchend` → stopTransmitting()
- `touchcancel` → stopTransmitting() (handles phone call interruption, etc.)

**PreventDefault() on touch:**
Prevents page scrolling when user presses PTT button on mobile. Critical for good mobile UX.

### Busy State Logic

**Channel is busy when:**
- `channelState.isBusy === true` (someone else transmitting)
- PTT button disabled, shows "Channel Busy" state
- Speaker name displayed: `channelState.speakerName`

**User is transmitting when:**
- `isPressed === true` (local state)
- Button shows "Transmitting..." state
- Channel state updates to `isBusy: true` via onSpeakerChanged callback

**State flow:**
1. User presses PTT button → `setIsPressed(true)`, call `startTransmitting()`
2. ConnectionManager sends PTT_START to server
3. Server grants lock, broadcasts SPEAKER_CHANGED event
4. ConnectionManager's `onSpeakerChanged` callback fires
5. `updateChannelState(channelId, { isBusy: true, speakerId: userId, speakerName: userName })`
6. ChannelContext updates `channelStates[channelId]`
7. All ChannelCard instances for this channel re-render with busy indicator

## Next Phase Readiness

**Phase 3 Plan 03 (Channels Page) can proceed:**
- ✅ ChannelCard component ready for rendering in grid
- ✅ useChannelConnection handles connection lifecycle
- ✅ ChannelContext tracks speaker state globally
- ✅ No blockers

**CSS styling needed:**
- `.channel-card` container styles
- `.ptt-button` with states: `.ptt-idle`, `.ptt-transmitting`, `.ptt-blocked`
- `.channel-card__speaker` speaker indicator
- `.channel-card__status` connection status pill
- `.pill` badge styles with variants: `.pill--ok`, `.pill--info`, `.pill--warn`, `.pill--error`, `.pill--muted`

**Future enhancements:**
- Audio feedback tones (transmit-start, transmit-stop, busy-tone)
- PttButton visual component for fancier UI
- Toggle mode PTT (click to start, click to stop)
- Latency measurement (time from press to audio playback)

## Self-Check: PASSED

**Created files verified:**
- ✅ web-ui/src/hooks/useChannelConnection.js exists
- ✅ web-ui/src/components/ChannelCard.jsx exists

**Commits verified:**
- ✅ 2301952 exists in git log
- ✅ a6b131b exists in git log
