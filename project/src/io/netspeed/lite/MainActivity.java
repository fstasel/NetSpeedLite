package io.netspeed.lite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Single-screen minimal native UI for NetSpeed Lite.
 * Built with pure Android framework Views (zero external UI dependencies).
 */
public final class MainActivity extends Activity implements SpeedMonitorService.SpeedListener {
    private static final int REQ_POST_NOTIFICATIONS = 101;

    private TextView statusBadgeView;
    private TextView downSpeedView;
    private TextView upSpeedView;
    private TextView totalSpeedView;
    private TextView sessionTotalsView;
    private Button toggleServiceBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(0xFF0B1120);
            window.setNavigationBarColor(0xFF0B1120);
        }

        setContentView(buildContentView());

        ensureNotificationPermissionAndMaybeStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SpeedMonitorService.setSpeedListener(this);
        updateServiceUiState(SpeedMonitorService.isRunning());
    }

    @Override
    protected void onPause() {
        SpeedMonitorService.setSpeedListener(null);
        super.onPause();
    }

    private void ensureNotificationPermissionAndMaybeStart() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_POST_NOTIFICATIONS);
        } else if (Prefs.isServiceEnabled(this) && !SpeedMonitorService.isRunning()) {
            startMonitorService();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Prefs.isServiceEnabled(this) && !SpeedMonitorService.isRunning()) {
                    startMonitorService();
                }
            } else {
                updateServiceUiState(SpeedMonitorService.isRunning());
            }
        }
    }

    private void startMonitorService() {
        Prefs.setServiceEnabled(this, true);
        Intent intent = new Intent(this, SpeedMonitorService.class);
        intent.setAction(SpeedMonitorService.ACTION_START);
        startForegroundService(intent);
        updateServiceUiState(true);
    }

    private void stopMonitorService() {
        Prefs.setServiceEnabled(this, false);
        Intent intent = new Intent(this, SpeedMonitorService.class);
        intent.setAction(SpeedMonitorService.ACTION_STOP);
        startService(intent);
        updateServiceUiState(false);
    }

    private void notifyPrefsChanged() {
        if (SpeedMonitorService.isRunning()) {
            Intent intent = new Intent(this, SpeedMonitorService.class);
            intent.setAction(SpeedMonitorService.ACTION_RELOAD_PREFS);
            startService(intent);
        }
    }

    @Override
    public void onSpeedUpdated(
            final boolean running,
            final long rxBytesPerSec,
            final long txBytesPerSec,
            final long sessionRxBytes,
            final long sessionTxBytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateServiceUiState(running);
                downSpeedView.setText(SpeedFormatter.formatSpeed(rxBytesPerSec));
                upSpeedView.setText(SpeedFormatter.formatSpeed(txBytesPerSec));
                totalSpeedView.setText(SpeedFormatter.formatSpeed(rxBytesPerSec + txBytesPerSec));
                sessionTotalsView.setText(
                        "↓ " + SpeedFormatter.formatBytes(sessionRxBytes)
                        + "    •    ↑ " + SpeedFormatter.formatBytes(sessionTxBytes));
            }
        });
    }

    private void updateServiceUiState(boolean running) {
        boolean notifGranted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;

        if (!notifGranted) {
            statusBadgeView.setText("NOTIFICATION PERMISSION REQUIRED");
            statusBadgeView.setTextColor(0xFFFBBF24);
            statusBadgeView.setBackground(makeRoundedRect(0xFF451A03, 0xFFD97706, dp(16)));
        } else if (running) {
            statusBadgeView.setText("●  ACTIVE IN STATUS BAR");
            statusBadgeView.setTextColor(0xFF34D399);
            statusBadgeView.setBackground(makeRoundedRect(0xFF064E3B, 0xFF059669, dp(16)));
        } else {
            statusBadgeView.setText("○  MONITOR STOPPED");
            statusBadgeView.setTextColor(0xFF94A3B8);
            statusBadgeView.setBackground(makeRoundedRect(0xFF1E293B, 0xFF334155, dp(16)));
        }

        if (running) {
            toggleServiceBtn.setText("Stop Status Bar Monitor");
            toggleServiceBtn.setTextColor(Color.WHITE);
            toggleServiceBtn.setBackground(makeRoundedRect(0xFFBE123C, 0xFFE11D48, dp(12)));
        } else {
            toggleServiceBtn.setText("Start Status Bar Monitor");
            toggleServiceBtn.setTextColor(0xFF0B1120);
            toggleServiceBtn.setBackground(makeRoundedRect(0xFF38BDF8, 0xFF7DD3FC, dp(12)));
        }
    }

    private View buildContentView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF0B1120);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(24), pad, dp(32));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header
        TextView title = new TextView(this);
        title.setText("NetSpeed Lite");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Zero-dependency real-time TrafficStats monitor (Android 14+)");
        subtitle.setTextColor(0xFF94A3B8);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        root.addView(subtitle, subLp);

        // Status Pill
        statusBadgeView = new TextView(this);
        statusBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusBadgeView.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadgeView.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.topMargin = dp(14);
        badgeLp.bottomMargin = dp(18);
        root.addView(statusBadgeView, badgeLp);

        // Live Speed Row (Download & Upload Cards)
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(speedRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        downSpeedView = new TextView(this);
        LinearLayout downCard = buildMetricCard("↓ DOWNLOAD", downSpeedView, 0xFF38BDF8);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftLp.rightMargin = dp(8);
        speedRow.addView(downCard, leftLp);

        upSpeedView = new TextView(this);
        LinearLayout upCard = buildMetricCard("↑ UPLOAD", upSpeedView, 0xFF34D399);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.leftMargin = dp(8);
        speedRow.addView(upCard, rightLp);

        // Total + Session Card
        LinearLayout summaryCard = makeCardContainer();
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sumLp.topMargin = dp(14);
        root.addView(summaryCard, sumLp);

        LinearLayout totalRow = new LinearLayout(this);
        totalRow.setOrientation(LinearLayout.HORIZONTAL);
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        summaryCard.addView(totalRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView totalLabel = new TextView(this);
        totalLabel.setText("COMBINED THROUGHPUT");
        totalLabel.setTextColor(0xFF94A3B8);
        totalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        totalLabel.setTypeface(Typeface.DEFAULT_BOLD);
        totalRow.addView(totalLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        totalSpeedView = new TextView(this);
        totalSpeedView.setText("0.0 KB/s");
        totalSpeedView.setTextColor(Color.WHITE);
        totalSpeedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        totalSpeedView.setTypeface(Typeface.DEFAULT_BOLD);
        totalRow.addView(totalSpeedView);

        View divider = new View(this);
        divider.setBackgroundColor(0xFF1E293B);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divLp.topMargin = dp(12);
        divLp.bottomMargin = dp(12);
        summaryCard.addView(divider, divLp);

        LinearLayout sessionRow = new LinearLayout(this);
        sessionRow.setOrientation(LinearLayout.HORIZONTAL);
        sessionRow.setGravity(Gravity.CENTER_VERTICAL);
        summaryCard.addView(sessionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout sessionTextCol = new LinearLayout(this);
        sessionTextCol.setOrientation(LinearLayout.VERTICAL);
        sessionRow.addView(sessionTextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView sessionLabel = new TextView(this);
        sessionLabel.setText("SESSION DATA TRANSFERRED");
        sessionLabel.setTextColor(0xFF94A3B8);
        sessionLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sessionLabel.setTypeface(Typeface.DEFAULT_BOLD);
        sessionTextCol.addView(sessionLabel);

        sessionTotalsView = new TextView(this);
        sessionTotalsView.setText("↓ 0.0 KB    •    ↑ 0.0 KB");
        sessionTotalsView.setTextColor(0xFFE2E8F0);
        sessionTotalsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = dp(3);
        sessionTextCol.addView(sessionTotalsView, stLp);

        Button resetBtn = new Button(this);
        resetBtn.setText("Reset");
        resetBtn.setAllCaps(false);
        resetBtn.setTextColor(0xFFCBD5E1);
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        resetBtn.setPadding(dp(14), dp(6), dp(14), dp(6));
        resetBtn.setMinimumHeight(0);
        resetBtn.setMinHeight(0);
        resetBtn.setMinimumWidth(0);
        resetBtn.setMinWidth(0);
        resetBtn.setBackground(makeRoundedRect(0xFF1E293B, 0xFF334155, dp(8)));
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SpeedMonitorService.isRunning()) {
                    Intent intent = new Intent(MainActivity.this, SpeedMonitorService.class);
                    intent.setAction(SpeedMonitorService.ACTION_RESET_SESSION);
                    startService(intent);
                } else {
                    sessionTotalsView.setText("↓ 0.0 KB    •    ↑ 0.0 KB");
                }
            }
        });
        sessionRow.addView(resetBtn);

        // Start / Stop Button
        toggleServiceBtn = new Button(this);
        toggleServiceBtn.setAllCaps(false);
        toggleServiceBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        toggleServiceBtn.setTypeface(Typeface.DEFAULT_BOLD);
        toggleServiceBtn.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(16);
        root.addView(toggleServiceBtn, btnLp);

        toggleServiceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SpeedMonitorService.isRunning()) {
                    stopMonitorService();
                } else {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                REQ_POST_NOTIFICATIONS);
                        return;
                    }
                    startMonitorService();
                }
            }
        });

        // Settings Section: Status Bar Icon Mode
        LinearLayout iconModeCard = makeCardContainer();
        LinearLayout.LayoutParams imLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        imLp.topMargin = dp(20);
        root.addView(iconModeCard, imLp);

        TextView iconModeTitle = new TextView(this);
        iconModeTitle.setText("STATUS BAR ICON METRIC");
        iconModeTitle.setTextColor(0xFF38BDF8);
        iconModeTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        iconModeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        iconModeCard.addView(iconModeTitle);

        RadioGroup iconModeGroup = new RadioGroup(this);
        iconModeGroup.setOrientation(RadioGroup.VERTICAL);
        LinearLayout.LayoutParams rgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rgLp.topMargin = dp(8);
        iconModeCard.addView(iconModeGroup, rgLp);

        RadioButton rbTotal = makeRadioButton(10, "Total Speed (Download + Upload combined)");
        RadioButton rbDown = makeRadioButton(11, "Download Speed Only (↓)");
        RadioButton rbDom = makeRadioButton(12, "Dominant Direction with Arrow (↓ / ↑)");
        iconModeGroup.addView(rbTotal);
        iconModeGroup.addView(rbDown);
        iconModeGroup.addView(rbDom);

        int currentMode = Prefs.getIconMode(this);
        if (currentMode == Prefs.ICON_MODE_DOWNLOAD) {
            rbDown.setChecked(true);
        } else if (currentMode == Prefs.ICON_MODE_DOMINANT) {
            rbDom.setChecked(true);
        } else {
            rbTotal.setChecked(true);
        }

        iconModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int mode = Prefs.ICON_MODE_TOTAL;
                if (checkedId == 11) {
                    mode = Prefs.ICON_MODE_DOWNLOAD;
                } else if (checkedId == 12) {
                    mode = Prefs.ICON_MODE_DOMINANT;
                }
                Prefs.setIconMode(MainActivity.this, mode);
                notifyPrefsChanged();
            }
        });

        // Settings Section: Update Interval & Boot Switch
        LinearLayout intervalCard = makeCardContainer();
        LinearLayout.LayoutParams intLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        intLp.topMargin = dp(14);
        root.addView(intervalCard, intLp);

        TextView intervalTitle = new TextView(this);
        intervalTitle.setText("SAMPLING INTERVAL & BATTERY");
        intervalTitle.setTextColor(0xFF38BDF8);
        intervalTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        intervalTitle.setTypeface(Typeface.DEFAULT_BOLD);
        intervalCard.addView(intervalTitle);

        RadioGroup intervalGroup = new RadioGroup(this);
        intervalGroup.setOrientation(RadioGroup.VERTICAL);
        LinearLayout.LayoutParams irgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        irgLp.topMargin = dp(8);
        intervalCard.addView(intervalGroup, irgLp);

        RadioButton rb500 = makeRadioButton(20, "0.5 seconds (Instant response)");
        RadioButton rb1000 = makeRadioButton(21, "1.0 second (Recommended default)");
        RadioButton rb2000 = makeRadioButton(22, "2.0 seconds (Ultra-low CPU wakeup)");
        intervalGroup.addView(rb500);
        intervalGroup.addView(rb1000);
        intervalGroup.addView(rb2000);

        int currentInterval = Prefs.getIntervalMs(this);
        if (currentInterval == 500) {
            rb500.setChecked(true);
        } else if (currentInterval == 2000) {
            rb2000.setChecked(true);
        } else {
            rb1000.setChecked(true);
        }

        intervalGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int ms = 1000;
                if (checkedId == 20) {
                    ms = 500;
                } else if (checkedId == 22) {
                    ms = 2000;
                }
                Prefs.setIntervalMs(MainActivity.this, ms);
                notifyPrefsChanged();
            }
        });

        View div2 = new View(this);
        div2.setBackgroundColor(0xFF1E293B);
        LinearLayout.LayoutParams div2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        div2Lp.topMargin = dp(10);
        div2Lp.bottomMargin = dp(10);
        intervalCard.addView(div2, div2Lp);

        Switch bootSwitch = new Switch(this);
        bootSwitch.setText("Start automatically on device boot");
        bootSwitch.setTextColor(0xFFE2E8F0);
        bootSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bootSwitch.setChecked(Prefs.isStartOnBoot(this));
        bootSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setStartOnBoot(MainActivity.this, isChecked);
            }
        });
        intervalCard.addView(bootSwitch);

        TextView batteryNote = new TextView(this);
        batteryNote.setText("Note: Polling automatically pauses whenever the screen turns off and resumes on wake.");
        batteryNote.setTextColor(0xFF64748B);
        batteryNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams bnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bnLp.topMargin = dp(8);
        intervalCard.addView(batteryNote, bnLp);

        return scroll;
    }

    private LinearLayout buildMetricCard(String label, TextView valueView, int accentColor) {
        LinearLayout card = makeCardContainer();

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(accentColor);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(labelView);

        valueView.setText("0.0 KB/s");
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vLp.topMargin = dp(8);
        card.addView(valueView, vLp);

        return card;
    }

    private LinearLayout makeCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        card.setPadding(p, p, p, p);
        card.setBackground(makeRoundedRect(0xFF111C2D, 0xFF1E293B, dp(14)));
        return card;
    }

    private RadioButton makeRadioButton(int id, String label) {
        RadioButton rb = new RadioButton(this);
        rb.setId(id);
        rb.setText(label);
        rb.setTextColor(0xFFE2E8F0);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        rb.setPadding(dp(8), dp(6), dp(8), dp(6));
        return rb;
    }

    private GradientDrawable makeRoundedRect(int fillColor, int strokeColor, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        gd.setStroke(dp(1), strokeColor);
        return gd;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()));
    }
}
