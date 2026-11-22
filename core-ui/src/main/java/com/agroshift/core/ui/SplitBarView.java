package com.agroshift.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Animated two-part bar. Its screen always shows the values as text too. */
public final class SplitBarView extends View {
    private final Paint first = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint second = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private ValueAnimator animator;
    private float share;
    private boolean hasValue;

    public SplitBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        first.setColor(ContextCompat.getColor(context, R.color.zameen_accent));
        second.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setColors(@ColorRes int firstColor, @ColorRes int secondColor) {
        first.setColor(ContextCompat.getColor(getContext(), firstColor));
        second.setColor(ContextCompat.getColor(getContext(), secondColor));
        invalidate();
    }

    public void setValues(double firstValue, double secondValue) {
        double total = firstValue + secondValue;
        hasValue = total > 0;
        float target = hasValue ? (float) (firstValue / total) : 0f;
        if (animator != null) {
            animator.cancel();
        }
        if (!ZameenUi.motionEnabled(getContext())) {
            share = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(share, target);
        animator.setDuration(420);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(update -> {
            share = (float) update.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(Math.round(10 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = getHeight() / 2f;
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, second);
        if (!hasValue || share <= 0f) {
            return;
        }
        float size = Math.max(getHeight(), getWidth() * share);
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            canvas.drawRoundRect(getWidth() - size, 0, getWidth(), getHeight(), radius, radius, first);
        } else {
            canvas.drawRoundRect(0, 0, size, getHeight(), radius, radius, first);
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
