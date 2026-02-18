---
phase: quick-3
plan: 01
type: execute
wave: 1
depends_on: []
files_modified: []
autonomous: false
must_haves:
  truths:
    - "assembleDebug completes successfully producing a debug APK"
    - "User can install the debug APK on a physical Android device"
    - "App launches and reaches the login screen on the physical device"
  artifacts:
    - path: "android/app/build/outputs/apk/debug/app-debug.apk"
      provides: "Installable debug APK"
  key_links:
    - from: "JAVA_HOME environment"
      to: "Gradle build"
      via: "gradlew reads JAVA_HOME"
      pattern: "JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64"
---

<objective>
Build the VoicePing Android debug APK, fix any build issues encountered, and deploy to a physical device for testing.

Purpose: Get a working debug build on real hardware for functional testing of the v4.0 app.
Output: A debug APK installed and running on a physical Android device.
</objective>

<context>
@android/app/build.gradle.kts
@android/build.gradle.kts
@android/app/src/main/AndroidManifest.xml
</context>

<tasks>

<task type="auto">
  <name>Task 1: Fix JAVA_HOME and build debug APK</name>
  <files>android/app/build/outputs/apk/debug/app-debug.apk</files>
  <action>
The current JAVA_HOME is misconfigured — it points to the java binary
(`/usr/lib/jvm/java-21-openjdk-amd64/bin/java`) instead of the JVM
root directory. Fix this before building.

1. Export the corrected JAVA_HOME for the build:
   ```
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
   ```

2. Run the full debug APK build:
   ```
   cd /home/earthworm/Github-repos/voiceping-router/android && \
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
   ```

3. If the build fails, analyze the error output and fix the issue.
   Common issues to watch for:
   - Missing Android SDK components (install via sdkmanager)
   - Dependency resolution failures (check network/repos)
   - Kotlin compilation errors (fix source code)
   - Resource/manifest errors (fix XML)

4. Repeat until `assembleDebug` succeeds and the APK exists at:
   `android/app/build/outputs/apk/debug/app-debug.apk`

5. Report the APK file size and build outcome.

Note: Also recommend the user fix JAVA_HOME permanently in their shell
profile (~/.bashrc or ~/.zshrc) by changing it to the directory path
(not the binary path).
  </action>
  <verify>
`ls -lh android/app/build/outputs/apk/debug/app-debug.apk` shows the APK exists with a reasonable size (>5MB).
  </verify>
  <done>Debug APK is built successfully at android/app/build/outputs/apk/debug/app-debug.apk</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 2: Install and test on physical device</name>
  <what-built>Debug APK built by assembleDebug</what-built>
  <how-to-verify>
Prerequisites — make sure your physical Android device is ready:

1. **Enable Developer Options** on your phone:
   - Settings > About Phone > tap "Build number" 7 times
   - Settings > Developer options > enable "USB debugging"

2. **Connect phone via USB** to your computer

3. **Verify device is detected** (run from this machine):
   ```
   /home/earthworm/Android/Sdk/platform-tools/adb devices
   ```
   You should see your device serial number listed as "device" (not "unauthorized").
   If "unauthorized", check your phone for the USB debugging authorization prompt and accept it.

4. **Install the APK**:
   ```
   /home/earthworm/Android/Sdk/platform-tools/adb install -r \
     /home/earthworm/Github-repos/voiceping-router/android/app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Launch the app**:
   ```
   /home/earthworm/Android/Sdk/platform-tools/adb shell am start -n com.voiceping.android/.presentation.MainActivity
   ```

6. **Verify on device**:
   - App opens without crashing
   - Login screen is displayed
   - UI renders correctly (Compose layout, icons, text)

7. **Check logcat for errors** (optional but recommended):
   ```
   /home/earthworm/Android/Sdk/platform-tools/adb logcat -s VoicePing:* AndroidRuntime:E | head -100
   ```

If the app crashes on launch, share the logcat crash output so we can diagnose.

**Tip:** ADB is not in your PATH. Either use the full path above or add this to your shell profile:
```
export PATH=$PATH:/home/earthworm/Android/Sdk/platform-tools
```
  </how-to-verify>
  <resume-signal>Type "approved" if the app launches correctly, or paste crash logs / describe issues</resume-signal>
</task>

</tasks>

<verification>
- `assembleDebug` gradle task completes with BUILD SUCCESSFUL
- APK file exists at android/app/build/outputs/apk/debug/app-debug.apk
- APK can be installed on a physical device via adb install
- App launches and displays the login screen
</verification>

<success_criteria>
- Debug APK builds without errors
- APK installs on physical Android device
- App launches to the login screen without crashes
</success_criteria>

<output>
After completion, create `.planning/quick/3-build-debug-apk-and-test-on-physical-dev/3-SUMMARY.md`
</output>
