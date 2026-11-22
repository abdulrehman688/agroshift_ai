package com.agroshift.core.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;

/** A field that visibly changes with the selected What-If weather and crop score. */
public final class WhatIfFieldView extends View {
    private static final long FRAME_MILLIS = 33;
    private static final int PLANTS = 13;

    private final ArgbEvaluator colors = new ArgbEvaluator();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path leaf = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private final android.graphics.Rect visibleArea = new android.graphics.Rect();
    private final Runnable frame = this::nextFrame;
    private ValueAnimator animator;
    private float heat;
    private float dry;
    private float health = 0.7f;
    private float time;
    private boolean running;
    private long lastFrame;

    public WhatIfFieldView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setScene(boolean hotter, boolean drier, float cropHealth) {
        float fromHeat = heat;
        float fromDry = dry;
        float fromHealth = health;
        float toHeat = hotter ? 1f : 0f;
        float toDry = drier ? 1f : 0f;
        float toHealth = Math.max(0f, Math.min(1f, cropHealth));
        if (animator != null) {
            animator.cancel();
        }
        if (!ZameenUi.motionEnabled(getContext())) {
            heat = toHeat;
            dry = toDry;
            health = toHealth;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(560);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(update -> {
            float t = (float) update.getAnimatedValue();
            heat = fromHeat + (toHeat - fromHeat) * t;
            dry = fromDry + (toDry - fromDry) * t;
            health = fromHealth + (toHealth - fromHealth) * t;
            invalidate();
        });
        animator.start();
    }

    private void sync() {
        boolean should = isAttachedToWindow() && isShown() && ZameenUi.motionEnabled(getContext());
        if (should && !running) {
            running = true;
            lastFrame = SystemClock.uptimeMillis();
            postOnAnimation(frame);
        } else if (!should && running) {
            running = false;
            removeCallbacks(frame);
        }
    }

    private void nextFrame() {
        if (!running) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        time += Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (getGlobalVisibleRect(visibleArea)) {
            invalidate();
        }
        postOnAnimationDelayed(frame, FRAME_MILLIS);
    }

    private int mix(int from, int to, float amount) {
        return (Integer) colors.evaluate(Math.max(0f, Math.min(1f, amount)), from, to);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(Math.round(190 * density), heightSpec));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        clip.reset();
        rect.set(0, 0, width, height);
        clip.addRoundRect(rect, 18 * density, 18 * density, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float horizon = height * 0.5f;
        canvas.save();
        canvas.clipPath(clip);

        fill.setColor(mix(mix(0xFF29434D, 0xFF47503F, dry), 0xFF76512A, heat));
        canvas.drawRect(0, 0, width, horizon, fill);
        fill.setColor(0xFFF2D25F);
        canvas.drawCircle(width * 0.8f, height * 0.2f, (13 + 10 * heat) * density, fill);
        drawClouds(canvas, width, height);

        fill.setColor(mix(0xFF2F4522, 0xFF66512F, dry * 0.8f + heat * 0.2f));
        canvas.drawRect(0, horizon, width, height, fill);
        drawCracks(canvas, width, horizon, height);
        drawCrops(canvas, width, horizon, height);
        drawRain(canvas, width, horizon);
        drawHeat(canvas, width, horizon);
        canvas.restore();
    }

    private void drawClouds(Canvas canvas, float width, float height) {
        int alpha = Math.round(165 * (1 - dry));
        if (alpha <= 0) {
            return;
        }
        fill.setColor(Color.argb(alpha, 226, 232, 226));
        float drift = (time * 7 * density) % (width + 90 * density) - 45 * density;
        for (int i = 0; i < 2; i++) {
            float x = (drift + i * width * 0.55f) % (width + 60 * density);
            float y = height * (0.15f + i * 0.08f);
            canvas.drawOval(x - 28 * density, y - 9 * density, x + 28 * density, y + 9 * density, fill);
            canvas.drawCircle(x - 7 * density, y - 7 * density, 11 * density, fill);
            canvas.drawCircle(x + 10 * density, y - 5 * density, 9 * density, fill);
        }
    }

    private void drawRain(Canvas canvas, float width, float horizon) {
        int alpha = Math.round(120 * (1 - dry));
        if (alpha <= 0) {
            return;
        }
        line.setColor(Color.argb(alpha, 200, 228, 238));
        line.setStrokeWidth(1.3f * density);
        for (int i = 0; i < 18; i++) {
            float x = (i * 37 * density) % width;
            float y = ((time * 120 + i * 29) * density) % horizon;
            canvas.drawLine(x, y, x - 2 * density, y + 9 * density, line);
        }
    }

    private void drawCracks(Canvas canvas, float width, float horizon, float height) {
        int alpha = Math.round(150 * dry);
        if (alpha <= 0) {
            return;
        }
        line.setColor(Color.argb(alpha, 48, 34, 20));
        line.setStrokeWidth(1.4f * density);
        for (int i = 0; i < 9; i++) {
            float x = width * (i + 0.5f) / 9;
            float y = horizon + (height - horizon) * (0.25f + (i % 3) * 0.26f);
            canvas.drawLine(x - 15 * density, y, x - 3 * density, y + 5 * density, line);
            canvas.drawLine(x - 3 * density, y + 5 * density, x + 5 * density, y - 2 * density, line);
            canvas.drawLine(x + 5 * density, y - 2 * density, x + 15 * density, y + 3 * density, line);
        }
    }

    private void drawCrops(Canvas canvas, float width, float horizon, float height) {
        int color = mix(0xFFC8AD5C, 0xFF8FCB55, (health - 0.25f) / 0.55f);
        line.setColor(color);
        for (int row = 0; row < 3; row++) {
            float ground = horizon + (height - horizon) * (0.36f + row * 0.29f);
            float scale = 0.65f + row * 0.22f;
            float plantHeight = (11 + 28 * health) * density * scale;
            line.setStrokeWidth((1.4f + row * 0.4f) * density);
            for (int i = 0; i < PLANTS; i++) {
                float x = width * (i + (row % 2 == 0 ? 0.5f : 0.15f)) / PLANTS;
                float sway = (float) Math.sin(time * 1.7f + i * 0.7f + row) * 2.4f * density * scale;
                float tipX = x + sway;
                float tipY = ground - plantHeight;
                canvas.drawLine(x, ground, tipX, tipY, line);
                float reach = (4 + 6 * health) * density * scale;
                float midY = ground - plantHeight * 0.55f;
                leaf.reset();
                leaf.moveTo(x + sway * 0.5f, midY);
                leaf.quadTo(x - reach * 0.7f, midY - reach, x - reach, midY - reach * 0.1f);
                leaf.moveTo(x + sway * 0.5f, midY);
                leaf.quadTo(x + reach * 0.7f, midY - reach, x + reach, midY - reach * 0.1f);
                canvas.drawPath(leaf, line);
            }
        }
    }

    private void drawHeat(Canvas canvas, float width, float horizon) {
        int alpha = Math.round(70 * heat);
        if (alpha <= 0) {
            return;
        }
        line.setColor(Color.argb(alpha, 255, 214, 150));
        line.setStrokeWidth(1.7f * density);
        for (int row = 0; row < 3; row++) {
            float y = horizon - (row * 14 + (time * 9) % 14) * density;
            float previousX = 0;
            float previousY = y;
            for (float x = 14 * density; x <= width + 14 * density; x += 14 * density) {
                float nextY = y + (float) Math.sin(x / (18 * density) + time * 2.2f + row) * 2.5f * density;
                canvas.drawLine(previousX, previousY, x, nextY, line);
                previousX = x;
                previousY = nextY;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sync();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(frame);
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }
}
