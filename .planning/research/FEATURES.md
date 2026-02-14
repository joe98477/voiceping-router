# Feature Landscape: v4.0 Production Hardening

**Domain:** Enterprise PTT Communications Platform
**Researched:** 2026-02-15
**Confidence:** MEDIUM-HIGH

## Overview

This feature analysis covers production hardening features for a mature Android PTT app. The app already has working audio (mediasoup WebRTC), multi-channel monitoring, hardware PTT buttons, foreground service, and auto-reconnect. v4.0 adds: adaptive location tracking, audio reliability guarantees, power/bandwidth optimization, permission UX flows, and security audit.

## Table Stakes

Features users expect in enterprise PTT apps. Missing = product feels incomplete.

| Feature | Why Expected | Complexity | Depends On |
|---------|--------------|------------|------------|
| **Location Tracking** | Dispatch needs to see team positions, industry standard for field coordination | Medium | ACCESS_FINE_LOCATION permission, foreground service (exists) |
| **Stationary Detection** | Battery life expectation — GPS can't drain when workers are idle | Medium | Motion sensors (accelerometer), location history buffer |
| **Background Location** | Users expect tracking to work even when screen off (already have foreground service) | Low | ACCESS_BACKGROUND_LOCATION permission, existing foreground service notification |
| **Permission Education UI** | Android 14+ enforcement — must explain why location/mic needed before requesting | Medium | Upfront onboarding flow, re-prompt dialogs |
| **TLS/WSS Encryption** | Enterprise security baseline — production apps cannot use self-signed certs | Low | Replace dev certs, enforce wss:// protocol |
| **Audio Retry Queue** | Mission-critical expectation — every PTT transmission must arrive or user notified | High | Persistent queue, retry logic, failure UI |
| **Network Quality Feedback** | Users need to know if poor connection will impact transmission reliability | Low | Existing network monitor (exists), consumer stats (exists) |
| **Battery Optimization Whitelist** | Android Doze kills background apps — PTT must request exemption | Low | REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission (exists), onboarding prompt |
| **WiFi/Cellular Transition** | Auto-reconnect already exists, but quality degradation warnings expected | Low | Existing NetworkMonitor, bandwidth detection |

## Differentiators

Features that set product apart. Not expected, but valued in enterprise context.

| Feature | Value Proposition | Complexity | Depends On |
|---------|-------------------|------------|------------|
| **Adaptive Location Throttling** | Industry-leading battery life — competitors use fixed intervals, we use motion-aware | High | Geofencing API, accelerometer fusion, ML stationary detection |
| **Geofence Triggers** | Automated workflows (e.g., "arrived at site" auto-joins channel) | High | Geofencing API (100 geofence limit), WorkManager for background triggers |
| **Transmission Acknowledgment** | Visual confirmation PTT was heard by N recipients (vs. just "sent") | Medium | Server-side read receipts, client delivery tracking |
| **Offline Audio Queueing** | Unique for PTT — queue transmissions during network outage, send when reconnected | High | Local audio file storage, Room database queue, WorkManager upload |
| **Bandwidth-Aware Codec** | Auto-switch Opus bitrate based on cellular vs. WiFi (preserve data caps) | Medium | NetworkMonitor cellular detection, Producer reconfigure |
| **Power Profiling Dashboard** | Admin view of per-user battery consumption to identify misconfigured devices | Medium | Battery stats API, server-side aggregation |
| **Security Audit Log** | Compliance requirement for some enterprises (who accessed what channel, when) | Medium | Server-side event logging, encrypted client metadata |
| **Permission Recovery Flow** | If user revokes mic/location later, graceful degradation + re-prompt UI (not crash) | Medium | Runtime permission checks before PTT/location use |
| **Audio Jitter Buffer Tuning** | Reduce latency by 50-100ms for PTT (vs. default WebRTC streaming settings) | Low | MediasoupClient jitter buffer config, NetEQ tuning |

## Anti-Features

