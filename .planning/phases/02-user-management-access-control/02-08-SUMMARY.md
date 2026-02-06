---
phase: "02"
plan: "08"
title: "Load Testing & E2E Verification"
subsystem: "testing"
completed: 2026-02-06
duration: 5 minutes

tags:
  - load-testing
  - e2e-testing
  - verification
  - websocket
  - jwt
  - redis

requires:
  phases:
    - "02-07": "Integration wiring - all Phase 2 modules operational"
  features:
    - "JWT authentication with role-based claims"
    - "WebSocket signaling server"
    - "Redis state management with user-channel mappings"
    - "Role-based permissions (ADMIN, DISPATCH, GENERAL)"
    - "Priority PTT and emergency broadcast"
    - "Admin controls (force disconnect, ban/unban)"

provides:
  deliverables:
    - "Load test script for 100 concurrent users"
    - "Browser-based E2E test page for Phase 2 features"
    - "Dev endpoint for seeding Redis test data"
  capabilities:
    - "Automated scalability verification (100 users)"
    - "Manual feature verification for Phase 2 role-based controls"
    - "Performance metrics tracking (connection, join, PTT, interrupt times)"

affects:
  phases:
    - "03-browser-ui": "E2E test pattern can be adapted for Phase 3 browser UI testing"
    - "04-optimization-monitoring": "Load test provides baseline metrics for optimization targets"

tech-stack:
  added:
    - tool: "tsx"
      purpose: "Running TypeScript load test script directly"
  patterns:
    - pattern: "Load test with phased execution"
      description: "6-phase test: connection ramp, channel join, PTT load, dispatch priority, permission revocation, graceful disconnect"
    - pattern: "Browser-based E2E test page"
      description: "Three role-based panels with WebSocket signaling, JWT token generation, and live interaction testing"

key-files:
  created:
    - path: "src/server/test/loadTest.ts"
      purpose: "100-user load test with metrics tracking and pass/fail criteria"
    - path: "src/client/test/e2e-phase2.html"
      purpose: "HTML structure for Phase 2 E2E test page with 3 role panels"
    - path: "src/client/test/e2e-phase2.ts"
      purpose: "TypeScript logic for E2E test page with WebSocket signaling and role-based controls"
  modified:
    - path: "src/server/index.ts"
      purpose: "Added dev endpoint /dev/seed-test-data and routes for Phase 2 test page"
    - path: "package.json"
      purpose: "Added test:load and build:test-phase2 scripts"

decisions:
  - id: "TEST-001"
    decision: "Load test uses direct WebSocket connections instead of full browser automation"
    rationale: "Simpler, faster, and focuses on server scalability rather than browser compatibility"
    impact: "Load test can run headless on CI/CD; separate E2E test page handles browser-specific verification"
  - id: "TEST-002"
    decision: "E2E test page uses simple token generation on client for demo purposes"
    rationale: "Dev-only test page doesn't need production-grade token security; simplifies testing workflow"
    impact: "Test page can generate tokens without backend call; prod tokens still come from control-plane"
  - id: "TEST-003"
    decision: "Redis seeding endpoint is dev-only with NODE_ENV check"
    rationale: "Production deployments should never expose test data seeding"
    impact: "Test page functional in dev mode only; prod servers return 404 for /dev/* routes"
---

# Phase 02 Plan 08: Load Testing & E2E Verification Summary

**One-liner:** Automated load test for 100 concurrent users with 6-phase validation and browser-based E2E test page for Phase 2 role-based features

## Overview

This plan delivered comprehensive testing infrastructure for Phase 2 verification:

1. **Load Test Script (src/server/test/loadTest.ts):** Simulates 100 concurrent users with JWT authentication, Redis-seeded permissions, and 6 test phases to verify scalability, performance, and role-based features. Tracks metrics with pass/fail criteria.

2. **E2E Test Page (src/client/test/e2e-phase2.*):** Browser-based test page with 3 role-based panels (Admin, Dispatch, General) for manual verification of Phase 2 features including channel management, PTT controls, priority interruption, emergency broadcast, and admin actions.

