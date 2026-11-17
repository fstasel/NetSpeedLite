#!/usr/bin/env bash
set -euo pipefail

SANDBOX="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="${1:-${SANDBOX}/dist/NetSpeedLite-release.apk}"
PKG="io.netspeed.lite"

if [ ! -f "${APK_PATH}" ]; then
    echo "Error: APK not found at ${APK_PATH}"
    echo "Run ./build.sh first."
    exit 1
fi

echo "=== Installing ${APK_PATH} via ADB ==="
adb install -r "${APK_PATH}"

echo "=== Granting POST_NOTIFICATIONS permission & launching NetSpeed Lite ==="
adb shell pm grant "${PKG}" android.permission.POST_NOTIFICATIONS || true
adb shell am start -n "${PKG}/.MainActivity"
echo "✅ NetSpeed Lite installed and launched!"
