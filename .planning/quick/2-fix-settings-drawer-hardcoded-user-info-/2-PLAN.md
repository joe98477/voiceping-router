---
phase: quick-2
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/app/src/main/java/com/voiceping/android/data/storage/TokenManager.kt
  - android/app/src/main/java/com/voiceping/android/data/repository/AuthRepository.kt
  - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
  - android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
  - android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
autonomous: true
must_haves:
  truths:
    - "Settings drawer shows actual logged-in user name and email, not hardcoded placeholders"
    - "Channel selection (join) does not crash with 'No iceParameters' error"
    - "Transport parameters (iceParameters, iceCandidates, dtlsParameters) are correctly serialized to JSON strings from Gson-deserialized maps/lists"
  artifacts:
    - path: "android/app/src/main/java/com/voiceping/android/data/storage/TokenManager.kt"
      provides: "saveUserInfo and getUserName/getUserEmail methods"
      contains: "KEY_USER_NAME"
    - path: "android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt"
      provides: "Fixed transport parameter type casting using Gson serialization"
      contains: "Gson"
  key_links:
    - from: "AuthRepository.kt"
      to: "TokenManager.kt"
      via: "saveUserInfo call in login()"
      pattern: "tokenManager\\.saveUserInfo"
    - from: "ChannelListViewModel.kt"
      to: "TokenManager.kt"
      via: "getUserName/getUserEmail for StateFlows"
      pattern: "tokenManager\\.getUser"
    - from: "ChannelListScreen.kt"
      to: "ChannelListViewModel.kt"
      via: "collectAsState for userName/userEmail"
      pattern: "viewModel\\.userName\\.collectAsState"
---

<objective>
Fix two bugs in the Android client: (1) Settings/profile drawer showing hardcoded "User Name" and "user@example.com" instead of actual user data, and (2) "No iceParameters" crash when selecting a channel due to incorrect type casting of transport parameters.

Purpose: Both bugs block real-world usage of the app -- the profile drawer is cosmetically broken and the transport error prevents any channel audio connection.
Output: Working profile drawer with real user info, and transport creation that correctly handles Gson-deserialized JSON objects.
</objective>

