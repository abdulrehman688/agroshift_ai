package com.agroshift.feature.farm;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.RecordRepository;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.farm.RecordStats;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.SplitBarView;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

/** One field: money, quick actions, water, task progress and filterable records. */
public final class FieldDetailFragment extends Fragment {
    public static final String ARG_FIELD_ID = "fieldId";

    private List<FarmRecord> records = List.of();
    private FarmRecord.Type filter;
    private RecordListBinder binder;
    private View empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_field_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        ZameenUi.applySafeArea(view, true);
        String fieldId = requireArguments().getString(ARG_FIELD_ID);
        AppServices services = (AppServices) requireContext().getApplicationContext();
        RecordRepository repository = services.recordRepository();
        AppNavigator navigator = (AppNavigator) requireActivity();
        RecyclerView list = view.findViewById(R.id.detail_records);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        View header = getLayoutInflater().inflate(R.layout.header_field_detail, list, false);
        TextView name = view.findViewById(R.id.detail_name);
        TextView crops = view.findViewById(R.id.detail_crops);
        TextView money = header.findViewById(R.id.detail_money);
        TextView moneyLabel = header.findViewById(R.id.detail_money_label);
        TextView spent = header.findViewById(R.id.detail_spent);
        TextView earned = header.findViewById(R.id.detail_earned);
        SplitBarView moneyBar = header.findViewById(R.id.detail_money_bar);
        View taskSection = header.findViewById(R.id.detail_task_section);
        TextView taskText = header.findViewById(R.id.detail_task_text);
        SplitBarView taskBar = header.findViewById(R.id.detail_task_bar);
        View water = header.findViewById(R.id.detail_water);
        empty = header.findViewById(R.id.detail_empty);
        moneyBar.setColors(com.agroshift.core.ui.R.color.zameen_accent, com.agroshift.core.ui.R.color.zameen_error);
        taskBar.setColors(com.agroshift.core.ui.R.color.zameen_accent, com.agroshift.core.ui.R.color.zameen_hairline);
        ZameenUi.applySurface(header.findViewById(R.id.detail_money_card),
                com.agroshift.core.ui.R.color.zameen_surface_accent, com.agroshift.core.ui.R.dimen.zameen_radius_card);
        for (View card : new View[] {water, taskSection}) {
            ZameenUi.applySurface(card, com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
        binder = new RecordListBinder(list, header, view, repository);

        services.fieldRepository().observeFields().observe(getViewLifecycleOwner(), fields -> {
            for (FarmField field : fields) {
                if (field.id().equals(fieldId)) {
                    name.setText(field.name());
                    crops.setText(EngineText.rotationName(requireContext(), field.rotationKey()));
                }
            }
        });
        repository.observe(fieldId).observe(getViewLifecycleOwner(), items -> {
            records = items;
            renderRecords();
            RecordStats.Summary summary = RecordStats.total(items);
            moneyLabel.setText(summary.profit() < 0 ? string.money_loss : string.money_profit);
            money.setText(EngineText.rupees(requireContext(), Math.abs(summary.profit())));
            earned.setText(getString(string.money_earned) + ": " + EngineText.rupees(requireContext(), summary.earned()));
            spent.setText(getString(string.money_spent) + ": " + EngineText.rupees(requireContext(), summary.spent()));
            moneyBar.setValues(summary.earned(), summary.spent());
            taskSection.setVisibility(summary.totalTasks() == 0 ? View.GONE : View.VISIBLE);
            taskText.setText(getString(string.tasks_progress, summary.doneTasks(), summary.totalTasks()));
            taskBar.setValues(summary.doneTasks(), summary.openTasks());
        });

        LinearLayout actions = header.findViewById(R.id.detail_actions);
        addAction(actions, string.record_task, com.agroshift.core.ui.R.drawable.ic_ui_task, fieldId, FarmRecord.Type.TASK);
        addAction(actions, string.record_cost, com.agroshift.core.ui.R.drawable.ic_ui_cost, fieldId, FarmRecord.Type.EXPENSE);
        addAction(actions, string.record_sale, com.agroshift.core.ui.R.drawable.ic_ui_sale, fieldId, FarmRecord.Type.SALE);
        addAction(actions, string.record_note, com.agroshift.core.ui.R.drawable.ic_ui_note, fieldId, FarmRecord.Type.NOTE);
        bindFilters(header.findViewById(R.id.detail_filters));
        ZameenUi.attachPressSpring(water);
        water.setOnClickListener(v -> navigator.openWater(fieldId));
        MaterialButton plan = view.findViewById(R.id.detail_plan);
        ZameenUi.polishPrimary(plan);
        plan.setOnClickListener(v -> navigator.openAdvisor(fieldId));
        view.findViewById(R.id.detail_back).setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
    }

    private void renderRecords() {
        List<FarmRecord> shown = RecordStats.ofType(records, filter);
        binder.submit(shown);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addAction(LinearLayout parent, int label, int icon, String fieldId, FarmRecord.Type type) {
        View tile = getLayoutInflater().inflate(R.layout.item_quick_action, parent, false);
        ((ImageView) tile.findViewById(R.id.quick_icon)).setImageResource(icon);
        ((TextView) tile.findViewById(R.id.quick_label)).setText(label);
        ZameenUi.applySurface(tile, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tile.getLayoutParams();
        if (parent.getChildCount() > 0) {
            params.setMarginStart(Math.round(6 * getResources().getDisplayMetrics().density));
        }
        ZameenUi.attachPressSpring(tile);
        tile.setContentDescription(getString(string.add_record, getString(label)));
        tile.setOnClickListener(v -> AddRecordSheet.create(fieldId, type).show(getChildFragmentManager(), "add_record"));
        parent.addView(tile);
    }

    private void bindFilters(ChipGroup group) {
        addFilter(group, string.filter_all, null);
        addFilter(group, string.record_task, FarmRecord.Type.TASK);
        addFilter(group, string.record_cost, FarmRecord.Type.EXPENSE);
        addFilter(group, string.record_sale, FarmRecord.Type.SALE);
        addFilter(group, string.record_note, FarmRecord.Type.NOTE);
        group.setOnCheckedStateChangeListener((chips, ids) -> {
            if (!ids.isEmpty()) {
                filter = (FarmRecord.Type) chips.findViewById(ids.get(0)).getTag();
                renderRecords();
            }
        });
    }

    private void addFilter(ChipGroup group, int label, @Nullable FarmRecord.Type type) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_choice_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(type);
        chip.setChecked(type == null);
        group.addView(chip);
    }

    @Override
    public void onDestroyView() {
        binder = null;
        empty = null;
        super.onDestroyView();
    }
}
