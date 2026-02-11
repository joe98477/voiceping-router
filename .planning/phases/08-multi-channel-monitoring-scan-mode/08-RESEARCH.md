# Phase 8: Multi-Channel Monitoring & Scan Mode - Research

**Researched:** 2026-02-11
**Domain:** Multi-channel audio monitoring with scan mode, state management, DataStore persistence
**Confidence:** HIGH

## Summary

Phase 8 extends the single-channel architecture (Phase 7) to support monitoring up to 5 channels simultaneously with automatic scan mode switching. The core challenge is managing multiple mediasoup consumers for concurrent audio playback while maintaining state synchronization between the UI, foreground service, and audio system.

Key architectural insights:
1. **Multi-consumer pattern**: mediasoup-client natively supports multiple consumers on a single RecvTransport - no manual audio mixing required at application level
2. **State persistence**: DataStore's `stringSetPreferencesKey` provides efficient Set<String> persistence for monitored channel IDs
3. **Scan mode timing**: LaunchedEffect with rememberUpdatedState pattern enables non-restarting delayed state updates for bottom bar return logic
4. **Notification updates**: Update by ID (same notification ID) to avoid foreground service restart, critical for performance

The implementation leverages existing Hilt singleton architecture (ChannelRepository, SettingsRepository) extended to manage collections rather than single items. No new libraries required - Jetpack Compose state management with StateFlow, DataStore for persistence, and mediasoup's native multi-consumer support provide complete functionality.

**Primary recommendation:** Extend ChannelRepository from single-channel to multi-channel by replacing `joinedChannelId: String?` with `monitoredChannels: StateFlow<Map<String, ChannelMonitoringState>>` and creating per-channel consumer tracking. Scan mode logic lives in ViewModel as derived state from active speakers across channels.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Bottom bar scan behavior
- Instant swap (no animation) when a non-primary channel has an active speaker — bottom bar immediately shows that channel
- Audio continues on ALL monitored channels regardless of which channel the bottom bar shows — bottom bar is visual focus only, not an audio switch
- When multiple non-primary channels are active simultaneously, bottom bar shows the most recently started transmission
- 2-3 second pause after transmission ends before returning to primary channel
- Channel name color change to distinguish when showing a scanned (non-primary) channel vs primary
- Silent switch — no tone/beep when bottom bar changes channels; incoming audio itself is the signal
- Per-channel RX squelch still plays for each channel's speaker start — helps user distinguish which channel is active

#### Manual channel lock
- User can tap bottom bar to manually switch to a specific monitored channel — this LOCKS the bottom bar to that channel
- When locked, scan mode auto-switching is paused — bottom bar stays on the locked channel
- Tap bottom bar again to unlock and return to scan mode / primary channel
- Audio from all monitored channels continues playing even when locked — locking only affects visual focus and PTT target

#### PTT target
- Setting in profile drawer: "Always primary" vs "Displayed channel"
- When "Displayed channel": PTT targets whatever channel the bottom bar is currently showing (including scanned or locked channel)
- When "Always primary": PTT always targets primary regardless of bottom bar state
- Notification PTT follows the same setting as in-app PTT target

#### Multi-channel audio mixing
- Setting in profile drawer: "Equal volume" vs "Primary priority"
- Equal volume: all active channels play at the same volume
- Primary priority: primary channel at full volume, non-primary channels play quieter
- Per-channel volume control (0-100% slider) accessible via small settings icon on each channel row — not in the foreground UI
- "Mute all except primary" quick action in the top bar

#### Mute behavior
- Muting a channel triggers server-side unsubscribe — stops audio from server to save bandwidth
- Muted channels show NO visual activity indicators — fully silent, no speaker name or pulse
- Unmuting immediately re-subscribes to server — if someone is mid-transmission, user hears it (with slight rejoin delay)

#### Channel monitoring management
- Tap channel to join (toggle join/leave) — same tap gesture as current single-channel
- First joined channel becomes primary by default
- Long-press any joined channel to set it as primary — flexible reassignment
- Joined/monitored channels have filled/solid background; unjoined channels are outlined or dimmed
- When user tries to join a 6th channel: toast message "Maximum 5 channels. Leave a channel to join another."

#### Foreground notification
- Shows primary channel name + monitoring count: "Alpha (monitoring 3 others)"
- Notification PTT follows the same PTT target setting as in-app

#### Scan mode settings (Profile drawer)
- Scan mode toggle: on/off (default: ON when 2+ channels joined)
- PTT target: "Always primary" / "Displayed channel"
- Return delay: slider 2-5 seconds (default: 2-3s)
- Audio mix mode: "Equal volume" / "Primary priority"
- All settings persist across sessions via DataStore
- Monitored channels also persist — same channels on next launch

### Claude's Discretion
- Exact return delay default value within the 2-3s range (RECOMMENDATION: 2.5 seconds - middle of range)
- Per-channel settings icon design and placement details (RECOMMENDATION: Small IconButton with MoreVert icon at end of channel row)
- Primary channel visual indicator beyond filled background (RECOMMENDATION: Star icon badge in top-right corner of channel card)
- Animation details for channel join/leave transitions (RECOMMENDATION: fadeIn/fadeOut with 150ms duration for smooth visual feedback)
- DataStore schema for persisting monitored channels (RECOMMENDATION: stringSetPreferencesKey for channel IDs + separate stringPreferencesKey for primary channel ID)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope

</user_constraints>

## Standard Stack

### Core Libraries (Already in Project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| libmediasoup-android | 0.7.0 | WebRTC audio transport with multi-consumer support | crow-misia wrapper for mediasoup-client, single RecvTransport handles multiple consumers natively |
| Jetpack DataStore | 1.1.1 | Preferences persistence (scan settings, monitored channels) | Official replacement for SharedPreferences, type-safe keys including stringSetPreferencesKey for Set<String> |
| Jetpack Compose | 2026.01.00 BOM | UI with reactive state management | LaunchedEffect + rememberUpdatedState for delayed state updates (scan mode return timer) |
| Hilt | 2.59.1 | Dependency injection for singletons | @Singleton repositories manage shared state across multiple ViewModels |
| Kotlin Coroutines | 1.10.1 | Async operations and Flow-based state | StateFlow for reactive multi-channel state, standard for Jetpack Compose |

### Supporting Patterns
| Pattern | Purpose | When to Use |
|---------|---------|-------------|
| Map<String, T> in StateFlow | Track per-channel state (consumers, speakers, mute status) | Managing multiple channels with independent state |
| Derived State (derivedStateOf) | Compute scan mode target channel from active speakers | Avoid recomposition when calculating which channel should display in bottom bar |
| rememberUpdatedState | Capture latest values in LaunchedEffect without restarting coroutine | Scan mode return delay timer that reads current primary channel without effect restart |
| stringSetPreferencesKey | Persist Set<String> of monitored channel IDs | Efficient DataStore persistence of channel list across app restarts |

### No New Dependencies Required
All required functionality available in existing stack:
- Multi-consumer audio: mediasoup RecvTransport supports multiple consumers (verified in mediasoup docs)
- Audio volume control: WebRTC Consumer has volume setter (0.0-1.0 range)
- State persistence: DataStore stringSetPreferencesKey for Set<String> (verified in DataStore docs)
- Delayed state updates: Compose LaunchedEffect with delay() (standard Compose pattern)
- Collection state management: StateFlow<Map<K, V>> (standard Kotlin Flow)

## Architecture Patterns

### Recommended Data Model Structure

```kotlin
// Domain model for per-channel monitoring state
data class ChannelMonitoringState(
    val channelId: String,
    val channelName: String,
    val teamName: String,
    val isPrimary: Boolean,
    val isMuted: Boolean,
    val currentSpeaker: User?,
    val lastSpeaker: User?,
    val consumerId: String?,  // mediasoup consumer ID for this channel
    val volume: Float = 1.0f  // 0.0-1.0 range
)

// Scan mode state
data class ScanModeState(
    val enabled: Boolean,
    val returnDelaySeconds: Int,
    val displayedChannelId: String,  // What bottom bar shows (scan target or locked)
    val isLocked: Boolean  // Manual lock via tap
)

// PTT target mode
enum class PttTargetMode {
    ALWAYS_PRIMARY,
    DISPLAYED_CHANNEL
}

// Audio mix mode
enum class AudioMixMode {
    EQUAL_VOLUME,
    PRIMARY_PRIORITY
}
```

### Pattern 1: Multi-Channel State Management in Repository

**What:** Extend ChannelRepository singleton to manage Map of monitored channels instead of single channel

**When to use:** When multiple channels need independent state tracking (consumers, speakers, mute status)

