package com.agroshift.feature.home;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.OnlineServices;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.water.FieldWater;
import com.agroshift.core.domain.water.IrrigationAdvisor;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.i18n.WaterText;
import com.agroshift.core.ui.ZameenUi;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Swipeable cards for every farm, each with its own weather and water answer. */
final class FarmStrip {
    private final Fragment fragment;
    private final LinearLayout container;
    private final AppServices services;
    private final Consumer<FarmField> onSelect;
    private final Runnable onAdd;
    private final List<Row> rows = new ArrayList<>();
    private List<FarmField> shown = List.of();

    FarmStrip(Fragment fragment, LinearLayout container, AppServices services, Consumer<FarmField> onSelect,
            Runnable onAdd) {
        this.fragment = fragment;
        this.container = container;
        this.services = services;
        this.onSelect = onSelect;
        this.onAdd = onAdd;
    }

    void submit(List<FarmField> fields, String selectedId) {
        if (!fields.equals(shown)) {
            rebuild(fields);
        }
        for (Row row : rows) {
            boolean selected = row.field.id().equals(selectedId);
            row.selected.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            row.view.setSelected(selected);
            ZameenUi.applySurface(row.view, selected ? com.agroshift.core.ui.R.color.zameen_surface_accent
                    : com.agroshift.core.ui.R.color.zameen_surface, com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
    }

    private void rebuild(List<FarmField> fields) {
        clear();
        shown = List.copyOf(fields);
        LayoutInflater inflater = fragment.getLayoutInflater();
        LifecycleOwner owner = fragment.getViewLifecycleOwner();
        for (FarmField field : fields) {
            View view = inflater.inflate(R.layout.item_home_farm, container, false);
            Row row = new Row(field, view);
            row.name.setText(field.name());
            row.crops.setText(fragment.getString(string.farm_summary,
                    EngineText.rotationName(fragment.requireContext(), field.rotationKey()),
                    EngineText.number(field.areaAcres(), 1)));
            ZameenUi.attachPressSpring(view);
            view.setOnClickListener(v -> onSelect.accept(field));
            container.addView(view);
            rows.add(row);
            if (field.hasLocation()) {
                row.weatherData = services.onlineServices().observeWeather(field);
                row.weatherData.observe(owner, row.weatherObserver);
                row.waterData = services.waterRepository().observe(field.id());
                row.waterData.observe(owner, row.waterObserver);
            } else {
                row.weather.setText(string.farm_no_location);
                row.water.setVisibility(View.GONE);
            }
        }
        View add = inflater.inflate(R.layout.item_home_farm, container, false);
        ((TextView) add.findViewById(R.id.farm_name)).setText(string.farm_add);
        ((TextView) add.findViewById(R.id.farm_crops)).setText(string.farm_add_body);
        ZameenUi.applySurface(add, com.agroshift.core.ui.R.color.zameen_background,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        add.setBackground(strokedBackground());
        ZameenUi.attachPressSpring(add);
        add.setOnClickListener(v -> onAdd.run());
        container.addView(add);
    }

    private android.graphics.drawable.Drawable strokedBackground() {
        com.google.android.material.shape.MaterialShapeDrawable drawable =
                new com.google.android.material.shape.MaterialShapeDrawable(ZameenUi.smoothShape(
                        fragment.requireContext(), com.agroshift.core.ui.R.dimen.zameen_radius_card));
        drawable.setFillColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        drawable.setStroke(1.5f * fragment.getResources().getDisplayMetrics().density,
                androidx.core.content.ContextCompat.getColor(fragment.requireContext(),
                        com.agroshift.core.ui.R.color.zameen_hairline));
        return drawable;
    }

    void clear() {
        for (Row row : rows) {
            if (row.weatherData != null) {
                row.weatherData.removeObserver(row.weatherObserver);
            }
            if (row.waterData != null) {
                row.waterData.removeObserver(row.waterObserver);
            }
        }
        rows.clear();
        container.removeAllViews();
        shown = List.of();
    }

    private final class Row {
        final FarmField field;
        final View view;
        final TextView selected;
        final TextView name;
        final TextView crops;
        final TextView weather;
        final TextView water;
        OnlineServices.Weather latest;
        FieldWater fieldWater;
        LiveData<OnlineServices.Weather> weatherData;
        LiveData<FieldWater> waterData;
        final Observer<OnlineServices.Weather> weatherObserver = next -> {
            latest = next;
            render();
        };
        final Observer<FieldWater> waterObserver = next -> {
            fieldWater = next;
            render();
        };

        Row(FarmField field, View view) {
            this.field = field;
            this.view = view;
            selected = view.findViewById(R.id.farm_selected);
            name = view.findViewById(R.id.farm_name);
            crops = view.findViewById(R.id.farm_crops);
            weather = view.findViewById(R.id.farm_weather);
            water = view.findViewById(R.id.farm_water);
        }

        void render() {
            if (!fragment.isAdded()) {
                return;
            }
            if (latest != null) {
                weather.setText(fragment.getString(string.weather_now, EngineText.number(latest.currentC(), 0)));
            }
            if (fieldWater != null) {
                water.setText(WaterText.title(fragment.requireContext(), IrrigationAdvisor.decide(
                        latest == null ? List.of() : latest.days(), fieldWater.lastWatered(), LocalDate.now(),
                        field.water(), field.soil())));
            }
        }
    }
}
