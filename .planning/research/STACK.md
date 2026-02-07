# Stack Research: Android PTT Client

**Project:** VoicePing Android Native Client
**Researched:** 2026-02-08
**Confidence:** MEDIUM (WebRTC libraries verified, mediasoup wrapper needs validation)

## Executive Summary

The Android native PTT client requires a hybrid stack combining JNI-wrapped C++ libraries for WebRTC/mediasoup with modern Kotlin/Jetpack Compose for UI and business logic. The key architectural challenge is integrating libmediasoupclient (C++) with Android's Java/Kotlin ecosystem while maintaining low-latency audio performance for enterprise PTT use cases.

**Critical decision:** Use crow-misia/libmediasoup-android (0.21.0) as the mediasoup client wrapper. This is the most actively maintained, Maven-published wrapper with proper JNI bindings.

**Integration point:** The Android client connects to the EXISTING mediasoup 3.19 server via the EXISTING WebSocket signaling protocol at `/ws`. No server changes needed.

## Recommended Stack

### Core WebRTC & Media

| Technology | Version | Purpose | Rationale |
|------------|---------|---------|-----------|
| **libmediasoup-android** | 0.21.0 | mediasoup client wrapper | Most actively maintained JNI wrapper (May 2025 release), published to Maven Central, proper lifecycle management. Wraps libmediasoupclient C++ library. |
| **io.getstream:stream-webrtc-android** | 1.3.9+ | WebRTC core library | Pre-compiled WebRTC library reflecting recent Google WebRTC commits. Google stopped publishing official Android WebRTC binaries in 2018. GetStream maintains up-to-date builds compatible with modern Android. |

**Why NOT Google's libwebrtc directly:**
- Google discontinued pre-compiled Android/iOS distribution
- Building from source requires Linux, 8GB+ RAM, 50GB+ storage
- GetStream provides maintained, tested builds with Jetpack Compose integration

**Why crow-misia over alternatives:**
- haiyangwu/mediasoup-client-android: Last updated 2021, unmaintained
- chenjim fork: Inconsistent updates
- crow-misia: Active maintenance (610 commits), Maven Central publishing, GitHub Actions CI

### WebSocket & Networking

| Technology | Version | Purpose | Rationale |
|------------|---------|---------|-----------|
| **OkHttp** | 4.12.0 | WebSocket + HTTP client | Industry standard. Fixes memory leaks from 4.11. Native WebSocket support with connection pooling. Powers Retrofit. |
| **Retrofit** | 2.11.0 | REST API client | Type-safe HTTP client for login/auth endpoints. Built on OkHttp, automatic JSON parsing with Gson. |
| **Gson** | 2.11.0 | JSON serialization | Matches server's JSON protocol. Simpler than Moshi for straightforward DTO mapping. |

**WebSocket pattern:**
- OkHttp WebSocketListener for signaling protocol
- Manual reconnection with exponential backoff (existing server has /ws endpoint)
- Background thread for message handling (OkHttp callbacks run on worker threads)

### Android Framework

| Technology | Version | Purpose | Rationale |
|------------|---------|---------|-----------|
| **Jetpack Compose** | 1.10.0 (Dec 2025) | UI framework | Material 3 stable. Declarative UI, better than XML for dynamic channel lists. State hoisting for reactive PTT indicators. |
| **Compose Material 3** | 1.4.0 | Material Design 3 | Stable as of Dec 2025. Modern components (SegmentedButton, NavigationBar, Surface containers). |
| **Kotlin Coroutines** | 1.10.1+ | Async operations | Structured concurrency for WebSocket, WebRTC callbacks. Flow/StateFlow for reactive state. |
| **Hilt** | 2.51.1+ | Dependency injection | Dagger-based DI. Standard for Android. ViewModels, repositories, singleton services (WebSocket, mediasoup Device). |
| **Media3 MediaSessionService** | 1.5.0+ | Foreground service audio | Replaces deprecated MediaSession. Automatic notification handling, audio focus management, foreground service lifecycle. |

