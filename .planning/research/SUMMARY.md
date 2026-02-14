# Project Research Summary

**Project:** VoicePing Router v4.0 - Production Hardening
**Domain:** Enterprise PTT (Push-to-Talk) Communications Platform - Android Client
**Researched:** 2026-02-15
**Confidence:** MEDIUM-HIGH

## Executive Summary

VoicePing v4.0 transforms a working PTT prototype into a production-ready enterprise solution by adding five critical capabilities: adaptive location tracking with motion-aware throttling, audio reliability guarantees, power/bandwidth optimization, security hardening, and polished permission flows. The research reveals a mature Android ecosystem where most needed capabilities exist in Google Play Services and established libraries — the challenge is proper configuration and lifecycle management, not finding new dependencies.

The recommended approach leverages existing architectural patterns (singleton-based Hilt DI, data/presentation separation) by adding new @Singleton managers (LocationManager, PermissionManager) that slot cleanly alongside existing components. Location tracking flows through the established SignalingClient → Server → Redis pattern used for audio. Audio reliability improvements target WebRTC jitter buffer tuning and state machine hardening within the existing MediasoupClient, not library replacements. Power optimization focuses on adaptive strategies (location throttling, wake lock scoping) rather than battery exemptions, which create more problems than they solve.

The primary risks are integration pitfalls, not technical unknowns. Location tracking on Android 14+ requires explicit foreground service type declarations that crash if missing. WebRTC audio device changes during transmission cause silent failures unless Producer lifecycle coordinates with AudioManager. Permission denial loops create hostile UX if rationale tracking isn't implemented from day one. Each pitfall has a known prevention strategy — the key is addressing them during initial implementation rather than post-launch firefighting.

## Key Findings

### Recommended Stack

The v4.0 stack additions are minimal and strategic. Google Play Services provides battle-tested location APIs (FusedLocationProviderClient for GPS fusion, ActivityRecognitionClient for motion detection) that handle the complexity of adaptive tracking. WorkManager enables power-efficient background location batching with 15-minute intervals. OkHttp 5.3.0 upgrade brings TLS 1.3 and improved Android optimizations. Accompanist Permissions (experimental but stable) provides Compose-native permission flows.

**Core technologies (NEW for v4.0):**
- com.google.android.gms:play-services-location 21.3.0 — FusedLocationProviderClient + ActivityRecognitionClient for motion-aware location tracking, industry standard with adaptive throttling built-in
- androidx.work:work-runtime-ktx 2.10.1 — Periodic location batching (15min minimum), survives app restarts
- com.google.accompanist:accompanist-permissions 0.37.0 — Compose permission UI with rationale flows
- OkHttp 5.3.0 (upgrade from 4.12.0) — TLS 1.3, DNS over HTTPS, platform-specific optimizations

**Critical finding:** Do NOT add new audio libraries. Work within existing libmediasoup-android 0.21.0 by tuning WebRTC jitter buffers and enabling Opus FEC (Forward Error Correction). Intermittent silence is typically caused by network issues (TURN server needed for mobile), AEC ducking (audio processing settings), or buffer underruns (increase AudioTrack buffer size), not library deficiencies.

### Expected Features

Users expect location tracking, stationary detection, and background monitoring as table stakes for enterprise PTT. Missing these makes the product feel incomplete compared to competitors. Permission education screens are mandatory for Android 14+ Play Store review compliance. TLS/WSS encryption is non-negotiable for enterprise deployments.

**Must have (table stakes):**
- Location tracking with stationary detection — Battery life expectation, GPS can't drain when workers idle
- Permission education UI before requests — Android 14+ enforcement, explain why location/mic needed
- TLS/WSS encryption — Enterprise security baseline, production apps cannot use self-signed certs
- Audio retry queue with acknowledgment — Mission-critical expectation, every PTT transmission must arrive or user notified
- Network quality feedback — Users need to know if poor connection will impact reliability
- Battery optimization whitelist prompt — Android Doze kills background apps, PTT must request exemption

