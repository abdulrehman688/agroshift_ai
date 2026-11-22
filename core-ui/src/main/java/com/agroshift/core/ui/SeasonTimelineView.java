package com.agroshift.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.function.IntConsumer;

/** Draggable farming year: six Rabi months, then six Kharif months, mirrored in RTL. */
public final class SeasonTimelineView extends View {
    private static final int MONTHS = 12;
    private static final int KHARIF_START = 6;

    private final Paint rabi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kharif = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private ValueAnimator animator;
    private IntConsumer listener;
    private int selected;
    private int today;
    private float shown;

    public SeasonTimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rabi.setColor(ContextCompat.getColor(context, R.color.zameen_accent));
        rabi.setStrokeWidth(5 * density);
        rabi.setStrokeCap(Paint.Cap.ROUND);
        kharif.setColor(ContextCompat.getColor(context, R.color.zameen_canal));
        kharif.setStrokeWidth(5 * density);
        kharif.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(ContextCompat.getColor(context, R.color.zameen_text_secondary));
        tick.setStrokeWidth(1.5f * density);
        knob.setColor(ContextCompat.getColor(context, R.color.zameen_text));
        knob.setStyle(Paint.Style.STROKE);
        knob.setStrokeWidth(3 * density);
        knobFill.setColor(ContextCompat.getColor(context, R.color.zameen_background));
        setFocusable(true);
        setClickable(true);
        setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setScrollable(true);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                    choose(selected + 1, true);
                    return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    choose(selected - 1, true);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    public void setOnMonthChanged(IntConsumer next) {
        listener = next;
    }

    public void setToday(int index) {
        today = index;
        selected = index;
        shown = index;
        invalidate();
    }

    public void select(int index) {
        choose(index, true);
    }

    private void choose(int index, boolean notify) {
        int next = Math.max(0, Math.min(MONTHS - 1, index));
        if (next == selected) {
            return;
        }
        selected = next;
        if (animator != null) {
            animator.cancel();
        }
        if (ZameenUi.motionEnabled(getContext())) {
            animator = ValueAnimator.ofFloat(shown, next);
            animator.setDuration(140);
            animator.addUpdateListener(update -> {
                shown = (float) update.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            shown = next;
            invalidate();
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (notify && listener != null) {
            listener.accept(next);
        }
    }

    private float inset() {
        return 14 * density;
    }

    private float xFor(float index) {
        float width = getWidth() - 2 * inset();
        float value = inset() + (index + 0.5f) / MONTHS * width;
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - value : value;
    }

    private int indexAt(float x) {
        float position = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - x : x;
        return (int) Math.floor((position - inset()) / (getWidth() - 2 * inset()) * MONTHS);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                getParent().requestDisallowInterceptTouchEvent(true);
                choose(indexAt(event.getX()), true);
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                choose(indexAt(event.getX()), true);
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            default -> {
                return super.onTouchEvent(event);
            }
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            choose(selected + (rtl ? -1 : 1), true);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            choose(selected + (rtl ? 1 : -1), true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(Math.round(48 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float y = getHeight() / 2f;
        float gap = 3 * density;
        float left = xFor(-0.5f);
        float middle = xFor(KHARIF_START - 0.5f);
        float right = xFor(MONTHS - 0.5f);
        float direction = right > left ? 1 : -1;
        canvas.drawLine(left, y, middle - gap * direction, y, rabi);
        canvas.drawLine(middle + gap * direction, y, right, y, kharif);
        for (int i = 0; i < MONTHS; i++) {
            float x = xFor(i);
            float reach = (i == today ? 11 : 6) * density;
            canvas.drawLine(x, y + 6 * density, x, y + 6 * density + reach, tick);
        }
        float x = xFor(shown);
        canvas.drawCircle(x, y, 10 * density, knobFill);
        canvas.drawCircle(x, y, 10 * density, knob);
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
