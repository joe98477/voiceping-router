# Pitfalls Research: v4.0 Production Hardening

**Domain:** Android PTT app production hardening (location, audio reliability, power, security)
**Researched:** 2026-02-15
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: Android 14+ Location Permission Without Foreground Service Type Declaration

**What goes wrong:**
App crashes with SecurityException when starting foreground service for location tracking, even though ACCESS_FINE_LOCATION is granted. Android 14 introduced strict requirements where foreground services MUST declare specific service types in the manifest AND request matching runtime permissions. Simply having a foreground service is no longer sufficient to access location in the background.

**Why it happens:**
Developers assume that existing foreground services automatically grant location access, not realizing Android 14+ treats location as a privileged operation requiring explicit foreground service type declaration. The app may already have a foreground service for audio, but location requires adding `android:foregroundServiceType="location"` and the `FOREGROUND_SERVICE_LOCATION` permission.

**How to avoid:**
1. Add foreground service type to AndroidManifest.xml:
   ```xml
   <service android:name=".YourService"
            android:foregroundServiceType="location|microphone"
            android:exported="false" />
   ```
2. Declare permission: `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />`
3. Request ACCESS_BACKGROUND_LOCATION separately from ACCESS_FINE_LOCATION on Android 10+
4. Only access location AFTER foreground service is started with proper notification
5. Test on Android 14+ devices specifically — earlier versions won't catch this

**Warning signs:**
- SecurityException with message "Permission Denial: startForeground from background requires foreground service type"
- Crashes only on Android 14+ devices during location initialization
- Location works in foreground but fails when screen turns off
- Google Play pre-launch report flags foreground service type issues

**Phase to address:**
Phase 16 (Location Infrastructure) — must be first step before any location code

---

### Pitfall 2: GPS Lock Battery Drain Without Provider Strategy

**What goes wrong:**
Battery consumption jumps from 5%/hour to 15-25%/hour after adding location tracking. GPS stays locked continuously even when high accuracy isn't needed, draining battery 3-5x faster than necessary. Users complain about excessive battery drain and disable the feature or uninstall the app.

**Why it happens:**
Developers use `PRIORITY_HIGH_ACCURACY` universally without understanding the battery cost. GPS alone can consume 38% of battery when signal is weak (indoors, urban canyons). The fused location provider defaults to continuous GPS if not explicitly configured, and developers don't implement adaptive location strategies based on movement, signal strength, or time-of-day.

**How to avoid:**
1. Start with `PRIORITY_BALANCED_POWER_ACCURACY` (cell tower + WiFi, ~100m accuracy)
2. Only use `PRIORITY_HIGH_ACCURACY` when user is actively transmitting PTT (real-time need)
3. Switch to `PRIORITY_LOW_POWER` when idle for >5 minutes
4. Increase update interval based on movement detection (stationary = slower updates)
5. Stop location updates entirely if user hasn't interacted in 30+ minutes
6. Use geofencing instead of continuous tracking when monitoring fixed areas
7. Monitor battery drain with Battery Historian during development

**Warning signs:**
- Battery usage shows GPS constantly active in Android battery stats
- Location icon in status bar never disappears
- Device gets warm during normal use
- Battery Historian shows uninterrupted GPS wake locks
- User reviews mention "battery killer" within first week

**Phase to address:**
Phase 16 (Location Infrastructure) — implement adaptive strategy from day 1, don't optimize later

---

### Pitfall 3: WebRTC Audio Device Change Race Condition

**What goes wrong:**
Audio cuts out (silence) when Bluetooth headset connects/disconnects during active PTT transmission. The audio track is mid-stream when AudioManager switches devices, causing the Producer to become detached from the audio source. User hears silence, but the app still shows transmitting state. This is especially problematic because VoicePing already has a known intermittent silence bug.

**Why it happens:**
WebRTC's Audio Device Module (ADM) on Android doesn't automatically handle mid-session device changes. When AudioManager routes audio to a different device (e.g., Bluetooth headset connects), the existing MediaStreamTrack becomes stale but doesn't raise an error. The app continues producing audio from a dead source. The native WebRTC implementation expects the application layer to handle device enumeration and switching.

