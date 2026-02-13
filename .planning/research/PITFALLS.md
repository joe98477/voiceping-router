# Domain Pitfalls: mediasoup-android Integration

**Domain:** Real-time WebRTC audio communication (PTT) on Android
**Context:** Adding libmediasoup-android to existing app with custom AudioManager controls
**Researched:** 2026-02-13

## Critical Pitfalls

These mistakes cause app crashes, audio failures, or require complete rewrites.

### Pitfall 1: Dual AudioManager Control (WebRTC vs Application)

**What goes wrong:**
WebRTC's `PeerConnectionFactory` internally manages `AudioManager` (sets MODE_IN_COMMUNICATION, controls hardware echo cancellation, manages AudioRecord/AudioTrack). Your existing `AudioRouter` also controls `AudioManager` (sets MODE_IN_COMMUNICATION, manages speakerphone, Bluetooth SCO). When both systems try to control audio simultaneously, you get:
- AUDIO_RECORD_START_STATE_MISMATCH errors
- Recording failures: "Can only have one active PC/ADM in WebRTC on Android"
- Echo issues when hardware AEC is disabled/enabled by conflicting settings
- Speakerphone routing ignored or fighting between WebRTC and app code

**Why it happens:**
WebRTC creates `JavaAudioDeviceModule` which wraps `android.media.AudioRecord` and `android.media.AudioTrack`. This ADM automatically sets MODE_IN_COMMUNICATION when starting. If your `AudioRouter.requestAudioFocus()` or `setEarpieceMode()` also sets MODE_IN_COMMUNICATION, you have two systems fighting for the same AudioManager state.

**Consequences:**
- Microphone access fails (only one AudioRecord allowed)
- Audio routing becomes unpredictable
- Bluetooth SCO conflicts between WebRTC's internal management and your `AudioRouter.setBluetoothMode()`
- Silent failures where audio appears to work but is routed incorrectly

**Prevention:**

**Option A: Let WebRTC own AudioManager (RECOMMENDED)**
1. **Remove AudioRouter's MODE_IN_COMMUNICATION management** - WebRTC's PeerConnectionFactory will handle this
2. **Keep AudioRouter for routing only** - Use it to set speakerphone/Bluetooth routing AFTER WebRTC initializes
3. **Use AudioDeviceModule builder for configuration**:
```kotlin
val audioDeviceModule = JavaAudioDeviceModule.builder(context)
    .setUseHardwareAcousticEchoCanceler(true)
    .setUseHardwareNoiseSuppressor(true)
    .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
        override fun onWebRtcAudioRecordInitError(errorMessage: String) {
            Log.e(TAG, "AudioRecord init error: $errorMessage")
        }
        override fun onWebRtcAudioRecordStartError(
            errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode,
            errorMessage: String
        ) {
            Log.e(TAG, "AudioRecord start error: $errorCode - $errorMessage")
        }
        override fun onWebRtcAudioRecordError(errorMessage: String) {
            Log.e(TAG, "AudioRecord error: $errorMessage")
        }
    })
    .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
        override fun onWebRtcAudioTrackInitError(errorMessage: String) {
            Log.e(TAG, "AudioTrack init error: $errorMessage")
        }
        override fun onWebRtcAudioTrackStartError(
            errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode,
            errorMessage: String
        ) {
            Log.e(TAG, "AudioTrack start error: $errorCode - $errorMessage")
        }
        override fun onWebRtcAudioTrackError(errorMessage: String) {
            Log.e(TAG, "AudioTrack error: $errorMessage")
        }
    })
    .createAudioDeviceModule()

val peerConnectionFactory = PeerConnectionFactory.builder()
    .setAudioDeviceModule(audioDeviceModule)
    .createPeerConnectionFactory()
```

4. **Coordinate wake lock management** - WebRTC sets wake locks during audio playout. Your `ChannelMonitoringService` wake lock should be for service lifecycle, not audio.

**Option B: Disable WebRTC's internal audio management**
- Create custom AudioDeviceModule that doesn't touch AudioManager
- Let your AudioRouter fully control AudioManager
- More complex, requires deep WebRTC knowledge