**Implementation:**
```kotlin
@Singleton
class ChannelRepository @Inject constructor(...) {
    // Replace single-channel state with multi-channel map
    private val _monitoredChannels = MutableStateFlow<Map<String, ChannelMonitoringState>>(emptyMap())
    val monitoredChannels: StateFlow<Map<String, ChannelMonitoringState>> = _monitoredChannels.asStateFlow()

    private val _primaryChannelId = MutableStateFlow<String?>(null)
    val primaryChannelId: StateFlow<String?> = _primaryChannelId.asStateFlow()

    // Per-channel consumer tracking
    private val channelConsumers = mutableMapOf<String, MutableMap<String, String>>()  // channelId -> (producerId -> consumerId)

    suspend fun joinChannel(channelId: String, channelName: String, teamName: String): Result<Unit> {
        // Guard: max 5 channels
        if (_monitoredChannels.value.size >= 5 && !_monitoredChannels.value.containsKey(channelId)) {
            return Result.failure(Exception("Maximum 5 channels"))
        }

        // Create recv transport if first channel
        if (_monitoredChannels.value.isEmpty()) {
            mediasoupClient.createRecvTransport(channelId)
            startMonitoringService()
        }

        // Set as primary if first channel
        val isPrimary = _monitoredChannels.value.isEmpty()
        if (isPrimary) {
            _primaryChannelId.value = channelId
        }

        // Add to monitored channels
        _monitoredChannels.value = _monitoredChannels.value + (channelId to ChannelMonitoringState(
            channelId = channelId,
            channelName = channelName,
            teamName = teamName,
            isPrimary = isPrimary,
            isMuted = false,
            currentSpeaker = null,
            lastSpeaker = null,
            consumerId = null
        ))

        // Request JOIN_CHANNEL from server
        signalingClient.request(SignalingType.JOIN_CHANNEL, mapOf("channelId" to channelId))

        // Start observing speaker changes for this channel
        observeSpeakerChangesForChannel(channelId)

        return Result.success(Unit)
    }

    suspend fun leaveChannel(channelId: String): Result<Unit> {
        val state = _monitoredChannels.value[channelId] ?: return Result.success(Unit)

        // Close all consumers for this channel
        channelConsumers[channelId]?.forEach { (_, consumerId) ->
            mediasoupClient.closeConsumer(consumerId)
        }
        channelConsumers.remove(channelId)

        // Remove from monitored channels
        _monitoredChannels.value = _monitoredChannels.value - channelId

        // If was primary, assign new primary
        if (state.isPrimary && _monitoredChannels.value.isNotEmpty()) {
            val newPrimaryId = _monitoredChannels.value.keys.first()
            _primaryChannelId.value = newPrimaryId
            _monitoredChannels.value = _monitoredChannels.value.mapValues { (id, state) ->
                state.copy(isPrimary = id == newPrimaryId)
            }
        }

        // Stop service if last channel
        if (_monitoredChannels.value.isEmpty()) {
            stopMonitoringService()
            mediasoupClient.cleanup()
        }

        signalingClient.request(SignalingType.LEAVE_CHANNEL, mapOf("channelId" to channelId))

        return Result.success(Unit)
    }

    fun setPrimaryChannel(channelId: String) {
        if (!_monitoredChannels.value.containsKey(channelId)) return

        _primaryChannelId.value = channelId
        _monitoredChannels.value = _monitoredChannels.value.mapValues { (id, state) ->
            state.copy(isPrimary = id == channelId)
        }
    }

    suspend fun muteChannel(channelId: String) {
        // Close all consumers for this channel (bandwidth savings)
        channelConsumers[channelId]?.forEach { (_, consumerId) ->
            mediasoupClient.closeConsumer(consumerId)
        }
        channelConsumers[channelId]?.clear()

        // Update state
        _monitoredChannels.value[channelId]?.let { state ->
            _monitoredChannels.value = _monitoredChannels.value + (channelId to state.copy(isMuted = true))
        }
    }

    suspend fun unmuteChannel(channelId: String) {
        // Re-subscribe if someone is currently speaking
        _monitoredChannels.value[channelId]?.let { state ->
            _monitoredChannels.value = _monitoredChannels.value + (channelId to state.copy(isMuted = false))

            // If speaker active, consume immediately
            state.currentSpeaker?.let { speaker ->
                state.consumerId?.let { producerId ->
                    mediasoupClient.consumeAudio(producerId, speaker.id)
                }
            }
        }
    }
}
```

### Pattern 2: Scan Mode Logic in ViewModel with Derived State

**What:** Calculate which channel to display in bottom bar based on active speakers, using derivedStateOf to avoid unnecessary recompositions

**When to use:** When UI state depends on multiple input states (active speakers across channels, lock state, primary channel)

**Implementation:**
```kotlin
@HiltViewModel
class ChannelListViewModel @Inject constructor(...) {
    val monitoredChannels: StateFlow<Map<String, ChannelMonitoringState>> = channelRepository.monitoredChannels
    val primaryChannelId: StateFlow<String?> = channelRepository.primaryChannelId

    private val _scanModeLocked = MutableStateFlow(false)
    val scanModeLocked: StateFlow<Boolean> = _scanModeLocked.asStateFlow()

    private val _manuallySelectedChannelId = MutableStateFlow<String?>(null)

    // Derived state: which channel to display in bottom bar
    val displayedChannelId: StateFlow<String?> = combine(
        monitoredChannels,
        primaryChannelId,
        scanModeLocked,
        _manuallySelectedChannelId,
        settingsRepository.getScanModeEnabled()
    ) { channels, primary, locked, manual, scanEnabled ->
        when {
            locked && manual != null -> manual  // Manual lock takes priority
            !scanEnabled -> primary  // Scan disabled, show primary
            else -> {
                // Scan mode: find most recent active non-primary channel
                val activeChannels = channels.values
                    .filter { it.currentSpeaker != null && !it.isPrimary }
                    .sortedByDescending { it.currentSpeaker?.startTime ?: 0L }

                activeChannels.firstOrNull()?.channelId ?: primary
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleBottomBarLock() {
        val current = displayedChannelId.value
        if (_scanModeLocked.value) {
            // Unlock: return to scan mode
            _scanModeLocked.value = false
            _manuallySelectedChannelId.value = null
        } else {
            // Lock: freeze on current channel
            _scanModeLocked.value = true
            _manuallySelectedChannelId.value = current
        }
    }
}
```

### Pattern 3: Delayed Return to Primary Channel with rememberUpdatedState

**What:** Use rememberUpdatedState to capture latest primary channel ID in LaunchedEffect without restarting timer coroutine

**When to use:** Scan mode return delay (2-5 seconds) after transmission ends - timer shouldn't restart when primary changes

**Implementation:**
```kotlin
@Composable
fun ScanModeReturnEffect(
    currentSpeaker: User?,
    primaryChannelId: String?,
    returnDelaySeconds: Int,
    onReturnToPrimary: () -> Unit
) {
    // Capture latest primary ID without restarting effect
    val currentPrimary by rememberUpdatedState(primaryChannelId)

    // Start return timer when speaker stops (currentSpeaker becomes null)
    LaunchedEffect(currentSpeaker) {
        if (currentSpeaker == null && currentPrimary != null) {
            delay(returnDelaySeconds * 1000L)
            onReturnToPrimary()
        }
    }
}
```

