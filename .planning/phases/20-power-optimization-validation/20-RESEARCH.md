# Phase 20: Power Optimization & Validation - Research

**Researched:** 2026-02-16
**Domain:** Android power management, battery profiling, adaptive polling
**Confidence:** HIGH

## Summary

Phase 20 implements adaptive power management with wake locks, network polling, and location tracking optimizations to achieve <6%/hour battery consumption (up from v3.0 baseline of 5%/hour to account for new location tracking overhead). The phase involves coordinated power systems where wake lock state cascades to location tracking frequency and network polling adjusts dynamically per channel activity.

The implementation uses Android PowerManager partial wake locks with configurable timeout-based release (server-controlled, default 300s), per-channel network polling (5s active, 15s idle), and location tracking multipliers tied to wake lock state and battery saver mode. Validation uses Android Battery Historian for detailed subsystem power breakdown.

**Primary recommendation:** Implement wake lock timeout manager with Handler-based delayed release pattern, use per-channel polling timers tracking last activity independently, integrate location multiplier directly into existing MotionDetector tier system, and leverage Android's ACTION_POWER_SAVE_MODE_CHANGED broadcast for battery saver detection.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Wake lock behavior:**
- Release wake lock after configurable timeout of no audio activity (no transmit AND no incoming audio across all monitored channels)
- Timeout is server-configurable per user group, delivered via existing auth response payload
- Default timeout: 300 seconds (5 minutes) for both regular users and dispatch
- Client falls back to 300s default if server doesn't provide the config value
- Minimum hold: after reacquisition, hold wake lock for at least the full timeout period to prevent rapid toggling
- Wake lock reacquisition triggered by WebSocket events (server push) when audio activity resumes
- No PTT warmup delay: instant wake lock reacquisition on PTT press, accept any brief initial unreliability
- Wake lock management is invisible to users (no UI indicators)
- Coordinated location: when wake lock releases, location tracking interval doubles
- Location interval doubling is tied directly to wake lock release event (not a separate config)
- Location recovery uses gradual ramp-up over 1-2 cycles when audio resumes
- Location snap-back is immediate when battery saver is disabled (different from audio-idle recovery)

**Network polling strategy:**
- Poll WebRTC stats (jitter, packet loss, bitrate) AND channel/team membership (belt-and-suspenders for missed WebSocket events)
- Active channel polling: 5-second intervals
- Idle channel polling: 15-second intervals
- Activity window: 60 seconds — channel stays at 5s polling for 60s after last audio activity, then drops to 15s
- Fixed polling values (5s/15s) — not server-configurable
- Per-channel activity timers: each monitored channel independently tracks its own active/idle state
- Suspend polling on empty channels (zero consumers); resume via WebSocket when someone joins
- New channel join treated as activity: starts at 5s polling for 60-second window
- Continue 15s idle polling even when wake lock is released (maintain channel monitoring at all times)
- Dev stats screen shows current polling interval per channel

**Battery target & measurement:**
- Target: <6%/hour with screen off in idle monitoring scenario (joined channels, listening, location tracking, no active PTT)
- Measurement tool: Android Battery Historian for detailed power breakdown per subsystem
- Test duration: 2-hour profiling session on any available physical device
- Profiling timing: after optimizations only (v3.0 5%/hr baseline serves as pre-optimization reference)
- If target missed: document results and ship — optimizations being in place is sufficient for v4.0
- Results captured in dedicated .planning/BATTERY.md with historical data across milestones
- BATTERY.md includes v3.0 baseline and tracks before/after deltas per optimization

**Power mode awareness:**
- App detects and responds to Android battery saver mode
- Battery saver reduces location frequency further (4x multiplier instead of 2x) — audio and polling unchanged
- Toast notification shown every time app is opened while battery saver is active: "Battery saver active — location updates reduced"
- Toast shows on every app open with battery saver on (not just first detection)
- Immediate location snap-back when battery saver is disabled (no gradual ramp)
- No in-app low-power toggle — rely on system battery saver only
- Battery saver state and current location multiplier visible in dev stats screen

### Claude's Discretion

