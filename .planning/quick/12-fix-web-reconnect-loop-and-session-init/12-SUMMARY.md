---
phase: quick-12
status: complete
date: 2026-02-21
commit: 2ceb75b
---

# Quick-12: Fix Web UI Infinite Reconnect Loop

## Problem
When the WebSocket dropped during `connect()` step 4 (`device.load()` / GET_ROUTER_CAPABILITIES),
`transportClient` was never initialized. On every reconnect, `handleReconnection()` threw
"Cannot recover session: components not initialized", cycling error→reconnect→error forever
at ~1/second. This rapidly exhausted the auth rate limiter (locked out for 372,151ms).

## Root Cause
`handleReconnection()` had a hard throw when `transportClient` was null, with no fallback to
re-run initialization from scratch.

## Fix
Extracted steps 3-9 of `connect()` into `initializeSession()`. `handleReconnection()` now
detects partial initialization (null transportClient/device/microphoneManager), cleans up
partial state, and calls `initializeSession()` for a fresh full init instead of throwing.

## Files Modified
- src/client/connectionManager.ts

## Deployed
Production server: git pull + docker compose build web-ui + docker compose restart
