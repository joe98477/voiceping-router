---
phase: 19-security-hardening-code-quality
plan: 01
subsystem: security
tags: [tls, network-security, error-handling]
dependency-graph:
  requires: []
  provides:
    - tls-enforcement
    - cleartext-blocking
    - tls-error-detection
  affects:
    - signaling-client
    - network-config
tech-stack:
  added:
    - android-network-security-config
  patterns:
    - defense-in-depth
    - tls-only-production
    - graceful-error-handling
key-files:
  created:
    - android/app/src/main/res/xml/network_security_config.xml
    - android/app/src/debug/res/xml/network_security_config.xml
  modified:
    - src/server/config.ts
    - src/server/signaling/websocketServer.ts
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/main/java/com/voiceping/android/domain/model/ConnectionState.kt
    - android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt
    - android/app/src/main/java/com/voiceping/android/presentation/shell/ConnectionBanner.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
decisions:
  - decision: "Server-side WS rejection in production mode for defense-in-depth"
    rationale: "Even behind reverse proxy, reject cleartext connections at application layer"
    alternatives: "Rely solely on reverse proxy or network security config"
  - decision: "TLS errors show user-visible error instead of silent retry"
    rationale: "User needs to know why connection fails (VPN, network, cert issues) rather than infinite retry loop"
    alternatives: "Silent retry with timeout"
  - decision: "No certificate pinning, use system CA store only"
    rationale: "User decision from planning phase - simplifies deployment and rotation"
    alternatives: "Implement certificate pinning"
metrics:
  duration: 352s
  tasks: 2
  files: 9
  commits: 2
  completed_at: 2026-02-15T11:12:22Z
---

# Phase 19 Plan 01: TLS Enforcement & Cleartext Traffic Blocking Summary

**One-liner:** Server-side WS rejection in production mode + Android network security config blocking cleartext traffic with TLS error detection and user-visible error state.

## Overview

Implemented comprehensive TLS enforcement for all signaling connections between Android client and server. Defense-in-depth approach: server rejects cleartext WebSocket connections in production mode, Android network security config blocks all cleartext traffic in release builds, and TLS connection failures show user-visible error banner instead of silently retrying.

## Tasks Completed

### Task 1: Add server-side WS rejection and Android network security config
**Commit:** 9df5147
**Files modified:** 5

**Server-side changes:**
- Added `nodeEnv` field to `config.server` reading `NODE_ENV` environment variable
- Added production WS rejection in `verifyClientAsync` before rate limiting
- Rejects cleartext WebSocket connections when `nodeEnv === 'production'` with 403 status code and "TLS required" message

**Android network security config:**
- Created release variant (`android/app/src/main/res/xml/network_security_config.xml`) blocking ALL cleartext traffic (`cleartextTrafficPermitted="false"`)
- Created debug variant (`android/app/src/debug/res/xml/network_security_config.xml`) allowing cleartext only to localhost/10.0.2.2/127.0.0.1
- Added `android:networkSecurityConfig="@xml/network_security_config"` attribute to `<application>` element in AndroidManifest
- Debug variant overrides release variant automatically in debug builds

**Key decisions:**
- No certificate pinning per user decision (standard system CA store only)
- Defense-in-depth: server rejects even if network config allows (reverse proxy bypass protection)

### Task 2: Add TLS error detection and user-visible error state in Android client
**Commit:** 4052f92
**Files modified:** 4

**TLS error detection:**
- Added `TLS_ERROR` enum state to `ConnectionState`
- Added TLS detection logic in `SignalingClient.onFailure` checking for `SSLException`, `SSLHandshakeException`, `CertificateException`, and message content matching "SSL", "certificate", or "TLS"
- When TLS error detected, set `TLS_ERROR` state and do NOT schedule reconnection
- Updated `manualRetry()` to accept both `FAILED` and `TLS_ERROR` states (user can retry after fixing network/VPN)

