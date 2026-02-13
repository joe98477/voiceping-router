# Phase 15: Release Build Validation and Device Testing - Research

**Researched:** 2026-02-13
**Domain:** Android release build validation, ProGuard/R8 optimization, physical device testing, battery profiling
**Confidence:** MEDIUM-HIGH

## Summary

Phase 15 validates that the VoicePing Android app works correctly in production release builds with R8 code shrinking and obfuscation enabled, and that real-world performance meets battery efficiency requirements on physical hardware. The three critical validation areas are: (1) ProGuard/R8 rules for JNI class preservation in mediasoup and WebRTC libraries, (2) end-to-end audio testing on physical Android devices, and (3) battery profiling to ensure WebRTC threads don't cause excessive drain during background operation.

The app uses libmediasoup-android 0.21.0 (crow-misia), which wraps WebRTC native libraries (C++) with Kotlin/JNI bindings. R8's static analysis cannot detect JNI method calls from native code, creating risk that critical classes will be stripped during release builds unless explicit keep rules are provided. The existing proguard-rules.pro file has baseline rules but needs validation through actual release APK testing.

Battery profiling is critical because the app runs as a foreground service with WebRTC audio threads active during screen-off operation. The success criterion is under 10%/hour drain with screen off, which requires analyzing wake locks, WebRTC thread CPU usage, and AudioDeviceModule behavior using Android Studio Energy Profiler or command-line tools (adb dumpsys batterystats, Battery Historian).

**Primary recommendation:** Build release APK incrementally—first verify build succeeds with current rules, then test on physical device with end-to-end audio (transmit PTT + receive consumer), then profile battery with overnight screen-off monitoring. Use Android Studio Energy Profiler for real-time wake lock detection and adb batterystats for historical analysis.

## Standard Stack

### Core Tools

| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| R8 | AGP 9.0.0 (bundled) | Code shrinking, obfuscation, optimization | Default optimizer in AGP 9.0+, replaced ProGuard |
| Android Studio Profiler | 2026.1+ | Real-time battery, CPU, memory profiling | Official IDE profiler with Energy Profiler HUD |
| adb (Android Debug Bridge) | Platform Tools latest | APK installation, batterystats, logcat | Standard Android device communication tool |
| Battery Historian | v2.0+ | Historical battery drain analysis from bugreports | Google's official battery analysis tool |

### Supporting Tools

| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| Gradle assembleRelease | 9.3.1 | Build release APK with R8 enabled | Every release build |
| adb dumpsys batterystats | Platform Tools | Collect battery statistics from device | Post-testing analysis |
| adb bugreport | Platform Tools | Generate full system diagnostics zip | Battery Historian input |
| Android Device Monitor (deprecated) | N/A | Replaced by Android Studio Profiler | Don't use—use Studio Profiler |

### Configuration Files

| File | Purpose | Location |
|------|---------|----------|
| proguard-rules.pro | App-specific R8 keep rules | android/app/proguard-rules.pro |
| build.gradle.kts (release buildType) | Enable minifyEnabled, set ProGuard files | android/app/build.gradle.kts |
| local.properties | Android SDK location (gitignored) | android/local.properties |

**Build command:**
```bash
cd android
./gradlew assembleRelease
# Output: android/app/build/outputs/apk/release/app-release-unsigned.apk
```

**Install release APK:**
```bash
adb install -r android/app/build/outputs/apk/release/app-release-unsigned.apk
```

## Architecture Patterns

### Recommended Testing Flow

```
1. Build Validation
   ├── assembleRelease (verify build succeeds)
   ├── Inspect build/outputs/mapping/release/mapping.txt (obfuscation map)
   └── Check for JNI-related warnings in build log

2. Device Testing
   ├── adb install -r app-release.apk
   ├── Test login and channel join
   ├── Test PTT transmission (Producer creation)
   ├── Test audio reception (Consumer playback)
   └── Monitor logcat for NoSuchMethodError or UnsatisfiedLinkError

3. Battery Profiling
   ├── Android Studio → Profiler → Energy (real-time)
   ├── Enable wake lock tracking: adb shell dumpsys batterystats --enable full-wake-history
   ├── Run overnight test with screen off
   ├── Collect report: adb bugreport bugreport.zip
   └── Analyze with Battery Historian
```

### Pattern 1: ProGuard/R8 JNI Keep Rules

**What:** Preserve classes and methods called from native code via JNI using explicit keep rules.

