package io.netspeed.lite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;

/**
 * Renders the compact two-line network speed bitmap for the Android status bar small icon.
 * Reuses its Bitmap, Canvas, and Paint instances to avoid per-tick allocations.
 */
public final class StatusBarIconRenderer {
    private static final int ICON_SIZE = 96; // 24dp @ xxxhdpi (4x) for sharp edges

    private final Bitmap bitmap;
    private final Canvas canvas;
    private final Paint valuePaint;
    private final Paint unitPaint;
    private final Rect textBounds = new Rect();

    public StatusBarIconRenderer() {
        this.bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        this.canvas = new Canvas(bitmap);

        Typeface boldSans = Typeface.create("sans-serif-condensed", Typeface.BOLD);

        this.valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        this.valuePaint.setColor(Color.WHITE);
        this.valuePaint.setTypeface(boldSans);
        this.valuePaint.setTextAlign(Paint.Align.CENTER);

        this.unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        this.unitPaint.setColor(Color.WHITE);
        this.unitPaint.setTypeface(boldSans);
        this.unitPaint.setTextAlign(Paint.Align.CENTER);
    }

    public synchronized Icon render(String valueText, String unitText) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // Top line: numeric speed value (auto-fit width inside 92px)
        float valueTextSize = 54f;
        valuePaint.setTextSize(valueTextSize);
        float measuredWidth = valuePaint.measureText(valueText);
        if (measuredWidth > 90f) {
            valueTextSize = valueTextSize * (90f / measuredWidth);
            valuePaint.setTextSize(valueTextSize);
        }
        valuePaint.getTextBounds(valueText, 0, valueText.length(), textBounds);
        float valueY = 50f;
        canvas.drawText(valueText, ICON_SIZE / 2f, valueY, valuePaint);

        // Bottom line: unit (e.g. "KB/s", "MB/s", "↓MB/s")
        float unitTextSize = 31f;
        unitPaint.setTextSize(unitTextSize);
        float unitWidth = unitPaint.measureText(unitText);
        if (unitWidth > 92f) {
            unitTextSize = unitTextSize * (92f / unitWidth);
            unitPaint.setTextSize(unitTextSize);
        }
        float unitY = 88f;
        canvas.drawText(unitText, ICON_SIZE / 2f, unitY, unitPaint);

        return Icon.createWithBitmap(bitmap);
    }
}