- Exact Battery Historian setup and report format
- Location multiplier implementation details (how to integrate with existing MotionDetector tiers)
- WebSocket event handling for wake lock reacquisition (which events trigger it)
- Dev stats screen layout for new power management fields
- BATTERY.md document structure and formatting

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| PowerManager | Android SDK | CPU wake lock management | Official Android API for keeping CPU active when screen off — required for background audio monitoring |
| Handler/Looper | Android SDK | Delayed wake lock release | Standard Android mechanism for posting delayed actions — used universally for timeout-based operations |
| BroadcastReceiver | Android SDK | Battery saver mode detection | Android's pub/sub mechanism for system events like ACTION_POWER_SAVE_MODE_CHANGED |
| Battery Historian | 3.1 (Docker) | Battery consumption analysis | Google's official battery profiling tool for Android 5.0+ — visualizes subsystem power draw from bugreport data |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Kotlin Coroutines | 1.9.0 | Async polling loops | Already used project-wide for background work — natural fit for periodic polling tasks |
| StateFlow | Kotlin std | Wake lock/polling state | Project convention for reactive state management — expose current power state to UI |
| Handler.postDelayed | Android SDK | Activity timeout tracking | Lightweight timer for 60-second activity windows per channel |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| PowerManager.WakeLock | WorkManager periodic tasks | WakeLock required for real-time audio monitoring — WorkManager designed for deferrable background work, not PTT use case |
| Handler.postDelayed | ScheduledExecutorService | Handler is Android-native and integrates better with main thread UI updates; ScheduledExecutorService is Java stdlib but requires more ceremony |
| BroadcastReceiver | PowerManager.isPowerSaveMode polling | Broadcast is event-driven (battery-efficient), polling wastes CPU cycles checking unchanged state |

**Installation:**

No new dependencies — all APIs are part of Android SDK or existing Kotlin project stack.

## Architecture Patterns

### Recommended Project Structure

```
android/app/src/main/java/com/voiceping/android/
├── data/
│   ├── power/
│   │   ├── WakeLockManager.kt           # Singleton managing partial wake lock lifecycle
│   │   └── BatterySaverMonitor.kt       # BroadcastReceiver for battery saver detection
│   ├── network/
│   │   └── ChannelStatsPoller.kt        # Per-channel network stats polling coordinator
│   └── location/
│       └── LocationManager.kt           # MODIFIED: integrate wake lock/battery saver multipliers
├── domain/model/
│   └── PowerState.kt                    # Data class for wake lock + battery saver state
└── presentation/settings/
    └── DevStatsScreen.kt                # MODIFIED: display power management fields
```

### Pattern 1: Wake Lock Timeout Manager

**What:** Singleton managing partial wake lock with timeout-based release using Handler.postDelayed

**When to use:** Background PTT audio monitoring requiring CPU active when screen off, with power optimization via timeout

**Example:**

```kotlin
@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "VoicePing::AudioMonitoring"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var releaseRunnable: Runnable? = null

    // Server-configurable timeout (ms), default 300s
    var wakeLockTimeoutMs: Long = 300_000L

    fun acquire() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
            Log.d(TAG, "Wake lock acquired")
        }
        resetTimeout()
    }

    fun resetTimeout() {
        releaseRunnable?.let { handler.removeCallbacks(it) }
        releaseRunnable = Runnable {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "Wake lock released after ${wakeLockTimeoutMs}ms timeout")
                onWakeLockReleased?.invoke()
            }
        }
        handler.postDelayed(releaseRunnable!!, wakeLockTimeoutMs)
    }

    fun releaseImmediate() {
        releaseRunnable?.let { handler.removeCallbacks(it) }
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "Wake lock released immediately")
        }
    }

    var onWakeLockReleased: (() -> Unit)? = null
}
```

**Source:** Android PowerManager.WakeLock official docs combined with Handler.postDelayed timeout pattern

### Pattern 2: Per-Channel Activity Timer

**What:** Independent activity tracking per monitored channel with 60-second window

**When to use:** Dynamic polling intervals that adapt per channel based on last audio activity

**Example:**

```kotlin
data class ChannelPollState(
    val channelId: String,
    var lastActivityMs: Long = System.currentTimeMillis(),
    var currentIntervalMs: Long = 5000L // Start at 5s (active)
) {
    fun updateActivity() {
        lastActivityMs = System.currentTimeMillis()
        currentIntervalMs = 5000L
    }

    fun checkActivityWindow(): Long {
        val elapsed = System.currentTimeMillis() - lastActivityMs
        return if (elapsed > 60_000L) {
            currentIntervalMs = 15_000L // Idle
            15_000L
        } else {
            5_000L // Active
        }
    }
}

class ChannelStatsPoller @Inject constructor() {
    private val channelStates = mutableMapOf<String, ChannelPollState>()

    fun startPolling(channelId: String) {
        val state = channelStates.getOrPut(channelId) {
            ChannelPollState(channelId)
        }

        scope.launch {
            while (isActive && monitoredChannels.contains(channelId)) {
                val interval = state.checkActivityWindow()

                // Poll stats AND membership
                pollWebRtcStats(channelId)
                pollChannelMembership(channelId)

                delay(interval)
            }
        }
    }

    fun markActivity(channelId: String) {
        channelStates[channelId]?.updateActivity()
    }
}
```