Features to explicitly NOT build.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Continuous GPS (no throttling)** | Battery drain horror stories, app store rejection risk (Google Play 2026 policy) | Motion-aware adaptive throttling with stationary detection |
| **Location without permission education** | Android 14+ will reject at review, user trust issue | Upfront contextual education flow before permission prompt |
| **Custom audio codec** | Opus is industry standard for PTT, unnecessary complexity | Stick with Opus, tune bitrate/FEC instead |
| **Audio compression on retry** | Quality loss unacceptable for mission-critical (workers miss commands) | Store original Opus packets, retry as-is |
| **Manual network selection** | Users don't understand cellular vs. WiFi technical details | Auto-detect and auto-optimize, show simple "Good/Poor" indicator |
| **Always-on wake lock** | Google Play 2026 excessive battery policy (2hr+ partial wake lock = warning label) | Use existing foreground service, release wake lock when idle |
| **Mic/location permission on first launch** | Android best practice violation — request in-context when feature used | Defer mic until first PTT press, location until first tracking enable |
| **Background location without foreground service** | Android 10+ restriction — background location requires foreground notification | Already have foreground service, just add location to it |

## Feature Dependencies

### Location Tracking
```
Permission Education Flow
  ↓
ACCESS_FINE_LOCATION (runtime permission)
  ↓
Foreground Service (exists: ChannelMonitoringService)
  ↓
ACCESS_BACKGROUND_LOCATION (runtime permission, requires foreground)
  ↓
FusedLocationProviderClient (Google Play Services)
  ↓
Adaptive Throttling (motion sensors + geofencing)
```

### Audio Reliability
```
Audio Retry Queue
  ↓
Room Database (exists: VoicePingDatabase)
  ↓
WorkManager (for background retry when connection restored)
  ↓
Server Acknowledgment Protocol (new signaling message type)
  ↓
Transmission Acknowledgment UI (delivery confirmation)
```

### Power/Bandwidth Optimization
```
Battery Optimization Whitelist
  ↓
Doze Exemption (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
  ↓
Adaptive Audio Bitrate
  ↓
NetworkMonitor cellular detection (exists)
  ↓
Producer reconfigure (bitrate adjustment)
```

### Permission Flows
```
Onboarding Education Screens
  ↓
shouldShowRequestPermissionRationale() checks
  ↓
Runtime Permission Requests (in-context)
  ↓
Permission Recovery Flow (if revoked later)
```

### Security Audit
```
TLS/WSS Enforcement
  ↓
Certificate Validation (reject self-signed)
  ↓
Server-Side Audit Logging
  ↓
Client Metadata (device ID, IP, channel access)
```

## Implementation Patterns by Domain

### 1. Adaptive Location Tracking

**Expected UX:**
- Location updates every 5-10 seconds when moving (high precision)
- Location updates every 5-10 minutes when stationary (battery saver)
- Geofence responsiveness: 2-5 minutes (optimized for battery, not real-time)
- User sees: "Tracking: Active" / "Tracking: Idle" indicator

