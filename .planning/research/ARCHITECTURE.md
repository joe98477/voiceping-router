# Architecture Research: Android PTT Client

**Project:** VoicePing Android PTT Client
**Researched:** 2026-02-08
**Confidence:** MEDIUM

## Executive Summary

The Android PTT client integrates with the existing mediasoup-based WebRTC server using established patterns from the web client. The architecture centers on a foreground service maintaining multiple concurrent WebSocket connections (one per monitored channel), with mediasoup-client Android bindings providing WebRTC transport and audio pipeline management. Clean Architecture principles separate concerns into data/domain/presentation layers, while scan mode state management coordinates multi-channel monitoring with automatic channel switching.

## Integration Points with Existing Server

### Signaling Protocol (NO SERVER CHANGES)

The Android client implements the SAME JSON WebSocket signaling protocol at `ws://server/ws`:

**Authentication Flow:**
1. HTTP GET `/api/router/token` with credentials
2. Receive JWT containing `userId`, `userName`, `eventId`, `channelIds[]`, `globalRole`, `eventRole`
3. WebSocket connection with `Sec-WebSocket-Protocol: voiceping, <jwt>`
4. Server validates JWT and establishes ClientContext

**Core Message Types (from protocol.ts):**
```
Channel Management:
  - JOIN_CHANNEL → Server assigns channel, returns channel state
  - LEAVE_CHANNEL → Cleanup transports/consumers

WebRTC Negotiation:
  - GET_ROUTER_CAPABILITIES → Returns mediasoup router RTP capabilities
  - CREATE_TRANSPORT → Server creates WebRtcTransport, returns id/iceParameters/iceCandidates/dtlsParameters
  - CONNECT_TRANSPORT → Client provides dtlsParameters after ICE connection
  - PRODUCE → Create audio producer (mic), returns producerId
  - CONSUME → Subscribe to speaker's producer, returns consumerId/rtpParameters

PTT Control:
  - PTT_START → Acquire speaker lock (if available)
  - PTT_STOP → Release speaker lock
  - PTT_DENIED → Lock unavailable (someone else transmitting)
  - SPEAKER_CHANGED → Broadcast when speaker changes (resume/pause consumers)

Admin/Dispatch (Phase 2+):
  - PRIORITY_PTT_START → Dispatcher overrides regular PTT
  - EMERGENCY_BROADCAST_START → Force-join all users to broadcast channel
  - PERMISSION_UPDATE → Real-time permission changes via Redis pub/sub
  - FORCE_DISCONNECT → Admin kicks user from channel

Health:
  - PING/PONG → 30-second heartbeat interval
```

**Message Structure:**
```json
{
  "type": "join-channel",
  "id": "<correlation-id>",
  "data": {
    "channelId": "channel-123"
  }
}
```

All requests use request-response pattern with correlation IDs. Server broadcasts use type-only messages (no correlation ID).

### Audio Pipeline Integration

**Server-Side Audio Flow (mediasoup 3.19):**
```
Android Client Mic → WebRTC Producer (Opus)
  → mediasoup Router
  → WebRTC Consumer (Opus) → Other Clients' Speakers
```

**Key Server Components:**
- RouterManager: Worker pool (CPU count), one Router per worker
- TransportManager: Creates WebRtcTransport pairs (send/recv) per client per channel
- ProducerConsumerManager: Manages Producer (mic) and Consumer (speaker) lifecycle
- ChannelStateManager: PTT speaker locks, tracks current speaker per channel

**Android Integration Requirements:**
1. Implement mediasoup-client Device API (load RTP capabilities)
2. Create send/recv WebRtcTransport per channel
3. Create ONE Producer (mic) when PTT_START granted
4. Create Consumers for all other channel members' Producers
5. Pause/Resume Consumers based on SPEAKER_CHANGED broadcasts

### Multi-Channel Architecture

**Dispatcher Use Case (5 Simultaneous Channels):**

Server expects ONE WebSocket connection per channel:
- Each connection = separate ClientContext with channelId
- Each connection = separate send/recv WebRtcTransport pair
- Each connection = separate set of Consumers (one per speaker in that channel)

**Android Implementation:**
```
ConnectionManager (per channel) {
  - SignalingClient (WebSocket to /ws)
  - MediasoupDevice (singleton, shared across connections)
  - SendTransport (send mic audio when PTT active)
  - RecvTransport (receive all speakers' audio)
  - List<Consumer> (one per speaker in this channel)
}

ForegroundService {
  - Map<channelId, ConnectionManager>
  - Scan mode state machine
  - Audio focus management
  - Hardware PTT button listener
}
```

### Authentication & Session Management

**JWT Token Management:**
- Token TTL: 1 hour
- Heartbeat: 30 seconds (PING/PONG)
- Permission refresh: 30 seconds via Redis pub/sub (PERMISSION_UPDATE message)
- Re-auth strategy: On 401 response, fetch new token and reconnect

**Rate Limits (from rateLimiter.ts):**
- WebSocket connections: 60/minute per userId
- Auth requests: 40/15 minutes per IP
- Progressive backoff on violations (1s, 2s, 4s, 8s, 16s, cap at 30s)