**Should have (competitive differentiators):**
- Adaptive location throttling (motion-aware) — Industry-leading battery life, competitors use fixed intervals
- Transmission acknowledgment with recipient count — Visual confirmation PTT was heard by N recipients (vs. just "sent")
- Bandwidth-aware codec switching — Auto-switch Opus bitrate based on cellular vs. WiFi
- Permission recovery flow — If user revokes mic/location later, graceful degradation + re-prompt UI (not crash)
- Security audit log — Compliance requirement for some enterprises (who accessed what channel, when)

**Defer (v2+):**
- Geofence automation workflows — "Arrived at site" auto-joins channel, high complexity
- End-to-End Encryption (E2EE) — 3GPP MCPTT KMS implementation, requires security audit
- Offline audio queueing — Queue transmissions during network outage, complex storage/sync

### Architecture Approach

v4.0 maintains the v3.0 clean architecture by adding new @Singleton components in the data layer without introducing circular dependencies. LocationManager becomes a peer to PttManager/AudioRouter, handling FusedLocationProviderClient lifecycle and adaptive throttling. Location data flows through the existing SignalingClient → Server → Redis pub/sub pattern used for speaker changes. Audio reliability improvements target the existing MediasoupClient/PttManager pipeline with buffer tuning and state machine hardening, not new components.

**Major components (NEW for v4.0):**
1. **LocationManager (@Singleton)** — FusedLocationProviderClient lifecycle, adaptive mode switching (PRECISE/GENERAL/MOTION_AWARE), motion detection via ActivityRecognitionClient, batching for power efficiency. Delegates permission checks to PermissionManager, server transmission to SignalingClient.

2. **PermissionManager (@Singleton)** — Centralized permission state checking (granted/denied/never-ask-again), request coordination with Activity via callback pattern, rationale display logic, settings redirect. Prevents circular dependencies by using callback delegation instead of direct Activity references.

3. **MediasoupClient hardening** — WebRTC jitter buffer tuning (increase to 80ms for mobile variance), Opus FEC enablement, transport health monitoring, Producer retry logic on failures. No new libraries, configuration changes only.

4. **ChannelMonitoringService extension** — Add location tracking to existing foreground service (requires android:foregroundServiceType="location|microphone" declaration), wake lock scoping (release after 30s of silence), notification enhancement (show current channel status).

5. **Server-side minimal changes** — New signaling type LOCATION_UPDATE (send-only, no response), Redis pub/sub broadcast to dispatch console, optional database persistence for location history.

**Critical pattern:** Callback-based communication for cross-layer coordination without circular dependencies. Example: PttManager → ChannelRepository → TonePlayer callbacks preserve unidirectional dependency flow while enabling complex interactions.

### Critical Pitfalls

Research identified 10 critical pitfalls with proven prevention strategies. The top 5 must be addressed during initial implementation to avoid production outages and emergency releases.

1. **Android 14+ Location Permission Crash** — SecurityException when starting foreground service without explicit type declaration. Must add android:foregroundServiceType="location|microphone" to manifest AND FOREGROUND_SERVICE_LOCATION permission. Test on Android 14+ specifically, earlier versions won't catch this.

2. **GPS Battery Drain Without Adaptive Strategy** — Battery jumps from 5%/hour to 15-25%/hour with continuous GPS lock. Use PRIORITY_BALANCED_POWER_ACCURACY (not HIGH_ACCURACY) by default, switch to HIGH only during active PTT, implement stationary detection to reduce update frequency. Monitor with Battery Historian during development.

3. **WebRTC Audio Device Change Race Condition** — Audio cuts out when Bluetooth connects/disconnects during PTT transmission. Producer becomes detached from audio source. Must register AudioDeviceCallback, pause/resume Producer on device changes, implement "audio heartbeat" monitoring to detect silent transmissions. Test by toggling Bluetooth during active PTT hold.

4. **Wake Lock + Doze Mode Exemption Breaking Battery** — Requesting REQUEST_IGNORE_BATTERY_OPTIMIZATIONS disables ALL battery optimization, causing 2-3x drain even when idle. Audio wake locks are ALREADY exempt from Doze. Never request battery optimization exemption for PTT apps, design for Doze windows instead.

