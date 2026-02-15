# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-15)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** Phase 16 - Permission Management (v4.0 milestone)

## Current Position

Phase: 18 of 20 (Location Tracking)
Plan: 01 of 03 complete
Status: In Progress
Last activity: 2026-02-15 — Completed 18-01-PLAN.md (Location Tracking Foundation)

Progress: [█████████████████████████░░░] 86% (66/77 plans complete across all milestones)

## Performance Metrics

**Velocity:**
- Total plans completed: 66 (v1.0: 24, v2.0: 26, v3.0: 10, v4.0: 6)
- Average duration: v1.0 ~10.5 min, v2.0 ~8.2 min, v3.0 ~4.0 min, v4.0 ~5.0 min
- Total execution time: v1.0 ~4.2 hours, v2.0 ~3.5 hours, v3.0 ~0.67 hours, v4.0 ~0.5 hours

**By Milestone:**

| Milestone | Phases | Plans | Status |
|-----------|--------|-------|--------|
| v1.0 WebRTC Rebuild | 4 | 24/24 | Complete (2026-02-07) |
| v2.0 Android Client | 6 | 26/26 | Complete (2026-02-13) |
| v3.0 mediasoup Integration | 5 | 10/10 | Complete (2026-02-15) |
| v4.0 Production Hardening | 5 | 6/13 | In progress |

**Recent Trend:**
- v3.0 complete: 10 plans, 5 phases (11-15), real mediasoup audio verified on physical device
- Battery profiling validated: 5%/hour with screen off
- Phase 16 complete: Permission management foundation (2 plans, 787s total)
- Phase 17 complete: Audio reliability improvements (3 plans, 848s total)
- Phase 18 in progress: Location tracking (1/3 plans, 263s)
- Trend: Stable velocity ~4-5 minutes per plan in v4.0

*Updated after 18-01 completion*

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 17 P01 | 284s | 4 | 7 |
| Phase 17 P02 | 213s | 2 | 7 |
| Phase 17 P03 | 351s | 2 | 4 |
| Phase 18 P01 | 263s | 2 | 8 |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting v4.0 work:

- v3.0: libmediasoup-android 0.21.0 integration confirmed working on physical device
- v3.0: Full R8 optimization with comprehensive JNI keep rules (42.8 MB APK)
- v3.0: Battery profiling shows 5%/hour baseline with screen off
- v4.0 research: Permission management foundation required before location/audio features
- v4.0 research: Audio reliability targets WebRTC jitter buffer tuning, not library replacement
- Phase 16-01: In-memory denial tracking resets on app restart (no persistent tracking across restarts)
- Phase 16-01: Settings redirect dialog appears after 2 denials of same permission
- Phase 16-01: Permission banner auto-dismisses when permission granted (not user-dismissible)
- Phase 16-01: PTT button visibly disabled (grayed out with MicOff icon) when mic missing
- Phase 16-02: Permission education screen skippable with "Skip" button
- Phase 16-02: No auto-retry after granting from rationale dialog — user re-triggers action
- Phase 16-02: Location icon purely informational (not tappable)
- Phase 16-02: Permission banner Fix action navigates to in-app settings (not system settings)
- Phase 16-02: Notification revocation shows one-time toast warning
- Phase 17-01: Producer retry logic: 2 retries (3 total attempts) with exponential backoff (1s, 2s)
- Phase 17-01: Opus DTX disabled for continuous stream (comfort noise), FEC always enabled with packetLossPercentage=10
- Phase 17-01: Confirmation tone default ON, independent toggle from roger beep
- Phase 17-01: Error haptic pattern: double-buzz at full amplitude (distinct from denied/release)
- Phase 17-02: ACK flash overrides all PTT button colors with 300ms duration (green=success, red=failure)
- Phase 17-02: Confirmation tone toggle independent from roger beep (both can be enabled/disabled separately)
- Phase 17-02: DevStatsScreen debug-only via BuildConfig.DEBUG guard (consumer stats stubbed pending validation)
- Phase 17-03: 2s grace period for mid-transmission transport failures (reduces false failures)
- Phase 17-03: 15s orphan cleanup for disconnected transports (aligns with WebRTC auto-recovery window)
- Phase 17-03: 5 max auto-rejoin attempts with exponential backoff (balances recovery with user control)
- Phase 17-03: SEND_DEGRADED state for partial failures (user can still hear when send transport fails)
- Phase 17-03: Silent recovery to HEALTHY state (no toast on recovery, reduces notification fatigue)

### Pending Todos

None yet.

### Blockers/Concerns

**Known from v3.0:**
- Consumer.getStats() returns stub quality (crow-misia API undocumented) - low priority
- No automated integration tests for mediasoup audio pipeline - deferred
- HW-02 rugged phone PTT hardware unavailable - deferred

**v4.0 Phase Planning:**
- Phase 17: WebRTC jitter buffer API access via crow-misia library needs validation during planning
- Phase 18: Android 14+ foreground service type declaration must be tested on Android 14+ device
- Phase 19: Security audit scope needs definition (server + client or client-only)
- Phase 20: Battery profiling target adjusted to <6%/hour (from 5%/hour) to account for location tracking overhead

## Session Continuity

Last session: 2026-02-15
Stopped at: Completed 18-01-PLAN.md (Location Tracking Foundation)
Resume file: None

**Next action:** Continue Phase 18 with plan 02 (Server Location Endpoint).

**Previous Milestones:**
- v1.0 WebRTC Audio Rebuild + Web UI — SHIPPED 2026-02-07
- v2.0 Android Client App — SHIPPED 2026-02-13
- v3.0 mediasoup Library Integration — SHIPPED 2026-02-15

**Current Milestone Progress:**
- v4.0 Phase 16 (Permission Management): 2/2 plans complete ✅
  - 16-01: Permission Manager & Graceful Degradation ✅
  - 16-02: Permission Education Screen ✅
- v4.0 Phase 17 (Audio Reliability Improvements): 3/3 plans complete ✅
  - 17-01: Audio Reliability Foundation ✅
  - 17-02: Producer ACK & Confirmation Feedback ✅
  - 17-03: Transport Health Monitoring & Auto-Rejoin ✅
- v4.0 Phase 18 (Location Tracking): 1/3 plans complete
  - 18-01: Location Tracking Foundation ✅
  - 18-02: Server Location Endpoint (next)
  - 18-03: SignalingClient Location Transmission (pending)

---
*Last updated: 2026-02-15 after Phase 18-01 completion*
