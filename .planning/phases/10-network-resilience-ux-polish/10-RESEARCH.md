# Phase 10: Network Resilience & UX Polish - Research

**Researched:** 2026-02-12
**Domain:** Android network resilience (WebSocket reconnection, WiFi/cellular handoff), offline caching, UI polish (haptic feedback, network indicators, transmission history, settings consolidation)
**Confidence:** HIGH

## Summary

Phase 10 transforms the app from functional to production-ready by adding cellular network resilience and UX polish. This phase addresses the harsh reality of mobile networks: connections drop, WiFi hands off to cellular mid-transmission, and users need to know what's happening without disrupting their workflow.

The technical challenge is multi-layered. Network resilience requires coordinating ConnectivityManager callbacks with WebSocket reconnection logic, maintaining offline state with disk-cached data, and handling WiFi-to-cellular handoffs without dropping PTT transmissions. UX polish requires completing haptic feedback patterns for all transmission events, adding real-time network quality indicators via WebSocket ping latency, implementing transmission history with in-memory storage, and consolidating scattered settings into a proper Settings screen.

Fortunately, the existing codebase provides strong foundations. SignalingClient already has heartbeat infrastructure (25s PING) that can be enhanced for latency measurement. SettingsRepository uses DataStore for type-safe settings persistence. TonePlayer and HapticFeedback exist with established patterns. The challenge is integrating these pieces with Android platform APIs (ConnectivityManager, Room or DataStore for offline caching) and Material3 UI components (ModalBottomSheet, Snackbar/banner, settings composables).

**Primary recommendation:** Use ConnectivityManager NetworkCallback for network state detection, enhance existing SignalingClient with exponential backoff reconnection (1s→2s→4s→8s capped at 30s), persist channel/event/team structure to Room database for offline caching, add network quality measurement via WebSocket ping round-trip timing, implement transmission history with in-memory circular buffer (last 20 per channel), complete haptic patterns for transmission events, and build dedicated Settings screen with Material3 preference-style composables.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Reconnection behavior
- Brief drops (1-3s): Audio drops silently, resumes when back. No user notification for brief blips.
- Long disconnection (5+s): "Reconnecting..." banner slides down from top bar AND connection dot changes color. Both indicators active.
- WiFi-to-cellular handoff: Hold PTT, attempt transparent reconnection across handoff. Don't auto-release.
- Extended disconnection (30+s): Auto-rejoin ALL previously monitored channels when connection restores.
- Retry strategy: Exponential backoff (1s, 2s, 4s, 8s... capped at 30s).
- Max retry: Give up after 5 minutes, show "Connection lost" with manual Retry button.
- Network restore: When ConnectivityManager detects network available, reset backoff and retry immediately.
- Banner text: Just "Reconnecting..." — no attempt count or elapsed time shown.

#### Connection tones
- Subtle up tone when connecting/reconnecting successfully.
- Down tone on disconnection.
- Respect existing tone toggle setting (silent if tones disabled).

#### PTT during reconnection
- PTT button stays interactive during reconnection.
- If user presses PTT while disconnected, show error tone + haptic (existing error patterns).
- Do NOT gray out or disable PTT.

#### Offline state & caching
- Cached channel list stays interactive when offline — user can browse, tap channels, see details.
- PTT errors if pressed while offline (same error pattern as reconnection).
- Persist event/channel/team structure to disk — app shows last-known state even on cold start with no network.
- Bottom bar shows primary channel name with offline badge. PTT available but errors if pressed.
- Foreground notification keeps channel name, adds "Reconnecting..." as subtitle.

#### Network quality indicator
- Placement: Top bar, near existing connection status dot.
- Visual form: Signal bars icon (like cellular signal strength). Universally understood.
- Tap action: Tapping signal bars reveals popup/tooltip showing latency (ms), connection type (WiFi/cellular), and server name.
- Quality metric: WebSocket ping latency (simple round-trip on existing connection).

#### Transmission history
- Access: Long-press on channel row opens bottom sheet with transmission history.
- Entry info: Speaker name + timestamp + duration per transmission.
- History depth: Last 20 transmissions per channel.
- Persistence: Current session only (in-memory). Clears on app restart.

### Claude's Discretion
- Exact reconnection timing thresholds (what counts as "brief" vs "long")
- Signal bars threshold values (latency ranges for 1/2/3/4 bars)
- Quality popup layout and animation
- Bottom sheet design for transmission history
- Haptic feedback completion — specific vibration patterns for any missing events
- Settings screen organization and grouping
- ConnectivityManager implementation details (NetworkCallback vs polling)
- Disk caching mechanism (Room, DataStore, or file-based)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope

</user_constraints>

## Standard Stack

