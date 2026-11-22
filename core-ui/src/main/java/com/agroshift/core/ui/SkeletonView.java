package com.agroshift.core.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Layout-matched loading block. It animates only while visible and motion is enabled. */
public final class SkeletonView extends View {
    private ObjectAnimator pulse;

    public SkeletonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ZameenUi.applySurface(this, R.color.zameen_surface, R.dimen.zameen_radius_card);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncPulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncPulse();
    }

    private void syncPulse() {
        if (!isAttachedToWindow() || !isShown() || !ZameenUi.motionEnabled(getContext())) {
            stopPulse();
            return;
        }
        if (pulse == null) {
            pulse = ObjectAnimator.ofFloat(this, ALPHA, 0.55f, 1f);
            pulse.setDuration(700);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
        }
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
            setAlpha(1f);
        }
    }
}
