# NetSpeed Lite (`io.netspeed.lite`)

Ultra-lightweight, zero-dependency real-time Android 14+ (`minSdk 34`, `targetSdk 34`) network speed monitor that displays instantaneous upload/download speeds in the **status bar icon** and **notification shade** without root or network permissions.

---

## Features & Design

- **Passive Zero-Overhead Measurement**: Samples `TrafficStats.getTotalRxBytes()` and `TrafficStats.getTotalTxBytes()` using `SystemClock.elapsedRealtime()` delta calculation. Does **not** request `INTERNET` permission—100% offline & private.
- **Dynamic Status Bar Bitmap Icon**: Renders a crisp 96×96 (`24dp @ xxxhdpi`) two-line bitmap icon via `Icon.createWithBitmap(...)` directly in the top status bar, reusing `Bitmap`, `Canvas`, and `Paint` objects across ticks to eliminate GC churn.
- **Auto-Scaling Units (`KB/s` & `MB/s`)**:
  - `< 1 MB/s` displays as `KB/s`
  - `>= 1 MB/s` displays as `MB/s` (base-1024)
- **Configurable Status Bar Metric**:
  - **Total Speed** (`↓ + ↑` combined — default)
  - **Download Speed Only** (`↓`)
  - **Dominant Direction** (`↓` or `↑` prefix arrow)
- **Expanded Notification Shade Card**:
  - Title: `↓ <Download Speed>   •   ↑ <Upload Speed>`
  - Subtitle: `Session: ↓ <Rx Total>  |  ↑ <Tx Total>`
  - Silent `IMPORTANCE_LOW` ongoing notification with `FOREGROUND_SERVICE_IMMEDIATE` and a **Stop** action button.
- **Zero-Wakeup Screen-Off Optimization**:
  - Automatically pauses timer callbacks on `Intent.ACTION_SCREEN_OFF` and resumes immediately on `Intent.ACTION_SCREEN_ON` (while folding any bytes transferred during screen-off into the session total without causing a fake speed spike).
  - Skips redundant `NotificationManager.notify(...)` calls when idle (`0 KB/s` across consecutive ticks).
- **Android 14+ Compliant Foreground Service**:
  - Uses `foregroundServiceType="specialUse"` with `FOREGROUND_SERVICE_SPECIAL_USE` and the required `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property.

---

## Sandbox Directory Layout

Everything lives inside `/root/netspeed-sandbox` with zero global system modifications:

```text
/root/netspeed-sandbox/
├── build.sh                  # Fast standalone ARM64 build script (aapt2 + javac + R8 + zipalign + apksigner)
├── install.sh                # ADB install + permission grant + launch helper
├── build.gradle              # Standard Gradle root config
├── settings.gradle           # Standard Gradle settings
├── android-sdk/android-34/   # Local Android 14 (API 34) android.jar
├── sysroot/                  # Local ARM64 aapt2, zipalign, and apksigner runtime
├── tools/                    # Local R8 optimizer/DEX compiler & wrapper scripts
├── keystore/                 # Local release signing keystore
├── dist/                     # Output APK directory (NetSpeedLite-release.apk)
└── project/
    ├── AndroidManifest.xml
    ├── build.gradle
    ├── proguard-rules.pro
    ├── res/
    │   ├── drawable/ic_launcher.xml
    │   └── values/strings.xml
    └── src/io/netspeed/lite/
        ├── MainActivity.java
        ├── SpeedMonitorService.java
        ├── StatusBarIconRenderer.java
        ├── SpeedFormatter.java
        ├── Prefs.java
        └── BootReceiver.java
```

---

## Building & Installing

### 1. Build the Signed Release APK
```bash
cd /root/netspeed-sandbox
chmod +x build.sh install.sh
./build.sh
```
Output APK: `/root/netspeed-sandbox/dist/NetSpeedLite-release.apk`

### 2. Install via ADB
```bash
./install.sh
```
