package com.agroshift.feature.advisor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.text.BidiFormatter;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.agroshift.core.data.AnalysisRepository;
import com.agroshift.core.data.AnalysisState;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.FieldRepository;
import com.agroshift.core.domain.data.DataAccessException;
import com.agroshift.core.domain.engine.ClimateAlert;
import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.engine.RotationScores;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.MonthlyChartView;
import com.agroshift.core.ui.ScoreRingView;
import com.agroshift.core.ui.WhatIfFieldView;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Full rotation comparison with scores, climate chart, risks and What-If. */
public final class AdvisorFragment extends Fragment {
    private static final int MAX_ALERTS = 6;

    private FieldRepository fields;
    private AnalysisRepository analysis;
    private final List<FarmField> currentFields = new ArrayList<>();
    private FarmField selected;
    private AnalysisState lastState;
    private LiveData<AnalysisState> activeState;
    private final Observer<AnalysisState> stateObserver = this::render;
    private boolean bindingChips;

    private View empty;
    private View loading;
    private View error;
    private View content;
    private ChipGroup fieldChips;
    private ChipGroup priorityChips;
    private ChipGroup whatIfChips;
    private WhatIfFieldView scene;
    private ScoreRingView bestRing;
    private ClimateScenario activeScenario = ClimateScenario.NORMAL;
    private RotationAdvisor.Advice shownAdvice;
    private RotationAdvisor.Kind previewKind;
    private final Map<RotationAdvisor.Kind, View> optionCards = new EnumMap<>(RotationAdvisor.Kind.class);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_advisor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        ZameenUi.applySafeArea(view, false);
        AppServices services = (AppServices) requireContext().getApplicationContext();
        fields = services.fieldRepository();
        analysis = services.analysisRepository();
        empty = view.findViewById(R.id.advisor_empty);
        loading = view.findViewById(R.id.advisor_loading);
        error = view.findViewById(R.id.advisor_error);
        content = view.findViewById(R.id.advisor_content);
        fieldChips = view.findViewById(R.id.advisor_fields);
        priorityChips = view.findViewById(R.id.advisor_priorities);
        whatIfChips = view.findViewById(R.id.advisor_whatif);
        scene = view.findViewById(R.id.advisor_scene);
        bestRing = view.findViewById(R.id.advisor_best_ring);
        for (int id : new int[] {R.id.advisor_card_score, R.id.advisor_card_rain, R.id.advisor_card_demand}) {
            ZameenUi.applySurface(view.findViewById(id), com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
        MonthlyChartView monthChart = view.findViewById(R.id.advisor_chart);
        monthChart.setOnMonthSelected(this::showChartMonth);

        int radius = com.agroshift.core.ui.R.dimen.zameen_radius_card;
        ZameenUi.applySurface(view.findViewById(R.id.advisor_best_card),
                com.agroshift.core.ui.R.color.zameen_surface_accent, radius);
        ZameenUi.applySurface(view.findViewById(R.id.advisor_chart_card),
                com.agroshift.core.ui.R.color.zameen_surface, radius);

        MaterialButton add = view.findViewById(R.id.advisor_add);
        ZameenUi.polishPrimary(add);
        add.setOnClickListener(v -> ((AppNavigator) requireActivity()).openFieldSetup());
        MaterialButton retry = view.findViewById(R.id.advisor_retry);
        ZameenUi.polishPrimary(retry);
        retry.setOnClickListener(v -> {
            if (selected != null) {
                analysis.load(selected, true);
            }
        });

        addPriority(string.priority_water, FarmGoal.Priority.WATER);
        addPriority(string.priority_soil, FarmGoal.Priority.SOIL);
        addPriority(string.priority_income, FarmGoal.Priority.INCOME);
        addPriority(string.priority_erosion, FarmGoal.Priority.EROSION);
        addPriority(string.priority_pest, FarmGoal.Priority.PEST);
        priorityChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (bindingChips || selected == null) {
                return;
            }
            List<FarmGoal.Priority> priorities = new ArrayList<>();
            for (int id : ids) {
                priorities.add((FarmGoal.Priority) group.findViewById(id).getTag());
            }
            FarmGoal goal = FarmGoal.of(priorities);
            if (!goal.equals(selected.goal())) {
                fields.updateGoal(selected.id(), goal);
            }
        });