**How to avoid:**
1. Register AudioManager.OnAudioFocusChangeListener and AudioDeviceCallback
2. When device change detected during active PTT:
   - Pause Producer (don't close)
   - Wait for AudioManager routing to stabilize (50-100ms delay)
   - Reinitialize audio capture with new device
   - Resume Producer with new audio track
3. Implement "audio heartbeat" monitoring: if no audio packets sent for >500ms during transmission, trigger device recheck
4. Test specifically: connect/disconnect Bluetooth during PTT hold
5. Add telemetry to track "silent transmission" occurrences in production

**Warning signs:**
- Silence reports increase when Bluetooth headset usage is common
- Logs show Producer active but no audio packets being sent
- Issue reproduces reliably when toggling Bluetooth during PTT
- WebRTC stats show `bytesSent` stops incrementing during transmission
- Users report "sounds fine on my end but others hear nothing"

**Phase to address:**
Phase 18 (Audio Reliability Fixes) — must address BEFORE production, not after user reports

---

### Pitfall 4: Wake Lock + Doze Mode Exemption Breaking Battery Optimization

**What goes wrong:**
App requests battery optimization exemption to ensure real-time delivery, but this PREVENTS Doze mode from ever engaging, causing 2-3x higher battery drain even when idle. Google Play flags the app for excessive battery usage and may display a warning on the store listing. The app gets removed from battery optimization whitelist by aggressive OEM battery managers (Samsung, Xiaomi), breaking functionality unpredictably.

**Why it happens:**
Developers misunderstand Android power management. Requesting `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` disables ALL battery optimization, not just specific restrictions. This keeps CPU and network running at full power continuously. The existing partial wake lock in VoicePing is already exempt from Doze calculations if used for audio playback, so requesting additional exemptions is redundant and harmful.

**How to avoid:**
1. **DO NOT** use `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for PTT apps
2. Audio wake locks are ALREADY exempt from Doze restrictions
3. Use high-priority FCM messages for time-sensitive notifications instead of exemptions
4. If background work is needed, use JobScheduler with `setRequiresBatteryNotLow(false)`
5. Design for Doze windows: buffer audio during maintenance window, resume after
6. Test with `adb shell dumpsys deviceidle force-idle` to verify Doze behavior
7. Monitor Android Vitals "Excessive wake lock" metric (threshold: >2 hours/24hrs)

**Warning signs:**
- Battery usage remains high even when app is idle for hours
- `dumpsys deviceidle` shows device never enters Doze mode
- Play Console shows "Excessive battery usage" warning
- Battery Historian shows continuous wake locks during idle periods
- App appears in "Battery optimization excluded" list without clear user benefit

**Phase to address:**
Phase 19 (Power Optimization) — remove any exemption requests, validate Doze compatibility

---

### Pitfall 5: Certificate Pinning Breaking Production Updates

**What goes wrong:**
App pins TLS certificate for router server, then certificate expires or is rotated during routine renewal. All existing app installations immediately lose connectivity and cannot reach the server. Emergency app update is required, but users can't download it because the Play Store connection might also be affected. Service outage lasts hours to days while users gradually update.

**Why it happens:**
Developers implement certificate pinning as a security best practice without understanding the operational burden. Leaf certificates expire frequently (90 days is becoming standard), and pinning to a single certificate creates a ticking time bomb. When the certificate rotates, every installed app version becomes non-functional until updated. Third-party services (if any) can rotate certificates without warning, breaking app functionality instantly.

**How to avoid:**
1. **Preferred approach for 2026:** Do NOT use certificate pinning for owned infrastructure
2. Modern alternatives (better security, less operational risk):
   - Rely on system PKI trust store
   - Enable Certificate Transparency enforcement
   - Use network security config with domain config instead of pinning
3. If pinning is mandatory (compliance requirement):
   - Pin to intermediate or root CA, NOT leaf certificate
   - Pin to at least 2 certificates (current + backup)
   - Implement remote pin update mechanism (don't require app update)
   - Monitor certificate expiry 30+ days in advance
   - Test certificate rotation in staging environment

**Warning signs:**
- All client connections fail simultaneously after server update
- SSL handshake errors with "Certificate pinning failure" in logs
- Certificate expiry date approaching in next 30 days
- Unable to test certificate rotation without pushing app update
- No backup pinned certificate configured

**Phase to address:**
Phase 20 (Security Hardening) — decide pin strategy BEFORE implementing TLS, or skip pinning entirely

---

### Pitfall 6: Permission Denial Loop Without Rationale Tracking

**What goes wrong:**
User denies location permission once, app repeatedly prompts on every screen transition or app launch, creating hostile UX. Android eventually marks permission as "permanently denied," but app doesn't detect this and continues showing broken permission prompts that do nothing. User frustrated, leaves 1-star review citing "constant permission nagging."

**Why it happens:**
Developers call `requestPermissions()` without checking `shouldShowRequestPermissionRationale()` first. This method returns `true` if user denied permission previously and should see explanation, or `false` if user chose "Don't ask again." Without tracking denial state, app falls into infinite prompt loop. Android 11+ made permission prompts more aggressive, so poor permission UX is amplified.

**How to avoid:**
1. Track permission denial count in SharedPreferences
2. Flow:
   - First ask: Direct permission request (no rationale needed)
   - If denied: Show in-app rationale with clear benefit explanation
   - If denied twice: Stop prompting, show permanent "Enable in Settings" card
3. Use `shouldShowRequestPermissionRationale()` to detect "Don't ask again" state
4. For location: Request while-in-use FIRST, then background permission separately
5. Only request permissions when feature is first used, NOT on app launch
6. Android 13+: Request notification permission contextually, not on startup
7. Gracefully degrade features when permission denied (don't block core functionality)

**Warning signs:**
- Permission dialog appears repeatedly on same screen
- User taps "Deny" but sees prompt again immediately
- App shows permission rationale after "Don't ask again" selected
- No fallback UI when permission permanently denied
- Play Store reviews mention "keeps asking for location"

**Phase to address:**
Phase 21 (Permission Refactoring) — implement denial tracking BEFORE adding new permission requests

---

### Pitfall 7: Network Security Config Allowing Cleartext in Production

**What goes wrong:**
Developer enables `android:cleartextTrafficPermitted="true"` for local testing, accidentally ships to production. App communicates over HTTP (not HTTPS) for some requests, exposing authentication tokens, audio metadata, and user data to network eavesdropping. Security audit or penetration test discovers cleartext traffic, requiring emergency release to fix.

**Why it happens:**
Android 9+ disables cleartext by default, forcing developers to explicitly allow it during development. Developers add global cleartext permission to `network_security_config.xml` or manifest, forget to remove it before release, or use same config for debug and release builds. Build variants not properly configured to use environment-specific network security configs.

**How to avoid:**
1. NEVER use `android:usesCleartextTraffic="true"` in manifest
2. Use build-variant-specific network security configs:
   - `res/xml/network_security_config_debug.xml` (allows cleartext for localhost only)
   - `res/xml/network_security_config_release.xml` (no cleartext allowed)
3. Configure in `build.gradle`:
   ```kotlin
   buildTypes {
       debug {
           manifestPlaceholders["networkSecurityConfig"] =
               "@xml/network_security_config_debug"
       }
       release {
           manifestPlaceholders["networkSecurityConfig"] =
               "@xml/network_security_config_release"
       }
   }
   ```
4. Add CI check: `grep -r "cleartextTrafficPermitted=\"true\"" app/src/main/`
5. Use StrictMode in debug builds to detect cleartext violations early

**Warning signs:**
- HTTP URLs in release build logs
- Security scanner flags cleartext traffic
- Network traffic inspection shows unencrypted HTTP requests
- No TLS handshake in packet captures for API calls
- Build includes network_security_config.xml with `<domain-config cleartextTrafficPermitted="true">`

**Phase to address:**
Phase 20 (Security Hardening) — verify network config BEFORE security audit, not after

---

### Pitfall 8: WebSocket Reconnection During Producer Active State

**What goes wrong:**
Network switches from WiFi to cellular (or vice versa) while user is actively transmitting PTT. WebSocket reconnects successfully, but mediasoup Producer is orphaned on the old connection. App thinks it's transmitting (UI shows active), but audio isn't reaching the server. The existing SignalingClient reconnection logic doesn't coordinate with MediasoupClient state, creating silent failure.

**Why it happens:**
WebSocket reconnection logic in SignalingClient handles transport layer (socket), but doesn't inform MediasoupClient about connection state changes. When WebSocket reconnects, it gets a new socket instance, but the Producer still references the old closed transport. MediasoupClient continues calling producer.send() on a closed peer connection, silently failing. No error propagated because WebRTC doesn't immediately detect closed connections.

**How to avoid:**
1. Implement connection state observer pattern:
   ```kotlin
   interface ConnectionStateObserver {
       fun onConnecting()
       fun onConnected()
       fun onDisconnected()
       fun onFailed()
   }
   ```
2. MediasoupClient subscribes to SignalingClient state changes
3. On disconnect while Producer active:
   - Save PTT state (user still holding button)
   - Close Producer/Transport cleanly
   - After reconnection + rejoin, auto-resume PTT transmission
4. Add connection state to PttState enum:
   - `Transmitting.Connected`
   - `Transmitting.Reconnecting` (show different UI)
5. UI shows "reconnecting" indicator if PTT held during network change
6. Implement end-to-end transmission monitoring: server ACKs audio packets, client detects missing ACKs

**Warning signs:**
- "Sounds fine on my phone but others hear nothing" reports
- Issue reproduces when switching WiFi/cellular during PTT
- Logs show WebSocket reconnect but no new Producer created
- Producer.close() never called during network switch
- UI shows transmitting but server logs show no audio packets received

**Phase to address:**
Phase 18 (Audio Reliability Fixes) — critical for production, affects core PTT functionality

---

### Pitfall 9: Foreground Service Notification Channel Importance Too Low

**What goes wrong:**
Foreground service notification gets swiped away by user or hidden by system because channel importance is set to LOW or MIN. Android kills the foreground service shortly after, terminating audio playback and location tracking. App appears to be running (in recent apps) but is actually dead, user misses incoming PTT messages.

**Why it happens:**
Developers set notification channel importance to LOW to avoid annoying users with sound/vibration. However, Android 8+ ties notification dismissibility to channel importance. LOW importance notifications can be dismissed, which removes foreground service protection. Some OEMs (Samsung, Xiaomi) aggressively hide low-importance notifications, which the system interprets as user dismissal.

**How to avoid:**
1. Use `NotificationManager.IMPORTANCE_LOW` (not MIN) for foreground service channel
2. Set notification as ongoing: `setOngoing(true)` (prevents swipe-to-dismiss)
3. Make notification useful, not annoying:
   - Show current channel name and connection status
   - Add action buttons (mute, disconnect)
   - Update dynamically when PTT active
4. Explain notification purpose in first-run tutorial
5. Android 13+: Request notification permission with contextual rationale
6. Test on Samsung/Xiaomi devices with aggressive battery optimization

**Warning signs:**
- Service killed shortly after app backgrounded
- Users report "app stops working when I swipe away notification"
- Audio playback stops when notification cleared
- Foreground service killed without `onDestroy()` being called
- Service restarts frequently due to system killing it

**Phase to address:**
Phase 17 (Production Infrastructure) — validate notification behavior on multiple OEM devices

---

### Pitfall 10: Audio Track Silence Due to Missing AudioRecord Restart on Resume

**What goes wrong:**
App backgrounds during active monitoring, then returns to foreground. Incoming audio plays fine, but when user tries to transmit PTT, microphone captures silence. AudioRecord is in stopped state or capturing from wrong audio source. Existing VoicePing "intermittent silence bug" may be related to AudioRecord lifecycle not properly synchronized with Producer lifecycle.

**Why it happens:**
Android releases audio resources when app backgrounds. AudioRecord may transition to stopped state or be reclaimed by system. When app resumes, the MediaStreamTrack is still "live" (enabled=true) but not actually capturing audio. WebRTC doesn't automatically restart audio capture after backgrounding. Producer continues sending packets, but they contain silence because AudioRecord isn't running.

**How to avoid:**
1. Implement LifecycleObserver in MediasoupClient:
   ```kotlin
   class MediasoupClient : LifecycleObserver {
       @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
       fun onAppBackgrounded() {
           if (!isForegroundServiceActive) {
               pauseAudioCapture()
           }
       }

       @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
       fun onAppForegrounded() {
           resumeAudioCapture()
       }
   }
   ```
2. Before starting Producer, verify AudioRecord state is RECORDSTATE_RECORDING
3. Add audio level monitoring: if capture level = 0 for >200ms, restart AudioRecord
4. Use MediaRecorder.AudioSource.VOICE_COMMUNICATION (optimized for PTT)
5. Request audio focus before capture: `AudioManager.requestAudioFocus()`
6. Implement watchdog: if Producer active but no audio samples for 500ms, trigger restart

**Warning signs:**
- Users report "mic doesn't work after putting app in background"
- Issue reproduces after app pause/resume cycle
- Waveform visualization shows flat line during transmission
- WebRTC stats show packets sent but bytesPerSecond = 0
- AudioRecord.getRecordingState() returns RECORDSTATE_STOPPED during capture
- Known "intermittent silence" bug reports correlate with app lifecycle events

**Phase to address:**
Phase 18 (Audio Reliability Fixes) — critical fix for existing known bug, high priority

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip battery optimization testing, ship and monitor in production | Faster release, defer optimization | Play Store warnings, user complaints, 1-star reviews, emergency patches | Never — battery is top user complaint for background apps |
| Use global wake lock exemption instead of FCM high-priority | Simpler implementation, guaranteed delivery | 2-3x battery drain, Play Store flags, user uninstalls | Never — audio wake locks already exempt |
| Pin leaf certificate instead of CA | Easier to implement (single cert) | Service outage on cert expiry, emergency releases | Never — 90-day cert lifetimes make this non-viable |
| Request all permissions on first launch | User granted permissions immediately | High denial rate, poor first impression, users skip onboarding | Never — Android 11+ recommends contextual requests |
| Global cleartext allowed for faster debug iteration | Skip HTTPS setup during development | Security vulnerabilities in production if forgotten | Only in debug builds with build-variant-specific configs |
| Implement location tracking without adaptive strategy | Simpler code, fewer states to manage | 3-5x battery drain, feature disabled by users | Only for MVP proof-of-concept, must optimize before public release |
| Skip Doze mode testing on physical devices | Faster test cycles (emulator only) | Unexpected behavior on real devices, late discovery of power bugs | Only in early prototyping, must test on physical devices before beta |
| Use single network security config for all build types | Less configuration overhead | Cleartext allowed in production accidentally | Never — build variants must have separate configs |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Fused Location Provider | Requesting location without checking Play Services availability | Always check `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable()` before location requests, handle SERVICE_MISSING, SERVICE_VERSION_UPDATE_REQUIRED |
| Android 14+ Location in Foreground Service | Starting service then requesting location | Request ACCESS_FINE_LOCATION FIRST, then start foreground service with type="location", then access location API |
| WebRTC AudioManager | Setting audio mode globally without restoring | Save previous audio mode, restore on release: `savedMode = audioManager.mode; audioManager.mode = MODE_IN_COMMUNICATION; /* later */ audioManager.mode = savedMode` |
| Bluetooth Headset Button | Registering MediaButtonReceiver without priority | Use ordered broadcast with priority > 0, handle in onReceive before system default handler |
| Notification Permission (Android 13+) | Requesting on app launch before user sees value | Request when user enables first notification-worthy feature (e.g., enable scan mode), show rationale first |
| Network Security Config | Testing only on Android 10+, shipping to Android 9 | Android 9 introduced cleartext restrictions, test on API 28 specifically, verify TLS handshake in packet capture |
| Battery Optimization Exemption | Using `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent | Use high-priority FCM for time-sensitive work, rely on audio wake lock exemptions, design for Doze windows |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Continuous GPS lock without movement detection | 15-25% battery/hour, device warm, GPS icon always visible | Use Fused Location Provider with `setMaxWaitTime()` to batch updates, switch to LOW_POWER when stationary for >5min | Immediately on first production use, scales linearly with usage time |
| WebSocket reconnection creating orphaned Producers | Silent audio transmission failures, "sounds fine here but others hear nothing" | Implement connection observer pattern, coordinate WebSocket state with MediasoupClient, close stale Producers | Network switches (WiFi/cellular), intermittent connectivity, affects ~5-10% of transmissions |
| AudioRecord not restarted after app resume | Mic captures silence after backgrounding, flat waveform | Monitor audio levels, restart AudioRecord if silent for >200ms during capture, verify RECORDSTATE_RECORDING before producing | Every background/foreground cycle, affects all transmissions after first background |
| Foreground service notification dismissed by user | Service killed, audio stops, location tracking ends | Set notification channel importance to LOW (not MIN), use `setOngoing(true)`, make notification useful not annoying | Varies by OEM (Samsung/Xiaomi more aggressive), affects long-running sessions |
| Location updates during idle periods | Unnecessary battery drain even when not in use | Stop location updates after 30min of user inactivity, resume on next interaction, use geofencing for area monitoring | After first 30min of idle, cumulative waste over days/weeks |
| Multiple wake locks held simultaneously | Excessive wake lock metric in Play Console (>2hrs/24hrs) | Audit wake lock acquisition, release when audio stops, use reference counting for nested holds | Threshold: >2 cumulative hours in 24hr period triggers Play Store warning |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Cleartext traffic allowed in production build | Authentication tokens, user data exposed to network eavesdropping, MITM attacks | Use build-variant-specific network security configs, CI check for cleartext allowances, test with network traffic inspection |
| Certificate pinning to single leaf certificate | Service outage on cert rotation (90-day expiry), emergency release required | Don't pin (preferred 2026 approach), or pin to CA root + backup, implement remote pin updates |
| No TLS hostname verification | MITM with valid certificate for different domain | Verify OkHttp `hostnameVerifier` uses default strict checking, don't override without strong reason |
| WebSocket over WS instead of WSS in production | All signaling messages (join, PTT state, audio metadata) transmitted in cleartext | Enforce WSS:// URLs in release build, reject WS:// connections, use network security config to block cleartext |
| Location data logged to crash reporting | Precise user location exposed in crash logs, privacy violation | Strip location from exception messages, use coarse location buckets in analytics, comply with data retention policies |
| No rate limiting on PTT transmission | Abusive user can DoS channel by holding PTT indefinitely | Server-side max transmission duration (30-60s), client-side transmission timeout, exponential backoff on repeated long transmissions |
| Audio recordings stored without encryption | Sensitive conversation content accessible if device lost/stolen | Use Android Keystore for encryption keys, encrypt audio files at rest, use FILE_PROVIDER with restricted permissions |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Permission rationale shown after user denies | User already said no, seeing explanation after denial feels pushy and annoying | Show rationale BEFORE first request (if `shouldShowRequestPermissionRationale()` == false), or after first denial (if == true) |
| Generic "Location Permission Required" message | User doesn't understand why PTT app needs location | Explain specific feature benefit: "Share your location with dispatch during emergencies" or "Auto-switch to nearest repeater" |
| No visual indication during network reconnection | User holds PTT, thinks they're transmitting, but audio not sent during reconnect | Show "Reconnecting..." badge on PTT button, vibrate differently when transmission actually starts |
| Battery optimization prompt on first launch | User doesn't trust app yet, high denial rate | Request after user successfully uses app for 3-5 sessions, show value first |
| Foreground notification says "App is running" | Waste of notification space, no useful info | Show current channel, connection status, last speaker, make actionable (mute/disconnect buttons) |
| App appears running but actually killed | User misses messages, thinks app is monitoring but it's not | Implement heartbeat check, show warning if service not actually running, auto-restart service with WorkManager |
| Silent audio failures without user feedback | User transmits, thinks others heard, but audio was silent | Implement transmission acknowledgment (server confirms audio received), show warning if no ACK within 2s |
| No graceful degradation when permissions denied | Core features blocked, user forced to grant or uninstall | Essential features work without location (basic PTT), premium features require permission (location sharing) |

## "Looks Done But Isn't" Checklist

- [ ] **Location Tracking:** Often missing background location permission separate from fine location — verify both `ACCESS_FINE_LOCATION` AND `ACCESS_BACKGROUND_LOCATION` requested on Android 10+
- [ ] **Foreground Service:** Often missing service type declaration for Android 14+ — verify `android:foregroundServiceType="location|microphone"` in manifest AND `FOREGROUND_SERVICE_LOCATION` permission
- [ ] **Network Security Config:** Often allows cleartext in production accidentally — verify build-variant-specific configs, no `cleartextTrafficPermitted="true"` in release
- [ ] **Permission Rationale:** Often requests permissions without checking prior denial — verify `shouldShowRequestPermissionRationale()` checked, denial count tracked, no infinite loops
- [ ] **Battery Optimization:** Often exempts app unnecessarily — verify no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` used, audio wake locks rely on built-in exemptions
- [ ] **Doze Mode Compatibility:** Often breaks during Doze windows — verify tested with `adb shell dumpsys deviceidle force-idle`, app functions in idle mode
- [ ] **Audio Device Changes:** Often doesn't handle Bluetooth connect/disconnect mid-stream — verify tested with headset toggle during PTT, audio recovers gracefully
- [ ] **WebSocket Reconnection Coordination:** Often reconnects socket but not MediasoupClient — verify Producer/Consumer recreated after reconnect, no orphaned streams
- [ ] **Notification Channel Importance:** Often set too low, allows dismissal — verify `IMPORTANCE_LOW` (not MIN), notification is ongoing, tested on Samsung/Xiaomi
- [ ] **Audio Lifecycle:** Often doesn't restart AudioRecord after backgrounding — verify audio capture works after pause/resume cycle, monitor for silent captures

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Certificate pinning breaks production | HIGH — emergency release required, 24-48hr user migration | 1. Deploy new certificate to server. 2. Release emergency app update with new pin + old pin. 3. Monitor adoption rate. 4. After 95% updated, rotate server cert. 5. Future: remove pinning entirely |
| Cleartext traffic in production | MEDIUM — release required, no downtime | 1. Emergency release with network_security_config enforcing TLS. 2. No server changes needed if already using HTTPS. 3. Release within 24hrs to minimize exposure |
| Location permission missing service type | LOW — app update only | 1. Add foreground service type to manifest. 2. Add FOREGROUND_SERVICE_LOCATION permission. 3. Release update. No server changes needed |
| Battery optimization exemption granted | MEDIUM — requires user action | 1. Release update removing exemption request. 2. Can't revoke already-granted exemptions programmatically. 3. Educate users to manually disable in Settings > Battery Optimization |
| GPS battery drain excessive | LOW — app update only | 1. Implement adaptive location strategy (HIGH_ACCURACY → BALANCED_POWER). 2. Add idle detection. 3. Release update. Users see immediate battery improvement |
| Audio device change causes silence | MEDIUM — requires testing multiple device types | 1. Implement AudioDeviceCallback. 2. Add Producer restart logic. 3. Test on multiple Bluetooth headsets (different manufacturers). 4. Monitor telemetry for silent transmission events |
| WebSocket reconnect orphans Producer | MEDIUM — coordination logic needed | 1. Implement ConnectionStateObserver pattern. 2. Add Producer/Consumer lifecycle coordination. 3. Test network switch scenarios. 4. Add end-to-end monitoring |
| Permission denial loop annoys users | LOW — UX improvement only | 1. Track denial count in SharedPreferences. 2. Add "Don't ask again" detection. 3. Show permanent "Enable in Settings" card after 2 denials. 4. Release update |
| Foreground notification dismissed | LOW — notification configuration change | 1. Set channel importance to LOW (not MIN). 2. Add `setOngoing(true)`. 3. Make notification useful (show status). 4. Release update |
| AudioRecord not restarted after resume | MEDIUM — lifecycle handling needed | 1. Implement LifecycleObserver in MediasoupClient. 2. Add audio level monitoring. 3. Restart AudioRecord if silent >200ms during capture. 4. Test pause/resume cycles extensively |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Android 14+ location permission crash | Phase 16 (Location Infrastructure) | Test on Android 14+ device, verify no SecurityException when service starts, check manifest has foregroundServiceType |
| GPS battery drain | Phase 16 (Location Infrastructure) | Run Battery Historian for 1hr session, verify <8% battery drain, GPS not constantly locked |
| Audio device change silence | Phase 18 (Audio Reliability Fixes) | Connect/disconnect Bluetooth during PTT hold, verify audio recovers, remote user hears continuous audio |
| Wake lock + Doze exemption | Phase 19 (Power Optimization) | Force Doze mode with adb, verify app still functions, check Android Vitals for excessive wake lock warnings |
| Certificate pinning breaks updates | Phase 20 (Security Hardening) | If pinning implemented: pin to CA not leaf, include backup pin, test cert rotation in staging |
| Permission denial loop | Phase 21 (Permission Refactoring) | Deny permission 3 times, verify no infinite prompts, "Enable in Settings" card shown after 2nd denial |
| Cleartext traffic in production | Phase 20 (Security Hardening) | Inspect release APK network_security_config.xml, verify no cleartext allowed, test with packet capture |
| WebSocket reconnect orphans Producer | Phase 18 (Audio Reliability Fixes) | Switch WiFi/cellular during PTT, verify transmission continues after reconnect, server receives audio |
| Notification dismissed kills service | Phase 17 (Production Infrastructure) | Swipe notification on Samsung device, verify service stays running, notification reappears as ongoing |
| AudioRecord not restarted | Phase 18 (Audio Reliability Fixes) | Background app for 30s, return, transmit PTT, verify mic captures audio (not silence), waveform shows activity |

## Sources

### Location & Battery
- [About background location and battery life | Android Developers](https://developer.android.com/develop/sensors-and-location/location/battery)
- [Background location usage best practices | Google Developers](https://developers.google.com/maps/documentation/navigation/android-sdk/background-location-usage)
- [Background Location Limits Over Different Android Versions | Medium](https://medium.com/@mahbooberezaee68/background-location-limits-over-different-android-versions-df67202250bd)
- [Restrictions on starting a foreground service from the background | Android Developers](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Foreground service types are required | Android Developers](https://developer.android.com/about/versions/14/changes/fgs-types-required)

### Power Management & Doze
- [Optimize for Doze and App Standby | Android Developers](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Excessive partial wake locks | Android Developers](https://developer.android.com/topic/performance/vitals/excessive-wakelock)
- [Android addressing 'excessive' battery drain with new app wake locks metric](https://9to5google.com/2025/04/15/android-excessive-battery-drain-wake-locks/)

### WebRTC & Audio
- [What reasons for silent audio tracks from remote streams? | W3C GitHub Issue #2564](https://github.com/w3c/webrtc-pc/issues/2564)
- [Intermittent WebRTC audio fade out issue | discuss-webrtc](https://groups.google.com/g/discuss-webrtc/c/fgJEv_Ziy_g)
- [What is the proper way of handling audio device changes mid session? | discuss-webrtc](https://groups.google.com/g/discuss-webrtc/c/v69XTuM3Shw)
- [Audio device handling is poor with WebRTC | Mozilla Fenix Issue #16653](https://github.com/mozilla-mobile/fenix/issues/16653)
- [WebRTC Issues and How to Debug Them | CloudBees](https://www.cloudbees.com/blog/webrtc-issues-and-how-to-debug-them)

### Security
- [Security with network protocols | Android Developers](https://developer.android.com/privacy-and-security/security-ssl)
- [Network security configuration | Android Developers](https://developer.android.com/privacy-and-security/security-config)
- [Cleartext communications | Android Developers](https://developer.android.com/privacy-and-security/risks/cleartext-communications)
- [Android SSL Certificate Pinning A Practical Guide | NextNative](https://nextnative.dev/blog/android-ssl-certificate-pinning)
- [Avoiding downtime: modern alternatives to outdated certificate pinning practices | Cloudflare](https://blog.cloudflare.com/why-certificate-pinning-is-outdated/)
- [The Obsolescence of SSL Pinning in Mobile App Security](https://caverav.cl/posts/ssl-pinning/ssl-pinning/)

### Permissions
- [Request runtime permissions | Android Developers](https://developer.android.com/training/permissions/requesting)
- [App permissions best practices | Android Developers](https://developer.android.com/training/permissions/usage-notes)
- [Permission Denials | Android Vitals](https://developer.android.com/topic/performance/vitals/permissions)
- [Better permissions on Android | Sid Patil](https://siddroid.com/post/post-android-rationale-permission-dialogs-2020/)

### Production & Monitoring
- [Websocket closes connection with EOF exception | OkHttp Issue #4012](https://github.com/square/okhttp/issues/4012)
- [Okhttp Websocket client crashes Android application | Ktor Issue #1356](https://github.com/ktorio/ktor/issues/1356)
- [Build Real-Time Android Apps with WebSockets and Kotlin | Bugfender](https://bugfender.com/blog/android-websockets/)
- [Foreground Services with Notification Channel | Android Developers](https://medium.com/huawei-developers/foreground-services-with-notification-channel-in-android-7a272f07ad1)

---

*Pitfalls research for: v4.0 Production Hardening (Location, Audio Reliability, Power, Security)*
*Researched: 2026-02-15*
*Focus: Integration pitfalls when adding production features to existing Android PTT app*
