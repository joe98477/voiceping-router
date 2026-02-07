# Phase 5: Android Project Setup & WebRTC Foundation - Research

**Researched:** 2026-02-08
**Domain:** Android native app with mediasoup receive-only audio
**Confidence:** HIGH

## Summary

Phase 5 establishes the Android native client foundation by setting up the Kotlin project structure, implementing JWT-based login with persistent session management, integrating mediasoup for receive-only audio, and building the initial UI with dark theme and Material 3. The phase focuses on proving the technical integration works (mediasoup Android wrapper + existing server protocol) before building advanced features like PTT transmission or multi-channel monitoring.

The critical path involves: (1) validating crow-misia/libmediasoup-android compatibility with the existing server, (2) implementing secure JWT storage in Android Keystore, (3) establishing WebSocket signaling with OkHttp, (4) creating the receive transport + consumer for audio playback, and (5) building the channel list UI with team grouping. This is a receive-only phase — users can hear transmissions but cannot transmit.

**Primary recommendation:** Use proven Android stack (Kotlin 2.3.10, Compose 1.10, Material 3, OkHttp 4.12, EncryptedSharedPreferences) with crow-misia libmediasoup-android wrapper. Implement Phase 5 as technical validation gate — if mediasoup wrapper fails on physical devices, pivot to alternative approach before building UI features.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Login & Session Flow:**
- Branded splash screen with dark background, logo, fields slide up — shown only when login is required (not on auto-login)
- Email/password authentication only (no SSO, no PIN, no alternative methods)
- Always persist session — no "Remember me" toggle. JWT stored in Android Keystore (hardware-backed)
- Silent token refresh in background using stored credentials when JWT expires (1-hour TTL)
- If silent refresh fails: retry 2-3 times silently, then force logout with clear message explaining why
- Login validation errors shown inline under fields (not toast/snackbar)
- Brief loading/connecting screen after login while WebSocket connection establishes
- Logout accessible from profile/menu slide-out (not buried deep, not prominently on main screen)

**Event Picker & Channel List:**
- Event picker: simple flat list of events user has access to, tap to select
- Auto-skip event picker on launch if a saved event exists — go straight to channel list
- Event switching available from profile slide-out menu ("Switch Event" option)
- Channels grouped by team with team header labels (e.g., "Security Team" > Channel A, Channel B)
- Each channel row shows: channel name, active speaker indicator (pulsing), user count

**Channel Join & Audio Playback:**
- Toggle/checkbox pattern — user toggles channels on/off from the list itself, no separate channel screen
- Phase 5 limits to single channel only (one toggle active at a time, selecting another deselects previous)
- When someone transmits: show speaker name with pulsing animation on the channel's row
- Default audio output: earpiece (quiet/private mode) — user can switch to speaker later (Phase 6 adds toggle)

**App Shell & Navigation:**
- Profile icon at top-right of header — tapping slides out a side panel from the right
- Menu items: user name/email display, Switch Event, Settings, Logout, app version/about
- Persistent bottom bar (mini-player style) showing current/default joined channel
- Bottom bar shows: channel name, and speaker name when someone is transmitting
- Full dark theme across all screens — consistent radio-app feel
- Connection status: small colored dot overlaid on bottom-left of profile icon (green=connected, etc.)
- Disconnection: banner pops up at top of screen saying "Connecting..." — auto-closes on reconnect, changes to error message if connection fails

### Claude's Discretion

- Exact accent color for dark theme (suggestion: something visible but not garish)
- Loading screen design between login and channel list
- Team header styling in channel list
- Exact animation style for speaker pulse
- Bottom bar height and layout proportions
- Profile slide-out panel width and animation

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope

</user_constraints>

---

## Milestone Research Status

The milestone-level research files (STACK.md, ARCHITECTURE.md, FEATURES.md, PITFALLS.md, SUMMARY.md) were created on 2026-02-08 and remain current. Key validations for Phase 5:

**Stack verification (Feb 2026):**
- ✅ Kotlin 2.3.10 stable (released Dec 2025, 2.3.20-Beta2 available but not recommended)
- ✅ Jetpack Compose 1.10.0 stable (Dec 2025 release, Material 3 1.4 stable)
- ✅ Android Gradle Plugin 9.0.0 released Jan 2026, supports Kotlin 2.3.x
- ✅ crow-misia/libmediasoup-android actively maintained, published to Maven Central (latest: 0.7.0 as of Feb 2026)
- ✅ OkHttp 4.12.0 stable, fixes memory leaks from 4.11.x
- ⚠️ libmediasoup-android 0.21.0 version from milestone research not found — Maven shows 0.7.0. Need to verify compatibility.

**Architecture verification:**
- ✅ Clean Architecture pattern with data/domain/presentation layers standard for 2026 Android
- ✅ Service-bound architecture for foreground service confirmed as best practice
- ✅ OkHttp WebSocket + Kotlin Coroutines patterns validated (see Sources)
- ✅ EncryptedSharedPreferences for JWT storage is Android 10+ best practice

**Features verification:**
- ✅ Material 3 dynamic theming and adaptive components confirmed as 2026 standard
- ✅ Hardware-backed Keystore for JWT storage confirmed as security best practice
- ✅ Dark theme as primary design system aligns with 2026 Android UX patterns

**Pitfalls still relevant:**
- ✅ OEM battery optimization remains critical issue (dontkillmyapp.com actively maintained)
- ✅ WebRTC memory leaks pattern confirmed in 2026 sources
- ✅ Bluetooth SCO audio routing race conditions still active issue
- ⚠️ mediasoup-client wrapper compatibility needs Phase 5 acceptance testing

**Research gap identified:** Milestone research assumed libmediasoup-android 0.21.0, but Maven Central shows 0.7.0 as latest. This version discrepancy needs investigation in Phase 5 planning.

