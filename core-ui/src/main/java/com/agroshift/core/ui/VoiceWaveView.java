package com.agroshift.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Moving sound bars shown only while an answer is being read aloud. */
public final class VoiceWaveView extends View {
    private static final long FRAME_MILLIS = 40;
    private static final int BARS = 5;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final Runnable frame = this::nextFrame;
    private boolean running;

    public VoiceWaveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        paint.setColor(ContextCompat.getColor(context, R.color.zameen_accent));
        paint.setStrokeWidth(3 * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void sync() {
        boolean should = isAttachedToWindow() && isShown() && ZameenUi.motionEnabled(getContext());
        if (should && !running) {
            running = true;
            postOnAnimation(frame);
        } else if (!should && running) {
            running = false;
            removeCallbacks(frame);
        }
    }

    private void nextFrame() {
        if (running) {
            invalidate();
            postOnAnimationDelayed(frame, FRAME_MILLIS);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float time = SystemClock.uptimeMillis() / 1000f;
        float center = getHeight() / 2f;
        float gap = getWidth() / (float) (BARS + 1);
        float max = getHeight() * 0.42f;
        for (int i = 0; i < BARS; i++) {
            float wave = running ? 0.55f + 0.45f * (float) Math.sin(time * 7 + i * 1.25f) : 0.6f;
            float half = Math.max(2 * density, max * wave);
            float x = gap * (i + 1);
            canvas.drawLine(x, center - half, x, center + half, paint);
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
        super.onDetachedFromWindow();
    }
}
