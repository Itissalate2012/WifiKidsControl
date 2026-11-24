#!/bin/bash
# Build script for WifiKidsControl Android APK

set -e

echo "🔨 Building WifiKidsControl APK..."

# Check for Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "❌ ANDROID_HOME not set. Please set it to your Android SDK path."
    exit 1
fi

# Check for Gradle
if ! command -v gradle &> /dev/null; then
    echo "📦 Using Gradle Wrapper..."
    GRADLE_CMD="./gradlew"
else
    GRADLE_CMD="gradle"
fi

cd "$(dirname "$0")/WifiKidsControl"

# Clean build
echo "🧹 Cleaning previous build..."
$GRADLE_CMD clean

# Build debug APK
echo "📱 Building Debug APK..."
$GRADLE_CMD assembleDebug

# Build release APK (requires signing config)
echo "📦 Building Release APK..."
$GRADLE_CMD assembleRelease

# Output locations
DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="app/build/outputs/apk/release/app-release.apk"

echo ""
echo "✅ Build complete!"
echo ""
echo "📱 Debug APK: $DEBUG_APK"
if [ -f "$RELEASE_APK" ]; then
    echo "📦 Release APK: $RELEASE_APK"
else
    echo "⚠️  Release APK not found (signing config needed)"
fi
echo ""
echo "📲 Install on device:"
echo "   adb install -r $DEBUG_APK"
echo ""
echo "🔧 For release build, set these env vars:"
echo "   export KEYSTORE_PATH=/path/to/keystore.jks"
echo "   export KEYSTORE_PASSWORD=your_store_password"
echo "   export KEY_ALIAS=your_key_alias"
echo "   export KEY_PASSWORD=your_key_password"

# Copy to project root for easy access
cp "$DEBUG_APK" ../WifiKidsControl-debug.apk 2>/dev/null || true
echo "📋 Debug APK copied to project root: WifiKidsControl-debug.apk"