package com.agroshift.feature.account;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.agroshift.core.domain.scene.LivingScene;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.LivingPhotoView;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;

/** Three photo pages shown once, with Urdu and English side by side in the reading flow. */
public final class OnboardingFragment extends Fragment {
    public interface Host {
        void onOnboardingDone();
    }

    private static final LivingScene WELCOME_SCENE = new LivingScene(0f, 0f, 0f, 0.42f, 0.25f, 1f, 0f, 0f, 0f, 1f);
    private static final int[] PHOTOS = {com.agroshift.core.ui.R.drawable.photo_onboard_field,
            com.agroshift.core.ui.R.drawable.photo_onboard_harvest,
            com.agroshift.core.ui.R.drawable.photo_onboard_tractor};
    private static final int[] MASKS = {com.agroshift.core.ui.R.drawable.photo_onboard_field_mask,
            com.agroshift.core.ui.R.drawable.photo_onboard_harvest_mask,
            com.agroshift.core.ui.R.drawable.photo_onboard_tractor_mask};
    private static final int[] TITLES_UR = {string.bi_title_1_ur, string.bi_title_2_ur, string.bi_title_3_ur};
    private static final int[] BODIES_UR = {string.bi_body_1_ur, string.bi_body_2_ur, string.bi_body_3_ur};
    private static final int[] TITLES_EN = {string.bi_title_1_en, string.bi_title_2_en, string.bi_title_3_en};
    private static final int[] BODIES_EN = {string.bi_body_1_en, string.bi_body_2_en, string.bi_body_3_en};

    private ViewPager2 pager;
    private ViewPager2.OnPageChangeCallback callback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        pager = view.findViewById(R.id.onboarding_pager);
        LinearLayout dots = view.findViewById(R.id.onboarding_dots);
        MaterialButton next = view.findViewById(R.id.onboarding_next);
        View skip = view.findViewById(R.id.onboarding_skip);
        View controls = view.findViewById(R.id.onboarding_controls);
        ZameenUi.polishPrimary(next);
        int controlsBottom = controls.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) skip.getLayoutParams();
            params.topMargin = safe.top + dp(4);
            skip.setLayoutParams(params);
            controls.setPadding(controls.getPaddingLeft(), controls.getPaddingTop(),
                    controls.getPaddingRight(), controlsBottom + safe.bottom);
            return insets;
        });
        pager.setAdapter(new PageAdapter());
        pager.setOffscreenPageLimit(1);
        LivingPhotoView living = view.findViewById(R.id.onboarding_photo);
        living.setFieldMode(true);
        living.setScene(WELCOME_SCENE);
        living.setTrack(PHOTOS, MASKS, 0f);
        for (int i = 0; i < PHOTOS.length; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(8), dp(6));
            params.setMarginEnd(dp(6));
            dots.addView(dot, params);
        }
        callback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float offset, int offsetPixels) {
                living.setTrack(PHOTOS, MASKS, position + offset);
            }

            @Override
            public void onPageSelected(int position) {
                renderDots(dots, position);
                next.setText(position == PHOTOS.length - 1 ? string.bi_start : string.bi_next);
            }
        };
        pager.registerOnPageChangeCallback(callback);
        next.setOnClickListener(v -> {
            if (pager.getCurrentItem() == PHOTOS.length - 1) {
                ((Host) requireActivity()).onOnboardingDone();
            } else {
                pager.setCurrentItem(pager.getCurrentItem() + 1, true);
            }
        });
        skip.setOnClickListener(v -> ((Host) requireActivity()).onOnboardingDone());
    }

    private void renderDots(LinearLayout dots, int selected) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            ViewGroup.LayoutParams params = dot.getLayoutParams();
            params.width = dp(i == selected ? 26 : 8);
            dot.setLayoutParams(params);
            dot.setBackgroundColor(ContextCompat.getColor(requireContext(), i == selected
                    ? com.agroshift.core.ui.R.color.zameen_accent
                    : com.agroshift.core.ui.R.color.zameen_hairline));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        pager.unregisterOnPageChangeCallback(callback);
        pager.setAdapter(null);
        pager = null;
        callback = null;
        super.onDestroyView();
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final TextView titleUr;
        final TextView bodyUr;
        final TextView titleEn;
        final TextView bodyEn;

        PageHolder(@NonNull View view) {
            super(view);
            titleUr = view.findViewById(R.id.page_title_ur);
            bodyUr = view.findViewById(R.id.page_body_ur);
            titleEn = view.findViewById(R.id.page_title_en);
            bodyEn = view.findViewById(R.id.page_body_en);
        }
    }

    private static final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.titleUr.setText(TITLES_UR[position]);
            holder.bodyUr.setText(BODIES_UR[position]);
            holder.titleEn.setText(TITLES_EN[position]);
            holder.bodyEn.setText(BODIES_EN[position]);
        }

        @Override
        public int getItemCount() {
            return PHOTOS.length;
        }
    }
}