**When to use:** Any Android app using WebRTC, mediasoup, or other C++ libraries with JNI bindings.

**Problem:** R8 performs static analysis on Java/Kotlin bytecode but cannot detect method calls originating from C++ via JNI string lookups. Without keep rules, R8 strips "unused" classes, causing UnsatisfiedLinkError or NoSuchMethodError at runtime.

**Example keep rules for WebRTC and mediasoup:**
```proguard
# Keep all WebRTC classes (library contains JNI upcalls)
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }

# Keep all mediasoup classes (crow-misia wrapper)
-keep class org.mediasoup.** { *; }
-keepclassmembers class org.mediasoup.** { *; }
-keep class io.github.crow_misia.mediasoup.** { *; }
-keepclassmembers class io.github.crow_misia.mediasoup.** { *; }

# Keep native method signatures (called by Java/Kotlin)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep methods called from JNI (if annotated)
-keepclassmembers class * {
    @org.webrtc.CalledByNative <methods>;
}

# Keep Gson models (for JSON serialization in mediasoup)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.voiceping.android.data.model.** { *; }
-keep class com.voiceping.android.domain.model.** { *; }
```

**Source:** [Android Developers: Configure and troubleshoot R8 Keep Rules](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html)

### Pattern 2: Incremental R8 Adoption

**What:** Enable R8 optimizations gradually to isolate issues.

**When to use:** First release build with minification enabled, or after adding new native libraries.

**Steps:**
1. Start with `-dontobfuscate` to disable name obfuscation (keeps class/method names readable in stack traces)
2. Enable code shrinking only (`-dontshrink` disabled)
3. Progressively enable full mode and obfuscation once basic build succeeds
4. Test after each step

**Example temporary configuration:**
```gradle
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        // Temporary: disable obfuscation for initial testing
        // Remove this once JNI classes are confirmed working
        // proguardFiles("proguard-dontobfuscate.pro")
    }
}
```

