---
phase: quick-8
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - VERSION
  - package.json
  - control-plane/package.json
  - web-ui/package.json
  - android/app/build.gradle.kts
  - scripts/bump-version.sh
autonomous: true
must_haves:
  truths:
    - "Single VERSION file is the source of truth for the project version"
    - "All component version numbers match the VERSION file"
    - "A bump script can increment major, minor, or patch and update all components"
    - "Git tag v4.1.0 exists marking the current state after quick tasks"
  artifacts:
    - path: "VERSION"
      provides: "Single source of truth for project version"
      contains: "4.1.0"
    - path: "scripts/bump-version.sh"
      provides: "Version bump automation"
    - path: "package.json"
      provides: "Root package version aligned"
      contains: "4.1.0"
    - path: "control-plane/package.json"
      provides: "Control plane version aligned"
      contains: "4.1.0"
    - path: "web-ui/package.json"
      provides: "Web UI version aligned"
      contains: "4.1.0"
    - path: "android/app/build.gradle.kts"
      provides: "Android version aligned"
      contains: "4.1.0"
  key_links:
    - from: "scripts/bump-version.sh"
      to: "VERSION"
      via: "reads and writes version"
      pattern: "VERSION"
    - from: "scripts/bump-version.sh"
      to: "package.json"
      via: "sed replacement"
      pattern: "package\\.json"
---

<objective>
Introduce a single-file versioning strategy so the project version is defined in one place and propagated to all components (server, control-plane, web-ui, Android) via a bump script.

Purpose: The project has shipped v4.0 across 4 milestones but version numbers are scattered and inconsistent (root=1.0.0, control-plane=0.1.0, web-ui=0.1.0, android=1.0.0). A unified version simplifies release tracking, Docker image tagging, and changelog management.

Output: VERSION file, aligned component versions, bump script, git tag for current state.
</objective>

<execution_context>
@/home/earthworm/.claude/get-shit-done/workflows/execute-plan.md
@/home/earthworm/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@package.json
@control-plane/package.json
@web-ui/package.json
@android/app/build.gradle.kts
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create VERSION file and align all component versions to 4.1.0</name>
  <files>VERSION, package.json, control-plane/package.json, web-ui/package.json, android/app/build.gradle.kts</files>
  <action>
The project shipped v4.0 as the last milestone. Since multiple quick tasks (4-7) have been completed since v4.0 was tagged, the current state should be marked as v4.1.0.

1. Create `VERSION` file at project root containing exactly `4.1.0` (no trailing newline beyond what echo produces, just the semver string on one line).

2. Update `package.json` (root): change `"version": "1.0.0"` to `"version": "4.1.0"`.

3. Update `control-plane/package.json`: change `"version": "0.1.0"` to `"version": "4.1.0"`.

4. Update `web-ui/package.json`: change `"version": "0.1.0"` to `"version": "4.1.0"`.

5. Update `android/app/build.gradle.kts`:
   - Change `versionCode = 1` to `versionCode = 41000` (encoding: major*10000 + minor*1000 + patch, so 4*10000 + 1*1000 + 0 = 41000). This scheme supports up to version 99.9.999.
   - Change `versionName = "1.0.0"` to `versionName = "4.1.0"`.

Note: The existing git tags (v2.0, v3.0, v4.0) use two-part versions. Going forward, use three-part semver (v4.1.0). The old tags remain as-is for history.
  </action>
  <verify>
Run these checks:
- `cat VERSION` shows `4.1.0`
- `grep '"version": "4.1.0"' package.json` matches
- `grep '"version": "4.1.0"' control-plane/package.json` matches
- `grep '"version": "4.1.0"' web-ui/package.json` matches
- `grep 'versionName = "4.1.0"' android/app/build.gradle.kts` matches
- `grep 'versionCode = 41000' android/app/build.gradle.kts` matches
  </verify>
  <done>All 5 version locations show 4.1.0 and VERSION file exists as single source of truth</done>
</task>

<task type="auto">
  <name>Task 2: Create version bump script and tag current state</name>
  <files>scripts/bump-version.sh</files>
  <action>
Create `scripts/bump-version.sh` (executable) that automates version bumps across all components.

The script should:
1. Accept one argument: `major`, `minor`, or `patch`
2. Read current version from `VERSION` file
3. Parse into major.minor.patch components
4. Increment the requested component (reset lower components to 0)
5. Write new version to `VERSION`
6. Update `package.json` (root) version field using sed
7. Update `control-plane/package.json` version field using sed
8. Update `web-ui/package.json` version field using sed
9. Update `android/app/build.gradle.kts`:
   - Compute new versionCode as `major*10000 + minor*1000 + patch`
   - Replace versionCode value
   - Replace versionName value
10. Print the old and new version
11. Print reminder: "Run: git add -A && git commit -m 'chore: bump version to vX.Y.Z' && git tag vX.Y.Z"

The script should NOT automatically commit or tag (let the user decide when).

Use `#!/usr/bin/env bash` shebang. Use `set -euo pipefail`. The script should cd to the repo root (relative to script location via `SCRIPT_DIR`) so it works from any working directory.

After creating the script, make it executable, then create the v4.1.0 git tag on the current HEAD:
- `git tag v4.1.0`

Also retroactively create `v1.0` tag if missing (it is missing -- v2.0, v3.0, v4.0 exist but no v1.0). Skip this if it would be confusing to find the right commit. Actually, do NOT create v1.0 retroactively -- the user can do this if they want. Just create v4.1.0.
  </action>
  <verify>
Run these checks:
- `bash scripts/bump-version.sh` (no args) shows usage message
- `file scripts/bump-version.sh` confirms it is executable (or `test -x scripts/bump-version.sh`)
- `git tag -l v4.1.0` shows the tag exists
- Dry-run test: copy VERSION to /tmp, run `bash scripts/bump-version.sh patch`, verify VERSION now reads `4.1.1`, then restore VERSION from /tmp. Actually, simpler: just verify the script parses correctly by reading it and confirming the sed patterns are correct.
  </verify>
  <done>Bump script exists and is executable, v4.1.0 git tag exists on current HEAD, script handles major/minor/patch bumps across all 5 version locations</done>
</task>

</tasks>

<verification>
- `cat VERSION` outputs `4.1.0`
- All package.json files and build.gradle.kts show version 4.1.0
- `git tag -l 'v4*'` shows both `v4.0` and `v4.1.0`
- `scripts/bump-version.sh` is executable and shows usage when called without args
- `cd /tmp && bash /path/to/scripts/bump-version.sh patch` would correctly bump (but don't actually run from /tmp since it needs repo context)
</verification>

<success_criteria>
- Single VERSION file is the authoritative version source
- All 4 components (root server, control-plane, web-ui, Android) show 4.1.0
- Bump script handles major/minor/patch and updates all components
- v4.1.0 git tag marks the current project state
</success_criteria>

<output>
After completion, create `.planning/quick/8-introduce-a-simple-versioning-strategy-f/8-SUMMARY.md`
</output>
