# Stack Research - v4.0 Production Hardening

**Domain:** Enterprise PTT Android App - Production Features
**Researched:** 2026-02-15
**Confidence:** MEDIUM

## Executive Summary

This research covers **only NEW stack additions** for v4.0 milestone features:
1. Adaptive location tracking with motion-aware throttling
2. Audio reliability hardening (intermittent silence fixes)
3. Power/bandwidth optimization
4. Security audit tooling
5. Runtime permissions best practices

**Key Finding:** Most needed capabilities exist in Google Play Services and existing OkHttp/WorkManager libraries. Minimal new dependencies required. Focus on configuration and implementation patterns rather than new libraries.

## Recommended Stack Additions

### Location & Motion Detection

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| com.google.android.gms:play-services-location | 21.3.0 | FusedLocationProviderClient, GPS tracking | Industry standard, battery-efficient location API with adaptive throttling built-in |
| com.google.android.gms:play-services-location | 21.3.0 | ActivityRecognitionClient | Stationary/motion detection using device sensors, enables motion-aware location throttling |
| androidx.work:work-runtime-ktx | 2.10.1+ | Periodic background location updates | Battery-efficient periodic tasks (15min minimum interval), survives app restarts |

**Implementation Notes:**
- `FusedLocationProviderClient` already handles adaptive GPS/WiFi/cellular fusion
- `ActivityRecognitionClient` provides STILL, WALKING, IN_VEHICLE states for throttling logic
- WorkManager PeriodicWorkRequest has **15-minute minimum** interval (API limitation)
- For sub-15min updates, use FusedLocationProviderClient with LocationRequest intervals

### Audio Reliability & Debugging

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **NO NEW LIBRARIES** | - | Audio debugging | Use existing Android NBLOG APIs, AudioTrack.getUnderrunCount(), mediasoup stats |
| androidx.media3:media3-exoplayer | 1.5.0 (optional) | Advanced audio buffer management | Only if switching from direct AudioTrack, provides sophisticated jitter handling |