**Detection:**
- Log messages: "AUDIO_RECORD_START_STATE_MISMATCH"
- AudioRecord.startRecording() throws IllegalStateException
- Speakerphone state flips unexpectedly during calls
- Bluetooth SCO starts/stops erratically

**Which phase addresses this:**
Phase 1 (WebRTC Integration Setup) - Must design AudioManager ownership upfront. Refactor AudioRouter to delegate MODE_IN_COMMUNICATION to WebRTC.

**Sources:**
- [WebRTC AudioManager conflicts (Google Groups)](https://groups.google.com/g/discuss-webrtc/c/Pqag6R7QV2c)
- [Multiple AudioDeviceModule issue (Chromium bugs)](https://bugs.chromium.org/p/webrtc/issues/detail?id=2498)
- [AudioDeviceModule API guidance](https://github.com/maitrungduc1410/webrtc/blob/master/modules/audio_device/g3doc/audio_device_module.md)

---

### Pitfall 2: Native Callbacks on Wrong Thread (JNI Threading)

**What goes wrong:**
mediasoup-android uses JNI to bridge C++ libmediasoupclient to Kotlin. Native callbacks (Transport.Listener, Producer.Listener, Consumer.Listener) are invoked from native threads, NOT the main/UI thread. If you:
- Update UI directly from callbacks → crashes with "Only the original thread that created a view hierarchy can touch its views"
- Access Kotlin coroutines without proper dispatcher → race conditions
- Hold references to Activity/Fragment in listeners → memory leaks (JNI global refs not released)

**Why it happens:**
JNI threads don't have a Looper and can't interact with Android UI. The native code calls Java methods via `AttachCurrentThread()`, executes the callback, then may `DetachCurrentThread()`. Your Kotlin code runs in this native thread context.

**Consequences:**
- UI updates crash: `CalledFromWrongThreadException`
- Race conditions accessing shared state (e.g., `_pttState.value` in PttManager)
- Memory leaks if you store global refs to contexts/activities in listener lambdas
- Deadlocks if callback tries to acquire locks held by main thread

**Prevention:**

1. **Never access UI from listeners**:
```kotlin
// BAD
transport.on("connect") { callback ->
    // This runs on native thread!
    textView.text = "Connected" // CRASHES
}

// GOOD
transport.on("connect") { callback ->
    scope.launch(Dispatchers.Main) {
        textView.text = "Connected"
    }
}
```

2. **Use appropriate coroutine dispatchers**:
```kotlin
class MediasoupManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sendTransportListener = object : SendTransport.Listener {
        override fun onConnect(transport: Transport, dtlsParameters: String) {
            // Native thread - dispatch immediately to IO for network calls
            scope.launch(Dispatchers.IO) {
                val response = signalingClient.request(
                    SignalingType.CONNECT_SEND_TRANSPORT,
                    mapOf("dtlsParameters" to dtlsParameters)
                )
                // Callback must be called on same thread as listener
                if (response.error == null) {
                    callback.onSuccess()
                } else {
                    callback.onFailure(response.error)
                }
            }
        }

        override fun onProduce(
            transport: Transport,
            kind: String,
            rtpParameters: String,
            appData: String,
            callback: SendTransport.ProduceCallback
        ) {
            scope.launch(Dispatchers.IO) {
                val response = signalingClient.request(
                    SignalingType.PRODUCE,
                    mapOf("kind" to kind, "rtpParameters" to rtpParameters)
                )
                val producerId = response.data?.get("id") as? String
                if (producerId != null) {
                    callback.onSuccess(producerId)
                } else {
                    callback.onFailure(response.error ?: "No producer ID")
                }
            }
        }
    }
}
```

3. **Avoid storing Activity/Fragment refs in listeners**:
```kotlin
// BAD - leaks activity
class MyActivity : AppCompatActivity() {
    private val listener = object : Consumer.Listener {
        override fun onTransportClose(consumer: Consumer) {
            this@MyActivity.finish() // LEAKS via JNI global ref
        }
    }
}

// GOOD - use weak ref or lifecycle-aware component
class MediasoupRepository @Inject constructor() {
    private val listeners = mutableListOf<WeakReference<TransportListener>>()
}
```

4. **JNIEnv is thread-local - don't share across threads**:
- Each native callback gets its own JNIEnv
- Don't cache JNIEnv pointers
- If spawning threads from JNI, attach them with AttachCurrentThread

**Detection:**
- Crashes: `android.view.ViewRootImpl$CalledFromWrongThreadException`
- Log messages about thread violations
- Memory leaks detected by LeakCanary showing JNI global refs to Activities
- Intermittent race conditions that don't reproduce consistently

**Which phase addresses this:**
Phase 1 (WebRTC Integration Setup) - Establish threading patterns for all listeners upfront.

**Sources:**
- [JNI threading tips (Android NDK)](https://developer.android.com/training/articles/perf-jni)
- [JNI multithreading guide](https://clintpaul.medium.com/jni-on-android-how-callbacks-work-c350bf08157f)
- [mediasoup Transport listener docs](https://mediasoup.discourse.group/t/transport-on-connect-not-triggring-the-event/4497)

---

### Pitfall 3: Incomplete Cleanup Causes Memory Leaks

**What goes wrong:**
mediasoup objects (Device, Transport, Producer, Consumer) hold native memory via JNI. If you don't explicitly close/dispose them in correct order, you leak:
- ~30MB per unclosed transport/producer/consumer (iOS reports, likely similar on Android)
- Native WebRTC threads keep running
- AudioRecord/AudioTrack remain active → microphone stays captured
- Event listeners accumulate → multiple callbacks fire for same event

**Why it happens:**
Java GC doesn't know about native memory. Even if Kotlin object is garbage collected, the underlying C++ object persists until you call `.close()`. mediasoup has strict lifecycle rules: must close consumers before transport, producers before transport, transport before device.

**Consequences:**
- Out of memory crashes after repeated connect/disconnect cycles
- Microphone remains captured preventing other apps from using it
- Battery drain from zombie threads
- Multiple event handlers fire → duplicate audio playback

**Prevention:**

1. **Follow cleanup hierarchy**:
```kotlin
// Cleanup order: Consumers → Producers → Transports → Device

// Step 1: Close all consumers
recvTransport?.consumers?.forEach { consumer ->
    consumer.close()
}

// Step 2: Close all producers
sendTransport?.producer?.close()

// Step 3: Close transports
recvTransport?.close()
sendTransport?.close()

// Step 4: ONLY if completely done with mediasoup
device?.dispose() // Rare - usually keep device for reconnects
```

2. **Listen for transportclose events**:
```kotlin
val consumerListener = object : Consumer.Listener {
    override fun onTransportClose(consumer: Consumer) {
        // Transport closed → consumer auto-closed
        // Remove from collection, don't call close() again
        consumers.remove(consumer.id)
    }
}
```

3. **Use try-finally for cleanup**:
```kotlin
suspend fun disconnect() {
    try {
        // Stop producing first (stops audio capture)
        producer?.close()
        producer = null

        // Then close transport
        sendTransport?.close()
        sendTransport = null
    } catch (e: Exception) {
        Log.e(TAG, "Error during cleanup", e)
    } finally {
        // Even if close() throws, ensure state is reset
        isConnected = false
    }
}
```

4. **Track object lifecycle in collections**:
```kotlin
class MediasoupClient {
    private val consumers = ConcurrentHashMap<String, Consumer>()

    fun addConsumer(consumer: Consumer) {
        consumers[consumer.id] = consumer
        consumer.on("transportclose") {
            consumers.remove(consumer.id)
        }
    }

    suspend fun cleanup() {
        consumers.values.forEach { it.close() }
        consumers.clear()
    }
}
```

5. **Don't rely on finalize() or GC**:
```kotlin
// BAD - GC timing is unpredictable
class TransportWrapper(private val transport: Transport) {
    protected fun finalize() {
        transport.close() // May never be called!
    }
}

// GOOD - explicit lifecycle
class TransportWrapper(private val transport: Transport) : Closeable {
    override fun close() {
        transport.close()
    }
}
```

**Detection:**
- Memory profiler shows native heap growing on repeated connect/disconnect
- LeakCanary reports Transport/Producer/Consumer leaks
- Microphone stays active after leaving channel (notification LED on)
- Log messages about multiple listener invocations

**Which phase addresses this:**
Phase 2 (Transport Lifecycle Management) - Design cleanup sequences for reconnection, disconnection, and error recovery.

**Sources:**
- [mediasoup garbage collection docs](https://mediasoup.org/documentation/v3/mediasoup/garbage-collection/)
- [mediasoup iOS memory leak report (30MB)](https://github.com/ethand91/mediasoup-ios-client/issues/55)
- [Transport/Consumer close events](https://mediasoup.discourse.group/t/observer-events-and-know-if-producers-or-consumers-closed-abruptly/2916)

---

### Pitfall 4: AGP 9.0 Breaks NDK in Library Modules

**What goes wrong:**
Android Gradle Plugin 9.0 **disallows NDK execution in library modules**. If you structure your project with mediasoup code in a separate `:mediasoup` library module, build fails with: "NDK execution in library modules and C++ code execution and JNI will not be supported at all since AGP 9.0".

libmediasoup-android is a native library (C++ with JNI). If integrated incorrectly, builds break on AGP 9.0+.

**Why it happens:**
AGP 9.0 policy: native code (CMake, ndk-build) must live in application module, not library modules. This breaks multi-module architectures where you isolate WebRTC/mediasoup in separate module.

**Consequences:**
- Build fails on AGP 9.0+ if mediasoup code in library module
- Forced to move all mediasoup code to `:app` module → loss of modularity
- CI/CD breaks if you upgrade AGP without restructuring

**Prevention:**

1. **Keep mediasoup in application module from day one**:
```
android/
  app/
    src/main/java/com/voiceping/android/
      data/
        network/
          MediasoupClient.kt  ← Here, not in separate module
```

2. **If you need modularity, use pure Kotlin wrapper**:
```
android/
  mediasoup-wrapper/  ← Pure Kotlin/Java library module (no NDK)
    src/main/java/
      MediasoupRepository.kt
  app/  ← Contains actual mediasoup-android dependency
    build.gradle.kts:
      dependencies {
        implementation("io.github.crow-misia.libmediasoup-android:libmediasoup-android:0.21.0")
        implementation(project(":mediasoup-wrapper"))
      }
```

3. **Verify AGP compatibility**:
```kotlin
// android/gradle/libs.versions.toml
[versions]
agp = "9.0.0"  # Confirm compatible with libmediasoup-android

// Check libmediasoup-android release notes for AGP compatibility
```

4. **NDK version compatibility**:
- NDK r26: No 16KB page size support
- NDK r27: Supports flexible page sizes (required for some newer devices)
- Ensure libmediasoup-android built with compatible NDK version

**Detection:**
- Build error: "NDK execution in library modules... not supported"
- Gradle sync fails after AGP upgrade to 9.0+

**Which phase addresses this:**
Phase 0 (Pre-Integration Planning) - Architecture decisions before writing code.

**Sources:**
- [AGP 9.0 migration guide (NDK restrictions)](https://nek12.dev/blog/en/agp-9-0-migration-guide-android-gradle-plugin-9-kmp-migration-kotlin)
- [NDK r27 flexible page sizes](https://github.com/android/ndk/wiki/Changelog-r27)

---

### Pitfall 5: Race Conditions During Reconnection

**What goes wrong:**
Network disconnects while PTT active. Your code tries to reconnect while mediasoup is still tearing down the previous transport. You get:
- Duplicate producers (old producer not closed, new one created)
- Transport.connect() called on closed transport
- State inconsistency: `PttManager` thinks transmitting, but transport is closed

**Why it happens:**
Async operations overlap: disconnect cleanup coroutine still running while reconnect coroutine starts. mediasoup operations like `producer.pause()` don't block, so calling `transport.pipeToRouter()` immediately after can read stale state.

**Consequences:**
- Producer/consumer count grows on every reconnect → memory leak
- "Transport already closed" exceptions
- Audio stops working after reconnect (producer paused but consumer.producerPaused still false)
- PTT button unresponsive after network recovery

**Prevention:**

1. **Use state machine with atomic transitions**:
```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Reconnecting : ConnectionState()  // Key: separate state for reconnect
}

class MediasoupClient {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val stateMutex = Mutex()

    suspend fun reconnect() {
        stateMutex.withLock {
            // Prevent concurrent reconnects
            if (_connectionState.value == ConnectionState.Reconnecting) {
                Log.w(TAG, "Already reconnecting, ignoring")
                return
            }
            _connectionState.value = ConnectionState.Reconnecting
        }

        // Cleanup old connection
        cleanup()

        // Wait for cleanup to complete (join on coroutines)
        cleanupJob?.join()

        // Now safe to create new transport
        connect()
    }
}
```

2. **Cancel ongoing operations before reconnect**:
```kotlin
class PttManager {
    private var transmissionJob: Job? = null

    suspend fun handleReconnection() {
        // Cancel ongoing transmission
        transmissionJob?.cancelAndJoin()  // Wait for cancellation

        // Force state to Idle
        _pttState.value = PttState.Idle

        // Now safe to reconnect
        mediasoupClient.reconnect()
    }
}
```

3. **Wait for producer.pause/resume to complete**:
```kotlin
// BAD - race condition
producer.pause()
transport.pipeToRouter(...)  // May see stale producer.paused state

// GOOD - documented pattern
producer.pause()
// mediasoup operations complete synchronously (blocking current thread)
// Safe to proceed immediately after call returns
transport.pipeToRouter(...)
```

4. **Handle transportclose events**:
```kotlin
val producerListener = object : Producer.Listener {
    override fun onTransportClose(producer: Producer) {
        // Transport closed unexpectedly (network error, server restart)
        Log.w(TAG, "Producer's transport closed, triggering reconnect")

        // Clean up immediately
        _pttState.value = PttState.Idle
        producer.close()  // Safe even though already closed

        // Trigger reconnect
        scope.launch {
            delay(1000)  // Backoff before reconnect
            reconnect()
        }
    }
}
```

5. **Implement exponential backoff**:
```kotlin
class ReconnectionManager {
    private var reconnectAttempts = 0

    suspend fun reconnect() {
        val delay = min(1000L * (2.0.pow(reconnectAttempts).toLong()), 30000L)
        Log.d(TAG, "Reconnecting in ${delay}ms (attempt ${reconnectAttempts + 1})")

        delay(delay)

        try {
            mediasoupClient.connect()
            reconnectAttempts = 0  // Reset on success
        } catch (e: Exception) {
            reconnectAttempts++
            if (reconnectAttempts < 5) {
                reconnect()  // Retry
            } else {
                Log.e(TAG, "Max reconnect attempts reached")
            }
        }
    }
}
```

**Detection:**
- Multiple producers/consumers for same channel in logs
- IllegalStateException: "Transport is closed"
- Inconsistent state: UI shows transmitting, but no audio flows
- Memory growth during network flapping

**Which phase addresses this:**
Phase 3 (Network Resilience & Reconnection) - Design reconnection state machine with race condition guards.

**Sources:**
- [mediasoup reconnection handling](https://mediasoup.discourse.group/t/recording-reconnection-handling/4907)
- [Race condition example (pause/resume + pipeToRouter)](https://mediasoup.discourse.group/t/observer-events-and-know-if-producers-or-consumers-closed-abruptly/2916)
- [Transport connection state disconnected](https://github.com/Blancduman/mediasoup-client-flutter/issues/36)

---

## Moderate Pitfalls

These cause bugs or poor UX, but not catastrophic failures.

### Pitfall 6: ProGuard/R8 Strips JNI Methods

**What goes wrong:**
Release build crashes with `NoSuchMethodError` or `UnsatisfiedLinkError` when calling mediasoup methods. ProGuard/R8 obfuscates/removes JNI methods because it doesn't detect they're called from native code.

**Prevention:**

1. **Add consumer ProGuard rules** (libmediasoup-android should provide these, but verify):
```proguard
# Keep all mediasoup classes and JNI methods
-keep class org.mediasoup.droid.** { *; }
-keepclassmembers class org.mediasoup.droid.** {
    native <methods>;
}

# Keep WebRTC classes used by mediasoup
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** {
    native <methods>;
}
```

2. **Check AAR for consumer-rules.pro**:
```bash
# Verify libmediasoup-android AAR includes ProGuard rules
unzip -l libmediasoup-android-0.21.0.aar | grep proguard
# Should see: META-INF/proguard/consumer-proguard-rules.pro
```

3. **Test release builds early**:
```bash
./gradlew assembleRelease
adb install android/app/build/outputs/apk/release/app-release.apk
# Test PTT immediately
```

**Detection:**
- Release build crashes, debug build works
- `NoSuchMethodError` for native methods
- Logcat: "UnsatisfiedLinkError: No implementation found for..."

**Which phase addresses this:**
Phase 4 (Release Build Testing) - Test ProGuard configuration with actual release builds.

**Sources:**
- [ProGuard consumer rules guide](https://drjansari.medium.com/mastering-proguard-in-android-multi-module-projects-agp-8-4-r8-and-consumable-rules-ae28074b6f1f)
- [ProGuard troubleshooting (Android Developers)](https://medium.com/androiddevelopers/troubleshooting-proguard-issues-on-android-bce9de4f8a74)

---

### Pitfall 7: Device.load() Codec Compatibility Issues

**What goes wrong:**
`device.load(routerRtpCapabilities)` succeeds on test devices but fails on production devices (Huawei, Samsung). Some devices support H.264 decode but not encode, causing asymmetric codec negotiation. Audio-only apps get video codecs in RTP capabilities, audio codecs missing.

**Prevention:**

1. **Validate RTP capabilities after load**:
```kotlin
try {
    device.load(routerRtpCapabilities)

    val caps = device.getRtpCapabilities()
    val hasOpus = caps.codecs.any { it.mimeType.equals("audio/opus", ignoreCase = true) }

    if (!hasOpus) {
        Log.e(TAG, "Device does not support Opus codec")
        // Fallback: request different codec from server or show error
    }
} catch (e: Exception) {
    Log.e(TAG, "Failed to load device capabilities", e)
    // Handle: maybe device WebRTC support is broken
}
```

2. **Test on wide device range**:
- Huawei devices (many lack H.264 encode)
- Samsung Galaxy A series (low-end)
- Older devices (API 26-28)

3. **Server-side codec fallback**:
```typescript
// Server should offer multiple codecs
router.rtpCapabilities.codecs = [
  { kind: 'audio', mimeType: 'audio/opus', clockRate: 48000, channels: 2 },
  { kind: 'audio', mimeType: 'audio/PCMU', clockRate: 8000, channels: 1 },  // Fallback
]
```

4. **Handle load() errors gracefully**:
```kotlin
suspend fun initializeDevice(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val response = signalingClient.request(SignalingType.GET_ROUTER_RTP_CAPABILITIES)
        val capsJson = response.data?.get("rtpCapabilities") as? String
            ?: return@withContext Result.failure(Exception("No RTP capabilities"))

        device.load(capsJson)
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Device load failed", e)
        Result.failure(e)
    }
}
```

**Detection:**
- TypeError: "caps is not an object" during device.load()
- RTP capabilities missing expected codecs
- Crash reports from specific device models (Huawei, Samsung)

**Which phase addresses this:**
Phase 1 (WebRTC Integration Setup) - Device initialization with error handling.
Phase 5 (Device Compatibility Testing) - Test on diverse hardware.

**Sources:**
- [Device.load() codec issues on Android](https://github.com/haiyangwu/mediasoup-client-android/issues/9)
- [Chrome Android RTP capabilities bug](https://mediasoup.discourse.group/t/weird-issue-with-chrome-android-and-rtpcapabilities-after-device-load/1537)
- [Huawei H.264 encode limitation](https://github.com/versatica/mediasoup-client/issues/141)

---

### Pitfall 8: Wake Lock Conflicts with WebRTC

**What goes wrong:**
Your `ChannelMonitoringService` holds PARTIAL_WAKE_LOCK. WebRTC's AudioTrack also holds wake lock during playout. Both wake locks active simultaneously drains battery excessively. User reports "app uses 50% battery in 2 hours".

**Prevention:**

1. **Let WebRTC manage audio wake locks**:
```kotlin
class ChannelMonitoringService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        // ONLY acquire wake lock for non-audio tasks (e.g., keeping service alive)
        // WebRTC handles audio wake lock automatically
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VoicePing::ServiceKeepAlive"  // NOT for audio
        )
    }

    fun onAudioStarted() {
        // Release our wake lock - let WebRTC handle it
        wakeLock?.release()
    }

    fun onAudioStopped() {
        // Re-acquire if service should stay alive
        if (shouldKeepServiceAlive()) {
            wakeLock?.acquire(10 * 60 * 1000L)  // 10 min timeout
        }
    }
}
```

2. **Monitor wake lock usage**:
```bash
# Check wake locks held by app
adb shell dumpsys power | grep -A 10 "com.voiceping.android"

# Battery stats
adb shell dumpsys batterystats --charged com.voiceping.android
```

3. **Use wake lock best practices**:
- Set timeout: `wakeLock.acquire(timeout)`
- Release in onDestroy() even if exception thrown
- Use foreground service + notification (Android 11+ requirement)

**Detection:**
- Battery drain reports from users
- Android Vitals: "Excessive wake locks"
- Multiple wake locks shown in dumpsys power

**Which phase addresses this:**
Phase 2 (Audio Integration) - Coordinate wake lock strategy with WebRTC.

**Sources:**
- [WebRTC wake lock behavior](https://groups.google.com/g/discuss-webrtc/c/CHG9ndvMN7M)
- [Wake lock best practices (Android Developers)](https://developer.android.com/develop/background-work/background-tasks/scheduling/wakelock)

---

## Minor Pitfalls

These cause inconvenience but are easily fixed.

### Pitfall 9: Listener Registration Leaks

**What goes wrong:**
You register Transport.Listener but never unregister. Listener accumulates across reconnects. After 5 reconnects, each transport event triggers 5 callbacks.

**Prevention:**
```kotlin
class MediasoupClient {
    private val listeners = mutableMapOf<String, Transport.Listener>()

    fun createTransport(direction: String) {
        // Remove old listener if exists
        listeners[direction]?.let { oldListener ->
            transport?.removeListener(oldListener)
        }

        val listener = object : Transport.Listener {
            override fun onConnect(...) { ... }
        }

        transport.setListener(listener)
        listeners[direction] = listener
    }

    fun cleanup() {
        listeners.forEach { (_, listener) ->
            transport?.removeListener(listener)
        }
        listeners.clear()
    }
}
```

**Which phase addresses this:**
Phase 2 (Transport Lifecycle Management)

---

### Pitfall 10: Missing LibraryLoader.initialize()

**What goes wrong:**
First mediasoup call crashes with `UnsatisfiedLinkError: couldn't find DSO to load: libjingle_peerconnection_so.so`.

**Prevention:**
```kotlin
class VoicePingApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Load WebRTC native library BEFORE any mediasoup calls
        System.loadLibrary("jingle_peerconnection_so")

        // Initialize mediasoup
        mediasoupclient.Initialize()
    }
}
```

**Which phase addresses this:**
Phase 1 (WebRTC Integration Setup) - Application initialization.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Phase 1: WebRTC Integration Setup | Pitfall 1 (AudioManager conflicts), Pitfall 2 (JNI threading) | Design AudioManager ownership, establish listener threading patterns |
| Phase 2: Transport Lifecycle Management | Pitfall 3 (memory leaks), Pitfall 5 (race conditions) | Implement cleanup hierarchy, use state machine with mutex |
| Phase 2: Audio Integration | Pitfall 1 (AudioManager conflicts), Pitfall 8 (wake lock conflicts) | Refactor AudioRouter, coordinate wake locks |
| Phase 3: Network Resilience | Pitfall 5 (race conditions during reconnection) | Atomic state transitions, cancel ongoing ops before reconnect |
| Phase 4: Release Build Testing | Pitfall 6 (ProGuard strips JNI) | Add consumer ProGuard rules, test release builds |
| Phase 5: Device Compatibility Testing | Pitfall 7 (Device.load() codec issues) | Test on Huawei/Samsung devices, validate RTP caps |

---

## Research Confidence Assessment

| Area | Confidence | Basis |
|------|------------|-------|
| AudioManager conflicts | HIGH | Multiple WebRTC official discussions, documented ADM conflicts, your existing AudioRouter code analysis |
| JNI threading | HIGH | Android NDK official docs, JNI threading guides, mediasoup architecture (C++ + JNI) |
| Memory leaks | MEDIUM | mediasoup iOS reports (30MB), mediasoup docs on garbage collection, but Android-specific numbers unverified |
| AGP 9.0 NDK restrictions | HIGH | Official AGP 9.0 migration guide, your project already on AGP 9.0.0 |
| Race conditions | MEDIUM | mediasoup discourse examples, general async pattern knowledge, but mediasoup-android specific examples limited |
| ProGuard issues | MEDIUM | General Android ProGuard knowledge, consumer rules best practices, but no mediasoup-android specific ProGuard rules found |
| Device.load() issues | MEDIUM | Multiple GitHub issues on mediasoup-client-android, but scattered anecdotal reports |
| Wake lock conflicts | MEDIUM | WebRTC wake lock behavior documented, Android best practices, but specific interaction with mediasoup unverified |

## Research Gaps

**Critical gaps requiring phase-specific research:**

1. **Phase 1: libmediasoup-android ProGuard rules** - Could not verify if AAR includes consumer-rules.pro. Must inspect AAR before Phase 4.

2. **Phase 2: Exact threading model of mediasoup-android listeners** - Docs say methods "block current thread" but don't specify which thread callbacks run on. Needs experimentation.

3. **Phase 5: Android-specific memory leak magnitudes** - iOS reports 30MB per leak, but Android numbers unknown. Monitor with Android Profiler.

4. **Phase 1: AudioDeviceModule configuration compatibility with existing AudioRouter** - No examples found of WebRTC + custom AudioManager. May need trial-and-error.

**Non-critical gaps (low priority):**

- Performance characteristics of mediasoup-android on low-end devices (API 26, 2GB RAM)
- Battery consumption benchmarks (WebRTC + foreground service + wake locks)
- Specific NDK version used by libmediasoup-android 0.21.0

## Sources

**HIGH confidence (official/authoritative):**
- [Android NDK JNI Tips](https://developer.android.com/training/articles/perf-jni)
- [WebRTC AudioDeviceModule docs](https://github.com/maitrungduc1410/webrtc/blob/master/modules/audio_device/g3doc/audio_device_module.md)
- [mediasoup Garbage Collection](https://mediasoup.org/documentation/v3/mediasoup/garbage-collection/)
- [mediasoup libmediasoupclient API](https://mediasoup.org/documentation/v3/libmediasoupclient/api/)
- [AGP 9.0 Migration Guide](https://nek12.dev/blog/en/agp-9-0-migration-guide-android-gradle-plugin-9-kmp-migration-kotlin)
- [Android Wake Lock Best Practices](https://developer.android.com/develop/background-work/background-tasks/scheduling/wakelock)
- [ProGuard Consumer Rules (Android Developers)](https://developer.android.com/topic/performance/app-optimization/library-optimization)

**MEDIUM confidence (community/issue trackers):**
- [WebRTC AudioManager Conflicts](https://groups.google.com/g/discuss-webrtc/c/Pqag6R7QV2c)
- [Multiple ADM Issue](https://bugs.chromium.org/p/webrtc/issues/detail?id=2498)
- [mediasoup-client-android Issues](https://github.com/haiyangwu/mediasoup-client-android/issues)
- [mediasoup iOS Memory Leak Report](https://github.com/ethand91/mediasoup-ios-client/issues/55)
- [JNI Callbacks Guide](https://clintpaul.medium.com/jni-on-android-how-callbacks-work-c350bf08157f)
- [WebRTC Wake Lock Discussion](https://groups.google.com/g/discuss-webrtc/c/CHG9ndvMN7M)

**LOW confidence (needs verification):**
- mediasoup-android specific ProGuard configuration (no official rules found)
- Android-specific memory leak magnitudes (extrapolated from iOS)
- Device.load() failure patterns on specific manufacturers (anecdotal GitHub issues)
