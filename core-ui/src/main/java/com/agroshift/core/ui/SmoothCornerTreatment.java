package com.agroshift.core.ui;

import androidx.annotation.NonNull;
import com.google.android.material.shape.CornerTreatment;
import com.google.android.material.shape.ShapePath;

/** Continuous-looking corner with a softer entry than a circular arc. */
public final class SmoothCornerTreatment extends CornerTreatment {
    private static final float CONTROL = 0.28f;

    @Override
    public void getCornerPath(@NonNull ShapePath path, float angle, float interpolation,
            float radius) {
        float size = radius * interpolation;
        path.reset(0, size, 180, 180 - angle);
        path.cubicToPoint(0, size * CONTROL, size * CONTROL, 0, size, 0);
    }
}
