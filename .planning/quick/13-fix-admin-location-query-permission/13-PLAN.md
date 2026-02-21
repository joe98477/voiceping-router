---
phase: quick-13
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/server/signaling/handlers.ts
autonomous: true

must_haves:
  truths:
    - "ADMIN role can call LOCATION_QUERY and receive field unit locations"
    - "DISPATCH role continues to work (no regression)"
    - "GENERAL/OPERATOR roles still denied LOCATION_QUERY"
  artifacts:
    - path: "src/server/signaling/handlers.ts"
      provides: "LOCATION_QUERY allowed for ADMIN and DISPATCH roles"
      contains: "ctx.role === UserRole.ADMIN || ctx.role === UserRole.DISPATCH"
  key_links:
    - from: "handlers.ts:handleLocationQuery"
      to: "handlers.ts:role check"
      via: "UserRole.ADMIN added to allowed roles"
      pattern: "LOCATION_QUERY denied"
---

<objective>
Fix LOCATION_QUERY permission: ADMIN role is denied access to dispatch map location data.
Admin should have all DISPATCH permissions plus admin rights (per product spec).
Server log shows: "LOCATION_QUERY denied for <admin-userId>: not a Dispatch user"

The fix is in handleLocationQuery (or wherever the DISPATCH role check is) in handlers.ts —
extend the role guard to allow UserRole.ADMIN in addition to UserRole.DISPATCH.

Also audit all other permission guards in handlers.ts and dispatchHandlers.ts to ensure
any DISPATCH-only checks also permit ADMIN, keeping the invariant: ADMIN ⊇ DISPATCH.

Purpose: Admin user can see field unit locations on the dispatch map.
Output: Fixed handlers.ts with ADMIN allowed for location queries.
</objective>

<execution_context>
@.planning/quick/13-fix-admin-location-query-permission/13-PLAN.md
</execution_context>

<context>
@src/server/signaling/handlers.ts
@src/server/signaling/dispatchHandlers.ts
@src/shared/types.ts
</context>