3. **Dev Endpoint (/dev/seed-test-data):** POST endpoint for seeding Redis with test users and channel mappings, enabling quick test setup.

## What Was Built

### Load Test Script

**File:** `src/server/test/loadTest.ts` (654 lines)

**Features:**
- JWT token generation for 100 test users (80 GENERAL, 15 DISPATCH, 5 ADMIN)
- Redis seed/cleanup functions for user-channel mappings
- 6 test phases with metrics tracking:
  1. **Connection Phase:** Ramp 100 users over 10s (10 users/sec)
  2. **Channel Join Phase:** Each user joins assigned channels
  3. **PTT Load Phase:** 10% of users start PTT simultaneously on different channels
  4. **Dispatch Priority Phase:** Dispatch user interrupts General user
  5. **Permission Revocation Phase:** Revoke 5 users' access, verify removal within 30s
  6. **Disconnect Phase:** Graceful disconnect all users
- Metrics table with pass/fail criteria:
  - Connection success rate: 100%
  - Avg connection time: <500ms
  - Avg join time: <200ms
  - Avg PTT lock time: <50ms
  - Dispatch interrupt time: <100ms

**Usage:**
```bash
npm run test:load
```

### E2E Test Page

**Files:**
- `src/client/test/e2e-phase2.html` (HTML structure)
- `src/client/test/e2e-phase2.ts` (TypeScript logic)

**Features:**
- **Three Role-Based Panels:**
  - **Admin Panel:** Force disconnect, ban/unban controls
  - **Dispatch Panel:** Priority PTT, emergency broadcast (2s hold), force disconnect
  - **General Panel:** Standard PTT controls
- **Common Controls:**
  - Connection status indicator
  - Channel list with join/leave buttons
  - PTT button with active state animation
  - Status log with timestamped entries (info/success/error/warning)
- **Test Controls:**
  - "Seed Test Data" button (calls /dev/seed-test-data)
  - "Clear All Logs" button
  - JWT token generator form (userId, role, eventId, channelIds)

**Access:**
- URL: `http://localhost:3000/test/phase2` (dev mode only)
- Users: `admin-1`, `dispatch-1`, `general-1`
- Channels: `test-channel-1`, `test-channel-2`

### Dev Endpoint

**Route:** `POST /dev/seed-test-data` (dev mode only)

**Action:** Seeds Redis with test users and channel mappings:
- Creates 3 test users (admin-1, dispatch-1, general-1)
- Assigns each user to test-channel-1 and test-channel-2
- Creates channel-user reverse mappings
- Creates event-channel mapping for test-event-1

**Response:**
```json
{
  "success": true,
  "users": ["admin-1", "dispatch-1", "general-1"]
}
```

## Deviations from Plan

None - plan executed exactly as written.

## Task Commits

1. **553e5e3** - test(02-08): add load test script for 100 concurrent users
   - Created `src/server/test/loadTest.ts`
   - Added `test:load` script to package.json
   - 6-phase load test with metrics tracking

2. **c26fbc7** - feat(02-08): add Phase 2 E2E test page for feature verification
   - Created `src/client/test/e2e-phase2.html` and `e2e-phase2.ts`
   - Added dev endpoint `/dev/seed-test-data` to `index.ts`
   - Added `build:test-phase2` script to package.json
   - Served at `/test/phase2` in dev mode

## Decisions Made