### Core Libraries

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.room | 2.6.1 | SQLite database for offline caching | Official Jetpack solution for structured disk persistence, type-safe queries, coroutine support, migration handling |
| androidx.datastore | 1.1.1 | Settings persistence (already in use) | Modern replacement for SharedPreferences, coroutine-first API, already integrated |
| OkHttp | 4.12.0 | WebSocket with reconnection (already in use) | Industry standard for Android networking, already powering SignalingClient |
| ConnectivityManager NetworkCallback | API 26+ | Network state monitoring | Platform API for detecting WiFi/cellular handoff, network loss/restore events |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| androidx.compose.material3 | 1.5.0-alpha13 | ModalBottomSheet, Snackbar | Transmission history bottom sheet, reconnection banner |
| kotlinx.coroutines | 1.9.0 | Flow-based state management (already in use) | Network state flows, reconnection logic coordination |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Room database | File-based JSON caching | Room provides type safety, migrations, query support. JSON simpler but fragile at scale |
| NetworkCallback | BroadcastReceiver for CONNECTIVITY_ACTION | NetworkCallback is modern (API 26+), BroadcastReceiver deprecated and less reliable |
| ModalBottomSheet | Custom AnimatedVisibility bottom sheet | Material3 component provides standard drag handle, dismiss gestures, scrim out-of-box |

**Room Setup:**
```kotlin
// Already have: DataStore, OkHttp, Material3, Coroutines
// Add Room only:
dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
```

## Architecture Patterns

### Recommended Project Structure

Existing codebase follows clean architecture with data/domain/presentation layers. Phase 10 adds:

```
android/app/src/main/java/com/voiceping/android/
├── data/
│   ├── network/
│   │   ├── SignalingClient.kt              # Enhance with reconnection logic
│   │   └── NetworkMonitor.kt                # NEW: ConnectivityManager wrapper
│   ├── database/                            # NEW: Room entities and DAOs
│   │   ├── VoicePingDatabase.kt
│   │   ├── entities/
│   │   │   ├── EventEntity.kt
│   │   │   ├── ChannelEntity.kt
│   │   │   └── TeamEntity.kt
│   │   └── dao/
│   │       ├── EventDao.kt
│   │       └── ChannelDao.kt
│   ├── repository/
│   │   ├── ChannelRepository.kt             # Enhance with disk caching
│   │   └── TransmissionHistoryRepository.kt # NEW: In-memory history
│   └── audio/
│       ├── TonePlayer.kt                    # Add connection tones
│       └── HapticFeedback.kt                # Add transmission event patterns
├── domain/
│   └── model/
│       ├── ConnectionState.kt               # Enhance: RECONNECTING state
│       ├── NetworkQuality.kt                # NEW: latency-based quality
│       └── TransmissionHistoryEntry.kt      # NEW: history model
└── presentation/
    ├── settings/
    │   └── SettingsScreen.kt                # NEW: Dedicated settings screen
    └── channels/
        └── components/
            ├── NetworkQualityIndicator.kt   # NEW: Signal bars + popup
            ├── ReconnectionBanner.kt        # NEW: Top bar banner
            └── TransmissionHistorySheet.kt  # NEW: Bottom sheet

```

### Pattern 1: Exponential Backoff Reconnection with NetworkCallback Reset

**What:** WebSocket reconnection with exponential backoff that resets when ConnectivityManager detects network restoration.

**When to use:** Mobile apps where network transitions (WiFi→cellular) and brief disconnections are common.

**Example:**
```kotlin
// Based on official Android connectivity docs + OkHttp patterns
class SignalingClient @Inject constructor(
    private val gson: Gson,
    private val networkMonitor: NetworkMonitor // NEW: wraps ConnectivityManager
) {
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private val maxReconnectAttempts = 10 // 5 minutes at 30s cap

    private fun calculateBackoff(): Long {
        val delay = (2.0.pow(reconnectAttempt) * 1000L).toLong()
        return delay.coerceAtMost(30_000L) // Cap at 30 seconds
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delay = calculateBackoff()
            delay(delay)

            if (reconnectAttempt >= maxReconnectAttempts) {
                _connectionState.value = ConnectionState.FAILED
                return@launch
            }

            reconnectAttempt++
            connect(lastServerUrl, lastToken) // Retry connection
        }
    }

    init {
        // When network becomes available, reset backoff and retry immediately
        scope.launch {
            networkMonitor.isNetworkAvailable.collect { available ->
                if (available && connectionState.value == ConnectionState.RECONNECTING) {
                    reconnectAttempt = 0 // Reset backoff
                    scheduleReconnect() // Retry immediately
                }
            }
        }
    }
}
```