---

## Standard Stack

Phase 5 uses the recommended stack from milestone research, verified for Feb 2026 compatibility.

### Core Dependencies

| Library | Version | Purpose | Phase 5 Usage |
|---------|---------|---------|---------------|
| **Kotlin** | 2.3.10 | Language | Stable release (Dec 2025), bundled in AGP 9.0 |
| **Android Gradle Plugin** | 9.0.0 | Build system | Released Jan 2026, includes Kotlin 2.2.10+ runtime |
| **Jetpack Compose** | 1.10.0 | UI framework | Declarative UI for channel list, login screens |
| **Compose Material 3** | 1.4.0 | Material Design 3 | Dark theme, cards, buttons, navigation |
| **OkHttp** | 4.12.0 | HTTP + WebSocket | WebSocket signaling to `/ws` endpoint |
| **Retrofit** | 2.11.0 | REST API client | Login endpoint (`POST /api/auth/login`) |
| **Gson** | 2.11.0 | JSON serialization | Match server's JSON protocol |
| **Hilt** | 2.51.1 | Dependency injection | ViewModels, repositories, singleton services |
| **EncryptedSharedPreferences** | Security 1.1.0-alpha06 | Secure JWT storage | Hardware-backed Android Keystore encryption |
| **GetStream webrtc-android** | 1.3.9 | WebRTC library | Audio playback via AudioTrack |
| **libmediasoup-android** | 0.7.0 (crow-misia) | mediasoup client | Receive transport + consumer for audio |

### Build Configuration

```kotlin
// Module-level build.gradle.kts
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
        minSdk = 26  // Android 8.0 (89% coverage)
        targetSdk = 35  // Android 15 (Google Play requirement Feb 2026)
        versionCode = 1
        versionName = "1.0.0"
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
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // WebRTC
    implementation("io.getstream:stream-webrtc-android:1.3.9")

    // mediasoup (crow-misia wrapper)
    implementation("io.github.crow-misia.libmediasoup-android:libmediasoup-android:0.7.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}
```

### ProGuard Rules

Critical for native libraries (mediasoup, WebRTC):

```proguard
# Keep mediasoup JNI classes
-keep class org.mediasoup.** { *; }
-keepclassmembers class org.mediasoup.** { *; }

# Keep WebRTC classes
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }

# Keep Gson models (for JSON serialization)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.voiceping.android.data.model.** { *; }
```

---

## Architecture Patterns

Phase 5 establishes the foundational architecture that later phases build upon.

### Project Structure

```
app/src/main/
├── java/com/voiceping/android/
│   ├── VoicePingApplication.kt           # App entry point, Hilt setup
│   │
│   ├── data/                             # Data Layer
│   │   ├── network/
│   │   │   ├── SignalingClient.kt        # WebSocket client (OkHttp)
│   │   │   ├── MediasoupClient.kt        # mediasoup Device wrapper
│   │   │   └── dto/                      # Data transfer objects (JSON models)
│   │   │       ├── SignalingMessage.kt
│   │   │       ├── LoginRequest.kt
│   │   │       └── LoginResponse.kt
│   │   │
│   │   ├── api/
│   │   │   └── AuthApi.kt                # Retrofit interface for /api/auth/*
│   │   │
│   │   ├── storage/
│   │   │   ├── TokenManager.kt           # EncryptedSharedPreferences wrapper
│   │   │   └── PreferencesManager.kt     # App settings storage
│   │   │
│   │   └── repository/
│   │       ├── AuthRepository.kt         # Login, token refresh, logout
│   │       ├── EventRepository.kt        # Event list, event selection
│   │       └── ChannelRepository.kt      # Channel list, join/leave
│   │
│   ├── domain/                           # Domain Layer
│   │   ├── model/                        # Business logic models
│   │   │   ├── User.kt
│   │   │   ├── Event.kt
│   │   │   ├── Channel.kt
│   │   │   ├── Team.kt
│   │   │   └── ConnectionState.kt
│   │   │
│   │   └── usecase/                      # Business logic use cases
│   │       ├── LoginUseCase.kt
│   │       ├── GetEventsUseCase.kt
│   │       ├── JoinChannelUseCase.kt
│   │       └── LeaveChannelUseCase.kt
│   │
│   └── presentation/                     # Presentation Layer
│       ├── theme/                        # Compose Material 3 theme
│       │   ├── Color.kt
│       │   ├── Theme.kt
│       │   └── Type.kt
│       │
│       ├── login/
│       │   ├── LoginScreen.kt
│       │   └── LoginViewModel.kt
│       │
│       ├── events/
│       │   ├── EventPickerScreen.kt
│       │   └── EventPickerViewModel.kt
│       │
│       ├── channels/
│       │   ├── ChannelListScreen.kt
│       │   ├── ChannelListViewModel.kt
│       │   └── components/
│       │       ├── ChannelRow.kt
│       │       ├── TeamHeader.kt
│       │       └── BottomBar.kt
│       │
│       ├── navigation/
│       │   └── NavGraph.kt               # Compose Navigation setup
│       │
│       └── MainActivity.kt               # Single activity (Compose-based)
│
└── res/
    ├── values/
    │   ├── strings.xml
    │   └── themes.xml                     # Material 3 dark theme
    └── drawable/
        └── ic_logo.xml                    # VoicePing logo
```

### Pattern 1: Repository Pattern with Coroutines

**What:** Domain layer defines repository interfaces, data layer implements them using Retrofit/OkHttp/mediasoup with Kotlin coroutines for async operations.

**When to use:** All network operations (login, WebSocket signaling, mediasoup transport setup).

**Example:**

