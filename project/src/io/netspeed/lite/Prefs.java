package io.netspeed.lite;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String PREFS_NAME = "netspeed_prefs";

    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_ICON_MODE = "icon_mode";
    private static final String KEY_INTERVAL_MS = "interval_ms";
    private static final String KEY_START_ON_BOOT = "start_on_boot";

    public static final int ICON_MODE_TOTAL = 0;      // Down + Up combined
    public static final int ICON_MODE_DOWNLOAD = 1;   // Download only
    public static final int ICON_MODE_DOMINANT = 2;   // Dominant direction with arrow

    public static final int DEFAULT_INTERVAL_MS = 1000;

    private Prefs() {}

    private static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isServiceEnabled(Context context) {
        return get(context).getBoolean(KEY_SERVICE_ENABLED, true);
    }

    public static void setServiceEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }

    public static int getIconMode(Context context) {
        return get(context).getInt(KEY_ICON_MODE, ICON_MODE_TOTAL);
    }

    public static void setIconMode(Context context, int mode) {
        get(context).edit().putInt(KEY_ICON_MODE, mode).apply();
    }

    public static int getIntervalMs(Context context) {
        int val = get(context).getInt(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS);
        if (val != 500 && val != 1000 && val != 2000) {
            return DEFAULT_INTERVAL_MS;
        }
        return val;
    }

    public static void setIntervalMs(Context context, int intervalMs) {
        get(context).edit().putInt(KEY_INTERVAL_MS, intervalMs).apply();
    }

    public static boolean isStartOnBoot(Context context) {
        return get(context).getBoolean(KEY_START_ON_BOOT, false);
    }

    public static void setStartOnBoot(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_START_ON_BOOT, enabled).apply();
    }
}
