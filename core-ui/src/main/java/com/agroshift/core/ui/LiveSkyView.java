package com.agroshift.core.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.agroshift.core.domain.water.SkyState;
import java.util.Random;

/** Draws the field's current weather over the Home photo. Pauses when it cannot be seen. */
public final class LiveSkyView extends View {
    private static final long FRAME_MILLIS = 33;
    private static final int MAX_DROPS = 70;
    private static final int STREAKS = 12;
    private static final int CLOUDS = 4;

    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cloudPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float[] dropX = new float[MAX_DROPS];
    private final float[] dropY = new float[MAX_DROPS];
    private final float[] dropSpeed = new float[MAX_DROPS];
    private final float[] streakX = new float[STREAKS];
    private final float[] streakY = new float[STREAKS];
    private final float[] cloudX = new float[CLOUDS];
    private final Random random = new Random(7);
    private final float density;
    private final Runnable frame = this::nextFrame;
    private SkyState sky;
    private Bitmap cloud;
    private boolean paused;
    private boolean running;
    private long lastFrame;
    private float time;

    public LiveSkyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rainPaint.setColor(Color.argb(150, 210, 232, 240));
        rainPaint.setStrokeWidth(1.4f * density);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);
        windPaint.setColor(Color.argb(70, 243, 245, 232));
        windPaint.setStrokeWidth(1.2f * density);
        windPaint.setStrokeCap(Paint.Cap.ROUND);
        heatPaint.setColor(Color.argb(34, 255, 196, 120));
        heatPaint.setStyle(Paint.Style.STROKE);
        heatPaint.setStrokeWidth(2 * density);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setSky(@Nullable SkyState next) {
        sky = next;
        sync();
        invalidate();
    }

    public void setPaused(boolean value) {
        paused = value;
        sync();
    }

    private boolean effectsMove() {
        if (sky == null) {
            return false;
        }
        SkyState.Condition condition = sky.condition();
        return condition != SkyState.Condition.CLEAR || sky.cloudPercent() >= 25;
    }

    private void sync() {
        boolean should = !paused && isAttachedToWindow() && isShown() && getHeight() > 0
                && effectsMove() && ZameenUi.motionEnabled(getContext());
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
        advance(delta);
        invalidate();
        postOnAnimationDelayed(frame, FRAME_MILLIS);
    }

    private void advance(float delta) {
        time += delta;
        float width = getWidth();
        float height = getHeight();
        float windSpeed = sky == null ? 0 : (float) Math.min(60, sky.windKmh());
        for (int i = 0; i < MAX_DROPS; i++) {
            dropY[i] += dropSpeed[i] * delta;
            dropX[i] += windSpeed * density * delta;
            if (dropY[i] > height) {
                dropY[i] = -20 * density;
                dropX[i] = random.nextFloat() * width;
            }
            if (dropX[i] > width) {
                dropX[i] -= width;
            }
        }
        for (int i = 0; i < STREAKS; i++) {
            streakX[i] += (90 + windSpeed * 5) * density * delta;
            if (streakX[i] > width + 60 * density) {
                streakX[i] = -60 * density;
                streakY[i] = random.nextFloat() * height * 0.7f;
            }
        }
        for (int i = 0; i < CLOUDS; i++) {
            cloudX[i] += (4 + windSpeed * 0.25f) * (1 + i * 0.3f) * density * delta;
            if (cloudX[i] > width) {
                cloudX[i] = -cloudWidth();
            }
        }
    }

    private float cloudWidth() {
        return getWidth() * 0.75f;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int i = 0; i < MAX_DROPS; i++) {
            dropX[i] = random.nextFloat() * width;
            dropY[i] = random.nextFloat() * height;
            dropSpeed[i] = (420 + random.nextFloat() * 280) * density;
        }
        for (int i = 0; i < STREAKS; i++) {
            streakX[i] = random.nextFloat() * width;
            streakY[i] = random.nextFloat() * height * 0.7f;
        }
        for (int i = 0; i < CLOUDS; i++) {
            cloudX[i] = width * i / (float) CLOUDS - width * 0.3f;
        }
        if (cloud == null) {
            cloud = makeCloud();
        }
        sync();
    }

    private static Bitmap makeCloud() {
        Bitmap bitmap = Bitmap.createBitmap(240, 90, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[][] puffs = {{70, 54, 46}, {125, 42, 44}, {175, 56, 40}};
        for (float[] puff : puffs) {
            paint.setShader(new RadialGradient(puff[0], puff[1], puff[2], Color.argb(150, 235, 238, 230),
                    Color.argb(0, 235, 238, 230), Shader.TileMode.CLAMP));
            canvas.drawCircle(puff[0], puff[1], puff[2], paint);
        }
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sky == null) {
            return;
        }
        if (!sky.day()) {
            canvas.drawColor(Color.argb(95, 8, 14, 26));
        }
        SkyState.Condition condition = sky.condition();
        if (condition == SkyState.Condition.RAIN || condition == SkyState.Condition.HEAVY_RAIN) {
            canvas.drawColor(Color.argb(45, 20, 28, 34));
        }
        drawClouds(canvas);
        if (condition == SkyState.Condition.HOT) {
            canvas.drawColor(Color.argb(22, 255, 150, 60));
            drawHeat(canvas);
        }
        if (condition == SkyState.Condition.WINDY || sky.windKmh() >= 25) {
            for (int i = 0; i < STREAKS; i++) {
                float length = (28 + i * 3) * density;
                canvas.drawLine(streakX[i], streakY[i], streakX[i] + length, streakY[i], windPaint);
            }
        }
        if (condition == SkyState.Condition.RAIN || condition == SkyState.Condition.HEAVY_RAIN) {
            int drops = condition == SkyState.Condition.HEAVY_RAIN ? MAX_DROPS : MAX_DROPS / 2;
            float slant = (float) Math.min(60, sky.windKmh()) * 0.08f * density;
            for (int i = 0; i < drops; i++) {
                canvas.drawLine(dropX[i], dropY[i], dropX[i] - slant, dropY[i] - 13 * density, rainPaint);
            }
        }
    }

    private void drawClouds(Canvas canvas) {
        if (cloud == null || sky.cloudPercent() < 25) {
            return;
        }
        int count = Math.min(CLOUDS, 1 + sky.cloudPercent() / 30);
        float width = cloudWidth();
        float height = width * 0.375f;
        cloudPaint.setAlpha(Math.min(190, 70 + sky.cloudPercent()));
        for (int i = 0; i < count; i++) {
            float y = (18 + i * 26) * density;
            canvas.save();
            canvas.translate(cloudX[i], y);
            canvas.scale(width / cloud.getWidth(), height / cloud.getHeight());
            canvas.drawBitmap(cloud, 0, 0, cloudPaint);
            canvas.restore();
        }
    }

    private void drawHeat(Canvas canvas) {
        float base = getHeight() * 0.5f;
        for (int line = 0; line < 3; line++) {
            float y = base + line * 22 * density - (time * 12 * density) % (22 * density);
            float previousX = 0;
            float previousY = y;
            for (float x = 16 * density; x <= getWidth(); x += 16 * density) {
                float nextY = y + (float) Math.sin(x / (22 * density) + time * 2 + line) * 3 * density;
                canvas.drawLine(previousX, previousY, x, nextY, heatPaint);
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
        super.onDetachedFromWindow();
    }
}
