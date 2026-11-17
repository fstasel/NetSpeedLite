package io.netspeed.lite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts SpeedMonitorService automatically on device boot or app update
 * when the user has enabled "Start on Boot".
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            if (Prefs.isStartOnBoot(context) && Prefs.isServiceEnabled(context)) {
                Intent serviceIntent = new Intent(context, SpeedMonitorService.class);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