5. **Certificate Pinning Breaking Production Updates** — Pinning leaf certificate creates ticking time bomb (90-day expiry). When cert rotates, all installations lose connectivity, requiring emergency release. Modern 2026 recommendation: don't pin at all, use Certificate Transparency enforcement instead. If pinning mandatory, pin to CA root (not leaf) with backup pins.

**Additional critical pitfalls:**
- Permission denial loop without rationale tracking (infinite prompts, hostile UX)
- Network security config allowing cleartext in production (security vulnerability)
- WebSocket reconnection orphaning Producer during network switch (silent audio failures)
- Foreground notification importance too low (service killed when notification dismissed)
- AudioRecord not restarted after app resume (mic captures silence after backgrounding)

## Implications for Roadmap

Based on combined research, v4.0 should proceed in five phases with clear dependency ordering. Location and permission management are foundational, audio reliability and power optimization build on that base, security hardening runs in parallel.

### Phase 16: Permission Management Foundation
**Rationale:** No dependencies, enables all subsequent permission-requiring features. Must come first because location, mic, and notifications all need proper permission flows. Android 14+ requires permission education before requests, not after-the-fact fixes.

**Delivers:** PermissionManager @Singleton with Activity callback delegation, first-launch permission flow with rationale screens, denial tracking to prevent infinite loops, graceful degradation when permissions denied.

**Addresses (FEATURES.md):** Permission education UI (table stakes), permission recovery flow (differentiator)

