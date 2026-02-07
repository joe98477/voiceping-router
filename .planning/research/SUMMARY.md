# Project Research Summary

**Project:** VoicePing Android Native PTT Client
**Domain:** Mobile PTT communications (Android native client for existing mediasoup WebRTC server)
**Researched:** 2026-02-08
**Confidence:** MEDIUM

## Executive Summary

Building an Android native PTT client for the existing mediasoup-based VoicePing server requires a hybrid stack combining JNI-wrapped C++ libraries (libmediasoup-android) with modern Kotlin/Jetpack Compose for UI. The client will integrate with the existing WebSocket signaling protocol and WebRTC infrastructure without requiring any server changes. The recommended approach uses crow-misia/libmediasoup-android (most actively maintained wrapper), OkHttp for WebSocket connectivity, and a foreground service architecture to enable "pocket radio" functionality with lock-screen PTT operation.

The critical path focuses on three high-value features: (1) lock screen PTT operation for hands-free field use, (2) scan mode with automatic channel switching for dispatcher workflow, and (3) hardware button mapping for true walkie-talkie experience. These differentiators require careful audio pipeline management, with multiple concurrent WebRTC connections (one per monitored channel) coordinated through a state machine that handles priority-based audio routing.

The largest deployment risk is OEM battery optimization killing foreground services despite proper configuration (Xiaomi, Samsung, Huawei are notorious). Even with correct implementation, users must manually whitelist the app in device-specific battery settings. The second critical risk is mediasoup-client native library compatibility — third-party Android wrappers have known ABI fragmentation issues that require early validation with physical devices. Other key risks include Bluetooth SCO audio routing race conditions (2-3 second connection latency cuts off first words), WebRTC native object memory leaks in multi-channel scenarios, and cellular NAT traversal failures requiring TURN relay.

## Key Findings

### Recommended Stack

The Android client uses a proven stack of industry-standard libraries with one critical dependency: libmediasoup-android (JNI wrapper for mediasoup C++ client). Core networking uses OkHttp 4.12.0 for WebSocket signaling and Retrofit 2.11.0 for REST API authentication. WebRTC is provided by GetStream's webrtc-android 1.3.9 (maintained builds of Google's WebRTC library, which Google stopped publishing for Android in 2018). UI layer uses Jetpack Compose 1.10.0 with Material 3 for declarative reactive interfaces. Background operation relies on Media3 MediaSessionService for foreground service lifecycle and audio focus management.

**Core technologies:**
- **libmediasoup-android 0.21.0** (crow-misia): JNI wrapper for mediasoup C++ client — most actively maintained with Maven Central publishing, 610 commits, last updated May 2025
- **OkHttp 4.12.0**: WebSocket client for signaling protocol — industry standard, fixes memory leaks from 4.11, powers Retrofit
- **GetStream webrtc-android 1.3.9**: Pre-compiled WebRTC library — maintained builds with Jetpack Compose integration, avoids 50GB+ build-from-source requirement
- **Jetpack Compose 1.10.0**: UI framework — declarative reactive UI better suited for dynamic PTT states than XML layouts
- **Media3 MediaSessionService 1.5.0**: Foreground service audio — replaces deprecated MediaSession, handles notifications/audio focus/lifecycle automatically
- **Hilt 2.51.1**: Dependency injection — Google-recommended DI with compile-time safety for ViewModels and singleton services

**Build system:** Android Gradle Plugin 9.0.0 + Kotlin 2.3.10, min SDK 26 (Android 8.0, 89% coverage), target SDK 35 (Android 15, Google Play requirement Feb 2026).

**Critical decision:** Use crow-misia/libmediasoup-android over haiyangwu fork (last updated 2021, unmaintained, known arm64-v8a crashes on Android 10+). Acceptance criteria: must successfully connect to existing server and work on 3+ physical devices before proceeding past Phase 1.

### Expected Features

The Android client delivers feature parity with the web client for core PTT functionality, plus Android-specific enhancements for field worker use cases. Research identified clear categories: table stakes (expected by users), differentiators (competitive advantages), and anti-features (explicit scope exclusions).

