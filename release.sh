#!/usr/bin/env bash
# Build a ready-to-install APK, signed with the ShackScreen release key, into ../Releases.
# Upload it to a GitHub release so people can install without building anything.
set -euo pipefail
cd "$(dirname "$0")"
[ -f ../Signing/signing.properties ] || { echo "ERROR: ../Signing/signing.properties is missing; releases must use the release key." >&2; exit 1; }
./build-and-install.sh >/dev/null
VERSION=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)
OUT=../Releases/ShackScreen-$VERSION.apk
mkdir -p ../Releases
cp app/build/outputs/apk/release/app-release.apk "$OUT"
# apksigner runs on Java too, and this shell didn't inherit build-and-install.sh's settings.
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)}"
export PATH="$JAVA_HOME/bin:$PATH"
APKSIGNER=$(ls -d "${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"/build-tools/*/apksigner | tail -1)
"$APKSIGNER" verify --print-certs "$OUT" | grep -E 'Signer #1 certificate (DN|SHA-256)'
echo "Release APK: $OUT"