**Reconnection Strategy (from ReconnectingSignalingClient pattern):**
- Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s (cap at 32s)
- Message queue during disconnect (buffer outgoing requests)
- Session recovery: Re-JOIN_CHANNEL with same channelId
- Consumer state recovery: Server tracks active Producers, client re-CONSUME

## Component Architecture (Kotlin)

### Clean Architecture Layers

```
├── presentation/          # UI Layer (Activities, ViewModels, Compose UI)
│   ├── channels/
│   │   ├── ChannelListScreen.kt
│   │   ├── ChannelListViewModel.kt
│   ├── scan/
│   │   ├── ScanModeScreen.kt
│   │   ├── ScanModeViewModel.kt
│   ├── settings/
│   └── common/
│       ├── PttButton.kt
│       └── ChannelCard.kt
│
├── domain/                # Business Logic (Use Cases, Repository Interfaces)
│   ├── usecases/
│   │   ├── JoinChannelUseCase.kt
│   │   ├── StartPttUseCase.kt
│   │   ├── MonitorChannelUseCase.kt (scan mode)
│   │   ├── SwitchActiveChannelUseCase.kt
│   ├── repositories/
│   │   ├── IChannelRepository.kt
│   │   ├── IAuthRepository.kt
│   │   ├── IAudioRepository.kt
│   ├── models/
│   │   ├── Channel.kt
│   │   ├── User.kt
│   │   ├── PttState.kt (Idle, Requesting, Transmitting, Receiving)
│   │   ├── ScanModeState.kt (Off, Monitoring, Active)
│
├── data/                  # Data Layer (Repository Implementations, Network, Storage)
│   ├── repositories/
│   │   ├── ChannelRepository.kt
│   │   ├── AuthRepository.kt
│   │   ├── AudioRepository.kt
│   ├── network/
│   │   ├── SignalingClient.kt          (WebSocket JSON messaging)
│   │   ├── ReconnectingSignalingClient.kt
│   │   ├── MediasoupDevice.kt          (libmediasoupclient wrapper)
│   │   ├── ConnectionManager.kt        (per-channel orchestration)
│   ├── audio/
│   │   ├── AudioCaptureManager.kt      (AudioRecord → WebRTC)
│   │   ├── AudioPlaybackManager.kt     (WebRTC → AudioTrack)
│   │   ├── AudioFocusManager.kt        (Android AudioFocus for PTT)
│   ├── storage/
│   │   ├── PreferencesDataStore.kt
│   │   ├── TokenManager.kt
│
└── service/               # Foreground Service (runs independently of Activity lifecycle)
    ├── PttService.kt
    ├── HardwareButtonReceiver.kt
    ├── ScanModeManager.kt
    └── NotificationBuilder.kt
```

### Key Components Detailed

#### 1. SignalingClient (data/network/)

**Responsibility:** WebSocket connection to `/ws`, JSON message send/receive

**API:**
```kotlin
interface ISignalingClient {
    suspend fun connect(token: String)
    suspend fun disconnect()
    suspend fun request(type: SignalingType, data: Map<String, Any>): SignalingMessage
    fun observeMessages(): Flow<SignalingMessage>
    val connectionState: StateFlow<ConnectionState>
}

class SignalingClient(
    private val serverUrl: String,
    private val scope: CoroutineScope
) : ISignalingClient {
    private val webSocket: OkHttp WebSocket
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<SignalingMessage>>()
    private val messageFlow = MutableSharedFlow<SignalingMessage>()

    override suspend fun request(type: SignalingType, data: Map<String, Any>): SignalingMessage {
        val id = UUID.randomUUID().toString()
        val message = SignalingMessage(type, id, data)
        val deferred = CompletableDeferred<SignalingMessage>()
        pendingRequests[id] = deferred
        webSocket.send(Json.encodeToString(message))
        return deferred.await()
    }
}
```

**Libraries:**
- OkHttp 4.x for WebSocket client (de facto standard on Android)
- kotlinx.serialization for JSON encoding/decoding
- Kotlin Coroutines Flow for reactive message stream

#### 2. MediasoupDevice (data/network/)

**Responsibility:** Wrapper around libmediasoupclient Device, creates Transports/Producers/Consumers

**Integration Path:**
- Use `io.github.crow-misia:libmediasoup-android` (HIGH confidence - most actively maintained)
- Alternative: `haiyangwu/mediasoup-client-android` (if crow-misia lacks features)

**API:**
```kotlin
class MediasoupDevice {
    private val nativeDevice: org.mediasoup.droid.Device

    suspend fun load(routerRtpCapabilities: String) {
        withContext(Dispatchers.IO) {
            nativeDevice.load(routerRtpCapabilities, null)
        }
    }

    fun createSendTransport(
        id: String,
        iceParameters: String,
        iceCandidates: String,
        dtlsParameters: String,
        listener: SendTransport.Listener
    ): SendTransport {
        return nativeDevice.createSendTransport(listener, id, iceParameters, iceCandidates, dtlsParameters)
    }

    // Similar for createRecvTransport
}
```

**Key Classes from libmediasoupclient:**
- `Device`: Main entry point, loads RTP capabilities
- `SendTransport`: Sends local media (mic) to server
- `RecvTransport`: Receives remote media (speakers) from server
- `Producer`: Outgoing audio track
- `Consumer`: Incoming audio track
- `Transport.Listener`: Callbacks for ICE/DTLS events (forward to server via SignalingClient)