**Must have (table stakes):**
- Hardware PTT button mapping (volume keys, Bluetooth headset buttons, dedicated PTT buttons on rugged phones) — industry standard for PTT apps
- Lock screen PTT operation (foreground service with wake lock, screen-off audio) — critical for "pocket radio" use case where device stays in pocket
- Foreground service for background audio — required for PTT audio playback when app backgrounded or screen off
- Channel list with activity indicators — visual feedback showing who's talking on which channel
- Audio routing control (speaker vs earpiece vs Bluetooth) — default to speaker for walkie-talkie convention, auto-switch to Bluetooth when connected
- Auto-reconnect with session recovery — cellular network drops common in field environments, must recover gracefully
- Per-channel volume control — different channels need different volume (noisy warehouse vs quiet office)
- Busy state indicators — visual feedback when channel busy (someone else talking), disable PTT button

**Should have (competitive differentiators):**
- **Scan mode with auto-switch** — monitor up to 5 channels simultaneously, auto-switch to active channel, drop back to primary when transmission ends (killer feature for dispatch/field coordination, like two-way radio scan mode)
- **Scan mode visual bottom bar** — persistent UI showing all monitored channels with real-time activity indicators, tap to manually switch
- **Bluetooth headset PTT with dual-button support** — map primary/secondary buttons to different channels/functions (competitive advantage over Zello)
- Instant channel monitoring (no manual join flow) — add channel to scan list with one tap, reduces friction
- Network quality indicator — real-time latency/jitter/packet loss display, helps troubleshoot connectivity
- Haptic feedback for PTT events — vibrate on press/release/busy state, improves tactile UX for lock-screen use
- Emergency broadcast override — dispatch force-transmit to all monitored channels (server already supports this)

**Defer (v2+):**
- Persistent transmission history — storage bloat, privacy concerns, not core PTT use case (keep only recent 10-20 in memory, clear on restart)
- In-app messaging/chat — feature creep, VoicePing is voice-first (link to external chat if needed)
- GPS tracking/location sharing — privacy nightmare, battery drain, regulatory complexity (integrate with external MDM instead)
- Recording/playback — legal liability, storage complexity, privacy concerns (server-side with compliance framework if needed)

### Architecture Approach

The Android client implements clean architecture with three layers (data/domain/presentation) and a foreground service boundary separating lifecycle-independent operations from UI. The service manages multiple ConnectionManager instances (one per monitored channel), each maintaining an independent WebSocket connection and mediasoup WebRTC transport pair. This mirrors the web client architecture but scales to support dispatcher workflow (5+ simultaneous channels).

**Major components:**
1. **SignalingClient (data/network/)** — WebSocket connection to `/ws`, JSON message send/receive with request-response correlation using pending requests map. Uses OkHttp WebSocket with exponential backoff reconnection (ReconnectingSignalingClient wrapper).
2. **ConnectionManager (data/network/)** — Orchestrates signaling + media for ONE channel. Lifecycle: init → loadDevice → joinChannel → createTransports → ready for PTT. Handles SPEAKER_CHANGED broadcasts to create/pause/resume consumers based on active speaker.
3. **MediasoupDevice (data/network/)** — Wrapper around libmediasoupclient Device, creates Transports/Producers/Consumers. Shared singleton instance across all ConnectionManagers (one Device, multiple Transports).
4. **PttService (service/)** — Foreground service holding Map<channelId, ConnectionManager>, scan mode state machine, hardware PTT button receiver. Exposes state via Binder to UI ViewModels. Runs independently of Activity lifecycle for background operation.
5. **ScanModeManager (service/)** — State machine (Off/Monitoring/Active) coordinating multi-channel audio switching. Monitors all channels for speaker changes, auto-switches to active channel, returns to primary after timeout. Implements priority-based audio routing.
6. **AudioPlaybackManager (data/audio/)** — Manages WebRTC Consumer → AudioTrack playback. Handles pause/resume for scan mode channel switching. Android automatically mixes up to 5 AudioTrack instances (no manual mixing required for scan mode).

**Integration with existing server:** NO server changes required. Uses existing WebSocket protocol at `/ws`, same JSON message format (JOIN_CHANNEL, PTT_START, SPEAKER_CHANGED, etc.), existing mediasoup 3.19 RTP capabilities negotiation. Each monitored channel = separate WebSocket connection with separate ClientContext on server (matches dispatcher web client pattern).

