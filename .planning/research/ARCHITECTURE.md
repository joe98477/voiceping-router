# Architecture Patterns: v4.0 Production Hardening & Location

**Domain:** Enterprise PTT communications platform (production hardening phase)
**Researched:** 2026-02-15
**Focus:** Integration of location tracking, audio reliability fixes, permission management, and power optimization into existing architecture

## Executive Summary

v4.0 adds production-critical features to the existing singleton-based Hilt DI architecture without major structural changes. Location tracking, permission management, and power optimization slot cleanly into the established patterns. Audio reliability improvements target the existing MediasoupClient/PttManager pipeline with buffer tuning and state machine hardening.

**Key Integration Points:**
- LocationManager fits as a new @Singleton alongside existing managers (AudioRouter, AudioDeviceManager, PttManager)
- Location data flows: LocationManager → SignalingClient → Server → Redis pub/sub → Dispatch web UI (existing pattern)
- Audio reliability: WebRTC jitter buffer tuning + MediasoupClient state machine hardening (no new components)
- Permission management: New PermissionManager @Singleton with Activity/ViewModel delegation
- Power optimization: Leverage existing foreground service + WorkManager for location batching

**Architectural Philosophy:**
Maintain the v2.0/v3.0 clean architecture: presentation → data → domain layers with @Singleton providers in the data layer. No circular dependencies. New features add singletons, not new layers.

## Existing Architecture Overview (v3.0 Baseline)

### Component Map

| Layer | Component | Type | Responsibility |
|-------|-----------|------|----------------|
| **Data** | MediasoupClient | @Singleton | WebRTC Device, transports (RecvTransport map, SendTransport singleton), consumers/producers, lifecycle |
| **Data** | SignalingClient | @Singleton | WebSocket connection, request/response correlation, heartbeat, reconnect logic |
| **Data** | PttManager | @Singleton | PTT state machine (Idle/Requesting/Transmitting/Denied), audio production lifecycle |
| **Data** | ChannelRepository | @Singleton | Multi-channel monitoring, speaker tracking, primary channel concept, consumer management |
| **Data** | AudioRouter | @Singleton | Audio focus, mode control (earpiece/speaker/BT), phone call handling |
| **Data** | AudioDeviceManager | @Singleton | BT device enumeration, output device selection, BT disconnect detection |
| **Data** | AuthRepository | @Singleton | Login, JWT token refresh, router token acquisition |
| **Data** | SettingsRepository | @Singleton | DataStore preferences (6 groups), Flow-based settings observation |
| **Data** | NetworkMonitor | @Singleton | Network connectivity state, WiFi/cellular transitions |
| **Data** | HapticFeedback | @Singleton | Vibration patterns (PTT press/release/busy) |
| **Data** | TonePlayer | @Singleton | Audio cues (PTT start, roger beep, error, connection/disconnection) |
| **Data** | MediaButtonHandler | @Singleton | Bluetooth media button PTT integration via MediaSession |
| **Presentation** | ViewModels | @HiltViewModel | UI state, user actions → repository calls |
| **Service** | ChannelMonitoringService | ForegroundService | Persistent notification, pocket radio mode, wake lock |
| **Service** | AudioCaptureService | ForegroundService | Microphone permission foreground indicator (PTT transmission) |

### Dependency Flow (No Circular Dependencies)

```
ViewModels
  ↓
Repositories (ChannelRepository, AuthRepository, EventRepository)
  ↓
Managers (PttManager, AudioRouter, AudioDeviceManager, MediaButtonHandler)
  ↓
Clients (MediasoupClient, SignalingClient, NetworkMonitor)
  ↓
Infrastructure (OkHttpClient, Gson, Room Database, DataStore)
```

**Critical Pattern:** Callbacks for cross-layer communication without circular deps
- Example: `PttManager.onPttGranted` callback wired by `ChannelRepository` to trigger `TonePlayer.playPttStartTone()`
- Example: `AudioRouter.onPhoneCallStarted` callback wired by `ChannelRepository` to force-release PTT

### Data Flow: Existing WebRTC Audio

