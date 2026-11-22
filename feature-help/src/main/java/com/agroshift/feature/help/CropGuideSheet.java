package com.agroshift.feature.help;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.domain.engine.CropJourney;
import com.agroshift.core.domain.scene.LivingScene;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.CropPhotos;
import com.agroshift.core.ui.LivingPhotoView;
import com.agroshift.core.ui.SplitBarView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.slider.Slider;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Interactive crop guide: slide through the season and compare the crop's traits. */
public final class CropGuideSheet extends BottomSheetDialogFragment {
    private static final String ARG_CROP = "crop";
    private static final int MAX_TRAIT = 5;

    private String slug;

    public static CropGuideSheet create(String slug) {
        CropGuideSheet sheet = new CropGuideSheet();
        Bundle args = new Bundle();
        args.putString(ARG_CROP, slug);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_crop_guide, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Context context = requireContext();
        Crop crop = Crop.bySlug(requireArguments().getString(ARG_CROP));
        ((TextView) view.findViewById(R.id.guide_name)).setText(EngineText.cropName(context, crop.slug()));
        ((TextView) view.findViewById(R.id.guide_season)).setText(getString(string.guide_season_range,
                EngineText.month(context, crop.calendar().sowMonth()),
                EngineText.month(context, crop.calendar().harvestMonth())));

        List<CropJourney.Step> journey = CropJourney.of(crop);
        LivingPhotoView growth = view.findViewById(R.id.guide_growth);
        slug = crop.slug();
        growth.setFieldMode(true);
        growth.setCropFocus(0.5f, 0.6f);
        growth.setScene(LivingScene.forField(LocalDateTime.now(), null));
        TextView month = view.findViewById(R.id.guide_month);
        TextView stage = view.findViewById(R.id.guide_stage);
        Slider slider = view.findViewById(R.id.guide_slider);
        slider.setValueFrom(0f);
        slider.setValueTo(Math.max(1, journey.size() - 1));
        slider.setStepSize(1f);
        slider.setValue(0f);
        slider.setEnabled(journey.size() > 1);
        slider.setLabelFormatter(value -> EngineText.month(context,
                journey.get(Math.min(journey.size() - 1, Math.round(value))).month()));
        slider.addOnChangeListener((bar, value, fromUser) -> showStep(context, journey, value, growth, month, stage));
        showStep(context, journey, 0f, growth, month, stage);

        LinearLayout traits = view.findViewById(R.id.guide_traits);
        addTrait(traits, string.trait_drought, com.agroshift.core.ui.R.drawable.ic_ui_drop, crop.droughtTolerance());
        addTrait(traits, string.trait_heat, com.agroshift.core.ui.R.drawable.ic_ui_heat, crop.heatTolerance());
        addTrait(traits, string.trait_frost, com.agroshift.core.ui.R.drawable.ic_ui_frost, crop.frostTolerance());
        addTrait(traits, string.trait_land, com.agroshift.core.ui.R.drawable.ic_ui_shield, crop.erosionProtection());
        addTrait(traits, string.trait_income, com.agroshift.core.ui.R.drawable.ic_ui_money, crop.incomeLevel());

        ((TextView) view.findViewById(R.id.guide_water)).setText(getString(string.guide_water_need,
                EngineText.number(crop.waterNeedMm(), 0)));
        List<String> facts = new ArrayList<>();
        facts.add(getString(crop.waterNeedMm() < 450 ? string.guide_water_low
                : crop.waterNeedMm() <= 700 ? string.guide_water_medium : string.guide_water_high));
        if (crop.heatTolerance() >= 4) {
            facts.add(getString(string.guide_heat_good));
        } else if (crop.heatTolerance() <= 2) {
            facts.add(getString(string.guide_heat_bad));
        }
        if (crop.frostTolerance() >= 4) {
            facts.add(getString(string.guide_frost_good));
        } else if (crop.frostTolerance() <= 2) {
            facts.add(getString(string.guide_frost_bad));
        }
        if (crop.nitrogenFixing()) {
            facts.add(getString(string.guide_soil_good));
        }
        ((TextView) view.findViewById(R.id.guide_facts)).setText(String.join("\n", facts));
    }

    private void showStep(Context context, List<CropJourney.Step> journey, float value, LivingPhotoView growth,
            TextView month, TextView stage) {
        CropJourney.Step step = journey.get(Math.min(journey.size() - 1, Math.round(value)));
        CropPhotos.show(growth, slug, (float) step.growth());
        month.setText(EngineText.month(context, step.month()));
        stage.setText(getString(string.timeline_stage, EngineText.stage(context, step.stageKey())));
    }

    private void addTrait(LinearLayout parent, int label, int icon, int value) {
        View row = getLayoutInflater().inflate(R.layout.item_crop_trait, parent, false);
        ((ImageView) row.findViewById(R.id.trait_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.trait_label)).setText(label);
        ((TextView) row.findViewById(R.id.trait_value)).setText(getString(string.trait_value, value, MAX_TRAIT));
        ((SplitBarView) row.findViewById(R.id.trait_bar)).setValues(value, MAX_TRAIT - value);
        parent.addView(row);
    }
}
