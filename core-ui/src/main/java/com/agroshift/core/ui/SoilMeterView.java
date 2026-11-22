package com.agroshift.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Estimated soil water level. The value is always paired with text by its screen. */
public final class SoilMeterView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int normal;
    private final int low;
    private final float density;
    private ValueAnimator animator;
    private float level;

    public SoilMeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        track.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        normal = ContextCompat.getColor(context, R.color.zameen_canal);
        low = ContextCompat.getColor(context, R.color.zameen_error);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setLevel(float value) {
        float target = Math.max(0f, Math.min(1f, value));
        if (animator != null) {
            animator.cancel();
        }
        if (!ZameenUi.motionEnabled(getContext())) {
            level = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(level, target);
        animator.setDuration(650);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(update -> {
            level = (float) update.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(Math.round(14 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = getHeight() / 2f;
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, track);
        if (level <= 0f) {
            return;
        }
        fill.setColor(level < 0.3f ? low : normal);
        float size = Math.max(getHeight(), getWidth() * level);
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            canvas.drawRoundRect(getWidth() - size, 0, getWidth(), getHeight(), radius, radius, fill);
        } else {
            canvas.drawRoundRect(0, 0, size, getHeight(), radius, radius, fill);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }
}
