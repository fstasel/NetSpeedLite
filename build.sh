#!/usr/bin/env bash
set -euo pipefail

SANDBOX="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT="${SANDBOX}/project"
BUILD="${SANDBOX}/build"
DIST="${SANDBOX}/dist"
KEYSTORE="${SANDBOX}/keystore/netspeed-release.jks"
KS_PASS="NetSpeed2026!"
KEY_ALIAS="netspeed"

ANDROID_JAR="${SANDBOX}/android-sdk/android-34/android.jar"
R8_JAR="${SANDBOX}/tools/r8.jar"
AAPT2="${SANDBOX}/tools/bin/aapt2"
ZIPALIGN="${SANDBOX}/tools/bin/zipalign"
APKSIGNER="${SANDBOX}/tools/bin/apksigner"

echo "=== [0/6] Ensuring local sandbox SDK & ARM64 build tools ==="
mkdir -p "${SANDBOX}/android-sdk/android-34" "${SANDBOX}/tools/bin" "${SANDBOX}/keystore"

if [ ! -f "${ANDROID_JAR}" ] && [ -f "/root/aa-sandbox/android-sdk/android-34/android.jar" ]; then
    cp "/root/aa-sandbox/android-sdk/android-34/android.jar" "${ANDROID_JAR}"
fi
if [ ! -f "${R8_JAR}" ] && [ -f "/root/aa-sandbox/tools/r8.jar" ]; then
    cp "/root/aa-sandbox/tools/r8.jar" "${R8_JAR}"
fi
if [ ! -d "${SANDBOX}/sysroot" ] && [ -d "/root/aa-sandbox/sysroot" ]; then
    cp -a "/root/aa-sandbox/sysroot" "${SANDBOX}/sysroot"
fi

cat > "${AAPT2}" << 'EOF'
#!/usr/bin/env bash
SANDBOX_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export LD_LIBRARY_PATH="${SANDBOX_DIR}/sysroot/usr/lib:${SANDBOX_DIR}/sysroot/usr/lib/aarch64-linux-gnu:${SANDBOX_DIR}/sysroot/usr/lib/aarch64-linux-gnu/android:${SANDBOX_DIR}/sysroot/usr/lib/7zip:${LD_LIBRARY_PATH:-}"
exec "${SANDBOX_DIR}/sysroot/usr/lib/android-sdk/build-tools/debian/aapt2" "$@"
EOF

cat > "${ZIPALIGN}" << 'EOF'
#!/usr/bin/env bash
SANDBOX_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export LD_LIBRARY_PATH="${SANDBOX_DIR}/sysroot/usr/lib:${SANDBOX_DIR}/sysroot/usr/lib/aarch64-linux-gnu:${SANDBOX_DIR}/sysroot/usr/lib/aarch64-linux-gnu/android:${SANDBOX_DIR}/sysroot/usr/lib/7zip:${LD_LIBRARY_PATH:-}"
exec "${SANDBOX_DIR}/sysroot/usr/lib/android-sdk/build-tools/debian/zipalign" "$@"
EOF

cat > "${APKSIGNER}" << 'EOF'
#!/usr/bin/env bash
SANDBOX_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec /usr/bin/java -jar "${SANDBOX_DIR}/sysroot/usr/share/java/apksigner.jar" "$@"
EOF

chmod +x "${AAPT2}" "${ZIPALIGN}" "${APKSIGNER}"

echo "=== [1/6] Preparing build directories & release keystore ==="
rm -rf "${BUILD}"
mkdir -p "${BUILD}/compiled_res" "${BUILD}/gen" "${BUILD}/classes" "${BUILD}/dex" "${DIST}"

if [ ! -f "${KEYSTORE}" ]; then
    keytool -genkeypair \
        -v \
        -keystore "${KEYSTORE}" \
        -alias "${KEY_ALIAS}" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "${KS_PASS}" \
        -keypass "${KS_PASS}" \
        -dname "CN=NetSpeedLite, OU=Mobile, O=NetSpeed, L=Tech, S=CA, C=US"
fi

echo "=== [2/6] Compiling Android resources with native ARM64 aapt2 ==="
"${AAPT2}" compile --dir "${PROJECT}/res" -o "${BUILD}/compiled_res/resources.zip"

echo "=== [3/6] Linking APK resource package (API 34) ==="
"${AAPT2}" link \
    -I "${ANDROID_JAR}" \
    --manifest "${PROJECT}/AndroidManifest.xml" \
    --java "${BUILD}/gen" \
    -o "${BUILD}/resources.ap_" \
    --auto-add-overlay \
    "${BUILD}/compiled_res/resources.zip"

echo "=== [4/6] Compiling Java sources (JDK -> Bytecode) ==="
find "${PROJECT}/src" "${BUILD}/gen" -name "*.java" > "${BUILD}/sources.txt"
javac --release 11 \
    -cp "${ANDROID_JAR}" \
    -d "${BUILD}/classes" \
    @"${BUILD}/sources.txt"

jar cf "${BUILD}/app-classes.jar" -C "${BUILD}/classes" .

echo "=== [5/6] Running R8 optimizer & DEX compiler (min-api 34) ==="
java -cp "${R8_JAR}" com.android.tools.r8.R8 \
    --release \
    --min-api 34 \
    --lib "${ANDROID_JAR}" \
    --pg-conf "${PROJECT}/proguard-rules.pro" \
    --output "${BUILD}/dex" \
    "${BUILD}/app-classes.jar"

echo "=== [6/6] Packaging, Zipaligning & Signing NetSpeedLite-release.apk ==="
cp "${BUILD}/resources.ap_" "${BUILD}/unsigned.apk"
(cd "${BUILD}/dex" && zip -q "${BUILD}/unsigned.apk" classes*.dex)

"${ZIPALIGN}" -p -f 4 "${BUILD}/unsigned.apk" "${BUILD}/aligned.apk"

"${APKSIGNER}" sign \
    --ks "${KEYSTORE}" \
    --ks-key-alias "${KEY_ALIAS}" \
    --ks-pass "pass:${KS_PASS}" \
    --key-pass "pass:${KS_PASS}" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "${DIST}/NetSpeedLite-release.apk" \
    "${BUILD}/aligned.apk"

"${APKSIGNER}" verify --verbose "${DIST}/NetSpeedLite-release.apk" | head -n 12

echo "=== BUILD COMPLETE ==="
ls -lh "${DIST}/NetSpeedLite-release.apk"

if [ -d "/sdcard/Download" ]; then
    mkdir -p "/sdcard/Download/NetSpeedLite"
    cp -f "${DIST}/NetSpeedLite-release.apk" "/sdcard/Download/NetSpeedLite/NetSpeedLite-release.apk"
    echo "Copied to /sdcard/Download/NetSpeedLite/NetSpeedLite-release.apk"
fi
