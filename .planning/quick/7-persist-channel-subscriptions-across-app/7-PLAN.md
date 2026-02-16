---
phase: quick-7
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
  - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
autonomous: true
must_haves:
  truths:
    - "User closes app and reopens it — same channels are joined automatically"
    - "User opens app from notification after background — same channels are active"
    - "User explicitly logs out — channels are NOT restored on next login"
    - "User switches event — channels from old event are NOT restored"
  artifacts:
    - path: "android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt"
      provides: "disconnectAll split into preserve-state and clear-state variants"
    - path: "android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt"
      provides: "auto-rejoin logic reading persisted channel IDs on init"
  key_links:
    - from: "ChannelListViewModel.init"
      to: "SettingsRepository.getMonitoredChannels()"
      via: "Flow.first() read on startup"
      pattern: "getMonitoredChannels.*first"
    - from: "ChannelListViewModel.init"
      to: "ChannelRepository.joinChannel()"
      via: "loop over persisted channel IDs"
      pattern: "joinChannel.*channelId"
    - from: "ChannelListViewModel.onCleared()"
      to: "ChannelRepository.disconnectAllPreservingState()"
      via: "preserving variant that skips clearMonitoredChannels"
      pattern: "disconnectAllPreservingState"
---

<objective>
Persist channel subscriptions across app restart and background resume.

Purpose: When the user closes and reopens the app (or returns from background via notification),
the app should automatically rejoin all channels from the last session. Same UX as a dedicated
radio -- turn it off and back on, same channels are selected. Explicit logout or event-switch
should still clear the saved channels.

Output: Modified ChannelRepository and ChannelListViewModel with auto-rejoin on startup.
</objective>

<execution_context>
@.planning/quick/7-persist-channel-subscriptions-across-app/7-PLAN.md
</execution_context>

<context>
@android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt
@android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
@android/app/src/main/java/com/voiceping/android/data/storage/SettingsRepository.kt
@android/app/src/main/java/com/voiceping/android/data/repository/EventRepository.kt
@android/app/src/main/java/com/voiceping/android/domain/model/Channel.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Split disconnectAll into preserve-state and clear-state variants</name>
  <files>android/app/src/main/java/com/voiceping/android/data/repository/ChannelRepository.kt</files>
  <files>android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt</files>
  <action>
In ChannelRepository.kt:

1. Rename the existing `disconnectAll()` method to `disconnectAllPreservingState()`. This is the
   variant called when the app is closing or going to background. It should do everything the
   current `disconnectAll()` does EXCEPT:
   - Do NOT call `settingsRepository.clearMonitoredChannels()`
   - Do NOT send LEAVE_CHANNEL to server for each channel (the WebSocket disconnect handles this)
   - Still clear the in-memory maps (_monitoredChannels, _primaryChannelId, channelConsumers, etc.)
   - Still stop services, cancel jobs, release resources (audio, wake lock, location, etc.)

   Specifically, replace the coroutine block at the end that calls `leaveChannel` for each channel.
   Instead, just clear in-memory state directly:
   ```
   // Clear in-memory state without server communication or persistence clearing
   channelConsumers.clear()
   _monitoredChannels.value = emptyMap()
   _primaryChannelId.value = null
   ```

2. Create a new `disconnectAllAndClearState()` method that does what the old `disconnectAll()` did:
   calls `disconnectAllPreservingState()` first, then in a coroutine calls
   `settingsRepository.clearMonitoredChannels()`.

In ChannelListViewModel.kt:

3. Change `onCleared()` to call `channelRepository.disconnectAllPreservingState()` instead of
   `channelRepository.disconnectAll()`.

4. The existing logout flow in NavGraph.kt navigates to login which recreates the whole nav stack.
   Logout must clear persisted channels. Find where logout is triggered -- it goes through
   `onLogout` callback which navigates to LOGIN route, popping everything. The ChannelListViewModel
   is destroyed (onCleared called), but we need an explicit clear. Add a `logout()` method to
   ChannelListViewModel that calls `channelRepository.disconnectAllAndClearState()` and invoke it
   from the `onLogout` lambda. Similarly for `onSwitchEvent`.

   In ChannelListViewModel, add:
   ```kotlin
   fun logout() {
       channelRepository.disconnectAllAndClearState()
   }

   fun switchEvent() {
       channelRepository.disconnectAllAndClearState()
   }
   ```

   Note: The ChannelListScreen already has `onLogout` and `onSwitchEvent` callbacks. We need to
   call `viewModel.logout()` / `viewModel.switchEvent()` BEFORE navigating away. Update
   ChannelListScreen.kt's onSwitchEvent and onLogout lambdas in the ProfileDrawer to call
   the viewModel methods first. Specifically:

   In ChannelListScreen.kt, change the ProfileDrawer callbacks:
   - `onSwitchEvent`: call `viewModel.switchEvent()` then `onSwitchEvent()`
   - `onLogout`: call `viewModel.logout()` then `onLogout()`
  </action>
  <verify>
`cd /home/earthworm/Github-repos/voiceping-router/android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileDebugKotlin` passes without errors.
Verify:
- `disconnectAllPreservingState()` exists and does NOT call `clearMonitoredChannels`
- `disconnectAllAndClearState()` exists and DOES call `clearMonitoredChannels`
- `onCleared()` calls `disconnectAllPreservingState()`
- `logout()` and `switchEvent()` call `disconnectAllAndClearState()`
  </verify>
  <done>
App close/background preserves persisted channel IDs in DataStore. Logout and switch-event clear them.
  </done>
</task>