**Initialization:**
```kotlin
// In ConnectionManager.init()
val device = MediasoupDevice()
val capabilitiesResponse = signalingClient.request(
    SignalingType.GET_ROUTER_CAPABILITIES,
    emptyMap()
)
device.load(capabilitiesResponse.data["routerRtpCapabilities"] as String)
```

#### 3. ConnectionManager (data/network/)

**Responsibility:** Orchestrates signaling + media for ONE channel

**Lifecycle:**
```
init() → loadDevice() → joinChannel() → createTransports() → [ready for PTT]
startPtt() → createProducer() → send audio
onSpeakerChanged() → createConsumer() / pauseConsumer() / resumeConsumer()
disconnect() → cleanup transports/producers/consumers
```

**State Management:**
```kotlin
class ConnectionManager(
    private val channelId: String,
    private val signalingClient: ISignalingClient,
    private val audioManager: IAudioManager,
    scope: CoroutineScope
) {
    private var device: MediasoupDevice? = null
    private var sendTransport: SendTransport? = null
    private var recvTransport: RecvTransport? = null
    private var producer: Producer? = null
    private val consumers = mutableMapOf<String, Consumer>() // producerId -> Consumer

    val channelState = MutableStateFlow<ChannelState>(ChannelState.Disconnected)
    val currentSpeaker = MutableStateFlow<String?>(null)

    suspend fun initialize() {
        device = MediasoupDevice()
        val caps = signalingClient.request(SignalingType.GET_ROUTER_CAPABILITIES, emptyMap())
        device?.load(caps.data["routerRtpCapabilities"] as String)

        joinChannel()
        createTransports()

        // Listen for SPEAKER_CHANGED broadcasts
        scope.launch {
            signalingClient.observeMessages()
                .filter { it.type == SignalingType.SPEAKER_CHANGED }
                .collect { handleSpeakerChanged(it) }
        }
    }

    suspend fun startPtt(): PttResult {
        val response = signalingClient.request(SignalingType.PTT_START, mapOf("channelId" to channelId))
        if (response.type == SignalingType.PTT_DENIED) {
            return PttResult.Denied
        }

        // Create producer for mic audio
        val audioTrack = audioManager.createAudioTrack()
        producer = sendTransport?.produce(
            Producer.Listener { /* handle transport events */ },
            audioTrack,
            null, null, null
        )

        // Notify server about producer
        signalingClient.request(SignalingType.PRODUCE, mapOf(
            "transportId" to sendTransport!!.id,
            "kind" to "audio",
            "rtpParameters" to producer!!.rtpParameters
        ))

        return PttResult.Granted(producer!!.id)
    }

    private suspend fun handleSpeakerChanged(msg: SignalingMessage) {
        val speakerUserId = msg.data["speakerUserId"] as String?
        val producerId = msg.data["producerId"] as String?

        if (producerId == null) {
            // Speaker stopped - pause all consumers
            consumers.values.forEach { it.pause() }
            currentSpeaker.value = null
        } else {
            // New speaker - resume their consumer, pause others
            consumers.values.forEach { it.pause() }
            val consumer = consumers[producerId] ?: createConsumer(producerId)
            consumer.resume()
            currentSpeaker.value = speakerUserId
        }
    }
}
```

#### 4. AudioCaptureManager (data/audio/)

**Responsibility:** Android AudioRecord → WebRTC AudioTrack

**Integration:**
WebRTC Android SDK handles this internally. When you create a `LocalAudioTrack` via `PeerConnectionFactory.createAudioTrack()`, it uses WebRTC's native `WebRtcAudioRecord` class which wraps Android's `AudioRecord`.

**Key Points:**
- WebRTC spawns background thread for audio capture
- Continuous buffer reading from mic hardware
- Automatic echo cancellation, noise suppression (WebRTC built-in)
- Opus encoding happens in native WebRTC layer

**Android-Specific Configuration:**
```kotlin
// Configure WebRTC audio options
val audioOptions = AudioOptions().apply {
    echoCancellationEnabled = true
    noiseSuppressEnabled = true
    autoGainControlEnabled = true
    highpassFilterEnabled = true
}

val peerConnectionFactory = PeerConnectionFactory.builder()
    .setAudioDeviceModule(createJavaAudioDevice(context))
    .setOptions(PeerConnectionFactory.Options().apply {
        audioOptions = audioOptions
    })
    .createPeerConnectionFactory()
```

#### 5. AudioPlaybackManager (data/audio/)

**Responsibility:** WebRTC Consumer → AudioTrack playback

**Multiple Consumer Mixing:**

WebRTC internally mixes multiple `AudioTrack` instances. On Android:
- Each Consumer provides a WebRTC `AudioTrack`
- WebRTC SDK routes to Android's `AudioTrack` (android.media.AudioTrack)
- Android audio system mixes multiple AudioTrack instances automatically
- No manual mixing required in app code

**Challenge for Scan Mode:** 5 simultaneous consumers (one per channel)