**Receive Path (Multi-channel):**
```
Server producer → SignalingClient (SPEAKER_CHANGED event)
  → ChannelRepository.observeSpeakerChanges()
  → MediasoupClient.createRecvTransport(channelId) [per-channel map]
  → MediasoupClient.consumeAudio(channelId, producerId, peerId)
  → Consumer.resume() → AudioTrack playback (volume 0-10)
  → AudioRouter (earpiece/speaker/BT routing)
```

**Transmit Path (PTT):**
```
User PTT press → PttManager.requestPtt(channelId)
  → SignalingClient.request(PTT_START) [wait for server grant]
  → MediasoupClient.createSendTransport(channelId) [singleton]
  → MediasoupClient.startProducing()
  → AudioSource (WebRTC mic capture) → AudioTrack → Producer (Opus CBR, DTX, FEC)
  → SendTransport → RTP packets to server
User PTT release → PttManager.releasePtt()
  → MediasoupClient.stopProducing() [dispose AudioSource, AudioTrack, Producer]
  → SignalingClient.send(PTT_STOP)
```

## v4.0 Architecture Integration

### 1. Location Tracking Architecture

#### Component: LocationManager (@Singleton)

**Placement:** Data layer, peer to PttManager/AudioRouter/AudioDeviceManager

**Responsibilities:**
- FusedLocationProviderClient initialization and lifecycle
- Adaptive location update strategy (precise/general/throttled modes)
- Motion-aware throttling using activity recognition
- Location batching for power efficiency
- Permission state coordination with PermissionManager

**DI Wiring:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @Singleton
    fun provideLocationManager(
        @ApplicationContext context: Context,
        signalingClient: SignalingClient,
        settingsRepository: SettingsRepository,
        permissionManager: PermissionManager
    ): LocationManager
}
```

**Why FusedLocationProvider:**
- Battery-optimized (batches location requests from multiple apps)
- Adaptive fused strategy (GPS, WiFi, cellular based on accuracy needs)
- PRIORITY_BALANCED_POWER_ACCURACY default (sufficient for dispatch tracking)
- Official Google recommendation for 2026+ (getCurrentLocation() pattern)

Source: [Android FusedLocationProvider Documentation](https://developers.google.com/location-context/fused-location-provider)

#### Data Flow: Android → Server → Dispatch

```
LocationManager.startTracking(mode: LocationTrackingMode)
  ↓
FusedLocationProviderClient.getCurrentLocation() [batched every 5-60s based on mode]
  ↓
LocationManager processes location fix (lat/lon/accuracy/timestamp)
  ↓
SignalingClient.send(LOCATION_UPDATE, { channelId, location: { lat, lon, accuracy, timestamp } })
  ↓
Server: channelRouter receives location update
  ↓
Server: Redis pub/sub broadcasts to dispatch console subscribers
  ↓
