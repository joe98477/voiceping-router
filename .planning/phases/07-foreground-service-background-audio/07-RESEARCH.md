# Phase 7: Foreground Service & Background Audio - Research

**Researched:** 2026-02-10
**Domain:** Android foreground services, wake locks, audio focus management, phone call handling
**Confidence:** HIGH

## Summary

Phase 7 transforms the VoicePing app into a true "pocket radio" that operates reliably with the screen off. The core challenge is keeping WebSocket connections alive and audio processing active while navigating Android's aggressive battery optimization systems (Doze mode, App Standby) without triggering Google Play's 2026 battery drain enforcement.

The existing architecture already has AudioCaptureService (microphone foreground service for PTT transmission). This phase extends the service model with a new persistent foreground service (mediaPlayback type) that manages channel monitoring, persistent notifications with controls, and audio playback while screen-off. Audio focus management provides phone call detection without requiring READ_PHONE_STATE permission—the system automatically handles call interruptions via AUDIOFOCUS_LOSS_TRANSIENT events.

Wake locks are explicitly NOT needed for audio playback (Android's audio system manages them automatically), but foreground service with notification keeps the app alive during Doze mode's maintenance windows. Battery optimization exemption request is acceptable for this use case (real-time communication app) under Google Play policy.

**Primary recommendation:** Create dedicated ChannelMonitoringService (foreground service type: mediaPlayback) with persistent notification containing Mute/Disconnect actions. Leverage audio focus change listener for phone call handling instead of READ_PHONE_STATE permission. Request battery optimization exemption when user first joins a channel (not on app launch). Avoid wake locks—audio system and foreground service provide necessary lifecycle guarantees.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Notification controls:**
- Persistent notification shows active channel name and current speaker (minimal, like a music player showing song title)
- No PTT button in notification — PTT happens via hardware button or by opening the app
- Notification includes Mute button (toggle incoming audio) and Disconnect button (end service)
- Minimal updates only — update on channel change or service state change, NOT on every speaker change (saves battery)

**Screen-off feedback:**
- Incoming audio: squelch tone (from Phase 6) plays before audio starts, no vibration — audio itself is the feedback
- PTT feedback with screen off: same tones as Phase 6 (TX confirm tone on success, error tone on deny), no additional vibration
- Audio focus: duck other audio apps (music, podcasts) when channel audio plays, restore volume after — radio takes priority but doesn't kill music
- Volume: respect standard Android device volume controls, no minimum floor enforcement

**Phone call handling:**
- Incoming call detected: immediate pause of all channel audio (no fade)
- If user was transmitting during call: force-release PTT with a distinct double beep (different from normal roger beep) to signal call interruption to other users
- After call ends: auto-resume channel audio immediately, no delay
- Outgoing calls: same behavior as incoming — pause audio, force-release PTT, auto-resume after call ends

**Service lifecycle:**
- Service starts: on first channel join (not on login or app launch — no service if just browsing)
- Service stops: when user taps Disconnect in notification, logs out, OR leaves all monitored channels
- Force-kill (swipe from recents): service stays dead, does NOT auto-restart. User opens app again when ready
- Battery optimization: request exemption (ignore battery optimizations) but NOT on first launch — prompt when user first joins a channel (when service starts)

### Claude's Discretion

- Notification channel priority and importance level
- Wake lock strategy for Doze mode survival
- Heartbeat interval adjustments for background operation
- Service restart strategy (START_STICKY vs START_NOT_STICKY given no auto-restart on force-kill)
- Exact audio focus request type and ducking implementation

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Android SDK | API 26+ (Android 8.0) | Minimum target set in Phase 5 | App targets minSdk 26, compileSdk 35 per build.gradle.kts |
| Foreground Service | API 26+ | Background audio playback with notification | Required for screen-off audio since API 26; API 34+ requires type declaration |
| AudioManager | API 26+ | Audio focus management, routing control | System service for all audio operations; already used in AudioRouter.kt |
| NotificationCompat | AndroidX | Notification creation and management | Backward compatibility for notification features across API levels |
| PowerManager | API 23+ | Battery optimization check/request (optional wake lock) | System service for power management and Doze mode interaction |
| Hilt DI | 2.59.1 | Dependency injection | Already configured project-wide per build.gradle.kts |
| Kotlin Coroutines | 1.10.1 | Async operations in service lifecycle | Already used throughout codebase for async work |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| BroadcastReceiver | Built-in | Handle notification action intents | Required for Mute/Disconnect buttons in notification |
| MediaSessionCompat | AndroidX (optional) | Media-style notification controls | If enhanced media controls needed beyond basic actions |
| WakeLock (avoid) | PowerManager API | Keep CPU alive during Doze (NOT RECOMMENDED) | Only if audio playback fails without it (test first) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Audio focus listener for call detection | READ_PHONE_STATE + TelephonyManager | Audio focus = privacy-friendly, no dangerous permission; TelephonyManager = more explicit call state but requires READ_PHONE_STATE (privacy concern, unnecessary) |
| START_NOT_STICKY | START_STICKY | START_NOT_STICKY = respects user force-kill intent (user decision locked); START_STICKY = system would auto-restart service (unwanted for this use case) |
| Foreground service | WorkManager + wake locks | Foreground service = designed for long-running operations with notification; WorkManager = task scheduling, not continuous operation |
| IMPORTANCE_DEFAULT | IMPORTANCE_HIGH | IMPORTANCE_DEFAULT = visible notification without heads-up; IMPORTANCE_HIGH = intrusive heads-up (unnecessary for persistent service) |

**Installation:**
No new Gradle dependencies required—all needed APIs are part of Android SDK or already included in project (Hilt, Coroutines, AndroidX).

## Architecture Patterns

### Recommended Project Structure
```
android/app/src/main/java/com/voiceping/android/
├── service/
│   ├── AudioCaptureService.kt          # Existing: microphone foreground service for PTT TX
│   ├── ChannelMonitoringService.kt     # NEW: mediaPlayback foreground service for RX
│   └── NotificationActionReceiver.kt   # NEW: handles Mute/Disconnect from notification
├── data/
│   ├── ptt/
│   │   └── PttManager.kt                # Existing: extend with phone call handling
│   ├── audio/
│   │   └── AudioRouter.kt               # Existing: extend with audio focus change listener
│   └── repository/
│       └── ChannelRepository.kt         # Existing: extend with monitoring state
└── presentation/
    └── channels/
        └── ChannelListViewModel.kt      # Existing: trigger service start on first join
```

### Pattern 1: Foreground Service with Notification Actions

**What:** Foreground service with persistent notification containing actionable buttons (Mute, Disconnect) that send broadcasts to BroadcastReceiver.

**When to use:** Long-running background operations (audio monitoring) that must survive screen-off and Doze mode.

**Example:**
```kotlin
// Source: https://developer.android.com/develop/ui/views/notifications/build-notification
class ChannelMonitoringService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                createNotificationChannel()
                val notification = buildNotification(channelName = "Channel 1", speaker = null)
                startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }
            ACTION_UPDATE_NOTIFICATION -> {
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
                val speaker = intent.getStringExtra(EXTRA_SPEAKER)
                updateNotification(channelName, speaker)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY // User decision: no auto-restart after force-kill
    }

    private fun buildNotification(channelName: String?, speaker: String?): Notification {
        val contentText = speaker?.let { "Speaking: $it" } ?: "Monitoring"

        val muteIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            this, 0, muteIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            this, 1, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(channelName ?: "VoicePing")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_logo)
            .setOngoing(true) // Persistent, cannot be swiped away
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(R.drawable.ic_volume_off, "Mute", mutePendingIntent)
            .addAction(R.drawable.ic_disconnect, "Disconnect", disconnectPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.voiceping.START_MONITORING"
        const val ACTION_UPDATE_NOTIFICATION = "com.voiceping.UPDATE_NOTIFICATION"
        const val ACTION_STOP = "com.voiceping.STOP_MONITORING"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_SPEAKER = "speaker"
        private const val CHANNEL_ID = "channel_monitoring"
        private const val NOTIFICATION_ID = 1000
    }
}
```

### Pattern 2: Audio Focus Change Listener for Phone Call Detection

**What:** AudioFocusRequest with OnAudioFocusChangeListener that responds to AUDIOFOCUS_LOSS_TRANSIENT (incoming/outgoing calls) instead of using READ_PHONE_STATE permission.

**When to use:** Apps that need to pause audio during phone calls without requesting dangerous permissions.

**Example:**
```kotlin
// Source: https://developer.android.com/media/optimize/audio-focus
class AudioRouter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // Callback for phone call interruption handling
    var onPhoneCallStarted: (() -> Unit)? = null
    var onPhoneCallEnded: (() -> Unit)? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Phone call started—pause all audio, force-release PTT
                Log.d(TAG, "Audio focus lost (transient): phone call started")
                onPhoneCallStarted?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Phone call ended—resume audio
                Log.d(TAG, "Audio focus regained: phone call ended")
                onPhoneCallEnded?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Another app wants to duck (e.g., navigation)—we duck
                // System handles ducking automatically on API 26+ if setWillPauseWhenDucked(false)
                Log.d(TAG, "Audio focus: ducking for transient sound")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss (e.g., music app started)—duck our audio
                Log.d(TAG, "Audio focus lost permanently: another app playing audio")
                // User decision: duck, don't pause. Radio audio takes lower priority than user's music.
            }
        }
    }

    fun requestAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setWillPauseWhenDucked(false) // Enable automatic ducking (API 26+)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus granted")
            this.audioFocusRequest = focusRequest
        }
    }

    fun releaseAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }
}
```

### Pattern 3: Battery Optimization Exemption Request

**What:** Prompt user to disable battery optimization for the app when service starts (first channel join), not on app launch.

**When to use:** Real-time communication apps that need to maintain WebSocket connections during Doze mode.

**Example:**
```kotlin
// Source: https://developer.android.com/training/monitoring-device-state/doze-standby
class ChannelListViewModel @Inject constructor(
    private val context: Context,
    private val powerManager: PowerManager
) : ViewModel() {

    fun checkAndRequestBatteryOptimization() {
        val packageName = context.packageName
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // Show dialog explaining why exemption is needed
            showBatteryOptimizationDialog()
        }
    }

    private fun showBatteryOptimizationDialog() {
        // User decision: prompt on first channel join, not app launch
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        // Start intent from Activity (requires Activity context)
        context.startActivity(intent)
    }
}
```

### Pattern 4: Notification Channel Creation (API 26+)

**What:** Create NotificationChannel with appropriate importance level for persistent foreground service notification.

**When to use:** All foreground services on API 26+ require notification channel creation.

**Example:**
```kotlin
// Source: https://developer.android.com/develop/ui/views/notifications/channels
private fun createNotificationChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Channel Monitoring",
        NotificationManager.IMPORTANCE_DEFAULT // User discretion: no heads-up, but visible
    ).apply {
        description = "Persistent notification shown while monitoring channels"
        setShowBadge(false) // Pocket radio doesn't need badge
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Show on lock screen
    }

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}
```

### Pattern 5: BroadcastReceiver for Notification Actions

**What:** BroadcastReceiver handles intents from notification action buttons (Mute, Disconnect).

**When to use:** Interactive notifications that trigger app logic without opening Activity.

**Example:**
```kotlin
// Source: https://developer.android.com/develop/ui/views/notifications/build-notification
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_MUTE -> {
                // Toggle mute state (requires injecting service/repository)
                val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
                    action = ACTION_TOGGLE_MUTE
                }
                context.startService(serviceIntent)
            }
            ACTION_DISCONNECT -> {
                // Stop monitoring service
                val serviceIntent = Intent(context, ChannelMonitoringService::class.java).apply {
                    action = ChannelMonitoringService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_MUTE = "com.voiceping.TOGGLE_MUTE"
        const val ACTION_DISCONNECT = "com.voiceping.DISCONNECT"
    }
}
```

### Anti-Patterns to Avoid

- **Using wake locks for audio playback:** Android's audio system automatically manages wake locks. Manual PARTIAL_WAKE_LOCK would be redundant and drain battery. Only consider wake locks if audio playback fails during Doze (test first).
- **Updating notification on every speaker change:** User decision locks minimal updates. Frequent notification updates drain battery (NotificationManager rate limits, Android 15+ has cooldown).
- **Using READ_PHONE_STATE permission:** Audio focus listener provides phone call detection without dangerous permission. READ_PHONE_STATE violates privacy-first design and requires runtime permission.
- **Starting foreground service from background after force-kill:** User decision: no auto-restart. Attempting background service start after force-kill violates Android 12+ restrictions.
- **Setting notification importance after channel creation:** NotificationChannel importance is immutable—user can only change it in system settings. Set correctly at creation time.
- **Holding audio focus permanently with AUDIOFOCUS_GAIN:** Use AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK for PTT bursts. Permanent focus prevents music apps from playing when radio is quiet.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Phone call detection | Custom TelephonyManager listener with READ_PHONE_STATE | AudioManager.OnAudioFocusChangeListener responding to AUDIOFOCUS_LOSS_TRANSIENT | Audio focus is privacy-friendly (no dangerous permission), system-integrated, and handles both incoming/outgoing calls automatically. Custom TelephonyManager requires READ_PHONE_STATE permission (privacy concern) and has edge cases (VoIP calls, Bluetooth calls). |
| Wake lock management for audio | Custom PowerManager.WakeLock acquire/release logic | Let Android audio system manage wake locks automatically | Audio playback APIs (AudioTrack, MediaPlayer) acquire/release wake locks internally. Manual wake lock management is error-prone (leaks drain battery), violates Google Play 2026 battery policy, and is unnecessary for audio use cases. |
| Service restart after crash | Custom crash handler + service restart logic | START_STICKY (if desired) or rely on user reopening app (user decision) | Android's service restart flags (START_STICKY, START_REDELIVER_INTENT) handle crash recovery. User decision locks START_NOT_STICKY (no auto-restart after force-kill), so custom restart logic would contradict user intent. |
| Notification update throttling | Custom debounce/rate limiting for notification updates | NotificationManager built-in rate limiting + user decision (minimal updates) | NotificationManager automatically rate-limits excessive updates. Android 15+ has notification cooldown (1-2 min for rapid notifications). User decision already constrains updates to channel/state changes only. |
| Doze mode survival with continuous wake locks | Persistent wake lock throughout Doze | Foreground service + battery optimization exemption | Foreground services continue running during Doze (with maintenance windows for network access). Wake locks are ignored in Doze. Battery optimization exemption (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) is correct approach for real-time communication apps (acceptable under Google Play policy). |

**Key insight:** Android provides robust system services (AudioManager, NotificationManager, PowerManager) specifically designed for background audio, phone call handling, and power management. Custom solutions miss edge cases (VoIP calls, Bluetooth, Doze mode variants) and violate platform best practices. Google Play's March 2026 battery drain enforcement makes wake lock misuse a policy violation risk.

## Common Pitfalls

### Pitfall 1: Audio Focus Loss Not Handled → Audio Plays During Phone Calls

**What goes wrong:** App continues playing channel audio during incoming/outgoing phone calls, creating jarring user experience (radio audio competing with phone call).

**Why it happens:** AudioRouter.kt currently requests audio focus but doesn't register OnAudioFocusChangeListener to respond to focus loss events. Phone calls trigger AUDIOFOCUS_LOSS_TRANSIENT, but app ignores it.

**How to avoid:** Register OnAudioFocusChangeListener in AudioFocusRequest.Builder. When AUDIOFOCUS_LOSS_TRANSIENT received, pause all channel audio and force-release PTT (if transmitting). When AUDIOFOCUS_GAIN received, resume audio immediately.

**Warning signs:**
- User reports hearing channel audio during phone calls
- PTT transmission continues when phone rings
- Channel audio doesn't resume after call ends

### Pitfall 2: Foreground Service Without Type Declaration (API 34+)

**What goes wrong:** App crashes on Android 14+ (API 34) with SecurityException: "Starting FGS with type none requires permission".

**Why it happens:** Android 14 requires explicit foregroundServiceType declaration in AndroidManifest.xml AND passing service type constant to startForeground(). Existing AudioCaptureService correctly declares type="microphone", but new ChannelMonitoringService needs type="mediaPlayback".

**How to avoid:**
1. Declare `android:foregroundServiceType="mediaPlayback"` in AndroidManifest.xml `<service>` tag
2. Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />` to manifest
3. Call `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` (third parameter)

**Warning signs:**
- SecurityException on Android 14+ devices
- Service starts on Android 13 but crashes on Android 14
- Logcat shows "MissingForegroundServiceTypeException"

### Pitfall 3: Battery Optimization Exemption Rejected by Google Play

**What goes wrong:** App submitted to Google Play is rejected or flagged for violating battery optimization policy (March 2026 enforcement).

**Why it happens:** Google Play policy restricts REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission to specific use cases. Apps must justify exemption (e.g., real-time communication, task automation, companion device). VoicePing qualifies (real-time PTT communication), but improper implementation (e.g., requesting on app launch instead of when needed, or excessive wake lock usage >2 hours/day) triggers enforcement.

**How to avoid:**
1. Only request exemption when user joins first channel (service starts), not on app launch
2. Avoid using wake locks for audio playback (let audio system manage)
3. Test wake lock usage with Battery Historian tool (ensure <2 hours/day)
4. Prepare Google Play review explanation: "VoicePing is a real-time PTT communication app requiring persistent WebSocket connection for instant push-to-talk. Battery optimization exemption ensures users receive audio without delay."

**Warning signs:**
- Google Play Console shows battery vitals warning
- App flagged during review for "excessive wake lock usage"
- Battery Historian shows >2 cumulative hours of wake locks per 24 hours

### Pitfall 4: Notification Actions Don't Work → Tapping Mute/Disconnect Does Nothing

**What goes wrong:** User taps Mute or Disconnect in notification, but nothing happens. Service continues running.

**Why it happens:** BroadcastReceiver not registered in AndroidManifest.xml, or PendingIntent created with wrong flags (mutable vs immutable). Android 12+ requires explicit mutability declaration for PendingIntent.

**How to avoid:**
1. Register NotificationActionReceiver in AndroidManifest.xml with `<receiver>` tag and `android:exported="false"`
2. Use `PendingIntent.FLAG_IMMUTABLE` for notification action intents (Android 12+ requirement)
3. Test notification actions on Android 12+ devices specifically

**Warning signs:**
- Tapping notification actions has no effect
- Logcat shows "BroadcastReceiver not found"
- PendingIntent security exception on Android 12+

### Pitfall 5: WebSocket Disconnects During Doze Mode

**What goes wrong:** App loses WebSocket connection after device enters Doze mode (screen off for 30+ minutes). Users miss incoming audio.

**Why it happens:** Doze mode suspends network access, even for foreground services, outside of maintenance windows. WebSocket idle timeout (server-side) expires before next maintenance window. SignalingClient.kt has 25-second heartbeat, but heartbeats may not fire during Doze idle phases.

**How to avoid:**
1. Request battery optimization exemption (allows wake locks and network access during Doze)
2. Increase server-side WebSocket idle timeout to 15+ minutes (allows survival across Doze maintenance windows)
3. Implement reconnection logic that triggers during Doze maintenance windows
4. Consider FCM high-priority push for critical channel events (wakes app from Doze)

**Warning signs:**
- WebSocket disconnects after 30-60 minutes of screen-off
- Heartbeats stop sending during Doze idle phase
- Connection resumes when screen turns on (exiting Doze)

### Pitfall 6: Service Restart After Force-Kill Violates User Intent

**What goes wrong:** User swipes app from recents (force-kill), but service auto-restarts moments later. Notification reappears, audio monitoring resumes.

**Why it happens:** Service returns START_STICKY, causing Android to restart service after force-kill. User decision locks START_NOT_STICKY (no auto-restart).

**How to avoid:** Return START_NOT_STICKY from onStartCommand(). When user force-kills app, service stays dead. User must manually reopen app to rejoin channels.

**Warning signs:**
- Service reappears after force-kill
- User complaints about "can't fully close the app"
- Notification reappears without user action

## Code Examples

Verified patterns from official sources:

### Creating Foreground Service with Media Playback Type (Android 14+)

```kotlin
// Source: https://developer.android.com/develop/background-work/services/fgs/service-types
class ChannelMonitoringService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                createNotificationChannel()
                val notification = buildNotification()

                // Android 14+ requires service type constant
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY // User decision: no auto-restart
    }

    companion object {
        const val ACTION_START = "com.voiceping.START_MONITORING"
        private const val NOTIFICATION_ID = 1000
    }
}
```

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<service
    android:name=".service.ChannelMonitoringService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false" />
<receiver
    android:name=".service.NotificationActionReceiver"
    android:exported="false" />
```

