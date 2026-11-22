package com.agroshift.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Simple growing plants showing a crop's progress through its season. */
public final class CropGrowthView extends View {
    private final Paint soil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plant = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leaf = new Path();
    private final float density;
    private ValueAnimator animator;
    private float growth;

    public CropGrowthView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        soil.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        soil.setStrokeWidth(3 * density);
        soil.setStrokeCap(Paint.Cap.ROUND);
        plant.setColor(0xFF8FBF55);
        plant.setStyle(Paint.Style.STROKE);
        plant.setStrokeWidth(2.2f * density);
        plant.setStrokeCap(Paint.Cap.ROUND);
        head.setColor(ContextCompat.getColor(context, R.color.zameen_accent));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setGrowth(float value) {
        float target = Math.max(0f, Math.min(1f, value));
        if (animator != null) {
            animator.cancel();
        }
        if (!ZameenUi.motionEnabled(getContext())) {
            growth = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(growth, target);
        animator.setDuration(220);
        animator.addUpdateListener(update -> {
            growth = (float) update.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float ground = getHeight() - 6 * density;
        canvas.drawLine(4 * density, ground, getWidth() - 4 * density, ground, soil);
        if (growth <= 0.01f) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            float x = getWidth() * (i + 1) / 4f;
            float scale = i == 1 ? 1f : 0.82f;
            drawPlant(canvas, x, ground, (ground - 8 * density) * growth * scale);
        }
    }

    private void drawPlant(Canvas canvas, float x, float ground, float height) {
        canvas.drawLine(x, ground, x, ground - height, plant);
        int leaves = 1 + Math.round(growth * 3);
        for (int i = 1; i <= leaves; i++) {
            float y = ground - height * i / (leaves + 1f);
            float reach = (7 + 5 * growth) * density;
            float side = i % 2 == 0 ? 1 : -1;
            leaf.reset();
            leaf.moveTo(x, y);
            leaf.quadTo(x + side * reach * 0.6f, y - reach * 0.8f, x + side * reach, y - reach * 0.2f);
            canvas.drawPath(leaf, plant);
        }
        if (growth > 0.6f) {
            float size = (growth - 0.6f) * 12 * density + 2 * density;
            canvas.drawOval(x - size * 0.6f, ground - height - size * 1.6f, x + size * 0.6f, ground - height + size * 0.4f, head);
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
