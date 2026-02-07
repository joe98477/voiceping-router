---
phase: 05-android-setup-webrtc
plan: 02
title: "Authentication & Login Flow"
subsystem: android-client
status: complete
completed: 2026-02-08

requires:
  - 05-01-android-project-foundation

provides:
  - Secure JWT storage with EncryptedSharedPreferences
  - Login flow with email/password authentication
  - Auto-login on app relaunch with token validation
  - Silent token refresh with 3-retry logic
  - Branded login UI with inline validation errors
  - Loading/connecting screen between login and channel list
  - Navigation logic with auto-login detection

affects:
  - 05-04: Channel list UI will use authenticated sessions
  - 05-05: WebSocket signaling will use stored JWT token

key-files:
  created:
    - android/app/src/main/java/com/voiceping/android/data/storage/TokenManager.kt
    - android/app/src/main/java/com/voiceping/android/data/storage/PreferencesManager.kt
    - android/app/src/main/java/com/voiceping/android/data/api/AuthApi.kt
    - android/app/src/main/java/com/voiceping/android/data/network/dto/LoginRequest.kt
    - android/app/src/main/java/com/voiceping/android/data/network/dto/LoginResponse.kt
    - android/app/src/main/java/com/voiceping/android/data/repository/AuthRepository.kt
    - android/app/src/main/java/com/voiceping/android/domain/usecase/LoginUseCase.kt
    - android/app/src/main/java/com/voiceping/android/di/AuthModule.kt
    - android/app/src/main/java/com/voiceping/android/presentation/login/LoginScreen.kt
    - android/app/src/main/java/com/voiceping/android/presentation/login/LoginViewModel.kt
    - android/app/src/main/java/com/voiceping/android/presentation/loading/LoadingScreen.kt
    - android/app/src/main/java/com/voiceping/android/presentation/loading/LoadingViewModel.kt
  modified:
    - android/app/build.gradle.kts
    - android/app/src/main/java/com/voiceping/android/presentation/navigation/NavGraph.kt
    - android/app/src/main/java/com/voiceping/android/presentation/MainActivity.kt

tech-stack:
  added:
    - EncryptedSharedPreferences (security-crypto 1.1.0-alpha06)
    - Retrofit AuthApi interface
    - LoginUseCase domain layer pattern

  patterns:
    - EncryptedSharedPreferences with AES256_GCM MasterKey for JWT storage
    - Repository pattern with 3-retry token refresh logic
    - ViewModel UiState pattern with separate field errors
    - Compose AnimatedVisibility for field slide-up effect
    - Auto-login navigation logic with token expiry checks

decisions:
  - title: "Store credentials (email/password) for silent refresh"
    rationale: "Server requires email/password for token refresh (no refresh token endpoint). Storing in EncryptedSharedPreferences is secure (hardware-backed Keystore). Matches user decision for 'always persist session' behavior."
    alternatives: "Refresh token endpoint (would require server changes, out of scope)"
    phase: 05
    plan: 02

  - title: "Inline validation errors under fields (not toast/snackbar)"
    rationale: "Per user decision: 'Login validation errors shown inline under fields (not toast/snackbar)'. Better UX - errors stay visible, don't auto-dismiss."
    alternatives: "Toast/Snackbar (rejected - disappear too quickly)"
    phase: 05
    plan: 02

  - title: "2-second loading screen delay"
    rationale: "Brief visual feedback between login and channel list. Actual WebSocket connection logic comes in Plan 03, so simulated delay for now."
    alternatives: "No loading screen (jarring transition), wait for real connection (requires Plan 03 first)"
    phase: 05
    plan: 02

  - title: "BuildConfig.SERVER_URL default to emulator localhost (10.0.2.2:3000)"
    rationale: "10.0.2.2 is Android emulator's host loopback address. Developers can change this in build.gradle.kts for physical devices or production servers."
    alternatives: "Hardcoded URL (inflexible), runtime configuration screen (out of scope for Phase 5)"
    phase: 05
    plan: 02

