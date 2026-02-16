#!/usr/bin/env bash
set -euo pipefail

# Script to bump project version across all components
# Usage: ./scripts/bump-version.sh [major|minor|patch]

# Get script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

# Check argument
if [ $# -ne 1 ]; then
  echo "Usage: $0 [major|minor|patch]"
  echo ""
  echo "Examples:"
  echo "  $0 patch   # 4.1.0 -> 4.1.1"
  echo "  $0 minor   # 4.1.0 -> 4.2.0"
  echo "  $0 major   # 4.1.0 -> 5.0.0"
  exit 1
fi

BUMP_TYPE="$1"

if [[ ! "$BUMP_TYPE" =~ ^(major|minor|patch)$ ]]; then
  echo "Error: Bump type must be 'major', 'minor', or 'patch'"
  exit 1
fi

# Read current version
if [ ! -f VERSION ]; then
  echo "Error: VERSION file not found"
  exit 1
fi

CURRENT_VERSION=$(cat VERSION)
echo "Current version: $CURRENT_VERSION"

# Parse version components
if [[ ! "$CURRENT_VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Error: VERSION file does not contain valid semver (X.Y.Z)"
  exit 1
fi

MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"

# Increment version based on bump type
case "$BUMP_TYPE" in
  major)
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
    ;;
  minor)
    MINOR=$((MINOR + 1))
    PATCH=0
    ;;
  patch)
    PATCH=$((PATCH + 1))
    ;;
esac

NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
echo "New version: $NEW_VERSION"

# Compute new Android versionCode (major*10000 + minor*1000 + patch)
NEW_VERSION_CODE=$((MAJOR * 10000 + MINOR * 1000 + PATCH))
echo "New Android versionCode: $NEW_VERSION_CODE"

# Update VERSION file
echo "$NEW_VERSION" > VERSION

# Update package.json files using sed
sed -i.bak "s/\"version\": \"$CURRENT_VERSION\"/\"version\": \"$NEW_VERSION\"/" package.json && rm package.json.bak
sed -i.bak "s/\"version\": \"$CURRENT_VERSION\"/\"version\": \"$NEW_VERSION\"/" control-plane/package.json && rm control-plane/package.json.bak
sed -i.bak "s/\"version\": \"$CURRENT_VERSION\"/\"version\": \"$NEW_VERSION\"/" web-ui/package.json && rm web-ui/package.json.bak

# Update Android build.gradle.kts
# Replace versionCode line
sed -i.bak "s/versionCode = [0-9]\\+/versionCode = $NEW_VERSION_CODE/" android/app/build.gradle.kts
# Replace versionName line
sed -i.bak "s/versionName = \"[0-9]\\+\\.[0-9]\\+\\.[0-9]\\+\"/versionName = \"$NEW_VERSION\"/" android/app/build.gradle.kts
rm android/app/build.gradle.kts.bak

echo ""
echo "Version bumped from $CURRENT_VERSION to $NEW_VERSION"
echo ""
echo "Files updated:"
echo "  - VERSION"
echo "  - package.json"
echo "  - control-plane/package.json"
echo "  - web-ui/package.json"
echo "  - android/app/build.gradle.kts"
echo ""
echo "Next steps:"
echo "  git add -A"
echo "  git commit -m 'chore: bump version to v$NEW_VERSION'"
echo "  git tag v$NEW_VERSION"
echo ""
