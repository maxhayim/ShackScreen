#!/usr/bin/env bash
# Build ShackScreen and install it on a TV over network ADB:  TV_HOST=192.168.1.50 ./build-and-install.sh
# Leave TV_HOST unset to only build the APK.
set -euo pipefail
cd "$(dirname "$0")"
# macOS: java_home or Homebrew; Linux: the java on PATH.
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || { [ -d /opt/homebrew/opt/openjdk@17 ] && echo /opt/homebrew/opt/openjdk@17; } || true)}"
# Android Studio's SDK on macOS, then Linux, then Homebrew's command-line tools.
export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk ~/Android/Sdk /opt/homebrew/share/android-commandlinetools; do [ -d "$d" ] && echo "$d" && break; done)}"
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
# The release key, kept next to the repo and never in it; see app/build.gradle.
[ -f ../Signing/signing.properties ] && export SHACKSCREEN_SIGNING="${SHACKSCREEN_SIGNING:-$(cd ../Signing && pwd)/signing.properties}"
./gradlew assembleRelease -q --console=plain
APK=app/build/outputs/apk/release/app-release.apk
echo "Built $APK"
[ -n "${TV_HOST:-}" ] || exit 0
ADB="${ADB:-$(command -v adb || echo "$ANDROID_HOME/platform-tools/adb")}"
TARGET="$TV_HOST"; [[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"
"$ADB" connect "$TARGET"
echo "If the TV asks to allow debugging, accept it."
"$ADB" -s "$TARGET" install -r "$APK"
"$ADB" -s "$TARGET" shell am start -n com.maxhayim.shackscreen/.MainActivity
