package com.agroshift.core.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
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
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.agroshift.core.domain.scene.LivingScene;
import com.agroshift.core.domain.scene.RenderBudget;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Brings real photos to life on the GPU and blends between them in real time. It adapts to each
 * phone, pauses when hidden, and falls back to the normal still photo.
 */
public final class LivingPhotoView extends FrameLayout implements TextureView.SurfaceTextureListener {
    private static final float RIPPLE_SECONDS = 1.4f;
    private static final int MAX_TEXTURES = 12;
    /** Motion values stay small forever, because large shader times break up on many phone GPUs. */
    private static final float SWAY_SECONDS = 60f;
    private static final float LOOP_SECONDS = 40f;

    private static final String VERTEX = "attribute vec2 aPos;\n"
            + "uniform vec4 uCrop;\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vec2 base = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);\n"
            + "  vUv = base * uCrop.xy + uCrop.zw;\n"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
            + "}\n";

    private record Layer(int photo, int mask) {
    }

    /** Ordered photos, such as crop stages, with a continuous position along them. */
    private record Track(List<Layer> layers, float position) {
        Layer nearest() {
            return layers.get(Math.min(layers.size() - 1, Math.round(position)));
        }
    }

    private record Decoded(int resource, Bitmap bitmap) {
    }

    private final PhotoView poster;
    private final TextureView texture;
    private final Object lock = new Object();
    private final Rect visibleArea = new Rect();
    private final RenderBudget budget;
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::updateOnScreen;
    private volatile LivingScene scene = new LivingScene(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
    private volatile Track track;
    private volatile boolean fieldMode;
    private volatile boolean onScreen = true;
    private volatile boolean revealed;
    private volatile float photoAspect = 1.6f;
    private float focusX = 0.5f;
    private float focusY = 0.5f;
    private RenderThread renderer;
    private boolean paused;
    private float rippleX;
    private float rippleY;
    private long rippleAt;

    public LivingPhotoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        budget = LivingDevice.budget(context);
        TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.PhotoView);
        boolean scrim = values.getBoolean(R.styleable.PhotoView_photoScrim, false);
        float radius = values.getDimension(R.styleable.PhotoView_photoRadius, 0f);
        values.recycle();
        poster = new PhotoView(context, null);
        poster.setScrimEnabled(false);
        addView(poster, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        texture = new TextureView(context);
        texture.setOpaque(true);
        texture.setAlpha(0f);
        texture.setSurfaceTextureListener(this);
        addView(texture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        if (scrim) {
            int background = ContextCompat.getColor(context, R.color.zameen_background);
            int clear = background & 0x00FFFFFF;
            View shade = new View(context);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {(0x2E << 24) | clear, clear, clear, clear, (0x70 << 24) | clear, background}));
            shade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(shade, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        if (radius > 0f) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            setClipToOutline(true);
        }
    }

    /** Shows one photo, cross-fading from the current one. */
    public void setPhoto(@DrawableRes int photo, @DrawableRes int mask) {
        setTrack(new int[] {photo}, new int[] {mask}, 0f);
    }

    /** Moves continuously along ordered photos, such as a crop's growth stages. */
    public void setTrack(int[] photos, int[] masks, float position) {
        List<Layer> list = new ArrayList<>(photos.length);
        for (int i = 0; i < photos.length; i++) {
            list.add(new Layer(photos[i], masks[i]));
        }
        applyTrack(list, position);
    }