**Source:** [How to Implement Reconnection Logic for WebSockets](https://oneuptime.com/blog/post/2026-01-27-websocket-reconnection-logic/view), [ConnectivityManager.NetworkCallback - Android Developers](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)

### Pattern 2: Room Single-Source-of-Truth with Network-First Loading

**What:** Repository pattern where Room database is authoritative source, UI always observes database, network updates write to database.

**When to use:** Apps requiring offline-first architecture with disk-cached server data.

**Example:**
```kotlin
@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val signalingClient: SignalingClient
) {
    // UI observes this Flow - always reflects database state
    val channels: Flow<List<Channel>> = channelDao.getAllChannelsFlow()
        .map { entities -> entities.map { it.toDomain() } }

    suspend fun refreshChannels() {
        try {
            // Fetch from network
            val response = signalingClient.request(SignalingType.GET_CHANNELS)
            val serverChannels = parseChannels(response)

            // Write to database (single source of truth)
            channelDao.insertAll(serverChannels.map { it.toEntity() })

            // UI automatically updates via Flow
        } catch (e: Exception) {
            // Network failed, UI still shows cached data from database
            Log.e(TAG, "Failed to refresh channels, using cached data", e)
        }
    }
}
```

**Source:** [Page from network and database - Android Developers](https://developer.android.com/topic/libraries/architecture/paging/v3-network-db), [Enabling cache & offline support on Android using Room](https://proandroiddev.com/enabling-cache-offline-support-on-android-using-room-4b82ae0c9c88)

### Pattern 3: WebSocket Ping Latency Measurement

**What:** Measure round-trip time (RTT) using application-level heartbeat messages with timestamps.

**When to use:** Need to show real-time connection quality to users (latency-based signal bars).

**Example:**
```kotlin
class SignalingClient @Inject constructor(
    private val gson: Gson
) {
    private val _latency = MutableStateFlow<Long?>(null)
    val latency: StateFlow<Long?> = _latency.asStateFlow()

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (connectionState.value == ConnectionState.CONNECTED) {
                val startTime = System.currentTimeMillis()

                try {
                    // Send PING, wait for PONG response
                    request(SignalingType.PING, timeout = 5000)

                    val rtt = System.currentTimeMillis() - startTime
                    _latency.value = rtt
                } catch (e: Exception) {
                    _latency.value = null // Connection issue
                }

                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }
}

// UI layer maps latency to signal bars
enum class NetworkQuality {
    EXCELLENT, // <100ms
    GOOD,      // 100-300ms
    FAIR,      // 300-600ms
    POOR       // >600ms or null
}
```

**Source:** [Measuring WebSockets connection latency](https://github.com/vtortola/WebSocketListener/wiki/Measuring-WebSockets-connection-latency), [How to Implement Heartbeat/Ping-Pong in WebSockets](https://oneuptime.com/blog/post/2026-01-27-websocket-heartbeat/view)

### Pattern 4: Material3 ModalBottomSheet with Lazy Column

**What:** Bottom sheet with scrollable list content, standard Material3 drag handle and scrim.

**When to use:** Showing auxiliary information (transmission history) without navigating away from main screen.

**Example:**
```kotlin
@Composable
fun TransmissionHistorySheet(
    channelId: String,
    onDismiss: () -> Unit,
    historyRepository: TransmissionHistoryRepository
) {
    val sheetState = rememberModalBottomSheetState()
    val history by historyRepository.getHistory(channelId).collectAsState(initial = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            items(history) { entry ->
                ListItem(
                    headlineContent = { Text(entry.speakerName) },
                    supportingContent = {
                        Text("${entry.timestamp.format()} • ${entry.durationSeconds}s")
                    }
                )
            }
        }
    }
}
```

**Source:** [Bottom sheets - Jetpack Compose - Android Developers](https://developer.android.com/develop/ui/compose/components/bottom-sheets), [ModalBottomSheet - Material 3 Compose](https://composables.com/docs/androidx.compose.material3/material3/components/ModalBottomSheet)

### Pattern 5: ConnectivityManager NetworkCallback for WiFi/Cellular Transitions

**What:** Modern API for monitoring network state changes with detailed capability information.

**When to use:** Detecting network loss, WiFi→cellular handoff, network restoration for reconnection triggers.

**Example:**
```kotlin
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _networkType = MutableStateFlow<NetworkType>(NetworkType.NONE)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isNetworkAvailable.value = true
        }

        override fun onLost(network: Network) {
            _isNetworkAvailable.value = false
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            _networkType.value = when {
                capabilities.hasTransport(TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                else -> NetworkType.OTHER
            }
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
```

**Source:** [Monitor connectivity status - Android Developers](https://developer.android.com/training/monitoring-device-state/connectivity-status-type), [ConnectivityManager.NetworkCallback - Android Developers](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)

### Anti-Patterns to Avoid

- **Blocking UI on network operations:** Always use suspend functions with Dispatchers.IO, never runBlocking on main thread
- **Infinite reconnection without user escape:** After max attempts (5 minutes), require manual intervention (Retry button)
- **Clearing cache on disconnect:** Offline mode requires showing last-known state, never clear database on network loss
- **Exposing raw WebSocket state to UI:** Use domain-level ConnectionState enum, hide implementation details (OkHttp WebSocketListener callbacks)
- **Hardcoding latency thresholds in UI:** Define NetworkQuality enum with centralized thresholds, makes tuning easier

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Network state monitoring | Custom broadcast receivers for CONNECTIVITY_ACTION | ConnectivityManager.NetworkCallback | CONNECTIVITY_ACTION deprecated, NetworkCallback provides reliable WiFi/cellular transition events with detailed capabilities |
| Offline database schema migration | Manual SQLite ALTER TABLE scripts | Room migration API with @Database(version) | Room handles schema versioning, migrations, and rollback safely. Manual migrations are fragile and error-prone |
| Bottom sheet gestures and scrim | Custom AnimatedVisibility with touch handlers | Material3 ModalBottomSheet | Drag handle, dismiss gestures, scrim, accessibility support, keyboard handling all built-in |
| Exponential backoff timer | Custom delay calculation with coroutine launch | WorkManager BackoffPolicy.EXPONENTIAL or coroutine delay with 2^n formula | WorkManager survives process death, coroutine delay simpler for in-process backoff. Don't build custom scheduler |
| Settings preference UI | Custom composables for each setting type | Material3 ListItem with Switch/RadioButton/Slider patterns | Consistent Material3 styling, accessibility support, standard interaction patterns |

**Key insight:** Android platform provides robust solutions for all network resilience and offline caching problems. Custom implementations introduce edge cases (race conditions in network callbacks, schema migration bugs, gesture handling inconsistencies). Use proven platform APIs and Jetpack components — they've been battle-tested across millions of devices.

## Common Pitfalls

### Pitfall 1: WebSocket Reconnection Race Condition

**What goes wrong:** Multiple reconnection attempts triggered simultaneously when network flutters (WiFi signal weak, repeatedly connects/disconnects in 1-2 second cycles).

**Why it happens:** NetworkCallback.onAvailable fires every time network becomes available, but WebSocket connection may still be attempting previous reconnection. Result: multiple parallel connection attempts, socket leaks, confusing UI state.

**How to avoid:**
- Cancel pending reconnection job before starting new one
- Use StateFlow for connection state, check current state before attempting reconnection
- Debounce network availability events (wait 500ms for network to stabilize)

**Warning signs:**
- "WebSocket already connecting" errors in logs
- Connection status dot rapidly flickering
- Multiple simultaneous "Reconnecting..." banners

**Code pattern:**
```kotlin
private var reconnectJob: Job? = null

private fun scheduleReconnect() {
    reconnectJob?.cancel() // CRITICAL: Cancel existing job

    if (connectionState.value != ConnectionState.RECONNECTING) {
        return // Don't reconnect if already connected or connecting
    }

    reconnectJob = scope.launch {
        delay(calculateBackoff())
        connect(lastServerUrl, lastToken)
    }
}
```

### Pitfall 2: Room Database Queries on Main Thread

**What goes wrong:** App freezes (ANR - Application Not Responding) when querying Room database on main thread, especially on cold start when database is large.

**Why it happens:** Room enforces main thread query safety by throwing IllegalStateException, but developers sometimes use `.allowMainThreadQueries()` as quick fix. This causes UI jank and ANRs.

**How to avoid:**
- Always use suspend DAO functions called from ViewModel coroutines
- Use Flow-based queries for UI observation (`.collectAsState()` in composables)
- Never use `.allowMainThreadQueries()` in production code
- Profile database operations with Android Profiler to identify slow queries

**Warning signs:**
- UI freezes when navigating to channel list screen
- ANR (Application Not Responding) dialogs on app launch
- StrictMode violations in debug builds

**Code pattern:**
```kotlin
// DAO - all methods are suspend or return Flow
@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE eventId = :eventId")
    fun getChannelsFlow(eventId: String): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)
}

// Repository - wraps DAO in Dispatchers.IO
suspend fun refreshChannels() = withContext(Dispatchers.IO) {
    val serverChannels = signalingClient.request(SignalingType.GET_CHANNELS)
    channelDao.insertAll(serverChannels.map { it.toEntity() })
}

// ViewModel - launches coroutines in viewModelScope
fun loadChannels() {
    viewModelScope.launch {
        channelRepository.refreshChannels()
    }
}

// UI - collects Flow as Compose State
val channels by channelRepository.channels.collectAsState(initial = emptyList())
```

**Source:** [The Hidden Dangers of Room Database Performance (And How to Fix Them)](https://proandroiddev.com/the-hidden-dangers-of-room-database-performance-and-how-to-fix-them-ac93830885bd)

### Pitfall 3: Memory Leak from Uncollected Flows in Lifecycle

**What goes wrong:** Transmission history or network state Flows continue collecting after screen navigates away, causing memory leaks and wasted CPU cycles.

**Why it happens:** StateFlow is hot and keeps emitting even without collectors. If ViewModel collects Flow in `init` block without lifecycle awareness, collection survives screen navigation.

**How to avoid:**
- Use `viewModelScope.launch` in ViewModels (auto-cancels when ViewModel cleared)
- Use `collectAsState()` in composables (auto-cancels when composable leaves composition)
- Use `repeatOnLifecycle(Lifecycle.State.STARTED)` for Fragment/Activity collection
- Never use `GlobalScope.launch` for Flow collection

**Warning signs:**
- Memory leaks detected by LeakCanary
- Battery drain from background coroutines
- Log statements continuing after screen navigation

**Code pattern:**
```kotlin
// GOOD: ViewModel with viewModelScope
class ChannelListViewModel @Inject constructor(
    private val channelRepository: ChannelRepository
) : ViewModel() {

    val channels = channelRepository.channels
        .stateIn(
            scope = viewModelScope, // Auto-cancels when ViewModel cleared
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

// GOOD: Composable with collectAsState
@Composable
fun ChannelListScreen(viewModel: ChannelListViewModel) {
    val channels by viewModel.channels.collectAsState() // Auto-cancels on leave
}

// BAD: GlobalScope leak
init {
    GlobalScope.launch { // NEVER DO THIS
        networkMonitor.isNetworkAvailable.collect { ... }
    }
}
```

### Pitfall 4: ConnectionState Races Between SignalingClient and UI

**What goes wrong:** UI shows "Connected" while SignalingClient is actually reconnecting, or banner shows "Reconnecting..." after connection already restored.

**Why it happens:** Multiple components (SignalingClient, NetworkMonitor, ChannelRepository) all emit connection state changes. If UI observes wrong source or combines sources incorrectly, state becomes inconsistent.

**How to avoid:**
- Single source of truth: SignalingClient.connectionState is authoritative
- NetworkMonitor only triggers reconnection attempts, doesn't override connection state
- UI observes SignalingClient.connectionState directly or through Repository wrapper
- Use sealed class for ConnectionState to prevent invalid state combinations

**Warning signs:**
- Banner stays visible after successful reconnection
- Connection dot color doesn't match actual state
- "Connection lost" message while actively transmitting

**Code pattern:**
```kotlin
// SignalingClient - single source of truth
private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

// Repository - delegates to SignalingClient, doesn't override
val connectionState: StateFlow<ConnectionState> = signalingClient.connectionState

// UI - observes Repository or SignalingClient directly
val connectionState by viewModel.connectionState.collectAsState()

when (connectionState) {
    ConnectionState.CONNECTED -> { /* Show green dot */ }
    ConnectionState.RECONNECTING -> { /* Show banner + yellow dot */ }
    ConnectionState.FAILED -> { /* Show "Connection lost" with Retry */ }
    else -> { /* ... */ }
}
```

### Pitfall 5: Disk Cache Staleness Without TTL

**What goes wrong:** User sees outdated channel list or team structure that was cached days ago, doesn't realize data is stale.

**Why it happens:** Room database persists data indefinitely. If network is offline on app launch, cached data from previous session is shown without indication it might be outdated.

**How to avoid:**
- Store timestamp with each cached entity (e.g., `lastUpdated: Long`)
- Show "Last updated X minutes ago" in offline state
- Attempt refresh on app launch, fall back to cache if network unavailable
- Consider TTL policy (e.g., cache older than 24 hours shows warning)

**Warning signs:**
- User reports seeing channels they're no longer assigned to
- Team structure doesn't match server state
- Confusion about which data is current vs cached

**Code pattern:**
```kotlin
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val teamId: String,
    val lastUpdated: Long = System.currentTimeMillis() // Add timestamp
)

// Repository checks staleness
suspend fun getChannelsWithFreshness(): Pair<List<Channel>, Boolean> {
    val cached = channelDao.getAllChannels()
    val isStale = cached.any {
        System.currentTimeMillis() - it.lastUpdated > 24.hours.inWholeMilliseconds
    }

    if (isStale) {
        // Try to refresh from network
        try {
            refreshChannels()
        } catch (e: Exception) {
            // Network failed, return cached with staleness flag
            return cached.map { it.toDomain() } to true
        }
    }

    return cached.map { it.toDomain() } to false
}
```

**Source:** [How to make an offline cache in android using Room database and MVVM architecture](https://divyanshutw.medium.com/how-to-make-an-offline-cache-in-android-using-room-database-and-mvvm-architecture-6d1b011e819c)

## Code Examples

Verified patterns from official sources and established Android best practices:

### Network Quality Signal Bars Mapping

```kotlin
// Based on typical PTT radio latency expectations
enum class NetworkQuality(val bars: Int) {
    EXCELLENT(4), // <100ms - LAN/WiFi optimal
    GOOD(3),      // 100-300ms - Normal cellular/WiFi
    FAIR(2),      // 300-600ms - Degraded connection
    POOR(1);      // >600ms or null - Unusable for PTT

    companion object {
        fun fromLatency(latencyMs: Long?): NetworkQuality {
            return when {
                latencyMs == null -> POOR
                latencyMs < 100 -> EXCELLENT
                latencyMs < 300 -> GOOD
                latencyMs < 600 -> FAIR
                else -> POOR
            }
        }
    }
}

@Composable
fun NetworkQualityIndicator(
    latency: Long?,
    networkType: NetworkType,
    serverUrl: String,
    onClick: () -> Unit
) {
    val quality = NetworkQuality.fromLatency(latency)

    IconButton(onClick = onClick) {
        when (quality.bars) {
            4 -> Icon(Icons.Default.SignalCellular4Bar, "Excellent")
            3 -> Icon(Icons.Default.SignalCellular3Bar, "Good")
            2 -> Icon(Icons.Default.SignalCellular2Bar, "Fair")
            1 -> Icon(Icons.Default.SignalCellular1Bar, "Poor")
            else -> Icon(Icons.Default.SignalCellularNull, "No signal")
        }
    }
}
```

### Connection/Disconnection Tones

```kotlin
// Add to TonePlayer.kt (already exists with DTMF patterns)
class TonePlayer @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    /**
     * Play connection established tone - subtle up chirp.
     *
     * Tone: DTMF 9 (1477 Hz + 852 Hz) for 120ms
     * Configurable: Yes (respects PTT start tone toggle)
     * Purpose: Audio feedback that reconnection succeeded
     */
    fun playConnectionTone() {
        try {
            if (settingsRepository.getCachedPttStartToneEnabled()) {
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_9, 120)
                Log.d(TAG, "Playing connection tone")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing connection tone", e)
        }
    }

    /**
     * Play disconnection tone - subtle down chirp.
     *
     * Tone: DTMF 7 (1209 Hz + 852 Hz) for 150ms
     * Configurable: Yes (respects PTT start tone toggle)
     * Purpose: Audio feedback that connection lost
     */
    fun playDisconnectionTone() {
        try {
            if (settingsRepository.getCachedPttStartToneEnabled()) {
                toneGenerator?.startTone(ToneGenerator.TONE_DTMF_7, 150)
                Log.d(TAG, "Playing disconnection tone")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing disconnection tone", e)
        }
    }
}
```

**Source:** [ToneGenerator - Android Developers](https://developer.android.com/reference/android/media/ToneGenerator)

### Transmission Event Haptic Patterns

```kotlin
// Add to HapticFeedback.kt (already exists with PTT patterns)
class HapticFeedback @Inject constructor(
    @ApplicationContext context: Context
) {
    /**
     * Vibrate when incoming transmission starts - light pulse.
     *
     * Pattern: Single pulse, 40ms duration, half amplitude
     * Purpose: Subtle tactile feedback someone started speaking
     */
    fun vibrateTransmissionStart() {
        try {
            if (vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE / 2)
                vibrator.vibrate(effect)
                Log.d(TAG, "Transmission start vibration triggered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating on transmission start", e)
        }
    }

    /**
     * Vibrate when channel becomes busy - double tap pattern.
     *
     * Pattern: Tap (30ms), pause (40ms), tap (30ms)
     * Purpose: Distinct "channel busy" feedback when user tries to PTT
     */
    fun vibrateBusy() {
        try {
            if (vibrator?.hasVibrator() == true) {
                val timings = longArrayOf(0, 30, 40, 30)
                val amplitudes = intArrayOf(0, 128, 0, 128)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
                Log.d(TAG, "Channel busy vibration triggered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating on busy channel", e)
        }
    }
}
```

**Source:** [Create custom haptic effects - Android Developers](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects), [Haptics design principles - Android Developers](https://developer.android.com/develop/ui/views/haptics/haptics-principles)

### Room Database Schema for Offline Caching

```kotlin
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val eventId: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val teamId: String,
    val teamName: String, // Denormalized for easier queries
    val eventId: String,
    val userCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE eventId = :eventId ORDER BY teamName, name")
    fun getChannelsFlow(eventId: String): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE eventId = :eventId")
    suspend fun deleteByEvent(eventId: String)
}

@Database(
    entities = [EventEntity::class, TeamEntity::class, ChannelEntity::class],
    version = 1,
    exportSchema = true
)
abstract class VoicePingDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun channelDao(): ChannelDao
}
```

**Source:** [Save data in a local database using Room - Android Developers](https://developer.android.com/training/data-storage/room)

### Reconnection Banner Composable

```kotlin
@Composable
fun ReconnectionBanner(
    connectionState: ConnectionState,
    onRetry: () -> Unit
) {
    AnimatedVisibility(
        visible = connectionState == ConnectionState.RECONNECTING ||
                  connectionState == ConnectionState.FAILED,
        enter = slideInVertically { -it }, // Slide down from top
        exit = slideOutVertically { -it }
    ) {
        Surface(
            color = if (connectionState == ConnectionState.FAILED)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (connectionState == ConnectionState.FAILED)
                        "Connection lost"
                    else
                        "Reconnecting...",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (connectionState == ConnectionState.FAILED) {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
```

**Source:** [Create a notification with a snackbar - Jetpack Compose - Android Developers](https://developer.android.com/develop/ui/compose/quick-guides/content/create-snackbar-notification)

### In-Memory Transmission History Repository

```kotlin
data class TransmissionHistoryEntry(
    val speakerName: String,
    val timestamp: LocalDateTime,
    val durationSeconds: Int
)

@Singleton
class TransmissionHistoryRepository @Inject constructor() {
    private val history = ConcurrentHashMap<String, CircularBuffer<TransmissionHistoryEntry>>()
    private val maxEntriesPerChannel = 20

    fun addEntry(channelId: String, entry: TransmissionHistoryEntry) {
        history.getOrPut(channelId) {
            CircularBuffer(maxEntriesPerChannel)
        }.add(entry)
    }

    fun getHistory(channelId: String): Flow<List<TransmissionHistoryEntry>> {
        return flowOf(history[channelId]?.toList() ?: emptyList())
    }

    fun clearAll() {
        history.clear()
    }
}

// Simple circular buffer implementation
class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = mutableListOf<T>()

    fun add(item: T) {
        if (buffer.size >= capacity) {
            buffer.removeAt(0) // Remove oldest
        }
        buffer.add(item)
    }

    fun toList(): List<T> = buffer.toList()
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SharedPreferences | Jetpack DataStore | 2021 (DataStore 1.0) | Type-safe, coroutine-first API, no UI blocking. EncryptedSharedPreferences deprecated 2025-12 |
| BroadcastReceiver for CONNECTIVITY_ACTION | ConnectivityManager.NetworkCallback | API 26 (2017) | More reliable WiFi/cellular transition detection, detailed network capabilities |
| Custom settings preference XML | Compose-based settings with Material3 | 2023-2024 (Material3 stable) | Declarative UI, easier theming, better state management |
| Manual exponential backoff timers | Coroutine delay with 2^n formula or WorkManager | Kotlin coroutines stable (2019) | Simpler, testable, survives process death (WorkManager) |
| Manual SQLite with cursors | Room with Flow-based queries | Room 2.0 (2018) | Type-safe, migration support, reactive updates |

**Deprecated/outdated:**
- `CONNECTIVITY_ACTION` BroadcastReceiver: Use NetworkCallback instead (more reliable, detailed capabilities)
- `EncryptedSharedPreferences`: Deprecated Dec 2025, migrate to DataStore + Tink
- `allowMainThreadQueries()` in Room: Never use in production, always use suspend or Flow
- Custom WebSocket libraries: OkHttp is industry standard for Android, well-tested

## Open Questions

### Question 1: Room Database Size Impact on Cold Start

**What we know:**
- Room databases are loaded lazily, but schema validation happens on first access
- Channel/event/team data is relatively small (<1000 rows total expected)
- SQLite startup time is negligible for small databases (<1MB)

**What's unclear:**
- Whether to pre-load database on app launch or lazily load on channel list screen
- Impact of Room schema migration on startup time (first install vs upgrade)

**Recommendation:**
- Lazy load on first screen that needs data (channel list)
- Monitor startup time with Android Profiler
- Add database schema version to debug logs for migration tracking

### Question 2: WebSocket Reconnection During Active PTT Transmission

**What we know:**
- User decision: Hold PTT, attempt transparent reconnection across handoff
- Existing PttManager uses server-side SPEAKER_CHANGED event for PTT grant/deny

**What's unclear:**
- Should PttManager buffer audio locally during brief reconnection (<3s)?
- How to handle PTT state when WebSocket reconnects mid-transmission?
- Does server-side speaker lock survive WebSocket reconnection?

**Recommendation:**
- Phase 10 focuses on reconnection infrastructure only
- Document assumption: Server maintains speaker lock across brief disconnections
- Add TODO for audio buffering investigation if WiFi handoff drops PTT in testing
- Validate behavior: Does server send SPEAKER_CHANGED when WebSocket reconnects with active speaker?

### Question 3: Transmission History Entry Creation Timing

**What we know:**
- User wants speaker name + timestamp + duration
- History triggered by long-press on channel row
- Last 20 transmissions per channel

**What's unclear:**
- When to create history entry: on SPEAKER_CHANGED event (start) or when speaker stops?
- How to calculate duration if entry created on start (need end event correlation)
- Should history include user's own transmissions or only incoming?

**Recommendation:**
- Create entry when speaker STOPS (have both start timestamp and duration)
- Track active speaker start time in ChannelRepository speaker observer
- Include both incoming and outgoing transmissions (users want full channel history)
- Store start timestamp in observer, create HistoryEntry on speaker change to null

### Question 4: Settings Screen Navigation and State Management

**What we know:**
- Settings currently scattered across ProfileDrawer (PTT, scan mode, hardware buttons)
- User wants consolidated Settings screen
- ProfileDrawer has 15+ setting parameters

**What's unclear:**
- Should Settings be separate screen (navigation) or bottom sheet (modal)?
- How to organize settings: flat list vs grouped categories?
- Should ProfileDrawer still exist for user info + logout, or merge everything?

**Recommendation:**
- Dedicated SettingsScreen (separate destination) for better organization
- ProfileDrawer keeps user info, event switcher, logout, settings launcher
- Settings groups: PTT (mode, tones, audio), Scan Mode (enable, delays, targeting), Hardware (volume keys, Bluetooth, boot), Advanced (auto-release timeout)
- Use Material3 ListItem + Switch/RadioButton patterns (standard preference-like UI)

## Sources

### Primary (HIGH confidence)

**Official Android Documentation:**
- [ConnectivityManager.NetworkCallback - Android Developers](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)
- [Monitor connectivity status - Android Developers](https://developer.android.com/training/monitoring-device-state/connectivity-status-type)
- [Save data in a local database using Room - Android Developers](https://developer.android.com/training/data-storage/room)
- [App Architecture: DataStore - Android Developers](https://developer.android.com/topic/libraries/architecture/datastore)
- [Bottom sheets - Jetpack Compose - Android Developers](https://developer.android.com/develop/ui/compose/components/bottom-sheets)
- [ToneGenerator - Android Developers](https://developer.android.com/reference/android/media/ToneGenerator)
- [Create custom haptic effects - Android Developers](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects)
- [Haptics design principles - Android Developers](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
- [BackoffPolicy - Android Developers](https://developer.android.com/reference/androidx/work/BackoffPolicy)

**Material Design Guidelines:**
- [Snackbar - Material Design 3](https://m3.material.io/components/snackbar/guidelines)
- [ModalBottomSheet - Material 3 Compose](https://composables.com/docs/androidx.compose.material3/material3/components/ModalBottomSheet)

### Secondary (MEDIUM confidence)

**Verified Technical Articles:**
- [How to Implement Reconnection Logic for WebSockets (2026)](https://oneuptime.com/blog/post/2026-01-27-websocket-reconnection-logic/view)
- [How to Implement Heartbeat/Ping-Pong in WebSockets (2026)](https://oneuptime.com/blog/post/2026-01-27-websocket-heartbeat/view)
- [Enabling cache & offline support on Android using Room - ProAndroidDev](https://proandroiddev.com/enabling-cache-offline-support-on-android-using-room-4b82ae0c9c88)
- [The Hidden Dangers of Room Database Performance - ProAndroidDev](https://proandroiddev.com/the-hidden-dangers-of-room-database-performance-and-how-to-fix-them-ac93830885bd)
- [Goodbye EncryptedSharedPreferences: A 2026 Migration Guide - ProAndroidDev](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a)
- [Android WorkManager: A Complete Technical Deep Dive (Nov 2025) - ProAndroidDev](https://proandroiddev.com/android-workmanager-a-complete-technical-deep-dive-f037c768d87b)
- [Measuring WebSockets connection latency - GitHub](https://github.com/vtortola/WebSocketListener/wiki/Measuring-WebSockets-connection-latency)

**Community Libraries and Patterns:**
- [Compose-Settings - GitHub (alorma)](https://github.com/alorma/Compose-Settings) - Material3 settings components reference
- [ComposePreference - GitHub (zhanghai)](https://github.com/zhanghai/ComposePreference) - Preference implementation patterns

### Tertiary (LOW confidence - for context only)

- [Build Real-Time Android Apps with WebSockets and Kotlin - Bugfender](https://bugfender.com/blog/android-websockets/)
- [How to make an offline cache in android using Room database and MVVM architecture - Medium](https://divyanshutw.medium.com/how-to-make-an-offline-cache-in-android-using-room-database-and-mvvm-architecture-6d1b011e819c)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries are official Jetpack/Android platform APIs with stable releases
- Architecture patterns: HIGH - Based on official Android architecture guide and verified community patterns
- Pitfalls: HIGH - Derived from official documentation warnings and production Android developer experience
- Connection tones/haptics: MEDIUM - Based on existing TonePlayer/HapticFeedback patterns, specific frequencies verified from official ToneGenerator docs
- Settings UI organization: MEDIUM - No single "blessed" pattern, multiple viable approaches (settled on dedicated screen based on Material3 conventions)

**Research date:** 2026-02-12
**Valid until:** ~60 days (March 2026) - Stack is stable (Jetpack libraries mature), patterns unlikely to change. Material3 Compose on regular release cycle but backward compatible.

**Cross-verification notes:**
- WebSocket reconnection patterns verified across 3 sources (OneUpTime 2026 guides, official OkHttp issues, Android connectivity docs)
- Room offline caching verified across official docs + 2 production-tested community articles
- NetworkCallback verified in official docs + practical implementation examples
- Haptic patterns verified against official Android haptics UX guidelines + API reference
- All code examples tested for compilation patterns (not executed, but structurally sound based on official API signatures)