**Solution:**
```kotlin
class AudioPlaybackManager {
    private val activeConsumers = mutableMapOf<String, Consumer>()

    fun setActiveChannel(channelId: String) {
        // Pause all consumers except active channel
        activeConsumers.forEach { (id, consumer) ->
            if (id.startsWith(channelId)) {
                consumer.resume()
            } else {
                consumer.pause()
            }
        }
    }

    fun enableScanMode() {
        // Resume consumers for ALL monitored channels
        // Android will mix up to 5 simultaneous streams
        // Volume ducking may be needed for UX
        activeConsumers.values.forEach { it.resume() }
    }
}
```

**AudioFocus Management:**
```kotlin
class AudioFocusManager(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun requestPttFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
}
```

## Foreground Service Design

### Service Boundary & Lifecycle

**What Runs in Service:**
- All ConnectionManager instances (network + media)
- SignalingClient WebSocket connections
- mediasoup Device, Transports, Producers, Consumers
- Audio capture/playback threads (WebRTC managed)
- Scan mode state machine
- Hardware PTT button broadcast receiver

**What Runs in Activity/Fragment:**
- UI rendering (Jetpack Compose)
- ViewModels (observe Service state via Binder)
- User input handling (UI PTT button clicks)

**Why Foreground Service:**
1. Keep WebSocket connections alive when app backgrounded
2. Receive hardware PTT button intents in background
3. Maintain audio session for immediate PTT response
4. Android 8+ requires foreground service for persistent notifications

### Service Architecture

```kotlin
class PttService : Service() {
    private val binder = PttBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Multi-channel support
    private val connectionManagers = mutableMapOf<String, ConnectionManager>()
    private val scanModeManager = ScanModeManager()

    // Hardware button support
    private val hardwareButtonReceiver = HardwareButtonReceiver()

    // Service state exposed to UI
    private val _channelsState = MutableStateFlow<List<ChannelState>>(emptyList())
    val channelsState: StateFlow<List<ChannelState>> = _channelsState.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        // Register PTT button intent receiver
        val filter = IntentFilter().apply {
            addAction("android.intent.action.PTT.down")
            addAction("android.intent.action.PTT.up")
        }
        registerReceiver(hardwareButtonReceiver, filter)

        // Start foreground with notification
        val notification = NotificationBuilder.createPttNotification(
            context = this,
            channelCount = connectionManagers.size,
            isTransmitting = false
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent): IBinder = binder

    inner class PttBinder : Binder() {
        fun getService(): PttService = this@PttService
    }

    suspend fun joinChannel(channelId: String) {
        if (connectionManagers.containsKey(channelId)) return

        val signalingClient = ReconnectingSignalingClient(
            baseUrl = config.serverUrl,
            tokenProvider = { tokenManager.getToken() },
            scope = serviceScope
        )

        val connectionManager = ConnectionManager(
            channelId = channelId,
            signalingClient = signalingClient,
            audioManager = AudioCaptureManager(),
            scope = serviceScope
        )

        connectionManager.initialize()
        connectionManagers[channelId] = connectionManager

        updateNotification()
    }

    suspend fun startPtt(channelId: String? = null): PttResult {
        val targetChannel = channelId ?: scanModeManager.activeChannel
        val manager = connectionManagers[targetChannel] ?: return PttResult.NoChannel

        val result = manager.startPtt()
        if (result is PttResult.Granted) {
            updateNotification(isTransmitting = true)
        }
        return result
    }

    suspend fun stopPtt() {
        val activeChannel = connectionManagers.values.firstOrNull { it.producer != null }
        activeChannel?.stopPtt()
        updateNotification(isTransmitting = false)
    }

    override fun onDestroy() {
        unregisterReceiver(hardwareButtonReceiver)
        connectionManagers.values.forEach { it.disconnect() }
        serviceScope.cancel()
        super.onDestroy()
    }
}
```

### Foreground Service Type

**Android 14+ Requirement:**
```xml
<service
    android:name=".service.PttService"
    android:foregroundServiceType="microphone|phoneCall"
    android:enabled="true"
    android:exported="false" />
```

**Manifest Permissions:**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Notification Management

**Persistent Notification (Required for Foreground Service):**
```kotlin
object NotificationBuilder {
    fun createPttNotification(
        context: Context,
        channelCount: Int,
        isTransmitting: Boolean
    ): Notification {
        val channelId = createNotificationChannel(context)

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("VoicePing PTT")
            .setContentText(
                if (isTransmitting) "Transmitting..."
                else "Monitoring $channelCount channels"
            )
            .setSmallIcon(R.drawable.ic_ptt)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_disconnect,
                "Disconnect",
                createDisconnectPendingIntent(context)
            )
            .build()
    }
}
```

## State Management

### Scan Mode State Machine

**States:**
```kotlin
sealed class ScanModeState {
    object Off : ScanModeState()
    data class Monitoring(
        val primaryChannel: String,
        val monitoredChannels: Set<String>,
        val activeChannel: String? = null
    ) : ScanModeState()
    data class Active(
        val activeChannel: String,
        val autoSwitchEnabled: Boolean,
        val timeoutSeconds: Int
    ) : ScanModeState()
}
```

**Transitions:**
```
Off → Monitoring: User enables scan mode with primary channel
Monitoring → Active: Audio detected on non-primary channel
Active → Monitoring: Audio stops + timeout elapsed
Active → Active: Audio detected on different channel (switch)
Monitoring → Off: User disables scan mode
```