**Key pattern:** Service-bound architecture keeps network/media in foreground service, UI observes state via StateFlow. Hardware PTT button broadcasts forward to service via Intent. ViewModels never directly manage WebRTC objects (lifecycle mismatch with Activity).

### Critical Pitfalls

Research identified 13 pitfalls across severity levels. Top 5 by impact:

1. **mediasoup-client Native Build Fragmentation** — Third-party wrappers (haiyangwu, crow-misia) have known crashes on specific ABIs (arm64-v8a on Android 10+), version mismatches between libwebrtc and libmediasoupclient cause build failures or runtime crashes. PREVENTION: Phase 1 acceptance gate with physical device testing (3+ devices, different OEMs, API levels). Pin NDK/WebRTC/wrapper versions together, never upgrade independently. Fallback: WebView hybrid approach if no stable wrapper exists.

2. **OEM Battery Optimization Kills Foreground Services** — Despite proper foreground service implementation, OEM battery savers (Xiaomi MIUI, Samsung "Sleeping Apps", Huawei PowerGenie) kill apps after 5-10 minutes screen-off regardless of foreground service status. Field workers miss critical PTT messages. PREVENTION: OEM-specific battery optimization detection and mandatory whitelist setup wizard on first launch (Xiaomi: Autostart + Battery Optimization + App Pinning; Samsung: remove from Sleeping Apps; Huawei: App Launch Manager manual control). Document device-specific steps with screenshots.

3. **Bluetooth SCO Audio Routing Race Conditions** — Bluetooth SCO connection takes 500ms-2s to establish. If PTT transmission starts before SCO ready, audio routes to phone speaker instead of headset. First 1-2 seconds of transmission cut off. PREVENTION: Wait for SCO_AUDIO_STATE_CONNECTED before transmitting, add 200ms pre-roll countdown ("Connecting to headset..."). Alternative: pre-start SCO when Bluetooth headset connects and keep alive for session duration (trades battery for zero-latency).

4. **WebRTC Native Object Memory Leaks** — PeerConnection/MediaStream/MediaStreamTrack backed by C++ objects not freed by Java GC. Must explicitly call .dispose() in correct order. Multi-channel context: 5 channels × 10 reconnects = 50+ leaked PeerConnections if not disposed. App crashes after 30-60 minutes with OutOfMemoryError. PREVENTION: Strict lifecycle management with disposal order: peerConnection.close() → stream.dispose() → track.dispose() → peerConnection.dispose(). Track created vs disposed count in debug builds, use LeakCanary for native leak detection.

5. **Multi-Channel Audio Mixing Thread Contention** — 5 simultaneous PeerConnections delivering audio buffers every 20ms. Naive mixing (separate AudioTrack per channel) causes stuttering/glitches. Manual mixing in callback adds latency (50-200ms) and CPU overhead (40%+ on low-end devices). PREVENTION: Use priority-based selective playback (only play ONE channel at a time) for MVP simplicity. Advanced: custom AudioTrack mixer with Oboe for low-latency mixing, or single PeerConnection with multiplexed tracks (requires server-side mixer or client track multiplexing).

**Additional moderate risks:** Android API fragmentation for foreground services (API 26/28/31/34 have different permission requirements), WebSocket heartbeat timing in Doze mode (timers get deferred, connection drops), cellular NAT traversal failures (requires TURN relay for Carrier-Grade NAT), volume button capture conflicts with system volume (Select to Speak accessibility feature breaks PTT).

## Implications for Roadmap

Based on research, recommended 6-phase build order prioritizing technical de-risking (mediasoup integration validation) followed by core PTT functionality, then advanced features. Each phase has clear deliverables and avoids specific pitfalls.

### Phase 1: WebRTC Foundation (Week 1-2)
**Rationale:** Validate mediasoup-client Android wrapper compatibility BEFORE building features on top. De-risk critical technical assumption (third-party JNI wrapper works with existing server).
**Delivers:** Minimal console app connects to server, joins channel, receives audio via Consumer, logs all signaling messages.
**Uses:** libmediasoup-android 0.21.0, OkHttp WebSocket, MediasoupDevice wrapper
**Addresses:** Pitfall #1 (native build fragmentation), #4 (memory leaks — establish disposal patterns early), #13 (Opus config)
**Acceptance criteria:** Successful build + connection to existing server + tested on 3+ physical devices (Samsung, Google Pixel, OnePlus/Oppo) with different API levels (26, 31, 34).
**Research flag:** LOW — if wrapper fails acceptance criteria, need deeper research on alternatives (haiyangwu fork, custom JNI, WebView hybrid).

