package com.agroshift.core.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

/** Shared Zameen shape and motion helpers. */
public final class ZameenUi {
    private static final float PRESSED_SCALE = 0.97f;

    private ZameenUi() {
    }

    public static boolean motionEnabled(Context context) {
        return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    public static ShapeAppearanceModel smoothShape(Context context, @DimenRes int radius) {
        float size = context.getResources().getDimension(radius);
        return ShapeAppearanceModel.builder()
                .setAllCorners(new SmoothCornerTreatment())
                .setAllCornerSizes(size)
                .build();
    }

    public static void applySurface(View view, @ColorRes int color, @DimenRes int radius) {
        Context context = view.getContext();
        MaterialShapeDrawable drawable = new MaterialShapeDrawable(smoothShape(context, radius));
        drawable.setFillColor(ColorStateList.valueOf(ContextCompat.getColor(context, color)));
        view.setBackground(drawable);
    }

    /** Adds system-bar space to a screen while keeping its own margins. */
    public static void applySafeArea(View root, boolean includeBottom) {
        int start = root.getPaddingStart();
        int top = root.getPaddingTop();
        int end = root.getPaddingEnd();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            view.setPaddingRelative(start + (rtl ? safe.right : safe.left), top + safe.top,
                    end + (rtl ? safe.left : safe.right), bottom + (includeBottom ? safe.bottom : 0));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    public static void polishPrimary(MaterialButton button) {
        button.setShapeAppearanceModel(
                smoothShape(button.getContext(), R.dimen.zameen_radius_action));
        attachPressSpring(button);
    }

    /** Scale follows the finger and can reverse mid-animation from its current value. */
    @SuppressLint("ClickableViewAccessibility")
    public static void attachPressSpring(View view) {
        SpringForce forceX = spring();
        SpringForce forceY = spring();
        SpringAnimation scaleX = new SpringAnimation(view, DynamicAnimation.SCALE_X).setSpring(forceX);
        SpringAnimation scaleY = new SpringAnimation(view, DynamicAnimation.SCALE_Y).setSpring(forceY);
        view.setOnTouchListener((target, event) -> {
            if (!motionEnabled(target.getContext())) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                scaleX.animateToFinalPosition(PRESSED_SCALE);
                scaleY.animateToFinalPosition(PRESSED_SCALE);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                scaleX.animateToFinalPosition(1f);
                scaleY.animateToFinalPosition(1f);
            }
            return false;
        });
    }

    private static SpringForce spring() {
        return new SpringForce(1f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_MEDIUM);
    }
}