<execution_context>
@/home/earthworm/.claude/get-shit-done/workflows/execute-plan.md
@/home/earthworm/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@android/app/src/main/java/com/voiceping/android/data/storage/TokenManager.kt
@android/app/src/main/java/com/voiceping/android/data/repository/AuthRepository.kt
@android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
@android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
@android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
@android/app/src/main/java/com/voiceping/android/data/network/dto/LoginResponse.kt
@android/app/src/main/java/com/voiceping/android/presentation/shell/ProfileDrawer.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix settings drawer hardcoded user info</name>
  <files>
    android/app/src/main/java/com/voiceping/android/data/storage/TokenManager.kt
    android/app/src/main/java/com/voiceping/android/data/repository/AuthRepository.kt
    android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListViewModel.kt
    android/app/src/main/java/com/voiceping/android/presentation/channels/ChannelListScreen.kt
  </files>
  <action>
    **TokenManager.kt** -- Add user info storage alongside existing credential storage:
    - Add companion constants: `KEY_USER_NAME = "user_name"` and `KEY_USER_EMAIL = "user_email"`
    - Add method `saveUserInfo(name: String, email: String)` that writes both to encryptedPrefs
    - Add methods `getUserName(): String?` and `getUserEmail(): String?` that read from encryptedPrefs
    - Ensure `clearAll()` already handles these (it does -- `edit().clear().apply()` clears everything)

    **AuthRepository.kt** -- Save user info on login:
    - In the `login()` method, after `val response = authApi.login(...)`, call `tokenManager.saveUserInfo(response.displayName ?: email, email)` BEFORE `tokenManager.saveCredentials(email, password)`. Use `response.displayName ?: email` as the name fallback (matching `LoginResponse.toUser()` logic).

    **ChannelListViewModel.kt** -- Expose user data:
    - Add `TokenManager` as a constructor parameter (Hilt will inject it automatically since TokenManager is @Singleton @Inject)
    - Add two simple val properties (NOT StateFlows -- this data doesn't change during the session):
      ```
      val userName: String = tokenManager.getUserName() ?: "User"
      val userEmail: String = tokenManager.getUserEmail() ?: ""
      ```

    **ChannelListScreen.kt** -- Wire real values:
    - Replace line 188 `userName = "User Name"` with `userName = viewModel.userName`
    - Replace line 189 `userEmail = "user@example.com"` with `userEmail = viewModel.userEmail`
    - No new imports needed since viewModel is already available
  </action>
  <verify>
    Run `cd /home/earthworm/Github-repos/voiceping-router/android && ./gradlew compileDebugKotlin` -- must compile without errors.
    Grep for "User Name" in ChannelListScreen.kt -- must NOT appear.
    Grep for "user@example.com" in ChannelListScreen.kt -- must NOT appear.
  </verify>
  <done>
    TokenManager has saveUserInfo/getUserName/getUserEmail methods. AuthRepository saves user info on login. ChannelListViewModel exposes userName/userEmail from TokenManager. ChannelListScreen passes real values to ProfileDrawer instead of hardcoded strings.
  </done>
</task>

<task type="auto">
  <name>Task 2: Fix iceParameters type casting in MediasoupClient</name>
  <files>
    android/app/src/main/java/com/voiceping/android/data/network/MediasoupClient.kt
  </files>
  <action>
    The server sends `iceParameters`, `iceCandidates`, and `dtlsParameters` as JSON objects/arrays. Gson deserializes these into `Map<String, Any>` and `List<Any>` respectively (stored in the `Map<String, Any?>` response data). The current code does `as? String` which returns null, causing `IllegalStateException("No iceParameters")`.

    The mediasoup-android library expects these parameters as JSON strings. The fix is to use Gson to re-serialize the deserialized objects back to JSON strings.

    **MediasoupClient.kt** changes:
    - Add import: `import com.google.gson.Gson`
    - Add a `private val gson = Gson()` property in the class body (after the existing property declarations, around line 41)
    - Create a private helper method:
      ```kotlin
      private fun toJsonString(data: Any?): String {
          return gson.toJson(data) ?: throw IllegalStateException("Failed to serialize to JSON")
      }
      ```
    - In `createRecvTransport()` (lines 109-116), replace all three `as? String` casts:
      - `val iceParameters = toJsonString(transportData["iceParameters"])` (remove the null check -- toJsonString throws if null)
      - `val iceCandidates = toJsonString(transportData["iceCandidates"])`
      - `val dtlsParameters = toJsonString(transportData["dtlsParameters"])`
      - Keep `val transportId = transportData["id"] as? String` as-is (id IS a String)
    - In `createSendTransport()` (lines 277-284), apply the SAME fix:
      - `val iceParameters = toJsonString(transportData["iceParameters"])`
      - `val iceCandidates = toJsonString(transportData["iceCandidates"])`
      - `val dtlsParameters = toJsonString(transportData["dtlsParameters"])`
      - Keep `val transportId = transportData["id"] as? String` as-is

    Do NOT change `consumeAudio()` -- its `rtpParameters` cast may also need fixing but the bug report only covers transport creation. Leave consumeAudio's casts for now (they follow the same pattern but are in TODO-commented code).
  </action>
  <verify>
    Run `cd /home/earthworm/Github-repos/voiceping-router/android && ./gradlew compileDebugKotlin` -- must compile without errors.
    Grep for `as? String` in MediasoupClient.kt in the createRecvTransport and createSendTransport methods -- should only appear for `transportData["id"]`, NOT for iceParameters/iceCandidates/dtlsParameters.
  </verify>
  <done>
    MediasoupClient uses Gson.toJson() to serialize transport parameters (iceParameters, iceCandidates, dtlsParameters) from Gson-deserialized Map/List objects to JSON strings. Both createRecvTransport() and createSendTransport() are fixed. The "No iceParameters" crash is resolved.
  </done>
</task>

</tasks>

<verification>
1. `cd /home/earthworm/Github-repos/voiceping-router/android && ./gradlew compileDebugKotlin` passes
2. No hardcoded "User Name" or "user@example.com" in ChannelListScreen.kt
3. No `as? String` for iceParameters/iceCandidates/dtlsParameters in MediasoupClient.kt
4. TokenManager has KEY_USER_NAME and KEY_USER_EMAIL constants
5. AuthRepository.login() calls tokenManager.saveUserInfo()
</verification>

<success_criteria>
- Profile drawer displays actual user name and email from login response (stored in encrypted prefs)
- Transport creation in MediasoupClient correctly converts Gson-deserialized objects to JSON strings
- Project compiles cleanly with `./gradlew compileDebugKotlin`
</success_criteria>

<output>
After completion, create `.planning/quick/2-fix-settings-drawer-hardcoded-user-info-/2-SUMMARY.md`
</output>
