package io.netspeed.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Lightweight Foreground Service (specialUse on Android 14+) that samples TrafficStats
 * Rx/Tx counters and updates the status bar icon + notification shade.
 * Automatically pauses sampling while the device screen is off.
 */
public final class SpeedMonitorService extends Service {
    public static final String ACTION_START = "io.netspeed.lite.ACTION_START";
    public static final String ACTION_STOP = "io.netspeed.lite.ACTION_STOP";
    public static final String ACTION_RELOAD_PREFS = "io.netspeed.lite.ACTION_RELOAD_PREFS";
    public static final String ACTION_RESET_SESSION = "io.netspeed.lite.ACTION_RESET_SESSION";

    private static final String CHANNEL_ID = "netspeed_live_channel";
    private static final int NOTIFICATION_ID = 1001;

    public interface SpeedListener {
        void onSpeedUpdated(
                 boolean running,
                long rxBytesPerSec,
                long txBytesPerSec,
                long sessionRxBytes,
                long sessionTxBytes);
    }

    private static volatile boolean sRunning = false;
    private static volatile SpeedListener sListener = null;
    private static volatile long sLastRxSpeed = 0L;
    private static volatile long sLastTxSpeed = 0L;
    private static volatile long sSessionRxBytes = 0L;
    private static volatile long sSessionTxBytes = 0L;

    public static boolean isRunning() {
        return sRunning;
    }

    public static void setSpeedListener(SpeedListener listener) {
        sListener = listener;
        if (listener != null) {
            listener.onSpeedUpdated(sRunning, sLastRxSpeed, sLastTxSpeed, sSessionRxBytes, sSessionTxBytes);
        }
    }

    private Handler handler;
    private NotificationManager notificationManager;
    private StatusBarIconRenderer iconRenderer;
    private PendingIntent contentPendingIntent;
    private Notification.Action stopAction;

    private int intervalMs = Prefs.DEFAULT_INTERVAL_MS;
    private int iconMode = Prefs.ICON_MODE_TOTAL;
    private boolean screenInteractive = true;
    private boolean screenReceiverRegistered = false;

    private long prevRxBytes = TrafficStats.UNSUPPORTED;
    private long prevTxBytes = TrafficStats.UNSUPPORTED;
    private long prevElapsedMs = 0L;