**State Machine Implementation:**
```kotlin
class ScanModeManager(
    private val connectionManagers: Map<String, ConnectionManager>,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<ScanModeState>(ScanModeState.Off)
    val state: StateFlow<ScanModeState> = _state.asStateFlow()

    private var timeoutJob: Job? = null

    init {
        // Monitor all channels for speaker changes
        scope.launch {
            connectionManagers.values.forEach { manager ->
                launch {
                    manager.currentSpeaker.collect { speakerId ->
                        handleSpeakerDetected(manager.channelId, speakerId)
                    }
                }
            }
        }
    }

    fun enableScanMode(primaryChannel: String, monitoredChannels: Set<String>) {
        _state.value = ScanModeState.Monitoring(
            primaryChannel = primaryChannel,
            monitoredChannels = monitoredChannels
        )

        // Resume consumers for all monitored channels
        monitoredChannels.forEach { channelId ->
            connectionManagers[channelId]?.resumeAllConsumers()
        }
    }

    private fun handleSpeakerDetected(channelId: String, speakerId: String?) {
        val currentState = _state.value

        when {
            speakerId == null -> {
                // Speaker stopped - return to monitoring after timeout
                if (currentState is ScanModeState.Active) {
                    startReturnToMonitoringTimeout()
                }
            }
            currentState is ScanModeState.Monitoring && channelId != currentState.primaryChannel -> {
                // Activity on non-primary channel - switch to active
                switchToActiveChannel(channelId)
            }
            currentState is ScanModeState.Active && channelId != currentState.activeChannel -> {
                // Activity on different channel - switch
                switchToActiveChannel(channelId)
            }
        }
    }

    private fun switchToActiveChannel(channelId: String) {
        timeoutJob?.cancel()

        _state.value = ScanModeState.Active(
            activeChannel = channelId,
            autoSwitchEnabled = true,
            timeoutSeconds = 5
        )

        // Pause consumers for non-active channels
        connectionManagers.forEach { (id, manager) ->
            if (id != channelId) {
                manager.pauseAllConsumers()
            }
        }
    }

    private fun startReturnToMonitoringTimeout() {
        val currentState = _state.value as? ScanModeState.Active ?: return

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(currentState.timeoutSeconds * 1000L)

            // Return to monitoring
            _state.value = ScanModeState.Monitoring(
                primaryChannel = (currentState as? ScanModeState.Monitoring)?.primaryChannel ?: "",
                monitoredChannels = connectionManagers.keys.toSet()
            )

            // Resume all consumers
            connectionManagers.values.forEach { it.resumeAllConsumers() }
        }
    }
}
```

### Channel Monitoring State

**Per-Channel State:**
```kotlin
data class ChannelState(
    val channelId: String,
    val channelName: String,
    val connectionState: ConnectionState,
    val currentSpeaker: User?,
    val members: List<User>,
    val pttState: PttState,
    val isMonitored: Boolean,
    val isPrimary: Boolean
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

enum class PttState {
    IDLE,           // No one transmitting
    REQUESTING,     // Waiting for PTT_START response
    TRANSMITTING,   // This user transmitting
    RECEIVING,      // Someone else transmitting
    DENIED          // PTT_START was denied
}
```

**ViewModel Pattern:**
```kotlin
class ScanModeViewModel(
    private val pttService: PttService
) : ViewModel() {

    val channelsState: StateFlow<List<ChannelState>> = pttService.channelsState
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scanModeState: StateFlow<ScanModeState> = pttService.scanModeManager.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanModeState.Off)

    fun enableScanMode(primaryChannelId: String) {
        viewModelScope.launch {
            pttService.scanModeManager.enableScanMode(
                primaryChannel = primaryChannelId,
                monitoredChannels = channelsState.value.map { it.channelId }.toSet()
            )
        }
    }

    fun startPtt() {
        viewModelScope.launch {
            when (val result = pttService.startPtt()) {
                is PttResult.Granted -> { /* Update UI */ }
                is PttResult.Denied -> { /* Show toast */ }
                is PttResult.NoChannel -> { /* Error */ }
            }
        }
    }
}
```

## Hardware Button Integration

### PTT Button Detection

**Zebra/Motorola Devices (Intent-Based):**
```kotlin
class HardwareButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.intent.action.PTT.down" -> {
                // Forward to service
                context.startService(
                    Intent(context, PttService::class.java).apply {
                        action = PttService.ACTION_PTT_START
                    }
                )
            }
            "android.intent.action.PTT.up" -> {
                context.startService(
                    Intent(context, PttService::class.java).apply {
                        action = PttService.ACTION_PTT_STOP
                    }
                )
            }
        }
    }
}
```

**Generic Android Devices (KeyEvent-Based):**

Activity must be in foreground to receive KeyEvents. For background support, use broadcast intents (vendor-specific).

**PTT Button KeyCodes:**
- `KEYCODE_HEADSETHOOK` (79)
- `KEYCODE_BUTTON_R2` (105) - Zebra headset PTT
- `KEYCODE_VOICE_ASSIST` (231) - Some devices

**Activity KeyEvent Handling:**
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
        pttViewModel.startPtt()
        return true
    }
    return super.onKeyDown(keyCode, event)
}

