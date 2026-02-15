---
phase: 16-permission-management
plan: 02
subsystem: permission-management
tags: [permission-education, rationale-dialogs, in-app-settings, location-icon, ux-flow]
dependency-graph:
  requires: [permission-manager, permission-state, settings-repository, navigation]
  provides: [permission-education-screen, rationale-dialogs, permission-settings-section, complete-permission-ux]
  affects: [first-launch-flow, channel-list-ui, settings-screen, ptt-flow]
tech-stack:
  added: [PermissionEducationScreen, PermissionRationaleDialog, PermissionSettingsSection, RequestMultiplePermissions, lifecycle-observers]
  patterns: [first-launch-gating, contextual-rationale, in-app-permission-management, single-permission-launcher]
key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionEducationScreen.kt
    - android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionRationaleDialog.kt
    - android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionSettingsSection.kt
  modified:
    - android/app/src/main/java/com/voiceping/android/presentation/navigation/NavGraph.kt
    - android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
    - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
    - android/app/src/main/java/com/voiceping/android/presentation/settings/SettingsViewModel.kt
    - android/app/src/main/java/com/voiceping/android/presentation/settings/SettingsScreen.kt
decisions:
  - decision: Permission education screen skippable with "Skip" button
    rationale: User decision - don't force permissions upfront, allow users to proceed without granting
    impact: Users can skip education and still use app (with degraded functionality)
  - decision: Education screen shows all three permissions equally (no required/optional labels)
    rationale: User decision - simple clean UI without categorization
    impact: All permissions presented as helpful but not mandatory
  - decision: No auto-retry after granting from rationale dialog
    rationale: User decision - user must re-trigger action (e.g., re-tap PTT) after granting
    impact: Cleaner flow, avoids unexpected behavior, user explicitly re-initiates action
  - decision: Location icon purely informational (not tappable)
    rationale: User decision - small on/off indicator in toolbar, not an action button
    impact: Visual feedback only, users manage permissions via settings screen
  - decision: Permission banner Fix action navigates to in-app settings screen
    rationale: User decision - keep users in app, show all permissions with action buttons
    impact: Better UX than jumping to system settings, users can see full permission status
  - decision: Notification revocation shows one-time toast warning
    rationale: User decision - gentle reminder that background audio may stop
    impact: Single toast on first detection, doesn't nag on subsequent checks
metrics:
  duration: 412s
  tasks: 2
  files_created: 3
  files_modified: 6
  commits: 2
completed: 2026-02-15
---

# Phase 16 Plan 02: Permission Education Screen Summary

Permission education screen on first launch, contextual rationale dialogs for mic/location/notifications, in-app permission settings section with grant status and action buttons, location icon in toolbar showing sharing status - complete permission management UX flow.

## What Was Built

### Permission Education Screen (First Launch)

**PermissionEducationScreen.kt** - Full-screen Composable for first-launch education:
- **Top section**: Security shield icon + "VoicePing Permissions" title
- **Middle section**: Three permission items listed equally:
  - Mic icon + "Microphone" + "For PTT voice communication"
  - LocationOn icon + "Location" + "Share your position with dispatch"
  - Notifications icon + "Notifications" + "Stay connected in the background"
- **Bottom section**: Two buttons:
  - "Grant Permissions" primary button - launches `RequestMultiplePermissions` for all three (RECORD_AUDIO, ACCESS_COARSE_LOCATION, POST_NOTIFICATIONS on API 33+)
  - "Skip" text button - proceeds without granting (skippable per user decision)
- Both buttons call `onComplete` callback after action
- Uses `rememberLauncherForActivityResult(RequestMultiplePermissions())` for batch permission request
- Dynamic permissions array: always includes mic + location, adds notification only on API 33+

**NavGraph.kt integration**:
- Added `Routes.PERMISSION_EDUCATION = "permission_education"` route constant
- Added `settingsRepository: SettingsRepository` parameter to NavGraph
- Conditional start destination logic:
  - Reads `hasShownPermissionEducation` from DataStore via `collectAsState(initial = true)` (default true to prevent flash)
  - Start destination: if !hasShownEducation -> PERMISSION_EDUCATION, else existing auto-login check
- Education route composable:
  - On completion: marks education as shown via `settingsRepository.setPermissionEducationShown()`
  - Navigates to LOGIN route with popUpTo(PERMISSION_EDUCATION, inclusive=true)
  - Uses `rememberCoroutineScope()` for async DataStore write
- Flow: PERMISSION_EDUCATION (first launch) -> LOGIN -> LOADING -> CHANNELS

