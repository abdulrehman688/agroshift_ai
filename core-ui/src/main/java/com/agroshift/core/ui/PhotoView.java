package com.agroshift.core.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;
import android.view.ViewOutlineProvider;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Decodes a sized photo off the main thread and blends it into the Zameen background. */
public final class PhotoView extends AppCompatImageView {
    private static final int CACHE_BYTES = 16 * 1024 * 1024;
    private static final long FADE_MILLIS = 180;
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "photo-decoder");
        thread.setDaemon(true);
        return thread;
    });
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getAllocationByteCount();
        }
    };

    private final Paint scrimPaint = new Paint();
    private boolean scrim;
    private final int scrimColor;
    private int requested;

    public PhotoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.CENTER_CROP);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        scrimColor = ContextCompat.getColor(context, R.color.zameen_background);
        setBackgroundColor(ContextCompat.getColor(context, R.color.zameen_surface));
        TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.PhotoView);
        int photo = values.getResourceId(R.styleable.PhotoView_photo, 0);
        scrim = values.getBoolean(R.styleable.PhotoView_photoScrim, true);
        float radius = values.getDimension(R.styleable.PhotoView_photoRadius, 0f);
        values.recycle();
        if (radius > 0f) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            setClipToOutline(true);
        }
        if (photo != 0 && !isInEditMode()) {
            setPhoto(photo);
        }
    }

    public void setScrimEnabled(boolean enabled) {
        scrim = enabled;
        invalidate();
    }

    public void setPhoto(@DrawableRes int resource) {
        if (resource == requested) {
            return;
        }
        requested = resource;
        Resources resources = getResources();
        int target = resources.getDisplayMetrics().widthPixels;
        String key = resource + ":" + target;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            setImageBitmap(cached);
            return;
        }
        setImageDrawable(null);
        DECODER.execute(() -> {
            Bitmap bitmap = decode(resources, resource, target);
            if (bitmap == null) {
                return;
            }
            CACHE.put(key, bitmap);
            post(() -> {
                if (requested == resource) {
                    show(bitmap);
                }
            });
        });
    }

    private void show(Bitmap bitmap) {
        setImageBitmap(bitmap);
        if (ZameenUi.motionEnabled(getContext())) {
            setAlpha(0f);
            animate().alpha(1f).setDuration(FADE_MILLIS).start();
        }
    }

    @Nullable
    private static Bitmap decode(Resources resources, int resource, int targetWidth) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, resource, options);
        int sample = 1;
        while (options.outWidth / (sample * 2) >= targetWidth) {
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        return BitmapFactory.decodeResource(resources, resource, options);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (scrim && height > 0) {
            int clear = scrimColor & 0x00FFFFFF;
            int top = (0x2E << 24) | clear;
            scrimPaint.setShader(new LinearGradient(0, 0, 0, height,
                    new int[] {top, clear, clear, (0x78 << 24) | clear, scrimColor | Color.BLACK},
                    new float[] {0f, 0.18f, 0.55f, 0.84f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (scrim) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        }
    }
}