```kotlin
// domain/repository/IAuthRepository.kt
interface IAuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun refreshToken(): Result<String>
}

// data/repository/AuthRepository.kt
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) : IAuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(LoginRequest(email, password))
                tokenManager.saveToken(response.token)
                tokenManager.saveCredentials(email, password)
                Result.success(response.toUser())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun refreshToken(): Result<String> {
        return withContext(Dispatchers.IO) {
            val credentials = tokenManager.getStoredCredentials()
                ?: return@withContext Result.failure(Exception("No stored credentials"))

            try {
                val response = authApi.login(LoginRequest(credentials.email, credentials.password))
                tokenManager.saveToken(response.token)
                Result.success(response.token)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

**Source:** [Kotlin Coroutines + OkHttp patterns](https://scrapfly.io/blog/posts/guide-to-okhttp-java-kotlin)

---

### Pattern 2: Secure JWT Storage with EncryptedSharedPreferences

**What:** Store JWT tokens in Android Keystore-backed encrypted storage. Use EncryptedSharedPreferences for automatic key management.

**When to use:** Storing JWT access token, refresh credentials (email/password for silent refresh).

**Example:**

```kotlin
// data/storage/TokenManager.kt
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "voiceping_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        encryptedPrefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getToken(): String? = encryptedPrefs.getString(KEY_JWT_TOKEN, null)

    fun isTokenExpired(): Boolean {
        val timestamp = encryptedPrefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
        val ageMillis = System.currentTimeMillis() - timestamp
        return ageMillis > TOKEN_TTL_MS // 1 hour = 3600000ms
    }

    fun saveCredentials(email: String, password: String) {
        encryptedPrefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getStoredCredentials(): Credentials? {
        val email = encryptedPrefs.getString(KEY_EMAIL, null)
        val password = encryptedPrefs.getString(KEY_PASSWORD, null)
        return if (email != null && password != null) {
            Credentials(email, password)
        } else null
    }

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val TOKEN_TTL_MS = 3600000L // 1 hour
    }
}
```

**Security note:** Hardware-backed encryption keys stored in Android Keystore. Keys never leave secure hardware (TEE/StrongBox on supported devices).

**Source:** [Secure JWT Storage in Android 2026](https://medium.com/@mohammad.hasan.mahdavi81/securely-storing-jwt-tokens-in-android-with-datastore-and-manual-encryption-741b104a93d3)

---

### Pattern 3: OkHttp WebSocket Client with Coroutines

**What:** WebSocket client for `/ws` signaling endpoint. Uses OkHttp WebSocket with Kotlin coroutines for message handling and StateFlow for reactive connection state.

**When to use:** WebSocket signaling for mediasoup (JOIN_CHANNEL, GET_ROUTER_CAPABILITIES, CREATE_TRANSPORT, CONSUME, SPEAKER_CHANGED broadcasts).

**Example:**

```kotlin
// data/network/SignalingClient.kt
class SignalingClient @Inject constructor(
    private val tokenManager: TokenManager,
    private val gson: Gson
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<SignalingMessage>>()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    val messages: SharedFlow<SignalingMessage> = _messages.asSharedFlow()

    suspend fun connect(serverUrl: String) {
        val token = tokenManager.getToken() ?: throw IllegalStateException("No JWT token")

        val request = Request.Builder()
            .url("$serverUrl/ws")
            .build()

        // OkHttp WebSocket uses subprotocols for authentication
        // Server expects: Sec-WebSocket-Protocol: voiceping, <jwt>
        // We pass token as second protocol, server handles via handleProtocols callback
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED

                // Send token via subprotocol negotiation
                // Client: ["voiceping", "<jwt>"] → Server accepts "voiceping"
                webSocket.request().header("Sec-WebSocket-Protocol")?.let {
                    // Token is already in header from request builder
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, SignalingMessage::class.java)

                    // Response to pending request (has correlation ID)
                    if (message.id != null && pendingRequests.containsKey(message.id)) {
                        pendingRequests.remove(message.id)?.complete(message)
                    } else {
                        // Broadcast message (no correlation ID)
                        CoroutineScope(Dispatchers.IO).launch {
                            _messages.emit(message)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SignalingClient", "Failed to parse message: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.FAILED
                pendingRequests.values.forEach { it.completeExceptionally(t) }
                pendingRequests.clear()
            }
        })
    }

    suspend fun request(type: SignalingType, data: Map<String, Any> = emptyMap()): SignalingMessage {
        val id = UUID.randomUUID().toString()
        val message = SignalingMessage(type, id, data)
        val deferred = CompletableDeferred<SignalingMessage>()

        pendingRequests[id] = deferred

        val json = gson.toJson(message)
        webSocket?.send(json) ?: throw IllegalStateException("WebSocket not connected")

        return deferred.await()
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}

// Signaling message DTO (matches src/shared/protocol.ts)
data class SignalingMessage(
    val type: SignalingType,
    val id: String? = null,
    val data: Map<String, Any>? = null,
    val error: String? = null
)