### Phase 2: PTT Transmission (Week 2-3)
**Rationale:** Complete bidirectional audio pipeline (transmit + receive) for single-channel use case. Establishes core PTT flow.
**Delivers:** App can transmit PTT (mic → server) and receive PTT (server → speaker) in single channel. Basic Compose UI with PTT button, speaker indicator.
**Uses:** WebRTC PeerConnectionFactory for audio capture, AudioCaptureManager, Producer/Consumer lifecycle
**Addresses:** Table stakes features (PTT transmission, busy state indicators), Pitfall #6 (API fragmentation — implement foreground service type early)
**Research flag:** NONE — standard WebRTC patterns, well-documented.

### Phase 3: Foreground Service & Background Operation (Week 3-4)
**Rationale:** Enable "pocket radio" functionality (lock screen PTT, background audio). Critical for field worker use case.
**Delivers:** App runs in background with foreground service notification, WebSocket stays alive when screen off, hardware PTT button (intent-based) triggers transmission.
**Uses:** Media3 MediaSessionService, PttService with Binder pattern, HardwareButtonReceiver broadcast receiver
**Addresses:** Table stakes (lock screen PTT, foreground service, hardware button mapping), Pitfall #2 (OEM battery killers — implement detection + whitelist wizard), #7 (WebSocket Doze — use AlarmManager for heartbeat)
**Research flag:** MEDIUM — OEM-specific battery optimization requires testing on Xiaomi/Samsung/Huawei devices to validate setup steps.

### Phase 4: Multi-Channel Support (Week 4-5)
**Rationale:** Foundation for scan mode. Multiple simultaneous connections required for dispatcher workflow.
**Delivers:** App monitors 3 channels simultaneously, receives audio from all, shows per-channel connection state.
**Uses:** Map<channelId, ConnectionManager>, shared MediasoupDevice instance, channel list UI (Compose)
**Addresses:** Differentiator (instant channel monitoring), Pitfall #4 (memory leaks — test repeated join/leave cycles), #5 (audio mixing — validate Android's automatic mixing with 3 streams)
**Research flag:** MEDIUM — Multi-channel audio mixing behavior needs validation. If Android mixing fails with 3+ streams, need research on custom mixer or Oboe.

### Phase 5: Scan Mode & Auto-Switch (Week 5-6)
**Rationale:** Killer feature differentiator. Enables dispatcher workflow (monitor 5 channels, auto-switch to active).
**Delivers:** Scan mode with primary channel, auto-switch to active channel, return to primary after timeout. Bottom bar UI with monitored channel indicators.
**Uses:** ScanModeManager state machine, AudioPlaybackManager pause/resume coordination
**Addresses:** Differentiators (scan mode auto-switch, visual bottom bar), Pitfall #5 (multi-channel mixing — priority-based selective playback reduces complexity)
**Research flag:** LOW — State machine pattern is straightforward, audio switching leverages Phase 4 foundation.

### Phase 6: Bluetooth & Hardware Integration (Week 6-7)
**Rationale:** Advanced hardware integration after core audio works. Bluetooth is complex and device-specific.
**Delivers:** Bluetooth SCO audio routing, Bluetooth headset PTT button support, volume key PTT mapping (with accessibility detection).
**Uses:** AudioManager.startBluetoothSco(), SCO_AUDIO_STATE_UPDATED receiver, KeyEvent interception
**Addresses:** Table stakes (audio routing control, Bluetooth button mapping), Differentiator (dual-button support), Pitfall #3 (Bluetooth SCO race — implement pre-roll countdown), #9 (volume button conflicts)
**Research flag:** HIGH — Bluetooth PTT button event handling is manufacturer-specific (Plantronics vs Jabra vs cheap AliExpress headsets send different KeyCodes). Need physical device testing to build compatibility mapping.

