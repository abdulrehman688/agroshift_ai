package com.agroshift.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.text.NumberFormat;

/** Animated score from 0 to 100, with the number always shown in the center. */
public final class ScoreRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;
    private ValueAnimator animator;
    private float shown;

    public ScoreRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        track.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(7 * density);
        progress.setColor(ContextCompat.getColor(context, R.color.zameen_accent));
        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeWidth(7 * density);
        progress.setStrokeCap(Paint.Cap.ROUND);
        number.setColor(ContextCompat.getColor(context, R.color.zameen_text));
        number.setTextAlign(Paint.Align.CENTER);
        number.setFakeBoldText(true);
        number.setTextSize(26 * getResources().getDisplayMetrics().scaledDensity);
    }

    public void setScore(double score) {
        float target = (float) Math.max(0, Math.min(100, score));
        if (animator != null) {
            animator.cancel();
        }
        if (!ZameenUi.motionEnabled(getContext())) {
            shown = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(shown, target);
        animator.setDuration(620);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(update -> {
            shown = (float) update.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = 6 * density;
        oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(oval, 0, 360, false, track);
        if (shown > 0f) {
            canvas.drawArc(oval, -90, 360 * shown / 100f, false, progress);
        }
        float y = getHeight() / 2f - (number.descent() + number.ascent()) / 2f;
        canvas.drawText(NumberFormat.getIntegerInstance().format(Math.round(shown)), getWidth() / 2f, y, number);
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
