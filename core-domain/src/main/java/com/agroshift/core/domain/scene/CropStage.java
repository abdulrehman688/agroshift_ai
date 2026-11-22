package com.agroshift.core.domain.scene;

/** Picks which real growth-stage photo matches a crop's progress through its season. */
public final class CropStage {
    private CropStage() {
    }

    public static int index(float growth, int count) {
        if (count <= 1) {
            return 0;
        }
        float clamped = Math.max(0f, Math.min(1f, growth));
        return Math.round(clamped * (count - 1));
    }
}