### Phase 7: Network Resilience & Polish (Week 7-8)
**Rationale:** Production readiness after core features proven. Address cellular edge cases and error handling.
**Delivers:** Cellular NAT traversal (TURN), network change handling (WiFi ↔ cellular), error states (PTT denied, reconnecting), settings screen, battery usage profiling.
**Addresses:** Pitfall #8 (cellular NAT — verify TURN server configured), #11 (audio focus loss during phone calls), #12 (wake lock battery drain — release during idle)
**Research flag:** MEDIUM — Cellular NAT behavior requires testing on AT&T/Verizon/T-Mobile SIM cards. TURN server capacity needs verification for production load.

### Phase Ordering Rationale

**Technical de-risking first:** Phase 1 validates the riskiest assumption (third-party mediasoup wrapper compatibility) before investing in UI/features. If wrapper fails, pivot to WebView hybrid or custom JNI is possible at minimal cost.

**Sequential dependency chain:** Each phase builds on previous foundation:
- Phase 1 (WebRTC) → Phase 2 (PTT) → Phase 3 (Background) → Phase 4 (Multi-channel) → Phase 5 (Scan mode)
- Cannot implement scan mode without multi-channel, cannot have multi-channel without PTT working, cannot have PTT without WebRTC foundation.

**Bluetooth deferred:** Phase 6 (Bluetooth) parallelizes risk — core app works with on-screen PTT button even if Bluetooth integration proves difficult. Bluetooth is device-specific and can iterate based on field testing.

**Polish last:** Phase 7 addresses edge cases and production hardening after core features proven. Cellular testing requires physical SIM cards and real-world field conditions.

**Pitfall avoidance:** Early phases establish patterns that prevent later pitfalls:
- Phase 1: Memory leak disposal patterns, Opus config
- Phase 2: Foreground service type (API fragmentation)
- Phase 3: OEM battery optimization detection
- Phase 4: Multi-channel memory leak testing
- Phase 5: Priority-based audio (simpler than full mixing)
- Phase 6: Bluetooth SCO pre-roll (race condition)
- Phase 7: TURN relay (cellular NAT)

### Research Flags

**Phases needing deeper research during planning:**
- **Phase 1:** If crow-misia wrapper fails acceptance testing → research alternatives (haiyangwu fork stability, custom JNI feasibility, WebView hybrid trade-offs)
- **Phase 4:** If Android audio mixing glitches with 3+ streams → research custom mixer (Oboe library, manual AudioTrack mixing, buffer management)
- **Phase 6:** Bluetooth PTT button event mapping → research manufacturer-specific SDKs (Pryme, Flic, Seecode), HID protocol variations, KeyCode compatibility matrix
- **Phase 7:** Cellular NAT traversal failures → research TURN server capacity planning, carrier-specific NAT policies, ICE timeout tuning