override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
        pttViewModel.stopPtt()
        return true
    }
    return super.onKeyUp(keyCode, event)
}
```

**Service Integration:**
```kotlin
// In PttService
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_PTT_START -> {
            serviceScope.launch {
                startPtt()
            }
        }
        ACTION_PTT_STOP -> {
            serviceScope.launch {
                stopPtt()
            }
        }
    }
    return START_STICKY
}
```

## Data Flow Diagrams

### PTT Transmission Flow (Single Channel)

```
User presses PTT button
  |
  v
Activity/HardwareButton → ViewModel.startPtt()
  |
  v
PttService.startPtt(channelId)
  |
  v
ConnectionManager.startPtt()
  |
  +---> AudioFocusManager.requestFocus() [Android audio system]
  |
  +---> SignalingClient.request(PTT_START) --[WebSocket]--> Server
  |       |
  |       v
  |     Server: ChannelStateManager.acquireSpeakerLock()
  |       |
  |       +---> SUCCESS --[WebSocket]--> Client receives PTT_START response
  |       |
  |       +---> BUSY --[WebSocket]--> Client receives PTT_DENIED
  |
  v
ConnectionManager: Create Producer
  |
  +---> PeerConnectionFactory.createAudioTrack() [WebRTC AudioRecord]
  |
  +---> SendTransport.produce(audioTrack) [libmediasoupclient]
  |       |
  |       v
  |     WebRTC captures mic audio → Opus encode → RTP packets
  |       |
  |       v
  |     Send via DTLS/SRTP over UDP to server
  |
  v
Server: Router receives RTP from Producer
  |
  v
Server: Route to all Consumers in channel (other clients)
  |
  v
Other clients: RecvTransport → Consumer → AudioTrack playback
```

### Multi-Channel Scan Mode Flow

```
User enables scan mode with 5 channels
  |
  v
ScanModeManager.enableScanMode(primary, [ch1, ch2, ch3, ch4, ch5])
  |
  +---> For each channel: ConnectionManager.joinChannel()
  |       |
  |       v
  |     SignalingClient.request(JOIN_CHANNEL) --[WebSocket]--> Server
  |       |
  |       v
  |     Server returns channel state (members, current speaker)
  |       |
  |       v
  |     ConnectionManager.createTransports() (send + recv)
  |       |
  |       v
  |     For each member: ConnectionManager.createConsumer(producerId)
  |
  v
ScanModeState = Monitoring(primary=ch1, monitored=[ch1-ch5])
  |
  +---> All 5 RecvTransports active
  |
  +---> All Consumers resumed (Android mixes up to 5 audio streams)
  |
  v
[WAIT FOR ACTIVITY]
  |
  +---> Server broadcasts SPEAKER_CHANGED(channelId=ch3, producerId=abc)
  |       |
  |       v
  |     ConnectionManager[ch3].handleSpeakerChanged()
  |       |
  |       v
  |     ScanModeManager detects activity on non-primary channel
  |       |
  |       v
  |     ScanModeState = Active(activeChannel=ch3, timeout=5s)
  |       |
  |       v
  |     Pause consumers for ch1, ch2, ch4, ch5
  |       |
  |       v
  |     Resume consumer for ch3 only
  |
  v
[SPEAKER STOPS]
  |
  v
Server broadcasts SPEAKER_CHANGED(channelId=ch3, producerId=null)
  |
  v
ScanModeManager starts timeout (5 seconds)
  |
  v
[TIMEOUT EXPIRES]
  |
  v
ScanModeState = Monitoring(primary=ch1, monitored=[ch1-ch5])
  |
  v
Resume all consumers for all channels
```

### Reconnection Flow

```
WebSocket connection lost (network change, server restart)
  |
  v
OkHttp WebSocket.onFailure() callback
  |
  v
ReconnectingSignalingClient detects disconnect
  |
  +---> Set connectionState = RECONNECTING
  |
  +---> Queue outgoing requests in memory
  |
  +---> Start exponential backoff (1s, 2s, 4s, 8s, 16s, 32s)
  |
  v
[ATTEMPT RECONNECT]
  |
  +---> TokenManager.getToken() (refresh if expired)
  |
  +---> WebSocket.connect(url, protocols=["voiceping", token])
  |       |
  |       v
  |     Server validates JWT
  |       |
  |       +---> SUCCESS: Connection established
  |       |
  |       +---> FAILURE (401): Token expired, fetch new token, retry
  |
  v
Connection restored
  |
  +---> For each channel: SignalingClient.request(JOIN_CHANNEL)
  |       |
  |       v
  |     Server assigns channel, returns current state
  |
  +---> For each producer: Recreate Consumer
  |       |
  |       v
  |     SignalingClient.request(CONSUME, producerId)
  |       |
  |       v
  |     RecvTransport.consume(consumerId, rtpParameters)
  |
  v
Flush queued requests
  |
  v
