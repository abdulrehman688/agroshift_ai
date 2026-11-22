package com.agroshift.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.function.IntConsumer;

/** Tappable monthly bars, using only the farmer's saved sales. */
public final class SalesBarsView extends View {
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private double[] values = new double[0];
    private String[] labels = new String[0];
    private IntConsumer listener;
    private ValueAnimator animator;
    private float reveal = 1f;
    private int selected = -1;

    public SalesBarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        bar.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        active.setColor(ContextCompat.getColor(context, R.color.zameen_accent));
        base.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        label.setColor(ContextCompat.getColor(context, R.color.zameen_text_secondary));
        label.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
        label.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    public void setOnBarSelected(IntConsumer next) {
        listener = next;
    }

    public void setData(double[] nextValues, String[] nextLabels) {
        values = nextValues.clone();
        labels = nextLabels.clone();
        selected = values.length - 1;
        if (animator != null) {
            animator.cancel();
        }
        if (ZameenUi.motionEnabled(getContext())) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(520);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(update -> {
                reveal = (float) update.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            reveal = 1f;
            invalidate();
        }
    }

    private float columnX(int index, float column) {
        float x = (index + 0.5f) * column;
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - x : x;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (values.length == 0) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float position = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - event.getX() : event.getX();
            int index = Math.max(0, Math.min(values.length - 1, (int) (position / (getWidth() / (float) values.length))));
            if (index != selected) {
                selected = index;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
                if (listener != null) {
                    listener.accept(index);
                }
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(Math.round(132 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.length == 0) {
            return;
        }
        double max = 0;
        for (double value : values) {
            max = Math.max(max, value);
        }
        float column = getWidth() / (float) values.length;
        float baseline = getHeight() - 26 * density;
        float top = 6 * density;
        float half = Math.min(15 * density, column * 0.32f);
        canvas.drawLine(0, baseline, getWidth(), baseline, base);
        for (int i = 0; i < values.length; i++) {
            float x = columnX(i, column);
            if (max > 0 && values[i] > 0) {
                float height = Math.max(4 * density, (float) (values[i] / max) * (baseline - top) * reveal);
                float radius = 5 * density;
                canvas.drawRoundRect(x - half, baseline - height, x + half, baseline, radius, radius,
                        i == selected ? active : bar);
            }
            canvas.drawText(labels[i], x, getHeight() - 6 * density, label);
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