**Behavior Patterns:**
- **Motion Detection:** Combine accelerometer + gyroscope to detect movement threshold before requesting GPS fix
- **Stationary Detection:** If location changes <100m over 5 minutes, switch to "idle" mode with 10-minute intervals
- **Geofencing:** Maximum 100 geofences per app (Android limit), use for site boundaries, not individual workers
- **Battery Impact:** Industry target is <5% battery per hour with location tracking enabled (source: [Android battery optimization guide](https://developer.android.com/develop/sensors-and-location/location/battery))

**Implementation:**
- FusedLocationProviderClient with PRIORITY_BALANCED_POWER_ACCURACY (not HIGH_ACCURACY when stationary)
- GeofencingClient for site boundaries with 5-minute responsiveness (setNotificationResponsiveness(300000))
- Accelerometer sensor fusion to delay GPS requests until movement detected
- WorkManager for geofence trigger events (background processing)

**Confidence:** HIGH (official Android documentation, Google Play Services standard API)

### 2. Audio Reliability Hardening

**Expected UX:**
- Visual confirmation "Delivered to 5/8 listeners" after PTT release
- Retry indicator: "Sending..." with spinner if network poor
- Failure notification: "Transmission failed - retry?" with manual retry button
- Transmission history shows: "Sent", "Delivered", "Failed" status per message

**Behavior Patterns:**
- **Transmission Queue:** Store Opus packets in Room database with timestamp, channelId, retry count
- **Acknowledgment Protocol:** Server sends ACK message when audio delivered to N consumers
- **Retry Logic:** Exponential backoff (1s, 2s, 4s, 8s, max 30s), WorkManager for background retry when offline
- **Packet Loss Recovery:** WebRTC NetEQ jitter buffer + Opus in-band FEC (Forward Error Correction) for 5-10% packet loss tolerance
- **Latency Target:** <300ms PTT latency (industry standard, source: [PeakPTT specifications](https://www.peakptt.com/))
- **Availability Target:** 99.9% (enterprise SLA, source: [Viasat PTT Select](https://www.viasat.com/enterprise/services/ptt-select/))

**Implementation:**
- Room entity: `TransmissionQueueEntry(id, audioFilePath, channelId, timestamp, retryCount, status)`
- WorkManager PeriodicWorkRequest to check queue every 15 minutes
- Server signaling: `AUDIO_DELIVERED` message type with recipient count
- Opus FEC enabled via MediasoupClient producerOptions: `enableOpusFec: true`
- NetEQ jitter buffer: Default WebRTC adaptive buffer (15-120ms), tune to PTT profile (lower bound 10ms)

**Confidence:** MEDIUM-HIGH (WebRTC standards documented, PTT latency targets from industry sources, implementation requires server-side changes)

### 3. Power/Bandwidth Optimization

**Expected UX:**
- Settings option: "Data Saver Mode" (lower audio quality on cellular)
- Automatic quality adjustment with toast: "Switched to WiFi - audio quality improved"
- Battery stats: "VoicePing used 8% battery in last 24 hours"
- No excessive battery drain warnings from Android OS

**Behavior Patterns:**
- **Doze Mode:** App must request battery optimization whitelist (isIgnoringBatteryOptimizations) during onboarding
- **Foreground Service Exemption:** Already have ChannelMonitoringService with mediaPlayback type, exempt from Doze restrictions
- **Adaptive Bitrate:** Opus codec 16kbps (cellular) vs. 32kbps (WiFi) for voice quality vs. data usage balance
- **Wake Lock Management:** Release partial wake locks after 2 cumulative hours (Google Play 2026 policy enforcement)
- **Network Detection:** ConnectivityManager.getActiveNetwork() + NetworkCapabilities.TRANSPORT_WIFI vs. TRANSPORT_CELLULAR
- **Background Restrictions:** WorkManager with network constraints for retry operations (Constraints.Builder().setRequiredNetworkType(CONNECTED))

**Implementation:**
- Check `PowerManager.isIgnoringBatteryOptimizations()` in onboarding, request via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- NetworkMonitor already exists, extend with `isCellular()` helper
- Producer reconfigure on network change: `producer.replaceTrack(newAudioTrack)` with different Opus bitrate
- WorkManager constraints: `.setRequiredNetworkType(NetworkType.UNMETERED)` for non-urgent uploads

**Confidence:** HIGH (official Android documentation, Google Play policy documented, WebRTC Producer API known)

### 4. Permission Flows

**Expected UX:**
- **Upfront (before permission request):** Educational screen: "VoicePing needs microphone access to transmit voice messages to your team"
- **In-Context (when feature used):** PTT button pressed → if no mic permission → show rationale dialog → request permission
- **Re-Prompt (if denied once):** Show educational dialog explaining why permission needed, with "Open Settings" button
- **Graceful Degradation:** If mic revoked, PTT button shows "Microphone access required" instead of crashing
- **Location Permission Flow:** Two-step (fine location first, then background location after user enables tracking)

**Behavior Patterns:**
- **shouldShowRequestPermissionRationale():** Returns true if user denied once (show educational UI before re-requesting)
- **Don't Ask Again:** If user selects "Don't ask again", rationale returns false → must guide to Settings
- **Contextual Timing:** Request mic only when PTT button first pressed, location only when "Enable Tracking" toggled
- **Permission Groups:** RECORD_AUDIO (dangerous, runtime), ACCESS_FINE_LOCATION (dangerous, runtime), ACCESS_BACKGROUND_LOCATION (dangerous, runtime, requires FINE first)
- **Android 14+ Requirements:** Must show clear explanation before requesting sensitive permissions (Play Store review requirement)

**Implementation:**
- Onboarding screen: `PermissionEducationScreen` with "Why we need this" text + "Continue" button
- Before permission request: Check `shouldShowRequestPermissionRationale()`, show dialog if true
- Permission request: `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`
- Graceful degradation: Before PTT use, check `ContextCompat.checkSelfPermission()`, show error state if denied
- Settings redirect: `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)` with app package URI

**Confidence:** HIGH (official Android permission guidelines, NN/g UX research documented)

### 5. Security Audit

**Expected UX:**
- Admin dashboard: "Security Events" log showing: "User 123 joined Channel ABC at 2026-02-15 10:30 UTC from IP 1.2.3.4"
- TLS enforcement: App refuses to connect to non-wss:// servers (no fallback to ws://)
- Certificate validation: Rejects self-signed certificates with error: "Invalid server certificate"
- End-to-End Encryption indicator: "Secure connection" icon in notification (already have TLS, consider E2EE for MCPTT compliance)

**Behavior Patterns:**
- **TLS 1.3:** WebRTC DTLS 1.3 for media, WSS (TLS 1.3) for signaling (industry migration from 1.2 in 2025)
- **Certificate Pinning:** Optional for high-security deployments (pin server certificate to prevent MITM)
- **Signaling Security:** HTTPS/WSS for all signaling, protect against CSRF/XSS on control-plane API
- **TURN Credentials:** Time-limited TURN credentials (not static passwords) for WebRTC NAT traversal
- **Authentication:** JWT tokens already implemented, ensure expiry enforcement and refresh token rotation
- **MCPTT End-to-End Encryption:** 3GPP standard requires E2EE for mission-critical, optional for commercial PTT
- **Audit Log Requirements:** User ID, device ID, channel ID, timestamp, IP address, action (join/leave/transmit)

**Implementation:**
- SignalingClient: Enforce `wss://` schema, reject `ws://` connections
- OkHttp TLS configuration: `.sslSocketFactory(tlsSocketFactory, trustManager)` with system trust store (reject self-signed)
- Server-side audit log: PostgreSQL table `audit_events(user_id, device_id, channel_id, action, timestamp, ip_address)`
- Optional E2EE: Implement 3GPP MCPTT KMS (Key Management Service) for end-to-end encryption (HIGH complexity, deferred for v5.0)
- TURN credential rotation: Server generates time-limited credentials with TTL (already supported by TURN protocol)

**Confidence:** MEDIUM-HIGH (WebRTC security architecture documented, MCPTT 3GPP standards available, E2EE is complex and may require external audit)

## MVP Recommendation

Prioritize features by impact and dependency:

### Phase 1: Foundation (Low-Hanging Fruit)
1. **TLS/WSS Enforcement** — Security baseline, low complexity
2. **Battery Optimization Whitelist** — User education during onboarding
3. **Permission Education UI** — Required for Android 14+ compliance
4. **Network Quality Feedback Enhancement** — Extend existing NetworkMonitor

### Phase 2: Location (Core Differentiator)
5. **Basic Location Tracking** — FusedLocationProviderClient with fixed intervals
6. **Permission Flows (Location)** — Two-step fine → background location
7. **Stationary Detection** — Adaptive throttling based on movement
8. **Geofencing (Optional)** — If enterprise clients need site-based workflows

### Phase 3: Audio Reliability (Mission-Critical)
9. **Audio Retry Queue** — Room database + WorkManager retry
10. **Transmission Acknowledgment** — Server protocol + delivery UI
11. **Bandwidth-Aware Codec** — Opus bitrate switching on cellular
12. **Jitter Buffer Tuning** — Reduce PTT latency from 300ms → 200ms

### Phase 4: Hardening (Production-Ready)
13. **Security Audit Log** — Server-side event logging
14. **Permission Recovery Flow** — Graceful degradation if permissions revoked
15. **Power Profiling (Admin)** — Battery stats dashboard for fleet management

### Defer to v5.0:
- **End-to-End Encryption (E2EE)** — MCPTT KMS implementation, requires security audit
- **Offline Audio Queueing** — Complex storage/sync, lower priority than retry queue
- **Geofence Automation** — Workflow engine for "arrived at site" triggers

## Feature Complexity Assessment

| Feature | Complexity | Reason | Estimated Effort |
|---------|------------|--------|------------------|
| TLS/WSS Enforcement | Low | Configuration change, certificate replacement | 1 plan |
| Battery Whitelist UI | Low | Single permission request with rationale | 1 plan |
| Permission Education | Medium | Multi-screen onboarding flow, state management | 2 plans |
| Basic Location Tracking | Medium | FusedLocationProviderClient integration, foreground service update | 2 plans |
| Stationary Detection | Medium | Sensor fusion, motion detection algorithm | 2 plans |
| Geofencing | High | GeofencingClient, WorkManager triggers, 100 geofence limit | 3 plans |
| Audio Retry Queue | High | Room schema, WorkManager integration, retry logic | 3 plans |
| Transmission ACK | Medium | Server protocol change, client UI update | 2 plans |
| Bandwidth-Aware Codec | Medium | NetworkMonitor integration, Producer reconfigure | 2 plans |
| Jitter Buffer Tuning | Low | MediasoupClient configuration | 1 plan |
| Security Audit Log | Medium | Server-side implementation, client metadata | 2 plans |
| Permission Recovery | Medium | Runtime checks, graceful degradation UI | 2 plans |
| Power Profiling | Medium | Battery stats API, server aggregation | 2 plans |
| End-to-End Encryption | Very High | MCPTT KMS, key distribution, security audit | 6+ plans |

## Sources and Confidence Levels

### HIGH Confidence Sources
- [Android Official: Location Optimization](https://developer.android.com/develop/sensors-and-location/location/battery)
- [Android Official: Geofencing](https://developer.android.com/develop/sensors-and-location/location/geofencing)
- [Android Official: Doze Optimization](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android Official: Runtime Permissions](https://developer.android.com/training/permissions/requesting)
- [Android Official: Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [WebRTC Security Architecture](https://rtcweb-wg.github.io/security-arch/)
- [NN/g Permission UX Research](https://www.nngroup.com/articles/permission-requests/)

### MEDIUM Confidence Sources
- [PeakPTT Latency Specifications](https://www.peakptt.com/) — Industry PTT latency targets
- [Viasat PTT Select Availability](https://www.viasat.com/enterprise/services/ptt-select/) — 99.9% SLA
- [WebRTC NetEQ Jitter Buffer](https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/) — Technical deep dive
- [MCPTT Security Overview](https://www.npstc.org/download.jsp?tableId=37&column=217&id=4308&file=NPSTC_MCPTT_Console_Report_200703.pdf) — Mission-critical requirements
- [Android Geofencing 2026 Guide](https://smartupworld.com/android-geofencing/) — Best practices

### LOW Confidence (Needs Validation)
- Google Play 2026 battery policy (2hr wake lock threshold) — Policy documentation incomplete, verify with official source
- MCPTT E2EE KMS implementation complexity — No open-source reference implementations found, may require consulting
- Offline audio queueing patterns — Limited PTT app documentation on this specific feature

## Gap Analysis

### Areas with Incomplete Research
1. **MCPTT End-to-End Encryption:** 3GPP standards are public, but implementation guides scarce. May require commercial MCPTT SDK or security consulting.
2. **Geofence Workflow Automation:** No industry-standard patterns found for "auto-join channel when arriving at site". Custom implementation needed.
3. **Power Profiling Dashboard:** Battery stats API (BatteryStatsManager) is restricted in recent Android versions. May need alternative approach via WorkManager execution logs.

### Topics Needing Phase-Specific Research
- **Phase-Specific (Location):** Motion sensor fusion algorithms for stationary detection (accelerometer + gyroscope thresholds)
- **Phase-Specific (Audio):** Server-side acknowledgment protocol design (how many ACKs = "delivered"? All consumers? Majority?)
- **Phase-Specific (Security):** Certificate pinning trade-offs for enterprise deployments (operational complexity vs. security benefit)

## Production Hardening Checklist

Before v4.0 ships, validate:

- [ ] TLS certificate from trusted CA (not self-signed)
- [ ] Battery optimization whitelist requested during onboarding
- [ ] Permission education screens comply with Play Store review guidelines
- [ ] Location tracking <5% battery drain per hour (test on physical device)
- [ ] Audio retry queue handles 24-hour offline scenario
- [ ] Transmission acknowledgment tested with 100+ simultaneous consumers
- [ ] Bandwidth-aware codec switches seamlessly between WiFi/cellular
- [ ] Security audit log captures all channel access events
- [ ] Permission recovery flow tested (revoke mic mid-PTT, location mid-tracking)
- [ ] No Google Play excessive battery warnings (wake lock <2hr cumulative)

---

**Research Complete:** 2026-02-15
**Overall Confidence:** MEDIUM-HIGH
**Ready for Roadmap Planning:** YES (with noted gaps for phase-specific research)
