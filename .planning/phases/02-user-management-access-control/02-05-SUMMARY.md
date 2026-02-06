---
phase: 02-user-management-access-control
plan: 05
subsystem: signaling
tags: [dispatch, priority-ptt, emergency-broadcast, role-based-access, mediasoup, redis]

# Dependency graph
requires:
  - phase: 02-01
    provides: UserRole enum, AuditLogger with PRIORITY_PTT_START/INTERRUPTED/EMERGENCY_BROADCAST actions
  - phase: 02-03
    provides: SignalingHandlers with role-aware context, permission enforcement patterns
  - phase: 02-04
    provides: Event-to-channel mapping in ChannelStateManager (getChannelsForEvent, pauseAllSpeakers)
provides:
  - DispatchHandlers class for priority PTT and emergency broadcast
  - Priority PTT: Dispatch can interrupt General users (not other Dispatch)
  - Emergency broadcast: Multi-channel broadcast with 2s hold guard
  - getUserProducerId method in SignalingHandlers for producer access
  - Delegation methods in SignalingHandlers with role validation
affects: [02-06-force-disconnect, 07-websocket-integration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Priority PTT override pattern (Dispatch > General, Dispatch = Dispatch)
    - Emergency broadcast temporary channel join tracking
    - PTT_INTERRUPTED message for interrupted speakers

key-files:
  created:
    - src/server/signaling/dispatchHandlers.ts
  modified:
    - src/server/signaling/handlers.ts

key-decisions:
  - "Dispatch can interrupt General users but not other Dispatch users (equal priority among Dispatch)"
  - "Emergency broadcast requires 2-second hold guard to prevent accidental activation"
  - "Temporary channel joins tracked separately and cleaned up after emergency broadcast"
  - "getUserProducerId made public for DispatchHandlers to access producer IDs"
  - "Delegation methods include role validation before delegating (defense in depth)"

patterns-established:
  - "Priority override pattern: Check current speaker role, interrupt if lower priority"
  - "Emergency broadcast pattern: Pause all speakers, temporary joins, multi-channel lock acquisition"
  - "PTT_INTERRUPTED message sent to interrupted users with interrupter name and reason"

# Metrics
duration: 3min
completed: 2026-02-06
---

# Phase 2 Plan 5: Dispatch PTT Priority & Emergency Broadcast Summary

**Priority PTT enables Dispatch to interrupt General users; emergency broadcast pauses all speakers across event channels with 2s hold guard**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-06T11:05:15Z
- **Completed:** 2026-02-06T11:08:58Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Implemented DispatchHandlers with priority PTT override logic (Dispatch > General)
- Emergency broadcast with event-wide speaker pause and temporary channel joins
- Role-based delegation methods in SignalingHandlers with permission validation
- Producer ID access method for dispatch operations

## Task Commits

Each task was committed atomically:

1. **Task 1: Create DispatchHandlers** - `929823f` (feat)
2. **Task 2: Wire dispatch handlers into SignalingHandlers** - `4fc85e2` (feat)

## Files Created/Modified
- `src/server/signaling/dispatchHandlers.ts` - DispatchHandlers class with priority PTT and emergency broadcast logic
- `src/server/signaling/handlers.ts` - Added getUserProducerId method and delegation methods with role validation

## Decisions Made

**DISPATCH-001: Priority PTT override rules**
- **Decision:** Dispatch can interrupt General users, but denied if another Dispatch is speaking
- **Rationale:** Dispatch users have equal priority (no hierarchy within Dispatch role), but higher priority than General users
- **Impact:** Prevents Dispatch-on-Dispatch interruption while enabling supervision of General users

**DISPATCH-002: Emergency broadcast hold duration guard**
- **Decision:** Require 2-second button hold to activate emergency broadcast (config.dispatch.emergencyBroadcastHoldMs)
- **Rationale:** Prevents accidental activation of event-wide broadcast which interrupts all speakers
- **Impact:** Client must track button hold duration and only send message after threshold; reduces risk of mis-clicks

**DISPATCH-003: Temporary channel join tracking**
- **Decision:** Track which channels were temporary joins during emergency broadcast, leave only those channels after broadcast ends
- **Rationale:** Dispatch user may be permanently in some channels; only temporary joins should be cleaned up
- **Impact:** Emergency broadcast state tracks temporaryJoins Set, cleanup only leaves those channels

**DISPATCH-004: getUserProducerId public method**
- **Decision:** Made getUserProducerId public in SignalingHandlers for DispatchHandlers to access producer IDs
- **Rationale:** DispatchHandlers needs to pause/resume producers for interrupted speakers and Dispatch user
- **Impact:** Exposes producer lookup without exposing entire userProducers map; clean encapsulation

**DISPATCH-005: Delegation methods with role validation**
- **Decision:** Delegation methods in SignalingHandlers validate ctx.role before delegating to DispatchHandlers
- **Rationale:** Defense in depth - role check at entry point plus in handler implementation
- **Impact:** Audit logs PERMISSION_DENIED at SignalingHandlers level for unauthorized attempts

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for 02-06 (Force Disconnect):**
- DispatchHandlers infrastructure in place
- Role validation pattern established
- Audit logging integrated

**Wiring deferred to Plan 07:**
- DispatchHandlers instantiation and wiring to SignalingHandlers will happen in Plan 07 (WebSocket integration)
- sendToUser function currently stubbed (placeholder), will be wired in Plan 07
- getClientContext helper method stubbed, will be implemented when WebSocketServer client map is accessible

**No blockers** - all Dispatch PTT and emergency broadcast logic complete, ready for integration.

## Self-Check: PASSED

All files and commits verified successfully.

---
*Phase: 02-user-management-access-control*
*Completed: 2026-02-06*
