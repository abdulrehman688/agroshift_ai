package com.agroshift.app;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import com.agroshift.core.domain.season.SeasonPosition;
import com.agroshift.core.ui.PhotoView;
import com.agroshift.core.ui.ZameenUi;
import java.time.LocalDate;

/** Short branded screen shown when the app reopens. Tap skips it, and it never blocks loading. */
final class LaunchScreen {
    private static final long SHOW_MILLIS = 1450;
    private static final long REDUCED_SHOW_MILLIS = 550;
    private static final long EXIT_MILLIS = 320;

    private LaunchScreen() {
    }

    static void show(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        View screen = LayoutInflater.from(activity).inflate(R.layout.view_launch, content, false);
        SeasonPosition season = SeasonPosition.of(LocalDate.now());
        PhotoView photo = screen.findViewById(R.id.launch_photo);
        photo.setPhoto(season.season() == SeasonPosition.Season.RABI
                ? com.agroshift.core.ui.R.drawable.photo_home_rabi
                : com.agroshift.core.ui.R.drawable.photo_home_kharif);
        content.addView(screen);

        boolean motion = ZameenUi.motionEnabled(activity);
        View brand = screen.findViewById(R.id.launch_brand);
        if (motion) {
            photo.setScaleX(1.12f);
            photo.setScaleY(1.12f);
            photo.animate().scaleX(1f).scaleY(1f).setDuration(SHOW_MILLIS + EXIT_MILLIS)
                    .setInterpolator(new DecelerateInterpolator()).start();
            brand.setAlpha(0f);
            brand.setTranslationY(18 * activity.getResources().getDisplayMetrics().density);
            brand.animate().alpha(1f).translationY(0f).setStartDelay(90).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        Runnable close = () -> dismiss(screen, motion);
        screen.setOnClickListener(v -> close.run());
        screen.postDelayed(close, motion ? SHOW_MILLIS : REDUCED_SHOW_MILLIS);
    }

    private static void dismiss(View screen, boolean motion) {
        if (!(screen.getParent() instanceof ViewGroup parent) || !screen.isClickable()) {
            return;
        }
        screen.setClickable(false);
        if (motion) {
            screen.animate().alpha(0f).setDuration(EXIT_MILLIS).withEndAction(() -> parent.removeView(screen)).start();
        } else {
            parent.removeView(screen);
        }
    }
}
