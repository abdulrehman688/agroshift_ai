package com.agroshift.feature.farm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.FieldRepository;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.farm.RecordStats;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;

/** All farms with their crop progress, tasks and money at a glance. */
public final class FarmFragment extends Fragment {
    private List<FarmField> fields = List.of();
    private List<FarmRecord> records = List.of();
    private FieldListBinder binder;
    private View empty;
    private TextView acres;
    private TextView tasks;
    private TextView profit;
    private TextView subtitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_farm, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        ZameenUi.applySafeArea(view, false);
        AppServices services = (AppServices) requireContext().getApplicationContext();
        FieldRepository repository = services.fieldRepository();
        AppNavigator navigator = (AppNavigator) requireActivity();
        RecyclerView list = view.findViewById(R.id.farm_fields);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        View header = getLayoutInflater().inflate(R.layout.header_farm, list, false);
        empty = header.findViewById(R.id.farm_empty);
        acres = header.findViewById(R.id.farm_stat_acres);
        tasks = header.findViewById(R.id.farm_stat_tasks);
        profit = header.findViewById(R.id.farm_stat_profit);
        subtitle = header.findViewById(R.id.farm_subtitle);
        for (int id : new int[] {R.id.farm_card_acres, R.id.farm_card_tasks, R.id.farm_card_profit}) {
            ZameenUi.applySurface(header.findViewById(id), com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
        binder = new FieldListBinder(list, header, view, repository, field -> navigator.openField(field.id()));
        repository.observeFields().observe(getViewLifecycleOwner(), next -> {
            fields = next;
            render();
        });
        services.recordRepository().observeAll().observe(getViewLifecycleOwner(), next -> {
            records = next;
            render();
        });
        showSwipeHintOnce(view);
        MaterialButton add = header.findViewById(R.id.farm_add);
        ZameenUi.polishPrimary(add);
        add.setOnClickListener(v -> navigator.openFieldSetup());
    }

    private void render() {
        binder.submit(fields, RecordStats.byField(records));
        empty.setVisibility(fields.isEmpty() ? View.VISIBLE : View.GONE);
        double area = 0;
        for (FarmField field : fields) {
            area += field.areaAcres();
        }
        RecordStats.Summary total = RecordStats.total(records);
        acres.setText(EngineText.number(area, 1));
        tasks.setText(EngineText.number(total.openTasks(), 0));
        profit.setText(EngineText.rupees(requireContext(), total.profit()));
        subtitle.setText(getResources().getQuantityString(com.agroshift.core.i18n.R.plurals.farm_count,
                fields.size(), fields.size()));
    }

    private void showSwipeHintOnce(View view) {
        SharedPreferences prefs = requireContext().getSharedPreferences("agroshift_hints",
                Context.MODE_PRIVATE);
        if (!prefs.getBoolean("swipe_field", false)) {
            prefs.edit().putBoolean("swipe_field", true).apply();
            Snackbar.make(view, string.swipe_hint, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        binder = null;
        empty = null;
        acres = null;
        tasks = null;
        profit = null;
        subtitle = null;
        super.onDestroyView();
    }
}
