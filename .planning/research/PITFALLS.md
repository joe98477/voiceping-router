# Pitfalls Research: Android PTT Client

**Domain:** Android native PTT app with WebRTC/mediasoup
**Researched:** 2026-02-08
**Confidence:** HIGH (WebRTC/Android ecosystem well-documented)

## Executive Summary

Building an Android PTT client for an existing mediasoup server presents specific integration challenges beyond standard WebRTC apps. The most critical pitfalls involve: (1) mediasoup-client native library compilation/ABI issues, (2) OEM battery optimization killing foreground services, (3) Bluetooth SCO audio routing race conditions, (4) WebRTC native object memory leaks, and (5) multi-channel audio mixing thread contention. OEM-specific battery killers (Xiaomi, Samsung, Huawei) are the largest deployment risk — even properly configured foreground services get killed without user whitelist configuration.

---

## Critical Pitfalls

### 1. mediasoup-client Native Build Fragmentation

**What goes wrong:** mediasoup-client does not have official Android native support. Third-party wrappers like `haiyangwu/mediasoup-client-android` exist but have known crashes (arm64-v8a on Android 10+), are unmaintained, or lack ABI coverage. JNI/NDK linking errors, missing ABIs (x86, armeabi-v7a), and version mismatches between libwebrtc and libmediasoupclient cause build failures or runtime crashes.

**Why it happens:** mediasoup maintains official JavaScript (browser/Node.js) client only. Community Android ports wrap C++ `libmediasoupclient` with JNI, but require NDK expertise to maintain. WebRTC ABI support and NDK versions change frequently, breaking builds. Crashes on `PeerConnection.SetRemoteDescription()` have been reported on specific Android versions/ABIs.

**Consequences:**
- App crashes on specific devices (Samsung Galaxy S20 on arm64, etc.)
- Failed builds when upgrading NDK or WebRTC dependencies
- Client can't connect to mediasoup server despite server working perfectly
- Debug time measured in days due to native stack traces

**Prevention:**
- **Phase 1 (tech spike):** Evaluate ALL available Android mediasoup wrappers BEFORE committing:
  - `haiyangwu/mediasoup-client-android` (most popular, ~600 stars, last update?)
  - `crow-misia/libmediasoup-android` (Maven Central, actively maintained?)
  - Build sample app and test on physical devices (arm64-v8a minimum)
- **Acceptance criteria for wrapper:** Successful build + connection to your existing server + tested on 3+ physical devices (different OEMs, Android 10-14)
- **Fallback plan:** If no stable wrapper exists, consider mediasoup-client via WebView hybrid approach for MVP (delay native rewrite to post-MVP)
- **ABI coverage:** Ensure wrapper supports arm64-v8a (primary) and armeabi-v7a (legacy devices)
- **Version pinning:** Pin NDK version, libwebrtc version, and mediasoup-client-android version together — do NOT upgrade independently

**Detection:**
- Build failure: `undefined reference to 'mediasoup::...'` or JNI linking errors
- Runtime crash: `java.lang.UnsatisfiedLinkError: dlopen failed: cannot locate symbol`
- Device-specific crash: Works on emulator, crashes on Samsung Galaxy S21 (arm64-v8a)

