package com.agroshift.feature.advisor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.text.BidiFormatter;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.agroshift.core.data.AnalysisRepository;
import com.agroshift.core.data.AnalysisState;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.FieldRepository;
import com.agroshift.core.domain.engine.ClimateProfile;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.domain.engine.PlanStudio;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.RotationScores;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.CropPhotos;
import com.agroshift.core.ui.PhotoView;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Every crop plan for one field, with filters, swipe cards, build-your-own and comparison. */
public final class PlanStudioFragment extends Fragment {
    private static final String PREFS = "agroshift_plan_favorites";
    private static final int LIST_PREVIEW = 8;
    private static final int MAX_COMPARE = 2;
    private static final double STEADY_DROP = 6;
    private static final float PHOTO_GROWTH = 0.6f;

    private final Observer<AnalysisState> stateObserver = this::render;
    private final List<FarmField> currentFields = new ArrayList<>();
    private final List<PlanStudio.Entry> entries = new ArrayList<>();
    private final Map<String, Integer> ranks = new HashMap<>();
    private final Map<String, View> rows = new HashMap<>();
    private final List<String> compareKeys = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private final Map<ClimateScenario, View> stressRows = new EnumMap<>(ClimateScenario.class);
    private final Map<PlanStudio.Lens, View> scoreRows = new EnumMap<>(PlanStudio.Lens.class);

    private FieldRepository fields;
    private AnalysisRepository analysis;
    private SharedPreferences prefs;
    private FarmField selected;
    private AnalysisState lastState;
    private LiveData<AnalysisState> activeState;
    private PlanStudio.Lens lens = PlanStudio.Lens.BEST;
    private String selectedKey;
    private boolean showAll;
    private double currentScore = Double.NaN;

