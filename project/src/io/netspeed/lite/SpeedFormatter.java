package io.netspeed.lite;

import java.util.Locale;

/**
 * Formats instantaneous network throughput (bytes/sec) and cumulative byte totals.
 * Auto-scales between KB/s (< 1 MB/s) and MB/s (>= 1 MB/s) using base-1024.
 */
public final class SpeedFormatter {
    private static final double KB = 1024.0;
    private static final double MB = 1024.0 * 1024.0;
    private static final double GB = 1024.0 * 1024.0 * 1024.0;

    private SpeedFormatter() {}

    /**
     * Compact two-line components for the 24dp status bar icon:
     * line1 = numeric value (e.g. "0", "4.2", "350", "1.8", "42")
     * line2 = unit (e.g. "KB/s", "MB/s", or "↓MB/s" / "↑KB/s")
     */
    public static final class IconText {
        public final String value;
        public final String unit;

        public IconText(String value, String unit) {
            this.value = value;
            this.unit = unit;
        }
    }

    /**
     * Formats bytes per second for the compact status bar icon.
     */
    public static IconText formatForIcon(long bytesPerSec, String unitPrefix) {
        String prefix = (unitPrefix == null) ? "" : unitPrefix;
        if (bytesPerSec < 100) {
            return new IconText("0", prefix + "KB/s");
        }
        if (bytesPerSec < (long) MB) {
            double kb = bytesPerSec / KB;
            if (kb < 9.95) {
                return new IconText(String.format(Locale.US, "%.1f", kb), prefix + "KB/s");
            } else {
                return new IconText(String.format(Locale.US, "%.0f", kb), prefix + "KB/s");
            }
        } else {
            double mb = bytesPerSec / MB;
            if (mb < 9.95) {
                return new IconText(String.format(Locale.US, "%.1f", mb), prefix + "MB/s");
            } else if (mb < 99.95) {
                return new IconText(String.format(Locale.US, "%.1f", mb), prefix + "MB/s");
            } else {
                return new IconText(String.format(Locale.US, "%.0f", mb), prefix + "MB/s");
            }
        }
    }

    /**
     * Formats bytes per second for the notification shade and main dashboard
     * (e.g. "0.0 KB/s", "42.5 KB/s", "3.42 MB/s").
     */
    public static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec <= 0) {
            return "0.0 KB/s";
        }
        if (bytesPerSec < (long) MB) {
            double kb = bytesPerSec / KB;
            if (kb < 100.0) {
                return String.format(Locale.US, "%.1f KB/s", kb);
            } else {
                return String.format(Locale.US, "%.0f KB/s", kb);
            }
        } else {
            double mb = bytesPerSec / MB;
            if (mb < 10.0) {
                return String.format(Locale.US, "%.2f MB/s", mb);
            } else if (mb < 100.0) {
                return String.format(Locale.US, "%.1f MB/s", mb);
            } else {
                return String.format(Locale.US, "%.0f MB/s", mb);
            }
        }
    }

    /**
     * Formats cumulative session byte totals (KB / MB / GB).
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            bytes = 0;
        }
        if (bytes < (long) MB) {
            return String.format(Locale.US, "%.1f KB", bytes / KB);
        } else if (bytes < (long) GB) {
            return String.format(Locale.US, "%.2f MB", bytes / MB);
        } else {
            return String.format(Locale.US, "%.2f GB", bytes / GB);
        }
    }
}
