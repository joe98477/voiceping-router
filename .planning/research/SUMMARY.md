# Project Research Summary

**Project:** VoicePing Router - libmediasoup-android Integration
**Domain:** Real-time WebRTC Push-to-Talk (PTT) Communication
**Researched:** 2026-02-13
**Confidence:** HIGH

## Executive Summary

VoicePing Router is an existing Android PTT app undergoing library integration to replace stub methods with real WebRTC audio functionality. The app already has functional UI, authentication, WebSocket signaling, Room database, and Hilt dependency injection. The current milestone (v3.0) focuses on replacing MediasoupClient.kt stub code (// TODO comments) with actual crow-misia libmediasoup-android library calls.

The recommended approach is a straightforward dependency upgrade from libmediasoup-android 0.7.0 to 0.21.0 (latest stable, released 2026-02-10). This library bundles WebRTC M130, libmediasoupclient 3.5.0, and native binaries for all Android ABIs. The integration is drop-in compatible with the existing AGP 9.0.0 + Gradle 9.3.1 + Kotlin 2.2.0 stack. The architecture uses a singleton Device shared across channels, one RecvTransport for all incoming audio consumers, and one SendTransport created per PTT transmission. No external AudioRecord management is needed — the library handles audio capture, Opus encoding, and RTP packetization internally.

Critical risks center on AudioManager ownership conflicts (app's AudioRouter vs WebRTC's internal AudioDeviceModule), JNI threading in Transport.Listener callbacks (require runBlocking bridges to call suspend functions), and cleanup sequence discipline (producers before consumers before transports). These are well-documented patterns with clear prevention strategies. The existing codebase structure (singleton MediasoupClient, ordered cleanup stubs, PTT state machine) already follows correct patterns, requiring implementation rather than redesign.

## Key Findings

### Recommended Stack

The stack requires only a single dependency upgrade. The library is crow-misia/libmediasoup-android 0.21.0, which wraps libmediasoupclient 3.5.0 and WebRTC M130 (130.6723.2.0). All other project dependencies (Hilt 2.59.1, Coroutines 1.10.1, OkHttp 4.12.0, Room 2.8.4) remain unchanged and are already correctly configured.

**Core technology:**
- **libmediasoup-android 0.21.0**: WebRTC client for mediasoup SFU — Latest stable (2026-02-10), prebuilt native binaries, automatic ProGuard rules, backward compatible with AGP 9.0.0

**What's bundled in library:**
- libmediasoupclient 3.5.0 (C++ client)
- WebRTC M130 with Opus codec
- Native .so files for armeabi-v7a, arm64-v8a, x86_64 (~28MB total)
- ProGuard consumer rules (automatic)

**Build requirements met:**
- Android SDK 35 (installed)
- Java 17 runtime (configured)
- Gradle 9.3.1 (wrapper)
- NDK/CMake NOT required (library ships prebuilt binaries)

**Server compatibility:**
- Project uses mediasoup 3.19 (Node.js SFU)
- Library uses libmediasoupclient 3.5.0
- Protocol compatible (3.x series)

### Expected Features

**Must have (table stakes):**
- Device initialization with RTP capabilities — Core mediasoup pattern, server determines codec support
- Send/Recv transport creation — Bidirectional audio (PTT send + listen receive)
- Producer creation for PTT audio — Send audio when PTT pressed
- Consumer creation for incoming audio — Receive audio from other participants
- AudioTrack creation via PeerConnectionFactory — WebRTC audio source for Producer
- Transport listener callbacks (onConnect, onProduce) — mediasoup signaling protocol requirement
- Consumer pause/resume for listening control — Start/stop receiving audio
- Producer close on PTT release — Stop sending audio when PTT released
- Ordered resource disposal — Prevent memory leaks and crashes (producers → consumers → transports)

**Should have (competitive):**
- Per-consumer volume control — Individual channel volume (already in UI via ChannelVolumeDialog.kt), wire to `audioTrack.setVolume(0.0-10.0)` API
- Opus codec PTT optimization — Already configured in MediasoupClient.kt: opusDtx=true, opusFec=true, mono, 48kHz
- Audio device switching (earpiece/speaker/BT) — Already implemented in AudioRouter.kt, needs WebRTC integration coordination
- Consumer statistics monitoring — Network quality indicators via `consumer.getStats()` for packet loss, jitter, bitrate
- Echo cancellation + noise suppression — MediaConstraints: googEchoCancellation=true, googNoiseSuppression=true

**Defer (v2+):**
- Simulcast for bandwidth adaptation — Enable `encodings` parameter in `produce()` for multi-bitrate streams
- Data channel support — Use DataProducer/DataConsumer for text chat or metadata
- Video track support — Extend to video producers/consumers for future video PTT

### Architecture Approach

The existing singleton-based architecture (MediasoupClient, ChannelRepository, PttManager) requires minimal structural changes. The library manages its own PeerConnectionFactory with dedicated WebRTC threads (signaling, worker, network). Transport listener callbacks execute synchronously on WebRTC's signaling thread, not Android's main thread or coroutine dispatchers, requiring runBlocking bridges to call SignalingClient's suspend functions.

**Major components:**

1. **Device (singleton)** — Holds RTP capabilities, shared across all transports and channels. Initialize once with `device.load(routerRtpCapabilities)`, reuse for all channels. Never dispose (singleton pattern).

2. **RecvTransport (singleton)** — One receive transport shared across all channels for incoming audio consumers. Create on first channel join, reuse for all subsequent consumers. Close only on app disconnect.

3. **SendTransport (per-PTT)** — One send transport created when PTT is pressed, closed when released. Produces audio via Producer. Transport.Listener.onConnect signals DTLS params to server, onProduce returns server-assigned producer ID.

4. **AudioSource/AudioTrack (library-managed)** — Library creates AudioRecord and AudioTrack internally. No external AudioCaptureManager needed. Opus encoding happens in WebRTC worker thread (native code).

5. **AudioRouter (refactor required)** — Existing component controls AudioManager mode and device routing. Must coordinate with WebRTC's AudioDeviceModule to avoid dual AudioManager control (MODE_IN_COMMUNICATION conflicts).

**Key architectural patterns:**
- **Threading bridge:** Transport callbacks run on WebRTC signaling thread → use `runBlocking { signalingClient.request(...) }` to bridge to suspend functions
- **Cleanup hierarchy:** producers.close() → consumers.close() → sendTransport.close() → recvTransport.close() → device never disposed
- **State synchronization:** Use Mutex for atomic state transitions during reconnection to prevent race conditions

**Components to remove/refactor:**
- **AudioCaptureManager** — REMOVE, replaced by library's AudioSource
- **AudioCaptureService** — KEEP but simplify (still needed for foreground notification, but audio capture logic removed)
- **MediasoupClient.sendAudioData()** — REMOVE, Producer sends audio automatically from AudioTrack's internal capture

### Critical Pitfalls

1. **Dual AudioManager Control (WebRTC vs Application)** — WebRTC's PeerConnectionFactory internally manages AudioManager (sets MODE_IN_COMMUNICATION, controls hardware echo cancellation). Your existing AudioRouter also controls AudioManager. Both systems fighting simultaneously causes AUDIO_RECORD_START_STATE_MISMATCH errors, recording failures ("Can only have one active PC/ADM in WebRTC"), echo issues, speakerphone routing conflicts. **Prevention:** Let WebRTC own AudioManager via custom AudioDeviceModule builder, refactor AudioRouter to only set routing AFTER WebRTC initializes, remove AudioRouter's MODE_IN_COMMUNICATION management.

2. **Native Callbacks on Wrong Thread (JNI Threading)** — mediasoup-android uses JNI, callbacks execute from native threads (WebRTC signaling thread), not main/UI thread or coroutine dispatchers. Direct UI updates crash with CalledFromWrongThreadException, race conditions when accessing shared state, memory leaks if storing Activity/Fragment refs in listeners. **Prevention:** Never access UI from listeners, use `scope.launch(Dispatchers.Main)` for UI updates, use `runBlocking` or `suspendCoroutine` to bridge to suspend functions, avoid storing context refs in listener lambdas.

3. **Incomplete Cleanup Causes Memory Leaks** — mediasoup objects hold native memory via JNI. Without explicit close() in correct order, leak ~30MB per unclosed transport/producer/consumer, native WebRTC threads keep running, microphone stays captured, event listeners accumulate. **Prevention:** Follow cleanup hierarchy (consumers → producers → transports → device never disposed), listen for transportclose events, use try-finally for cleanup, track object lifecycle in collections, don't rely on GC.

4. **Race Conditions During Reconnection** — Network disconnects while PTT active. Reconnect attempts overlap with disconnect cleanup. Results in duplicate producers (old not closed, new created), Transport.connect() called on closed transport, state inconsistency between PttManager and transport state. **Prevention:** Use state machine with atomic transitions (Mutex), cancel ongoing operations before reconnect (Job.cancelAndJoin), wait for cleanup to complete (cleanupJob?.join()), handle transportclose events with exponential backoff.

5. **AGP 9.0 Breaks NDK in Library Modules** — AGP 9.0 disallows NDK execution in library modules. If mediasoup code placed in separate `:mediasoup` library module, build fails with "NDK execution in library modules... not supported". **Prevention:** Keep mediasoup in application module (`:app`), not separate library module. Project already structured correctly (MediasoupClient in app/src/main/kotlin/com/voiceping/android/data/network/).

## Implications for Roadmap

Based on research, the milestone v3.0 "mediasoup Library Integration" should be structured around integration risks, not feature delivery. The existing app already has UI, auth, signaling, and state management. The goal is to replace stub code with real library calls while avoiding the five critical pitfalls.

### Phase 1: Library Upgrade and WebRTC Foundation
**Rationale:** Establish WebRTC subsystem and resolve AudioManager ownership before any audio integration. This prevents Pitfall 1 (dual AudioManager control) and Pitfall 2 (JNI threading) from blocking all subsequent phases.
**Delivers:** Updated dependency (0.21.0), PeerConnectionFactory initialized, AudioDeviceModule configured, AudioRouter refactored to coordinate with WebRTC
**Addresses:** Device initialization (table stakes feature), echo cancellation + noise suppression (competitive feature)
**Avoids:** Pitfall 1 (AudioManager conflicts), Pitfall 2 (JNI threading patterns established), Pitfall 5 (AGP 9.0 verified)
**Research needed:** No — patterns well-documented in official WebRTC AudioDeviceModule guides

### Phase 2: Device and RecvTransport Integration
**Rationale:** Device.load() must complete before any transport creation (dependency). RecvTransport is simpler than SendTransport (no onProduce callback), making it better for proving JNI threading patterns work.
**Delivers:** Device.load(routerRtpCapabilities), RecvTransport with onConnect callback, Consumer creation and resume(), per-consumer volume control
**Uses:** libmediasoup-android Device/RecvTransport APIs, Kotlin coroutines with runBlocking bridge
**Implements:** Singleton Device, singleton RecvTransport shared across channels
**Avoids:** Pitfall 3 (cleanup hierarchy tested with consumers), Pitfall 2 (JNI threading bridge validated)
**Research needed:** No — standard mediasoup patterns

### Phase 3: SendTransport and Producer Integration
**Rationale:** Builds on validated Device/RecvTransport patterns. More complex due to onProduce callback and AudioSource creation. Allows end-to-end PTT testing.
**Delivers:** SendTransport with onConnect and onProduce callbacks, AudioSource + AudioTrack creation, Producer with Opus config, PTT transmission working
**Addresses:** Producer creation (table stakes), Opus codec optimization (competitive), AudioCaptureManager removal
**Avoids:** Pitfall 2 (onProduce callback threading), Pitfall 3 (producer cleanup)
**Research needed:** No — standard patterns, AudioCaptureManager removal is refactor not research

### Phase 4: Cleanup Lifecycle and Reconnection Resilience
**Rationale:** Once producers/consumers/transports working, focus shifts to lifecycle management. This is where most production bugs emerge (reconnection, network flapping, rapid PTT press/release).
**Delivers:** Ordered disposal (producers → consumers → transports), transportclose event handlers, state machine with Mutex for reconnection, exponential backoff
**Addresses:** Ordered resource disposal (table stakes feature)
**Avoids:** Pitfall 3 (memory leaks), Pitfall 4 (race conditions during reconnection)
**Research needed:** No — mediasoup reconnection patterns documented in discourse

### Phase 5: Release Build Validation and Device Testing
**Rationale:** ProGuard/R8 obfuscation can break JNI even if debug builds work. Device-specific codec issues (Huawei, Samsung) only surface on real hardware.
**Delivers:** ProGuard rules verified (consumer-rules.pro in AAR), release APK tested on physical device, RTP capabilities validated after Device.load(), battery/wake lock profiling
**Addresses:** N/A (validation phase)
**Avoids:** Pitfall 6 (ProGuard strips JNI), Pitfall 7 (Device.load() codec compatibility), Pitfall 8 (wake lock conflicts)
**Research needed:** No — standard Android release testing

### Phase Ordering Rationale

- **Phase 1 first:** AudioManager ownership must be resolved before any audio operations. Attempting audio integration with conflicting AudioManager control causes AUDIO_RECORD_START_STATE_MISMATCH failures that block all development.

- **RecvTransport before SendTransport (Phases 2 & 3):** RecvTransport.Listener has fewer callbacks (no onProduce), making it simpler for validating JNI threading bridge pattern. Success here proves pattern works before tackling more complex SendTransport.

- **Cleanup after basic integration (Phase 4):** Cleanup logic can't be tested until producers/consumers/transports exist. Attempting to design cleanup sequences in abstract leads to missing edge cases (transportclose events, reconnection races).

- **Release validation last (Phase 5):** ProGuard issues only surface in release builds. Testing release builds too early wastes time (code still changing). Deferring to Phase 5 ensures stable codebase before release testing investment.

### Research Flags

Phases with standard patterns (skip /gsd:research-phase):
- **Phase 1:** WebRTC AudioDeviceModule configuration well-documented in official guides
- **Phase 2:** Device.load() and RecvTransport patterns standard mediasoup usage
- **Phase 3:** SendTransport and Producer patterns standard mediasoup usage
- **Phase 4:** Reconnection patterns documented in mediasoup discourse
- **Phase 5:** Standard Android release testing, no domain-specific research needed

**No phases need deeper research.** All integration patterns are well-documented in mediasoup official docs, WebRTC guides, and Android NDK documentation. Research gaps identified (ProGuard rules verification, exact threading model) are validation tasks during implementation, not research blockers.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Library version verified in Maven Central, compatibility matrix confirmed in source code (buildSrc/Maven.kt, gradle/libs.versions.toml), all build requirements already met |
| Features | MEDIUM | API patterns verified in official mediasoup docs, implementation details from community sources (haiyangwu/mediasoup-client-android examples), volume control and stats APIs assumed to exist (verify method signatures in crow-misia source) |
| Architecture | MEDIUM | Threading model documented in WebRTC guides, singleton Device pattern verified in mediasoup design docs, PeerConnectionFactory exposure in crow-misia assumed but not confirmed (check API), runBlocking bridge pattern standard Kotlin-JNI integration |
| Pitfalls | HIGH | AudioManager conflicts documented in multiple WebRTC official discussions, JNI threading from Android NDK official docs, memory leaks from mediasoup iOS reports (~30MB) and garbage collection docs, AGP 9.0 restrictions in official migration guide, race conditions from mediasoup discourse examples |

**Overall confidence:** HIGH

All critical integration requirements verified from official sources (mediasoup.org API docs, Android NDK docs, WebRTC guides). Medium confidence areas (feature implementation details, architecture specifics) are tactical code-level questions answerable during implementation via source code inspection, not strategic blockers.

### Gaps to Address

**During Phase 1 (verify during implementation):**
- **PeerConnectionFactory access:** Does crow-misia expose `Device.getPeerConnectionFactory()` or require separate initialization? Check crow-misia API docs or source code in io.github.crow_misia.mediasoup.* packages.
- **AudioDeviceModule builder API:** Verify crow-misia supports custom AudioDeviceModule configuration (needed for AudioRouter coordination). If not, may need to use library's default and refactor AudioRouter differently.

**During Phase 2 (verify during implementation):**
- **Consumer volume control API:** Assumed `consumer.track.setVolume()` exists based on WebRTC AudioTrack API. Verify method signature in crow-misia Consumer class.
- **Codec options format:** Java API shows codecOptions as String (JSON). Verify crow-misia Kotlin API supports Opus DTX/FEC configuration or if it's server-side only.

**During Phase 4 (monitor during testing):**
- **Threading deadlock risk:** If WebRTC holds locks while waiting for callback return, runBlocking could deadlock. Monitor signaling thread with Android Profiler during reconnection testing. If deadlocks occur, switch to suspendCoroutine + CompletableFuture pattern.

**During Phase 5 (verify in AAR):**
- **ProGuard consumer rules:** Research assumes libmediasoup-android AAR includes consumer-proguard-rules.pro. Verify by inspecting AAR: `unzip -l libmediasoup-android-0.21.0.aar | grep proguard`. If missing, manually add ProGuard rules from WebRTC AudioDeviceModule docs.

**Non-blocking (defer to post-launch):**
- **Android-specific memory leak magnitudes:** iOS reports 30MB per unclosed transport, Android numbers unknown. Monitor with Android Profiler during Phase 4 testing. Not critical since cleanup hierarchy prevents leaks.
- **Bluetooth SCO interaction:** How library's AudioTrack interacts with AudioRouter's Bluetooth SCO setup unknown. Test audio routing with BT headset during Phase 1 integration.

## Sources

### Primary (HIGH confidence)
- [mediasoup libmediasoupclient API](https://mediasoup.org/documentation/v3/libmediasoupclient/api/) — Device, Transport, Producer, Consumer lifecycle, @async behavior, threading model
- [mediasoup libmediasoupclient Design](https://mediasoup.org/documentation/v3/libmediasoupclient/design/) — Threading model documentation, @async method behavior
- [crow-misia/libmediasoup-android GitHub](https://github.com/crow-misia/libmediasoup-android) — Library repository, verified version 0.21.0 in buildSrc/src/main/java/Maven.kt:5, NDK/CMake versions in core/build.gradle.kts, WebRTC M130 in VERSIONS file
- [Maven Central: libmediasoup-android 0.21.0](https://mvnrepository.com/artifact/io.github.crow-misia.libmediasoup-android/libmediasoup-android) — Version availability confirmed
- [versatica/libmediasoupclient GitHub](https://github.com/versatica/libmediasoupclient) — Upstream C++ library, verified commit 5464591 in tag 3.5.0
- [Android NDK JNI Tips](https://developer.android.com/training/articles/perf-jni) — JNI threading patterns, AttachCurrentThread behavior
- [WebRTC AudioDeviceModule API](https://github.com/maitrungduc1410/webrtc/blob/master/modules/audio_device/g3doc/audio_device_module.md) — AudioDeviceModule configuration, AudioManager integration
- [mediasoup Garbage Collection](https://mediasoup.org/documentation/v3/mediasoup/garbage-collection/) — Cleanup hierarchy, lifecycle management
- [AGP 9.0.0 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) — Compatibility verification
- [AGP 9.0 Migration Guide (NDK restrictions)](https://nek12.dev/blog/en/agp-9-0-migration-guide-android-gradle-plugin-9-kmp-migration-kotlin) — NDK execution in library modules restriction
- [Android Wake Lock Best Practices](https://developer.android.com/develop/background-work/background-tasks/scheduling/wakelock) — Wake lock coordination patterns

### Secondary (MEDIUM confidence)
- [haiyangwu/mediasoup-client-android GitHub](https://github.com/haiyangwu/mediasoup-client-android) — Alternative Android wrapper with example implementations, MediasoupClient.initialize(context) pattern
- [WebRTC Threading Model (Dyte)](https://dyte.io/blog/understanding-libwebrtc/) — WebRTC's 3-thread architecture (signaling/worker/network)
- [WebRTC Android Guide (VideoSDK)](https://www.videosdk.live/blog/webrtc-android) — PeerConnectionFactory initialization patterns
- [WebRTC AudioManager Conflicts (Google Groups)](https://groups.google.com/g/discuss-webrtc/c/Pqag6R7QV2c) — Multiple AudioDeviceModule issue discussions
- [Multiple ADM Issue (Chromium bugs)](https://bugs.chromium.org/p/webrtc/issues/detail?id=2498) — Audio record state mismatch errors
- [JNI Callbacks Guide (Medium)](https://clintpaul.medium.com/jni-on-android-how-callbacks-work-c350bf08157f) — JNI multithreading patterns
- [mediasoup iOS Memory Leak Report](https://github.com/ethand91/mediasoup-ios-client/issues/55) — 30MB leak magnitude
- [mediasoup Reconnection Handling (Discourse)](https://mediasoup.discourse.group/t/recording-reconnection-handling/4907) — Reconnection patterns
- [ProGuard Consumer Rules Guide (Medium)](https://drjansari.medium.com/mastering-proguard-in-android-multi-module-projects-agp-8-4-r8-and-consumable-rules-ae28074b6f1f) — Consumer rules in AAR
- [WebRTC Wake Lock Discussion (Google Groups)](https://groups.google.com/g/discuss-webrtc/c/CHG9ndvMN7M) — Wake lock behavior

### Tertiary (LOW confidence, needs verification)
- [Building WebRTC with MediaSoup (WebRTC.ventures)](https://webrtc.ventures/2022/05/webrtc-with-mediasoup/) — Architecture patterns (general, not Android-specific)
- [runBlocking Caution on Android (GetStream)](https://getstream.io/blog/caution-runblocking-android/) — Threading best practices (general, not mediasoup-specific)
- [Device.load() Codec Issues (GitHub)](https://github.com/haiyangwu/mediasoup-client-android/issues/9) — Anecdotal device-specific failures
- [Chrome Android RTP Capabilities Bug (Discourse)](https://mediasoup.discourse.group/t/weird-issue-with-chrome-android-and-rtpcapabilities-after-device-load/1537) — WebView issue, not native Android
- [Huawei H.264 Encode Limitation (GitHub)](https://github.com/versatica/mediasoup-client/issues/141) — Not applicable to audio-only app

---
*Research completed: 2026-02-13*
*Ready for roadmap: yes*