tags:
  - android
  - authentication
  - jwt
  - encrypted-storage
  - login
  - retrofit
  - compose-ui

duration: 192s
---

# Phase 05 Plan 02: Authentication & Login Flow Summary

**One-liner:** Implemented complete authentication flow with EncryptedSharedPreferences JWT storage, email/password login, auto-login on relaunch, 3-retry silent token refresh, branded login UI with inline errors, and loading screen navigation.

## What Was Built

This plan implemented the full authentication and login experience:

1. **Secure JWT Storage (TokenManager.kt):**
   - EncryptedSharedPreferences with AES256_GCM MasterKey
   - Store JWT token, timestamp, email, password for silent refresh
   - Token expiry check (1-hour TTL = 3600000ms)
   - Refresh threshold check (55 minutes = 3300000ms)
   - clearAll() method for logout

2. **Auth Data Layer:**
   - AuthApi (Retrofit): POST /api/auth/login endpoint
   - LoginRequest/LoginResponse DTOs matching server protocol
   - AuthRepository with login(), refreshToken(), refreshTokenWithRetry(), logout(), hasValidSession()
   - refreshTokenWithRetry: attempts 3 times with 1-second delays, clears credentials on failure
   - LoginUseCase: domain layer wrapper for AuthRepository.login()

3. **Preferences Storage (PreferencesManager.kt):**
   - Regular SharedPreferences for non-sensitive data
   - Store last selected eventId for auto-skip event picker logic
   - saveLastEventId(), getLastEventId(), clearLastEventId()

4. **Dependency Injection (AuthModule.kt):**
   - Provides Retrofit with BuildConfig.SERVER_URL base URL
   - Provides AuthApi from Retrofit
   - Provides TokenManager, PreferencesManager, AuthRepository as singletons
   - Uses OkHttpClient and Gson from AppModule

5. **Login UI (LoginScreen.kt):**
   - Branded dark design with logo at top (cyan tint)
   - Email OutlinedTextField with inline error text
   - Password OutlinedTextField with PasswordVisualTransformation
   - Separate emailError, passwordError, generalError display
   - CircularProgressIndicator in button during loading state
   - AnimatedVisibility with slideInVertically for fields

6. **Login ViewModel (LoginViewModel.kt):**
   - UiState sealed class: Idle, Loading, Success(user), Error(emailError, passwordError, generalError)
   - Local validation: email not blank, password not blank
   - Server errors shown in generalError field
   - checkAutoLogin(): returns true if token exists and not expired

7. **Loading Screen (LoadingScreen.kt):**
   - Dark background with centered logo
   - "Connecting..." text with CircularProgressIndicator
   - 2-second delay (simulates WebSocket connection until Plan 03)
   - LoadingViewModel: placeholder for future WebSocket logic

8. **Navigation Wiring (NavGraph.kt):**
   - Auto-login logic: determine start destination based on checkAutoLogin()
   - If hasValidSession → start at LOADING route
   - If no session → start at LOGIN route
   - LOGIN → LOADING on success (popUpTo LOGIN inclusive)
   - LOADING → EVENTS or CHANNELS based on PreferencesManager.getLastEventId()
   - popUpTo prevents back-navigation to login/loading screens

9. **MainActivity Integration:**
   - Inject PreferencesManager
   - Pass to NavGraph composable
   - Navigation handles all auth state transitions

## Task Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create secure storage and auth data layer | 1bf09ed | TokenManager.kt, PreferencesManager.kt, AuthApi.kt, LoginRequest.kt, LoginResponse.kt, AuthRepository.kt, LoginUseCase.kt, AuthModule.kt, build.gradle.kts |
| 2 | Create Login UI, auto-login, loading screen, navigation wiring | ff2d2e2 | LoginScreen.kt, LoginViewModel.kt, LoadingScreen.kt, LoadingViewModel.kt, NavGraph.kt, MainActivity.kt |

