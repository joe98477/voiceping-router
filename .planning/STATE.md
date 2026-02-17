# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-16)

**Core value:** Reliable, secure real-time audio communication for coordinating 1000+ distributed team members during high-profile events where security and uptime are critical
**Current focus:** v5.0 Dispatch Map View - Phase 21

## Current Position

Phase: 22 of 26 (Web Layout Split)
Plan: 1 of 2 (CSS Grid split-panel layout)
Status: Executing phase 22
Last activity: 2026-02-17 — Completed 22-01 CSS Grid split-panel layout

Progress: [█████████████████████████████████████████████████████████████████████████░░░] 75/85 plans (88.2%)

## Performance Metrics

**Velocity:**
- Total plans completed: 75 (v1.0: 24, v2.0: 26, v3.0: 10, v4.0: 13, v5.0: 2)
- Average duration: v1.0 ~10.5 min, v2.0 ~8.2 min, v3.0 ~4.0 min, v4.0 ~6.2 min, v5.0 ~2.5 min
- Total execution time: v1.0 ~4.2 hours, v2.0 ~3.5 hours, v3.0 ~0.67 hours, v4.0 ~1.28 hours, v5.0 ~0.09 hours

**By Milestone:**

| Milestone | Phases | Plans | Status |
|-----------|--------|-------|--------|
| v1.0 WebRTC Rebuild | 4 | 24/24 | Complete (2026-02-07) |
| v2.0 Android Client | 6 | 26/26 | Complete (2026-02-13) |
| v3.0 mediasoup Integration | 5 | 10/10 | Complete (2026-02-15) |
| v4.0 Production Hardening | 5 | 13/13 | Complete (2026-02-16) |
| v5.0 Dispatch Map View | 6 | 2/12 | In progress |

**Recent Plan:**
| Phase | Plan | Duration | Tasks | Files |
|-------|------|----------|-------|-------|
| Phase 21 P02 | 3 min | 2 tasks | 2 files | Android telemetry collection |
| Phase 21 P01 | 4 | 2 tasks | 5 files |
| Phase 22 P01 | 146 | 2 tasks | 3 files |

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md Key Decisions table (43 entries across 4 milestones).

Recent decisions affecting v5.0:
- Phase 18 (v4.0): SQLite for location storage (not PostgreSQL) — lightweight, embedded, 24h retention
- Phase 21 (v5.0): Battery % as additive protocol field — backward-compatible, old clients omit field
- Phase 21 (v5.0): Leaflet + Esri World Imagery for dispatch map — free, no API key, satellite tiles
- Phase 21 (v5.0): Cross-component stability constraint — all protocol changes backward-compatible
- Phase 21-02 (v5.0): Nullable telemetry with null omission in JSON — backward compatible, emulators return null gracefully
- Phase 21-02 (v5.0): Conservative null fallback (assume 100% battery) — don't skip location sends when battery unavailable
- Phase 22-01 (v5.0): No visible divider between panels (gap: 0) — map panel background shade provides subtle boundary
- Phase 22-01 (v5.0): Sharp edges on map-container (no border-radius) — allows edge-to-edge Leaflet rendering
- Phase 22-01 (v5.0): Stats bar space-around — items spread evenly across full page width
- Phase 22-01 (v5.0): Channel cards 180px minmax — ensures 2-column fit in 500px channels panel

### Pending Todos

None.

### Blockers/Concerns

**Carried forward:**
- Consumer.getStats() returns stub quality (crow-misia API undocumented) — low priority
- No automated integration tests for mediasoup audio pipeline — deferred
- HW-02 rugged phone PTT hardware unavailable — deferred
- PWR-04 battery profiling not validated — implementation complete, profiling deferred
- Hardcoded JWT secret default — production MUST override via ROUTER_JWT_SECRET

**v5.0 specific:**
- Leaflet memory leak risk — Phase 23 must establish correct cleanup pattern from start (React Strict Mode validation)
- Marker performance collapse at 200+ markers — Phase 25 must use canvas or clustering, not DOM markers
- WebSocket message queue saturation — Phase 24 needs separate LocationContext from ChannelContext

## Session Continuity

Last session: 2026-02-17
Stopped at: Completed 22-01-PLAN.md
Resume file: .planning/phases/22-web-layout-split/22-01-SUMMARY.md

**Next action:** Execute Phase 22 Plan 02 (Panel collapse and responsive layout)

**All Milestones:**
- v1.0 WebRTC Audio Rebuild + Web UI — SHIPPED 2026-02-07
- v2.0 Android Client App — SHIPPED 2026-02-13
- v3.0 mediasoup Library Integration — SHIPPED 2026-02-15
- v4.0 Production Hardening & Location — SHIPPED 2026-02-16
- v5.0 Dispatch Map View — IN PROGRESS (Phase 21-26)

---
*Last updated: 2026-02-17 after completing 22-01-PLAN.md*
