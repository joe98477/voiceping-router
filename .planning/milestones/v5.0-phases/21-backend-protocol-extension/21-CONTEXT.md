# Phase 21: Backend Protocol Extension - Context

**Gathered:** 2026-02-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend the location protocol with optional telemetry fields (battery percentage, power-save mode, network type) for dispatch monitoring. Touches Android (send), server (store/relay), and web (ignore gracefully). Old clients without telemetry continue to work unchanged.

</domain>

<decisions>
## Implementation Decisions

### Battery data scope
- Battery percentage as integer (0-100), no decimals
- No charging state — just the percentage number
- Include power-save mode status (boolean: Android battery saver on/off)
- Include network type: WiFi vs Cellular (simple binary, no 3G/4G/5G distinction)
- When battery info is unavailable (emulator, permissions denied): send null/-1 sentinel value to explicitly signal "unknown"

### Update triggers
- Telemetry piggybacks on existing LOCATION_UPDATE messages — no separate message type
- No urgent/out-of-cycle sends for battery events — telemetry rides the normal location cadence
- Server stores only the latest telemetry snapshot per user, not history
- Server always includes all telemetry fields in every LOCATION_BROADCAST (no delta/change-only optimization)
- Extend existing LOCATION_QUERY response with telemetry fields for initial state sync on dispatch connect/reconnect
- When a user goes offline, last known telemetry remains available to dispatch (with stale indicator from Phase 25)

### Low battery alerting
- Server flags lowBattery: true/false based on a 20% threshold (server-side determination, not client)
- Single threshold only — no critical tier
- Both: lowBattery flag in every LOCATION_BROADCAST AND a one-time LOW_BATTERY_ALERT event when crossing below 20%
- Alert fires once when dropping below 20%, suppressed until battery recovers above 20% and drops again
- No recovery event — just suppression reset

### Dispatch visibility intent (shapes Phase 25 data requirements)
- Battery display: battery icon + percentage number, icon changes color at threshold
- Color scheme: green (50%+), yellow (20-49%), red (below 20%) — classic traffic light
- All three telemetry fields visible in status popup (battery %, power-save, network type)
- Small battery color indicator attached to each map marker (visible without hovering)
- Power-save mode: warning icon on marker when active + "Power Saver: ON" text in popup
- Network type: text label ("WiFi" / "Cellular") in popup only — no marker icon for network
- Low battery alert UX: toast notification when threshold crossed + marker turns red (persistent)
- Map popup is sufficient — no separate battery summary panel needed

### Claude's Discretion
- Exact sentinel value for unavailable battery (null vs -1 vs omit)
- LOW_BATTERY_ALERT message payload structure
- Database column types for telemetry fields in SQLite
- How to read battery/power-save/network on Android (BatteryManager vs sticky intent)
- Toast notification duration and styling

</decisions>

<specifics>
## Specific Ideas

- User wants battery icon to resemble a phone battery indicator — instantly recognizable
- Toast + marker color change for low battery: "can't miss it" approach — dispatchers need to notice urgently
- Power-save warning icon on marker is important because power-save affects location accuracy (dispatchers should know)
- Keep network type simple (WiFi/Cellular text) — not a primary concern for dispatchers, just supplementary info

</specifics>

<deferred>
## Deferred Ideas

- Battery drain trend analysis (history storage) — potential future phase
- Battery summary panel sorted by level — could be added in Phase 26 polish or later
- Signal strength tracking — decided against for now, could be future enhancement
- Cellular generation (3G/4G/5G) distinction — kept simple as WiFi/Cellular for now

</deferred>

---

*Phase: 21-backend-protocol-extension*
*Context gathered: 2026-02-16*