## Verification Results

All verification criteria met:

✅ TokenManager uses EncryptedSharedPreferences with AES256_GCM MasterKey
✅ Credentials stored for silent refresh (email + password)
✅ Token expiry check at 1-hour TTL, refresh threshold at 55 minutes
✅ refreshTokenWithRetry() attempts 3 times before force logout
✅ LoginScreen shows inline errors (not toast/snackbar)
✅ Auto-login skips login screen when valid token exists
✅ LoadingScreen shows brief connecting state between login and channels
✅ Navigation: login → loading → events/channels with no back-stack issues
✅ All Hilt injections are properly wired

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

### 1. Store Credentials for Silent Refresh

**Context:** Server requires email/password for token refresh (no refresh token endpoint exists).

**Decision:** Store email and password in EncryptedSharedPreferences alongside JWT token.

**Rationale:**
- Server's POST /api/auth/login is the only way to refresh tokens
- EncryptedSharedPreferences uses hardware-backed Android Keystore (AES256_GCM)
- Keys never leave secure hardware (TEE/StrongBox on supported devices)
- Matches user decision for "always persist session" (no "Remember me" toggle)
- Security trade-off acceptable: credentials encrypted at rest, only accessible to this app

**Impact:** Users never manually re-enter credentials unless all 3 refresh attempts fail or they explicitly logout.

### 2. Inline Validation Errors

**Context:** User decision specified "Login validation errors shown inline under fields (not toast/snackbar)".

**Decision:** Display emailError, passwordError, generalError as Text components below respective fields.

