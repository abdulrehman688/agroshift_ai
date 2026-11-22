package com.agroshift.core.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.agroshift.core.domain.scene.LivingScene;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Brings a real photo to life on the GPU. If the phone cannot run the effect, or motion is off,
 * the normal still photo remains visible.
 */
public final class LivingPhotoView extends FrameLayout implements TextureView.SurfaceTextureListener {
    private static final int MAX_RENDER_WIDTH = 720;
    private static final float PHOTO_ASPECT = 1.6f;
    private static final float CROP_FOCUS_Y = 0.68f;
    private static final float RIPPLE_SECONDS = 1.4f;

    private static final String VERTEX = "attribute vec2 aPos;\n"
            + "uniform vec4 uCrop;\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vec2 base = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);\n"
            + "  vUv = base * uCrop.xy + uCrop.zw;\n"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
            + "}\n";

    private final PhotoView poster;
    private final TextureView texture;
    private final Object lock = new Object();
    private volatile LivingScene scene = new LivingScene(0.2f, 0f, 0f, 0f, 0f, 1f, 0f, 0f);
    private int photoResource;
    private int maskResource;
    private RenderThread renderer;
    private boolean paused;
    private float rippleX;
    private float rippleY;
    private long rippleAt;

    public LivingPhotoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        poster = new PhotoView(context, null);
        poster.setScrimEnabled(false);
        addView(poster, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        texture = new TextureView(context);
        texture.setOpaque(true);
        texture.setAlpha(0f);
        texture.setSurfaceTextureListener(this);
        addView(texture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        float radius = 18 * getResources().getDisplayMetrics().density;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        setClickable(true);
    }

    public void setPhoto(@DrawableRes int photo, @DrawableRes int mask) {
        photoResource = photo;
        maskResource = mask;
        poster.setPhoto(photo);
        startIfReady();
    }

    public void setScene(LivingScene next) {
        scene = next;
        wake();
    }

    public void setPaused(boolean value) {
        synchronized (lock) {
            paused = value;
            lock.notifyAll();
        }
    }

    private void wake() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private boolean shouldRender() {
        return !paused && isAttachedToWindow() && isShown();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        wake();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP && getWidth() > 0 && getHeight() > 0) {
            float[] crop = crop(getWidth(), getHeight());
            synchronized (lock) {
                rippleX = event.getX() / getWidth() * crop[0] + crop[2];
                rippleY = event.getY() / getHeight() * crop[1] + crop[3];
                rippleAt = System.nanoTime();
                lock.notifyAll();
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private static float[] crop(int width, int height) {
        float aspect = width / (float) height;
        float scaleX = 1f;
        float scaleY = 1f;
        if (aspect > PHOTO_ASPECT) {
            scaleY = PHOTO_ASPECT / aspect;
        } else {
            scaleX = aspect / PHOTO_ASPECT;
        }
        return new float[] {scaleX, scaleY, (1f - scaleX) * 0.62f, (1f - scaleY) * CROP_FOCUS_Y};
    }

    private void startIfReady() {
        SurfaceTexture surface = texture.getSurfaceTexture();
        if (renderer == null && surface != null && photoResource != 0 && ZameenUi.motionEnabled(getContext())
                && texture.getWidth() > 0 && texture.getHeight() > 0) {
            int width = Math.min(MAX_RENDER_WIDTH, texture.getWidth());
            int height = Math.max(1, Math.round(width * texture.getHeight() / (float) texture.getWidth()));
            surface.setDefaultBufferSize(width, height);
            renderer = new RenderThread(surface, width, height, crop(texture.getWidth(), texture.getHeight()));
            renderer.start();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        startIfReady();
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        stopRenderer();
        startIfReady();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        stopRenderer();
        texture.setAlpha(0f);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        // The render thread reveals the view after its first complete frame.
    }

    private void stopRenderer() {
        RenderThread running = renderer;
        renderer = null;
        if (running != null) {
            running.finish();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopRenderer();
        super.onDetachedFromWindow();
    }

    private final class RenderThread extends Thread {
        private static final long FRAME_NANOS = 33_000_000L;
        private static final long LOW_FRAME_NANOS = 50_000_000L;
        private static final long SLOW_WORK_NANOS = 27_000_000L;
        private static final int SAMPLE_FRAMES = 45;

        private final SurfaceTexture surfaceTexture;
        private final int width;
        private final int height;
        private final float[] cropValues;
        private volatile boolean running = true;
        private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        private EGLContext context = EGL14.EGL_NO_CONTEXT;
        private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
        private float flow;
        private float progress;
        private float rain;
        private float wind;
        private float cloud;
        private float daylight = 1f;
        private float golden;
        private float heat;

        RenderThread(SurfaceTexture surfaceTexture, int width, int height, float[] cropValues) {
            super("living-photo");
            this.surfaceTexture = surfaceTexture;
            this.width = width;
            this.height = height;
            this.cropValues = cropValues;
            LivingScene start = scene;
            flow = start.flow();
            progress = start.turnProgress();
            rain = start.rain();
            wind = start.wind();
            cloud = start.cloud();
            daylight = start.daylight();
            golden = start.golden();
            heat = start.heat();
        }

        void finish() {
            running = false;
            synchronized (lock) {
                lock.notifyAll();
            }
            interrupt();
            try {
                join(600);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void run() {
            try {
                if (setUpEgl()) {
                    int program = buildProgram();
                    if (program != 0) {
                        loadTextures(program);
                        renderLoop(program);
                    }
                }
            } catch (RuntimeException | IOException | InterruptedException failure) {
                // The still photo stays visible if the GPU effect cannot run.
            } finally {
                releaseEgl();
            }
        }

        private boolean setUpEgl() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, version, 0, version, 1)) {
                return false;
            }
            int[] attributes = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) {
                return false;
            }
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            if (context == EGL14.EGL_NO_CONTEXT) {
                return false;
            }
            eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], new Surface(surfaceTexture),
                    new int[] {EGL14.EGL_NONE}, 0);
            return eglSurface != EGL14.EGL_NO_SURFACE
                    && EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context);
        }

        private int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                GLES20.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }

        private int buildProgram() throws IOException {
            int vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX);
            int fragment = compile(GLES20.GL_FRAGMENT_SHADER, readShader(getResources()));
            if (vertex == 0 || fragment == 0) {
                return 0;
            }
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertex);
            GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
            if (ok[0] == 0) {
                return 0;
            }
            GLES20.glUseProgram(program);
            FloatBuffer quad = ByteBuffer.allocateDirect(8 * Float.BYTES).order(ByteOrder.nativeOrder())
                    .asFloatBuffer().put(new float[] {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f});
            quad.position(0);
            int position = GLES20.glGetAttribLocation(program, "aPos");
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad);
            GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uCrop"), cropValues[0], cropValues[1],
                    cropValues[2], cropValues[3]);
            GLES20.glViewport(0, 0, width, height);
            return program;
        }

        private void loadTextures(int program) {
            upload(photoResource, 0, GLES20.glGetUniformLocation(program, "uPhoto"));
            upload(maskResource, 1, GLES20.glGetUniformLocation(program, "uMask"));
        }

        private void upload(int resource, int unit, int uniform) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resource, options);
            if (bitmap == null) {
                throw new IllegalStateException("Missing living photo layer");
            }
            int[] id = new int[1];
            GLES20.glGenTextures(1, id, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            GLES20.glUniform1i(uniform, unit);
            bitmap.recycle();
        }

        private void renderLoop(int program) throws InterruptedException {
            int time = GLES20.glGetUniformLocation(program, "uTime");
            int uFlow = GLES20.glGetUniformLocation(program, "uFlow");
            int uProgress = GLES20.glGetUniformLocation(program, "uProgress");
            int uRain = GLES20.glGetUniformLocation(program, "uRain");
            int uWind = GLES20.glGetUniformLocation(program, "uWind");
            int uCloud = GLES20.glGetUniformLocation(program, "uCloud");
            int uDay = GLES20.glGetUniformLocation(program, "uDay");
            int uGold = GLES20.glGetUniformLocation(program, "uGold");
            int uHeat = GLES20.glGetUniformLocation(program, "uHeat");
            int uLow = GLES20.glGetUniformLocation(program, "uLow");
            int uRippleAge = GLES20.glGetUniformLocation(program, "uRippleAge");
            int uRipple = GLES20.glGetUniformLocation(program, "uRipple");
            long start = System.nanoTime();
            long last = start;
            long slowWork = 0;
            int sampled = 0;
            boolean low = false;
            boolean revealed = false;
            while (running) {
                synchronized (lock) {
                    while (running && !shouldRender()) {
                        lock.wait(500);
                        last = System.nanoTime();
                    }
                }
                if (!running) {
                    return;
                }
                long now = System.nanoTime();
                float delta = Math.min(0.1f, (now - last) / 1_000_000_000f);
                last = now;
                ease(scene, Math.min(1f, delta * 2.2f));
                float ripple;
                float x;
                float y;
                synchronized (lock) {
                    ripple = rippleAt == 0 ? RIPPLE_SECONDS + 1f : (now - rippleAt) / 1_000_000_000f;
                    x = rippleX;
                    y = rippleY;
                }
                GLES20.glUniform1f(time, ((now - start) / 1_000_000L % 1_000_000L) / 1000f);
                GLES20.glUniform1f(uFlow, flow);
                GLES20.glUniform1f(uProgress, progress);
                GLES20.glUniform1f(uRain, rain);
                GLES20.glUniform1f(uWind, wind);
                GLES20.glUniform1f(uCloud, cloud);
                GLES20.glUniform1f(uDay, daylight);
                GLES20.glUniform1f(uGold, golden);
                GLES20.glUniform1f(uHeat, heat);
                GLES20.glUniform1f(uLow, low ? 1f : 0f);
                GLES20.glUniform1f(uRippleAge, ripple);
                GLES20.glUniform2f(uRipple, x, y);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                    return;
                }
                if (!revealed) {
                    revealed = true;
                    postDelayed(() -> texture.animate().alpha(1f).setDuration(260).start(), 70);
                }
                long work = System.nanoTime() - now;
                slowWork += work;
                if (!low && ++sampled == SAMPLE_FRAMES) {
                    low = slowWork / SAMPLE_FRAMES > SLOW_WORK_NANOS;
                    sampled = 0;
                    slowWork = 0;
                }
                long sleep = (low ? LOW_FRAME_NANOS : FRAME_NANOS) - work;
                if (sleep > 0) {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                }
            }
        }

        private void ease(LivingScene target, float amount) {
            flow += (target.flow() - flow) * amount;
            progress += (target.turnProgress() - progress) * amount;
            rain += (target.rain() - rain) * amount;
            wind += (target.wind() - wind) * amount;
            cloud += (target.cloud() - cloud) * amount;
            daylight += (target.daylight() - daylight) * amount;
            golden += (target.golden() - golden) * amount;
            heat += (target.heat() - heat) * amount;
        }

        private void releaseEgl() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, eglSurface);
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context);
                }
                EGL14.eglTerminate(display);
            }
        }
    }

    private static String readShader(Resources resources) throws IOException {
        try (InputStream input = resources.openRawResource(R.raw.living_photo_fragment);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