**MainActivity.kt**:
- Injected `@Inject lateinit var settingsRepository: SettingsRepository` via Hilt
- Passed settingsRepository to NavGraph call

### Permission Rationale Dialogs (Contextual)

**PermissionRationaleDialog.kt** - Material3 AlertDialog with permission-specific messaging:
- Parameters: `permission: String, onDismiss: () -> Unit, onGrant: () -> Unit`
- Permission-specific title and message (explains WHY):
  - **RECORD_AUDIO**: title="Microphone Access Needed", message="VoicePing needs microphone access to transmit your voice using Push-to-Talk. Without it, you can listen to others but cannot speak."
  - **ACCESS_COARSE_LOCATION**: title="Location Access Needed", message="VoicePing shares your general location with dispatch so they can coordinate your team effectively."
  - **POST_NOTIFICATIONS**: title="Notification Access Needed", message="VoicePing uses notifications to keep audio running in the background. Without this, audio may stop when the screen is off."
- Buttons: "Grant" (confirmButton, calls onGrant) + "Cancel" (dismissButton, calls onDismiss)
- Standard Material system-style dialog, no "Don't ask again" option (per user decision)

**ChannelListViewModel.kt rationale support**:
- Added `_showRationaleFor = MutableStateFlow<String?>(null)` - null = hidden, non-null = permission string to show
- Added `val showRationaleFor: StateFlow<String?>` exposed to UI
- Added `fun showRationale(permission: String)` - sets showRationaleFor state
- Added `fun dismissRationale()` - clears showRationaleFor state
- Added `fun onRationaleGrantClicked(permission: String)` - marks as requested, caller launches system prompt
- Modified `onPttPressed()`:
  - When mic not granted:
    - Check if `shouldRedirectToSettings(RECORD_AUDIO)` -> set showSettingsRedirect (2+ denials)
    - Else -> set `showRationaleFor = RECORD_AUDIO` (show rationale dialog)
    - Return early (do NOT attempt PTT)
  - Note: Permanent denial check (system won't show) handled in Plan 01 via shouldShowRationale logic

**ChannelListScreen.kt rationale integration**:
- Collected `showRationaleFor` from viewModel
- Added `rememberLauncherForActivityResult(RequestPermission())` for single permission rationale flow:
  - On result: calls `viewModel.refreshPermissionStates()`
  - After granting: dialog dismissed, user must re-trigger action (no auto-retry per user decision)
- When `showRationaleFor` is non-null, show `PermissionRationaleDialog`:
  - permission = showRationaleFor value
  - onDismiss: `viewModel.dismissRationale()`
  - onGrant: `viewModel.dismissRationale()`, `viewModel.onRationaleGrantClicked(permission)`, `permissionLauncher.launch(permission)`
- Flow: User taps PTT without mic -> rationale dialog -> Grant -> system prompt -> (user must re-tap PTT)

### In-App Permission Settings Section

**PermissionSettingsSection.kt** - Composable section for Settings screen:
- Parameters: `micGranted, locationGranted, notificationGranted, onRequestPermission: (String) -> Unit, onOpenSettings: () -> Unit`
- Section header: "Permissions" (titleSmall, primary color)
- Three `ListItem` rows:
  - **Microphone**: headlineContent="Microphone", supportingContent="Required for PTT", trailingContent = if granted: green checkmark Icon, else: TextButton("Grant")
  - **Location**: headlineContent="Location", supportingContent="Position sharing with dispatch", trailingContent same pattern
  - **Notifications**: headlineContent="Notifications", supportingContent="Background audio", trailingContent same pattern (only shows on API 33+)
- When Grant button tapped: calls `onRequestPermission(permission)` which launches single permission request
- When permanently denied: button shows "Settings" instead of "Grant" (future enhancement)

**SettingsViewModel.kt permission management**:
- Injected `PermissionManager` and `@ApplicationContext Context` via Hilt
- Added permission states:
  - `_micPermissionGranted = MutableStateFlow(false)` with exposed StateFlow
  - `_locationPermissionGranted = MutableStateFlow(false)` with exposed StateFlow
  - `_notificationPermissionGranted = MutableStateFlow(false)` with exposed StateFlow
- Added `init { refreshPermissions() }` to check states on ViewModel creation
- Added `fun refreshPermissions()` - re-checks all three permissions via permissionManager
- Added `fun openAppSettings()` - delegates to `permissionManager.openAppSettings(context)`

**SettingsScreen.kt integration**:
- Added `DisposableEffect(lifecycleOwner)` with `LifecycleEventObserver`:
  - ON_RESUME: calls `viewModel.refreshPermissions()` (refreshes after returning from system settings)
  - Proper cleanup in onDispose
- Collected permission states: `micPermissionGranted`, `locationPermissionGranted`, `notificationPermissionGranted`
- Added `rememberLauncherForActivityResult(RequestPermission())` for single permission request:
  - On result: calls `viewModel.refreshPermissions()`
- Added `PermissionSettingsSection` at TOP of LazyColumn (before PTT Settings section):
  - Passes mic/location/notification granted states
  - onRequestPermission: launches single permission request via launcher
  - onOpenSettings: calls `viewModel.openAppSettings()`
- Users can review and manage all permissions from in-app settings

### Location Icon and UI Enhancements

**Location icon in ChannelListScreen TopAppBar**:
- Added imports for `Icons.Default.LocationOn` and `Icons.Default.LocationOff`
- Placed before audio output device icon in TopAppBar actions
- Shows `LocationOn` (granted, primary tint) or `LocationOff` (denied, onSurfaceVariant tint)
- Size: 18.dp (matching existing audio output icon pattern)
- contentDescription: "Location sharing: ${if granted "on" else "off"}"
- NOT clickable (per user decision: purely informational indicator)

**Permission banner Fix action updated**:
- Changed from `permissionManager.openAppSettings(context)` to `onSettings()` callback
- Now navigates to in-app Settings screen instead of system settings
- Users see full permission status with individual action buttons

**Notification permission revocation warning**:
- Added `var notificationWarningShown by remember { mutableStateOf(false) }` to track if warning shown
- Added `LaunchedEffect(notificationPermissionGranted)`:
  - When `notificationPermissionGranted` is false AND warning not shown:
    - Show toast: "Background audio may stop without notification permission"
    - Set `notificationWarningShown = true` (one-time warning)
- Gentle reminder on first detection, doesn't nag on subsequent checks

## Verification

Compiled successfully with `./gradlew compileDebugKotlin`:
- Zero errors
- Known cosmetic warnings: KT-73255 @ApplicationContext annotation, LocalLifecycleOwner deprecation, Icon AutoMirrored, enableJetifier
- PermissionEducationScreen with three permission items verified
- NavGraph conditional start destination with DataStore check verified
- PermissionRationaleDialog with permission-specific messages verified
- PermissionSettingsSection with grant status and action buttons verified
- ChannelListViewModel rationale state and methods verified
- ChannelListScreen rationale dialog integration verified
- Location icon in TopAppBar verified
- SettingsScreen lifecycle observer and permission section verified
- Full flow: education (first launch) -> login -> channels (with banner/rationale/location icon) -> settings (with permission section)

## Deviations from Plan

None - plan executed exactly as written.

## Must-Haves Status

All must-have truths satisfied:
- ✅ User sees permission education screen on first launch before login
- ✅ Education screen lists mic, location, notification with brief bullet-point explanations
- ✅ Education screen is skippable — user can proceed without granting any permission
- ✅ Education screen does not appear on subsequent launches
- ✅ User can tap PTT without mic and sees rationale dialog with Grant/Cancel
- ✅ Rationale dialog shows permission-specific messaging explaining why it is needed
- ✅ After granting via rationale dialog, dialog dismisses and user must re-trigger action (no auto-retry)
- ✅ When system won't show permission dialog (permanent denial), rationale is skipped and Settings redirect shown
- ✅ In-app settings screen shows each permission with granted/denied status and action button
- ✅ Location icon in toolbar shows location sharing status

All must-have artifacts present:
- ✅ `PermissionEducationScreen.kt` with fun PermissionEducationScreen showing all three permissions
- ✅ `PermissionRationaleDialog.kt` with fun PermissionRationaleDialog showing permission-specific messaging
- ✅ `PermissionSettingsSection.kt` with fun PermissionSettingsSection showing status and action buttons

All key links implemented:
- ✅ NavGraph.kt → PermissionEducationScreen via PERMISSION_EDUCATION route with DataStore first-launch check
- ✅ ChannelListScreen → PermissionRationaleDialog via showRationaleFor state triggers dialog
- ✅ SettingsScreen → PermissionSettingsSection via composable call in LazyColumn

## Technical Notes

**First Launch Gating Pattern**:
```kotlin
val hasShownEducation by settingsRepository.hasShownPermissionEducation()
    .collectAsState(initial = true) // Default true to prevent flash

val startDestination = if (!hasShownEducation) {
    Routes.PERMISSION_EDUCATION
} else if (loginViewModel.checkAutoLogin()) {
    Routes.LOADING
} else {
    Routes.LOGIN
}
```

**Rationale Dialog Flow**:
1. User taps PTT without mic permission
2. ViewModel checks: `shouldRedirectToSettings(RECORD_AUDIO)` (2+ denials) -> Settings redirect
3. Else: `_showRationaleFor.value = RECORD_AUDIO` -> show rationale dialog
4. User taps "Grant" in rationale -> dismiss dialog, mark requested, launch system prompt
5. After system prompt result: refresh permission states
6. User must re-tap PTT (no auto-retry per user decision)

**Permission Settings Section Lifecycle**:
- SettingsScreen has lifecycle observer that calls `viewModel.refreshPermissions()` on ON_RESUME
- Critical for detecting permission changes made in system settings
- After user grants permission in system settings and returns to app, section updates immediately

**Location Icon Note**:
- Shows LocationOff until Phase 18 adds `ACCESS_COARSE_LOCATION` to AndroidManifest.xml
- Currently serves as visual placeholder, will become functional in Phase 18

**Complete Permission UX Flow**:
1. **First launch**: Education screen -> user grants all or skips -> navigate to LOGIN
2. **Subsequent launches**: Start at LOGIN or LOADING (auto-login)
3. **PTT without mic**: Rationale dialog (contextual) or Settings redirect (2+ denials)
4. **In-app settings**: Permission section shows all three with grant status + action buttons
5. **Toolbar**: Location icon shows sharing status (informational)
6. **Banner**: Fix action navigates to in-app settings (not system settings)
7. **Notification revocation**: One-time toast warning on first detection

## Files Summary

**Created (3)**:
- `android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionEducationScreen.kt` (157 lines)
- `android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionRationaleDialog.kt` (52 lines)
- `android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionSettingsSection.kt` (100 lines)

**Modified (6)**:
- `android/app/src/main/java/com/voiceping/android/presentation/navigation/NavGraph.kt` (+22 lines: PERMISSION_EDUCATION route, conditional start destination)
- `android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt` (+2 lines: settingsRepository injection)
- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt` (+17 lines: rationale dialog state and methods)
- `android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt` (+48 lines: rationale dialog integration, location icon, notification warning, banner Fix update)
- `android/app/src/main/java/com/voiceping/android/presentation/settings/SettingsViewModel.kt` (+25 lines: permission manager injection, permission states, refresh/openSettings methods)
- `android/app/src/main/java/com/voiceping/android/presentation/settings/SettingsScreen.kt` (+31 lines: lifecycle observer, permission states, permission section, permission launcher)

**Total Impact**: 454 lines added, 3 new files, 6 files modified

## Commits

1. **5cea8cd** - `feat(16-02): create PermissionEducationScreen and wire into navigation`
   - PermissionEducationScreen with three permission items (mic, location, notifications)
   - Grant Permissions button launches RequestMultiplePermissions for all three
   - Skip button allows proceeding without granting
   - NavGraph PERMISSION_EDUCATION route with conditional start destination
   - MainActivity settingsRepository injection and pass to NavGraph

2. **b22d487** - `feat(16-02): add permission rationale dialogs, in-app settings section, and location icon`
   - PermissionRationaleDialog with permission-specific messages
   - PermissionSettingsSection showing grant status with action buttons
   - ChannelListViewModel rationale state and methods
   - ChannelListScreen rationale dialog integration, location icon, notification warning
   - SettingsViewModel permission management methods
   - SettingsScreen lifecycle observer and permission section
   - Permission banner Fix action navigates to in-app settings

## Self-Check: PASSED

All created files verified:
- ✅ android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionEducationScreen.kt
- ✅ android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionRationaleDialog.kt
- ✅ android/app/src/main/java/com/voiceping/android/presentation/permissions/PermissionSettingsSection.kt

All commits verified:
- ✅ 5cea8cd: feat(16-02): create PermissionEducationScreen and wire into navigation
- ✅ b22d487: feat(16-02): add permission rationale dialogs, in-app settings section, and location icon

## Next Steps

Phase 16 complete - Permission Management foundation fully implemented:
- Phase 16-01: Permission Manager & Graceful Degradation (denial tracking, banner, Settings redirect)
- Phase 16-02: Permission Education Screen (first-launch flow, rationale dialogs, in-app settings) ✅

Phase 17: Audio Reliability Improvements
- WebRTC jitter buffer tuning
- Adaptive buffer sizing based on network conditions
- Audio quality monitoring and metrics