| ID | Decision | Rationale | Impact |
|----|----------|-----------|--------|
| TEST-001 | Load test uses direct WebSocket connections instead of full browser automation | Simpler, faster, and focuses on server scalability rather than browser compatibility | Load test can run headless on CI/CD; separate E2E test page handles browser-specific verification |
| TEST-002 | E2E test page uses simple token generation on client for demo purposes | Dev-only test page doesn't need production-grade token security; simplifies testing workflow | Test page can generate tokens without backend call; prod tokens still come from control-plane |
| TEST-003 | Redis seeding endpoint is dev-only with NODE_ENV check | Production deployments should never expose test data seeding | Test page functional in dev mode only; prod servers return 404 for /dev/* routes |

## Testing & Verification

### Compilation Verification

```bash
npx tsc --noEmit
# Result: No errors (verified after each task)
```

### Load Test Metrics

The load test provides automated verification of:
- **Scalability:** 100 concurrent users connect successfully
- **Performance:** Connection, join, PTT lock times meet thresholds
- **Role-based features:** Dispatch priority interrupt works correctly
- **Permission sync:** Revoked permissions propagate within 30s

Expected results (when server is running):
- Connection success rate: 100%
- Avg connection time: <500ms
- Avg join time: <200ms
- Avg PTT lock time: <50ms
- Dispatch interrupt time: <100ms

### E2E Test Page Verification

Manual verification workflow:
1. Start server: `npm run dev`
2. Open: `http://localhost:3000/test/phase2`
3. Click "Seed Test Data" button
4. Connect all three panels (Admin, Dispatch, General)
5. Join channels in each panel
6. Test PTT interactions:
   - General user starts PTT
   - Dispatch user interrupts with Priority PTT
   - Admin force disconnects General user
7. Test emergency broadcast (hold Dispatch button for 2s)
8. Test ban/unban controls

## Integration Points

### With Phase 2 Modules

**Load test validates:**
- WebSocket signaling server (01-04)
- JWT authentication (02-01)
- Redis state management (01-03, 02-03)
- Permission sync (02-04)
- Dispatch priority (02-05)
- Admin controls (02-06)
- Integration wiring (02-07)

**E2E test page exercises:**
- All Phase 2 signaling types
- Role-based permission checks
- Real-time permission updates
- Force disconnect and ban/unban flows

## Known Limitations

1. **Load test requires running server:** Cannot test server startup/shutdown behavior
2. **E2E test page JWT generation:** Uses base64-encoded payload for demo; real tokens need ROUTER_JWT_SECRET signing
3. **No automated browser testing:** E2E test page is manual; Phase 3 could add Playwright/Cypress
4. **Single-server testing only:** Load test doesn't validate multi-server distributed deployment

## Next Phase Readiness

### Phase 3 (Browser UI)

**Ready:**
- E2E test page provides pattern for browser-based client components
- Token generation form demonstrates client-side JWT handling
- Role-based panels show UI patterns for different user roles

**Needs:**
- Production-grade token generation endpoint
- Real authentication flow (login/logout)
- Browser UI component library (React, per PROJECT.md decision)

### Phase 4 (Optimization & Monitoring)

**Ready:**
- Load test provides baseline performance metrics
- Metrics tracking pattern can be extended to production monitoring

**Needs:**
- Prometheus/Grafana integration for real-time metrics
- Distributed load testing across multiple servers
- Long-running stability tests (hours/days)

## Risks/Concerns

1. **Load test thresholds may need tuning:** Current thresholds (500ms connection, 200ms join, 50ms PTT) are estimates; real-world performance may differ
2. **Redis cleanup on test failure:** Load test cleanup runs in finally block, but process kill may leave test data in Redis
3. **E2E test page token security:** Base64 encoding is NOT secure; only acceptable because dev-only with NODE_ENV check

## Performance Impact

- **Load test script:** Zero runtime impact (standalone script)
- **E2E test page:** Dev-only routes, zero prod impact
- **Dev endpoint:** Dev-only with NODE_ENV check, zero prod impact

## Self-Check: PASSED

Created files verified:
- FOUND: src/server/test/loadTest.ts
- FOUND: src/client/test/e2e-phase2.html
- FOUND: src/client/test/e2e-phase2.ts

Modified files verified:
- FOUND: src/server/index.ts (dev endpoint added)
- FOUND: package.json (scripts added)

Commits verified:
- FOUND: 553e5e3 (load test)
- FOUND: c26fbc7 (E2E test page)