Web UI: React dispatch console updates marker on map
```

**Server Changes (Minimal):**
- New signaling type: `LOCATION_UPDATE` (send-only from Android, no response)
- channelRouter handler: validate user is in channel, broadcast to dispatch subscribers
- Redis pub/sub pattern: reuse existing `channel:${channelId}:events` pattern
- Database persistence: optional (store last known location per user for dispatch history)

#### Location Tracking Modes

| Mode | Update Interval | Accuracy | Use Case | Battery Impact |
|------|----------------|----------|----------|----------------|
| PRECISE | 5s | PRIORITY_HIGH_ACCURACY | Active PTT transmission | High (5-8%/hour) |
| GENERAL | 60s | PRIORITY_BALANCED_POWER_ACCURACY | Monitoring (idle) | Low (1-2%/hour) |
| MOTION_AWARE | Variable | Dynamic (high when moving, low when stationary) | Auto-detect motion | Medium (2-4%/hour) |
| OFF | - | - | User disabled location | None |

**Implementation Strategy:**
```kotlin
@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signalingClient: SignalingClient,
    private val settingsRepository: SettingsRepository,
    private val permissionManager: PermissionManager
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var currentMode: LocationTrackingMode = LocationTrackingMode.OFF
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Motion detection for adaptive throttling
    private val activityRecognitionClient = ActivityRecognition.getClient(context)

    suspend fun startTracking(mode: LocationTrackingMode) {
        if (!permissionManager.hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted, cannot start tracking")
            return
        }

        currentMode = mode
        val interval = when (mode) {
            LocationTrackingMode.PRECISE -> 5_000L
            LocationTrackingMode.GENERAL -> 60_000L
            LocationTrackingMode.MOTION_AWARE -> 15_000L // Base interval, adjusted by motion
            LocationTrackingMode.OFF -> return
        }

        val priority = when (mode) {
            LocationTrackingMode.PRECISE -> Priority.PRIORITY_HIGH_ACCURACY
            else -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, interval)
            .setMaxUpdateDelayMillis(interval * 3) // Batch up to 3 intervals for power efficiency
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                sendLocationUpdate(location)
            }
        }
    }

    private fun sendLocationUpdate(location: Location) {
        scope.launch {
            // Send to server via existing SignalingClient
            val channelId = getCurrentChannelId() ?: return@launch
            signalingClient.send(
                SignalingType.LOCATION_UPDATE,
                mapOf(
                    "channelId" to channelId,
                    "location" to mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "accuracy" to location.accuracy,
                        "timestamp" to location.time
                    )
                )
            )
        }
    }
}
```

**ViewModel Integration:**
```kotlin
@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val locationManager: LocationManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    init {
        // Start location tracking when monitoring channels
        viewModelScope.launch {
            combine(
                monitoredChannels,
                settingsRepository.getLocationTrackingMode()
            ) { channels, mode ->
                if (channels.isNotEmpty() && mode != LocationTrackingMode.OFF) {
                    locationManager.startTracking(mode)
                } else {
                    locationManager.stopTracking()
                }
            }.collect()
        }
    }
}
```

**Foreground Service Integration:**

Location tracking requires `ACCESS_BACKGROUND_LOCATION` permission and background location access is subject to strict Google Play Store policy. The existing `ChannelMonitoringService` (foreground service type `mediaPlayback`) provides user-visible justification.

Add to service:
```kotlin
// ChannelMonitoringService.kt
@Inject lateinit var locationManager: LocationManager

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_START -> {
            // ... existing notification code ...

            // Start location tracking if enabled
            scope.launch {
                settingsRepository.getLocationTrackingMode().first().let { mode ->
                    if (mode != LocationTrackingMode.OFF) {
                        locationManager.startTracking(mode)
                    }
                }
            }
        }
        ACTION_STOP -> {
            locationManager.stopTracking()
            // ... existing cleanup ...
        }
    }
}
```

**Permission Requirements:**
- `ACCESS_FINE_LOCATION`: For precise GPS location (required)
- `ACCESS_COARSE_LOCATION`: Fallback for network-based location (optional but recommended)
- `ACCESS_BACKGROUND_LOCATION`: For location updates when app in background (required for foreground service location)

Source: [Android Location Permissions](https://developer.android.com/develop/sensors-and-location/location/permissions)

#### Dispatch Web UI Integration

Server-side changes are minimal:

```typescript
// server/src/signaling/channelRouter.ts (NEW handler)
case 'location-update': {
    const { channelId, location } = data;

    // Validate user is in channel
    const channelState = channelStates.get(channelId);
    if (!channelState?.members.has(ws)) {
        return { error: 'Not a member of channel' };
    }

    // Broadcast to dispatch console subscribers (existing pattern)
    const locationEvent = {
        type: 'location-update',
        userId: ws.userId,
        userName: ws.userName,
        location: {
            latitude: location.latitude,
            longitude: location.longitude,
            accuracy: location.accuracy,
            timestamp: location.timestamp
        }
    };

    // Use existing Redis pub/sub for dispatch updates
    await redisPublisher.publish(
        `channel:${channelId}:events`,
        JSON.stringify(locationEvent)
    );

    // Optional: persist to database for dispatch history
    await prisma.locationHistory.create({
        data: {
            userId: ws.userId,
            channelId,
            latitude: location.latitude,
            longitude: location.longitude,
            accuracy: location.accuracy,
            timestamp: new Date(location.timestamp)
        }
    });

    break;
}
```

Web dispatch console subscribes to location updates via existing Redis subscription:

```typescript
// web/src/components/DispatchConsole.tsx (NEW map overlay)
const [userLocations, setUserLocations] = useState<Map<string, Location>>(new Map());