**Source:** [Understanding rememberUpdatedState in Jetpack Compose](https://medium.com/@gaganraghunath99/understanding-rememberupdatedstate-in-jetpack-compose-14cd95aa71d9)

### Pattern 4: DataStore Persistence for Monitored Channels

**What:** Use stringSetPreferencesKey to persist Set<String> of monitored channel IDs across app restarts

**When to use:** Restoring multi-channel state on app relaunch - user returns to same monitored channels

**Implementation:**
```kotlin
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val MONITORED_CHANNEL_IDS = stringSetPreferencesKey("monitored_channel_ids")
        val PRIMARY_CHANNEL_ID = stringPreferencesKey("primary_channel_id")
        val SCAN_MODE_ENABLED = booleanPreferencesKey("scan_mode_enabled")
        val SCAN_RETURN_DELAY = intPreferencesKey("scan_return_delay")
        val PTT_TARGET_MODE = stringPreferencesKey("ptt_target_mode")
        val AUDIO_MIX_MODE = stringPreferencesKey("audio_mix_mode")
    }

    suspend fun setMonitoredChannels(channelIds: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MONITORED_CHANNEL_IDS] = channelIds
        }
    }

    fun getMonitoredChannels(): Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[Keys.MONITORED_CHANNEL_IDS] ?: emptySet()
    }

    suspend fun setPrimaryChannel(channelId: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PRIMARY_CHANNEL_ID] = channelId
        }
    }

    fun getPrimaryChannel(): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[Keys.PRIMARY_CHANNEL_ID]
    }

    suspend fun setScanReturnDelay(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SCAN_RETURN_DELAY] = seconds
        }
    }

    fun getScanReturnDelay(): Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[Keys.SCAN_RETURN_DELAY] ?: 2  // Default 2 seconds (user decision: 2-3s range)
    }

    suspend fun setPttTargetMode(mode: PttTargetMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PTT_TARGET_MODE] = mode.name
        }
    }

    fun getPttTargetMode(): Flow<PttTargetMode> = context.dataStore.data.map { preferences ->
        val modeName = preferences[Keys.PTT_TARGET_MODE] ?: PttTargetMode.ALWAYS_PRIMARY.name
        try {
            PttTargetMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            PttTargetMode.ALWAYS_PRIMARY
        }
    }
}
```

**Source:** [stringSetPreferencesKey documentation](https://androidx.github.io/kmp-eap-docs/libs/androidx.datastore/datastore-preferences-core/androidx.datastore.preferences.core/string-set-preferences-key.html)

### Pattern 5: Foreground Notification Update Without Service Restart

**What:** Update notification content using same notification ID to avoid foreground service stop/restart

**When to use:** Updating notification when primary channel changes or monitoring count changes - critical for performance

**Implementation:**
```kotlin
class ChannelMonitoringService : Service() {
    private fun updateNotification(primaryChannelName: String, monitoringCount: Int) {
        val notification = buildNotification(primaryChannelName, monitoringCount)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // CRITICAL: Use same NOTIFICATION_ID to update without service restart
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(primaryChannelName: String, monitoringCount: Int): Notification {
        val contentText = if (monitoringCount > 0) {
            "$primaryChannelName (monitoring $monitoringCount others)"
        } else {
            primaryChannelName
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentText)
            .setContentText("Monitoring")
            .setSmallIcon(R.drawable.ic_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1000  // SAME ID for all updates
    }
}
```

**Source:** [Updating Notifications - Android Developers](https://stuff.mit.edu/afs/sipb/project/android/docs/training/notify-user/managing.html)

### Anti-Patterns to Avoid

- **Creating new RecvTransport per channel:** mediasoup supports multiple consumers on single transport - creating separate transports wastes resources and complicates state management
- **Manual PCM audio mixing:** Android AudioFlinger and mediasoup handle mixing automatically - manual buffer mixing is unnecessary and error-prone
- **Rebuilding entire notification:** Use same notification ID to update existing notification, rebuilding creates performance overhead and can cause service restart
- **Optimistic state updates for multi-channel:** Current single-channel architecture waits for server confirmation before state change - maintain this pattern for reliability in multi-channel scenario
- **Using remember for channel list:** Channel state must survive configuration changes - use StateFlow in ViewModel, not remember in Composable

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Audio mixing from multiple sources | Custom PCM buffer mixer | mediasoup Consumer volume control + Android AudioFlinger | AudioFlinger mixes at hardware level, mediasoup Consumer has built-in volume setter (0.0-1.0), handles clipping/normalization automatically |
| Multi-consumer audio playback | Separate RecvTransport per channel | Multiple Consumers on single RecvTransport | mediasoup architecture designed for this - single transport handles up to 500+ consumers efficiently |
| Delayed state updates in Compose | Manual Timer + Handler | LaunchedEffect with delay() and rememberUpdatedState | Compose-native, lifecycle-aware, cancellation-safe, avoids memory leaks from Handler |
| Set persistence | JSON serialization of List | DataStore stringSetPreferencesKey | Type-safe, atomic writes, Flow-based, handles Set<String> natively without manual serialization |
| Scan mode target calculation | Manual listener pattern | derivedStateOf or combine operator | Recomposition-optimized, declarative, automatically updates when dependencies change |

**Key insight:** Multi-channel monitoring is a well-solved pattern in WebRTC libraries. mediasoup's architecture explicitly supports multiple consumers per transport, with built-in bandwidth management and volume control. Attempting to build custom audio mixing or per-channel transport management introduces unnecessary complexity and performance issues.

## Common Pitfalls

### Pitfall 1: Creating Separate RecvTransport Per Channel

**What goes wrong:** Creating one RecvTransport for each monitored channel wastes resources, complicates cleanup, and defeats mediasoup's scalability design

**Why it happens:** Developers assume one transport per channel mirrors the single-channel pattern from Phase 7, not realizing mediasoup transports are designed for multiple consumers

**How to avoid:** Create single RecvTransport on first channel join, create multiple Consumers on same transport for each channel's active speaker

**Warning signs:**
- Multiple `createRecvTransport()` calls in ChannelRepository
- Transport cleanup logic iterating over channel list
- Memory usage growing linearly with channel count beyond expected consumer overhead

**Correct pattern:**
```kotlin
// WRONG: One transport per channel
channelIds.forEach { channelId ->
    mediasoupClient.createRecvTransport(channelId)  // Creates N transports
}

// CORRECT: One transport, multiple consumers
mediasoupClient.createRecvTransport(firstChannelId)  // Once
channelIds.forEach { channelId ->
    observeSpeakerChangesForChannel(channelId)  // Creates consumers on same transport
}
```

**Source:** [mediasoup Scalability documentation](https://mediasoup.org/documentation/v3/scalability/) - "A mediasoup C++ subprocess can handle over ~500 consumers in total"

### Pitfall 2: Forgetting to Close Consumers on Mute (Bandwidth Leak)

**What goes wrong:** Muted channels continue consuming bandwidth from server because consumer remains active, defeating the "save bandwidth" purpose of mute

**Why it happens:** Developer updates UI mute state but forgets to close mediasoup consumer, assuming mute is just a volume change

**How to avoid:** Explicitly call `mediasoupClient.closeConsumer(consumerId)` when muting, clear consumer ID from state. On unmute, re-consume if speaker active.

**Warning signs:**
- Network traffic remains constant when muting channels
- Server logs show consumers for muted channels
- User reports no bandwidth savings from mute feature

**Correct pattern:**
```kotlin
// User decision: "Muting a channel triggers server-side unsubscribe"
suspend fun muteChannel(channelId: String) {
    // Close ALL consumers for this channel
    channelConsumers[channelId]?.forEach { (_, consumerId) ->
        mediasoupClient.closeConsumer(consumerId)  // Stops bandwidth
    }
    channelConsumers[channelId]?.clear()

    updateChannelState(channelId) { it.copy(isMuted = true) }
}

suspend fun unmuteChannel(channelId: String) {
    updateChannelState(channelId) { it.copy(isMuted = false) }

    // Re-consume if speaker is active (with rejoin delay)
    val state = monitoredChannels.value[channelId]
    state?.currentSpeaker?.let { speaker ->
        state.consumerId?.let { producerId ->
            mediasoupClient.consumeAudio(producerId, speaker.id)
        }
    }
}
```

### Pitfall 3: Notification Update Causing Service Restart

**What goes wrong:** Calling `startForeground()` with different notification ID or rebuilding notification from scratch causes service to stop and restart, interrupting audio

**Why it happens:** Developer treats notification update like creating a new notification, not understanding that foreground services require stable notification ID

**How to avoid:** Always use same NOTIFICATION_ID constant. Update notification content using `NotificationManager.notify(SAME_ID, updatedNotification)`, never call `startForeground()` again after initial service start.

**Warning signs:**
- Audio glitches when primary channel changes
- Service lifecycle logs showing onDestroy -> onCreate cycles
- Notification flickers or briefly disappears

**Correct pattern:**
```kotlin
// WRONG: New notification ID or startForeground again
fun updateChannelName(name: String) {
    val notification = buildNotification(name)
    startForeground(NEW_NOTIFICATION_ID, notification)  // Causes restart!
}

// CORRECT: Update via NotificationManager with same ID
fun updateChannelName(name: String) {
    val notification = buildNotification(name)
    notificationManager.notify(NOTIFICATION_ID, notification)  // Updates in place
}
```

**Source:** [Update Foreground Service Notification - B4X Forum](https://www.b4x.com/android/forum/threads/update-foreground-service-notification-message.110979/) - "It is important to update the existing notification by ID, otherwise the foreground service would stop and restart"

### Pitfall 4: LaunchedEffect Restarting on Primary Channel Change

**What goes wrong:** Scan mode return delay timer restarts every time primary channel changes, preventing timer from ever completing

**Why it happens:** LaunchedEffect includes primaryChannelId as key, causing coroutine cancellation and restart when primary changes

**How to avoid:** Use rememberUpdatedState to capture latest primary channel ID without restarting effect. Only use speaker change (null -> non-null -> null) as LaunchedEffect key.

**Warning signs:**
- Bottom bar never returns to primary channel after transmission ends
- Logs show delay timer being cancelled repeatedly
- Changing primary channel during scan mode breaks auto-return

**Correct pattern:**
```kotlin
// WRONG: primaryChannelId in key causes restarts
LaunchedEffect(currentSpeaker, primaryChannelId) {  // Restarts when primary changes!
    if (currentSpeaker == null) {
        delay(returnDelaySeconds * 1000L)
        returnToPrimary(primaryChannelId)
    }
}

// CORRECT: rememberUpdatedState captures latest without restart
val currentPrimary by rememberUpdatedState(primaryChannelId)
LaunchedEffect(currentSpeaker) {  // Only restarts on speaker change
    if (currentSpeaker == null && currentPrimary != null) {
        delay(returnDelaySeconds * 1000L)
        returnToPrimary(currentPrimary)  // Reads latest primary
    }
}
```

**Source:** [Understanding rememberUpdatedState in Jetpack Compose](https://medium.com/@gaganraghunath99/understanding-rememberupdatedstate-in-jetpack-compose-14cd95aa71d9)

### Pitfall 5: Not Handling "Most Recent" Speaker Logic When Multiple Channels Active

**What goes wrong:** When multiple non-primary channels have active speakers, bottom bar shows random channel instead of most recently started transmission

**Why it happens:** Developer filters for active speakers but doesn't track speaker start timestamps for sorting

**How to avoid:** Add `speakerStartTime: Long` to ChannelMonitoringState, update when speaker changes, sort by this timestamp descending when multiple active.

**Warning signs:**
- Bottom bar switches between channels unpredictably when multiple people talking
- User reports scan mode not showing "latest" speaker
- Manual testing shows wrong channel priority

**Correct pattern:**
```kotlin
// Add timestamp to state
data class ChannelMonitoringState(
    // ... other fields
    val currentSpeaker: User?,
    val speakerStartTime: Long = 0L  // System.currentTimeMillis() when speaker started
)

// Update timestamp on speaker change
private fun handleSpeakerChanged(channelId: String, speaker: User?) {
    val now = System.currentTimeMillis()
    updateChannelState(channelId) { state ->
        state.copy(
            currentSpeaker = speaker,
            speakerStartTime = if (speaker != null) now else 0L
        )
    }
}

// Sort by most recent when calculating scan target
fun calculateScanTarget(channels: Map<String, ChannelMonitoringState>, primaryId: String?): String? {
    val activeNonPrimary = channels.values
        .filter { it.currentSpeaker != null && !it.isPrimary }
        .sortedByDescending { it.speakerStartTime }  // Most recent first

    return activeNonPrimary.firstOrNull()?.channelId ?: primaryId
}
```

**User decision:** "When multiple non-primary channels are active simultaneously, bottom bar shows the most recently started transmission"

## Code Examples

Verified patterns for multi-channel implementation:

### Multi-Channel Speaker Observation

```kotlin
// Observe speaker changes for multiple channels simultaneously
// Each channel gets its own coroutine collecting SPEAKER_CHANGED broadcasts
private fun observeSpeakerChangesForChannel(channelId: String) {
    scope.launch {
        signalingClient.messages
            .filter { it.type == SignalingType.SPEAKER_CHANGED }
            .collect { message ->
                val data = message.data ?: return@collect
                val messageChannelId = data["channelId"] as? String

                // Only process for this channel
                if (messageChannelId == channelId) {
                    val speakerUserId = data["speakerUserId"] as? String
                    val speakerName = data["speakerName"] as? String
                    val producerId = data["producerId"] as? String

                    if (speakerUserId != null && producerId != null) {
                        // Speaker started
                        handleSpeakerStarted(channelId, User(speakerUserId, speakerName), producerId)
                    } else {
                        // Speaker stopped
                        handleSpeakerStopped(channelId)
                    }
                }
            }
    }
}

private suspend fun handleSpeakerStarted(
    channelId: String,
    speaker: User,
    producerId: String
) {
    val state = _monitoredChannels.value[channelId] ?: return

    // Play RX squelch (per-channel, helps distinguish which channel active)
    if (!pttManager.pttState.value is PttState.Transmitting) {
        tonePlayer.playRxSquelchOpen()
    }

    // Close previous consumer for this channel if exists
    channelConsumers[channelId]?.get(producerId)?.let { oldConsumerId ->
        mediasoupClient.closeConsumer(oldConsumerId)
    }

    // Consume audio if not muted
    if (!state.isMuted) {
        mediasoupClient.consumeAudio(producerId, speaker.id)

        // Track consumer for this channel
        if (channelConsumers[channelId] == null) {
            channelConsumers[channelId] = mutableMapOf()
        }
        channelConsumers[channelId]!![producerId] = producerId  // consumerId == producerId in this pattern
    }

    // Update state
    _monitoredChannels.value = _monitoredChannels.value + (channelId to state.copy(
        currentSpeaker = speaker,
        speakerStartTime = System.currentTimeMillis(),
        consumerId = producerId
    ))
}
```

### Bottom Bar with Scan Mode Display

```kotlin
@Composable
fun BottomBar(
    displayedChannelId: String?,
    displayedChannelName: String,
    isPrimaryChannel: Boolean,
    isLocked: Boolean,
    currentSpeaker: User?,
    pttState: PttState,
    onToggleLock: () -> Unit,
    onPttPressed: () -> Unit,
    onPttReleased: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onToggleLock() },  // Tap to lock/unlock
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Channel name with color indicating primary vs scanned
                Text(
                    text = displayedChannelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPrimaryChannel) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary  // Cyan for scanned channel
                    }
                )

                // Status indicator
                when {
                    pttState is PttState.Transmitting -> {
                        Text(
                            text = "Transmitting...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F)
                        )
                    }
                    currentSpeaker != null -> {
                        Text(
                            text = currentSpeaker.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    isLocked -> {
                        Text(
                            text = "Locked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            text = "Listening...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            PttButton(
                pttState = pttState,
                onPttPressed = onPttPressed,
                onPttReleased = onPttReleased
            )
        }
    }
}
```

### Scan Mode Return Logic with rememberUpdatedState

```kotlin
@Composable
fun ChannelListScreen(
    viewModel: ChannelListViewModel = hiltViewModel()
) {
    val monitoredChannels by viewModel.monitoredChannels.collectAsState()
    val displayedChannelId by viewModel.displayedChannelId.collectAsState()
    val primaryChannelId by viewModel.primaryChannelId.collectAsState()
    val scanReturnDelay by viewModel.scanReturnDelay.collectAsState()
    val scanModeEnabled by viewModel.scanModeEnabled.collectAsState()

    // Find displayed channel state
    val displayedChannel = monitoredChannels[displayedChannelId]
    val currentSpeaker = displayedChannel?.currentSpeaker

    // Scan mode return effect (delayed return to primary when speaker stops)
    if (scanModeEnabled && !viewModel.scanModeLocked.value) {
        val currentPrimary by rememberUpdatedState(primaryChannelId)
        val currentDisplayed by rememberUpdatedState(displayedChannelId)

        LaunchedEffect(currentSpeaker) {
            // When speaker stops and we're showing non-primary, return to primary after delay
            if (currentSpeaker == null && currentDisplayed != null && currentDisplayed != currentPrimary) {
                delay(scanReturnDelay * 1000L)
                viewModel.returnToPrimaryChannel()
            }
        }
    }

    // ... rest of UI
}
```

### Multi-Channel Volume Control

```kotlin
// Per-channel volume setting (0-100% slider in channel settings)
fun setChannelVolume(channelId: String, volumePercent: Int) {
    val volume = (volumePercent / 100f).coerceIn(0f, 1f)

    // Update state
    _monitoredChannels.value[channelId]?.let { state ->
        _monitoredChannels.value = _monitoredChannels.value + (channelId to state.copy(volume = volume))
    }

    // Apply to active consumer
    channelConsumers[channelId]?.forEach { (_, consumerId) ->
        mediasoupClient.setConsumerVolume(consumerId, volume)
    }
}

// Audio mix mode: equal volume vs primary priority
fun applyAudioMixMode(mode: AudioMixMode) {
    val primaryId = _primaryChannelId.value

    _monitoredChannels.value.forEach { (channelId, state) ->
        val volume = when (mode) {
            AudioMixMode.EQUAL_VOLUME -> state.volume  // Use per-channel volume
            AudioMixMode.PRIMARY_PRIORITY -> {
                if (channelId == primaryId) {
                    state.volume  // Full volume for primary
                } else {
                    state.volume * 0.5f  // Quieter for non-primary
                }
            }
        }

        channelConsumers[channelId]?.forEach { (_, consumerId) ->
            mediasoupClient.setConsumerVolume(consumerId, volume)
        }
    }
}
```

### "Mute All Except Primary" Quick Action

```kotlin
// Top bar quick action for noisy multi-channel situations
fun muteAllExceptPrimary() {
    val primaryId = _primaryChannelId.value

    scope.launch {
        _monitoredChannels.value.forEach { (channelId, state) ->
            if (channelId != primaryId && !state.isMuted) {
                muteChannel(channelId)
            }
        }
    }
}

// Restore all channels to unmuted (undo quick action)
fun unmuteAllChannels() {
    scope.launch {
        _monitoredChannels.value.forEach { (channelId, state) ->
            if (state.isMuted) {
                unmuteChannel(channelId)
            }
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SharedPreferences for settings | DataStore with stringSetPreferencesKey | 2020 (DataStore release) | Type-safe Set<String> persistence without manual JSON serialization |
| remember for screen state | StateFlow in ViewModel | Compose 1.0+ | Survives configuration changes, shared across composables |
| Manual timer with Handler | LaunchedEffect with delay() | Compose 1.0+ | Lifecycle-aware, cancellation-safe, no memory leaks |
| Custom audio mixing | WebRTC Consumer volume + Android AudioFlinger | WebRTC standard | Hardware-accelerated mixing, automatic clipping prevention |
| Separate transport per channel | Multiple consumers on single transport | mediasoup v3 design | Scalable to 500+ consumers, reduced resource overhead |

**Deprecated/outdated:**
- **SharedPreferences for structured data**: DataStore provides typed keys including stringSetPreferencesKey for Set<String>, making JSON serialization of channel lists unnecessary
- **rememberSaveable for channel list**: Too large for Bundle (5 channels with metadata exceeds Bundle size limits), use ViewModel StateFlow with DataStore persistence instead
- **MediaPlayer for mixing**: Creates separate AudioTrack per instance, requires manual mixing - use WebRTC Consumer with built-in volume control
- **Manual notification rebuilding**: NotificationCompat.Builder creates new object each time - update existing notification by ID via NotificationManager.notify()

## Open Questions

### 1. Consumer Volume Control API in libmediasoup-android

**What we know:**
- Standard mediasoup-client (JavaScript) has `consumer.volume` setter (0.0-1.0 range)
- crow-misia/libmediasoup-android is Kotlin wrapper for native mediasoup-client-android
- Documentation doesn't explicitly show volume control API

**What's unclear:**
- Whether libmediasoup-android 0.7.0 exposes Consumer.setVolume() method
- If volume control is available, whether it's per-consumer or global

**Recommendation:**
- Verify Consumer API in libmediasoup-android during implementation (Plan 01)
- If volume control not exposed, fallback: implement in Plan 02 by wrapping AudioTrack output with VolumeShaper (Android 8.0+)
- File issue/PR with crow-misia if volume setter missing but available in native library

**Impact:** Low - volume control is user enhancement (per-channel volume, primary priority mode), not core functionality. Scan mode and multi-channel monitoring work without volume adjustment.

### 2. Optimal Consumer Cleanup Timing on Channel Leave

**What we know:**
- Current single-channel pattern closes consumer on leave
- Multi-channel requires closing multiple consumers per channel
- mediasoup recommends closing consumers before transport

**What's unclear:**
- Whether to close consumers before or after sending LEAVE_CHANNEL to server
- If closing consumers first causes server-side cleanup issues

**Recommendation:**
- Follow existing single-channel pattern: close consumers locally first, then send LEAVE_CHANNEL
- Server should handle graceful consumer closure even if client closes first (defensive programming)
- Monitor server logs during testing for consumer cleanup warnings

**Impact:** Low - affects cleanup order only, both orders should work if server handles race conditions

### 3. DataStore Migration Strategy for Existing Single-Channel Users

**What we know:**
- Phase 7 doesn't persist joined channel (single-channel is transient)
- Phase 8 adds monitored_channel_ids Set persistence
- No existing data to migrate

**What's unclear:**
- Whether to auto-join user's last active channel on first Phase 8 launch
- How to handle users who had Phase 7 app in foreground when Phase 8 updates

**Recommendation:**
- No migration needed - Phase 8 starts with empty monitored channels set
- User manually joins channels (same UX as Phase 7 first launch)
- Update service to handle empty channel set gracefully (don't crash if no channels monitored on app startup)

**Impact:** Very Low - clean slate approach, no backward compatibility issues

## Sources

### Primary (HIGH confidence)
- [DataStore stringSetPreferencesKey documentation](https://androidx.github.io/kmp-eap-docs/libs/androidx.datastore/datastore-preferences-core/androidx.datastore.preferences.core/string-set-preferences-key.html) - Verified Set<String> persistence API
- [Jetpack Compose Side-effects documentation](https://developer.android.com/develop/ui/compose/side-effects) - LaunchedEffect and rememberUpdatedState patterns
- [mediasoup Scalability documentation](https://mediasoup.org/documentation/v3/scalability/) - Multi-consumer on single transport architecture
- [Android AudioTrack API reference](https://developer.android.com/reference/android/media/AudioTrack) - Audio mixing behavior via AudioFlinger
- [Hilt ViewModel scoping documentation](https://dagger.dev/hilt/view-model.html) - @Singleton and @ActivityRetainedScoped patterns

### Secondary (MEDIUM confidence)
- [Understanding rememberUpdatedState in Jetpack Compose](https://medium.com/@gaganraghunath99/understanding-rememberupdatedstate-in-jetpack-compose-14cd95aa71d9) - Timer pattern without effect restart
- [Updating Notifications - Android Developers](https://stuff.mit.edu/afs/sipb/project/android/docs/training/notify-user/managing.html) - Update by ID to avoid service restart
- [mediasoup discourse: multiple consumers](https://mediasoup.discourse.group/t/using-multiple-consumers-in-a-single-recvtransport/375) - Community verification of single transport pattern
- [Police Scanner Multi-Channel app](https://www.intercomsonline.com/2-way-radio-priority-channel-scan) - Real-world scan mode UX pattern (priority channel scanning)

### Tertiary (LOW confidence, marked for validation)
- [WebRTC multiple audio tracks mixing](https://webrtchacks.com/web-audio-conference/) - Browser-side Web Audio API mixing (not directly applicable to Android native, but confirms multi-track playback pattern)
- [Android audio mixing discussion](https://geek-answers.github.io/articles/650077/index.html) - Community discussion of AudioTrack mixing, confirms AudioFlinger handles it

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries already in project, stringSetPreferencesKey verified in DataStore docs
- Architecture: HIGH - Patterns verified in official Android/Compose/mediasoup documentation
- Pitfalls: HIGH - Based on verified anti-patterns from official docs and community discussions with specific sources

**Research date:** 2026-02-11
**Valid until:** 2026-03-13 (30 days - stable libraries, no fast-moving dependencies)

**Key assumptions verified:**
1. mediasoup single RecvTransport supports multiple consumers - VERIFIED ([mediasoup Scalability](https://mediasoup.org/documentation/v3/scalability/))
2. DataStore stringSetPreferencesKey exists - VERIFIED ([DataStore API docs](https://androidx.github.io/kmp-eap-docs/libs/androidx.datastore/datastore-preferences-core/androidx.datastore.preferences.core/string-set-preferences-key.html))
3. rememberUpdatedState prevents LaunchedEffect restart - VERIFIED ([Compose Side-effects docs](https://developer.android.com/develop/ui/compose/side-effects))
4. Notification update by ID doesn't restart service - VERIFIED ([Android Notifications docs](https://stuff.mit.edu/afs/sipb/project/android/docs/training/notify-user/managing.html))

**Unverified claims requiring implementation validation:**
- Consumer volume control API in libmediasoup-android 0.7.0 (not documented, need to check source/test)
- Exact audio mixing behavior when 5 channels simultaneously active (need physical device testing)