**Why Compose over XML:**
- Dynamic channel list with Material 3 cards/chips
- Reactive PTT states (pressed, transmitting, receiving) with less boilerplate
- Modern team preference (server is TypeScript, Compose is declarative like React)

### Audio Management

| Technology | Version | Purpose | Rationale |
|------------|---------|---------|-----------|
| **AudioManager** | Android SDK | Audio routing, focus | SCO routing for Bluetooth headsets. AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK for PTT interrupts. |
| **MediaSessionService** | Media3 1.5.0+ | Background playback | Foreground service type `mediaPlayback`. Auto-cleanup after 10min pause. Notification controls. |

**Audio routing priorities:**
1. Bluetooth SCO (if connected, startBluetoothSco() or setCommunicationDevice() for API 31+)
2. Wired headset (automatic via AudioManager)
3. Speaker (fallback)

**Audio focus strategy:**
- Request AUDIOFOCUS_GAIN_TRANSIENT when transmitting (PTT press)
- Request AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK when receiving (allow music to continue ducked)
- Automatic ducking available on Android 8+ (API 26, matches min SDK)

### State Management & Storage

| Technology | Version | Purpose | Rationale |
|------------|---------|---------|-----------|
| **EncryptedSharedPreferences** | Security 1.1.0-alpha06+ | JWT storage | Secure token persistence. Uses Android Keystore, AES256-GCM encryption. Simpler than manual DataStore + crypto. |
| **StateFlow / SharedFlow** | Coroutines 1.10.1+ | Reactive state | StateFlow for UI state (channel list, connection status). SharedFlow for events (PTT press, audio received). |

**JWT handling:**
- Store access token (1h TTL) in EncryptedSharedPreferences
- Retrofit Authenticator for automatic refresh on 401
- OkHttp Interceptor adds "Authorization: Bearer {token}" header

### Hardware Integration

| Technology | Version | Purpose | Rationale |
|------------|---------|---------|-----------|
| **KeyEvent API** | Android SDK | Volume/PTT button capture | onKeyDown/onKeyUp for KEYCODE_VOLUME_UP/DOWN. Bluetooth HID buttons appear as standard KeyEvents. |
| **BluetoothHeadset** | Android SDK | Bluetooth SCO audio | HSP/HFP profile for headset microphone. SCO connection for 8/16kHz mono audio routing. |

**Button capture pattern:**
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
            // PTT pressed
            viewModel.startPtt()
            true // consume event
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
```

**Bluetooth PTT compatibility:**
- Bluetooth HID buttons send KeyEvent (no special code needed)
- Headset call button (KEYCODE_HEADSETHOOK) can trigger PTT in toggle mode
- Third-party PTT buttons (Pryme, Flic, Seecode) work via HID or manufacturer SDKs

### Build System

| Technology | Version | Purpose | Rationale |
|------------|---------|---------|-----------|
| **Gradle** | 9.0.0 | Build system | Gradle 9 released Jan 2026. Embeds Kotlin 2.2.x runtime. |
| **Android Gradle Plugin** | 9.0.0 | Android build | AGP 9.0 includes built-in Kotlin support (KGP 2.2.10 bundled). |
| **Kotlin** | 2.3.10 | Language | Current stable (2.3.20 planned Mar-Apr 2026). AGP 9.0 requires KGP 2.2.10 minimum. |
| **Min SDK** | 26 (Android 8.0) | Minimum version | Matches project spec. Supports automatic audio ducking, Notification channels, EncryptedSharedPreferences. 89% device coverage. |
| **Target SDK** | 35 (Android 15) | Target version | Google Play requirement (Feb 2026). Apps must target API 35 for new submissions. |
| **Compile SDK** | 35 | Compile version | Match target SDK for latest APIs. |

## Integration Points with Existing Server

### 1. WebSocket Signaling Protocol

**Server endpoint:** `wss://{domain}/ws`