**Implementation Notes:**
- **Do NOT add new audio libraries** - work within existing libmediasoup-android 0.21.0
- Intermittent silence often caused by:
  - Network issues → TURN server required for mobile reliability ([Why WebRTC Calls Fail on Mobile Data](https://www.softpagecms.com/2026/01/06/why-webrtc-calls-fail-mobile-data-fix-2026/))
  - AEC ducking → adjust WebRTC audio processing settings in PeerConnectionFactory
  - Buffer underruns → increase AudioTrack buffer size, monitor with getUnderrunCount()
- Android 15 specific issue: audio capture stops after periods ([Flutter WebRTC Issue #1759](https://github.com/flutter-webrtc/flutter-webrtc/issues/1759))
- Use mediasoup Consumer stats for jitter monitoring (already available in 0.21.0)

### Security Audit & Testing

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| com.squareup.okhttp3:okhttp | 5.3.0 | TLS 1.3, CertificatePinner | Upgrade from 4.12.0 for improved TLS support, certificate pinning for MITM prevention |
| **Network Security Config** | Native (XML) | Certificate Transparency (Android 16+) | Native Android support for CT, no library needed |
| Nogotofail | Testing tool | Network security testing | Google's automated network security issue detection across all devices |

**Implementation Notes:**
- **Certificate pinning NOT recommended** for production Android apps ([Android Security Config](https://developer.android.com/privacy-and-security/security-config)) - CA changes break app without updates
- Use **Network Security Config XML** instead for declarative TLS policies
- Certificate Transparency officially supported Android 16+ ([Crystal Clear Certificates](https://www.spght.dev/articles/21-04-2025/crystal-clear-certs))
- For OkHttp CT enforcement on Android <16, use [appmattus/certificatetransparency](https://github.com/appmattus/certificatetransparency) interceptor
- TLS 1.3 supported in OkHttp 5.x

### Runtime Permissions

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| com.google.accompanist:accompanist-permissions | 0.37.0 | Compose permissions handling | Still experimental but no official replacement, standard for Jetpack Compose permission flows |

**Implementation Notes:**
- Accompanist Permissions **not deprecated** ([Accompanist Permissions](https://google.github.io/accompanist/permissions/)) - remains experimental
- Use `rememberPermissionState()` for reactive permission tracking in Compose
- Best practice: Request permissions **late in flow** ([Android Permissions Best Practices](https://developer.android.com/training/permissions/requesting))
- Android blocks permission dialog after repeated denials - provide rationale UI
- For v4.0: Implement upfront educational UI + just-in-time re-prompts

### Power Optimization

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **NO NEW LIBRARIES** | - | Doze mode exemption | Foreground service already exempt from Doze mode |
| **PARTIAL_WAKE_LOCK** | Native | Audio processing during sleep | Already using wake locks, validate configuration |

**Implementation Notes:**
- Foreground services **exempt from Doze** ([Optimize for Doze](https://developer.android.com/training/monitoring-device-state/doze-standby))
- Audio playback exempt from app standby
- WorkManager respects Doze constraints automatically
- Battery optimization: Use FusedLocationProviderClient PRIORITY_BALANCED_POWER_ACCURACY (not PRIORITY_HIGH_ACCURACY) for non-PTT location updates

## Installation

```gradle
// android/app/build.gradle.kts

dependencies {
    // EXISTING - keep versions
    implementation("io.github.crow-misia.libmediasoup-android:libmediasoup-android:0.21.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // UPGRADE to 5.3.0 recommended
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // NEW - Location & Motion
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // NEW - Background Tasks (already have WorkManager dependencies if needed)
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // NEW - Permissions UI (Compose)
    implementation("com.google.accompanist:accompanist-permissions:0.37.0")

    // OPTIONAL - Certificate Transparency for Android <16
    implementation("com.github.appmattus.certificatetransparency:certificatetransparency-android:1.1.3")
}
```

## Upgrade Recommendations

| Current | Upgrade To | Priority | Reason |
|---------|------------|----------|--------|
| OkHttp 4.12.0 | OkHttp 5.3.0 | MEDIUM | TLS 1.3 support, separate Android optimizations, DNS over HTTPS stable |
| Retrofit 2.11.0 | Keep | N/A | Current version compatible with OkHttp 5.x |
| Gson 2.11.0 | Moshi 1.15.x | LOW | Better Kotlin null safety, but Gson works fine |

**OkHttp 5.0 Migration Notes:**
- Separate JVM/Android artifacts for platform optimizations ([OkHttp 5.0 Changes](https://medium.com/@hiren6997/okhttp-5-0-what-changed-and-how-to-upgrade-without-breaking-everything-1e2dfb255848))
- MockWebServer moved to new coordinate (testing only)
- DNS over HTTPS now stable (not experimental)

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Certificate Pinning (CertificatePinner) | Server CA changes brick app without update | Network Security Config with domain-config |
| Google Maps SDK | Heavyweight for simple location tracking, 6MB+ APK size | FusedLocationProviderClient only (no map UI needed) |
| Custom AudioTrack management | Conflicts with libmediasoup-android WebRTC audio pipeline | Work within mediasoup Producer/Consumer |
| Alarmmanager for location | Battery drain, deprecated for this use case | WorkManager PeriodicWorkRequest |
| Multiple location libraries | Redundant battery drain | Consolidate on FusedLocationProviderClient |
| Third-party permission libraries | Unnecessary with Accompanist | Accompanist Permissions (experimental but stable) |

## Integration Points

### Location Tracking Architecture

```
WorkManager (15min periodic)
    → triggers LocationWorker
    → FusedLocationProviderClient.getCurrentLocation()
    → checks ActivityRecognitionClient.getActivityUpdates()
    → if STILL for >30min → reduce frequency
    → if IN_VEHICLE → increase frequency to 5min
    → upload to server via existing Retrofit API
```

**Key Configuration:**
- LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY (not HIGH_ACCURACY)
- ActivityRecognition detection every 60s (configurable)
- WorkManager constraints: CONNECTED network (defer uploads if offline)

### Audio Reliability Hardening

**No new libraries required** - configuration changes only:

```kotlin
// PeerConnectionFactory audio options
PeerConnectionFactory.InitializationOptions.builder(context)
    .setEnableInternalTracer(true) // Enable for debugging
    .createInitializationOptions()

// AudioTrack monitoring
audioTrack.getUnderrunCount() // Log periodically to detect silence causes

// Mediasoup Consumer stats
consumer.getStats().then { stats ->
    val jitter = stats.jitter
    val packetsLost = stats.packetsLost
    // Log for analysis
}
```

**Fixes for intermittent silence:**
1. **TURN server required** for mobile network reliability
2. Increase AudioTrack buffer size (reduce underruns)
3. Monitor jitter buffer via mediasoup stats
4. Disable aggressive AEC if headset detected
5. Android 15: Implement audio capture recovery (stop/restart on silence detection)

### Security Audit Tooling

**Network Security Config** (res/xml/network_security_config.xml):
```xml
<network-security-config>
    <domain-config>
        <domain includeSubdomains="true">app-connect-voice.cloud-loop.com</domain>
        <pin-set expiration="2027-01-01">
            <!-- Public key pins (backup required) -->
        </pin-set>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </domain-config>

    <!-- Certificate Transparency (Android 16+) -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

**OkHttp Interceptor for Logging:**
```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) Level.BODY else Level.BASIC
}

// Certificate Transparency interceptor (optional, Android <16)
val ctInterceptor = certificateTransparencyInterceptor {
    +BuildConfig.SERVER_URL
}
```

### Permission Flow Integration

**Compose Permission Handler:**
```kotlin
@Composable
fun LocationPermissionRequest() {
    val locationPermissionState = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Educational UI before requesting
    if (!locationPermissionState.status.isGranted) {
        PermissionRationaleDialog(
            onRequest = { locationPermissionState.launchPermissionRequest() }
        )
    }
}
```

**Upfront + Re-prompt Pattern:**
1. Onboarding: Show rationale → request all needed permissions
2. Feature-specific: Re-prompt with context when user tries feature
3. Settings deeplink: If permanently denied, guide to system settings

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| OkHttp 5.3.0 | Retrofit 2.11.0 | Fully compatible |
| play-services-location 21.3.0 | compileSdk 35 | Requires Google Play Services on device |
| WorkManager 2.10.1 | Hilt 2.59.1 | Use hilt-work for DI integration |
| Accompanist 0.37.0 | Compose BOM 2026.01.00 | Experimental API, stable in practice |
| libmediasoup-android 0.21.0 | WebRTC M124 | Bundled, no separate WebRTC dependency needed |

## Configuration Patterns

### Location Priority Modes

**Precise GPS (5min interval, PTT active):**
```kotlin
LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 300_000) // 5min
    .setMinUpdateIntervalMillis(60_000) // 1min fastest
    .setMaxUpdateDelayMillis(600_000) // 10min batch
    .build()
```

**General tracking (60s interval, background):**
```kotlin
LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000) // 1min
    .setMinUpdateIntervalMillis(30_000) // 30s fastest
    .build()
```

**Stationary mode (30min interval):**
```kotlin
// Triggered when ActivityRecognition detects STILL for >30min
LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1_800_000) // 30min
    .build()
```

### Motion Detection

```kotlin
val request = ActivityRecognitionRequest.Builder()
    .setDetectionInterval(60_000) // 60s detection
    .setRequestedActivities(
        DetectedActivity.STILL,
        DetectedActivity.WALKING,
        DetectedActivity.IN_VEHICLE
    )
    .build()

activityRecognitionClient.requestActivityUpdates(request, pendingIntent)
```

**Throttling Logic:**
- STILL + confidence >75% → reduce location frequency
- IN_VEHICLE + confidence >75% → increase to 5min
- WALKING → maintain 60s default
- Unknown → maintain 60s default

## Known Issues & Workarounds

### Android 15 Audio Capture Stops

**Issue:** Audio capture stops after 3-10 seconds with video, 1-1.5 minutes audio-only ([Flutter WebRTC #1759](https://github.com/flutter-webrtc/flutter-webrtc/issues/1759))

**Workaround:**
```kotlin
// Detect silence
if (audioSamplesAllZero && duration > 5_seconds) {
    // Trigger mute/unmute recovery
    producer.pause()
    delay(100)
    producer.resume()
}
```

### WorkManager 15-Minute Minimum

**Issue:** Cannot use WorkManager for <15min location updates

**Solution:** Use FusedLocationProviderClient `requestLocationUpdates()` directly for active tracking, WorkManager only for background periodic checks

### Certificate Transparency Android <16

**Issue:** Native CT support only Android 16+ (API 36)

**Solution:** Use [appmattus/certificatetransparency](https://github.com/appmattus/certificatetransparency) OkHttp interceptor for earlier versions

### TURN Server Required for Mobile

**Issue:** WebRTC audio fails or becomes one-way on mobile networks ([WebRTC Mobile Data Failures](https://www.softpagecms.com/2026/01/06/why-webrtc-calls-fail-mobile-data-fix-2026/))

**Solution:** Deploy TURN server (coturn) for relay when direct P2P fails. Already using mediasoup routing, but ensure TURN configuration in WebRTC ICE config.

## Sources

**HIGH Confidence (Official Documentation):**
- [FusedLocationProviderClient API](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient) - Google Play Services
- [Activity Recognition API](https://developers.google.com/location-context/activity-recognition) - Google official
- [Android Doze Mode](https://developer.android.com/training/monitoring-device-state/doze-standby) - Android official
- [Request Runtime Permissions](https://developer.android.com/training/permissions/requesting) - Android official
- [WorkManager Releases](https://developer.android.com/jetpack/androidx/releases/work) - AndroidX official
- [OkHttp Changelog](https://square.github.io/okhttp/changelogs/changelog/) - Square official
- [Network Security Config](https://developer.android.com/privacy-and-security/security-config) - Android official

**MEDIUM Confidence (Verified Community Sources):**
- [Crystal Clear Certificates - Certificate Transparency](https://www.spght.dev/articles/21-04-2025/crystal-clear-certs) - Android GDE article
- [OkHttp 5.0 Migration Guide](https://medium.com/@hiren6997/okhttp-5-0-what-changed-and-how-to-upgrade-without-breaking-everything-1e2dfb255848) - Community guide
- [Accompanist Permissions](https://google.github.io/accompanist/permissions/) - Google experimental library
- [Advanced Location Tracking Battery Efficiency](https://www.oneclickitsolution.com/centerofexcellence/android/advanced-location-tracking-with-battery-efficiency-in-android-app) - Implementation patterns

**LOW Confidence (Needs Validation):**
- [Why WebRTC Calls Fail on Mobile Data](https://www.softpagecms.com/2026/01/06/why-webrtc-calls-fail-mobile-data-fix-2026/) - Issue diagnosis, validate with testing
- [Flutter WebRTC Android 15 Issue](https://github.com/flutter-webrtc/flutter-webrtc/issues/1759) - Unresolved bug report
- [Audio Debugging AOSP](https://source.android.com/docs/core/audio/debugging) - AOSP docs (device-specific)

---

*Stack research for: VoicePing Router v4.0 Production Features*
*Researched: 2026-02-15*
*Confidence: MEDIUM - Official APIs verified, implementation patterns need testing*
