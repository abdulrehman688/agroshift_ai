package com.agroshift.feature.farm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.RecordRepository;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;

/** One field: money summary, quick add buttons and its records. */
public final class FieldDetailFragment extends Fragment {
    public static final String ARG_FIELD_ID = "fieldId";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_field_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        String fieldId = requireArguments().getString(ARG_FIELD_ID);
        AppServices services = (AppServices) requireContext().getApplicationContext();
        RecordRepository records = services.recordRepository();
        AppNavigator navigator = (AppNavigator) requireActivity();
        TextView name = view.findViewById(R.id.detail_name);
        TextView crops = view.findViewById(R.id.detail_crops);
        TextView money = view.findViewById(R.id.detail_money);
        TextView moneyLabel = view.findViewById(R.id.detail_money_label);
        TextView spent = view.findViewById(R.id.detail_spent);
        TextView earned = view.findViewById(R.id.detail_earned);
        View empty = view.findViewById(R.id.detail_empty);
        ZameenUi.applySurface(view.findViewById(R.id.detail_money_card),
                com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);

        services.fieldRepository().observeFields().observe(getViewLifecycleOwner(), fields -> {
            for (FarmField field : fields) {
                if (field.id().equals(fieldId)) {
                    name.setText(field.name());
                    crops.setText(EngineText.rotationName(requireContext(), field.rotationKey()));
                }
            }
        });
        RecyclerView list = view.findViewById(R.id.detail_records);
        RecordListBinder binder = new RecordListBinder(list, view, records);
        records.observe(fieldId).observe(getViewLifecycleOwner(), items -> {
            binder.submit(items);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            FarmRecord.Money totals = FarmRecord.totals(items);
            boolean loss = totals.profit() < 0;
            moneyLabel.setText(loss ? string.money_loss : string.money_profit);
            money.setText(EngineText.rupees(requireContext(), Math.abs(totals.profit())));
            spent.setText(getString(string.money_spent) + ": "
                    + EngineText.rupees(requireContext(), totals.spent()));
            earned.setText(getString(string.money_earned) + ": "
                    + EngineText.rupees(requireContext(), totals.earned()));
        });

        bindAdd(view, R.id.detail_add_task, fieldId, FarmRecord.Type.TASK);
        bindAdd(view, R.id.detail_add_cost, fieldId, FarmRecord.Type.EXPENSE);
        bindAdd(view, R.id.detail_add_sale, fieldId, FarmRecord.Type.SALE);
        bindAdd(view, R.id.detail_add_note, fieldId, FarmRecord.Type.NOTE);
        view.findViewById(R.id.detail_water).setOnClickListener(v -> navigator.openWater(fieldId));
        MaterialButton plan = view.findViewById(R.id.detail_plan);
        ZameenUi.polishPrimary(plan);
        plan.setOnClickListener(v -> navigator.openAdvisor(fieldId));
        view.findViewById(R.id.detail_back).setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
    }

    private void bindAdd(View root, int id, String fieldId, FarmRecord.Type type) {
        root.findViewById(id).setOnClickListener(v ->
                AddRecordSheet.create(fieldId, type).show(getChildFragmentManager(), "add_record"));
    }
}