### Handling Phone Calls via Audio Focus (No READ_PHONE_STATE Required)

```kotlin
// Source: https://developer.android.com/media/optimize/audio-focus
class AudioRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pttManager: PttManager,
    private val mediasoupClient: MediasoupClient
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isInPhoneCall = false
    private var wasPlayingBeforeCall = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Phone call started
                Log.d(TAG, "Phone call started: pausing audio")
                isInPhoneCall = true
                wasPlayingBeforeCall = mediasoupClient.isReceivingAudio()

                // Force-release PTT if transmitting
                if (pttManager.pttState.value is PttState.Transmitting) {
                    // Play double beep to signal call interruption to other users
                    tonePlayer.playDoubleBeep()
                    pttManager.releasePtt()
                }

                // Pause all channel audio (user decision: immediate, no fade)
                mediasoupClient.pauseAllAudio()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                // Phone call ended
                Log.d(TAG, "Phone call ended: resuming audio")
                isInPhoneCall = false

                // Resume audio if was playing before call (user decision: auto-resume immediately)
                if (wasPlayingBeforeCall) {
                    mediasoupClient.resumeAllAudio()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // System automatically ducks volume (API 26+)
                Log.d(TAG, "Ducking for transient sound")
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // User started music app—duck our radio audio
                Log.d(TAG, "Permanent focus loss: user started music")
                // User decision: duck, don't pause (radio is background)
            }
        }
    }

    fun requestAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setWillPauseWhenDucked(false) // Enable automatic ducking
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        audioManager.requestAudioFocus(focusRequest)
    }
}
```