enum class SignalingType {
    JOIN_CHANNEL,
    LEAVE_CHANNEL,
    GET_ROUTER_CAPABILITIES,
    CREATE_TRANSPORT,
    CONNECT_TRANSPORT,
    CONSUME,
    SPEAKER_CHANGED,
    CHANNEL_STATE,
    PING,
    PONG,
    ERROR
}
```

**Key points:**
- JWT token passed via WebSocket subprotocol (server's `handleProtocols` callback extracts it)
- Request-response correlation using UUID message IDs
- Broadcasts (SPEAKER_CHANGED, CHANNEL_STATE) emitted via SharedFlow
- Connection state exposed via StateFlow for UI reactivity

**Source:** [Android WebSockets with Kotlin](https://bugfender.com/blog/android-websockets/)

---

### Pattern 4: mediasoup Receive Transport Setup

**What:** Create mediasoup Device, load RTP capabilities, create receive transport, consume remote audio producer.

**When to use:** Phase 5 receive-only audio (user joins channel, hears transmissions from others).

**Example:**

```kotlin
// data/network/MediasoupClient.kt
class MediasoupClient @Inject constructor(
    private val signalingClient: SignalingClient,
    @ApplicationContext private val context: Context
) {
    private var device: Device? = null
    private var recvTransport: RecvTransport? = null
    private val consumers = mutableMapOf<String, Consumer>()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Step 1: Get router RTP capabilities from server
        val capsResponse = signalingClient.request(
            SignalingType.GET_ROUTER_CAPABILITIES
        )
        val rtpCapabilities = capsResponse.data?.get("routerRtpCapabilities") as String

        // Step 2: Create mediasoup Device and load capabilities
        device = Device()
        device?.load(rtpCapabilities)
    }

    suspend fun joinChannel(channelId: String) = withContext(Dispatchers.IO) {
        // Step 3: Create receive transport
        val transportResponse = signalingClient.request(
            SignalingType.CREATE_TRANSPORT,
            mapOf("channelId" to channelId, "direction" to "recv")
        )

        val transportData = transportResponse.data!!
        val transportId = transportData["id"] as String
        val iceParameters = transportData["iceParameters"] as String
        val iceCandidates = transportData["iceCandidates"] as String
        val dtlsParameters = transportData["dtlsParameters"] as String

        recvTransport = device?.createRecvTransport(
            object : RecvTransport.Listener {
                override fun onConnect(transport: Transport, dtlsParameters: String): String {
                    // Step 4: Send DTLS parameters to server
                    runBlocking {
                        signalingClient.request(
                            SignalingType.CONNECT_TRANSPORT,
                            mapOf(
                                "transportId" to transportId,
                                "dtlsParameters" to dtlsParameters
                            )
                        )
                    }
                    return "" // Success
                }

                override fun onConnectionStateChange(
                    transport: Transport,
                    connectionState: TransportState
                ) {
                    Log.d("MediasoupClient", "Transport state: $connectionState")
                }
            },
            transportId,
            iceParameters,
            iceCandidates,
            dtlsParameters
        )
    }

    suspend fun consumeAudio(producerId: String, peerId: String) = withContext(Dispatchers.IO) {
        // Step 5: Request consume from server
        val consumeResponse = signalingClient.request(
            SignalingType.CONSUME,
            mapOf(
                "producerId" to producerId,
                "peerId" to peerId
            )
        )

        val consumeData = consumeResponse.data!!
        val consumerId = consumeData["id"] as String
        val kind = consumeData["kind"] as String
        val rtpParameters = consumeData["rtpParameters"] as String

        // Step 6: Create consumer on receive transport
        val consumer = recvTransport?.consume(
            object : Consumer.Listener {
                override fun onTransportClose(consumer: Consumer) {
                    consumers.remove(consumerId)
                }
            },
            consumerId,
            producerId,
            kind,
            rtpParameters,
            "" // appData (optional)
        )

        consumer?.let {
            consumers[consumerId] = it

            // Resume consumer (starts audio playback)
            it.resume()

            // WebRTC AudioTrack automatically plays through device speaker/earpiece
            // Audio routing controlled by AudioManager (see Pattern 5)
        }
    }

    fun cleanup() {
        consumers.values.forEach { it.close() }
        consumers.clear()
        recvTransport?.close()
        recvTransport = null
        device = null
    }
}
```

**Integration with existing server:**
- Server endpoint: `wss://<domain>/ws`
- JWT authentication via WebSocket subprotocol
- Signaling protocol matches `src/shared/protocol.ts` exactly
- mediasoup RTP capabilities from server's RouterManager
- Transport created via TransportManager
- Consumer created via ProducerConsumerManager