    private ViewPager2 deck;
    private ViewPager2.OnPageChangeCallback pageCallback;
    private DeckAdapter adapter;
    private NestedScrollView scroll;
    private View waiting;
    private ChipGroup kharifChips;
    private ChipGroup rabiChips;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_plan_studio, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        ZameenUi.applySafeArea(view, true);
        AppServices services = (AppServices) requireContext().getApplicationContext();
        fields = services.fieldRepository();
        analysis = services.analysisRepository();
        prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        scroll = view.findViewById(R.id.studio_scroll);
        waiting = view.findViewById(R.id.studio_waiting);
        deck = view.findViewById(R.id.studio_deck);
        kharifChips = view.findViewById(R.id.studio_kharif);
        rabiChips = view.findViewById(R.id.studio_rabi);
        for (int id : new int[] {R.id.studio_detail, R.id.studio_stress_card, R.id.studio_build_card,
                R.id.studio_compare_card}) {
            ZameenUi.applySurface(view.findViewById(id), com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
        view.findViewById(R.id.studio_back).setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        ZameenUi.polishPrimary(view.findViewById(R.id.studio_build_open));

        adapter = new DeckAdapter();
        deck.setAdapter(adapter);
        pageCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == deck.getCurrentItem() && position < entries.size()) {
                    String key = entries.get(position).plan().key();
                    if (!key.equals(selectedKey)) {
                        selectedKey = key;
                        renderSelection();
                    }
                }
            }
        };
        deck.registerOnPageChangeCallback(pageCallback);

        addLensChips(view.findViewById(R.id.studio_lens));
        addCropChips(kharifChips, Crop.KHARIF);
        addCropChips(rabiChips, Crop.RABI);
        kharifChips.setOnCheckedStateChangeListener((group, ids) -> renderBuild());
        rabiChips.setOnCheckedStateChangeListener((group, ids) -> renderBuild());
        addMeasureRows(view.findViewById(R.id.studio_bars));
        addStressRows(view.findViewById(R.id.studio_stress));

        view.findViewById(R.id.studio_save).setOnClickListener(v -> toggleFavorite());
        view.findViewById(R.id.studio_compare).setOnClickListener(v -> toggleCompare());
        view.findViewById(R.id.studio_compare_clear).setOnClickListener(v -> {
            compareKeys.clear();
            renderButtons();
            renderCompare();
        });
        view.findViewById(R.id.studio_show_all).setOnClickListener(v -> {
            showAll = true;
            renderList();
        });
        view.findViewById(R.id.studio_build_open).setOnClickListener(v -> {
            String kharif = checkedSlug(kharifChips);
            String rabi = checkedSlug(rabiChips);
            if (kharif != null && rabi != null) {
                select(PlanStudio.build(kharif, rabi).key());
            }
        });

        fields.observeFields().observe(getViewLifecycleOwner(), list -> {
            currentFields.clear();
            currentFields.addAll(list);
            bindField(analysis.selectedFieldId().getValue());
        });
        analysis.selectedFieldId().observe(getViewLifecycleOwner(), this::bindField);
    }

    private void addLensChips(ChipGroup group) {
        for (PlanStudio.Lens item : PlanStudio.Lens.values()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_advisor_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(lensLabel(item));
            chip.setTag(item);
            chip.setChecked(item == lens);
            group.addView(chip);
        }
        group.setOnCheckedStateChangeListener((chips, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            PlanStudio.Lens next = (PlanStudio.Lens) chips.findViewById(ids.get(0)).getTag();
            if (next != lens) {
                lens = next;
                selectedKey = null;
                rebuild();
            }
        });
    }

    private void addCropChips(ChipGroup group, List<String> slugs) {
        for (String slug : slugs) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_advisor_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(EngineText.cropName(requireContext(), slug));
            chip.setTag(slug);
            group.addView(chip);
        }
    }

    private void addMeasureRows(LinearLayout parent) {
        for (PlanStudio.Lens item : PlanStudio.Lens.values()) {
            if (item != PlanStudio.Lens.BEST) {
                View row = barRow(parent, lensLabel(item), lensIcon(item));
                scoreRows.put(item, row);
                parent.addView(row);
            }
        }
    }

    private void addStressRows(LinearLayout parent) {
        for (ClimateScenario scenario : ClimateScenario.values()) {
            View row = barRow(parent, scenarioLabel(scenario), scenarioIcon(scenario));
            stressRows.put(scenario, row);
            parent.addView(row);
        }
    }

    private View barRow(ViewGroup parent, @StringRes int label, @DrawableRes int icon) {
        View row = getLayoutInflater().inflate(R.layout.item_score_bar, parent, false);
        ((ImageView) row.findViewById(R.id.bar_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.bar_label)).setText(label);
        return row;
    }

    private void setBar(View row, double value) {
        LinearProgressIndicator progress = row.findViewById(R.id.bar_progress);
        progress.setProgressCompat((int) Math.round(value), ZameenUi.motionEnabled(requireContext()));
        ((TextView) row.findViewById(R.id.bar_value)).setText(EngineText.number(value, 0));
    }

    @StringRes
    private static int lensLabel(PlanStudio.Lens item) {
        return switch (item) {
            case BEST -> string.studio_lens_best;
            case LESS_WATER -> string.score_water;
            case SAFE_CLIMATE -> string.score_climate;
            case SOIL -> string.score_soil;
            case EROSION -> string.score_erosion;
            case PEST -> string.score_pest;
            case INCOME -> string.score_income;
        };
    }

    @DrawableRes
    private static int lensIcon(PlanStudio.Lens item) {
        return switch (item) {
            case BEST -> com.agroshift.core.ui.R.drawable.ic_ui_star;
            case LESS_WATER -> com.agroshift.core.ui.R.drawable.ic_ui_drop;
            case SAFE_CLIMATE -> com.agroshift.core.ui.R.drawable.ic_ui_heat;
            case SOIL -> com.agroshift.core.ui.R.drawable.ic_ui_leaf;
            case EROSION -> com.agroshift.core.ui.R.drawable.ic_ui_shield;
            case PEST -> com.agroshift.core.ui.R.drawable.ic_ui_bug;
            case INCOME -> com.agroshift.core.ui.R.drawable.ic_ui_money;
        };
    }

    @StringRes
    private static int scenarioLabel(ClimateScenario scenario) {
        return switch (scenario) {
            case NORMAL -> string.whatif_normal;
            case HOTTER -> string.whatif_hot;
            case LESS_RAIN -> string.whatif_dry;
            case HOTTER_AND_DRIER -> string.whatif_both;
        };
    }

    @DrawableRes
    private static int scenarioIcon(ClimateScenario scenario) {
        return switch (scenario) {
            case NORMAL -> com.agroshift.core.ui.R.drawable.ic_ui_leaf;
            case HOTTER -> com.agroshift.core.ui.R.drawable.ic_ui_heat;
            case LESS_RAIN -> com.agroshift.core.ui.R.drawable.ic_ui_drop;
            case HOTTER_AND_DRIER -> com.agroshift.core.ui.R.drawable.ic_ui_alert;
        };
    }

    private void bindField(@Nullable String requestedId) {
        FarmField next = null;
        for (FarmField field : currentFields) {
            if (field.id().equals(requestedId)) {
                next = field;
            }
        }
        if (next == null && !currentFields.isEmpty()) {
            next = currentFields.get(0);
        }
        boolean changed = next == null || selected == null || !next.id().equals(selected.id());
        selected = next;
        if (next == null) {
            detachState();
            showWaiting(string.studio_unavailable, false);
            return;
        }
        ((TextView) requireView().findViewById(R.id.studio_field)).setText(getString(string.studio_for,
                BidiFormatter.getInstance().unicodeWrap(next.name())));
        if (changed) {
            selectedKey = null;
            compareKeys.clear();
            showAll = false;
            favorites.clear();
            favorites.addAll(prefs.getStringSet(next.id(), Set.of()));
        }
        LiveData<AnalysisState> live = analysis.observe(next.id());
        if (live != activeState) {
            detachState();
            activeState = live;
            live.observe(getViewLifecycleOwner(), stateObserver);
        } else if (lastState != null) {
            render(lastState);
        }
        analysis.load(next, false);
    }

    private void detachState() {
        if (activeState != null) {
            activeState.removeObserver(stateObserver);
            activeState = null;
        }
        lastState = null;
    }

    private void render(@Nullable AnalysisState state) {
        if (state == null || getView() == null) {
            return;
        }
        lastState = state;
        if (state.status() == AnalysisState.Status.READY && state.advice() != null) {
            waiting.setVisibility(View.GONE);
            scroll.setVisibility(View.VISIBLE);
            rebuild();
        } else if (state.status() == AnalysisState.Status.LOADING) {
            showWaiting(string.studio_waiting, true);
        } else {
            showWaiting(string.studio_unavailable, false);
        }
    }

    private void showWaiting(@StringRes int message, boolean busy) {
        scroll.setVisibility(View.GONE);
        waiting.setVisibility(View.VISIBLE);
        ((TextView) waiting.findViewById(R.id.studio_waiting_text)).setText(message);
        waiting.findViewById(R.id.studio_waiting_bar).setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void rebuild() {
        if (selected == null || lastState == null || lastState.advice() == null) {
            return;
        }
        ClimateProfile profile = lastState.advice().profile();
        List<PlanStudio.Entry> ranked = PlanStudio.rank(profile, selected.water(), selected.goal(), selected.soil(), lens);
        ranks.clear();
        currentScore = Double.NaN;
        for (int i = 0; i < ranked.size(); i++) {
            PlanStudio.Entry entry = ranked.get(i);
            ranks.put(entry.plan().key(), i + 1);
            if (PlanStudio.isSame(entry.plan(), selected.rotationKey())) {
                currentScore = entry.scores().overall();
            }
        }
        entries.clear();
        entries.addAll(PlanStudio.favoritesFirst(ranked, favorites));
        if (indexOf(selectedKey) < 0) {
            selectedKey = entries.get(0).plan().key();
        }
        adapter.notifyDataSetChanged();
        deck.setCurrentItem(indexOf(selectedKey), false);
        renderList();
        renderSelection();
        renderCompare();
        renderBuild();
    }

    private int indexOf(@Nullable String key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).plan().key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private PlanStudio.Entry entry(@Nullable String key) {
        int index = indexOf(key);
        return index < 0 ? null : entries.get(index);
    }

    private void select(String key) {
        int index = indexOf(key);
        if (index < 0) {
            return;
        }
        selectedKey = key;
        deck.setCurrentItem(index, ZameenUi.motionEnabled(requireContext()));
        renderSelection();
        if (ZameenUi.motionEnabled(requireContext())) {
            scroll.smoothScrollTo(0, 0);
        } else {
            scroll.scrollTo(0, 0);
        }
    }

    private void renderSelection() {
        PlanStudio.Entry chosen = entry(selectedKey);
        if (chosen == null || getView() == null) {
            return;
        }
        View root = requireView();
        RotationPlan plan = chosen.plan();
        ((PlanYearView) root.findViewById(R.id.studio_year)).setPlan(plan);
        ((TextView) root.findViewById(R.id.studio_water)).setText(getString(string.water_need,
                EngineText.number(plan.annualWaterNeedMm(), 0)));
        for (Map.Entry<PlanStudio.Lens, View> row : scoreRows.entrySet()) {
            setBar(row.getValue(), row.getKey().value(chosen.scores()));
        }
        Map<ClimateScenario, ClimateProfile> profiles = new EnumMap<>(ClimateScenario.class);
        for (Map.Entry<ClimateScenario, RotationAdvisor.Advice> outlook : lastState.outlooks().entrySet()) {
            profiles.put(outlook.getKey(), outlook.getValue().profile());
        }
        PlanStudio.Stress stress = PlanStudio.stress(plan, profiles, selected.water(), selected.goal(), selected.soil());
        for (Map.Entry<ClimateScenario, View> row : stressRows.entrySet()) {
            Double value = stress.scores().get(row.getKey());
            row.getValue().setVisibility(value == null ? View.GONE : View.VISIBLE);
            if (value != null) {
                setBar(row.getValue(), value);
            }
        }
        ((TextView) root.findViewById(R.id.studio_stress_summary)).setText(getString(
                stress.worstDrop() <= STEADY_DROP ? string.studio_stress_steady : string.studio_stress_sensitive,
                EngineText.number(stress.worstDrop(), 0)));
        renderButtons();
        for (Map.Entry<String, View> row : rows.entrySet()) {
            ZameenUi.applySurface(row.getValue(), row.getKey().equals(selectedKey)
                    ? com.agroshift.core.ui.R.color.zameen_surface_accent
                    : com.agroshift.core.ui.R.color.zameen_surface, com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
    }

    private void renderButtons() {
        View root = requireView();
        ((MaterialButton) root.findViewById(R.id.studio_save)).setText(
                favorites.contains(selectedKey) ? string.studio_saved : string.studio_save);
        ((MaterialButton) root.findViewById(R.id.studio_compare)).setText(
                compareKeys.contains(selectedKey) ? string.studio_comparing : string.studio_compare);
    }

    private void toggleFavorite() {
        if (selectedKey == null || selected == null) {
            return;
        }
        if (!favorites.remove(selectedKey)) {
            favorites.add(selectedKey);
        }
        prefs.edit().putStringSet(selected.id(), new HashSet<>(favorites)).apply();
        rebuild();
    }

    private void toggleCompare() {
        if (selectedKey == null) {
            return;
        }
        if (!compareKeys.remove(selectedKey)) {
            if (compareKeys.size() == MAX_COMPARE) {
                compareKeys.remove(0);
            }
            compareKeys.add(selectedKey);
        }
        renderButtons();
        renderCompare();
    }

    private void renderCompare() {
        View root = requireView();
        LinearLayout table = root.findViewById(R.id.studio_compare_table);
        table.removeAllViews();
        PlanStudio.Entry first = compareKeys.size() > 0 ? entry(compareKeys.get(0)) : null;
        PlanStudio.Entry second = compareKeys.size() > 1 ? entry(compareKeys.get(1)) : null;
        boolean ready = first != null && second != null;
        root.findViewById(R.id.studio_compare_hint).setVisibility(ready ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.studio_compare_clear).setVisibility(compareKeys.isEmpty() ? View.GONE : View.VISIBLE);
        if (!ready) {
            return;
        }
        View header = getLayoutInflater().inflate(R.layout.item_studio_compare, table, false);
        ((TextView) header.findViewById(R.id.compare_first)).setText(EngineText.cropSequence(requireContext(), first.plan()));
        ((TextView) header.findViewById(R.id.compare_second)).setText(EngineText.cropSequence(requireContext(), second.plan()));
        table.addView(header);
        for (PlanStudio.Lens measure : PlanStudio.Lens.values()) {
            View row = getLayoutInflater().inflate(R.layout.item_studio_compare, table, false);
            ((TextView) row.findViewById(R.id.compare_label)).setText(
                    measure == PlanStudio.Lens.BEST ? string.studio_overall : lensLabel(measure));
            PlanStudio.Winner winner = PlanStudio.winner(measure, first, second);
            setCompareValue(row.findViewById(R.id.compare_first), measure.value(first.scores()),
                    winner == PlanStudio.Winner.FIRST);
            setCompareValue(row.findViewById(R.id.compare_second), measure.value(second.scores()),
                    winner == PlanStudio.Winner.SECOND);
            table.addView(row);
        }
    }

    private void setCompareValue(TextView view, double value, boolean better) {
        String number = EngineText.number(value, 0);
        view.setText(better ? getString(string.studio_better, number) : number);
        view.setTextColor(ContextCompat.getColor(requireContext(), better
                ? com.agroshift.core.ui.R.color.zameen_accent : com.agroshift.core.ui.R.color.zameen_text));
    }

    @Nullable
    private static String checkedSlug(ChipGroup group) {
        int id = group.getCheckedChipId();
        return id == View.NO_ID ? null : (String) group.findViewById(id).getTag();
    }

    private void renderBuild() {
        View root = requireView();
        TextView result = root.findViewById(R.id.studio_build_result);
        View open = root.findViewById(R.id.studio_build_open);
        String kharif = checkedSlug(kharifChips);
        String rabi = checkedSlug(rabiChips);
        PlanStudio.Entry built = kharif == null || rabi == null ? null : entry(PlanStudio.build(kharif, rabi).key());
        open.setVisibility(built == null ? View.GONE : View.VISIBLE);
        result.setText(built == null ? getString(string.studio_build_hint) : getString(string.studio_build_result,
                EngineText.cropSequence(requireContext(), built.plan()),
                EngineText.number(built.scores().overall(), 0)));
    }

    private void renderList() {
        View root = requireView();
        LinearLayout list = root.findViewById(R.id.studio_list);
        list.removeAllViews();
        rows.clear();
        int count = showAll ? entries.size() : Math.min(LIST_PREVIEW, entries.size());
        for (int i = 0; i < count; i++) {
            PlanStudio.Entry item = entries.get(i);
            View row = getLayoutInflater().inflate(R.layout.item_plan_row, list, false);
            ((PhotoView) row.findViewById(R.id.row_photo)).setPhoto(photoFor(item.plan().uniqueCropSlugs().get(0)));
            ((TextView) row.findViewById(R.id.row_crops)).setText(EngineText.cropSequence(requireContext(), item.plan()));
            ((TextView) row.findViewById(R.id.row_meta)).setText(metaLine(item));
            ((TextView) row.findViewById(R.id.row_score)).setText(EngineText.number(lens.value(item.scores()), 0));
            ZameenUi.applySurface(row, item.plan().key().equals(selectedKey)
                    ? com.agroshift.core.ui.R.color.zameen_surface_accent
                    : com.agroshift.core.ui.R.color.zameen_surface, com.agroshift.core.ui.R.dimen.zameen_radius_card);
            ZameenUi.attachPressSpring(row);
            String key = item.plan().key();
            row.setOnClickListener(v -> select(key));
            rows.put(key, row);
            list.addView(row);
        }
        MaterialButton more = root.findViewById(R.id.studio_show_all);
        more.setVisibility(count < entries.size() ? View.VISIBLE : View.GONE);
        more.setText(getString(string.studio_show_all, EngineText.number(entries.size(), 0)));
    }

    /** Fit is always written out, with saved and current state in words rather than color alone. */
    private String metaLine(PlanStudio.Entry item) {
        StringBuilder line = new StringBuilder(EngineText.fit(requireContext(), item.fit()));
        if (PlanStudio.isSame(item.plan(), selected.rotationKey())) {
            line.append(" · ").append(getString(string.studio_current));
        } else if (!Double.isNaN(currentScore)) {
            long delta = Math.round(item.scores().overall() - currentScore);
            String signed = (delta > 0 ? "+" : "") + EngineText.number(delta, 0);
            line.append(" · ").append(getString(string.option_delta, BidiFormatter.getInstance().unicodeWrap(signed)));
        }
        if (favorites.contains(item.plan().key())) {
            line.append(" · ").append(getString(string.studio_saved));
        }
        return line.toString();
    }

    @DrawableRes
    private static int photoFor(String slug) {
        return CropPhotos.nearest(slug, PHOTO_GROWTH).photo();
    }

    @Override
    public void onDestroyView() {
        detachState();
        deck.unregisterOnPageChangeCallback(pageCallback);
        deck.setAdapter(null);
        deck = null;
        pageCallback = null;
        adapter = null;
        scroll = null;
        waiting = null;
        kharifChips = null;
        rabiChips = null;
        rows.clear();
        stressRows.clear();
        scoreRows.clear();
        super.onDestroyView();
    }

    private static final class CardHolder extends RecyclerView.ViewHolder {
        final PhotoView first;
        final PhotoView second;
        final TextView rank;
        final TextView crops;
        final TextView fit;
        final TextView score;

        CardHolder(@NonNull View view) {
            super(view);
            first = view.findViewById(R.id.card_photo_first);
            second = view.findViewById(R.id.card_photo_second);
            rank = view.findViewById(R.id.card_rank);
            crops = view.findViewById(R.id.card_crops);
            fit = view.findViewById(R.id.card_fit);
            score = view.findViewById(R.id.card_score);
        }
    }

    private final class DeckAdapter extends RecyclerView.Adapter<CardHolder> {
        @NonNull
        @Override
        public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View card = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plan_card, parent, false);
            ZameenUi.applySurface(card, com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
            return new CardHolder(card);
        }

        @Override
        public void onBindViewHolder(@NonNull CardHolder holder, int position) {
            PlanStudio.Entry item = entries.get(position);
            Context context = holder.itemView.getContext();
            List<String> crops = item.plan().uniqueCropSlugs();
            holder.first.setPhoto(photoFor(crops.get(0)));
            holder.second.setVisibility(crops.size() > 1 ? View.VISIBLE : View.GONE);
            if (crops.size() > 1) {
                holder.second.setPhoto(photoFor(crops.get(1)));
            }
            int rank = ranks.getOrDefault(item.plan().key(), position + 1);
            holder.rank.setText(context.getString(string.studio_rank, EngineText.number(rank, 0),
                    EngineText.number(entries.size(), 0)));
            holder.crops.setText(EngineText.cropSequence(context, item.plan()));
            holder.fit.setText(metaLine(item));
            RotationScores scores = item.scores();
            holder.score.setText(EngineText.number(lens.value(scores), 0));
            holder.score.setContentDescription(context.getString(string.advisor_score,
                    EngineText.number(lens.value(scores), 0)));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }
}
