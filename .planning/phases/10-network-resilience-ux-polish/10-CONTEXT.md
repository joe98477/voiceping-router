# Phase 10: Network Resilience & UX Polish - Context

**Gathered:** 2026-02-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Production-ready app with cellular network resilience and polished user experience. Covers auto-reconnection, WiFi/cellular handoff, offline state with cached data, network quality indicators, transmission history, and completion of haptic feedback patterns. Settings consolidation for all existing preferences (scan mode, button mapping, audio output, auto-start).

</domain>

<decisions>
## Implementation Decisions

### Reconnection behavior
- Brief drops (1-3s): Audio drops silently, resumes when back. No user notification for brief blips.
- Long disconnection (5+s): "Reconnecting..." banner slides down from top bar AND connection dot changes color. Both indicators active.
- WiFi-to-cellular handoff: Hold PTT, attempt transparent reconnection across handoff. Don't auto-release.
- Extended disconnection (30+s): Auto-rejoin ALL previously monitored channels when connection restores.
- Retry strategy: Exponential backoff (1s, 2s, 4s, 8s... capped at 30s).
- Max retry: Give up after 5 minutes, show "Connection lost" with manual Retry button.
- Network restore: When ConnectivityManager detects network available, reset backoff and retry immediately.
- Banner text: Just "Reconnecting..." — no attempt count or elapsed time shown.

### Connection tones
- Subtle up tone when connecting/reconnecting successfully.
- Down tone on disconnection.
- Respect existing tone toggle setting (silent if tones disabled).

### PTT during reconnection
- PTT button stays interactive during reconnection.
- If user presses PTT while disconnected, show error tone + haptic (existing error patterns).
- Do NOT gray out or disable PTT.

### Offline state & caching
- Cached channel list stays interactive when offline — user can browse, tap channels, see details.
- PTT errors if pressed while offline (same error pattern as reconnection).
- Persist event/channel/team structure to disk — app shows last-known state even on cold start with no network.
- Bottom bar shows primary channel name with offline badge. PTT available but errors if pressed.
- Foreground notification keeps channel name, adds "Reconnecting..." as subtitle.

### Network quality indicator
- Placement: Top bar, near existing connection status dot.
- Visual form: Signal bars icon (like cellular signal strength). Universally understood.
- Tap action: Tapping signal bars reveals popup/tooltip showing latency (ms), connection type (WiFi/cellular), and server name.
- Quality metric: WebSocket ping latency (simple round-trip on existing connection).

### Transmission history
- Access: Long-press on channel row opens bottom sheet with transmission history.
- Entry info: Speaker name + timestamp + duration per transmission.
- History depth: Last 20 transmissions per channel.
- Persistence: Current session only (in-memory). Clears on app restart.

### Claude's Discretion
- Exact reconnection timing thresholds (what counts as "brief" vs "long")
- Signal bars threshold values (latency ranges for 1/2/3/4 bars)
- Quality popup layout and animation
- Bottom sheet design for transmission history
- Haptic feedback completion — specific vibration patterns for any missing events
- Settings screen organization and grouping
- ConnectivityManager implementation details (NetworkCallback vs polling)
- Disk caching mechanism (Room, DataStore, or file-based)

</decisions>

<specifics>
## Specific Ideas

- Connection tones should feel like radio — subtle up tone for connect, down tone for disconnect (user's description)
- Offline experience should feel like a radio that lost signal — everything visible, just can't transmit
- Signal bars icon is familiar from cellular indicators — don't reinvent the wheel

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 10-network-resilience-ux-polish*
*Context gathered: 2026-02-12*