useEffect(() => {
    socket.on('location-update', (event: LocationUpdateEvent) => {
        setUserLocations(prev => new Map(prev).set(event.userId, event.location));
    });
}, []);

return (
    <MapContainer>
        {Array.from(userLocations.entries()).map(([userId, location]) => (
            <Marker
                key={userId}
                position={[location.latitude, location.longitude]}
                icon={userMarkerIcon}
            >
                <Popup>{userName} - Accuracy: {location.accuracy}m</Popup>
            </Marker>
        ))}
    </MapContainer>
);
```

### 2. Audio Reliability Fixes

**Problem:** Intermittent PTT silence, choppy audio, late arrivals

**Root Causes (from research):**
1. WebRTC jitter buffer too small for mobile networks ([WebRTC NetEQ Jitter Buffer](https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/))
2. Producer/Consumer state race conditions (already fixed in Phase 15-02 with `@Volatile producingRequested` flag)
3. Network flapping causing transport disconnection before auto-recovery window

**Architecture Changes:** No new components, tune existing MediasoupClient

#### Jitter Buffer Tuning

WebRTC's NetEQ adaptive jitter buffer defaults to 40ms initial, can grow to 120ms. For mobile PTT with cellular packet variance, increase target:

```kotlin
// MediasoupClient.kt
private fun createConsumerWithJitterBuffer(transport: RecvTransport, ...): Consumer {
    val consumer = transport.consume(...)

    // Increase jitter buffer target for mobile networks
    // Note: crow-misia library exposes jitterBufferTarget via Consumer.setJitterBufferTarget()
    // if available in libmediasoup-android 0.21.0 bindings
    consumer.setJitterBufferTarget(80) // 80ms target (vs default 40ms)

    consumer.resume()
    return consumer
}
```

**Caveat:** Check crow-misia 0.21.0 API documentation for actual jitter buffer control. If not exposed, this becomes server-side mediasoup configuration:

```typescript
// server/src/mediasoup/worker.ts
const rtpCapabilities = router.rtpCapabilities;

// Enable Opus in-band FEC (forward error correction) for packet loss recovery
rtpCapabilities.codecs.find(c => c.mimeType === 'audio/opus')?.parameters = {
    ...existingParams,
    useinbandfec: 1, // Enable FEC
    maxaveragebitrate: 48000, // Higher bitrate for better quality
};
```

Source: [mediasoup Opus FEC](https://github.com/versatica/mediasoup/issues/234)

#### State Machine Hardening

Already addressed in v3.0 Phase 14-15:
- Mutex-protected transport lifecycle (prevents concurrent creation/destruction)
- `@Volatile producingRequested` flag (prevents orphaned producer on PTT release during `produce()` blocking call)
- Connection state differentiation (disconnected vs failed for auto-recovery window)

**Additional Hardening (v4.0):**

```kotlin
// MediasoupClient.kt
private val transportHealthMonitor = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun startTransportHealthMonitoring() {
    transportHealthMonitor.launch {
        while (isActive) {
            delay(5_000L) // Check every 5 seconds

            // Monitor SendTransport health
            sendTransport?.let { transport ->
                val state = transport.connectionState
                if (state == "disconnected") {
                    val disconnectedDuration = System.currentTimeMillis() - disconnectedSince
                    if (disconnectedDuration > 15_000L) {
                        // Auto-recovery failed, force cleanup
                        Log.w(TAG, "SendTransport disconnected >15s, forcing cleanup")
                        audioProducer?.close()
                        audioProducer = null
                        sendTransport = null
                    }
                }
            }

            // Monitor RecvTransport health per channel
            recvTransports.forEach { (channelId, transport) ->
                val state = transport.connectionState
                if (state == "disconnected") {
                    // Similar auto-recovery monitoring
                }
            }
        }
    }
}
```

#### Retry Logic for Produce Failures

```kotlin
// PttManager.kt
suspend fun requestPtt(channelId: String) {
    // ... existing PTT_START request ...

    // Step 5: Start producing with retry
    var produceAttempts = 0
    while (produceAttempts < 3) {
        try {
            mediasoupClient.startProducing()
            break // Success
        } catch (e: Exception) {
            produceAttempts++
            Log.w(TAG, "Produce failed (attempt $produceAttempts/3): ${e.message}")
            if (produceAttempts >= 3) {
                // Give up, release PTT
                _pttState.value = PttState.Idle
                onPttDenied?.invoke()
                signalingClient.send(SignalingType.PTT_STOP, mapOf("channelId" to channelId))
                throw e
            }
            delay(500L * produceAttempts) // Exponential backoff
        }
    }
}
```

### 3. Permission Management Architecture

**Problem:** Android 6+ runtime permissions require request-at-usage pattern. Currently implicit (crash on permission denial).

**Solution:** Centralized PermissionManager @Singleton with Activity/ViewModel delegation

#### Component: PermissionManager (@Singleton)

**Placement:** Data layer (peer to SettingsRepository, AuthRepository)

**Responsibilities:**
- Permission state checking (granted/denied/never-ask-again)
- Permission request coordination with Activity via callback
- Rationale display logic (when to show explanation before request)
- Settings redirect for "never ask again" state

**DI Wiring:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object PermissionModule {
    @Provides
    @Singleton
    fun providePermissionManager(
        @ApplicationContext context: Context
    ): PermissionManager
}
```