**Client implementation:**
```kotlin
val client = OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket needs infinite read timeout
    .build()

val request = Request.Builder()
    .url("wss://domain/ws")
    .addHeader("Authorization", "Bearer $jwtToken")
    .build()

val listener = object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
        // Parse JSON message, route to handler
        val message = gson.fromJson(text, SignalingMessage::class.java)
        handleMessage(message)
    }
}

client.newWebSocket(request, listener)
```

**Message format:** Same JSON protocol as web client
- `{ type: "join", channel: "channel-uuid" }`
- `{ type: "ptt-request", channel: "channel-uuid" }`
- Server responds with RTP capabilities, transport parameters, consumer data

### 2. mediasoup Device Initialization

**Pattern:**
1. WebSocket connects, client sends "join" for channel
2. Server responds with `routerRtpCapabilities`
3. Client creates mediasoup Device, loads capabilities
4. Client creates send/recv transports
5. Client produces audio track, consumes remote tracks

**libmediasoup-android API:**
```kotlin
// Device creation
val device = Device()
device.load(routerRtpCapabilities) // from server

// Transport creation
val sendTransport = device.createSendTransport(
    listener = transportListener,
    id = transportId, // from server
    iceParameters = iceParams,
    iceCandidates = iceCandidates,
    dtlsParameters = dtlsParams
)

// Audio production
val audioTrack = createAudioTrack() // WebRTC API
val producer = sendTransport.produce(
    listener = producerListener,
    track = audioTrack,
    encodings = null,
    codecOptions = null
)

// Audio consumption
val consumer = recvTransport.consume(
    listener = consumerListener,
    id = consumerId, // from server
    producerId = producerId,
    kind = "audio",
    rtpParameters = rtpParams
)
```

### 3. JWT Authentication

**Flow:**
1. Android client POSTs to `/api/auth/login` with credentials (Retrofit)
2. Server returns `{ token: "jwt", expiresIn: 3600 }`
3. Client stores token in EncryptedSharedPreferences
4. WebSocket connection adds `Authorization: Bearer {token}` header
5. Heartbeat every 30s refreshes permissions (handled by server)

**Retrofit interceptor:**
```kotlin
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider() ?: return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
```

### 4. Audio Codec Configuration

**Server configuration (from MEMORY.md):**
- Opus codec, 48kHz, CBR
- DTX disabled, FEC enabled
- mediasoup 3.19

**Android WebRTC audio:**
- GetStream webrtc-android supports Opus natively
- Configure AudioTrack with 48kHz, mono
- Disable software echo cancellation (causes latency)
- Enable hardware AEC if available

```kotlin
val audioConstraints = MediaConstraints().apply {
    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "false"))
    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
}
```

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| **mediasoup wrapper** | crow-misia/libmediasoup-android 0.21.0 | haiyangwu/mediasoup-client-android | Unmaintained since 2021. No Maven Central publishing. Harder to integrate. |
| **WebRTC library** | GetStream webrtc-android 1.3.9 | Build Google libwebrtc from source | Requires Linux build machine, 50GB+ storage, complex build process. GetStream provides maintained binaries. |
| **UI framework** | Jetpack Compose 1.10 | XML layouts | Compose is modern standard. Better for reactive PTT states. Material 3 components. Team familiarity (React-like). |
| **WebSocket client** | OkHttp 4.12.0 | Ktor WebSocket | Ktor is overkill for client-only usage. OkHttp is lighter, better Android integration, powers Retrofit anyway. |
| **DI framework** | Hilt 2.51.1 | Koin | Hilt is Google-recommended, compile-time safety. Koin is runtime, easier but less safe. Hilt standard for new projects. |
| **Foreground service** | Media3 MediaSessionService | Manual Service + MediaSession | Media3 automates notification, audio focus, lifecycle. Deprecated MediaSession APIs harder to manage. |
| **JSON parser** | Gson 2.11.0 | Moshi or kotlinx.serialization | Moshi adds reflection overhead. kotlinx.serialization needs compiler plugin. Gson is proven, matches server's approach. |
| **Secure storage** | EncryptedSharedPreferences | DataStore + manual AES | EncryptedSharedPreferences handles Keystore, encryption automatically. DataStore + crypto is more code for same result. |

