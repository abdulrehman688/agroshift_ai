package com.agroshift.core.domain.scene;

/** Maps crop growth to a blend between three real photo stages: young, growing and mature. */
public final class CropVisual {
    public static final int LAST_STAGE = 2;

    public record Blend(int from, int to, float amount) {
    }

    private CropVisual() {
    }

    public static Blend blend(float growth) {
        float position = Math.max(0f, Math.min(1f, growth)) * LAST_STAGE;
        int from = Math.min(LAST_STAGE, (int) Math.floor(position));
        int to = Math.min(LAST_STAGE, from + 1);
        return new Blend(from, to, from == to ? 0f : position - from);
    }
}