**Phases with standard patterns (skip research-phase):**
- **Phase 2:** WebRTC audio capture/playback — official WebRTC documentation comprehensive
- **Phase 3:** Foreground service — official Android docs cover all API versions
- **Phase 5:** State machine — standard pattern, no domain-specific complexity

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM | WebRTC/OkHttp/Compose verified with official sources. libmediasoup-android wrapper actively maintained (May 2025) but requires acceptance testing to confirm compatibility with server. ABI coverage and version pinning critical. |
| Features | HIGH | Table stakes/differentiators validated against competitor analysis (Zello, WAVE PTX). Scan mode pattern verified with two-way radio documentation. Anti-features clear from PTT domain research. |
| Architecture | MEDIUM | Clean architecture + service-bound pattern is proven Android approach. Multi-ConnectionManager design mirrors web client dispatcher pattern. Audio mixing behavior needs validation (Android's automatic AudioTrack mixing with 5 streams unconfirmed). |
| Pitfalls | HIGH | OEM battery killers, WebRTC memory leaks, Bluetooth SCO races confirmed across multiple issue trackers (Signal, flutter-webrtc, WebRTC Chromium bugs). Cellular NAT traversal is well-known WebRTC challenge. Volume button conflicts confirmed by Google. |

**Overall confidence: MEDIUM**

Stack and architecture are sound, but two critical dependencies require validation:
1. libmediasoup-android wrapper compatibility (mitigate with Phase 1 acceptance gate)
2. OEM battery optimization behavior (mitigate with mandatory whitelist setup wizard)

### Gaps to Address

**Multi-channel audio mixing behavior:** Research assumes Android automatically mixes multiple AudioTrack instances without glitches. This needs validation in Phase 4. If mixing fails:
- **Fallback A:** Priority-based selective playback (only play one channel at a time) — simpler but loses multi-channel awareness
- **Fallback B:** Implement custom mixer with Oboe library — adds complexity but enables true simultaneous playback
- **Decision point:** Phase 4 acceptance criteria should include audio quality test with 3+ simultaneous streams

**Bluetooth PTT button compatibility:** Research found evidence of HID protocol support but manufacturer-specific variations (Plantronics vs Jabra vs cheap headsets). Phase 6 needs physical device procurement for testing.
- **Approach:** Build flexible button mapping system (detect any KeyCode), provide "Capture Button" UI for user configuration
- **Document:** Compatibility matrix of tested headsets (Bluetooth HID buttons, Motorola/Zebra rugged phone buttons)

**TURN server capacity:** Research assumes existing server has TURN configured, but capacity for 30%+ of Android clients on cellular is unknown.
- **Validation:** Phase 7 should include TURN server capacity planning (concurrent relays, bandwidth budget)
- **Monitoring:** Track ICE candidate types in production (relay % indicates cellular penetration)

**Cellular carrier NAT diversity:** AT&T/Verizon/T-Mobile have different NAT policies. Research found evidence of Dual-SIM STUN storms.
- **Testing:** Phase 7 requires physical SIM cards from multiple carriers for real-world validation
- **Configuration:** May need per-carrier ICE timeout tuning based on field data

## Sources

### Primary (HIGH confidence)
- [libmediasoup-android GitHub](https://github.com/crow-misia/libmediasoup-android) — wrapper maintenance status, commit history, issue tracker
- [mediasoup Official Documentation](https://mediasoup.org/documentation/v3/mediasoup-client/api/) — client API reference, RTP capabilities negotiation
- [Android Developers: Foreground Services](https://developer.android.com/develop/background-work/services/foreground-services) — official API requirements per Android version
- [Android Developers: Media3 MediaSessionService](https://developer.android.com/media/media3/session/background-playback) — foreground service audio patterns
- [WebRTC Native Code Android](https://webrtc.github.io/webrtc-org/native-code/android/) — official WebRTC Android integration guide
- [Jetpack Compose December 2025 release](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html) — Compose 1.10 + Material 3 1.4 stability
- [Android Gradle Plugin 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) — build system requirements

### Secondary (MEDIUM confidence)
- [Zello PTT Android Options Guide](https://support.zello.com/hc/en-us/articles/230749107-Android-Options-Guide) — competitor feature reference, UX patterns
- [WAVE PTX Mobile App documentation](https://www.airwavecommunication.com/wave-ptx-ptt/wave-ptx-mobile-app.htm) — dispatcher workflow patterns
- [Signal Android Bluetooth issue #6184](https://github.com/signalapp/Signal-Android/issues/6184) — Bluetooth SCO race condition evidence
- [flutter-webrtc audio routing issue #811](https://github.com/flutter-webrtc/flutter-webrtc/issues/811) — WebRTC audio routing challenges
- [WebRTC Chromium bug: MediaStream dispose fails](https://bugs.chromium.org/p/webrtc/issues/detail?id=5128) — memory leak evidence
- [Don't kill my app! (Xiaomi/Samsung/Huawei)](https://dontkillmyapp.com/) — OEM battery optimization documentation
- [100ms.live: Local Audio Streaming in Android](https://www.100ms.live/blog/webrtc-audio-streaming-android) — multi-channel audio mixing approach
- [Tait Radio Academy: How Scanning Works](https://www.taitradioacademy.com/topic/how-scanning-works-1/) — two-way radio scan mode reference
- [mediasoup Discourse: Multiple consumers in single RecvTransport](https://mediasoup.discourse.group/t/using-multiple-consumers-in-a-single-recvtransport/375) — architecture pattern validation

### Tertiary (LOW confidence, needs validation)
- Android AudioTrack mixing behavior with 5 simultaneous streams — no official documentation found, needs Phase 4 testing
- Battery consumption with 5 concurrent WebSocket connections — no benchmarks found, needs profiling
- Bluetooth PTT button KeyCodes on non-Zebra devices — vendor-specific, needs Phase 6 hardware testing
- TURN server capacity for production load — depends on deployment, needs Phase 7 validation

---
*Research completed: 2026-02-08*
*Ready for roadmap: yes*
