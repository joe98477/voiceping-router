# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-15)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** Phase 20 - Performance Optimization & Final Polish (v4.0 milestone)

## Current Position

Phase: 20 of 20 (Performance Optimization & Final Polish)
Plan: 01 of 02 complete
Status: In progress
Last activity: 2026-02-16 — Completed 20-01-PLAN.md (Adaptive Wake Lock & Network Stats Polling)

Progress: [███████████████████████████░] 94% (72/77 plans complete across all milestones)

## Performance Metrics

**Velocity:**
- Total plans completed: 72 (v1.0: 24, v2.0: 26, v3.0: 10, v4.0: 12)
- Average duration: v1.0 ~10.5 min, v2.0 ~8.2 min, v3.0 ~4.0 min, v4.0 ~6.8 min
- Total execution time: v1.0 ~4.2 hours, v2.0 ~3.5 hours, v3.0 ~0.67 hours, v4.0 ~1.26 hours

**By Milestone:**

| Milestone | Phases | Plans | Status |
|-----------|--------|-------|--------|
| v1.0 WebRTC Rebuild | 4 | 24/24 | Complete (2026-02-07) |
| v2.0 Android Client | 6 | 26/26 | Complete (2026-02-13) |
| v3.0 mediasoup Integration | 5 | 10/10 | Complete (2026-02-15) |
| v4.0 Production Hardening | 5 | 12/13 | In progress |

**Recent Trend:**
- v3.0 complete: 10 plans, 5 phases (11-15), real mediasoup audio verified on physical device
- Battery profiling validated: 5%/hour with screen off
- Phase 16 complete: Permission management foundation (2 plans, 787s total)
- Phase 17 complete: Audio reliability improvements (3 plans, 848s total)
- Phase 18 complete: Location tracking (3/3 plans, 1395s total)
- Phase 19 complete: Security hardening & code quality (3/3 plans, 1258s total)
- Phase 20 in progress: Power optimization & validation (1/2 plans, 700s so far)
- Trend: Stable velocity ~5-13 minutes per plan in v4.0

*Updated after 20-01 completion*

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 17 P01 | 284s | 4 | 7 |
| Phase 17 P02 | 213s | 2 | 7 |
| Phase 17 P03 | 351s | 2 | 4 |
| Phase 18 P01 | 263s | 2 | 8 |
| Phase 18 P02 | 783s | 2 | 10 |
| Phase 18 P03 | 349s | 2 | 11 |
| Phase 19 P01 | 352s | 2 | 9 |
| Phase 19 P02 | 310s | 2 | 5 |
| Phase 19 P03 | 596s | 2 | 23 |
| Phase 20 P01 | 700s | 2 | 10 |

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
- Phase 18-03: Offline queue FIFO eviction (prioritize fresh location data over stale data)
- Phase 18-03: Reconnect flush sends location-batch (not individual updates, reduces server overhead)
- Phase 18-03: PTT-triggered location wrapped in try/catch (prevent location errors from breaking PTT)
- Phase 18-03: currentLocation StateFlow updated before deduplication (shows all GPS fixes in debug screen)
- Phase 19-02: Hardcoded JWT secret default accepted as MEDIUM risk (production MUST override via ROUTER_JWT_SECRET env var)
- Phase 19-02: WebSocket rate limiting deferred to Phase 20 (authenticated connections, natural throttling, proxy-level protection)
- Phase 19-02: DTLS validation via fingerprint count logging (non-blocking, mediasoup enforces internally)
- [Phase 19]: TLS errors show user-visible error instead of silent retry (VPN/network/cert issues require user action)
- [Phase 19]: Defense-in-depth: server rejects cleartext WS in production even behind reverse proxy
- [Phase 19]: detekt maxIssues: -1 for initial run (566 weighted issues, mostly disabled noisy rules)
- [Phase 19]: Pre-commit hooks auto-format TypeScript (prettier) and Kotlin (ktlint) on every commit
- [Phase 19]: Formatting commit (53cdfca) isolated in .git-blame-ignore-revs for clean git blame history

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

Last session: 2026-02-16
Stopped at: Completed 20-01-PLAN.md (Adaptive Wake Lock & Network Stats Polling)
Resume file: None

**Next action:** Execute 20-02-PLAN.md (Location Tracking Power Integration).

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
- v4.0 Phase 18 (Location Tracking): 3/3 plans complete ✅
  - 18-01: Location Tracking Foundation ✅
  - 18-02: Server-Side Location Infrastructure ✅
  - 18-03: Android Location Service Integration ✅
- v4.0 Phase 19 (Security Audit & Hardening): 3/3 plans complete ✅
  - 19-01: TLS Enforcement & Cleartext Traffic Blocking ✅
  - 19-02: API Endpoint & DTLS Encryption Audit ✅
  - 19-03: Code Quality Improvements ✅
- v4.0 Phase 20 (Performance Optimization & Final Polish): 1/2 plans complete ⏳
  - 20-01: Adaptive Wake Lock & Network Stats Polling ✅
  - 20-02: Location Tracking Power Integration (pending)

---
*Last updated: 2026-02-16 after Phase 20-01 completion*
