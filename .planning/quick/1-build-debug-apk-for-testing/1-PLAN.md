---
phase: quick
plan: 1
type: execute
wave: 1
depends_on: []
files_modified: []
autonomous: true

must_haves:
  truths:
    - "Debug APK file is produced and exists on disk"
    - "Build completes without errors"
  artifacts:
    - path: "android/app/build/outputs/apk/debug/app-debug.apk"
      provides: "Debug APK for testing"
  key_links: []
---

<objective>
Build a debug APK from the existing Android project for testing purposes.

Purpose: Produce an installable debug APK to verify the build environment works and to enable on-device testing.
Output: `android/app/build/outputs/apk/debug/app-debug.apk`
</objective>

<execution_context>
@/home/earthworm/.claude/get-shit-done/workflows/execute-plan.md
@/home/earthworm/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
Build environment:
- Android SDK: /home/earthworm/Android/Sdk (confirmed in android/local.properties)
- Gradle wrapper: 9.3.1
- AGP: 9.0.0 (bundles Kotlin — do NOT apply org.jetbrains.kotlin.android separately)
- Compile SDK: 35, min SDK: 26
- App ID: com.voiceping.android
</context>

<tasks>

<task type="auto">
  <name>Task 1: Build debug APK</name>
  <files>android/app/build/outputs/apk/debug/app-debug.apk</files>
  <action>
Run the Gradle assembleDebug task from the android directory:

```bash
cd /home/earthworm/Github-repos/voiceping-router/android && ./gradlew assembleDebug
```

This compiles all Kotlin sources, processes resources, and packages the debug APK. No code changes needed — this is purely a build verification step.

If the build fails:
- Check that JAVA_HOME points to a JDK 17+ installation
- Check that /home/earthworm/Android/Sdk exists and contains platform SDK 35
- Review the Gradle error output for missing dependencies
  </action>
  <verify>
Confirm the APK exists and report its size:

```bash
ls -lh /home/earthworm/Github-repos/voiceping-router/android/app/build/outputs/apk/debug/app-debug.apk
```

Expected: File exists with a reasonable size (several MB).
  </verify>
  <done>
app-debug.apk exists at android/app/build/outputs/apk/debug/app-debug.apk and the build completed with BUILD SUCCESSFUL.
  </done>
</task>

</tasks>

<verification>
- `./gradlew assembleDebug` exits with code 0 and prints BUILD SUCCESSFUL
- `app-debug.apk` exists in `android/app/build/outputs/apk/debug/`
</verification>

<success_criteria>
A debug APK is produced that can be installed on an Android device or emulator running API 26+.
</success_criteria>

<output>
After completion, create `.planning/quick/1-build-debug-apk-for-testing/1-SUMMARY.md`
</output>
