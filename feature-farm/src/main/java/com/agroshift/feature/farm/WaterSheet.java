package com.agroshift.feature.farm;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.WaterRepository;
import com.agroshift.core.domain.water.CanalTurn;
import com.agroshift.core.domain.water.FieldWater;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.i18n.WaterText;
import com.agroshift.core.sync.CanalAlarms;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;

/** Watering date and weekly canal turn. Changes save immediately, so nothing can be lost. */
public final class WaterSheet extends BottomSheetDialogFragment {
    private static final String ARG_FIELD = "fieldId";
    private static final int[] DURATIONS = {30, 60, 90, 120, 180, 240};
    private static final int[] REMINDERS = {15, 30, 60, 120};
    private static final int DEFAULT_START = 6 * 60;

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> renderPermissions());

    private WaterRepository repository;
    private String fieldId;
    private FieldWater current;
    private boolean binding;
    private int startMinute = DEFAULT_START;
    private MaterialSwitch enabled;
    private View form;
    private ChipGroup days;
    private ChipGroup durations;
    private ChipGroup reminds;
    private MaterialButton time;
    private TextView summary;
    private TextView lastValue;
    private View allowAlerts;
    private View allowExact;

    public static WaterSheet create(String fieldId) {
        WaterSheet sheet = new WaterSheet();
        Bundle args = new Bundle();
        args.putString(ARG_FIELD, fieldId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_water, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        fieldId = requireArguments().getString(ARG_FIELD);
        repository = ((AppServices) requireContext().getApplicationContext()).waterRepository();
        enabled = view.findViewById(R.id.turn_enabled);
        form = view.findViewById(R.id.turn_form);
        days = view.findViewById(R.id.turn_days);
        durations = view.findViewById(R.id.turn_durations);
        reminds = view.findViewById(R.id.turn_reminds);
        time = view.findViewById(R.id.turn_time);
        summary = view.findViewById(R.id.turn_summary);
        lastValue = view.findViewById(R.id.water_last_value);
        allowAlerts = view.findViewById(R.id.water_allow_alerts);
        allowExact = view.findViewById(R.id.water_allow_exact);

        binding = true;
        for (DayOfWeek day : DayOfWeek.values()) {
            addChip(days, day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), day.getValue(),
                    day == LocalDate.now().getDayOfWeek());
        }
        for (int minutes : DURATIONS) {
            addChip(durations, getString(string.minutes_short, minutes), minutes, minutes == 60);
        }
        for (int minutes : REMINDERS) {
            addChip(reminds, getString(string.minutes_short, minutes), minutes, minutes == 30);
        }
        binding = false;
        renderTime();

        view.findViewById(R.id.water_today).setOnClickListener(
                v -> repository.setLastWatered(fieldId, LocalDate.now()));
        view.findViewById(R.id.water_pick).setOnClickListener(v -> pickDate());
        time.setOnClickListener(v -> pickTime());
        enabled.setOnCheckedChangeListener((button, checked) -> {
            form.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!binding) {
                saveTurn();
                if (checked) {
                    requestNotifications();
                }
            }
            renderPermissions();
        });
        ChipGroup.OnCheckedStateChangeListener save = (group, ids) -> {
            if (!binding) {
                saveTurn();
            }
        };
        days.setOnCheckedStateChangeListener(save);
        durations.setOnCheckedStateChangeListener(save);
        reminds.setOnCheckedStateChangeListener(save);
        allowAlerts.setOnClickListener(v -> requestNotifications());
        allowExact.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + requireContext().getPackageName())));
            }
        });
        repository.observe(fieldId).observe(getViewLifecycleOwner(), this::render);
    }

    private void render(FieldWater water) {
        boolean first = current == null;
        current = water;
        lastValue.setText(water.lastWatered() == null ? getString(string.water_last_none)
                : water.lastWatered().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)));
        if (first) {
            binding = true;
            enabled.setChecked(water.turn() != null);
            form.setVisibility(water.turn() != null ? View.VISIBLE : View.GONE);
            if (water.turn() != null) {
                startMinute = water.turn().startMinute();
                check(days, water.turn().day().getValue());
                check(durations, water.turn().durationMinutes());
                check(reminds, water.turn().remindMinutes());
                renderTime();
            }
            binding = false;
        }
        summary.setText(water.turn() == null ? "" : WaterText.nextTurn(requireContext(), water.turn()));
        renderPermissions();
    }

    private void saveTurn() {
        repository.setTurn(fieldId, enabled.isChecked() ? new CanalTurn(DayOfWeek.of(value(days)), startMinute,
                value(durations), value(reminds)) : null);
    }

    private void pickDate() {
        LocalDate shown = current != null && current.lastWatered() != null ? current.lastWatered() : LocalDate.now();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), (picker, year, month, day) ->
                repository.setLastWatered(fieldId, LocalDate.of(year, month + 1, day)),
                shown.getYear(), shown.getMonthValue() - 1, shown.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void pickTime() {
        new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
            startMinute = hour * 60 + minute;
            renderTime();
            saveTurn();
        }, startMinute / 60, startMinute % 60, DateFormat.is24HourFormat(requireContext())).show();
    }

    private void renderTime() {
        time.setText(getString(string.turn_time, WaterText.time(LocalTime.ofSecondOfDay(startMinute * 60L))));
    }

    private void requestNotifications() {
        if (needsNotificationPermission()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void renderPermissions() {
        if (getView() == null) {
            return;
        }
        boolean on = enabled.isChecked();
        allowAlerts.setVisibility(on && needsNotificationPermission() ? View.VISIBLE : View.GONE);
        allowExact.setVisibility(on && !CanalAlarms.canUseExactTime(requireContext()) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        renderPermissions();
        if (current != null && current.turn() != null) {
            CanalAlarms.sync(requireContext(), current);
        }
    }

    private void addChip(ChipGroup group, String label, int value, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_choice_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(value);
        chip.setChecked(checked);
        group.addView(chip);
    }

    private static void check(ChipGroup group, int value) {
        View chip = group.findViewWithTag(value);
        if (chip != null) {
            group.check(chip.getId());
        }
    }

    private static int value(ChipGroup group) {
        return (Integer) group.findViewById(group.getCheckedChipId()).getTag();
    }

    @Override
    public void onDestroyView() {
        enabled = null;
        form = null;
        days = null;
        durations = null;
        reminds = null;
        time = null;
        summary = null;
        lastValue = null;
        allowAlerts = null;
        allowExact = null;
        current = null;
        super.onDestroyView();
    }
}
