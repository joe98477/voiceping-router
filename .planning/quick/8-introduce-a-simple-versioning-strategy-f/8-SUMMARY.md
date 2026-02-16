---
phase: quick-8
plan: 01
subsystem: build-tooling
tags: [versioning, automation, release-management]
dependencies:
  requires: []
  provides: [single-source-version, bump-script]
  affects: [all-components]
tech-stack:
  added: [VERSION-file, bash-automation]
  patterns: [semver-single-source, android-version-encoding]
key-files:
  created:
    - VERSION
    - scripts/bump-version.sh
  modified:
    - package.json
    - control-plane/package.json
    - web-ui/package.json
    - android/app/build.gradle.kts
decisions:
  - "Use three-part semver (X.Y.Z) going forward; old git tags (v2.0, v3.0, v4.0) remain for history"
  - "Encode Android versionCode as major*10000 + minor*1000 + patch (supports up to 99.9.999)"
  - "Bump script does NOT auto-commit or tag; prints instructions for user to review and execute"
metrics:
  duration: 87
  completed: 2026-02-16
---

# Quick Task 8: Introduce Simple Versioning Strategy - Summary

Unified project versioning with single VERSION file as source of truth and automated bump script for all components.

## Overview

The project shipped v4.0 across 4 milestones but version numbers were scattered and inconsistent (root=1.0.0, control-plane=0.1.0, web-ui=0.1.0, android=1.0.0). This task introduced a single-file versioning strategy so the project version is defined in one place (VERSION file) and propagated to all components via a bump script.

**Current state:** All components aligned to v4.1.0 with git tag marking the state after quick tasks 4-7.

## Tasks Completed

### Task 1: Create VERSION file and align all component versions to 4.1.0
**Commit:** cdf2410

Created VERSION file at project root containing `4.1.0` and updated all 5 version locations:
- Root package.json: 1.0.0 → 4.1.0
- control-plane/package.json: 0.1.0 → 4.1.0
- web-ui/package.json: 0.1.0 → 4.1.0
- Android versionCode: 1 → 41000 (encoding: 4*10000 + 1*1000 + 0)
- Android versionName: 1.0.0 → 4.1.0

Rationale: The project shipped v4.0 as the last milestone. Quick tasks 4-7 added features (audio fix, docs, channel persistence), warranting a minor bump to v4.1.0.

### Task 2: Create version bump script and tag current state
**Commit:** 2e4f67b

Created `scripts/bump-version.sh` (executable) that:
- Accepts `major`, `minor`, or `patch` argument
- Reads current version from VERSION file
- Parses and increments the appropriate component
- Updates VERSION file
- Updates all package.json files using sed
- Computes Android versionCode (major*10000 + minor*1000 + patch)
- Updates android/app/build.gradle.kts versionCode and versionName
- Prints old/new versions and next steps (git commit + tag)

Created git tag `v4.1.0` marking the current HEAD after quick tasks.

The script uses `set -euo pipefail` and works from any directory by resolving the repo root relative to script location.

## Verification Results

All verification checks passed:
- `cat VERSION` outputs `4.1.0`
- All package.json files show version 4.1.0
- Android versionCode is 41000 and versionName is 4.1.0
- `git tag -l 'v4*'` shows both `v4.0` and `v4.1.0`
- Bump script is executable and shows usage when called without args
- Script correctly handles version parsing and component updates

## Deviations from Plan

None - plan executed exactly as written.

## Key Decisions

1. **Semver adoption:** Old git tags (v2.0, v3.0, v4.0) used two-part versions. Going forward, using three-part semver (v4.1.0) for better granularity. Old tags remain unchanged for history.

2. **Android versionCode encoding:** Using `major*10000 + minor*1000 + patch` scheme, which supports versions up to 99.9.999. This gives plenty of headroom for future releases.

3. **Manual commit/tag step:** Bump script does NOT automatically commit or tag. It prints instructions for the user to review changes and execute git commands manually. This prevents accidental releases and gives the user control.

## Impact Assessment

**Positive impacts:**
- Single source of truth eliminates version drift
- Automated bump script reduces human error
- Consistent versioning simplifies release tracking
- Android versionCode auto-computed from semver
- Docker image tagging and changelog management now have clear version source

**Components affected:**
- Root server package
- Control plane API
- Web UI
- Android app
- Docker builds (will read VERSION file in future)
- Release documentation

**Risks mitigated:**
- No more manual version sync across 5 files
- No more accidental version skew between components
- Clear version source for CI/CD pipelines

## Files Modified

**Created:**
- `VERSION` - Single source of truth for project version (4.1.0)
- `scripts/bump-version.sh` - Automated version bump tool (executable)

**Modified:**
- `package.json` - Version updated to 4.1.0
- `control-plane/package.json` - Version updated to 4.1.0
- `web-ui/package.json` - Version updated to 4.1.0
- `android/app/build.gradle.kts` - versionCode=41000, versionName=4.1.0

## Next Steps

**For next release:**
1. Run `./scripts/bump-version.sh patch` (or minor/major as appropriate)
2. Review changes with `git diff`
3. Commit: `git add -A && git commit -m 'chore: bump version to vX.Y.Z'`
4. Tag: `git tag vX.Y.Z`
5. Push with tags: `git push && git push --tags`

**Future enhancements (not required now):**
- Update Dockerfile to read VERSION file for image tagging
- Add VERSION to control-plane /health endpoint
- Display version in web UI footer
- Add version to Android app's About screen

## Self-Check: PASSED

Verified all claims:
- `cat VERSION` shows 4.1.0 ✓
- All package.json files show 4.1.0 ✓
- Android build.gradle.kts shows versionCode=41000, versionName=4.1.0 ✓
- Git tag v4.1.0 exists ✓
- Bump script exists and is executable ✓
- Commits cdf2410 and 2e4f67b exist ✓