**Sources:**
- [mediasoup Android/iOS native client support discussion](https://mediasoup.discourse.group/t/android-ios-native-client-support/4298)
- [haiyangwu/mediasoup-client-android GitHub](https://github.com/haiyangwu/mediasoup-client-android)
- [mediasoup-client-android crash on SetRemoteDescription](https://github.com/haiyangwu/mediasoup-client-android/issues/17)

---

### 2. OEM Battery Optimization Kills Foreground Services

**What goes wrong:** Despite implementing a proper foreground service with ongoing notification, OEM battery optimization (Xiaomi MIUI, Samsung "Put Apps to Sleep", Huawei PowerGenie) kills the app after 5-10 minutes with screen off. User expects pocket radio behavior but discovers the app stopped running and missed critical PTT messages. Foreground service notification disappears, WebSocket disconnects, audio stops.

**Why it happens:** Android's standard Doze mode exempts foreground services. BUT OEMs add aggressive battery savers (Xiaomi "Battery Saver", Samsung "Sleeping Apps", Huawei "App Launch Manager") that kill apps regardless of foreground service status. These OEM layers operate ABOVE Android's Doze mode and ignore standard exemptions. MIUI is notorious: "background processing simply does not work right" in default settings even with proper foreground services.

**Consequences:**
- App stops responding to PTT after 5-10 minutes with screen off
- User thinks radio is working but misses emergency broadcasts
- Trust erosion — field workers abandon app after missing critical communication
- Support burden — "why isn't the app working?" when settings are device-specific

**Prevention:**
- **Phase 1 (architecture):** Foreground service with `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission (API 34+)
- **Phase 1 (UI):** Implement OEM-specific battery optimization detection and in-app whitelist instructions:
  - Detect Xiaomi/MIUI: Check `android.os.Build.MANUFACTURER == "Xiaomi"` or `Build.getRadioVersion().contains("MIUI")`
  - Detect Samsung: Check for "Put Apps to Sleep" feature (API level check)
  - Detect Huawei: Check for "App Launch Manager" (EMUI/HarmonyOS)
  - Show device-specific setup wizard on first launch: "To use VoicePing as a pocket radio, disable battery optimization: [screenshots/steps]"
- **Phase 2 (resilience):** Implement battery optimization permission request flow:
  - Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission
  - Prompt user: "VoicePing needs to stay awake for PTT. Allow?"
  - Link to device-specific settings: Settings → Battery → App Launch (Huawei), Settings → Battery → Background Usage Limits → Sleeping Apps (Samsung)
- **Phase 2 (monitoring):** Detect unexpected service death and show prominent "Service stopped — check battery settings" notification on next app open
- **Documentation:** Include OEM-specific setup guide in onboarding and support docs

**Detection:**
- Service stops after 5-10 minutes screen-off on Xiaomi/Samsung/Huawei devices
- Logcat shows service killed without `onDestroy()` callback
- `adb shell dumpsys battery` shows app in restricted bucket
- User reports: "App worked for 5 minutes then stopped"

**OEM-Specific Mitigation:**
- **Xiaomi/MIUI:** Autostart permission + Battery Optimization disabled + App Pinning (lock in recent apps) + avoid Ultra Battery Saver
- **Samsung:** Remove from "Sleeping Apps" list, disable "Put unused apps to sleep"
- **Huawei:** App Launch Manager → Disable "Manage automatically" → Enable "Run in background"

**Sources:**
- [Don't kill my app! - Xiaomi](https://dontkillmyapp.com/xiaomi)
- [Don't kill my app! - Samsung](https://dontkillmyapp.com/samsung)
- [Don't kill my app! - Huawei](https://dontkillmyapp.com/huawei)
- [Android Doze mode optimization](https://developer.android.com/training/monitoring-device-state/doze-standby)

---

### 3. Bluetooth SCO Audio Routing Race Conditions

**What goes wrong:** User presses Bluetooth headset PTT button but audio routes to phone speaker or earpiece instead of headset. Or audio starts on headset, then switches mid-transmission to phone speaker. Or Bluetooth SCO connection takes 2-3 seconds to establish, cutting off first words of PTT transmission. User releases PTT button, audio stays stuck on Bluetooth when they expected speaker playback.

**Why it happens:** Bluetooth SCO (Synchronous Connection-Oriented) audio is a separate audio channel requiring explicit start/stop via `AudioManager.startBluetoothSco()`. This operation is ASYNCHRONOUS and takes 500ms-2s to establish. If you attempt to play audio before SCO is ready, Android routes to default output (earpiece/speaker). WebRTC's audio routing is managed internally and doesn't always wait for SCO establishment. Additionally, SCO sampling rate is restricted to 16kHz or 8kHz — your server uses 48kHz Opus, requiring resampling.

**Timing issues:**
- User presses PTT → app starts SCO → app starts WebRTC transmission BEFORE SCO ready → audio routes to speaker
- User releases PTT → SCO stays active → next incoming PTT plays on Bluetooth when user expected speaker
- Device switches between WiFi/cellular → Bluetooth disconnects/reconnects → audio routing changes mid-call

**Device-specific issues:**
- OnePlus devices: `setAudioRoute()` to Bluetooth doesn't work reliably
- Android 14+: More restrictive Bluetooth permissions (BLUETOOTH_CONNECT runtime permission)

**Consequences:**
- First 1-2 seconds of PTT transmission cut off (user says "Team 3, we need—" but "Team 3" doesn't transmit)
- Audio plays through phone speaker in noisy environment, can't hear dispatch
- User thinks PTT failed because they didn't hear audio feedback through expected device
- Confusing audio routing behavior erodes trust in app reliability

**Prevention:**
- **Phase 2 (Bluetooth integration):** Implement proper SCO lifecycle:
  - Register `AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED` broadcast receiver
  - When user presses PTT on Bluetooth button: start SCO, WAIT for `SCO_AUDIO_STATE_CONNECTED`, THEN start WebRTC transmission
  - Add 200ms pre-roll time before transmitting audio (visual countdown "Connecting to headset...")
  - When user releases PTT: optionally stop SCO (or keep alive for 5s for quick re-transmission)
- **Phase 2 (audio routing):** Maintain explicit audio routing state machine:
  - Track current output device (SPEAKER, EARPIECE, WIRED_HEADSET, BLUETOOTH)
  - On Bluetooth connect: set mode to `MODE_IN_COMMUNICATION`, route to Bluetooth
  - On Bluetooth disconnect: fallback to wired headset if present, else speaker
  - Provide manual audio output selector in UI for testing/override
- **Phase 3 (advanced):** Pre-start SCO when Bluetooth headset connects (keep SCO alive for duration of session for zero latency)
- **Testing:** Test with physical Bluetooth headsets from multiple manufacturers (Plantronics, Jabra, cheap AliExpress PTT headsets)

**Detection:**
- Audio routing logs show `setAudioRoute(BLUETOOTH)` but actual route is `SPEAKER`
- Logcat shows `SCO_AUDIO_STATE_CONNECTING` but transmission starts before `SCO_AUDIO_STATE_CONNECTED`
- User reports: "First word of my message is cut off when using Bluetooth"

**Sources:**
- [Android Bluetooth SCO audio routing issues (react-native-webrtc)](https://deepwiki.com/react-native-webrtc/react-native-incall-manager/7.2-bluetooth-sco-audio)
- [Signal Android Bluetooth headset microphone issue](https://github.com/signalapp/Signal-Android/issues/6184)
- [flutter-webrtc audio routing issue](https://github.com/flutter-webrtc/flutter-webrtc/issues/811)

---

### 4. WebRTC Native Object Memory Leaks

**What goes wrong:** After 30-60 minutes of use (multiple channel joins/leaves, network reconnections), app memory usage climbs from 80MB to 300MB+ and eventually crashes with `OutOfMemoryError`. Or app crashes on second channel join with `IllegalStateException: MediaStreamTrack has been disposed`. Native heap shows thousands of undisposed `PeerConnection`, `MediaStream`, `MediaStreamTrack` objects.

**Why it happens:** WebRTC native objects (PeerConnection, MediaStream, MediaStreamTrack, VideoTrack, AudioTrack) are backed by C++ objects managed through JNI. Java garbage collector does NOT automatically free these native objects — you must explicitly call `.dispose()` on each. If you:
- Create PeerConnection and forget to call `.close()` + `.dispose()` on disconnect
- Remove MediaStream from PeerConnection without calling `.dispose()` on the stream
- Dispose PeerConnection before disposing individual MediaStreamTrack objects (wrong order)
- Re-connect after network drop without cleaning up previous PeerConnection

...then native memory leaks accumulate until OOM crash.

**Multi-channel context:** VoicePing monitors up to 5 channels simultaneously = 5 PeerConnections + 5+ MediaStreams. Each reconnection creates new objects. After 10 reconnects across 5 channels = 50+ leaked PeerConnections if not disposed properly.

**Consequences:**
- App crashes after 30-60 minutes of normal use
- Memory pressure triggers Android low-memory killer, app gets killed in background
- Difficult to debug — leak is in native heap, not visible in Java heap profiler
- User reports: "App worked fine for an hour then crashed"

**Prevention:**
- **Phase 1 (architecture):** Implement strict lifecycle management for WebRTC objects:
  - Track all PeerConnections in map: `channelId -> PeerConnection`
  - On disconnect/channel leave: call in order:
    1. `peerConnection.close()`
    2. For each `MediaStream`: `stream.dispose()`
    3. For each `MediaStreamTrack`: `track.dispose()`
    4. `peerConnection.dispose()`
    5. Remove from tracking map
  - NEVER reuse disposed objects — create fresh instances on reconnect
- **Phase 2 (reconnection):** On network drop, ensure old PeerConnection is fully disposed BEFORE creating new one
- **Phase 3 (monitoring):** Add memory leak detection in debug builds:
  - Track count of created vs disposed PeerConnections
  - Log warning if count > number of active channels
  - Use LeakCanary library to detect native leaks
- **Testing:** Run app for 2+ hours with repeated channel joins/leaves and network disconnections, monitor memory usage with Android Profiler

**Detection:**
- Native heap size grows continuously (Android Studio Profiler → Memory → Native)
- Crash log: `java.lang.OutOfMemoryError: Failed to allocate...`
- Crash on second channel join: `IllegalStateException: MediaStreamTrack has been disposed`
- Logcat shows `PeerConnection.dispose() not called` warnings

**Sources:**
- [WebRTC Android MediaStream memory leak fix](https://codereview.webrtc.org/1308733004)
- [Android dispose MediaStream fails (Chromium bug)](https://bugs.chromium.org/p/webrtc/issues/detail?id=5128)
- [flutter-webrtc local stream disposing issue](https://github.com/flutter-webrtc/flutter-webrtc/issues/106)
- [Best practices for closing WebRTC PeerConnections](https://medium.com/@BeingOttoman/best-practices-for-closing-webrtc-peerconnections-b60616b1352)

---

### 5. Multi-Channel Audio Mixing Thread Contention

**What goes wrong:** User monitors 3 channels. All 3 channels have active PTT simultaneously (overlapping speech). Audio stutters, glitches, or one channel's audio drops entirely. Or CPU usage spikes to 80%+ and device gets hot. Or audio mixing introduces 500ms+ latency (violates <300ms requirement).

**Why it happens:** Android WebRTC uses `JavaAudioDeviceModule` which creates a single `AudioTrack` output for playback. When you have 5 separate PeerConnections (one per channel), each has its own audio thread delivering decoded audio buffers. These must be MIXED in software before sending to `AudioTrack`. Naive mixing approach:
- Each PeerConnection delivers audio to separate `AudioTrack` instances → AudioTracks "take turns" if same thread (stuttering), or if separate threads, audio glitches due to contention
- Manual mixing in callback: 5 channels × 20ms buffers = need to mix 5 buffers every 20ms in audio thread callback → if callback takes >20ms due to mixing overhead, buffer underrun occurs (glitch)

**WebRTC's internal audio management:** WebRTC for mobile doesn't expose API for custom audio mixing. Default assumes single audio stream per PeerConnection.

**Consequences:**
- Audio glitches (clicks, pops, dropouts) when 2+ channels active simultaneously
- High CPU usage (40%+ on low-end devices) due to mixing overhead
- Increased latency (mixing adds 50-200ms processing time)
- User reports: "Audio is garbled when multiple people talk at once"

**Prevention:**
- **Phase 2 (audio architecture):** Choose one approach:
  - **Option A (WebRTC internal mixing):** Use single PeerConnection with multiple incoming audio tracks (1 track per channel). Let WebRTC mix internally. **ISSUE:** mediasoup creates 1 consumer per channel — requires server-side mixer or client-side track multiplexing (complex).
  - **Option B (Custom AudioTrack mixer):** Implement custom audio mixing outside WebRTC:
    - Disable WebRTC's audio playback (`peerConnection.setAudioEnabled(false)` or similar)
    - Extract decoded audio buffers from each PeerConnection (requires accessing WebRTC internals — may not be exposed in Java API)
    - Mix buffers in separate mixer thread: sum samples with clipping protection (`output = clamp(sum(inputs), -32768, 32767)`)
    - Feed mixed buffer to single `AudioTrack`
  - **Option C (Priority-based selective playback):** Only play ONE channel at a time (highest priority: emergency > dispatch > first-to-speak). Simpler but loses multi-channel awareness.
- **Phase 2 (Oboe for low latency):** Use Oboe library instead of AudioTrack for output:
  - Oboe uses AAudio (API 27+) or OpenSL ES (older) for lower latency
  - Callback-based model better suited for real-time mixing
  - Set `PerformanceMode::LowLatency` and `SharingMode::Exclusive`
  - Set buffer size to 2× burst size (double buffering)
- **Phase 3 (optimization):** Implement per-channel volume control and muting to reduce mixing overhead (muted channels skip mixing)
- **Testing:** Test with 5 channels all transmitting simultaneously, measure CPU usage and latency with Android Profiler

**Detection:**
- Audio glitches when multiple channels active (clicks, pops, silence)
- CPU usage >40% during multi-channel playback
- Logcat shows `AudioTrack underrun` warnings
- User reports: "Can't hear all channels clearly when multiple people talk"

**Sources:**
- [Mixing multiparty audio from multiple peer connections (discuss-webrtc)](https://groups.google.com/d/topic/discuss-webrtc/ruhDuK8N8KA)
- [How We Built Local Audio Streaming in Android (100ms.live)](https://www.100ms.live/blog/webrtc-audio-streaming-android)
- [Android: Mixing multiple AudioTrack instances](https://bugsdb.com/_en/debug/2670846503c9b35bd45b53b61ade90db)
- [Android Oboe low latency audio](https://developer.android.com/games/sdk/oboe/low-latency-audio)

---

## Moderate Pitfalls

### 6. Android API Fragmentation for Foreground Services

**What goes wrong:** App builds and runs fine on Android 13 (API 33) but crashes on Android 14 (API 34) with `SecurityException: Permission Denial: startForeground requires android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Or app works on API 28 but crashes on API 26 (target minimum) with missing foreground service type.

**Why it happens:** Foreground service requirements evolved significantly across Android versions:
- **API 26-27:** `startForeground()` requires ongoing notification
- **API 28:** `FOREGROUND_SERVICE` permission required in manifest
- **API 31:** Foreground service type parameter introduced (optional)
- **API 34+:** Type-specific permissions MANDATORY — must request `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission in manifest AND at runtime for media playback services

This creates 3 different code paths depending on API level. Missing any causes `SecurityException` on specific Android versions.

**Prevention:**
- **Phase 1 (manifest):** Declare ALL required permissions and service types:
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

  <service android:name=".PttForegroundService"
           android:foregroundServiceType="mediaPlayback" />
  ```
- **Phase 1 (runtime):** Check API level and request permissions conditionally:
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
          != PackageManager.PERMISSION_GRANTED) {
          // Request permission
      }
  }
  ```
- **Testing:** Test on physical devices running API 26, 28, 31, 33, 34 (emulators may not enforce fully)

**Detection:**
- Crash on Android 14: `SecurityException: ... requires android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- Crash on Android 8: `IllegalStateException: foregroundServiceType not specified`

**Sources:**
- [Android foreground service changes (developer.android.com)](https://developer.android.com/develop/background-work/services/fgs/changes)
- [Foreground service types (developer.android.com)](https://developer.android.com/develop/background-work/services/fgs/service-types)

---

### 7. WebSocket Heartbeat Timing in Doze Mode

**What goes wrong:** User locks phone, device enters Doze mode after 5-10 minutes. WebSocket heartbeat fails to send every 30 seconds as expected (server closes connection after 60s of no heartbeat). User unlocks phone, discovers they've been disconnected for 10 minutes and missed PTT messages. Reconnection takes 5-10 seconds (ICE gathering, WebRTC negotiation).

**Why it happens:** Doze mode suspends network access and defers alarms/timers to maintenance windows (every 15-30 minutes). Your 30-second heartbeat timer (likely `Handler.postDelayed()` or Kotlin coroutine `delay()`) gets deferred until next maintenance window. Foreground services EXEMPT from App Standby but NOT fully exempt from Doze mode network restrictions. Chrome on Android suspends WebSocket timers completely when screen off — similar behavior can affect native WebSocket libraries.

**Prevention:**
- **Phase 1 (foreground service):** Foreground service with partial wake lock keeps CPU awake → heartbeat timer should fire reliably
- **Phase 2 (heartbeat mechanism):** Use `AlarmManager.setExactAndAllowWhileIdle()` for heartbeat timer (bypasses Doze restrictions):
  ```kotlin
  val alarmManager = getSystemService(AlarmManager::class.java)
  val intent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_HEARTBEAT), FLAGS)
  alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, intent)
  ```
- **Phase 2 (resilience):** Implement server-side grace period (120s instead of 60s) to tolerate occasional missed heartbeats
- **Phase 3 (monitoring):** Detect missed heartbeats and log to analytics: "Missed heartbeat — Doze mode interference detected"
- **Alternative:** Use FCM high-priority push notification to wake device when PTT message arrives (requires server changes — out of scope for pure client milestone)

**Detection:**
- WebSocket disconnects after 5-10 minutes screen-off despite foreground service running
- Logcat shows: "Heartbeat scheduled but not executed for 15 minutes"
- User reports: "App disconnects when I lock my phone"

**Sources:**
- [Android WebSocket Doze mode issues (Qt Forum)](https://forum.qt.io/topic/90939/sending-keep-alive-messages-on-android-even-if-the-device-is-in-sleep-doze-mode)
- [WebSocket connections and Android Doze (Ably)](https://ably.com/topic/websockets-android)
- [Android AlarmManager and Doze mode](https://developer.android.com/develop/background-work/services/alarms)

---

### 8. Cellular Network NAT Traversal Failures

**What goes wrong:** App works perfectly on WiFi but fails to connect on 4G/5G cellular. User sees "Connecting..." spinner indefinitely. WebRTC ICE gathering times out or only gathers `srflx` (server reflexive) candidates but connection fails. Or connection works but drops after 2-3 minutes when carrier NAT mapping times out.

**Why it happens:** Cellular networks use aggressive NAT (often Carrier-Grade NAT / CGN) with restrictive policies:
- **Symmetric NAT:** Most restrictive type, requires TURN relay (STUN alone insufficient)
- **Short NAT mapping timeout:** 30-60 seconds — if no traffic, mapping expires and connection drops
- **Different NAT types across carriers:** AT&T, Verizon, T-Mobile have different policies
- **Dual-SIM devices:** ICE gathering on wrong interface causes STUN storm, carrier blocks STUN packets

**Dual-SIM issue:** When using 'all' interfaces for ICE candidates, device queries both SIMs → sends STUN requests to both → triggers carrier firewall → STUN blocked → ICE fails.

**Prevention:**
- **Phase 1 (TURN server):** Ensure mediasoup server has TURN server configured (not just STUN). Verify TURN credentials passed to Android client via WebSocket signaling.
- **Phase 1 (ICE configuration):** Configure ICE with proper candidate gathering:
  ```kotlin
  val iceServers = listOf(
      PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
      PeerConnection.IceServer.builder("turn:your-server.com:3478")
          .setUsername("user")
          .setPassword("pass")
          .createIceServer()
  )
  ```
- **Phase 2 (network detection):** Detect cellular vs WiFi and adjust ICE timeout (cellular needs 10-15s vs WiFi 5s)
- **Phase 3 (keepalive):** Send periodic dummy packets (every 20s) to keep NAT mapping alive during silent periods
- **Testing:** Test on physical devices with 4G/5G SIM cards from multiple carriers (AT&T, Verizon, T-Mobile). Disable WiFi to force cellular.

**Detection:**
- Connection works on WiFi, fails on cellular
- ICE state stuck in `checking` or `failed`
- Logcat shows: `No TURN candidates gathered` or `ICE gathering timeout`
- User reports: "App doesn't work when I'm in the field without WiFi"

**Sources:**
- [WebRTC NAT traversal on cellular (moldstud.com)](https://moldstud.com/articles/p-troubleshooting-webrtc-ice-candidates-common-issues-and-solutions-explained)
- [Dual-SIM iPhone ICE connectivity issue](https://issues.webrtc.org/issues/42221045)
- [WebRTC NAT traversal methods](https://www.liveswitch.io/blog/webrtc-nat-traversal-methods-a-case-for-embedded-turn)

---

### 9. Volume Button Capture Conflicts with System Volume

**What goes wrong:** User configures volume keys as PTT buttons. Pressing volume down starts PTT transmission BUT also lowers media volume to zero (can't hear incoming PTT audio). Or Android's "Select to Speak" accessibility feature is enabled and volume buttons control TTS volume instead of media volume, causing confusing behavior.

**Why it happens:** Android volume buttons are hardware keys that generate `KEYCODE_VOLUME_DOWN` / `KEYCODE_VOLUME_UP` events. By default, these adjust the active audio stream volume (media/call/alarm). To use volume keys for PTT, you must intercept the key event in `onKeyDown()` and return `true` to consume it (prevent default volume adjustment). BUT:
- **Activity lifecycle:** Volume key interception only works when activity is in foreground. When screen is off or app in background, volume keys adjust volume normally (can't intercept).
- **Select to Speak bug (Android 14+):** When "Select to Speak" accessibility feature is enabled, volume keys control accessibility service volume instead of media volume, even when your app intercepts the event.
- **System volume dialog:** Even if you consume the key event, Android may still show volume slider UI (distracting).

**Consequences:**
- User presses volume down for PTT → media volume drops to zero → can't hear response
- User enables "Select to Speak" for accessibility → volume buttons stop working as PTT (no PTT transmission triggered)
- Confusing UX: volume slider appears during PTT transmission

**Prevention:**
- **Phase 2 (volume key PTT):** Implement volume key interception:
  ```kotlin
  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
      if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && pttEnabled) {
          startPtt()
          return true // Consume event
      }
      return super.onKeyDown(keyCode, event)
  }
  ```
- **Phase 2 (workaround for system volume):** Set audio focus to `AUDIOFOCUS_GAIN` before PTT session → locks media volume
- **Phase 3 (accessibility detection):** Detect "Select to Speak" enabled and show warning:
  ```kotlin
  val accessibilityManager = getSystemService(AccessibilityManager::class.java)
  if (/* Select to Speak enabled */) {
      showDialog("Volume keys may not work as PTT. Disable Select to Speak in Accessibility settings.")
  }
  ```
- **Phase 3 (alternative PTT trigger):** Offer on-screen PTT button as fallback when volume keys unreliable
- **Documentation:** Warn users that volume key PTT only works when screen is on (limitation of Android)

**Detection:**
- Volume slider appears when user presses volume key for PTT
- Media volume drops to zero after repeated PTT transmissions
- User reports: "Volume keys don't work as PTT after enabling accessibility features"

**Sources:**
- [Android volume button bug with Select to Speak](https://android.gadgethacks.com/news/android-volume-bug-confirmed-by-google-fix-coming/)
- [Zello: Using volume key for PTT on Android](https://support.zello.com/hc/en-us/articles/230745107)
- [Android intercepting hardware buttons](https://www.b4x.com/android/forum/threads/intercepting-hardware-buttons.135065/)

---

### 10. Audio Buffer Underruns with Oboe (XRuns)

**What goes wrong:** User monitors 2-3 channels on older device (Samsung Galaxy A50, Snapdragon 665). Audio playback has frequent clicks, pops, and brief moments of silence every 2-3 seconds. Logcat shows `AudioTrack underrun` warnings. Audio latency increases over time from 100ms to 500ms+.

**Why it happens:** Oboe uses low-latency audio with small buffer sizes (typically 2× burst size = ~5ms of audio). Audio callback runs every 5ms and must deliver next audio buffer BEFORE previous buffer exhausted. If callback takes >5ms (blocked by GC, CPU contention, heavy mixing logic), buffer underrun occurs (XRun). Common causes:
- **Memory allocation in callback:** `new`, `malloc`, Kotlin list operations → triggers GC → callback blocked 20ms → underrun
- **Thread locking:** Synchronization on shared state in callback → other thread holds lock → callback blocked → underrun
- **Heavy mixing logic:** Mixing 5 channels with volume adjustment, sample rate conversion → takes 10ms → underrun
- **File I/O in callback:** Logging to file, reading config → disk I/O → callback blocked → underrun

**Low-end devices:** Budget phones (Snapdragon 4xx/6xx series) have slower CPUs → callback overhead is proportionally higher → more frequent underruns.

**Prevention:**
- **Phase 2 (Oboe implementation):** Follow real-time audio best practices:
  - NEVER allocate memory in audio callback (pre-allocate all buffers)
  - NEVER lock mutexes in callback (use lock-free data structures or atomic operations)
  - NEVER perform I/O in callback (no logging, no file access)
  - Keep callback logic under 50% of buffer duration (if buffer is 5ms, callback must complete in <2.5ms)
- **Phase 2 (buffer sizing):** Start with larger buffer (4× burst size) for stability, optimize down to 2× after testing:
  ```cpp
  builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
  builder.setFramesPerCallback(2 * burstSize); // Double buffering
  ```
- **Phase 3 (monitoring):** Monitor XRun count in production:
  ```cpp
  int32_t xruns = stream->getXRunCount();
  if (xruns > previousXruns) {
      logXRunEvent(); // Track in analytics
  }
  ```
- **Phase 3 (fallback):** If XRun rate >10/minute, automatically increase buffer size (trade latency for stability)
- **Testing:** Test on low-end devices (2GB RAM, Snapdragon 4xx) with 5 channels active + background CPU load

**Detection:**
- Audio clicks, pops, glitches during playback
- Logcat: `AudioTrack: getXRunCount() returned XRun count > 0`
- Latency increases over time (buffer backlog)
- User reports: "Audio sounds choppy on my older phone"

**Sources:**
- [Oboe TechNote: Glitches](https://github.com/google/oboe/wiki/TechNote_Glitches)
- [Android low latency audio with Oboe](https://developer.android.com/games/sdk/oboe/low-latency-audio)
- [Real-time audio processing best practices](https://oboe.com/learn/c-for-audio-dsp-1qhwprx/real-time-audio-processing-1mzn463)

---

## Minor Pitfalls

### 11. Audio Focus Loss During Phone Calls

**What goes wrong:** User receives phone call while monitoring PTT channels. App continues playing incoming PTT audio during phone call (user misses important call audio or PTT audio leaks to caller). Or app loses audio focus and can't resume PTT after call ends.

**Why it happens:** Android audio focus system allows multiple apps to request audio output. When phone call starts, Phone app requests `AUDIOFOCUS_GAIN` → your app receives `AUDIOFOCUS_LOSS` → should pause/stop audio. If you don't handle this, Android may forcibly duck (lower volume) your audio or route it to unexpected output.

**Prevention:**
- **Phase 2 (audio focus):** Implement `OnAudioFocusChangeListener`:
  ```kotlin
  val focusListener = OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
          AudioManager.AUDIOFOCUS_LOSS -> pausePtt() // Stop PTT
          AudioManager.AUDIOFOCUS_GAIN -> resumePtt() // Resume PTT
          AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> /* Pause temporarily */
      }
  }
  audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_VOICE_CALL,
                                  AudioManager.AUDIOFOCUS_GAIN)
  ```
- **Phase 3 (notification):** Show notification: "PTT paused during phone call. Will resume after call ends."
- **Testing:** Test with incoming call, outgoing call, and WhatsApp/Signal calls

**Detection:**
- PTT audio continues playing during phone call
- User reports: "Caller heard my radio traffic" or "Couldn't hear my phone call"

---

### 12. Wake Lock Battery Drain

**What goes wrong:** App keeps device awake 24/7 even when user is not actively monitoring channels. Battery drains from 100% to 0% in 8 hours (expected: 24+ hours). Device gets noticeably warm in pocket.

**Why it happens:** Foreground service with `PARTIAL_WAKE_LOCK` keeps CPU awake to maintain WebSocket and audio. If wake lock is held continuously without release, CPU never enters deep sleep → high battery drain.

**Prevention:**
- **Phase 1 (wake lock management):** Only acquire wake lock when channels are actively monitored:
  ```kotlin
  val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoicePing::PttWakeLock")
  wakeLock.acquire(10*60*1000L /* 10 minutes */) // Timeout for safety
  ```
- **Phase 2 (optimization):** Release wake lock during idle periods (no active PTT for 5+ minutes)
- **Phase 3 (monitoring):** Track wake lock duration in analytics, alert if held >12 hours continuously

**Detection:**
- Battery drain rate >10%/hour with screen off
- Device warm to touch after 2-3 hours idle
- Android battery stats show app as top battery consumer

---

### 13. Incorrect Opus Decoder Configuration

**What goes wrong:** Audio sounds robotic, garbled, or has metallic artifacts. Or audio decoding fails entirely with `Opus decode error: invalid packet`.

**Why it happens:** Server sends Opus at 48kHz but Android client expects 16kHz. Or server uses VBR (Variable Bit Rate) but client decoder configured for CBR. WebRTC's Opus decoder must match server encoder settings (sample rate, channels, packet duration).

**Prevention:**
- **Phase 1 (decoder config):** Match server Opus configuration exactly:
  - Sample rate: 48kHz (server uses 48kHz per PROJECT.md)
  - Channels: 1 (mono) for PTT
  - Packet duration: 20ms (standard for WebRTC)
  - FEC: Enabled (server has FEC enabled)
  - DTX: Disabled (server has DTX disabled)
- **Testing:** Verify audio quality with side-by-side web client (known-good) vs Android client

**Detection:**
- Audio sounds robotic or garbled
- Decoding errors in logcat
- User reports: "Audio sounds weird on Android but fine on web"

---

## OEM-Specific Issues

### Xiaomi/MIUI

**Primary issue:** MIUI kills background apps aggressively even with foreground service + battery optimization disabled.

**Required mitigation (all must be configured by user):**
1. Disable battery optimization: Settings → Battery & Performance → App battery saver → VoicePing → No restrictions
2. Enable Autostart: Settings → Apps → Manage apps → VoicePing → Autostart → Enable
3. Lock app in recent apps: Recent apps → Drag VoicePing down → Tap lock icon
4. Disable "Put app to sleep after lock screen": Settings → Battery & Performance → App battery saver → Disable

**Detection:** `Build.MANUFACTURER == "Xiaomi"` or check for MIUI ROM

**In-app guidance:** Show Xiaomi-specific setup wizard with screenshots on first launch

---

### Samsung One UI

**Primary issue:** "Sleeping Apps" and "Deep Sleeping Apps" features put unused apps to sleep, killing foreground services.

**Required mitigation:**
1. Remove from Sleeping Apps: Settings → Battery → Background usage limits → Sleeping apps → Remove VoicePing
2. Disable "Put unused apps to sleep": Settings → Battery → Background usage limits → Toggle off

**One UI 8+ (2026):** AI-powered battery optimization is more aggressive, automatically moves apps to deep sleep after 3 days of no use.

**Detection:** `Build.MANUFACTURER == "samsung"`

**In-app guidance:** Show Samsung-specific setup wizard with device screenshots

---

### Huawei EMUI / HarmonyOS

**Primary issue:** PowerGenie task killer and App Launch Manager kill apps not on whitelist.

**Required mitigation:**
1. App Launch Manager: Settings → Battery → App launch → VoicePing → Disable "Manage automatically" → Enable "Run in background"
2. Battery optimization: Settings → Battery optimization → VoicePing → Don't allow

**PowerGenie:** EMUI 9+ has hard-coded app killer that ignores foreground services unless app is on system whitelist (can't be controlled by user or app).

**Detection:** `Build.MANUFACTURER == "HUAWEI"` or check for EMUI/HarmonyOS

**In-app guidance:** Show Huawei-specific setup wizard; warn that some EMUI versions may still kill app

---

### OnePlus OxygenOS

**Primary issue:** Bluetooth audio routing (`setAudioRoute()`) doesn't work reliably on OnePlus devices.

**Workaround:** Use `AudioManager.startBluetoothSco()` + explicit routing instead of relying on automatic routing.

**Detection:** `Build.MANUFACTURER == "OnePlus"`

---

### Oppo / Realme ColorOS

**Primary issue:** Similar to Xiaomi — aggressive battery optimization with "Sleeping apps" feature.

**Required mitigation:**
1. Disable battery optimization: Settings → Battery → App Battery Saver → VoicePing → No restrictions
2. Startup manager: Settings → Security → Startup Manager → VoicePing → Enable

**Detection:** `Build.MANUFACTURER == "OPPO"` or `"Realme"`

---

## Phase Mapping

| Phase | Pitfalls to Address | Why This Phase |
|-------|---------------------|----------------|
| **Phase 1: WebRTC Foundation** | #1 (mediasoup-client native), #4 (memory leaks), #6 (API fragmentation), #13 (Opus config) | Core WebRTC integration must be stable before building UI/features on top. Memory leak prevention is architectural. |
| **Phase 2: Foreground Service & Audio** | #2 (OEM battery killers), #5 (multi-channel mixing), #7 (WebSocket Doze), #10 (Oboe XRuns), #11 (audio focus), #12 (wake lock) | Background operation and audio reliability are critical for pocket radio use case. |
| **Phase 3: Hardware PTT & Bluetooth** | #3 (Bluetooth SCO), #9 (volume button conflicts) | Hardware integration comes after core audio works. Bluetooth is complex and can be iterated. |
| **Phase 4: Network Resilience** | #8 (cellular NAT traversal) | Network edge cases addressed after core functionality proven on WiFi. |

---

## Research Confidence Assessment

| Pitfall | Confidence | Source Quality |
|---------|------------|----------------|
| mediasoup-client native | HIGH | Official mediasoup forums, GitHub issues with crash reports |
| OEM battery killers | HIGH | dontkillmyapp.com (community-maintained), official Android docs |
| Bluetooth SCO | HIGH | Multiple WebRTC project issue trackers (Signal, flutter-webrtc) |
| WebRTC memory leaks | HIGH | Chromium bug tracker, WebRTC code reviews |
| Multi-channel mixing | MEDIUM | Community forums, need to verify with your specific architecture |
| API fragmentation | HIGH | Official Android developer docs |
| WebSocket Doze | MEDIUM | Community reports, need device testing to confirm |
| Cellular NAT | HIGH | WebRTC documentation, carrier NAT is well-known issue |
| Volume button conflicts | HIGH | Google confirmed bug, community workarounds |
| Oboe XRuns | HIGH | Official Oboe documentation |
| Audio focus | HIGH | Official Android audio docs |
| Wake lock drain | MEDIUM | Standard Android practice |
| Opus config | MEDIUM | Verified server uses 48kHz from PROJECT.md |

---

## Open Questions for Phase-Specific Research

1. **mediasoup-client wrapper maintenance status** — Check GitHub last commit date, open issues count, maintainer responsiveness for selected wrapper before committing (Phase 1 acceptance criteria)

2. **Multi-channel mixing architecture** — Does chosen mediasoup-client wrapper expose API for custom audio mixing? Or must we work around WebRTC's internal AudioTrack? (Phase 2 spike)

3. **Bluetooth PTT button event handling** — How do different Bluetooth headset manufacturers send PTT button events? (Some use media button, some use vendor-specific commands) (Phase 3 research)

4. **TURN server capacity** — Current TURN server can handle 30%+ of Android clients needing relay on cellular? Verify TURN server capacity before production. (Phase 4)

---

**Recommendations:**
1. **Phase 1 acceptance gate:** Build minimal WebRTC connection demo with selected mediasoup wrapper, test on 3 physical devices (different OEMs, API levels) before proceeding to full implementation
2. **Phase 2 OEM setup wizard:** Make OEM-specific battery optimization setup mandatory on first launch for Xiaomi/Samsung/Huawei devices (block app use until configured)
3. **Phase 3 Bluetooth pre-warming:** Keep SCO connection alive during entire PTT session (trade battery for zero-latency PTT response)
4. **Phase 4 cellular testing:** Dedicate test devices with SIM cards from AT&T, Verizon, T-Mobile for real-world NAT traversal validation

---

*Researched: 2026-02-08*
*Next: This research informs roadmap phase structure and per-phase acceptance criteria*