**Rationale:**
- Errors stay visible (don't auto-dismiss like Toasts)
- User can reference error while correcting input
- Better accessibility (TalkBack reads errors in context)
- Material 3 best practice for form validation

**Impact:** UiState has three separate error fields instead of single error string.

### 3. 2-Second Loading Screen Delay

**Context:** LoadingScreen shown between login and channel list, but WebSocket connection logic is in Plan 03.

**Decision:** Simulate connection with 2-second delay using LaunchedEffect + delay(2000).

**Rationale:**
- Provides visual feedback for successful login
- Placeholder for actual WebSocket connection logic (Plan 03 will replace delay with real connection)
- Prevents jarring immediate transition from login to channel list
- 2 seconds feels natural for "connecting" state

**Impact:** Plan 03 will replace `delay(2000)` with actual WebSocket connection + capabilities exchange.

### 4. BuildConfig.SERVER_URL Default

**Context:** Server URL needs to be configurable for dev/staging/production environments.

**Decision:** Set default to `https://10.0.2.2:3000` (Android emulator's host loopback).

**Rationale:**
- 10.0.2.2 is Android emulator's special alias for host machine's 127.0.0.1
- Developers can test against locally-running server (npm run dev on host)
- Physical devices can change to actual server IP in build.gradle.kts
- Production builds can use environment variables or separate buildConfigField

**Impact:** Emulator testing works out-of-box. Physical device testing requires build.gradle.kts change.

## Architecture Decisions

### Repository Pattern with Coroutines

All network operations use Kotlin coroutines with Dispatchers.IO:
- `withContext(Dispatchers.IO)` ensures network calls don't block main thread
- Result<T> return type encapsulates success/failure states
- ViewModel's viewModelScope.launch handles coroutine lifecycle

**Rationale:** Standard Android pattern for async operations. Coroutines are simpler than RxJava and integrate with Compose StateFlow.

### UiState Sealed Class Pattern

LoginViewModel exposes single `StateFlow<LoginUiState>` instead of multiple StateFlows:
- Idle: initial state
- Loading: login request in progress
- Success(user): login succeeded, trigger navigation
- Error(emailError, passwordError, generalError): validation or server errors

**Rationale:** Single source of truth prevents invalid state combinations (e.g., Loading + Error simultaneously). UI reacts to state changes predictably.

### Auto-Login Navigation Logic

Start destination determined at NavGraph creation time based on checkAutoLogin():
- If valid token exists → start at LOADING route
- If no token or expired → start at LOGIN route

**Rationale:** Users with valid sessions never see login screen on app relaunch. Matches "always persist session" user decision.

## Next Phase Readiness

**Blockers:** None

**Ready for Plan 03 (WebSocket Signaling):**
- ✅ TokenManager.getToken() provides JWT for WebSocket authentication
- ✅ AuthRepository.refreshTokenWithRetry() ready for use in WebSocket reconnection logic
- ✅ LoadingScreen ready to trigger WebSocket connection (replace delay with real logic)

**Ready for Plan 04 (Channel List UI):**
- ✅ Navigation routes to EVENTS/CHANNELS screens based on PreferencesManager
- ✅ Auto-skip event picker logic implemented (saved eventId → CHANNELS route)
- ✅ Authenticated session guaranteed before reaching channel list

**Ready for Plan 05 (End-to-end Integration):**
- ✅ Complete auth flow works from login → loading → channels
- ✅ Auto-login prevents repeated login prompts on app relaunch
- ✅ Token refresh logic ready for background operation

## Lessons Learned

### 1. EncryptedSharedPreferences Requires API 26+ for Best Security

EncryptedSharedPreferences works on API 23+, but hardware-backed encryption (StrongBox) requires API 28+. Our minSdk 26 gets hardware-backed encryption on most devices (fallback to software encryption on older devices).

**Takeaway:** minSdk 26 was correct choice for balance between security and device coverage.

### 2. Retrofit Needs BuildConfig for Dynamic URLs

Retrofit.Builder().baseUrl() requires compile-time constant. BuildConfig.SERVER_URL provides this while remaining configurable per build variant.

**Takeaway:** buildConfigField in build.gradle.kts is standard pattern for environment-specific configuration.

### 3. NavGraph Start Destination Can't Be Changed After Creation

Once NavHost is created with startDestination, it can't be changed. Auto-login logic must determine start destination BEFORE creating NavHost.

**Takeaway:** checkAutoLogin() called at NavGraph composition time, not in LaunchedEffect.

### 4. Compose AnimatedVisibility Requires State Variable

slideInVertically animation needs boolean state (showFields) to trigger. Direct use of constants (visible = true) doesn't animate.

**Takeaway:** Always use `var showFields by remember { mutableStateOf(false) }` and set to true in LaunchedEffect for entrance animations.

## Self-Check: PASSED

All key files verified to exist:
✅ android/app/src/main/java/com/voiceping/android/data/storage/TokenManager.kt
✅ android/app/src/main/java/com/voiceping/android/data/storage/PreferencesManager.kt
✅ android/app/src/main/java/com/voiceping/android/data/api/AuthApi.kt
✅ android/app/src/main/java/com/voiceping/android/data/network/dto/LoginRequest.kt
✅ android/app/src/main/java/com/voiceping/android/data/network/dto/LoginResponse.kt
✅ android/app/src/main/java/com/voiceping/android/data/repository/AuthRepository.kt
✅ android/app/src/main/java/com/voiceping/android/domain/usecase/LoginUseCase.kt
✅ android/app/src/main/java/com/voiceping/android/di/AuthModule.kt
✅ android/app/src/main/java/com/voiceping/android/presentation/login/LoginScreen.kt
✅ android/app/src/main/java/com/voiceping/android/presentation/login/LoginViewModel.kt
✅ android/app/src/main/java/com/voiceping/android/presentation/loading/LoadingScreen.kt
✅ android/app/src/main/java/com/voiceping/android/presentation/loading/LoadingViewModel.kt

All commits verified:
✅ 1bf09ed - Task 1 commit
✅ ff2d2e2 - Task 2 commit