**Source:** [Android Developers: Adopt optimizations incrementally](https://developer.android.com/topic/performance/app-optimization/adopt-optimizations-incrementally)

### Pattern 3: Battery Profiling Workflow

**What:** Use Android Studio Energy Profiler for real-time wake lock detection, then adb batterystats for historical analysis.

**When to use:** After release APK passes functional tests, before production deployment.

**Real-time profiling (Android Studio):**
1. Connect physical device via USB
2. Android Studio → View → Tool Windows → Profiler
3. Select app process
4. Click "Energy" profiler
5. Monitor System Timeline for wake locks, alarms, location requests
6. Look for PARTIAL_WAKE_LOCK events during screen-off periods

**Historical profiling (command line):**
```bash
# Reset battery stats before test
adb shell dumpsys batterystats --reset
adb shell dumpsys batterystats --enable full-wake-history

# Run test (e.g., overnight with screen off, channel monitoring active)
# After test:
adb bugreport bugreport.zip

# Upload bugreport.zip to Battery Historian web UI
# Analyze wake lock timeline and per-app power consumption
```

**Source:** [Android Studio: Inspect energy use with Energy Profiler](https://developer.android.com/studio/profile/power-profiler)

### Anti-Patterns to Avoid

- **Using `-dontwarn` to suppress R8 warnings without investigating** — Warnings indicate missing classes that may cause runtime crashes
- **Testing only debug builds** — R8 is disabled in debug, issues only appear in release
- **Ignoring mapping.txt file** — Needed to deobfuscate crash reports from production
- **Not testing on physical devices** — Emulators don't simulate battery drain, Bluetooth, or real audio hardware
- **Using deprecated Android Device Monitor** — Replaced by Android Studio Profiler in modern SDKs

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Battery profiling analysis | Custom log parsers for power stats | Battery Historian (Google), Android Studio Energy Profiler | Edge cases: wake lock attribution, Doze mode interactions, per-subsystem rail monitoring (ODPM) |
| JNI method preservation | Manual inspection of native code calls | R8 keep rules with `-keep` annotations | R8 static analysis can't detect JNI upcalls; explicit rules mandatory |
| APK signing | Custom keystore management scripts | Android Studio Build → Generate Signed Bundle/APK | Handles v1/v2/v3/v4 signature schemes, key rotation |
| Release build testing | Manual APK distribution to testers | Firebase App Distribution, Google Play Internal Testing | Tracks build versions, crash reports, device coverage |

**Key insight:** Battery profiling requires system-level instrumentation that only platform tools provide. Custom solutions miss kernel wake locks, ODPM power rail data, and Doze mode state transitions that impact real-world battery life.

## Common Pitfalls

### Pitfall 1: JNI Classes Stripped in Release Build

**What goes wrong:** Release APK builds successfully but crashes at runtime with `java.lang.UnsatisfiedLinkError: No implementation found for ...` or `NoSuchMethodError` when WebRTC/mediasoup attempts to call JNI methods.

**Why it happens:** R8 removes classes it believes are unused based on static analysis. JNI method calls from C++ use dynamic string lookups (e.g., `env->CallVoidMethod(...)`) which R8 cannot detect. Without explicit keep rules, R8 strips these "unused" Java methods.

**How to avoid:**
- Add comprehensive `-keep class org.webrtc.** { *; }` and `-keep class io.github.crow_misia.mediasoup.** { *; }` rules
- Use `-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }` for native method signatures
- Test release build on physical device immediately after enabling minification
- Check build logs for warnings like "can't find referenced class" related to org.webrtc or org.mediasoup

**Warning signs:**
- Build succeeds with warnings about "can't find referenced class"
- App works in debug but crashes in release
- Stack trace shows JNI-related errors (UnsatisfiedLinkError, NoSuchMethodError)
- Crash occurs during PeerConnectionFactory.initialize() or Device.load() calls

**Source:** [Groups: WebRTC not working after proguard-rules](https://groups.google.com/g/discuss-webrtc/c/tedmqVFTSJE)

### Pitfall 2: Missing Consumer Rules from Library

**What goes wrong:** App's proguard-rules.pro looks correct, but release build still strips library classes because library's consumer-rules.pro isn't being applied.

**Why it happens:** libmediasoup-android (crow-misia) may not include consumer-rules.pro in its AAR. Libraries are responsible for providing keep rules for their JNI/reflection usage, but not all library authors follow this practice.

**How to avoid:**
- Check library's AAR file for META-INF/proguard/ directory with consumer rules
- If library lacks consumer rules, add them manually to app's proguard-rules.pro
- Use broad `-keep` rules for entire library package (e.g., `io.github.crow_misia.mediasoup.**`)
- File issue with library maintainer requesting consumer rules

**Warning signs:**
- Your proguard-rules.pro has WebRTC rules but not mediasoup wrapper rules
- Crash occurs in mediasoup Device/Transport/Producer/Consumer classes (not org.webrtc)
- Build log shows no warnings, but runtime crashes in crow-misia package

**Source:** [Android Developers: Optimization for library authors](https://developer.android.com/topic/performance/app-optimization/library-optimization)

### Pitfall 3: Excessive Wake Lock from WebRTC Threads

**What goes wrong:** App drains 30-50% battery overnight despite being "idle" with screen off. Battery stats show VoicePing app holding wake locks for hours.

**Why it happens:** WebRTC AudioDeviceModule or mediasoup Transport may hold PARTIAL_WAKE_LOCK during ICE connection states ("connected", "checking") or when AudioRecord/AudioTrack are active but not properly stopped. Foreground service keeps app alive, but WebRTC threads stay active unnecessarily.

**How to avoid:**
- Verify Producer/Consumer are closed when channels go idle (no active speakers)
- Ensure AudioDeviceModule.dispose() is called on cleanup
- Use Android Studio Energy Profiler to identify which component holds wake locks
- Test with `adb shell dumpsys batterystats --enable full-wake-history` to see individual wake lock timelines
- Check that Foreground service uses `FOREGROUND_SERVICE_TYPE_MICROPHONE` and `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` (not generic foreground service)

**Warning signs:**
- Battery stats show app consuming 10%+ per hour with screen off
- `adb shell dumpsys batterystats` shows long-duration wake locks attributed to app package
- Android Play Console flags app for excessive background wake locks (>3 hours)
- CPU profiler shows WebRTC_Network or WebRTC_Signaling threads active during idle periods

**Source:** [Google Groups: On android, does webRTC drain battery life?](https://groups.google.com/g/discuss-webrtc/c/ISt0ayXO96o)

### Pitfall 4: Testing Only on WiFi

**What goes wrong:** App works perfectly on WiFi during testing, but users report failed PTT or no audio on cellular networks.

**Why it happens:** ICE candidate gathering may fail on cellular if TURN server isn't configured, or mediasoup transport times out due to NAT traversal issues. WiFi networks are typically more permissive.

**How to avoid:**
- Test physical device on cellular (mobile data) not just WiFi
- Verify TURN server credentials are configured in mediasoup server
- Check ICE connection state transitions in logs (checking → connected vs checking → failed)
- Test in restricted network environments (corporate WiFi with strict firewall)

**Warning signs:**
- Logs show ICE state stuck in "checking" or transitioning to "failed"
- No audio playback despite Consumer being created
- Transport.onConnectionStateChange shows "disconnected" or "failed" on cellular

### Pitfall 5: Not Preserving mapping.txt

**What goes wrong:** Production crash reports show obfuscated stack traces like `a.b.c.d.e()` that are impossible to debug.

**Why it happens:** R8 obfuscates class/method names in release builds and generates mapping.txt to reverse the process. If mapping.txt is lost, crash reports from that build version are undecodable.

**How to avoid:**
- Archive `android/app/build/outputs/mapping/release/mapping.txt` for each release build
- Upload mapping.txt to Google Play Console (automatic deobfuscation of crash reports)
- Use version control or CI/CD to store mappings by build number
- Consider retracetrace tool for manual deobfuscation: `retrace.sh mapping.txt stacktrace.txt`

**Warning signs:**
- Crash reports from Firebase Crashlytics show single-letter class names
- Unable to correlate crash location with actual source code
- Different builds produce different obfuscated names for same class

## Code Examples

### Verified ProGuard Rules for WebRTC + mediasoup

```proguard
# VoicePing Release Build ProGuard Rules
# Updated: 2026-02-13 for Phase 15 validation

# ===== WebRTC (org.webrtc) =====
# WebRTC contains extensive JNI bindings to native C++ code.
# R8 cannot detect method calls from C++, so we must preserve all classes.

-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-keep interface org.webrtc.** { *; }

# Keep methods annotated with @CalledByNative (JNI upcalls from C++)
-keepclassmembers class * {
    @org.webrtc.CalledByNative <methods>;
}

# ===== mediasoup (io.github.crow_misia) =====
# crow-misia wrapper library for mediasoup, also uses JNI bindings.
# Library does not provide consumer-rules.pro, so we preserve manually.

-keep class io.github.crow_misia.mediasoup.** { *; }
-keepclassmembers class io.github.crow_misia.mediasoup.** { *; }

# Alternative package name (check library JAR for actual package)
-keep class org.mediasoup.** { *; }
-keepclassmembers class org.mediasoup.** { *; }

# ===== Native Methods (JNI) =====
# Preserve all native method signatures (called from Java/Kotlin to C++)

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ===== Gson Serialization =====
# mediasoup uses Gson for JSON serialization of RTP parameters, ICE candidates, etc.
# Keep model classes and their field signatures.

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.voiceping.android.data.model.** { *; }
-keep class com.voiceping.android.domain.model.** { *; }

# ===== OkHttp/Retrofit =====
# Networking layer for signaling

-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ===== Hilt (Dependency Injection) =====
# Hilt uses code generation, keep generated classes

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ===== Debugging =====
# Uncomment during initial testing to disable obfuscation (keeps class names readable)
# -dontobfuscate
```

**Source:** Synthesized from [WebRTC ProGuard discussion](https://groups.google.com/g/discuss-webrtc/c/tedmqVFTSJE) and [Practical ProGuard rules examples](https://medium.com/androiddevelopers/practical-proguard-rules-examples-5640a3907dc9)

### Battery Stats Collection Script

```bash
#!/bin/bash
# battery-profile.sh — Collect battery stats for VoicePing overnight test
# Usage: ./battery-profile.sh start|stop

PACKAGE="com.voiceping.android"

case "$1" in
  start)
    echo "Resetting battery stats and enabling full wake history..."
    adb shell dumpsys batterystats --reset
    adb shell dumpsys batterystats --enable full-wake-history
    echo "Battery profiling started. Run app test now."
    echo "When done, run: $0 stop"
    ;;

  stop)
    echo "Collecting battery stats and generating bugreport..."
    adb shell dumpsys batterystats > batterystats.txt
    adb bugreport bugreport.zip
    echo "Done. Upload bugreport.zip to https://bathist.ef.lc/"
    echo "Battery stats saved to: batterystats.txt"
    ;;

  *)
    echo "Usage: $0 start|stop"
    echo "  start — Reset stats and begin profiling"
    echo "  stop  — Collect stats and generate bugreport"
    exit 1
    ;;
esac
```

**Source:** [GitHub: battery-historian](https://github.com/google/battery-historian)

### Release APK Build and Test

```bash
#!/bin/bash
# release-test.sh — Build release APK and install on device

set -e  # Exit on error

echo "=== Building release APK ==="
cd android
./gradlew assembleRelease

APK="app/build/outputs/apk/release/app-release-unsigned.apk"
MAPPING="app/build/outputs/mapping/release/mapping.txt"

if [ ! -f "$APK" ]; then
    echo "Error: Release APK not found at $APK"
    exit 1
fi

echo "=== Archiving mapping.txt ==="
mkdir -p ../release-archives/$(date +%Y%m%d-%H%M%S)
cp "$MAPPING" ../release-archives/$(date +%Y%m%d-%H%M%S)/mapping.txt

echo "=== Installing release APK on device ==="
adb install -r "$APK"

echo "=== Starting app and monitoring logcat ==="
adb shell am start -n com.voiceping.android/.presentation.MainActivity
adb logcat -c  # Clear logcat
adb logcat | grep -E "(VoicePing|MediasoupClient|WebRTC|FATAL|AndroidRuntime)"

echo "=== Release APK installed successfully ==="
echo "Test checklist:"
echo "  1. Login with credentials"
echo "  2. Select event and join channel"
echo "  3. Press PTT button (test Producer creation)"
echo "  4. Listen for remote audio (test Consumer playback)"
echo "  5. Monitor logcat for JNI errors (UnsatisfiedLinkError, NoSuchMethodError)"
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ProGuard | R8 | AGP 3.4.0 (2019) | R8 is faster, better optimization, full mode enabled by default in AGP 9.0 |
| Android Device Monitor | Android Studio Profiler | Android Studio 3.0 (2017) | Integrated profiling in IDE, better CPU/memory/network/energy analysis |
| Manual wake lock detection | Energy Profiler + Battery Historian | Android Studio Hedgehog (2023) | ODPM power rail monitoring, subsystem-level energy breakdown |
| Emulator-only testing | Physical device + Firebase Test Lab | Ongoing | Battery, Bluetooth, real audio hardware cannot be fully emulated |
| `-dontoptimize` for safety | R8 full mode (aggressive optimization) | AGP 9.0 (2024) | Code size reduction, but requires careful keep rules for JNI |

**Deprecated/outdated:**
- **proguard-android.txt baseline**: Replaced by R8's built-in optimizations in AGP 9.0+. Use `proguard-android-optimize.txt` for release builds.
- **adb shell am broadcast -a com.android.systemui.doze.pulse**: Deprecated Doze testing method. Use `adb shell dumpsys deviceidle force-idle` instead.
- **Android Device Monitor (ddms)**: Removed in Android SDK Platform-Tools 29.0.0. Use Android Studio Profiler.

**Source:** [Android Developers Blog: The End of proguard-android.txt](https://medium.com/@musaddiq625/%EF%B8%8F-the-end-of-proguard-android-txt-a-new-era-for-r8-optimization-ac1d0c3f6088)

## Open Questions

1. **libmediasoup-android consumer rules availability**
   - What we know: GitHub repository has no visible consumer-rules.pro or proguard-rules.pro in main branch
   - What's unclear: Does the published AAR on Maven Central include consumer rules? Need to download and inspect AAR contents.
   - Recommendation: Download AAR, extract with `unzip libmediasoup-android-0.21.0.aar`, check META-INF/proguard/ directory. If absent, add comprehensive keep rules to app's proguard-rules.pro manually.

2. **Battery drain threshold validation**
   - What we know: Success criterion is "under 10%/hour with screen off" but no baseline from v2.0 testing
   - What's unclear: What's acceptable drain for a foreground service with WebRTC audio monitoring? Normal range?
   - Recommendation: Establish baseline by testing v2.0 stub implementation first (no real WebRTC), then compare v3.0 with real WebRTC. Acceptable range likely 5-8%/hour for always-on PTT monitoring based on VoIP app benchmarks.

3. **Physical device availability**
   - What we know: "On-device testing not yet performed (no physical Android device during development)" — from STATE.md blockers
   - What's unclear: Will physical device be available for Phase 15 testing? If not, what's the fallback plan?
   - Recommendation: If no physical device available, use Firebase Test Lab for basic validation (login, channel join, PTT button press). Battery profiling requires physical device—cannot be done on emulators. Consider borrowing/purchasing low-cost Android device (min SDK 26+) for testing.

4. **TURN server configuration**
   - What we know: mediasoup server setup (Phases 1-4) but unclear if TURN server is configured for NAT traversal
   - What's unclear: Will cellular network testing work without TURN? Does server have TURN credentials configured?
   - Recommendation: Verify server's WebRtcTransport configuration includes TURN servers for cellular NAT traversal. Test on cellular network during Phase 15 device testing.

5. **Consumer.stats API undocumented**
   - What we know: Consumer.stats property type not documented in crow-misia library (Phase 12 stubbed as "Good" stats)
   - What's unclear: Does actual device testing reveal Consumer.stats structure for packet loss/jitter extraction?
   - Recommendation: During Phase 15 device testing, log `consumer.stats.toString()` or `consumer.stats::class.simpleName` to identify actual type (String JSON vs RTCStatsReport object). Update getConsumerStats() implementation if needed.

## Sources

### Primary (HIGH confidence)

- [Android Developers: Configure and troubleshoot R8 Keep Rules](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html) - Official R8 configuration guidance (2025)
- [Android Developers: Keep rule use cases and examples](https://developer.android.com/topic/performance/app-optimization/keep-rule-examples) - JNI keep rules examples
- [Android Developers: Inspect energy use with Energy Profiler](https://developer.android.com/studio/profile/power-profiler) - Energy Profiler usage
- [Android Developers: Debug wake locks locally](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/debug-locally) - Wake lock debugging
- [Android Developers: Adopt optimizations incrementally](https://developer.android.com/topic/performance/app-optimization/adopt-optimizations-incrementally) - Incremental R8 adoption
- [GitHub: google/battery-historian](https://github.com/google/battery-historian) - Battery Historian tool and usage

### Secondary (MEDIUM confidence)

- [Groups: WebRTC not working after proguard-rules](https://groups.google.com/g/discuss-webrtc/c/tedmqVFTSJE) - WebRTC ProGuard rules discussion (community)
- [Medium: Practical ProGuard rules examples](https://medium.com/androiddevelopers/practical-proguard-rules-examples-5640a3907dc9) - Wojtek Kaliciński (Android Developers team)
- [Groups: On android, does webRTC drain battery life?](https://groups.google.com/g/discuss-webrtc/c/ISt0ayXO96o) - WebRTC battery drain discussion
- [Medium: Mastering Android Profiling](https://medium.com/@pinankhpatel/mastering-android-profiling-a-complete-guide-to-battery-memory-ui-and-overall-app-performance-0f8bc4175aab) - Battery profiling guide (2025)
- [ProAndroidDev: Battery Profiling Like a Pro (2025 Edition)](https://proandroiddev.com/battery-profiling-like-a-pro-2025-edition-how-to-catch-android-power-hogs-in-the-act-635379b184ae) - Battery profiling strategies

### Tertiary (LOW confidence - needs validation)

- [crow-misia/libmediasoup-android GitHub](https://github.com/crow-misia/libmediasoup-android) - No consumer rules found in repository (verified via WebFetch)
- Community forums on wake lock detection (XDA Forums, StackOverflow) - Techniques valid but not official Google guidance

## Metadata

**Confidence breakdown:**
- ProGuard/R8 rules for JNI: HIGH — Official Android documentation, verified community patterns for WebRTC
- Battery profiling tools: HIGH — Official Android Studio tools, Google's Battery Historian
- Physical device testing: MEDIUM — Standard practice but no VoicePing-specific guidance
- libmediasoup-android consumer rules: LOW — Library lacks documentation, needs AAR inspection

**Research date:** 2026-02-13
**Valid until:** 2026-03-15 (30 days for stable Android SDK/tooling, R8 changes rare)

**Key knowledge gaps:**
- libmediasoup-android AAR contents (consumer rules presence)
- Baseline battery drain from v2.0 stub implementation
- Physical device availability for testing
- TURN server configuration on mediasoup server

**Next steps for planner:**
- Plan 15-01: ProGuard/R8 validation — Build release APK, test JNI preservation
- Plan 15-02: Physical device testing — End-to-end audio validation, battery profiling
- Consider splitting battery profiling into separate plan if overnight testing needed