**User-visible error handling:**
- Updated `ConnectionBanner` to show error banner for `TLS_ERROR` state immediately (no 5s delay)
- Error message: "Secure connection failed. Check your network or contact support."
- Red background matching `FAILED` state styling
- Retry button enabled for both `FAILED` and `TLS_ERROR` states
- Updated connection status dot in `ChannelListScreen` to show red for `TLS_ERROR`

**Error flow:**
1. TLS handshake fails (e.g., certificate error, cleartext blocked by network security config)
2. `onFailure` handler detects TLS-related exception
3. State set to `TLS_ERROR`, reconnection NOT scheduled
4. Banner appears immediately with error message
5. User can click "Retry" button to manually retry (after fixing VPN/network)

## Deviations from Plan

None - plan executed exactly as written.

## Verification Results

All verification steps passed:

**Server-side:**
- ✓ `nodeEnv` field added to `config.server` (reads `NODE_ENV` environment variable)
- ✓ WS rejection check added in `verifyClientAsync` (checks `info.secure` when `nodeEnv === 'production'`)

**Android network security config:**
- ✓ Release variant exists with `cleartextTrafficPermitted="false"`
- ✓ Debug variant exists with localhost cleartext allowed
- ✓ AndroidManifest references `@xml/network_security_config`

**TLS error handling:**
- ✓ `TLS_ERROR` state exists in `ConnectionState` enum
- ✓ TLS detection logic added in `SignalingClient` (checks `SSLException` and message content)
- ✓ `ConnectionBanner` shows error message for `TLS_ERROR` state
- ✓ `ChannelListScreen` handles `TLS_ERROR` state (red status dot)

**Build verification:**
- ✓ `cd android && ./gradlew compileDebugKotlin` succeeded
- Only deprecation warnings (known and cosmetic per project memory)

## Security Impact

**Production hardening:**
- Release builds enforce TLS-only communication at Android OS level
- Server rejects cleartext WebSocket even if reverse proxy misconfigured
- No silent failures — user sees actionable error message

**Development workflow:**
- Debug builds allow cleartext to localhost/emulator addresses only
- Developers can test against local server without WSS
- External cleartext traffic blocked even in debug builds

**User experience:**
- TLS errors (VPN interference, certificate issues) show clear error message
- User can manually retry after fixing network configuration
- No infinite retry loops consuming battery

## Self-Check

Verifying all claimed artifacts exist:

```bash
# Server files
[ -f "src/server/config.ts" ] && echo "FOUND: src/server/config.ts"
[ -f "src/server/signaling/websocketServer.ts" ] && echo "FOUND: src/server/signaling/websocketServer.ts"

# Android files
[ -f "android/app/src/main/res/xml/network_security_config.xml" ] && echo "FOUND: android/app/src/main/res/xml/network_security_config.xml"
[ -f "android/app/src/debug/res/xml/network_security_config.xml" ] && echo "FOUND: android/app/src/debug/res/xml/network_security_config.xml"
[ -f "android/app/src/main/AndroidManifest.xml" ] && echo "FOUND: android/app/src/main/AndroidManifest.xml"
[ -f "android/app/src/main/java/com/voiceping/android/domain/model/ConnectionState.kt" ] && echo "FOUND: android/app/src/main/java/com/voiceping/android/domain/model/ConnectionState.kt"
[ -f "android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt" ] && echo "FOUND: android/app/src/main/java/com/voiceping/android/data/network/SignalingClient.kt"
[ -f "android/app/src/main/java/com/voiceping/android/presentation/shell/ConnectionBanner.kt" ] && echo "FOUND: android/app/src/main/java/com/voiceping/android/presentation/shell/ConnectionBanner.kt"
[ -f "android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt" ] && echo "FOUND: android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt"

# Commits
git log --oneline --all | grep -q "9df5147" && echo "FOUND: 9df5147"
git log --oneline --all | grep -q "4052f92" && echo "FOUND: 4052f92"
```

## Self-Check: PASSED

All files and commits verified:
- ✓ All 9 key files exist
- ✓ Both commit hashes (9df5147, 4052f92) found in git history
