package com.agroshift.feature.help;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.agroshift.core.ui.ZameenUi;

/** Shows that an answer is being prepared. It animates only while visible. */
public final class TypingDotsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private ValueAnimator animator;
    private float phase;

    public TypingDotsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        paint.setColor(ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_accent));
        ZameenUi.applySurface(this, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        sync();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void sync() {
        if (!isAttachedToWindow() || !isShown() || !ZameenUi.motionEnabled(getContext())) {
            stop();
        } else if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(update -> {
                phase = (float) update.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        phase = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        for (int i = 0; i < 3; i++) {
            float wave = (float) Math.sin((phase - i * 0.18f) * 2 * Math.PI);
            paint.setAlpha(Math.round(135 + 120 * Math.max(0f, wave)));
            canvas.drawCircle(centerX + (i - 1) * 13 * density, centerY - Math.max(0f, wave) * 3 * density,
                    3.5f * density, paint);
        }
    }
}
