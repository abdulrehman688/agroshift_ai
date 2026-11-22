package com.agroshift.feature.help;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;

/** Assistant, simple crop guide and official contacts. */
public final class HelpFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_help, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        view.findViewById(R.id.help_credits).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(string.photo_credits)
                .setMessage(string.photo_credits_body)
                .setPositiveButton(string.action_close, null)
                .show());
        View ask = view.findViewById(R.id.help_ask);
        ZameenUi.applySurface(ask, com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.attachPressSpring(ask);
        ask.setOnClickListener(v -> ((AppNavigator) requireActivity()).openAssistant());

        ChipGroup crops = view.findViewById(R.id.help_crops);
        List<String> slugs = new ArrayList<>(Crop.KHARIF);
        slugs.addAll(Crop.RABI);
        slugs.add(Crop.YEAR_ROUND);
        for (String slug : slugs) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_help_chip, crops, false);
            chip.setText(EngineText.cropName(requireContext(), slug));
            chip.setOnClickListener(v -> showGuide(slug));
            crops.addView(chip);
        }

        LinearLayout links = view.findViewById(R.id.help_links);
        addLink(links, string.link_agri, "https://agripunjab.gov.pk/");
        addLink(links, string.link_pmd, "https://www.pmd.gov.pk/");
        addLink(links, string.link_ndma, "https://ndma.gov.pk/");
        addLink(links, string.link_flood, "https://ffc.gov.pk/");
    }

    private void showGuide(String slug) {
        Context context = requireContext();
        Crop crop = Crop.bySlug(slug);
        List<String> lines = new ArrayList<>();
        lines.add(getString(string.guide_sow, EngineText.month(context, crop.calendar().sowMonth())));
        lines.add(getString(string.guide_harvest, EngineText.month(context, crop.calendar().harvestMonth())));
        lines.add(getString(crop.waterNeedMm() < 450 ? string.guide_water_low
                : crop.waterNeedMm() <= 700 ? string.guide_water_medium : string.guide_water_high));
        if (crop.heatTolerance() >= 4) {
            lines.add(getString(string.guide_heat_good));
        } else if (crop.heatTolerance() <= 2) {
            lines.add(getString(string.guide_heat_bad));
        }
        if (crop.frostTolerance() >= 4) {
            lines.add(getString(string.guide_frost_good));
        } else if (crop.frostTolerance() <= 2) {
            lines.add(getString(string.guide_frost_bad));
        }
        if (crop.nitrogenFixing()) {
            lines.add(getString(string.guide_soil_good));
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(EngineText.cropName(context, slug))
                .setMessage(String.join("\n", lines))
                .setPositiveButton(string.action_close, null)
                .show();
    }

    private void addLink(LinearLayout parent, int label, String url) {
        TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_help_link, parent, false);
        row.setText(label);
        ZameenUi.applySurface(row, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        row.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException noBrowser) {
                Snackbar.make(requireView(), string.assistant_error, Snackbar.LENGTH_LONG).show();
            }
        });
        parent.addView(row);
    }
}