**Source:** Derived from user decision for 60-second activity window with per-channel independence

### Pattern 3: Battery Saver Broadcast Receiver

**What:** BroadcastReceiver detecting ACTION_POWER_SAVE_MODE_CHANGED and checking PowerManager.isPowerSaveMode()

**When to use:** React to system battery saver mode changes for location frequency adjustment

**Example:**

```kotlin
class BatterySaverMonitor(
    private val context: Context,
    private val onBatterySaverChanged: (Boolean) -> Unit
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                val isEnabled = powerManager.isPowerSaveMode
                Log.d(TAG, "Battery saver mode changed: $isEnabled")
                onBatterySaverChanged(isEnabled)
            }
        }
    }

    fun start() {
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        context.registerReceiver(receiver, filter)

        // Initial state check
        onBatterySaverChanged(powerManager.isPowerSaveMode)
    }

    fun stop() {
        context.unregisterReceiver(receiver)
    }
}
```

**Source:** [Jake Lee - Displaying a "Power Saving Enabled" Bar Inside Your Android App](https://blog.jakelee.co.uk/displaying-a-power-saving-enabled-bar-inside-your-android-app/)

### Pattern 4: Location Multiplier Integration

**What:** Apply wake lock and battery saver multipliers to existing MotionDetector intervals

**When to use:** Coordinate location tracking frequency with power state

**Example:**

```kotlin
// In LocationManager
private fun startTrackingWithAdaptiveInterval(motionState: MotionState) {
    val batteryLevel = getBatteryLevel()
    val lowBattery = batteryLevel < LOW_BATTERY_THRESHOLD

    var baseInterval = when (motionState) {
        MotionState.STILL -> INTERVAL_STILL_MS
        MotionState.WALKING -> INTERVAL_WALKING_MS
        MotionState.DRIVING -> INTERVAL_DRIVING_MS
        MotionState.UNKNOWN -> INTERVAL_UNKNOWN_MS
    }

    // Apply wake lock multiplier (2x when wake lock released)
    if (!wakeLockActive) {
        baseInterval *= 2
    }

    // Apply battery saver multiplier (4x when enabled)
    if (batterySaverActive) {
        baseInterval *= 4
    }

    // Override if low battery
    if (lowBattery && baseInterval < INTERVAL_STILL_MS) {
        baseInterval = INTERVAL_STILL_MS
    }

    val priority = if (lowBattery) {
        Priority.PRIORITY_LOW_POWER
    } else {
        Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }

    locationTracker.startTracking(baseInterval, priority) { location ->
        onLocationUpdate(location)
    }
}
```

**Source:** Derived from user decision for 2x/4x multipliers with immediate snap-back on battery saver disable

### Anti-Patterns to Avoid

- **Wake lock without timeout:** Leads to stuck partial wake locks (Android vitals violation) — always use timeout or explicit release
- **Polling on main thread:** Handler.postDelayed runs on main thread by default, but heavy polling work (WebRTC stats parsing) must be offloaded to background thread via coroutine
- **Wake lock release during audio activity:** User expects audio monitoring to be always-on — only release after full timeout period of zero activity across ALL channels
- **Battery saver polling:** Don't poll PowerManager.isPowerSaveMode — use broadcast receiver to react to changes event-driven

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Battery consumption analysis | Custom logcat battery metrics parser | Android Battery Historian (Docker image) | Battery Historian provides visual timeline, subsystem breakdown, and aggregated stats — handles Android bugreport format complexity and version differences |
| Wake lock timeout management | Custom Timer/TimerTask countdown | Handler.postDelayed with removeCallbacks | Handler integrates with Android Looper/main thread, supports cancellation, and is the Android-native timeout pattern — Timer is Java legacy |
| Battery saver detection | Polling PowerManager.isPowerSaveMode every N seconds | BroadcastReceiver for ACTION_POWER_SAVE_MODE_CHANGED | Broadcast is event-driven (zero CPU when state unchanged), polling wastes battery checking every N seconds |
| WebRTC stats aggregation over time | Rolling window calculation in memory | Poll raw stats every 1-2s, upload to server for backend aggregation | WebRTC stats are cumulative counters — frontend aggregation requires storing deltas, handling resets, managing memory; server-side aggregation is more robust |

**Key insight:** Wake locks are high-stakes (Android vitals tracks excessive/stuck wake locks starting March 1, 2026) — use official patterns, always set timeouts, and release ASAP to avoid Play Console exclusion from discovery surfaces.

## Common Pitfalls

### Pitfall 1: Wake Lock Held Too Long

**What goes wrong:** Wake lock held for hours drains battery excessively and triggers Android vitals "excessive partial wake lock" metric

**Why it happens:** No timeout set, OR timeout not reset when activity resumes (wake lock acquired at app start, never released)

**How to avoid:**
- Always use `wakeLock.acquire(timeoutMs)` OR implement Handler.postDelayed timeout with explicit release
- Reset timeout on every audio activity event (PTT press, new consumer joined)
- Release immediately when all channels stop monitoring (user navigates away)

**Warning signs:**
- Battery Historian shows "WakeLock VoicePing::AudioMonitoring" held continuously for hours
- Android vitals dashboard shows excessive wake lock usage above threshold
- User reports extreme battery drain with screen off

**Source:** [Android Developers - Optimize your app battery using Android vitals wake lock metric](https://android-developers.googleblog.com/2025/09/guide-to-excessive-wake-lock-usage.html)

### Pitfall 2: Polling Stats Too Early

**What goes wrong:** WebRTC getStats() called too early (< 2 seconds after connection) returns incomplete data — RTCRemoteInboundRTPAudio missing, jitter/packetsLoss/rtt unavailable

**Why it happens:** WebRTC stack needs time to generate remote inbound stats after peer connection establishment

**How to avoid:**
- Wait at least 2 seconds after consumer created before polling stats
- Handle missing stats gracefully (nullable fields, fallback to "N/A" in UI)
- Start polling timer AFTER first successful stats response, not immediately on consumer creation

**Warning signs:**
- DevStatsScreen shows "N/A" for jitter/packet loss even on active channels
- Logcat shows "RTCRemoteInboundRTPAudio not found in stats report"
- Stats suddenly appear after 2-3 seconds of consumer activity

**Source:** [BlogGeek.me - Making sense of getStats in WebRTC](https://bloggeek.me/getstats/)

### Pitfall 3: Location Snap-Back Without State Tracking

**What goes wrong:** Battery saver disabled, location frequency snaps back immediately, but wake lock state is still idle — location uses 1x multiplier when it should use 2x (wake lock released)

**Why it happens:** Battery saver snap-back and wake lock multiplier are independent — battery saver disable event doesn't check wake lock state

**How to avoid:**
- Track both wake lock state AND battery saver state independently
- Calculate final interval as `baseInterval * wakeLockMultiplier * batterySaverMultiplier`
- On battery saver disable, recalculate using current wake lock state (don't assume 1x)

**Warning signs:**
- Location updates too frequent immediately after battery saver disabled
- Battery drain spikes after disabling battery saver
- Dev stats shows "Location Multiplier: 1x" when wake lock is released

### Pitfall 4: Per-Channel Polling Memory Leak

**What goes wrong:** Channel polling coroutines never cancelled when channel removed from monitoring — accumulate over time, drain battery

**Why it happens:** Monitoring set updated (channel removed) but polling loop checks stale snapshot of monitored channels

**How to avoid:**
- Use `while (isActive && channelId in monitoredChannels.value)` loop condition
- Cancel polling job explicitly in `stopMonitoring(channelId)` via Job.cancel()
- Clear channel state from map when polling stops

**Warning signs:**
- Logcat shows "Polling stats for channel X" after channel unmonitored
- ChannelStatsPoller job count grows over time (inspect with debugger)
- Battery drain increases after joining/leaving many channels

## Code Examples

Verified patterns from Android SDK and WebRTC community:

### Wake Lock with Timeout

```kotlin
// Acquire with timeout
wakeLock.acquire(300_000L) // 5 minutes

// OR acquire indefinitely with Handler timeout
wakeLock.acquire()
handler.postDelayed({
    if (wakeLock.isHeld) {
        wakeLock.release()
    }
}, 300_000L)

// Cancel timeout and release early
handler.removeCallbacks(releaseRunnable)
if (wakeLock.isHeld) {
    wakeLock.release()
}
```

**Source:** [Android Developers - Set a wake lock](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set)

### WebRTC Stats Polling (mediasoup Consumer)

```kotlin
// mediasoup-android Consumer.getStats() returns Map<String, Any>?
val stats = consumer.stats
if (stats != null) {
    // Parse inbound-rtp stats
    val jitter = (stats["jitter"] as? Double) ?: 0.0
    val packetsLost = (stats["packetsLost"] as? Long) ?: 0L
    val packetsReceived = (stats["packetsReceived"] as? Long) ?: 0L

    val networkStats = ConsumerNetworkStats(
        packetsLost = packetsLost,
        jitter = jitter,
        packetsReceived = packetsReceived,
        indicator = ConsumerNetworkStats.calculateIndicator(packetsLost, (jitter * 1000).toInt())
    )
}
```

**Note:** crow-misia/mediasoup-android Consumer.getStats() API is undocumented — actual return format needs device validation (open question from Phase 17 research)

**Source:** Inferred from Phase 17 ConsumerNetworkStats model and mediasoup v3 documentation

### Battery Historian Setup (Docker)

```bash
# 1. Reset battery stats on device
adb shell dumpsys batterystats --reset

# 2. Run test scenario (2-hour idle monitoring with screen off)
# ... let device sit for 2 hours ...

# 3. Capture bugreport
adb bugreport bugreport.zip

# 4. Run Battery Historian Docker container
docker run -p 9999:9999 gcr.io/android-battery-historian/stable:3.1 --port 9999

# 5. Open http://localhost:9999 in browser
# 6. Upload bugreport.zip
# 7. Analyze "Wake Lock" timeline for VoicePing::AudioMonitoring duration
# 8. Check "App Stats" section for per-subsystem power draw breakdown
```

**Source:** [Android Developers - Profile battery usage with Batterystats and Battery Historian](https://developer.android.com/topic/performance/power/setup-battery-historian)

### Server Config in Auth Response

```typescript
// Server: Add wakeLockTimeoutSeconds to auth response
interface AuthResponse {
  token: string;
  userId: string;
  // ... existing fields
  wakeLockTimeoutSeconds?: number; // Optional, default 300
}

// Client: Parse in AuthApi
data class AuthConfig(
    val wakeLockTimeoutMs: Long = 300_000L // Default 5 minutes
)

fun parseAuthResponse(response: AuthResponse): AuthConfig {
    val timeoutSeconds = response.wakeLockTimeoutSeconds ?: 300
    return AuthConfig(wakeLockTimeoutMs = timeoutSeconds * 1000L)
}
```

**Source:** Derived from user decision for server-configurable timeout via auth response

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Always-on wake lock | Timeout-based release | Android 12 (2021) Battery Restrictions | Apps with excessive wake locks deprioritized in Play Store (March 2026 enforcement) |
| Polling battery state | BroadcastReceiver for battery events | Android 5.0 (2014) | Event-driven pattern eliminates polling overhead |
| Manual battery stats collection | Battery Historian web UI | Android 5.0 (2014) | Visual timeline + subsystem breakdown replaces manual logcat parsing |
| Fixed polling intervals | Adaptive intervals based on activity | WebRTC monitoring best practice (2023+) | 1-2 second polling standard for real-time quality monitoring, but idle channels can use longer intervals |

**Deprecated/outdated:**

- **Battery Historian active maintenance:** Google archived the project — still functional for Android 5.0+ but no new features. Alternative: Android Studio Power Profiler or Macrobenchmark power metric (for automated tests). For v4.0, Battery Historian Docker image is sufficient for one-time profiling.
- **FLAG_KEEP_SCREEN_ON for background work:** Only works in activities — use wake locks for background services/monitoring (Activity screen-on flag doesn't keep CPU active when screen off)

## Open Questions

### 1. mediasoup-android Consumer.getStats() Return Format

**What we know:**
- Phase 17 research identified crow-misia Consumer.getStats() API as undocumented
- DevStatsScreen currently shows stub consumer stats ("Pending device validation")
- mediasoup v3 server-side stats include jitter, packetsLost, packetsReceived

**What's unclear:**
- Actual Map<String, Any>? structure returned by crow-misia Consumer.getStats()
- Whether stats include RTCRemoteInboundRTPAudio fields (jitter, rtt)
- If stats are cumulative counters or per-interval deltas

**Recommendation:**
- Plan 20-01 includes task to validate Consumer.getStats() on physical device
- If stats parsing fails, fall back to server-side monitoring only (don't block polling feature)
- Document findings in task verification notes

### 2. Wake Lock Reacquisition WebSocket Event

**What we know:**
- Wake lock reacquired on PTT press (instant, no delay)
- Wake lock reacquired via WebSocket events (server push) when audio activity resumes
- Existing WebSocket events: SPEAKER_JOINED, SPEAKER_LEFT, NEW_PRODUCER, etc.

**What's unclear:**
- Which specific WebSocket event(s) trigger wake lock reacquisition
- Whether NEW_PRODUCER alone is sufficient or need SPEAKER_JOINED too
- How to distinguish "audio activity resumed" from "brief PTT press on another user's channel"

**Recommendation:**
- Wake lock reacquisition on: NEW_PRODUCER (audio started), PTT_START (user pressed PTT), CONSUMER_CREATED (we joined channel with active speaker)
- Do NOT reacquire on: SPEAKER_LEFT, CONSUMER_CLOSED (these are cleanup events)
- Test edge case: user joins channel, hears 10s transmission, then silence — wake lock should still timeout after 300s from last audio, not from channel join

### 3. Battery Historian Subsystem Interpretation

**What we know:**
- Battery Historian shows timeline graph and "App Stats" breakdown
- Wake lock duration visible on timeline as "WakeLock VoicePing::AudioMonitoring"
- Subsystem categories include: CPU, Network, GPS, Screen, etc.

**What's unclear:**
- How to attribute location tracking power to "GPS" vs "Network" (FusedLocationProvider uses both)
- Whether WebRTC audio processing appears under CPU or Audio subsystem
- Expected baseline for "Network" subsystem with WebSocket keepalive (25s PING)

**Recommendation:**
- Focus on total app power draw first (should be <6%/hour)
- If target missed, drill into subsystems to identify highest contributor
- Document findings in BATTERY.md with screenshots for future reference

## Sources

### Primary (HIGH confidence)

- [Android Developers - Profile battery usage with Batterystats and Battery Historian](https://developer.android.com/topic/performance/power/setup-battery-historian)
- [Android Developers - Set a wake lock](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set)
- [Android Developers - Release a wake lock](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/release)
- [Android Developers - PowerManager.WakeLock API Reference](https://developer.android.com/reference/android/os/PowerManager.WakeLock)
- [Android Developers - Excessive partial wake locks](https://developer.android.com/topic/performance/vitals/excessive-wakelock)

### Secondary (MEDIUM confidence)

- [Android Developers Blog - Optimize your app battery using Android vitals wake lock metric](https://android-developers.googleblog.com/2025/09/guide-to-excessive-wake-lock-usage.html) — 2025 enforcement details for wake lock vitals
- [Jake Lee - Displaying a "Power Saving Enabled" Bar Inside Your Android App](https://blog.jakelee.co.uk/displaying-a-power-saving-enabled-bar-inside-your-android-app/) — BroadcastReceiver pattern for battery saver detection
- [BlogGeek.me - Making sense of getStats in WebRTC](https://bloggeek.me/getstats/) — Stats polling timing (<2s issue)
- [WebRTC Developers - WebRTC Statistics using getStats](https://www.webrtc-developers.com/webrtc-statistics-using-getstats/) — Polling interval recommendations (1-2s)
- [GitHub - google/battery-historian](https://github.com/google/battery-historian) — Official Battery Historian repository (archived but functional)

### Tertiary (LOW confidence)

- [mediasoup.org - RTC Statistics](https://mediasoup.org/documentation/v3/mediasoup/rtc-statistics/) — Server-side stats reference (client-side crow-misia API differs)
- [100ms.live - Measuring WebRTC Call Quality - Part 1](https://www.100ms.live/blog/measuring-webrtc-call-quality-part-1) — MOS score discussion (informational, not actionable for phase)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Android SDK APIs (PowerManager, Handler, BroadcastReceiver) are official and stable since Android 5.0
- Architecture: HIGH — Wake lock timeout pattern verified in Android docs, per-channel polling derived from user decisions
- Pitfalls: HIGH — Wake lock vitals enforcement (March 2026) from official Android blog, stats timing issue from verified WebRTC community source

**Research date:** 2026-02-16
**Valid until:** 60 days (April 2026) — Android SDK APIs stable, Battery Historian Docker image frozen but functional