    private void applyTrack(List<Layer> list, float position) {
        Track current = track;
        Track next = new Track(current != null && current.layers().equals(list) ? current.layers() : List.copyOf(list),
                Math.max(0f, Math.min(list.size() - 1f, position)));
        if (next.equals(current)) {
            return;
        }
        track = next;
        if (current == null) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            bounds.inScaled = false;
            BitmapFactory.decodeResource(getResources(), list.get(0).photo(), bounds);
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                photoAspect = bounds.outWidth / (float) bounds.outHeight;
            }
        }
        if (!revealed) {
            poster.setPhoto(next.nearest().photo());
        }
        wake();
        startIfReady();
    }

    /** Field mode sends wind waves through crops instead of treating them as distant trees. */
    public void setFieldMode(boolean enabled) {
        fieldMode = enabled;
    }

    public void setCropFocus(float x, float y) {
        focusX = x;
        focusY = y;
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

    private void updateOnScreen() {
        boolean visible = isShown() && getGlobalVisibleRect(visibleArea);
        if (visible != onScreen) {
            onScreen = visible;
            wake();
        }
    }

    private boolean shouldRender() {
        return !paused && onScreen && isAttachedToWindow() && isShown();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        post(this::updateOnScreen);
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (poster != null) {
            updateOnScreen();
            wake();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isClickable()) {
            return super.onTouchEvent(event);
        }
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

    private float[] crop(int width, int height) {
        float aspect = width / (float) height;
        float scaleX = 1f;
        float scaleY = 1f;
        if (aspect > photoAspect) {
            scaleY = photoAspect / aspect;
        } else {
            scaleX = aspect / photoAspect;
        }
        return new float[] {scaleX, scaleY, (1f - scaleX) * focusX, (1f - scaleY) * focusY};
    }

    private void startIfReady() {
        SurfaceTexture surface = texture.getSurfaceTexture();
        if (renderer == null && surface != null && track != null && budget.animated()
                && texture.getWidth() > 0 && texture.getHeight() > 0) {
            int width = Math.min(budget.maxWidth(), texture.getWidth());
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
        revealed = false;
        texture.animate().cancel();
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

    private void showStillPhoto() {
        revealed = false;
        texture.animate().cancel();
        texture.setAlpha(0f);
        Track current = track;
        if (current != null) {
            poster.setPhoto(current.nearest().photo());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        stopRenderer();
        super.onDetachedFromWindow();
    }

    private final class RenderThread extends Thread {
        private static final long SLOW_WORK_NANOS = 27_000_000L;
        private static final int SAMPLE_FRAMES = 45;
        private static final float FADE_SPEED = 2.8f;

        private final SurfaceTexture surfaceTexture;
        private final int width;
        private final int height;
        private final float[] cropValues;
        private final Map<Integer, Integer> textures = new LinkedHashMap<>(16, 0.75f, true);
        private volatile boolean running = true;
        private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        private EGLContext context = EGL14.EGL_NO_CONTEXT;
        private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
        private final ExecutorService decoder = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "living-photo-decoder");
            thread.setDaemon(true);
            return thread;
        });
        private final Queue<Decoded> decoded = new ConcurrentLinkedQueue<>();
        private final Set<Integer> pending = new HashSet<>();
        private Track shownTrack;
        private float shownPosition;
        private Layer drawFrom;
        private Layer drawTo;
        private float drawMix;
        private Layer fadeFrom;
        private Layer fadeTo;
        private float fadeMix;
        private boolean fading;
        private boolean uploaded;
        private float flow;
        private float progress;
        private float rain;
        private float wind;
        private float cloud;
        private float golden;
        private float heat;
        private float dryness;
        private float health;

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
            golden = start.golden();
            heat = start.heat();
            dryness = start.dryness();
            health = start.health();
            shownTrack = track;
            shownPosition = shownTrack.position();
            setDraw(shownTrack, shownPosition);
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
            boolean failed = false;
            try {
                if (setUpEgl()) {
                    int program = buildProgram();
                    if (program != 0) {
                        renderLoop(program);
                    } else {
                        failed = true;
                    }
                } else {
                    failed = true;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException | IOException failure) {
                failed = true;
            } finally {
                decoder.shutdownNow();
                Decoded left;
                while ((left = decoded.poll()) != null) {
                    left.bitmap().recycle();
                }
                releaseEgl();
            }
            if (failed && running) {
                post(LivingPhotoView.this::showStillPhoto);
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
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uPhoto"), 0);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uMask"), 1);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uPhotoB"), 2);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uMaskB"), 3);
            GLES20.glViewport(0, 0, width, height);
            return program;
        }

        private Bitmap decode(int resource) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            if (budget.reducedEffects()) {
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeResource(getResources(), resource, options);
                options.inSampleSize = options.outWidth >= width * 2 ? 2 : 1;
                options.inJustDecodeBounds = false;
            }
            return BitmapFactory.decodeResource(getResources(), resource, options);
        }

        private int upload(int resource, Bitmap bitmap) {
            if (textures.size() >= MAX_TEXTURES) {
                Iterator<Map.Entry<Integer, Integer>> oldest = textures.entrySet().iterator();
                int[] old = {oldest.next().getValue()};
                oldest.remove();
                GLES20.glDeleteTextures(1, old, 0);
            }
            int[] id = new int[1];
            GLES20.glGenTextures(1, id, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE7);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
            textures.put(resource, id[0]);
            return id[0];
        }

        /** Only the first frame may decode here. Later photos arrive from the background decoder. */
        private int textureFor(int resource) {
            Integer existing = textures.get(resource);
            if (existing != null) {
                return existing;
            }
            Bitmap bitmap = decode(resource);
            if (bitmap == null) {
                throw new IllegalStateException("Missing living photo layer");
            }
            return upload(resource, bitmap);
        }

        private void request(int resource) {
            if (textures.containsKey(resource) || !pending.add(resource)) {
                return;
            }
            decoder.execute(() -> {
                Bitmap bitmap = decode(resource);
                if (bitmap != null) {
                    decoded.add(new Decoded(resource, bitmap));
                }
            });
        }

        private void receiveOnePhoto() {
            uploaded = false;
            Decoded next = decoded.poll();
            if (next != null) {
                pending.remove(next.resource());
                if (textures.containsKey(next.resource())) {
                    next.bitmap().recycle();
                } else {
                    upload(next.resource(), next.bitmap());
                    uploaded = true;
                }
            }
        }

        private boolean ready(Layer layer) {
            return textures.containsKey(layer.photo()) && textures.containsKey(layer.mask());
        }

        private void bind(int unit, int resource) {
            int id = textureFor(resource);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        }

        private void setDraw(Track source, float position) {
            int from = Math.min(source.layers().size() - 1, (int) Math.floor(position));
            int to = Math.min(source.layers().size() - 1, from + 1);
            drawFrom = source.layers().get(from);
            drawTo = source.layers().get(to);
            drawMix = from == to ? 0f : position - from;
        }

        /** Loads the photos around the current step, so a phone never holds a whole crop in memory. */
        private void requestAround(Track target) {
            request(target.nearest().photo());
            request(target.nearest().mask());
            if (!target.layers().equals(shownTrack.layers())) {
                return;
            }
            int base = (int) Math.floor(shownPosition);
            int last = target.layers().size() - 1;
            for (int i = Math.max(0, base - 1); i <= Math.min(last, base + 2); i++) {
                request(target.layers().get(i).photo());
                request(target.layers().get(i).mask());
            }
        }

        /** Moves along a track without restarting at stage edges, and fades only to a new track. */
        private boolean updateLayers(float delta) {
            receiveOnePhoto();
            Track target = track;
            requestAround(target);
            if (fading || !target.layers().equals(shownTrack.layers())) {
                Layer goal = target.nearest();
                if (!ready(goal)) {
                    if (!fading) {
                        return true;
                    }
                } else if (!fading) {
                    fadeFrom = drawMix >= 0.5f ? drawTo : drawFrom;
                    fadeTo = goal;
                    fadeMix = 0f;
                    fading = true;
                } else if (!goal.equals(fadeTo)) {
                    fadeFrom = fadeMix >= 0.5f ? fadeTo : fadeFrom;
                    fadeTo = goal;
                    fadeMix = 0f;
                }
                fadeMix = Math.min(1f, fadeMix + delta * FADE_SPEED);
                drawFrom = fadeFrom;
                drawTo = fadeTo;
                drawMix = fadeMix * fadeMix * (3f - 2f * fadeMix);
                if (fadeMix >= 1f && goal.equals(fadeTo)) {
                    fading = false;
                    shownTrack = target;
                    shownPosition = target.position();
                    setDraw(shownTrack, shownPosition);
                }
                return true;
            }
            shownTrack = target;
            float distance = target.position() - shownPosition;
            boolean moving = Math.abs(distance) > 0.002f;
            float next = moving ? shownPosition + distance * Math.min(1f, delta * 11f) : target.position();
            int from = Math.min(target.layers().size() - 1, (int) Math.floor(next));
            int to = Math.min(target.layers().size() - 1, from + 1);
            if (ready(target.layers().get(from)) && ready(target.layers().get(to))) {
                shownPosition = next;
                setDraw(target, next);
            }
            return moving;
        }

        private void renderLoop(int program) throws InterruptedException {
            int time = GLES20.glGetUniformLocation(program, "uTime");
            int uWaterT = GLES20.glGetUniformLocation(program, "uWaterT");
            int uRainT = GLES20.glGetUniformLocation(program, "uRainT");
            float waterPhase = 0f;
            float rainPhase = 0f;
            int uFlow = GLES20.glGetUniformLocation(program, "uFlow");
            int uProgress = GLES20.glGetUniformLocation(program, "uProgress");
            int uRain = GLES20.glGetUniformLocation(program, "uRain");
            int uWind = GLES20.glGetUniformLocation(program, "uWind");
            int uCloud = GLES20.glGetUniformLocation(program, "uCloud");
            int uGold = GLES20.glGetUniformLocation(program, "uGold");
            int uHeat = GLES20.glGetUniformLocation(program, "uHeat");
            int uLow = GLES20.glGetUniformLocation(program, "uLow");
            int uRippleAge = GLES20.glGetUniformLocation(program, "uRippleAge");
            int uRipple = GLES20.glGetUniformLocation(program, "uRipple");
            int uBlend = GLES20.glGetUniformLocation(program, "uBlend");
            int uField = GLES20.glGetUniformLocation(program, "uField");
            int uDry = GLES20.glGetUniformLocation(program, "uDry");
            int uHealth = GLES20.glGetUniformLocation(program, "uHealth");
            long start = System.nanoTime();
            long last = start;
            long slowWork = 0;
            int sampled = 0;
            boolean low = budget.reducedEffects();
            long frameNanos = budget.frameMillis() * 1_000_000L;
            boolean shownFirstFrame = false;
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
                ease(scene, Math.min(1f, delta * 2.6f));
                boolean moving = updateLayers(delta);
                bind(0, drawFrom.photo());
                bind(1, drawFrom.mask());
                bind(2, drawTo.photo());
                bind(3, drawTo.mask());
                float ripple;
                float x;
                float y;
                synchronized (lock) {
                    ripple = rippleAt == 0 ? RIPPLE_SECONDS + 1f : (now - rippleAt) / 1_000_000_000f;
                    x = rippleX;
                    y = rippleY;
                }
                float cycle = (float) (((now - start) / 1_000_000L) % (long) (SWAY_SECONDS * 2000f)) / 1000f;
                waterPhase = (waterPhase + (0.16f + 0.95f * flow) * delta) % LOOP_SECONDS;
                rainPhase = (rainPhase + delta) % LOOP_SECONDS;
                GLES20.glUniform1f(time, SWAY_SECONDS - Math.abs(cycle - SWAY_SECONDS));
                GLES20.glUniform1f(uWaterT, waterPhase);
                GLES20.glUniform1f(uRainT, rainPhase);
                GLES20.glUniform1f(uFlow, flow);
                GLES20.glUniform1f(uProgress, progress);
                GLES20.glUniform1f(uRain, rain);
                GLES20.glUniform1f(uWind, wind);
                GLES20.glUniform1f(uCloud, cloud);
                GLES20.glUniform1f(uGold, golden);
                GLES20.glUniform1f(uHeat, heat);
                GLES20.glUniform1f(uLow, low ? 1f : 0f);
                GLES20.glUniform1f(uRippleAge, ripple);
                GLES20.glUniform2f(uRipple, x, y);
                GLES20.glUniform1f(uBlend, drawMix);
                GLES20.glUniform1f(uField, fieldMode ? 1f : 0f);
                GLES20.glUniform1f(uDry, dryness);
                GLES20.glUniform1f(uHealth, health);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                    if (running) {
                        post(LivingPhotoView.this::showStillPhoto);
                    }
                    return;
                }
                if (!shownFirstFrame) {
                    shownFirstFrame = true;
                    revealed = true;
                    postDelayed(() -> {
                        if (revealed) {
                            texture.animate().alpha(1f).setDuration(260).start();
                        }
                    }, 70);
                }
                long work = System.nanoTime() - now;
                if (!uploaded) {
                    slowWork += work;
                    if (!low && ++sampled == SAMPLE_FRAMES) {
                        if (slowWork / SAMPLE_FRAMES > SLOW_WORK_NANOS) {
                            low = true;
                            frameNanos = Math.max(frameNanos, 40_000_000L);
                        }
                        sampled = 0;
                        slowWork = 0;
                    }
                }
                long pace = moving ? Math.min(frameNanos, low ? 33_000_000L : 16_000_000L) : frameNanos;
                long sleep = pace - work;
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
            golden += (target.golden() - golden) * amount;
            heat += (target.heat() - heat) * amount;
            dryness += (target.dryness() - dryness) * amount;
            health += (target.health() - health) * amount;
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