### Battery Optimization Exemption Request

```kotlin
// Source: https://developer.android.com/training/monitoring-device-state/doze-standby
class ChannelListViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun onFirstChannelJoin() {
        // User decision: prompt for exemption when service starts (first channel join)
        checkBatteryOptimization()
        startMonitoringService()
    }

    private fun checkBatteryOptimization() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // Explain why exemption is needed before showing system dialog
            _showBatteryOptimizationDialog.value = true
        }
    }

    fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        // Note: This intent must be started from Activity, not Application context
        context.startActivity(intent)
    }
}
```

### Minimal Notification Updates

```kotlin
// User decision: update notification only on channel change or service state change, NOT speaker change
class ChannelMonitoringService : Service() {
    private var currentChannelName: String? = null
    private var isServiceRunning = false

    fun updateNotification(channelName: String?, speaker: String?) {
        // Only update if channel changed or service state changed
        if (channelName != currentChannelName || !isServiceRunning) {
            currentChannelName = channelName
            isServiceRunning = true

            val notification = buildNotification(channelName, speaker)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)

            Log.d(TAG, "Notification updated: channel=$channelName")
        } else {
            // Speaker changed but channel same—don't update (battery optimization)
            Log.d(TAG, "Skipping notification update for speaker change")
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| TelephonyManager + READ_PHONE_STATE for call detection | AudioManager.OnAudioFocusChangeListener for AUDIOFOCUS_LOSS_TRANSIENT | Android 5.0 (API 21) | Audio focus approach is privacy-friendly (no dangerous permission), system-integrated, and handles VoIP calls automatically |
| Manual wake lock management for audio playback | Audio system manages wake locks automatically | Always (best practice) | Eliminates wake lock leaks, reduces battery drain, avoids Google Play 2026 enforcement |
| START_STICKY for all foreground services | START_NOT_STICKY for user-controlled services | Android 2.0 (API 5+) | Respects user intent when force-killing app (no zombie services) |
| Foreground service without type declaration | Explicit foregroundServiceType required | Android 14 (API 34) | Security: prevents apps from bypassing background restrictions with generic foreground services |
| PendingIntent without mutability flag | FLAG_IMMUTABLE required for notification actions | Android 12 (API 31) | Security: prevents malicious apps from modifying PendingIntents |
| IMPORTANCE_HIGH for all persistent notifications | IMPORTANCE_DEFAULT for non-urgent persistent notifications | Android 8.0 (API 26) | User experience: reduces notification fatigue, heads-up notifications reserved for urgent alerts |

**Deprecated/outdated:**
- **READ_PHONE_STATE for call detection**: Replaced by audio focus system (privacy-first design). Still available but unnecessary for most use cases.
- **WakeLock for audio playback**: Android's audio APIs handle wake locks internally. Manual management causes battery drain and violates 2026 Google Play policy.
- **PhoneStateListener**: Deprecated in API 31+, replaced by TelephonyCallback. However, both are unnecessary—use audio focus instead.

## Open Questions

### Question 1: Wake Lock Necessity for Doze Mode Survival

**What we know:**
- Official docs state foreground services are NOT automatically stopped by Doze
- Audio playback APIs automatically manage wake locks
- WebSocket in SignalingClient.kt uses OkHttp with infinite read timeout
- Battery optimization exemption request is planned (user decision)

**What's unclear:**
- Will WebSocket heartbeats (25-second interval in SignalingClient.kt) continue sending during Doze idle phase without PARTIAL_WAKE_LOCK?
- Does battery optimization exemption alone keep foreground service's network access alive?

**Recommendation:**
1. **Test first without wake locks:** Launch Phase 7 using only foreground service + battery optimization exemption. Monitor WebSocket connection during Doze using Battery Historian and logcat.
2. **Add wake lock only if needed:** If WebSocket disconnects during Doze idle phase (30+ min screen-off), add PARTIAL_WAKE_LOCK specifically for heartbeat intervals (acquire before send, release after response).
3. **Measure battery impact:** Google Play enforces <2 cumulative hours wake lock per 24 hours. Validate with Battery Historian before release.

### Question 2: Notification Importance Level for Persistent Foreground Service

**What we know:**
- User decision: minimal updates (channel/state changes only, not speaker changes)
- User decision: notification shows channel name + current speaker
- Pocket radio metaphor: unobtrusive background operation

**What's unclear:**
- IMPORTANCE_DEFAULT (visible, no heads-up, makes sound): Appropriate for persistent service?
- IMPORTANCE_LOW (visible, no sound): Better for non-urgent persistent notification?
- User might want sound alert when channel audio starts—conflicts with minimal approach?

**Recommendation:**
Use **IMPORTANCE_LOW** (NotificationManager.IMPORTANCE_LOW):
- **Rationale:** Persistent notifications should not make sound (notification cooldown in Android 15+ would rate-limit anyway). User hears squelch tone + audio itself (user decision: "audio is the feedback"). Notification is purely status display, not alert.
- **User control:** Users can upgrade to IMPORTANCE_DEFAULT in system settings if they want notification sound.
- **Battery optimization:** Lower importance reduces system overhead for notification updates.

### Question 3: Audio Focus Request Type for Channel Audio

**What we know:**
- User decision: duck other audio apps when channel audio plays, restore volume after
- Existing AudioRouter.kt uses AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK with USAGE_VOICE_COMMUNICATION

**What's unclear:**
- Is AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK correct for continuous monitoring (not just PTT bursts)?
- Should permanent monitoring use AUDIOFOCUS_GAIN instead?
- Does USAGE_VOICE_COMMUNICATION prevent automatic ducking when music plays?

**Recommendation:**
**Keep AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK for both TX and RX:**
- **Rationale:** "Transient" fits pocket radio use case—audio comes in bursts (PTT transmissions), not continuous. Even during monitoring, actual audio playback is intermittent (only when someone speaks).
- **Ducking behavior:** AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK signals system that music apps can duck (lower volume) instead of pausing. User decision: "radio takes priority but doesn't kill music" → music ducks when radio audio plays, resumes full volume after.
- **USAGE_VOICE_COMMUNICATION:** Correct for walkie-talkie audio. Signals to system this is speech content (prevents automatic ducking of our audio).

## Sources

### Primary (HIGH confidence)

- [Foreground service types | Android Developers](https://developer.android.com/develop/background-work/services/fgs/service-types) - Service type requirements for API 34+
- [Manage audio focus | Android Developers](https://developer.android.com/media/optimize/audio-focus) - Audio focus request types, ducking, phone call handling
- [Optimize for Doze and App Standby | Android Developers](https://developer.android.com/training/monitoring-device-state/doze-standby) - Doze mode behavior, wake locks, battery optimization exemption
- [Create and manage notification channels | Android Developers](https://developer.android.com/develop/ui/views/notifications/channels) - Notification channel importance levels, user control
- [Choose the right API to keep the device awake | Android Developers](https://developer.android.com/develop/background-work/background-tasks/scheduling/wakelock) - Wake lock alternatives, when NOT to use wake locks
- [Follow wake lock best practices | Android Developers](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/best-practices) - Wake lock naming, foreground service requirement, try-finally pattern
- [Create a notification | Android Developers](https://developer.android.com/develop/ui/views/notifications/build-notification) - Notification action buttons, PendingIntent patterns

### Secondary (MEDIUM confidence)

- [Android Service API reference](https://developer.android.com/reference/android/app/Service) - START_STICKY vs START_NOT_STICKY behavior
- [Is it safe to use ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission? - Google Play Developer Community](https://support.google.com/googleplay/android-developer/thread/255896306) - 2026 battery optimization policy enforcement
- [Google Play Store to Warn Users of Battery-Draining Apps in 2026 | WebProNews](https://www.webpronews.com/google-play-store-to-warn-users-of-battery-draining-apps-in-2026/) - March 2026 enforcement timeline, wake lock metrics
- [Minimize your permission requests | Android Developers](https://developer.android.com/privacy-and-security/minimize-permission-requests) - Audio focus as alternative to READ_PHONE_STATE

### Tertiary (LOW confidence)

- [Building an Android service that never stops running | Roberto Huertas (2019)](https://robertohuertas.com/2019/06/29/android_foreground_services/) - Foreground service patterns (pre-API 34)
- [Action Button & Broadcast Receiver — Notifications in Android | Medium](https://medium.com/@stevdza-san/action-button-broadcast-receiver-notifications-in-android-69a0ae478d13) - Notification action implementation examples

## Metadata

**Confidence breakdown:**
- **Standard stack:** HIGH - All APIs verified with official Android docs, existing codebase uses compatible versions (API 26+, Hilt, Coroutines)
- **Architecture:** HIGH - Patterns verified with official Android Developer documentation and best practices guides
- **Pitfalls:** MEDIUM-HIGH - Pitfalls identified from official docs (API 34 service types, Android 12 PendingIntent), web search results (2026 battery policy), and prior project context (user decisions on service lifecycle)
- **Wake lock necessity:** MEDIUM - Official docs state foreground services survive Doze, but practical WebSocket survival during idle phase needs testing

**Research date:** 2026-02-10
**Valid until:** 2026-03-10 (30 days for stable Android APIs; March 2026 Google Play battery enforcement may introduce new details)