**Avoids (PITFALLS.md):** Permission denial loop (#6), helps prevent Android 14+ location crash (#1) by ensuring permissions granted before service starts

**Plans:** 2 plans (PermissionManager + MainActivity integration, rationale dialogs + settings redirect)

**Research flag:** Skip phase research — standard Android permission patterns, well-documented

---

### Phase 17: Location Tracking Infrastructure
**Rationale:** Depends on Phase 16 for permission handling. Table stakes feature for enterprise PTT, differentiates VoicePing with adaptive throttling. Must implement battery-efficient strategy from day 1, not optimize later.

**Delivers:** LocationManager @Singleton with FusedLocationProviderClient integration, adaptive tracking modes (PRECISE/GENERAL/MOTION_AWARE), motion detection via ActivityRecognitionClient, foreground service type declaration for Android 14+, server signaling LOCATION_UPDATE, dispatch web UI map overlay.

**Addresses (FEATURES.md):** Location tracking (table stakes), stationary detection (table stakes), adaptive location throttling (differentiator)

**Avoids (PITFALLS.md):** Android 14+ location crash (#1), GPS battery drain (#2)

**Uses (STACK.md):** play-services-location 21.3.0, WorkManager 2.10.1

**Implements (ARCHITECTURE.md):** LocationManager @Singleton in data layer, SignalingClient extension for LOCATION_UPDATE type, ChannelMonitoringService location integration

**Plans:** 2 plans (LocationManager + adaptive modes, server signaling + dispatch UI)

**Research flag:** Skip phase research — Google Play Services APIs well-documented, standard location patterns

---

### Phase 18: Audio Reliability Hardening
**Rationale:** Independent of location (can run in parallel with Phase 17). Addresses known "intermittent silence" bug from v3.0. Mission-critical for PTT, audio must be reliable before production.

**Delivers:** WebRTC jitter buffer tuning (80ms target for mobile), Opus FEC enablement, Producer retry logic (3 attempts with exponential backoff), audio device change handling (Bluetooth connect/disconnect during PTT), transport health monitoring, AudioRecord lifecycle fixes (restart after app resume), WebSocket reconnection coordination with MediasoupClient.

**Addresses (FEATURES.md):** Audio retry queue (table stakes), network quality feedback enhancement (table stakes)

**Avoids (PITFALLS.md):** Audio device change race condition (#3), WebSocket reconnection orphaning Producer (#8), AudioRecord not restarted (#10)

**Uses (STACK.md):** Existing libmediasoup-android 0.21.0 (no new libraries), WebRTC jitter buffer configuration, MediasoupClient state machine hardening

**Implements (ARCHITECTURE.md):** MediasoupClient hardening, PttManager retry logic, ConnectionStateObserver pattern

**Plans:** 2 plans (jitter buffer + FEC + device change handling, retry logic + health monitoring + reconnection coordination)

**Research flag:** Consider phase research for WebRTC jitter buffer API specifics — crow-misia library may not expose all controls, might need server-side mediasoup configuration instead

---

### Phase 19: Power Optimization
**Rationale:** Depends on Phase 17 (location batching) and Phase 18 (audio fixes) to measure baseline battery consumption. Must validate <5%/hour total target. Critical for 24/7 pocket radio operation.

**Delivers:** Location batching with WorkManager (15-minute intervals for GENERAL mode), network quality polling reduction (15s idle vs. 5s active), wake lock scoping (release after 30s silence), battery optimization whitelist prompt (during onboarding), battery profiling validation on physical devices.

**Addresses (FEATURES.md):** Battery optimization whitelist (table stakes), power profiling dashboard (differentiator, deferred to server-side implementation)

**Avoids (PITFALLS.md):** Wake lock + Doze exemption breaking battery (#4), ensures GPS battery drain optimization from Phase 17 (#2)

**Uses (STACK.md):** WorkManager for location batching, existing foreground service wake lock management

**Implements (ARCHITECTURE.md):** LocationBatchManager, ChannelMonitoringService wake lock scoping, network quality polling optimization

**Plans:** 2 plans (location batching + polling reduction, wake lock scoping + battery validation)

**Research flag:** Skip phase research — WorkManager patterns well-documented, battery optimization is configuration not new APIs

---

### Phase 20: Security Audit and Hardening
**Rationale:** Independent of other phases (can run in parallel with 18-19). Required for enterprise deployments. Must be complete before production launch, not added post-launch.

**Delivers:** TLS/WSS enforcement (reject ws:// connections), network security config (build-variant-specific, no cleartext in production), OkHttp 5.3.0 upgrade (TLS 1.3 support), Certificate Transparency enforcement (Android 16+ native, <16 via appmattus interceptor), secure token storage (EncryptedSharedPreferences), server-side audit logging (user/channel/action/timestamp/IP).

**Addresses (FEATURES.md):** TLS/WSS encryption (table stakes), security audit log (differentiator)

**Avoids (PITFALLS.md):** Certificate pinning breaking updates (#5), cleartext traffic in production (#7)

**Uses (STACK.md):** OkHttp 5.3.0, network security config XML, appmattus/certificatetransparency (optional for Android <16)

**Implements (ARCHITECTURE.md):** SignalingClient WSS enforcement, SecureTokenManager, server-side audit log table

**Plans:** 2 plans (TLS enforcement + secure storage, Certificate Transparency + audit logging)

**Research flag:** Skip phase research for TLS/WSS (standard patterns). Consider phase research if E2EE (End-to-End Encryption) is added — 3GPP MCPTT KMS is complex, requires security audit consultation.

---

### Phase Ordering Rationale

**Why this order:**
1. **Phase 16 first:** Permission management has no dependencies and unblocks everything else (location, mic, notifications). Must establish permission flows before adding permission-requiring features.

2. **Phase 17 location:** Depends on Phase 16 for permissions. Table stakes feature that users expect, differentiates with adaptive throttling. Battery optimization from day 1 prevents user complaints.

3. **Phase 18 audio in parallel:** Independent of location, can run simultaneously with Phase 17. Addresses known v3.0 bug, mission-critical for PTT reliability. Must be production-ready before launch.

4. **Phase 19 power after 17+18:** Needs baseline from location + audio to measure battery consumption accurately. Validates <5%/hour target with all features active.

5. **Phase 20 security in parallel:** Independent of other phases, can run with 18-19. Enterprise requirement, must be complete for production but doesn't block feature development.

**Grouping logic:**
- Foundation (16): Enables all permission-requiring features
- Core features (17-18): Location and audio reliability, both user-facing
- Optimization (19): Power management after features complete
- Hardening (20): Security audit before production

**Pitfall avoidance:**
- Phase 16 prevents permission denial loops before they occur
- Phase 17 prevents location battery drain by implementing adaptive strategy from start
- Phase 18 prevents audio reliability issues from reaching production
- Phase 19 prevents battery optimization exemption mistakes
- Phase 20 prevents security vulnerabilities in production builds

### Research Flags

**Phases likely needing deeper research during planning:**

- **Phase 18 (Audio Reliability):** WebRTC jitter buffer configuration may require server-side mediasoup changes if crow-misia libmediasoup-android 0.21.0 doesn't expose jitter buffer controls. Research WebRTC NetEQ API surface and mediasoup Opus FEC configuration patterns.

- **Phase 20 (Security) IF E2EE added:** 3GPP MCPTT End-to-End Encryption is complex, requires Key Management Service (KMS) implementation. Research shows sparse open-source implementations, may require commercial MCPTT SDK or security consulting. Recommend deferring to v5.0 unless enterprise client mandates.

**Phases with standard patterns (skip research-phase):**

- **Phase 16 (Permissions):** Well-documented Android permission patterns, Accompanist library documentation comprehensive
- **Phase 17 (Location):** Google Play Services FusedLocationProvider is mature, official Android documentation extensive
- **Phase 19 (Power):** WorkManager and Doze mode optimization patterns well-established, Android Vitals metrics documented

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM | Official APIs verified (FusedLocationProvider, WorkManager, OkHttp), but implementation patterns need testing. WebRTC jitter buffer control uncertain (crow-misia library API surface unknown). |
| Features | HIGH | Feature expectations grounded in industry research (PeakPTT specs, Viasat SLAs) and Android official guidelines. Table stakes vs. differentiators clearly delineated. |
| Architecture | MEDIUM-HIGH | Integration points fit cleanly into existing v3.0 architecture (singleton pattern, Hilt DI). LocationManager/PermissionManager follow established patterns. Audio reliability fixes target known components. |
| Pitfalls | HIGH | All 10 critical pitfalls sourced from official Android documentation, verified bug reports, and production postmortems. Prevention strategies proven. |

**Overall confidence:** MEDIUM-HIGH

Confidence is high for features (user expectations) and pitfalls (known failure modes), medium-high for architecture (fits existing patterns but needs validation), medium for stack (official APIs but implementation details need testing). The research provides a solid foundation for roadmap planning, with clear identification of areas needing phase-specific research (WebRTC jitter buffer, potential E2EE).

### Gaps to Address

**Areas where research was inconclusive or needs validation during implementation:**

1. **WebRTC Jitter Buffer Control:** Research shows jitter buffer tuning is critical for mobile PTT reliability, but crow-misia libmediasoup-android 0.21.0 API documentation doesn't clearly expose jitter buffer configuration. May require server-side mediasoup configuration instead of client-side. Validate during Phase 18 planning.

2. **Stationary Detection Thresholds:** Motion detection via ActivityRecognitionClient provides STILL/WALKING/IN_VEHICLE states, but optimal thresholds for location throttling (e.g., "STILL for >30min = reduce frequency") are application-specific. Industry patterns suggest starting points, but need field validation with actual battery profiling.

3. **Transmission Acknowledgment Protocol:** Research identifies need for server ACK messages confirming audio delivery, but doesn't specify optimal acknowledgment threshold. Should "delivered" mean all consumers received audio, majority (>50%), or at least one? Requires product decision, affects server protocol design.

4. **Geofence Workflow Automation (deferred):** No industry-standard patterns found for "auto-join channel when arriving at site" workflows. If added in v5.0, will require custom implementation research.

5. **MCPTT End-to-End Encryption (deferred):** 3GPP standards are public, but implementation guides scarce. May require commercial MCPTT SDK or security consulting if enterprise clients mandate E2EE.

**How to handle during planning:**

- **Jitter buffer:** Plan Phase 18-01 to include API research on crow-misia library, fallback to server-side mediasoup configuration if needed
- **Stationary detection:** Use industry starting points (30min STILL threshold), validate with Battery Historian during Phase 17 testing
- **Transmission ACK:** Make product decision during Phase 18 requirements (recommend "majority of active consumers" threshold)
- **Geofencing:** Defer to v5.0, flag for future research if enterprise clients request
- **E2EE:** Defer to v5.0, note security audit requirement if added

## Sources

### Primary (HIGH confidence)

**Stack & Technology:**
- [FusedLocationProviderClient API | Google Developers](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)
- [Activity Recognition API | Google Developers](https://developers.google.com/location-context/activity-recognition)
- [WorkManager Releases | AndroidX](https://developer.android.com/jetpack/androidx/releases/work)
- [OkHttp Changelog | Square](https://square.github.io/okhttp/changelogs/changelog/)
- [Network Security Config | Android Developers](https://developer.android.com/privacy-and-security/security-config)
- [Accompanist Permissions | Google](https://google.github.io/accompanist/permissions/)

**Features & Best Practices:**
- [About background location and battery life | Android Developers](https://developer.android.com/develop/sensors-and-location/location/battery)
- [Request Runtime Permissions | Android Developers](https://developer.android.com/training/permissions/requesting)
- [Optimize for Doze and App Standby | Android Developers](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Foreground Service Types Required | Android 14](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [WebRTC Security Architecture | IETF](https://rtcweb-wg.github.io/security-arch/)

**Pitfalls & Production Issues:**
- [Restrictions on starting foreground services from background | Android Developers](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Excessive partial wake locks | Android Vitals](https://developer.android.com/topic/performance/vitals/excessive-wakelock)
- [Security with network protocols | Android Developers](https://developer.android.com/privacy-and-security/security-ssl)

### Secondary (MEDIUM confidence)

**Architecture & Implementation Patterns:**
- [How WebRTC's NetEQ Jitter Buffer Provides Smooth Audio | WebRTC Hacks](https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/)
- [mediasoup Opus FEC Issue #234 | GitHub](https://github.com/versatica/mediasoup/issues/234)
- [Crystal Clear Certificates - Certificate Transparency | Android GDE](https://www.spght.dev/articles/21-04-2025/crystal-clear-certs)
- [OkHttp 5.0 Migration Guide | Medium](https://medium.com/@hiren6997/okhttp-5-0-what-changed-and-how-to-upgrade-without-breaking-everything-1e2dfb255848)
- [Advanced Location Tracking Battery Efficiency | OneClick IT](https://www.oneclickitsolution.com/centerofexcellence/android/advanced-location-tracking-with-battery-efficiency-in-android-app)

**Industry Benchmarks:**
- [PeakPTT Latency Specifications](https://www.peakptt.com/) — <300ms PTT latency target
- [Viasat PTT Select Availability](https://www.viasat.com/enterprise/services/ptt-select/) — 99.9% SLA
- [NN/g Permission UX Research](https://www.nngroup.com/articles/permission-requests/) — Permission request patterns

**Known Issues & Workarounds:**
- [Flutter WebRTC Android 15 Audio Issue #1759 | GitHub](https://github.com/flutter-webrtc/flutter-webrtc/issues/1759) — Audio capture stops periodically
- [Audio device handling is poor with WebRTC | Mozilla Fenix #16653](https://github.com/mozilla-mobile/fenix/issues/16653)
- [Intermittent WebRTC audio fade out | discuss-webrtc](https://groups.google.com/g/discuss-webrtc/c/fgJEv_Ziy_g)

### Tertiary (LOW confidence, needs validation)

- [Why WebRTC Calls Fail on Mobile Data | softpagecms](https://www.softpagecms.com/2026/01/06/why-webrtc-calls-fail-mobile-data-fix-2026/) — TURN server required for mobile reliability (validate with production testing)
- Google Play 2026 battery policy (2hr wake lock threshold) — Policy documentation incomplete, verify with official source during Phase 19

---

**Research completed:** 2026-02-15

**Ready for roadmap:** YES

All four research files (STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md) provide comprehensive foundation for roadmap planning. Phase structure suggestions are grounded in dependency analysis and pitfall avoidance. Research flags clearly identify areas needing deeper investigation during plan execution (WebRTC jitter buffer API, potential E2EE complexity).