        addWhatIf(string.whatif_normal, com.agroshift.core.ui.R.drawable.ic_ui_leaf, ClimateScenario.NORMAL, true);
        addWhatIf(string.whatif_hot, com.agroshift.core.ui.R.drawable.ic_ui_heat, ClimateScenario.HOTTER, false);
        addWhatIf(string.whatif_dry, com.agroshift.core.ui.R.drawable.ic_ui_drop, ClimateScenario.LESS_RAIN, false);
        addWhatIf(string.whatif_both, com.agroshift.core.ui.R.drawable.ic_ui_alert, ClimateScenario.HOTTER_AND_DRIER, false);
        whatIfChips.setOnCheckedStateChangeListener((group, ids) -> renderOutlook());
        fieldChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (!bindingChips && !ids.isEmpty()) {
                analysis.selectField((String) group.findViewById(ids.get(0)).getTag());
            }
        });

        fields.observeFields().observe(getViewLifecycleOwner(), list -> {
            currentFields.clear();
            currentFields.addAll(list);
            bindSelection(analysis.selectedFieldId().getValue());
        });
        analysis.selectedFieldId().observe(getViewLifecycleOwner(), this::bindSelection);
    }

    private void addPriority(int label, FarmGoal.Priority priority) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_advisor_chip, priorityChips, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(priority);
        priorityChips.addView(chip);
    }

    private void addWhatIf(int label, int icon, ClimateScenario scenario, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_advisor_chip, whatIfChips, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(scenario);
        chip.setChipIconResource(icon);
        chip.setChipIconVisible(true);
        chip.setCheckedIconVisible(false);
        chip.setChipIconTintResource(com.agroshift.core.ui.R.color.zameen_accent);
        chip.setChecked(checked);
        whatIfChips.addView(chip);
    }

    private void bindSelection(@Nullable String requestedId) {
        FarmField next = null;
        for (FarmField field : currentFields) {
            if (field.id().equals(requestedId)) {
                next = field;
            }
        }
        if (next == null && !currentFields.isEmpty()) {
            next = currentFields.get(0);
        }
        selected = next;
        renderFieldChips();
        if (next == null) {
            detachState();
            show(empty);
            return;
        }
        bindPriorities(next.goal());
        LiveData<AnalysisState> live = analysis.observe(next.id());
        if (live != activeState) {
            detachState();
            activeState = live;
            live.observe(getViewLifecycleOwner(), stateObserver);
        }
        analysis.load(next, false);
    }

    private void bindPriorities(FarmGoal goal) {
        bindingChips = true;
        for (int i = 0; i < priorityChips.getChildCount(); i++) {
            Chip chip = (Chip) priorityChips.getChildAt(i);
            chip.setChecked(goal.has((FarmGoal.Priority) chip.getTag()));
        }
        bindingChips = false;
    }

    private void detachState() {
        if (activeState != null) {
            activeState.removeObserver(stateObserver);
            activeState = null;
        }
    }

    private void renderFieldChips() {
        bindingChips = true;
        fieldChips.removeAllViews();
        for (FarmField field : currentFields) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_advisor_chip, fieldChips, false);
            chip.setId(View.generateViewId());
            chip.setText(field.name());
            chip.setTag(field.id());
            fieldChips.addView(chip);
            if (selected != null && field.id().equals(selected.id())) {
                fieldChips.check(chip.getId());
            }
        }
        bindingChips = false;
        requireView().findViewById(R.id.advisor_fields_scroll)
                .setVisibility(currentFields.size() > 1 ? View.VISIBLE : View.GONE);
    }

    private void render(@Nullable AnalysisState state) {
        if (state == null || getView() == null) {
            return;
        }
        lastState = state;
        switch (state.status()) {
            case LOADING -> {
                show(loading);
                int total = Math.max(1, state.totalBlocks());
                LinearProgressIndicator bar = requireView().findViewById(R.id.advisor_progress);
                bar.setProgressCompat(Math.max(5, state.completedBlocks() * 100 / total), true);
            }
            case FAILED -> {
                show(error);
                ((TextView) requireView().findViewById(R.id.advisor_error_text)).setText(errorText(state.error()));
            }
            case NEEDS_LOCATION -> show(empty);
            case READY -> {
                renderOutlook();
                requireView().findViewById(R.id.advisor_stale)
                        .setVisibility(state.stale() ? View.VISIBLE : View.GONE);
                show(content);
            }
        }
    }

    private int errorText(@Nullable DataAccessException.Kind kind) {
        if (kind == DataAccessException.Kind.RATE_LIMITED || kind == DataAccessException.Kind.SERVER) {
            return string.advisor_error_busy;
        }
        return kind == DataAccessException.Kind.BAD_DATA
                ? string.advisor_error_data : string.advisor_error_network;
    }

    private void show(View visible) {
        for (View view : new View[] {empty, loading, error, content}) {
            view.setVisibility(view == visible ? View.VISIBLE : View.GONE);
        }
    }

    /** What-If updates every score, chart and risk below it. */
    private void renderOutlook() {
        if (getView() == null || lastState == null || lastState.status() != AnalysisState.Status.READY) {
            return;
        }
        View chip = whatIfChips.findViewById(whatIfChips.getCheckedChipId());
        ClimateScenario scenario = chip == null ? ClimateScenario.NORMAL : (ClimateScenario) chip.getTag();
        RotationAdvisor.Advice normal = lastState.advice();
        RotationAdvisor.Advice outlook = lastState.outlooks().get(scenario);
        TextView result = requireView().findViewById(R.id.advisor_whatif_result);
        if (outlook == null) {
            result.setText(string.advisor_error_data);
            outlook = normal;
        } else {
            String name = EngineText.optionName(requireContext(), outlook.best().kind());
            result.setText(outlook.best().kind() == normal.best().kind()
                    ? getString(string.whatif_same, name) : getString(string.whatif_change, name));
        }
        activeScenario = scenario;
        renderComparison(normal, outlook);
        renderAdvice(outlook);
    }

    private void renderComparison(RotationAdvisor.Advice normal, RotationAdvisor.Advice outlook) {
        View root = requireView();
        double score = outlook.best().scores().overall();
        double rain = outlook.profile().annualRainMm();
        double demand = yearly(outlook);
        ((TextView) root.findViewById(R.id.advisor_stat_score)).setText(EngineText.number(score, 0));
        ((TextView) root.findViewById(R.id.advisor_stat_rain)).setText(getString(string.whatif_mm, EngineText.number(rain, 0)));
        ((TextView) root.findViewById(R.id.advisor_stat_demand)).setText(getString(string.whatif_mm, EngineText.number(demand, 0)));
        ((TextView) root.findViewById(R.id.advisor_delta_score)).setText(delta(score - normal.best().scores().overall()));
        ((TextView) root.findViewById(R.id.advisor_delta_rain)).setText(delta(rain - normal.profile().annualRainMm()));
        ((TextView) root.findViewById(R.id.advisor_delta_demand)).setText(delta(demand - yearly(normal)));
    }

    private static double yearly(RotationAdvisor.Advice advice) {
        double total = 0;
        for (double value : advice.profile().series(Metric.ET0_MM)) {
            if (!Double.isNaN(value)) {
                total += value;
            }
        }
        return total;
    }

    private String delta(double value) {
        long rounded = Math.round(value);
        if (rounded == 0) {
            return getString(string.whatif_no_change);
        }
        String signed = (rounded > 0 ? "+" : "") + EngineText.number(rounded, 0);
        return getString(string.whatif_vs_normal, BidiFormatter.getInstance().unicodeWrap(signed));
    }

    private void showPreview(RotationAdvisor.Kind kind) {
        if (shownAdvice == null) {
            return;
        }
        RotationAdvisor.Option chosen = shownAdvice.best();
        for (RotationAdvisor.Option option : shownAdvice.options()) {
            if (option.kind() == kind) {
                chosen = option;
            }
        }
        previewKind = chosen.kind();
        scene.setScene(activeScenario.hotter(), activeScenario.drier(), (float) (chosen.scores().overall() / 100));
        scene.setContentDescription(getString(string.whatif_scene_description,
                EngineText.optionName(requireContext(), chosen.kind()),
                EngineText.number(chosen.scores().overall(), 0)));
        for (Map.Entry<RotationAdvisor.Kind, View> entry : optionCards.entrySet()) {
            boolean active = entry.getKey() == previewKind;
            entry.getValue().findViewById(R.id.option_showing).setVisibility(active ? View.VISIBLE : View.GONE);
            ZameenUi.applySurface(entry.getValue(), active ? com.agroshift.core.ui.R.color.zameen_surface_accent
                    : com.agroshift.core.ui.R.color.zameen_surface, com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
    }

    private void showChartMonth(int month) {
        if (shownAdvice == null || getView() == null) {
            return;
        }
        double rain = shownAdvice.profile().series(Metric.PRECIP_MM)[month];
        double demand = shownAdvice.profile().series(Metric.ET0_MM)[month];
        ((TextView) requireView().findViewById(R.id.advisor_chart_month)).setText(getString(string.chart_month_detail,
                EngineText.month(requireContext(), month + 1),
                EngineText.number(Double.isNaN(rain) ? 0 : rain, 0),
                EngineText.number(Double.isNaN(demand) ? 0 : demand, 0)));
    }

    private void renderAdvice(RotationAdvisor.Advice advice) {
        View root = requireView();
        Context context = requireContext();
        shownAdvice = advice;
        bestRing.setScore(advice.best().scores().overall());
        bestRing.setContentDescription(getString(string.advisor_score,
                EngineText.number(advice.best().scores().overall(), 0)));
        ((TextView) root.findViewById(R.id.advisor_best_name))
                .setText(EngineText.optionName(context, advice.best().kind()));
        ((TextView) root.findViewById(R.id.advisor_best_crops))
                .setText(EngineText.cropSequence(context, advice.best().plan()));
        ((TextView) root.findViewById(R.id.advisor_best_reason))
                .setText(EngineText.benefit(context, advice.benefit()));

        LinearLayout options = root.findViewById(R.id.advisor_options);
        options.removeAllViews();
        optionCards.clear();
        double currentScore = advice.current().scores().overall();
        for (RotationAdvisor.Option option : advice.options()) {
            View card = optionCard(options, option, option == advice.best(), currentScore);
            optionCards.put(option.kind(), card);
            ZameenUi.attachPressSpring(card);
            card.setOnClickListener(v -> showPreview(option.kind()));
            options.addView(card);
        }
        showPreview(previewKind == null ? advice.best().kind() : previewKind);

        MonthlyChartView chart = root.findViewById(R.id.advisor_chart);
        chart.setSeries(advice.profile().series(Metric.PRECIP_MM), advice.profile().series(Metric.ET0_MM));
        int month = LocalDate.now().getMonthValue() - 1;
        chart.selectMonth(month);
        showChartMonth(month);
        ((TextView) root.findViewById(R.id.advisor_year_rain)).setText(getString(
                string.advisor_year_rain, EngineText.number(advice.profile().annualRainMm(), 0)));

        LinearLayout alerts = root.findViewById(R.id.advisor_alerts);
        alerts.removeAllViews();
        List<ClimateAlert> found = advice.current().alerts();
        if (found.isEmpty()) {
            TextView none = new TextView(context);
            none.setTextAppearance(com.agroshift.core.ui.R.style.TextAppearance_AgroShift_Body);
            none.setText(string.advisor_no_alerts);
            alerts.addView(none);
        }
        for (ClimateAlert alert : found.subList(0, Math.min(MAX_ALERTS, found.size()))) {
            alerts.addView(alertCard(alerts, alert));
        }
        ((TextView) root.findViewById(R.id.advisor_source)).setText(getString(string.advisor_source,
                advice.profile().startYear(), advice.profile().endYear()));
    }

    private View optionCard(ViewGroup parent, RotationAdvisor.Option option, boolean best,
            double currentScore) {
        Context context = requireContext();
        View card = getLayoutInflater().inflate(R.layout.item_option, parent, false);
        ZameenUi.applySurface(card, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        RotationScores scores = option.scores();
        ((TextView) card.findViewById(R.id.option_name)).setText(EngineText.optionName(context, option.kind()));
        TextView score = card.findViewById(R.id.option_score);
        score.setText(EngineText.number(scores.overall(), 0));
        score.setContentDescription(getString(string.advisor_score, EngineText.number(scores.overall(), 0)));
        if (best) {
            score.setTextColor(ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_accent));
        }
        TextView fit = card.findViewById(R.id.option_fit);
        String label = EngineText.fit(context, option.fit());
        long delta = Math.round(scores.overall() - currentScore);
        if (option.kind() != RotationAdvisor.Kind.CURRENT) {
            String signed = (delta > 0 ? "+" : "") + EngineText.number(delta, 0);
            label += " · " + getString(string.option_delta, BidiFormatter.getInstance().unicodeWrap(signed));
        }
        fit.setText(label);
        fit.setTextColor(ContextCompat.getColor(context, switch (option.fit()) {
            case GOOD -> com.agroshift.core.ui.R.color.zameen_accent;
            case OKAY -> com.agroshift.core.ui.R.color.zameen_text_secondary;
            case RISKY -> com.agroshift.core.ui.R.color.zameen_error;
        }));
        ((TextView) card.findViewById(R.id.option_crops)).setText(EngineText.cropSequence(context, option.plan()));
        ((TextView) card.findViewById(R.id.option_water)).setText(getString(string.water_need,
                EngineText.number(option.plan().annualWaterNeedMm(), 0)));
        LinearLayout bars = card.findViewById(R.id.option_bars);
        addBar(bars, string.score_water, com.agroshift.core.ui.R.drawable.ic_ui_drop, scores.waterFit());
        addBar(bars, string.score_climate, com.agroshift.core.ui.R.drawable.ic_ui_heat, scores.climateRisk());
        addBar(bars, string.score_soil, com.agroshift.core.ui.R.drawable.ic_ui_leaf, scores.soilHealth());
        addBar(bars, string.score_erosion, com.agroshift.core.ui.R.drawable.ic_ui_shield, scores.erosion());
        addBar(bars, string.score_pest, com.agroshift.core.ui.R.drawable.ic_ui_bug, scores.pestBreak());
        addBar(bars, string.score_income, com.agroshift.core.ui.R.drawable.ic_ui_money, scores.income());
        return card;
    }

    private void addBar(LinearLayout parent, int label, int icon, double value) {
        View row = getLayoutInflater().inflate(R.layout.item_score_bar, parent, false);
        ((ImageView) row.findViewById(R.id.bar_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.bar_label)).setText(label);
        LinearProgressIndicator progress = row.findViewById(R.id.bar_progress);
        progress.setProgressCompat((int) Math.round(value), ZameenUi.motionEnabled(requireContext()));
        ((TextView) row.findViewById(R.id.bar_value)).setText(EngineText.number(value, 0));
        parent.addView(row);
    }

    private View alertCard(ViewGroup parent, ClimateAlert alert) {
        Context context = requireContext();
        View card = getLayoutInflater().inflate(R.layout.item_alert, parent, false);
        ZameenUi.applySurface(card, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ImageView icon = card.findViewById(R.id.alert_icon);
        icon.setImageResource(switch (alert.type()) {
            case HEAT -> com.agroshift.core.ui.R.drawable.ic_ui_heat;
            case DROUGHT -> com.agroshift.core.ui.R.drawable.ic_ui_sun;
            case WATER_DEFICIT -> com.agroshift.core.ui.R.drawable.ic_ui_drop;
            case FROST -> com.agroshift.core.ui.R.drawable.ic_ui_frost;
            case WATERLOGGING -> com.agroshift.core.ui.R.drawable.ic_ui_rain;
            case SOIL_DRYING -> com.agroshift.core.ui.R.drawable.ic_ui_field;
        });
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                alert.severity().compareTo(ClimateAlert.Severity.HIGH) >= 0
                        ? com.agroshift.core.ui.R.color.zameen_error
                        : com.agroshift.core.ui.R.color.zameen_accent)));
        ((TextView) card.findViewById(R.id.alert_title)).setText(EngineText.alertName(context, alert));
        TextView severity = card.findViewById(R.id.alert_severity);
        severity.setText(EngineText.severity(context, alert.severity()));
        severity.setTextColor(ContextCompat.getColor(context,
                alert.severity().compareTo(ClimateAlert.Severity.HIGH) >= 0
                        ? com.agroshift.core.ui.R.color.zameen_error
                        : com.agroshift.core.ui.R.color.zameen_accent));
        ((TextView) card.findViewById(R.id.alert_reason)).setText(EngineText.alertReason(context, alert));
        ((TextView) card.findViewById(R.id.alert_action)).setText(EngineText.alertAction(context, alert));
        return card;
    }

    @Override
    public void onDestroyView() {
        detachState();
        empty = null;
        loading = null;
        error = null;
        content = null;
        fieldChips = null;
        priorityChips = null;
        whatIfChips = null;
        scene = null;
        bestRing = null;
        shownAdvice = null;
        previewKind = null;
        optionCards.clear();
        lastState = null;
        super.onDestroyView();
    }
}