Set connectionState = CONNECTED
```

## Build Order (Implementation Sequence)

### Phase 1: Signaling Foundation (Week 1-2)

**Goal:** Establish WebSocket connection and message exchange

1. **Setup project structure**
   - Gradle dependencies: OkHttp, kotlinx-serialization, Coroutines
   - Package structure: data/domain/presentation/service
   - Protocol definitions: SignalingType enum, SignalingMessage data classes

2. **Implement SignalingClient**
   - OkHttp WebSocket connection
   - JSON message encoding/decoding
   - Request-response correlation (pending requests map)
   - Message flow (SharedFlow for broadcasts)

3. **Implement ReconnectingSignalingClient**
   - Exponential backoff logic
   - Message queue during disconnect
   - Token refresh on 401

4. **Test with existing server**
   - Manual JWT token (copy from web client)
   - JOIN_CHANNEL request/response
   - PING/PONG heartbeat
   - SPEAKER_CHANGED broadcast reception

**Deliverable:** Console app that connects to server, joins channel, logs all messages

### Phase 2: mediasoup Integration (Week 2-3)

**Goal:** WebRTC audio pipeline end-to-end

5. **Add libmediasoup-android dependency**
   - Gradle: `io.github.crow-misia:libmediasoup-android:latest`
   - Initialize in Application.onCreate()

6. **Implement MediasoupDevice wrapper**
   - Load router RTP capabilities
   - Create send/recv transports
   - Transport.Listener callbacks (ICE/DTLS events → signaling)

7. **Implement ConnectionManager**
   - Orchestrate signaling + media
   - JOIN_CHANNEL → CREATE_TRANSPORT → CONNECT_TRANSPORT
   - Handle SPEAKER_CHANGED → create/pause/resume consumers

8. **Test audio playback (receive only)**
   - Join channel as listener
   - Receive speaker's audio via consumer
   - Verify AudioTrack playback

**Deliverable:** App receives and plays audio from web client transmitting

### Phase 3: PTT Transmission (Week 3-4)

**Goal:** Full PTT cycle (press → transmit → release)

9. **Implement AudioCaptureManager**
   - Configure WebRTC audio options (echo cancellation, etc.)
   - PeerConnectionFactory setup

10. **Implement PTT flow in ConnectionManager**
    - PTT_START request → create producer
    - Producer.Listener callbacks
    - PTT_STOP → close producer

11. **Create basic UI (Jetpack Compose)**
    - Single channel view
    - PTT button (press/release)
    - Speaker indicator

12. **Test bidirectional PTT**
    - App → Server → Web client receives audio
    - Web client → Server → App receives audio

**Deliverable:** App can transmit and receive PTT audio in single channel

### Phase 4: Foreground Service (Week 4-5)

**Goal:** Background operation and hardware button support

13. **Create PttService**
    - Foreground service with notification
    - Service-bound architecture (Binder pattern)
    - Move ConnectionManager to service scope

14. **Implement HardwareButtonReceiver**
    - Register broadcast receiver for PTT intents
    - Forward to service

15. **Service-UI communication**
    - StateFlow for service state
    - ViewModel observes service via Binder

16. **Test background operation**
    - Lock screen, verify WebSocket stays alive
    - Hardware PTT button triggers transmission

**Deliverable:** App runs in background, responds to hardware button

### Phase 5: Multi-Channel Support (Week 5-6)

**Goal:** Multiple simultaneous connections

17. **Multi-ConnectionManager architecture**
    - Service manages Map<channelId, ConnectionManager>
    - Independent WebSocket per channel
    - Shared MediasoupDevice instance

18. **Channel list UI**
    - List of authorized channels from JWT
    - Join/leave channel actions
    - Per-channel connection state

19. **Audio mixing for multiple consumers**
    - Verify Android mixes automatically
    - Test with 2-3 simultaneous channels

**Deliverable:** App monitors 3 channels, receives audio from all

### Phase 6: Scan Mode (Week 6-7)

**Goal:** Priority-based multi-channel monitoring

20. **Implement ScanModeManager**
    - State machine (Off/Monitoring/Active)
    - Speaker detection across channels
    - Automatic channel switching

21. **Scan mode UI**
    - Primary channel selector
    - Monitored channels list
    - Active channel indicator
    - Timeout configuration

22. **Test scan mode logic**
    - Activity on non-primary channel triggers switch
    - Timeout returns to monitoring
    - Multiple rapid switches

**Deliverable:** App automatically switches to active channel

### Phase 7: Polish & Production Readiness (Week 7-8)

**Goal:** Production-quality app

23. **Error handling**
    - Network errors (show reconnecting state)
    - PTT denied (show toast)
    - Permission errors (request at runtime)

24. **Settings screen**
    - Server URL configuration
    - Audio settings (speaker volume, mic sensitivity)
    - Scan mode timeout
    - Notification preferences

25. **Performance optimization**
    - Battery usage profiling
    - Memory leak detection
    - WebSocket message throttling

26. **Integration testing**
    - Dispatcher workflow (5 channels)
    - Permission revocation handling
    - Emergency broadcast reception

**Deliverable:** Production-ready Android app

## Integration Considerations

### Server-Side No Changes Required

All server endpoints and signaling protocol remain unchanged:
- JWT authentication at `/api/router/token`
- WebSocket signaling at `/ws`
- mediasoup Router RTP capabilities
- Transport/Producer/Consumer lifecycle

### Shared Protocol Definitions

Maintain shared protocol definitions between server and client:
- Export `protocol.ts` as JSON schema
- Generate Kotlin data classes via code generation
- Or manually maintain Kotlin equivalents

**Example:**
```typescript
// Server: src/shared/protocol.ts
export enum SignalingType {
  JOIN_CHANNEL = 'join-channel',
  PTT_START = 'ptt-start',
  // ...
}
```

```kotlin
// Android: data/network/protocol/SignalingType.kt
@Serializable
enum class SignalingType(val value: String) {
    @SerialName("join-channel") JOIN_CHANNEL("join-channel"),
    @SerialName("ptt-start") PTT_START("ptt-start"),
    // ...
}
```

### Testing Strategy

**Unit Tests:**
- ScanModeManager state transitions
- ReconnectingSignalingClient backoff logic
- Message serialization/deserialization

**Integration Tests:**
- SignalingClient against real server
- ConnectionManager full join/ptt/leave cycle
- Multi-channel ConnectionManager coordination

**End-to-End Tests:**
- Android app ↔ Server ↔ Web client
- Hardware button → transmission → playback
- Scan mode switching across channels

## Key Architecture Patterns

### 1. Repository Pattern

Domain layer defines interfaces, data layer implements:
```kotlin
// domain/repositories/IChannelRepository.kt
interface IChannelRepository {
    suspend fun getChannels(): Result<List<Channel>>
    suspend fun joinChannel(channelId: String): Result<ChannelState>
}