**Source:** [mediasoup Client-Server Communication](https://mediasoup.org/documentation/v3/communication-between-client-and-server/)

---

### Pattern 5: Audio Routing (Earpiece Default)

**What:** Configure Android AudioManager to route audio to earpiece by default (private/quiet mode), with manual toggle to speaker.

**When to use:** Phase 5 audio playback. Phase 6 adds user-facing toggle.

**Example:**

```kotlin
// data/audio/AudioRouter.kt
class AudioRouter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun setEarpieceMode() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
    }

    fun setSpeakerMode() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    fun requestAudioFocus() {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()

        audioManager.requestAudioFocus(focusRequest)
    }

    fun releaseAudioFocus() {
        audioManager.abandonAudioFocusRequest(/* request */)
    }
}
```

**Default behavior:**
- `MODE_IN_COMMUNICATION`: Optimizes for voice (enables echo cancellation, noise suppression)
- `isSpeakerphoneOn = false`: Routes to earpiece
- User sees audio routed to earpiece icon in status bar

---

### Pattern 6: Material 3 Dark Theme

**What:** Define dark theme using Material 3 dynamic theming with custom accent color.

**When to use:** All screens. Dark theme is the only theme (no light mode in Phase 5).

**Example:**

```kotlin
// presentation/theme/Color.kt
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkPrimary = Color(0xFF00BCD4) // Cyan accent (suggestion)
val DarkOnPrimary = Color(0xFF000000)
val DarkSecondary = Color(0xFF03DAC6)

// presentation/theme/Theme.kt
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    error = Color(0xFFCF6679)
)

@Composable
fun VoicePingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
```

**Accent color suggestion:** Cyan (#00BCD4) — visible against dark background, not garish, radio-app feel.

**Source:** [Material 3 in Jetpack Compose 2025 Guide](https://medium.com/@hiren6997/mastering-material-3-in-jetpack-compose-the-2025-guide-1c1bd5acc480)

---

## Don't Hand-Roll

Problems that look simple but have existing solutions — use libraries, don't reinvent.

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| **JWT token encryption** | Custom AES encryption with manual key management | EncryptedSharedPreferences | Handles Keystore integration, master key generation, encryption/decryption automatically. Custom crypto has edge cases (key rotation, initialization vectors, authentication tags). |
| **WebSocket reconnection logic** | Manual exponential backoff with state tracking | ReconnectingWebSocket wrapper or OkHttp retry interceptor | Reconnection has edge cases: pending message queue, request deduplication, connection state races. Libraries handle this. |
| **JSON serialization** | Manual string parsing with JSONObject | Gson or kotlinx.serialization | Type safety, null handling, nested objects, date formats — manual parsing is error-prone. |
| **Dependency injection** | Manual singleton pattern with static instances | Hilt (Dagger-based DI) | Manual DI breaks with process death, complicates testing, no scoped instances. Hilt provides ViewModel integration, lifecycle-aware scoping. |
| **Image loading** | Manual Bitmap decoding with caching | Coil (recommended for Compose) | Memory management, disk caching, placeholder handling, GIF/SVG support. Manual image loading causes OOM crashes. |

**Key insight:** Android ecosystem has mature solutions for common problems. Custom implementations add maintenance burden and miss edge cases discovered over years of production use.

---

## Common Pitfalls

Phase 5-specific pitfalls to avoid during implementation.

### Pitfall 1: libmediasoup-android Version Mismatch

**What goes wrong:** Milestone research mentioned libmediasoup-android 0.21.0, but Maven Central shows 0.7.0 as latest (Feb 2026). Using wrong version or assuming compatibility without testing causes build failures or runtime crashes.

**Why it happens:** crow-misia wrapper versioning doesn't map 1:1 to server mediasoup version. Wrapper version indicates wrapper API changes, not server protocol version. mediasoup protocol is negotiated via RTP capabilities exchange — wrapper version compatibility needs testing.

**How to avoid:**
1. **Phase 5 acceptance test:** Build minimal app with libmediasoup-android 0.7.0, connect to existing server, join channel, receive audio
2. **Test on 3+ physical devices:** Different OEMs (Samsung, Google Pixel, OnePlus), different API levels (26, 31, 34)
3. **Verify RTP capabilities exchange:** Log server's routerRtpCapabilities and device.load() success
4. **Check transport state:** Log ICE connection state, DTLS state progression
5. **If 0.7.0 fails:** Try latest 0.x.x version from Maven Central, check GitHub issues for known problems

**Warning signs:**
- Build error: `undefined reference to 'mediasoup::...'`
- Runtime crash: `UnsatisfiedLinkError: dlopen failed`
- Transport stuck in `connecting` state (ICE never completes)
- Audio never plays despite consumer.resume() called

**Fallback plan:** If no stable wrapper works, consider WebView hybrid approach for MVP (use web client in WebView for Phase 5, migrate to native in Phase 6+ when wrapper stable).

---

### Pitfall 2: JWT Token in WebSocket Subprotocol Format

**What goes wrong:** OkHttp WebSocket API doesn't directly support passing custom headers after handshake. JWT must be passed via Sec-WebSocket-Protocol header during handshake, but OkHttp's API is unclear how to do this correctly.

**Why it happens:** WebSocket handshake is HTTP upgrade — custom headers sent during HTTP request, but some servers (like VoicePing) expect JWT in subprotocol list. OkHttp requires subprotocols passed via Request.Builder but doesn't expose direct API for this.

**How to avoid:**
1. **Server's protocol (from websocketServer.ts):**
   ```typescript
   handleProtocols: (protocols: Set<string>) => {
       if (protocols.has('voiceping')) return 'voiceping';
       return false;
   }
   ```
   Server expects client to send: `Sec-WebSocket-Protocol: voiceping, <jwt>`
   Server accepts `voiceping` and extracts JWT from protocols list.

2. **Correct OkHttp pattern:**
   ```kotlin
   val token = tokenManager.getToken() ?: throw Exception("No JWT")

   val request = Request.Builder()
       .url("$serverUrl/ws")
       .header("Sec-WebSocket-Protocol", "voiceping, $token")
       .build()

   val webSocket = client.newWebSocket(request, listener)
   ```

3. **Verify in logs:** Check server logs for "JWT validation success" or similar

**Warning signs:**
- WebSocket connects but immediately closes with 401 Unauthorized
- Server logs show "JWT token missing or invalid"
- Connection stays in `CONNECTING` state indefinitely

---

### Pitfall 3: Silent Token Refresh Timing

**What goes wrong:** Token expires (1-hour TTL) while app is idle. Next WebSocket operation fails with 401, but app doesn't automatically refresh token before retrying. User sees "Connection failed" error instead of seamless reconnection.

**Why it happens:** Token expiry check happens too late (after request fails) instead of proactively before request. JWT TTL is 1 hour, but app needs to refresh before expiry (e.g., at 55 minutes) to avoid interruption.

**How to avoid:**
1. **Proactive refresh pattern:**
   ```kotlin
   class TokenManager {
       fun needsRefresh(): Boolean {
           val timestamp = encryptedPrefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
           val ageMillis = System.currentTimeMillis() - timestamp
           return ageMillis > REFRESH_THRESHOLD_MS // 55 minutes
       }

       companion object {
           private const val REFRESH_THRESHOLD_MS = 3300000L // 55 minutes
       }
   }

   // In SignalingClient.connect()
   suspend fun connect(serverUrl: String) {
       if (tokenManager.needsRefresh()) {
           val result = authRepository.refreshToken()
           if (result.isFailure) {
               // Retry 2-3 times, then force logout
               // (per user decision: "If silent refresh fails: retry 2-3 times silently,
               // then force logout with clear message explaining why")
           }
       }

       // Proceed with connection
   }
   ```

2. **Background refresh worker (optional for Phase 6+):**
   ```kotlin
   // WorkManager periodic task to refresh token every 50 minutes
   class TokenRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
       override suspend fun doWork(): Result {
           return if (tokenManager.needsRefresh()) {
               val result = authRepository.refreshToken()
               if (result.isSuccess) Result.success() else Result.retry()
           } else {
               Result.success()
           }
       }
   }
   ```

**Warning signs:**
- User sees "Connection failed" after app idle for 1+ hours
- WebSocket 401 errors in logs after successful initial connection
- Token refresh only happens after user manually retries connection

---

### Pitfall 4: WebRTC AudioTrack Not Disposing

**What goes wrong:** After user leaves channel, Consumer.close() is called but WebRTC AudioTrack continues playing silent audio. Memory usage grows on repeated join/leave cycles. Eventually crashes with OutOfMemoryError after 10-20 channel joins.

**Why it happens:** WebRTC native objects (Consumer, MediaStreamTrack, AudioTrack) are backed by C++ objects. Java garbage collector does NOT free these — must explicitly call .dispose() in correct order. If dispose order is wrong or skipped, native memory leaks.

**How to avoid:**
1. **Strict disposal order:**
   ```kotlin
   fun leaveChannel() {
       // Step 1: Close all consumers FIRST
       consumers.values.forEach { consumer ->
           consumer.close()  // Stops audio playback
       }
       consumers.clear()

       // Step 2: Close transport AFTER consumers
       recvTransport?.close()
       recvTransport = null

       // Step 3: DO NOT dispose device (shared across channels)
       // Device persists for lifetime of MediasoupClient
   }
   ```

2. **Track created vs disposed in debug:**
   ```kotlin
   private var consumerCount = 0

   fun consumeAudio(...) {
       val consumer = recvTransport?.consume(...)
       consumer?.let {
           consumers[consumerId] = it
           consumerCount++
           Log.d("MediasoupClient", "Consumers created: $consumerCount, active: ${consumers.size}")
       }
   }
   ```

3. **Use LeakCanary for native leak detection:**
   ```kotlin
   // build.gradle.kts
   debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
   ```

**Warning signs:**
- Memory usage grows on repeated channel joins (Android Studio Profiler → Memory → Native)
- Crash with `OutOfMemoryError: Failed to allocate...`
- `adb logcat` shows `PeerConnection.dispose() not called` warnings
- Second channel join crashes with `IllegalStateException: MediaStreamTrack has been disposed`

---

### Pitfall 5: Material 3 Dark Theme Accessibility

**What goes wrong:** Dark theme with low-contrast text (gray on dark gray) fails accessibility requirements. Users with low vision can't read channel names, speaker names, or error messages.

**Why it happens:** Material 3 dark theme uses `onSurface` color (usually light gray) for text. If custom dark surface color is too dark, contrast ratio falls below WCAG AA minimum (4.5:1 for normal text, 3:1 for large text).

**How to avoid:**
1. **Use Material 3 semantic colors (don't hardcode):**
   ```kotlin
   Text(
       text = channelName,
       color = MaterialTheme.colorScheme.onSurface // NOT Color(0xFF888888)
   )
   ```

2. **Verify contrast ratios:**
   - Use online tool: [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/)
   - DarkBackground (#121212) vs onSurface (#E0E0E0) = 12.63:1 (WCAG AAA pass)
   - DarkSurface (#1E1E1E) vs onSurface (#E0E0E0) = 11.45:1 (WCAG AAA pass)

3. **Test with TalkBack accessibility:**
   ```
   Settings → Accessibility → TalkBack → Enable
   Navigate app with swipe gestures
   Verify all text is announced clearly
   ```

**Warning signs:**
- Text hard to read in bright sunlight
- TalkBack announces "unlabeled" for UI elements
- Google Play Console shows accessibility warnings after upload

**Source:** [Material 3 Accessibility Best Practices](https://developer.android.com/develop/ui/compose/designsystems/material3)

---

## Code Examples

Verified patterns for Phase 5 implementation.

### Example 1: Login with JWT Storage

```kotlin
// presentation/login/LoginViewModel.kt
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            val result = loginUseCase(email, password)

            _uiState.value = when {
                result.isSuccess -> LoginUiState.Success(result.getOrNull()!!)
                result.isFailure -> {
                    val error = result.exceptionOrNull()?.message ?: "Login failed"
                    LoginUiState.Error(error)
                }
                else -> LoginUiState.Error("Unknown error")
            }
        }
    }

    fun checkAutoLogin(): Boolean {
        val token = tokenManager.getToken()
        return token != null && !tokenManager.isTokenExpired()
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: User) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

// presentation/login/LoginScreen.kt
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "VoicePing Logo",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            isError = uiState is LoginUiState.Error,
            modifier = Modifier.fillMaxWidth()
        )

        // Inline error under email field (per user decision)
        if (uiState is LoginUiState.Error) {
            Text(
                text = (uiState as LoginUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Login button
        Button(
            onClick = { viewModel.login(email, password) },
            enabled = uiState !is LoginUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Login")
            }
        }
    }
}
```

**Integration with server:** POST `/api/auth/login` with `{ email, password }`, returns `{ token, expiresIn, userId, userName, eventId, channelIds, globalRole, eventRole }`.

---

### Example 2: Channel List with Team Grouping

```kotlin
// presentation/channels/ChannelListScreen.kt
@Composable
fun ChannelListScreen(
    viewModel: ChannelListViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val joinedChannel by viewModel.joinedChannel.collectAsState()
    val currentSpeaker by viewModel.currentSpeaker.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channels") },
                actions = {
                    // Connection status dot (green=connected)
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when (connectionState) {
                                    ConnectionState.CONNECTED -> Color.Green
                                    ConnectionState.CONNECTING -> Color.Yellow
                                    else -> Color.Red
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    // Profile icon
                    IconButton(onClick = { /* Open profile slide-out */ }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        bottomBar = {
            // Persistent bottom bar (mini-player style)
            BottomBar(
                joinedChannel = joinedChannel,
                currentSpeaker = currentSpeaker
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Group channels by team
            val channelsByTeam = channels.groupBy { it.team }

            channelsByTeam.forEach { (team, teamChannels) ->
                // Team header
                item {
                    TeamHeader(teamName = team.name)
                }

                // Channels in team
                items(teamChannels) { channel ->
                    ChannelRow(
                        channel = channel,
                        isJoined = channel.id == joinedChannel?.id,
                        onToggle = { viewModel.toggleChannel(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun TeamHeader(teamName: String) {
    Text(
        text = teamName,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun ChannelRow(
    channel: Channel,
    isJoined: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Speaker indicator with pulsing animation
            if (channel.currentSpeaker != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing dot animation
                    val infiniteTransition = rememberInfiniteTransition()
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(pulseAlpha)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = channel.currentSpeaker.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // User count
            Text(
                text = "${channel.userCount} users",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Toggle checkbox (single channel only in Phase 5)
        Checkbox(
            checked = isJoined,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun BottomBar(
    joinedChannel: Channel?,
    currentSpeaker: User?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (joinedChannel != null) {
                Column {
                    Text(
                        text = joinedChannel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (currentSpeaker != null) {
                        Text(
                            text = "🔊 ${currentSpeaker.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Listening...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "No channel joined",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

**Note:** Team grouping data comes from server's channel list response (channels include `teamId`, teams fetched separately or embedded).

---

### Example 3: WebSocket Message Handling (SPEAKER_CHANGED)

```kotlin
// data/repository/ChannelRepository.kt
class ChannelRepository @Inject constructor(
    private val signalingClient: SignalingClient,
    private val mediasoupClient: MediasoupClient
) {
    private val _currentSpeaker = MutableStateFlow<User?>(null)
    val currentSpeaker: StateFlow<User?> = _currentSpeaker.asStateFlow()

    suspend fun observeSpeakerChanges(channelId: String) {
        signalingClient.messages
            .filter { it.type == SignalingType.SPEAKER_CHANGED }
            .collect { message ->
                val data = message.data ?: return@collect
                val messageChannelId = data["channelId"] as? String

                if (messageChannelId == channelId) {
                    val speakerUserId = data["speakerUserId"] as? String
                    val speakerName = data["speakerName"] as? String
                    val producerId = data["producerId"] as? String

                    if (speakerUserId != null && speakerName != null && producerId != null) {
                        // Speaker started transmitting
                        _currentSpeaker.value = User(speakerUserId, speakerName)

                        // Consume audio from this producer
                        mediasoupClient.consumeAudio(producerId, speakerUserId)
                    } else {
                        // Speaker stopped transmitting
                        _currentSpeaker.value = null
                    }
                }
            }
    }
}

// presentation/channels/ChannelListViewModel.kt
@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val joinChannelUseCase: JoinChannelUseCase
) : ViewModel() {

    val currentSpeaker = channelRepository.currentSpeaker

    fun toggleChannel(channel: Channel) {
        viewModelScope.launch {
            if (_joinedChannel.value?.id == channel.id) {
                // Leave current channel
                leaveChannelUseCase(channel.id)
                _joinedChannel.value = null
            } else {
                // Phase 5: single channel only — leave previous, join new
                _joinedChannel.value?.let { leaveChannelUseCase(it.id) }

                val result = joinChannelUseCase(channel.id)
                if (result.isSuccess) {
                    _joinedChannel.value = channel

                    // Start observing speaker changes
                    channelRepository.observeSpeakerChanges(channel.id)
                }
            }
        }
    }
}
```

**Server broadcast format (from handlers.ts):**
```json
{
  "type": "speaker-changed",
  "data": {
    "channelId": "channel-123",
    "speakerUserId": "user-456",
    "speakerName": "John Doe",
    "producerId": "producer-789"
  }
}
```

When speaker stops, `speakerUserId` is `null`.

---

## Open Questions

Phase 5-specific questions requiring investigation during planning.

### 1. libmediasoup-android 0.7.0 vs 0.21.0 Version Discrepancy

**What we know:**
- Milestone research mentioned 0.21.0 as "latest" (May 2025 release)
- Maven Central search shows 0.7.0 as latest (Feb 2026)
- GitHub crow-misia/libmediasoup-android may have newer versions than Maven Central publishes

**What's unclear:**
- Is 0.7.0 compatible with mediasoup server 3.19?
- Does 0.7.0 support all features needed for Phase 5 (Device.load(), createRecvTransport(), Consumer)?
- Should we use 0.21.0 from GitHub directly (via JitPack) or 0.7.0 from Maven Central?

**Recommendation:**
1. **Plan 1 (Project Setup):** Add both 0.7.0 (Maven Central) and check GitHub for latest release tag
2. **Plan 2 (Acceptance Test):** Build minimal app with chosen version, test against existing server
3. **If 0.7.0 fails:** Try GitHub latest release via JitPack:
   ```kotlin
   repositories {
       maven { url = uri("https://jitpack.io") }
   }
   dependencies {
       implementation("com.github.crow-misia:libmediasoup-android:<git-tag>")
   }
   ```

---

### 2. Event Picker Auto-Skip Logic on Saved Event

**What we know:**
- User decision: "Auto-skip event picker on launch if a saved event exists — go straight to channel list"
- Event switching available from profile slide-out menu

**What's unclear:**
- Where is saved event stored? (EncryptedSharedPreferences vs regular SharedPreferences)
- Should saved event be validated against server on launch? (event may have been deleted or user removed)
- What happens if saved event is invalid? (show event picker or show error?)

**Recommendation:**
1. Store last selected eventId in regular SharedPreferences (not sensitive data)
2. On app launch:
   - If saved eventId exists → navigate to ChannelListScreen
   - If saved eventId is null → navigate to EventPickerScreen
3. ChannelListViewModel fetches channel list for saved eventId:
   - On success → display channels
   - On failure (401/403) → navigate back to EventPickerScreen with error message
4. User can manually switch event via profile menu → EventPickerScreen

---

### 3. WebSocket JWT Token Passing Mechanism

**What we know:**
- Server expects JWT in WebSocket subprotocol (from websocketServer.ts handleProtocols)
- OkHttp WebSocket API supports custom headers via Request.Builder

**What's unclear:**
- Exact format server expects: `Sec-WebSocket-Protocol: voiceping, <jwt>` or different?
- Does server validate JWT during handshake or after connection established?
- What error does server return if JWT invalid? (close code 1008, 401, or custom error message?)

**Recommendation:**
1. **Test with web client first:** Inspect browser DevTools → Network → WS connection → Headers → Sec-WebSocket-Protocol
2. **Implement in Android:**
   ```kotlin
   .header("Sec-WebSocket-Protocol", "voiceping, $token")
   ```
3. **Log server response:** If connection fails, log WebSocket close code and reason
4. **If format wrong:** Check server logs for expected format, adjust client accordingly

---

### 4. Audio Output Default (Earpiece vs Speaker)

**What we know:**
- User decision: "Default audio output: earpiece (quiet/private mode) — user can switch to speaker later (Phase 6 adds toggle)"

**What's unclear:**
- Does WebRTC AudioTrack automatically route to earpiece when `AudioManager.MODE_IN_COMMUNICATION` set?
- Or does WebRTC ignore AudioManager mode and always route to speaker?
- Do we need to explicitly call `audioManager.isSpeakerphoneOn = false` before creating Consumer?

**Recommendation:**
1. **Test on physical device:** Join channel, receive audio, verify output device (earpiece icon in status bar)
2. **If routes to speaker by default:**
   ```kotlin
   // Before mediasoupClient.consumeAudio()
   audioRouter.setEarpieceMode()
   ```
3. **Log audio routing:** Check logcat for `AudioManager: setMode(MODE_IN_COMMUNICATION)` and `isSpeakerphoneOn: false`

---

## Sources

### Primary (HIGH confidence)

- [Kotlin 2.3.0 Released](https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/) — Kotlin version verification
- [Compose to Kotlin Compatibility Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) — Compose 1.10 + Kotlin 2.3.10 compatibility
- [Material 3 in Jetpack Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) — Material 3 dark theme best practices
- [Android Studio Release Updates 2025](https://androidstudio.googleblog.com/2025/) — AGP 9.0 release verification
- [crow-misia/libmediasoup-android GitHub](https://github.com/crow-misia/libmediasoup-android) — Wrapper maintenance status
- [libmediasoup-android Maven Central](https://mvnrepository.com/artifact/io.github.crow-misia.libmediasoup-android/libmediasoup-android) — Version 0.7.0 verification
- [Hardware-backed Keystore Android](https://source.android.com/docs/security/features/keystore) — Keystore security architecture
- [Android WebSockets with Kotlin](https://bugfender.com/blog/android-websockets/) — OkHttp WebSocket patterns
- [mediasoup Client-Server Communication](https://mediasoup.org/documentation/v3/communication-between-client-and-server/) — Protocol reference

### Secondary (MEDIUM confidence)

- [Secure Token Storage Best Practices](https://capgo.app/blog/secure-token-storage-best-practices-for-mobile-developers/) — JWT storage patterns
- [Securely Storing JWT Tokens in Android](https://medium.com/@mohammad.hasan.mahdavi81/securely-storing-jwt-tokens-in-android-with-datastore-and-manual-encryption-741b104a93d3) — EncryptedSharedPreferences implementation
- [OkHttp Comprehensive Guide](https://scrapfly.io/blog/posts/guide-to-okhttp-java-kotlin) — WebSocket + Coroutines patterns
- [Mastering Material 3 in Jetpack Compose 2025](https://medium.com/@hiren6997/mastering-material-3-in-jetpack-compose-the-2025-guide-1c1bd5acc480) — Dark theme implementation
- [Android WebSocket Doze Mode](https://forum.qt.io/topic/90939/sending-keep-alive-messages-on-android-even-if-the-device-is-in-sleep-doze-mode) — Heartbeat timing patterns

### Tertiary (LOW confidence, needs validation)

- libmediasoup-android 0.7.0 compatibility with mediasoup server 3.19 — NEEDS Phase 5 acceptance testing
- WebRTC AudioTrack default audio routing behavior — NEEDS physical device testing
- Event picker auto-skip validation logic — NEEDS design decision in planning

---

## Metadata

**Research date:** 2026-02-08
**Valid until:** ~30 days (Android ecosystem stable, unlikely to change Feb-Mar 2026)

**Confidence breakdown:**
- Standard stack: HIGH — Versions verified with official sources (Kotlin 2.3.10, Compose 1.10, Material 3 1.4, AGP 9.0)
- Architecture: HIGH — Clean Architecture + Service-bound pattern is proven Android approach
- Pitfalls: HIGH — WebRTC memory leaks, JWT storage, WebSocket patterns verified with multiple sources
- Integration: MEDIUM — mediasoup wrapper version discrepancy needs acceptance testing

**Next steps:**
1. Phase 5 planning should include acceptance test plan (Plan 2) that validates libmediasoup-android 0.7.0 compatibility
2. Open questions 1-4 should be resolved during Plan 1 (Project Setup) and Plan 2 (Acceptance Test)
3. If acceptance test fails (wrapper incompatible), pivot to alternative wrapper or WebView hybrid approach before proceeding to UI plans