**Implementation Pattern:**

```kotlin
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Callback for Activity to handle permission request UI
    var requestPermissionCallback: ((Array<String>, (Map<String, Boolean>) -> Unit) -> Unit)? = null

    fun hasAudioPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on Android 9 and below
        }
    }

    suspend fun requestAudioPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        val callback = requestPermissionCallback
        if (callback == null) {
            Log.e(TAG, "Permission request callback not set (Activity not ready)")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        callback(arrayOf(Manifest.permission.RECORD_AUDIO)) { results ->
            continuation.resume(results[Manifest.permission.RECORD_AUDIO] == true)
        }
    }

    suspend fun requestLocationPermissions(): Boolean = suspendCancellableCoroutine { continuation ->
        val callback = requestPermissionCallback
        if (callback == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        // Incremental request: foreground first, then background
        callback(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) { results ->
            if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                // Foreground granted, now request background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    callback(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) { bgResults ->
                        continuation.resume(bgResults[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true)
                    }
                } else {
                    continuation.resume(true)
                }
            } else {
                continuation.resume(false)
            }
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
```

Source: [Android Permission Best Practices](https://developer.android.com/training/permissions/usage-notes)

**Activity Integration:**

```kotlin
// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionManager: PermissionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        currentPermissionCallback?.invoke(permissions)
        currentPermissionCallback = null
    }

    private var currentPermissionCallback: ((Map<String, Boolean>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wire permission request callback
        permissionManager.requestPermissionCallback = { permissions, callback ->
            currentPermissionCallback = callback
            permissionLauncher.launch(permissions)
        }

        // ... existing Compose setup ...
    }
}
```

**ViewModel Usage:**

```kotlin
// ChannelListViewModel.kt
fun onPttButtonPressed() {
    viewModelScope.launch {
        if (!permissionManager.hasAudioPermissions()) {
            val granted = permissionManager.requestAudioPermission()
            if (!granted) {
                _uiState.update { it.copy(
                    errorMessage = "Microphone permission required for PTT"
                ) }
                return@launch
            }
        }

        // Permission granted, proceed with PTT
        val targetChannelId = getHardwarePttTargetChannelId()
        if (targetChannelId != null) {
            pttManager.requestPtt(targetChannelId)
        }
    }
}
```

**First Launch Permission Prompt:**

```kotlin
// LoadingViewModel.kt
fun checkAndRequestPermissions() {
    viewModelScope.launch {
        val missingPermissions = mutableListOf<String>()

        if (!permissionManager.hasAudioPermissions()) {
            missingPermissions.add("Microphone (for PTT)")
        }
        if (!permissionManager.hasLocationPermission()) {
            missingPermissions.add("Location (for dispatch tracking)")
        }

        if (missingPermissions.isNotEmpty()) {
            _uiState.update { it.copy(
                showPermissionRationale = true,
                requiredPermissions = missingPermissions
            ) }
        } else {
            proceedToApp()
        }
    }
}
```

### 4. Power Optimization Architecture

**Goal:** Minimize battery drain for 24/7 pocket radio operation

**Current Baseline (v3.0):** 5%/hour with screen off, foreground service, active WebSocket

**Optimization Targets:**
- Location tracking: 1-2%/hour additional (GENERAL mode with batching)
- Network monitoring: Reduce polling frequency
- Wake lock optimization: Use partial wake lock only when needed

#### Strategy 1: Location Batching with WorkManager

For GENERAL mode (60s updates), batch location updates instead of real-time transmission:

```kotlin
@Singleton
class LocationBatchManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationManager: LocationManager
) {
    private val workManager = WorkManager.getInstance(context)

    fun startBatchedTracking() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<LocationUploadWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "location_batch_upload",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class LocationUploadWorker @Inject constructor(
    context: Context,
    params: WorkerParameters,
    private val signalingClient: SignalingClient,
    private val locationDatabase: LocationDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Upload batched location updates
        val pendingLocations = locationDatabase.getPendingLocations()

        pendingLocations.forEach { location ->
            try {
                signalingClient.send(SignalingType.LOCATION_UPDATE, location.toMap())
                locationDatabase.markUploaded(location.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload location", e)
            }
        }

        return Result.success()
    }
}
```

Source: [Android WorkManager Battery Optimization](https://developer.android.com/develop/background-work/background-tasks/optimize-battery)

#### Strategy 2: Network Quality Polling Reduction

Current: 5-second consumer stats polling (Phase 12-02)

Optimization: Increase to 15 seconds for idle channels, 5 seconds for active speakers

```kotlin
// ChannelRepository.kt
private fun startNetworkQualityMonitoring(channelId: String, consumerId: String) {
    scope.launch {
        while (isActive) {
            // Dynamic polling interval based on activity
            val interval = if (isChannelActivelyTransmitting(channelId)) {
                5_000L // Active: 5s
            } else {
                15_000L // Idle: 15s
            }

            delay(interval)

            val stats = mediasoupClient.getConsumerStats(consumerId)
            _networkQuality.update { it + (channelId to stats?.indicator.orEmpty()) }
        }
    }
}
```

#### Strategy 3: Wake Lock Scoping

Current: Foreground service holds wake lock continuously

Optimization: Release wake lock when no active audio (no speakers for >30s)

```kotlin
// ChannelMonitoringService.kt
private var wakeLock: PowerManager.WakeLock? = null
private var lastSpeakerActivityMs: Long = System.currentTimeMillis()
private val wakeLockScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun startWakeLockMonitoring() {
    wakeLockScope.launch {
        while (isActive) {
            delay(10_000L) // Check every 10s

            val idleDuration = System.currentTimeMillis() - lastSpeakerActivityMs
            if (idleDuration > 30_000L) {
                // No speaker activity for 30s, release wake lock
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "Released wake lock (idle for 30s)")
                    }
                }
            }
        }
    }
}

fun onSpeakerActivity() {
    lastSpeakerActivityMs = System.currentTimeMillis()

    // Acquire wake lock on speaker activity
    wakeLock?.let {
        if (!it.isHeld) {
            it.acquire(10*60*1000L /*10 minutes max*/)
            Log.d(TAG, "Acquired wake lock (speaker activity)")
        }
    }
}
```

**Battery Target (v4.0):**
- Base (screen off, monitoring): 5%/hour (existing)
- + Location tracking (GENERAL): +1%/hour
- + Network quality polling reduction: -0.5%/hour
- + Wake lock optimization: -1%/hour
- **Total: ~4.5%/hour** (22+ hours of operation)

### 5. Security Hardening Architecture

**Goal:** Production-ready security audit compliance

#### TLS Certificate Validation

**Current:** OkHttpClient uses default TLS with system CA trust

**Hardening:** Add certificate transparency enforcement, optional pinning

```kotlin
// AppModule.kt
@Provides
@Singleton
fun provideOkHttpClient(cookieJar: SessionCookieJar): OkHttpClient {
    val certificatePinner = CertificatePinner.Builder()
        // Optional: Pin server certificate for MITM protection
        // .add("your-domain.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        .build()

    return OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .certificatePinner(certificatePinner)
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
```

**Note:** Certificate pinning NOT recommended for production by Google (operational risk during certificate rotation). Use only if required by client security policy.

Source: [Android TLS Security](https://developer.android.com/training/articles/security-ssl)

#### WebSocket TLS Enforcement

```kotlin
// SignalingClient.kt
fun connect(url: String, token: String) {
    // Enforce wss:// (TLS WebSocket)
    if (!url.startsWith("wss://")) {
        throw IllegalArgumentException("WebSocket URL must use wss:// (TLS)")
    }

    // ... existing connection logic ...
}
```

#### Secure Token Storage

Current: DataStore (encrypted at rest by Android)

Additional: Use EncryptedSharedPreferences for JWT tokens

```kotlin
@Singleton
class SecureTokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        encryptedPrefs.edit().putString("jwt_token", token).apply()
    }

    fun getToken(): String? {
        return encryptedPrefs.getString("jwt_token", null)
    }
}
```

## Build Order and Dependencies

### Dependency Graph

```
Phase 16: Permission Management (no dependencies)
  ↓
Phase 17: Location Tracking (depends on PermissionManager)
  ↓
Phase 18: Audio Reliability (independent, can run parallel with 17)
  ↓
Phase 19: Power Optimization (depends on LocationManager, audio fixes)
  ↓
Phase 20: Security Hardening (independent, can run parallel with 18-19)
```

### Suggested Phase Structure

**Phase 16: Permission Management Foundation**
- Plan 1: PermissionManager @Singleton, MainActivity integration, first-launch flow
- Plan 2: Permission rationale dialogs, settings redirect for denied permissions

**Phase 17: Location Tracking**
- Plan 1: LocationManager @Singleton, FusedLocationProviderClient, adaptive modes
- Plan 2: Server signaling type LOCATION_UPDATE, Redis pub/sub, dispatch web UI map overlay

**Phase 18: Audio Reliability Fixes**
- Plan 1: WebRTC jitter buffer tuning, Opus FEC configuration, state machine hardening
- Plan 2: Produce retry logic, transport health monitoring, error recovery

**Phase 19: Power Optimization**
- Plan 1: Location batching with WorkManager, network quality polling reduction
- Plan 2: Wake lock scoping, battery profiling validation

**Phase 20: Security Audit and Hardening**
- Plan 1: TLS enforcement, secure token storage, certificate transparency
- Plan 2: Full security audit (penetration testing, dependency scan), documentation

## Component Boundaries

| Component | What It Owns | What It Delegates |
|-----------|--------------|-------------------|
| **LocationManager** | FusedLocationProviderClient lifecycle, location update batching, mode switching | Permission checks (to PermissionManager), server transmission (to SignalingClient) |
| **PermissionManager** | Permission state checking, request coordination, rationale display | Actual request UI (to Activity via callback) |
| **MediasoupClient** | WebRTC Device, transports, producers/consumers, jitter buffer tuning | Permission checks (implicit via Android), network state (to NetworkMonitor) |
| **PttManager** | PTT state machine, produce retry logic, duration tracking | Audio feedback (to TonePlayer/HapticFeedback via callbacks), permissions (to PermissionManager) |
| **ChannelRepository** | Multi-channel state, speaker tracking, consumer management | PTT logic (to PttManager), audio routing (to AudioRouter), location (to LocationManager) |

## Anti-Patterns to Avoid

### Anti-Pattern 1: Permission Requests in @Singleton Constructors
**What goes wrong:** Singletons initialize before Activity exists, permission dialogs crash
**Prevention:** Use lazy permission checks with PermissionManager callback pattern
**Detection:** App crashes with "Can't request permissions before Activity created"

### Anti-Pattern 2: Location Updates on Main Thread
**What goes wrong:** ANR (application not responding) during location fix
**Prevention:** Always use Dispatchers.IO for FusedLocationProviderClient calls
**Detection:** StrictMode warnings, ANR crashes

### Anti-Pattern 3: Circular Dependencies Between LocationManager and ChannelRepository
**What goes wrong:** Hilt DI fails with cycle detection
**Prevention:** LocationManager depends on SignalingClient (NOT ChannelRepository), ChannelRepository depends on LocationManager
**Detection:** Hilt compilation error: "Dependency cycle detected"

### Anti-Pattern 4: Synchronous Permission Requests in Suspend Functions
**What goes wrong:** Coroutine blocks indefinitely waiting for user action
**Prevention:** Use suspendCancellableCoroutine for permission callbacks
**Detection:** App freezes on permission request, no timeout

### Anti-Pattern 5: Certificate Pinning Without Backup Pins
**What goes wrong:** Certificate rotation breaks app, requires APK redeployment
**Prevention:** Include backup pins, use certificate transparency instead
**Detection:** All API calls fail after certificate rotation

## Scalability Considerations

| Concern | At 100 users | At 10K users | At 1M users |
|---------|--------------|--------------|-------------|
| Location updates | 100 updates/min, trivial server load | 10K updates/min, batch to Redis every 5s | 1M updates/min, use time-series DB (InfluxDB), WebSocket broadcast only to active dispatch consoles |
| Audio jitter buffer | 40ms default sufficient | 80ms target for mobile variance | Consider dedicated edge servers per region to reduce RTT |
| Permission state | In-memory per device | In-memory per device | In-memory per device (no server-side permission state) |
| Battery consumption | 4.5%/hour acceptable | Same (per-device concern) | Same (per-device concern) |

## Sources

### Location Tracking
- [About background location and battery life](https://developer.android.com/develop/sensors-and-location/location/battery)
- [Optimize location use for real-world scenarios](https://developer.android.com/develop/sensors-and-location/location/battery/scenarios)
- [Fused Location Provider API](https://developers.google.com/location-context/fused-location-provider)
- [Request location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions)
- [Request background location](https://developer.android.com/develop/sensors-and-location/location/permissions/background)

### Audio Reliability
- [How WebRTC's NetEQ Jitter Buffer Provides Smooth Audio](https://webrtchacks.com/how-webrtcs-neteq-jitter-buffer-provides-smooth-audio/)
- [WebRTC and Buffers](https://getstream.io/resources/projects/webrtc/advanced/buffers/)
- [mediasoup Opus FEC Issue](https://github.com/versatica/mediasoup/issues/234)
- [mediasoup API Documentation](https://mediasoup.org/documentation/v3/mediasoup/api/)

### Permission Management
- [App permissions best practices](https://developer.android.com/training/permissions/usage-notes)
- [Request runtime permissions](https://developer.android.com/training/permissions/requesting)
- [Permissions on Android](https://developer.android.com/guide/topics/permissions/overview)

### Power Optimization
- [Optimize battery use for task scheduling APIs](https://developer.android.com/develop/background-work/background-tasks/optimize-battery)
- [Android Performance Optimization & Battery Efficiency Guide](https://www.oneclickitsolution.com/centerofexcellence/android/android-performance-optimization-battery-efficiency-guide)
- [Battery Optimization for Android Apps](https://blog.mindorks.com/battery-optimization-for-android-apps-f4ef6170ff70)

### Security
- [Security with network protocols](https://developer.android.com/training/articles/security-ssl)
- [Android SSL Certificate Pinning](https://nextnative.dev/blog/android-ssl-certificate-pinning)
- [Secure Android Apps with TLS/SSL Pinning](https://proandroiddev.com/secure-android-apps-with-tls-ssl-pinning-c087fc7ef828)

### Dispatch Integration
- [Real-Time Location Tracking APIs](https://www.pubnub.com/how-to/explore-real-time-geolocation-solutions/)
- [Implementing Real-Time Location Tracking with WebSockets](https://slaptijack.com/programming/implementing-real-time-location-tracking-with-websockets.html)

---
*Last updated: 2026-02-15*