<task type="auto">
  <name>Task 2: Auto-rejoin persisted channels on ChannelListViewModel init</name>
  <files>android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt</files>
  <action>
In ChannelListViewModel.kt, add auto-rejoin logic in the `init` block, after the existing
`eventId?.let { loadChannels(it) }` call.

The auto-rejoin must:

1. Read persisted channel IDs from `settingsRepository.getMonitoredChannels()` (returns Flow,
   use `.first()` to get current value).
2. Read persisted primary channel ID from `settingsRepository.getPrimaryChannel()` (returns Flow,
   use `.first()` to get current value).
3. If the persisted set is non-empty, launch a coroutine that:
   a. Waits for `_channels` to be non-empty (the channel list needs to load first so we have
      channel names and team names for `joinChannel`). Use `_channels.first { it.isNotEmpty() }`.
      Add a timeout of 10 seconds -- if channels don't load, skip auto-rejoin (offline cache
      should make this rare).
   b. For each persisted channel ID, find the matching Channel object in `_channels.value` to
      get the name and teamName.
   c. Call `channelRepository.joinChannel(channelId, channelName, teamName)` for each, in order.
      Join the persisted primary channel FIRST (so it becomes the primary again).
   d. After all channels are joined, if the persisted primary differs from the current primary
      (because join order set a different one as primary), call
      `channelRepository.setPrimaryChannel(persistedPrimaryId)`.
   e. Log: "Auto-rejoined N channels from previous session"

Important details:
- Wrap the entire auto-rejoin in a try/catch. If it fails (e.g., channels were deleted from event),
  log the error and clear persisted channels via `settingsRepository.clearMonitoredChannels()`.
- If a persisted channel ID is not found in the loaded channel list (event was reconfigured),
  skip that channel and log a warning. Don't fail the whole rejoin.
- Use `kotlinx.coroutines.withTimeoutOrNull` for the channels-loaded wait.

The code should look approximately like:
```kotlin
// Auto-rejoin persisted channels from previous session
viewModelScope.launch {
    try {
        val persistedChannelIds = settingsRepository.getMonitoredChannels().first()
        val persistedPrimaryId = settingsRepository.getPrimaryChannel().first()

        if (persistedChannelIds.isEmpty()) return@launch

        Log.d(TAG, "Found ${persistedChannelIds.size} persisted channels, waiting for channel list...")

        // Wait for channel list to load (with timeout)
        val loadedChannels = withTimeoutOrNull(10_000L) {
            _channels.first { it.isNotEmpty() }
        }

        if (loadedChannels == null) {
            Log.w(TAG, "Channel list did not load in time, skipping auto-rejoin")
            return@launch
        }

        val channelMap = loadedChannels.associateBy { it.id }

        // Join primary channel first (so it becomes primary)
        val orderedIds = if (persistedPrimaryId != null && persistedPrimaryId in persistedChannelIds) {
            listOf(persistedPrimaryId) + (persistedChannelIds - persistedPrimaryId)
        } else {
            persistedChannelIds.toList()
        }

        var joinedCount = 0
        for (channelId in orderedIds) {
            val channel = channelMap[channelId]
            if (channel == null) {
                Log.w(TAG, "Persisted channel $channelId not found in event, skipping")
                continue
            }
            val result = channelRepository.joinChannel(channelId, channel.name, channel.teamName)
            if (result.isSuccess) {
                joinedCount++
            } else {
                Log.w(TAG, "Failed to rejoin channel ${channel.name}: ${result.exceptionOrNull()?.message}")
            }
        }

        // Restore primary if needed
        if (persistedPrimaryId != null && channelRepository.primaryChannelId.value != persistedPrimaryId
            && persistedPrimaryId in channelRepository.monitoredChannels.value) {
            channelRepository.setPrimaryChannel(persistedPrimaryId)
        }

        Log.d(TAG, "Auto-rejoined $joinedCount/${persistedChannelIds.size} channels from previous session")
    } catch (e: Exception) {
        Log.e(TAG, "Auto-rejoin failed", e)
    }
}
```

Add the required import: `import kotlinx.coroutines.withTimeoutOrNull`
  </action>
  <verify>
`cd /home/earthworm/Github-repos/voiceping-router/android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileDebugKotlin` passes without errors.
Verify:
- ChannelListViewModel.init contains auto-rejoin coroutine
- It reads from settingsRepository.getMonitoredChannels()
- It waits for channels to load before joining
- Primary channel is joined first and restored
  </verify>
  <done>
On app restart, the ChannelListViewModel automatically reads persisted channel subscriptions
from DataStore and rejoins them in order, restoring the primary channel. The user sees the
same channels selected as before they closed the app.
  </done>
</task>

</tasks>

<verification>
1. Build: `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew compileDebugKotlin` passes
2. Code review: `disconnectAllPreservingState` does not touch DataStore
3. Code review: `disconnectAllAndClearState` does clear DataStore
4. Code review: `onCleared` calls preserving variant
5. Code review: logout/switchEvent call clearing variant
6. Code review: Auto-rejoin reads persisted IDs and calls joinChannel for each
7. Code review: Primary channel is restored correctly
</verification>

<success_criteria>
- App closes and reopens: same channels are automatically joined (radio behavior)
- App returns from background via notification: channels are still active (foreground service handles this, auto-rejoin is a safety net)
- User logs out: channels are cleared, not restored on next login
- User switches event: channels from old event are cleared
- Build compiles successfully
</success_criteria>

<output>
After completion, create `.planning/quick/7-persist-channel-subscriptions-across-app/7-SUMMARY.md`
</output>