    private String lastNotifSignature = "";

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sRunning || !screenInteractive) {
                return;
            }
            sampleAndNotify(false);
            handler.postDelayed(this, intervalMs);
        }
    };

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenInteractive = false;
                handler.removeCallbacks(tickRunnable);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                screenInteractive = true;
                // Record any bytes transferred while screen was off into session total,
                // then reset the instantaneous baseline so we don't show a multi-minute spike.
                long nowRx = TrafficStats.getTotalRxBytes();
                long nowTx = TrafficStats.getTotalTxBytes();
                if (nowRx != TrafficStats.UNSUPPORTED && prevRxBytes != TrafficStats.UNSUPPORTED && nowRx >= prevRxBytes) {
                    sSessionRxBytes += (nowRx - prevRxBytes);
                }
                if (nowTx != TrafficStats.UNSUPPORTED && prevTxBytes != TrafficStats.UNSUPPORTED && nowTx >= prevTxBytes) {
                    sSessionTxBytes += (nowTx - prevTxBytes);
                }
                prevRxBytes = nowRx;
                prevTxBytes = nowTx;
                prevElapsedMs = SystemClock.elapsedRealtime();

                handler.removeCallbacks(tickRunnable);
                sampleAndNotify(true);
                handler.postDelayed(tickRunnable, intervalMs);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        notificationManager = getSystemService(NotificationManager.class);
        iconRenderer = new StatusBarIconRenderer();

        createNotificationChannel();
        initPendingIntents();
        reloadPrefs();
        resetBaseline();

        PowerManager pm = getSystemService(PowerManager.class);
        screenInteractive = (pm == null) || pm.isInteractive();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        screenReceiverRegistered = true;

        sRunning = true;
        Notification initialNotif = buildNotification(0L, 0L);
        startForeground(
                NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                Prefs.setServiceEnabled(this, false);
                stopMonitorService();
                return START_NOT_STICKY;
            } else if (ACTION_RESET_SESSION.equals(action)) {
                sSessionRxBytes = 0L;
                sSessionTxBytes = 0L;
                lastNotifSignature = "";
                sampleAndNotify(true);
                return START_STICKY;
            } else if (ACTION_RELOAD_PREFS.equals(action)) {
                reloadPrefs();
                lastNotifSignature = "";
                handler.removeCallbacks(tickRunnable);
                if (screenInteractive) {
                    sampleAndNotify(true);
                    handler.postDelayed(tickRunnable, intervalMs);
                }
                return START_STICKY;
            }
        }

        Prefs.setServiceEnabled(this, true);
        reloadPrefs();
        sRunning = true;
        handler.removeCallbacks(tickRunnable);
        if (screenInteractive) {
            sampleAndNotify(true);
            handler.postDelayed(tickRunnable, intervalMs);
        }
        return START_STICKY;
    }

    private void reloadPrefs() {
        intervalMs = Prefs.getIntervalMs(this);
        iconMode = Prefs.getIconMode(this);
    }

    private void resetBaseline() {
        prevRxBytes = TrafficStats.getTotalRxBytes();
        prevTxBytes = TrafficStats.getTotalTxBytes();
        prevElapsedMs = SystemClock.elapsedRealtime();
    }

    private void sampleAndNotify(boolean forceUpdate) {
        long nowRx = TrafficStats.getTotalRxBytes();
        long nowTx = TrafficStats.getTotalTxBytes();
        long nowElapsed = SystemClock.elapsedRealtime();

        long rxPerSec = 0L;
        long txPerSec = 0L;

        long deltaMs = nowElapsed - prevElapsedMs;
        if (deltaMs > 0
                && nowRx != TrafficStats.UNSUPPORTED
                && nowTx != TrafficStats.UNSUPPORTED
                && prevRxBytes != TrafficStats.UNSUPPORTED
                && prevTxBytes != TrafficStats.UNSUPPORTED) {
            long deltaRx = Math.max(0L, nowRx - prevRxBytes);
            long deltaTx = Math.max(0L, nowTx - prevTxBytes);

            sSessionRxBytes += deltaRx;
            sSessionTxBytes += deltaTx;

            rxPerSec = (deltaRx * 1000L) / deltaMs;
            txPerSec = (deltaTx * 1000L) / deltaMs;
        }

        prevRxBytes = nowRx;
        prevTxBytes = nowTx;
        prevElapsedMs = nowElapsed;

        sLastRxSpeed = rxPerSec;
        sLastTxSpeed = txPerSec;

        SpeedListener listener = sListener;
        if (listener != null) {
            listener.onSpeedUpdated(sRunning, rxPerSec, txPerSec, sSessionRxBytes, sSessionTxBytes);
        }

        // Compute display strings and avoid redundant SystemUI notification posts when idle
        SpeedFormatter.IconText iconText = resolveIconText(rxPerSec, txPerSec);
        String title = "↓ " + SpeedFormatter.formatSpeed(rxPerSec)
                + "   •   ↑ " + SpeedFormatter.formatSpeed(txPerSec);
        String subtitle = "Session: ↓ " + SpeedFormatter.formatBytes(sSessionRxBytes)
                + "  |  ↑ " + SpeedFormatter.formatBytes(sSessionTxBytes);

        String signature = iconText.value + "|" + iconText.unit + "|" + title + "|" + subtitle;
        if (forceUpdate || !signature.equals(lastNotifSignature)) {
            lastNotifSignature = signature;
            Notification notification = buildNotificationWithFormatted(iconText, title, subtitle);
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private SpeedFormatter.IconText resolveIconText(long rxPerSec, long txPerSec) {
        if (iconMode == Prefs.ICON_MODE_DOWNLOAD) {
            return SpeedFormatter.formatForIcon(rxPerSec, "");
        } else if (iconMode == Prefs.ICON_MODE_DOMINANT) {
            if (txPerSec > rxPerSec && txPerSec >= 100L) {
                return SpeedFormatter.formatForIcon(txPerSec, "↑");
            } else if (rxPerSec >= 100L) {
                return SpeedFormatter.formatForIcon(rxPerSec, "↓");
            } else {
                return SpeedFormatter.formatForIcon(0L, "");
            }
        } else {
            return SpeedFormatter.formatForIcon(rxPerSec + txPerSec, "");
        }
    }

    private Notification buildNotification(long rxPerSec, long txPerSec) {
        SpeedFormatter.IconText iconText = resolveIconText(rxPerSec, txPerSec);
        String title = "↓ " + SpeedFormatter.formatSpeed(rxPerSec)
                + "   •   ↑ " + SpeedFormatter.formatSpeed(txPerSec);
        String subtitle = "Session: ↓ " + SpeedFormatter.formatBytes(sSessionRxBytes)
                + "  |  ↑ " + SpeedFormatter.formatBytes(sSessionTxBytes);
        return buildNotificationWithFormatted(iconText, title, subtitle);
    }

    private Notification buildNotificationWithFormatted(
            SpeedFormatter.IconText iconText,
            String title,
            String subtitle) {
        Icon smallIcon = iconRenderer.render(iconText.value, iconText.unit);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(stopAction)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Live Network Speed",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Displays real-time network upload and download throughput in the status bar.");
        channel.setShowBadge(false);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
    }

    private void initPendingIntents() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        contentPendingIntent = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, SpeedMonitorService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        stopAction = new Notification.Action.Builder(
                null,
                "Stop",
                stopPendingIntent).build();
    }

    private void stopMonitorService() {
        sRunning = false;
        sLastRxSpeed = 0L;
        sLastTxSpeed = 0L;
        handler.removeCallbacks(tickRunnable);
        SpeedListener listener = sListener;
        if (listener != null) {
            listener.onSpeedUpdated(false, 0L, 0L, sSessionRxBytes, sSessionTxBytes);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        handler.removeCallbacks(tickRunnable);
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            screenReceiverRegistered = false;
        }
        SpeedListener listener = sListener;
        if (listener != null) {
            listener.onSpeedUpdated(false, 0L, 0L, sSessionRxBytes, sSessionTxBytes);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
