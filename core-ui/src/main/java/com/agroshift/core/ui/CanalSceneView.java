package com.agroshift.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Top-down canal, gate and field. Water enters the field only during a real canal turn. */
public final class CanalSceneView extends View {
    public enum Mode { NONE, WAITING, SOON, RUNNING }

    private static final long FRAME_MILLIS = 33;
    private static final float RIPPLE_SECONDS = 0.9f;

    private final Paint land = new Paint();
    private final Paint canal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bank = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rows = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flood = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final Runnable frame = this::nextFrame;
    private Mode mode = Mode.NONE;
    private float progress;
    private float phase;
    private float gateOpen;
    private float rippleX;
    private float rippleY;
    private float rippleAge = RIPPLE_SECONDS;
    private boolean running;
    private long lastFrame;

    public CanalSceneView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        land.setColor(ContextCompat.getColor(context, R.color.zameen_surface_accent));
        canal.setColor(ContextCompat.getColor(context, R.color.zameen_canal));
        flow.setColor(Color.argb(170, 235, 248, 250));
        flow.setStrokeWidth(2 * density);
        flow.setStrokeCap(Paint.Cap.ROUND);
        bank.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        rows.setColor(Color.argb(190, 126, 170, 76));
        rows.setStrokeWidth(2.5f * density);
        rows.setStrokeCap(Paint.Cap.ROUND);
        flood.setColor(Color.argb(120, 111, 183, 200));
        gate.setColor(ContextCompat.getColor(context, R.color.zameen_text));
        ripple.setStyle(Paint.Style.STROKE);
        ripple.setStrokeWidth(2 * density);
        ripple.setColor(Color.WHITE);
        setClickable(true);
    }

    public void setState(Mode next, float turnProgress) {
        boolean changed = next != mode;
        mode = next;
        progress = Math.max(0f, Math.min(1f, turnProgress));
        if (changed && !ZameenUi.motionEnabled(getContext())) {
            gateOpen = mode == Mode.RUNNING ? 1f : 0f;
        }
        sync();
        invalidate();
    }

    private boolean needsFrames() {
        return mode == Mode.SOON || mode == Mode.RUNNING || rippleAge < RIPPLE_SECONDS
                || gateOpen != (mode == Mode.RUNNING ? 1f : 0f);
    }

    private void sync() {
        boolean should = isAttachedToWindow() && isShown() && ZameenUi.motionEnabled(getContext()) && needsFrames();
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
        float delta = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        phase += delta * (mode == Mode.RUNNING ? 70 : 28) * density;
        float target = mode == Mode.RUNNING ? 1f : 0f;
        float step = delta * 1.6f;
        gateOpen = gateOpen < target ? Math.min(target, gateOpen + step) : Math.max(target, gateOpen - step);
        rippleAge = Math.min(RIPPLE_SECONDS, rippleAge + delta);
        invalidate();
        if (needsFrames()) {
            postOnAnimationDelayed(frame, FRAME_MILLIS);
        } else {
            running = false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            rippleX = event.getX();
            rippleY = event.getY();
            rippleAge = 0f;
            sync();
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
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(Math.round(170 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float radius = 18 * density;
        canvas.save();
        rect.set(0, 0, width, height);
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawRect(rect, land);

        float canalTop = 14 * density;
        float canalBottom = 50 * density;
        float fieldTop = 84 * density;
        float center = width / 2f;
        float channel = 8 * density;
        canvas.drawRect(0, canalTop - 3 * density, width, canalTop, bank);
        canvas.drawRect(0, canalBottom, width, canalBottom + 3 * density, bank);
        canvas.drawRect(0, canalTop, width, canalBottom, canal);
        float spacing = 34 * density;
        float offset = mode == Mode.NONE ? 0 : phase % spacing;
        for (int lane = 0; lane < 3; lane++) {
            float y = canalTop + (canalBottom - canalTop) * (lane + 1) / 4f;
            for (float x = -spacing + offset + lane * 9 * density; x < width; x += spacing) {
                canvas.drawLine(x, y, x + 13 * density, y, flow);
            }
        }

        if (gateOpen > 0f) {
            canvas.drawRect(center - channel, canalBottom, center + channel, fieldTop, canal);
            float dash = 12 * density;
            for (float y = canalBottom + (phase % dash); y < fieldTop; y += dash) {
                canvas.drawLine(center, y, center, Math.min(fieldTop, y + 5 * density), flow);
            }
        } else {
            canvas.drawRect(center - channel, canalBottom + 3 * density, center + channel, fieldTop, bank);
        }
        float gateY = canalBottom - gateOpen * 15 * density;
        canvas.drawRoundRect(center - channel - 5 * density, gateY - 2 * density,
                center + channel + 5 * density, gateY + 6 * density, 3 * density, 3 * density, gate);

        float left = 14 * density;
        float right = width - 14 * density;
        float bottom = height - 12 * density;
        if (mode == Mode.RUNNING) {
            float reach = (right - left) / 2f * Math.max(0.06f, progress);
            canvas.drawRect(center - reach, fieldTop, center + reach, bottom, flood);
        }
        for (float y = fieldTop + 7 * density; y < bottom; y += 13 * density) {
            canvas.drawLine(left, y, right, y, rows);
        }
        if (rippleAge < RIPPLE_SECONDS) {
            float life = rippleAge / RIPPLE_SECONDS;
            ripple.setAlpha(Math.round(170 * (1 - life)));
            canvas.drawCircle(rippleX, rippleY, (6 + 34 * life) * density, ripple);
        }
        canvas.restore();
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
