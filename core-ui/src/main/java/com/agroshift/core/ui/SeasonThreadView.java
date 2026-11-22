package com.agroshift.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Zameen signature: today's position across Rabi and Kharif, mirrored in RTL. */
public final class SeasonThreadView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float fraction;

    public SeasonThreadView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        track.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        track.setStrokeWidth(2 * density);
        progress.setColor(ContextCompat.getColor(context, R.color.zameen_accent));
        progress.setStrokeWidth(3 * density);
        progress.setStrokeCap(Paint.Cap.ROUND);
        knobFill.setColor(ContextCompat.getColor(context, R.color.zameen_background));
    }

    public void setFraction(float value) {
        fraction = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                resolveSize(Math.round(18 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = 6 * density;
        float start = radius + progress.getStrokeWidth() / 2;
        float end = getWidth() - start;
        float y = getHeight() / 2f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float from = rtl ? end : start;
        float to = rtl ? start : end;
        float current = from + (to - from) * fraction;
        float middle = (from + to) / 2;
        canvas.drawLine(start, y, end, y, track);
        canvas.drawLine(middle, y - 5 * density, middle, y + 5 * density, track);
        canvas.drawLine(from, y, current, y, progress);
        canvas.drawCircle(current, y, radius, knobFill);
        Paint.Style style = progress.getStyle();
        progress.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(current, y, radius, progress);
        progress.setStyle(style);
    }
}
