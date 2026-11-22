package com.agroshift.feature.help;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.CropPhotos;
import com.agroshift.core.ui.PhotoView;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;

/** Assistant, interactive crop guide and official contacts. */
public final class HelpFragment extends Fragment {
    private enum Filter { ALL, KHARIF, RABI }

    private static final int COLUMNS = 2;

    private LinearLayout grid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_help, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        ZameenUi.applySafeArea(view, false);
        view.findViewById(R.id.help_credits).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(string.photo_credits)
                .setMessage(string.photo_credits_body)
                .setPositiveButton(string.action_close, null)
                .show());
        View ask = view.findViewById(R.id.help_ask);
        ZameenUi.applySurface(ask, com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.applySurface(view.findViewById(R.id.help_ask_icon), com.agroshift.core.ui.R.color.zameen_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.attachPressSpring(ask);
        ask.setOnClickListener(v -> ((AppNavigator) requireActivity()).openAssistant());

        grid = view.findViewById(R.id.help_crop_grid);
        ChipGroup filters = view.findViewById(R.id.help_crop_filters);
        addFilter(filters, string.filter_all, Filter.ALL);
        addFilter(filters, string.season_kharif, Filter.KHARIF);
        addFilter(filters, string.season_rabi, Filter.RABI);
        filters.setOnCheckedStateChangeListener((group, ids) -> {
            if (!ids.isEmpty()) {
                renderCrops((Filter) group.findViewById(ids.get(0)).getTag());
            }
        });
        renderCrops(Filter.ALL);

        LinearLayout links = view.findViewById(R.id.help_links);
        addLink(links, string.link_agri, com.agroshift.core.ui.R.drawable.ic_ui_leaf, "https://agripunjab.gov.pk/");
        addLink(links, string.link_pmd, com.agroshift.core.ui.R.drawable.ic_ui_cloud, "https://www.pmd.gov.pk/");
        addLink(links, string.link_ndma, com.agroshift.core.ui.R.drawable.ic_ui_alert, "https://ndma.gov.pk/");
        addLink(links, string.link_flood, com.agroshift.core.ui.R.drawable.ic_ui_rain, "https://ffc.gov.pk/");
    }

    private void addFilter(ChipGroup group, int label, Filter filter) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_help_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setCheckable(true);
        chip.setText(label);
        chip.setTag(filter);
        chip.setChecked(filter == Filter.ALL);
        group.addView(chip);
    }

    private void renderCrops(Filter filter) {
        List<String> slugs = new ArrayList<>();
        if (filter != Filter.RABI) {
            slugs.addAll(Crop.KHARIF);
        }
        if (filter != Filter.KHARIF) {
            slugs.addAll(Crop.RABI);
        }
        if (filter == Filter.ALL) {
            slugs.add(Crop.YEAR_ROUND);
        }
        grid.removeAllViews();
        int gap = getResources().getDimensionPixelSize(com.agroshift.core.ui.R.dimen.zameen_gap);
        LinearLayout row = null;
        for (int i = 0; i < slugs.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = gap;
                grid.addView(row, rowParams);
            }
            View card = cropCard(row, slugs.get(i));
            if (i % COLUMNS == 1) {
                ((LinearLayout.LayoutParams) card.getLayoutParams()).setMarginStart(gap);
            }
            row.addView(card);
        }
        if (slugs.size() % COLUMNS == 1 && row != null) {
            View spacer = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 1, 1f);
            params.setMarginStart(gap);
            row.addView(spacer, params);
        }
    }

    private View cropCard(ViewGroup parent, String slug) {
        Context context = requireContext();
        Crop crop = Crop.bySlug(slug);
        View card = getLayoutInflater().inflate(R.layout.item_crop_card, parent, false);
        ZameenUi.applySurface(card, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        String name = EngineText.cropName(context, slug);
        ((TextView) card.findViewById(R.id.crop_name)).setText(name);
        ((TextView) card.findViewById(R.id.crop_season)).setText(Crop.KHARIF.contains(slug) ? string.season_kharif
                : Crop.RABI.contains(slug) ? string.season_rabi : string.crop_year_round);
        ((TextView) card.findViewById(R.id.crop_water)).setText(getString(string.crop_water_short,
                EngineText.number(crop.waterNeedMm(), 0)));
        ((PhotoView) card.findViewById(R.id.crop_photo)).setPhoto(CropPhotos.photo(slug));
        card.setContentDescription(getString(string.crop_open, name));
        ZameenUi.attachPressSpring(card);
        card.setOnClickListener(v -> {
            if (getChildFragmentManager().findFragmentByTag("crop_guide") == null) {
                CropGuideSheet.create(slug).show(getChildFragmentManager(), "crop_guide");
            }
        });
        return card;
    }

    private void addLink(LinearLayout parent, int label, int icon, String url) {
        View row = getLayoutInflater().inflate(R.layout.item_help_link, parent, false);
        ((ImageView) row.findViewById(R.id.link_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.link_label)).setText(label);
        ZameenUi.applySurface(row, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.attachPressSpring(row);
        row.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException noBrowser) {
                Snackbar.make(requireView(), string.assistant_error, Snackbar.LENGTH_LONG).show();
            }
        });
        parent.addView(row);
    }

    @Override
    public void onDestroyView() {
        grid = null;
        super.onDestroyView();
    }
}
