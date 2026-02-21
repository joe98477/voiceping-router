#!/bin/bash
# build-apk.sh - Build and optionally install the VoicePing Android APK
# Usage:
#   ./scripts/build-apk.sh              # debug APK
#   ./scripts/build-apk.sh release      # release APK
#   ./scripts/build-apk.sh debug install # build + install to connected device

set -e

export ANDROID_HOME=/home/oppy/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

BUILD_TYPE="${1:-debug}"
ACTION="${2:-}"
ANDROID_DIR="$(cd "$(dirname "$0")/../android" && pwd)"
APK_OUT="$ANDROID_DIR/app/build/outputs/apk/$BUILD_TYPE"

echo "=== VoicePing APK Builder ==="
echo "Build type: $BUILD_TYPE"
echo "Android dir: $ANDROID_DIR"
echo ""

cd "$ANDROID_DIR"

if [ "$BUILD_TYPE" = "release" ]; then
  echo "Building release APK..."
  ./gradlew assembleRelease
  APK_FILE="$APK_OUT/app-release-unsigned.apk"
  [ -f "$APK_OUT/app-release.apk" ] && APK_FILE="$APK_OUT/app-release.apk"
else
  echo "Building debug APK..."
  ./gradlew assembleDebug
  APK_FILE="$APK_OUT/app-debug.apk"
fi

if [ -f "$APK_FILE" ]; then
  SIZE=$(du -h "$APK_FILE" | cut -f1)
  echo ""
  echo "✅ Build successful!"
  echo "   APK: $APK_FILE"
  echo "   Size: $SIZE"

  # Copy to a known location for easy access
  DEST="/home/oppy/apk-builds/voiceping-${BUILD_TYPE}-$(date +%Y%m%d-%H%M).apk"
  mkdir -p /home/oppy/apk-builds
  cp "$APK_FILE" "$DEST"
  echo "   Saved to: $DEST"
  echo "   Latest:   /home/oppy/apk-builds/voiceping-${BUILD_TYPE}-latest.apk"
  cp "$APK_FILE" "/home/oppy/apk-builds/voiceping-${BUILD_TYPE}-latest.apk"

  if [ "$ACTION" = "install" ]; then
    echo ""
    echo "Installing on connected device..."
    adb install -r "$APK_FILE"
    echo "✅ Installed"
  fi
else
  echo "❌ Build failed — APK not found at $APK_FILE"
  exit 1
fi