## Dependencies (build.gradle.kts)

### Module-level build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.voiceping.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.voiceping.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15" // Matches Kotlin 2.3.10
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // WebRTC
    implementation("io.getstream:stream-webrtc-android:1.3.9")

    // mediasoup client
    implementation("io.github.crow-misia.libmediasoup-android:libmediasoup-android:0.21.0")

    // Media3 for foreground service
    implementation("androidx.media3:media3-session:1.5.0")
    implementation("androidx.media3:media3-common:1.5.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

### Project-level build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}
```

## Risks & Mitigations

### Risk 1: libmediasoupclient Android Compatibility

**Risk:** crow-misia wrapper may lag behind mediasoup server version (server is 3.19, wrapper is 0.21.0)

**Mitigation:**
- libmediasoupclient uses semantic versioning; 0.x indicates API instability but doesn't map to server version
- mediasoup protocol is version-negotiated via RTP capabilities exchange
- Test early: create minimal app that connects to existing server, loads capabilities, creates transport
- Fallback: If incompatible, evaluate haiyangwu fork or custom JNI wrapper

**Confidence:** MEDIUM - Need to verify in development, but protocol negotiation should handle version differences

### Risk 2: WebRTC Audio Latency

**Risk:** Android audio stack adds latency (20-200ms). Bluetooth SCO adds 100-200ms. PTT requires <500ms end-to-end.

**Mitigation:**
- Use OpenSL ES (low-level audio API) if available
- Disable software audio processing (AEC, AGC, NS)
- Test with real Bluetooth headsets early
- Configure Opus for low latency (frame size 10ms, complexity 4)
- Monitor with in-app latency indicators

**Confidence:** MEDIUM - Requires hardware testing, may need audio tuning phase

### Risk 3: Foreground Service Battery Drain

**Risk:** Continuous WebSocket + WebRTC connection drains battery. 8-hour shift = critical.

**Mitigation:**
- Use Media3's automatic foreground service transition (stops after 10min idle)
- Close recv transports for silent channels (only keep send transport ready)
- Implement "parking" mode: disconnect mediasoup, keep WebSocket for notifications
- Wake lock only during active PTT transmission
- Test battery drain: target <10% per hour in monitoring mode

**Confidence:** MEDIUM - Requires real-world testing with multi-hour sessions

### Risk 4: Bluetooth HID Button Diversity

**Risk:** Different Bluetooth PTT buttons send different KeyEvents. Some use proprietary SDKs.

**Mitigation:**
- Support common KeyEvents: VOLUME_DOWN, VOLUME_UP, HEADSETHOOK, MEDIA_NEXT, MEDIA_PREVIOUS
- Add settings screen: "Capture any button" mode to detect and map custom KeyEvents
- Document compatible hardware (Pryme PTT-Z, Flic, Seecode SHP, TWAYRDIO)
- Fallback: On-screen PTT button always available

**Confidence:** HIGH - Flexibility in mapping handles most cases

### Risk 5: Android Fragmentation

**Risk:** Different OEMs modify AudioManager, Bluetooth stack. Samsung, Xiaomi, Huawei have custom behaviors.

**Mitigation:**
- Min SDK 26 reduces fragmentation (89% devices, avoids Android 6-7 Bluetooth issues)
- Test on 3+ OEM devices (Samsung, Google Pixel, OnePlus/Oppo)
- Use compat libraries (Media3, AndroidX)
- Add diagnostic logs: audio routing, Bluetooth state, device info
- Community feedback: Beta program with field workers on diverse devices

**Confidence:** MEDIUM - Known Android challenge, requires broad testing

### Risk 6: WebSocket Reconnection in Poor Network

**Risk:** Field workers may have spotty LTE/5G. WebSocket drops, mediasoup state lost.

**Mitigation:**
- Exponential backoff reconnection (1s, 2s, 4s, 8s, 16s, cap at 30s)
- Persist channel list locally (Room database or DataStore)
- Auto-rejoin last channels on reconnect
- Show connection status indicator (connected/reconnecting/offline)
- Graceful degradation: queue PTT presses, send when reconnected (if <5s old)

**Confidence:** HIGH - Pattern is well-known, OkHttp handles connection pooling

### Risk 7: Kotlin/AGP Version Churn

**Risk:** AGP 9.0 just released (Jan 2026), may have bugs. Kotlin 2.3.10 stable but 2.4.0 coming June 2026.

**Mitigation:**
- Pin versions explicitly (don't use `+` or `latest`)
- AGP 9.0 is stable release, not alpha/beta
- Kotlin 2.3.10 is stable, tested with Compose 1.10
- Subscribe to Android Developers Blog for critical updates
- If AGP 9.0 issues: downgrade to AGP 8.7.x (supports Kotlin 2.3.x)

**Confidence:** HIGH - AGP 9.0 went through beta cycle, production-ready

## Installation Steps

### 1. Android Studio Setup

```bash
# Install Android Studio Ladybug (2025.1) or later
# Includes Gradle 9.0, Kotlin 2.3.10, AGP 9.0 support

# SDK Manager: Install
# - Android SDK Platform 35 (Android 15)
# - Android SDK Build-Tools 35.0.0
# - Android Emulator (for testing)
```

### 2. Create Project

```bash
# Android Studio: New Project → Empty Activity (Compose)
# Language: Kotlin
# Minimum SDK: API 26 (Android 8.0)
# Build configuration language: Kotlin DSL

# Update build.gradle.kts files with dependencies above
```

### 3. Add JitPack (if needed for unofficial libraries)

**settings.gradle.kts:**
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // If using JitPack libraries
    }
}
```

### 4. Configure ProGuard (for mediasoup native library)

**proguard-rules.pro:**
```proguard
# Keep mediasoup JNI classes
-keep class org.mediasoup.** { *; }
-keepclassmembers class org.mediasoup.** { *; }

# Keep WebRTC classes
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }

# Keep Gson models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.voiceping.android.data.model.** { *; }
```

### 5. Permissions (AndroidManifest.xml)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Audio -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <!-- Bluetooth -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" android:maxSdkVersion="30" />

    <!-- Foreground service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" android:minSdkVersion="33" />

    <!-- Wake lock (for PTT) -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
</manifest>
```

## Sources

### WebRTC & mediasoup
- [mediasoup Android/iOS native client discussion](https://mediasoup.discourse.group/t/android-ios-native-client-support/4298)
- [crow-misia/libmediasoup-android GitHub](https://github.com/crow-misia/libmediasoup-android)
- [libmediasoup-android Maven Central](https://central.sonatype.com/artifact/io.github.crow-misia.libmediasoup-android/libmediasoup-android)
- [GetStream webrtc-android GitHub](https://github.com/GetStream/webrtc-android)
- [WebRTC Android official docs](https://webrtc.github.io/webrtc-org/native-code/android/)

### Android Networking
- [OkHttp comprehensive guide](https://www.oreateai.com/blog/comprehensive-guide-to-the-application-of-okhttp-in-android-development/cdfeaf9886a2f917f525b71c991a2554)
- [Android WebSockets with Kotlin](https://bugfender.com/blog/android-websockets/)
- [Retrofit and OkHttp networking](https://androshelf.com/blogs/retrofit-okhttp-android-networking.html)
- [Retrofit official docs](https://square.github.io/retrofit/)

### Android Framework
- [Jetpack Compose December '25 release](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html)
- [Material 3 for Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Hilt dependency injection](https://developer.android.com/training/dependency-injection/hilt-android)
- [WorkManager overview](https://developer.android.com/develop/background-work/background-tasks/persistent)

### Audio & Bluetooth
- [Background playback with MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
- [Android audio focus management](https://developer.android.com/media/optimize/audio-focus)
- [Bluetooth SCO audio routing](http://gopinaths.gitlab.io/post/bluetooth_sco_android/)
- [AudioManager.startBluetoothSco()](https://learn.microsoft.com/en-us/dotnet/api/android.media.audiomanager.startbluetoothsco)

### Hardware Integration
- [Volume button KeyEvent handling](https://www.geeksforgeeks.org/android/how-to-listen-for-volume-button-and-back-key-events-programmatically-in-android/)
- [Bluetooth PTT button pairing (Zello guide)](https://support.zello.com/hc/en-us/articles/230745407-Pairing-a-Bluetooth-PTT-Button-Android)
- [KeyEvent API reference](https://developer.android.com/reference/android/view/KeyEvent)

### Build System & Security
- [AGP 9.0.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Kotlin 2.3.x updates](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
- [Android target SDK requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [JWT authentication in Android](https://medium.com/@sanjaykushwaha_58217/jwt-authentication-in-android-a-step-by-step-guide-d0dd768cb21a)
- [Secure token storage with EncryptedSharedPreferences](https://medium.com/@mohammad.hasan.mahdavi81/securely-storing-jwt-tokens-in-android-with-datastore-and-manual-encryption-741b104a93d3)

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| **WebRTC/mediasoup** | MEDIUM | crow-misia wrapper actively maintained, but need to verify server compatibility. WebSearch-verified with Maven Central. |
| **Networking** | HIGH | OkHttp + Retrofit is industry standard. Version 4.12.0 confirmed via WebSearch. |
| **Android Framework** | HIGH | Compose 1.10, Material 3 1.4 stable per official Android blog (Dec 2025). AGP 9.0 released Jan 2026. |
| **Audio Management** | MEDIUM | AudioManager/MediaSessionService APIs verified. Bluetooth SCO and latency need hardware testing. |
| **Hardware Integration** | MEDIUM | KeyEvent API is standard. Bluetooth PTT button diversity requires testing with real devices. |
| **Build System** | HIGH | AGP 9.0 + Kotlin 2.3.10 compatibility confirmed via official JetBrains blog. |

**Overall confidence: MEDIUM** - Core stack is proven (OkHttp, Compose, Hilt), but mediasoup Android wrapper and audio latency require validation in development. Recommend early prototype to verify mediasoup integration.

## Next Steps for Roadmap

**Phase prioritization based on stack:**

1. **Foundation Phase** - Verify mediasoup integration
   - Minimal app: Connect to server, load RTP capabilities, create transport
   - Proves crow-misia wrapper compatibility
   - De-risks critical technical assumption

2. **Networking Phase** - WebSocket + JWT
   - Standard pattern (OkHttp + Retrofit)
   - Low risk, well-documented

3. **Audio Phase** - MediaSessionService + AudioManager
   - Bluetooth SCO routing needs hardware testing
   - Foreground service is straightforward

4. **UI Phase** - Compose + Material 3
   - Stable APIs, low risk
   - Can parallelize with audio work

5. **Hardware Phase** - PTT buttons
   - Requires physical devices (Bluetooth PTT buttons)
   - Iterate based on device testing

**Research flags:**
- Phase 1 (Foundation): May need deeper research if mediasoup wrapper incompatible (custom JNI or fork)
- Phase 3 (Audio): May need research on low-latency audio (OpenSL ES, Oboe library)
- Phase 5 (Hardware): May need manufacturer SDKs for proprietary PTT buttons