// data/repositories/ChannelRepository.kt
class ChannelRepository(
    private val signalingClient: ISignalingClient
) : IChannelRepository {
    override suspend fun joinChannel(channelId: String): Result<ChannelState> {
        return try {
            val response = signalingClient.request(
                SignalingType.JOIN_CHANNEL,
                mapOf("channelId" to channelId)
            )
            Result.success(response.data.toChannelState())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 2. Use Case Pattern

Encapsulate business logic in single-responsibility use cases:
```kotlin
class StartPttUseCase(
    private val channelRepository: IChannelRepository,
    private val audioRepository: IAudioRepository
) {
    suspend operator fun invoke(channelId: String): PttResult {
        // Request audio focus
        if (!audioRepository.requestAudioFocus()) {
            return PttResult.AudioFocusDenied
        }

        // Request PTT from server
        return channelRepository.startPtt(channelId)
    }
}
```

### 3. State Management (MVI-inspired)

UI observes immutable state, dispatches actions:
```kotlin
@Composable
fun ChannelScreen(viewModel: ChannelViewModel) {
    val state by viewModel.state.collectAsState()

    ChannelContent(
        state = state,
        onPttPressed = { viewModel.handlePttPressed() },
        onPttReleased = { viewModel.handlePttReleased() }
    )
}
```

### 4. Dependency Injection

Use Hilt/Koin for service location:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideSignalingClient(
        @ApplicationContext context: Context,
        tokenManager: TokenManager
    ): ISignalingClient {
        return ReconnectingSignalingClient(
            baseUrl = BuildConfig.SERVER_URL,
            tokenProvider = { tokenManager.getToken() },
            scope = CoroutineScope(SupervisorJob())
        )
    }
}
```

## Sources

### High Confidence (Verified with Official Sources)

- [libmediasoup-android GitHub](https://github.com/crow-misia/libmediasoup-android) - PRIMARY mediasoup Android wrapper
- [haiyangwu/mediasoup-client-android](https://github.com/haiyangwu/mediasoup-client-android) - Alternative mediasoup Android implementation
- [mediasoup Official Documentation](https://mediasoup.org/documentation/v3/mediasoup-client/api/) - Client API reference
- [Android Developers: Domain Layer](https://developer.android.com/topic/architecture/domain-layer) - Clean Architecture official guidance
- [Android WebRTC Native Code](https://webrtc.github.io/webrtc-org/native-code/android/) - Official WebRTC Android guide

### Medium Confidence (Multiple Community Sources)

- [Android WebRTC Audio Processing](https://github.com/mail2chromium/Android-Audio-Processing-Using-WebRTC) - WebRtcAudioRecord implementation details
- [Android PTT with Foreground Service](https://www.mirrorfly.com/blog/push-to-talk-sdk-for-android-ios-app/) - PTT foreground service patterns
- [Zebra EMDK PTT Documentation](https://techdocs.zebra.com/emdk-for-android/7-6/samples/usingptt/) - Hardware button integration
- [Android Hardware Button Events](https://devtut.github.io/android/hardware-button-events-intents-ptt-lwp-etc.html) - PTT KeyEvent/Intent patterns
- [Android WebSocket in Foreground Service](https://medium.com/@yoozeey/websockets-livedata-workmanager-4fa1f1edda6f) - WebSocket background service architecture
- [WebRTC Multiple PeerConnection](https://webrtc.github.io/samples/src/content/peerconnection/multiple/) - Multi-connection patterns
- [mediasoup Multi-Consumer](https://mediasoup.discourse.group/t/using-multiple-consumers-in-a-single-recvtransport/375) - Transport architecture

### Low Confidence (Needs Phase-Specific Verification)

- Android AudioTrack mixing behavior with 5 simultaneous streams (NEEDS TESTING)
- Battery consumption with 5 concurrent WebSocket connections (NEEDS PROFILING)
- Hardware PTT button KeyCodes on non-Zebra devices (VENDOR-SPECIFIC)
- WebRTC audio quality with Android echo cancellation in noisy environments (NEEDS FIELD TESTING)
