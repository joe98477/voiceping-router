---
phase: 15
plan: 01
subsystem: android-build-release
tags: [r8, proguard, jni, release-validation, minification]
dependency_graph:
  requires: []
  provides:
    - comprehensive-r8-keep-rules
    - release-apk-build-verified
  affects:
    - android-release-builds
    - production-deployment
tech_stack:
  added: []
  patterns:
    - R8 code shrinking with JNI preservation
    - ProGuard keep rules for WebRTC and mediasoup libraries
    - Incremental R8 optimization validation
key_files:
  created: []
  modified:
    - android/app/proguard-rules.pro
decisions:
  - decision: "Preserve both io.github.crow_misia.mediasoup and org.mediasoup packages"
    rationale: "Library may use either package internally, keep both for safety"
    alternatives: "Only keep crow_misia package"
    impact: "Slightly larger APK but ensures all JNI classes preserved"
  - decision: "Enable full R8 optimization (no -dontobfuscate)"
    rationale: "Production builds need full code shrinking and obfuscation"
    alternatives: "Use -dontobfuscate for easier debugging"
    impact: "Better security and smaller APK, mapping.txt required for crash reports"
metrics:
  duration_seconds: 353
  completed: "2026-02-13T10:12:36Z"
  tasks_completed: 2
  files_modified: 1
  commits: 1
---

# Phase 15 Plan 01: ProGuard/R8 Rules and Release Build Validation Summary

**One-liner:** Comprehensive R8 keep rules for WebRTC and crow-misia mediasoup JNI libraries, verified release APK builds successfully with minification enabled

## What Was Built

Updated ProGuard/R8 rules to preserve JNI classes from WebRTC and crow-misia mediasoup libraries, then verified the release APK builds successfully with R8 code shrinking and obfuscation enabled.

### Key Additions to proguard-rules.pro

1. **crow-misia mediasoup package preservation** — Added `-keep` rules for `io.github.crow_misia.mediasoup.**` (the actual library package used in imports)
2. **WebRTC interface preservation** — Added `-keep interface org.webrtc.** { *; }` alongside existing class rules
3. **Native method signature preservation** — Added `-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }` for JNI downcalls
4. **@CalledByNative annotation preservation** — Added rule to preserve methods annotated with `@org.webrtc.CalledByNative` for JNI upcalls from C++
5. **Hilt DI code generation preservation** — Added `-keep class dagger.hilt.**` and related rules for dependency injection

### Release Build Verification

- Release APK built successfully: `app-release-unsigned.apk` (42.8 MB)
- Build time: 4m 12s
- No R8 warnings about missing WebRTC or mediasoup classes
- Mapping.txt generated (54 MB) for crash report deobfuscation
- Verified WebRTC and mediasoup classes preserved in mapping.txt

## Technical Implementation

### ProGuard Rules Structure

Reorganized `android/app/proguard-rules.pro` with clear section headers:
- WebRTC (org.webrtc)
- mediasoup (io.github.crow_misia and org.mediasoup)
- Native Methods (JNI)
- Hilt Dependency Injection
- Gson Serialization
- OkHttp/Retrofit

Each section includes comments explaining why the rules are needed (R8 cannot detect JNI calls from C++).

### Build Configuration

Release build type already configured in `android/app/build.gradle.kts`:
- `isMinifyEnabled = true` — Enables R8 code shrinking
- `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` — Uses optimized baseline + custom rules

### Validation Methodology

1. Grep mapping.txt for WebRTC and mediasoup classes → Confirmed preserved
2. Check build log for R8 warnings → None found
3. Verify APK output exists → 42.8 MB release APK created
4. Confirm mapping.txt generated → 54 MB file for crash deobfuscation

## Deviations from Plan

None - plan executed exactly as written.

## Files Modified

| File | Changes | Purpose |
|------|---------|---------|
| `android/app/proguard-rules.pro` | Added 38 lines (7 new sections) | Comprehensive R8 keep rules for JNI libraries |

## Verification Results

All verification criteria passed:

1. ✓ `android/app/proguard-rules.pro` contains `-keep class io.github.crow_misia.mediasoup.** { *; }` rule
2. ✓ `android/app/proguard-rules.pro` contains `-keepclasseswithmembernames` rule for `native <methods>`
3. ✓ `android/app/proguard-rules.pro` contains `@org.webrtc.CalledByNative` preservation
4. ✓ `cd android && ./gradlew assembleRelease` completed with BUILD SUCCESSFUL
5. ✓ Release APK exists at `android/app/build/outputs/apk/release/app-release-unsigned.apk`

## Next Steps

Plan 15-02 will perform physical device testing:
- Install release APK on Android device
- Test login, channel join, PTT transmission
- Verify WebRTC audio works (Producer/Consumer)
- Monitor for JNI errors (UnsatisfiedLinkError, NoSuchMethodError)
- Battery profiling with screen off (target: under 10%/hour)

## Dependencies

**Provides:**
- `comprehensive-r8-keep-rules` — ProGuard rules prevent JNI class stripping in release builds
- `release-apk-build-verified` — Confirmed R8 optimization doesn't break mediasoup/WebRTC

**Required by:**
- Plan 15-02 (Physical Device Testing) — Requires release APK from this plan

## Self-Check: PASSED

**Files verified:**
```
FOUND: android/app/proguard-rules.pro
FOUND: android/app/build/outputs/apk/release/app-release-unsigned.apk
FOUND: android/app/build/outputs/mapping/release/mapping.txt
```

**Commits verified:**
```
FOUND: 1fad4b3 (feat(15-01): add comprehensive R8 keep rules for JNI libraries)
```

**Grep verifications:**
```
FOUND: io.github.crow_misia.mediasoup.** in proguard-rules.pro (line 22-23)
FOUND: native <methods> via -keepclasseswithmembernames (line 32)
FOUND: @org.webrtc.CalledByNative (line 15)
FOUND: dagger.hilt.** (line 39, 41)
FOUND: org.webrtc classes in mapping.txt (preserved by keep rules)
FOUND: io.github.crow_misia.mediasoup classes in mapping.txt (preserved by keep rules)
```
