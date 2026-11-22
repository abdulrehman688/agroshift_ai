package com.agroshift.feature.farm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.Result;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.ZameenMap;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.List;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

/** Marks a field on the map and collects the inputs needed for rotation advice. */
public final class FieldSetupFragment extends Fragment {
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final GeoPoint PAKISTAN = new GeoPoint(30.6, 71.5);
    private static final double OVERVIEW_ZOOM = 6.0;
    private static final double FIELD_ZOOM = 15.0;
    private static final long LOCATION_TIMEOUT_MILLIS = 15_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (hasLocationPermission()) {
                    findLocation();
                } else {
                    message(string.setup_location_denied);
                }
            });

    private MapView map;
    private Marker marker;
    private TextView hint;
    private TextInputEditText name;
    private TextInputEditText area;
    private boolean satellite;
    private OnBackPressedCallback backGuard;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final Runnable locationTimeout = () -> {
        stopLocation();
        if (getView() != null) {
            message(string.setup_location_unavailable);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_field_setup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        map = view.findViewById(R.id.setup_map);
        hint = view.findViewById(R.id.setup_hint);
        name = view.findViewById(R.id.setup_name);
        area = view.findViewById(R.id.setup_area);
        ZameenMap.prepare(map, true);
        map.getController().setZoom(OVERVIEW_ZOOM);
        map.getController().setCenter(PAKISTAN);
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                placeMarker(point, false);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                placeMarker(point, false);
                return true;
            }
        }));
        if (state != null && state.containsKey(KEY_LAT)) {
            placeMarker(new GeoPoint(state.getDouble(KEY_LAT), state.getDouble(KEY_LON)), true);
        }

        View top = view.findViewById(R.id.setup_top);
        ViewCompat.setOnApplyWindowInsetsListener(top, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPaddingRelative(v.getPaddingStart(), safe.top + dp(8), v.getPaddingEnd(), 0);
            return insets;
        });
        View form = view.findViewById(R.id.setup_form);
        int formBottom = form.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(form, (v, insets) -> {
            Insets bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    formBottom + bottom.bottom);
            return insets;
        });

        ChipGroup summer = view.findViewById(R.id.setup_summer);
        ChipGroup winter = view.findViewById(R.id.setup_winter);
        View winterLabel = view.findViewById(R.id.setup_winter_label);
        for (String crop : Crop.KHARIF) {
            addCropChip(summer, crop, "cotton".equals(crop));
        }
        addCropChip(summer, Crop.YEAR_ROUND, false);
        for (String crop : Crop.RABI) {
            addCropChip(winter, crop, "wheat".equals(crop));
        }
        summer.setOnCheckedStateChangeListener((group, ids) -> {
            boolean yearRound = Crop.YEAR_ROUND.equals(selected(group));
            winter.setVisibility(yearRound ? View.GONE : View.VISIBLE);
            winterLabel.setVisibility(yearRound ? View.GONE : View.VISIBLE);
        });
        ChipGroup water = view.findViewById(R.id.setup_water);
        addChip(water, string.water_irrigated, WaterAccess.IRRIGATED.name(), true);
        addChip(water, string.water_limited, WaterAccess.LIMITED.name(), false);
        addChip(water, string.water_rainfed, WaterAccess.RAINFED.name(), false);
        ChipGroup soil = view.findViewById(R.id.setup_soil);
        addChip(soil, string.soil_unknown, SoilType.UNKNOWN.name(), true);
        addChip(soil, string.soil_sandy, SoilType.SANDY.name(), false);
        addChip(soil, string.soil_loam, SoilType.LOAM.name(), false);
        addChip(soil, string.soil_clay, SoilType.CLAY.name(), false);
        addChip(soil, string.soil_silt, SoilType.SILT.name(), false);
        ChipGroup priorities = view.findViewById(R.id.setup_priorities);
        addChip(priorities, string.priority_water, FarmGoal.Priority.WATER.name(), false);
        addChip(priorities, string.priority_soil, FarmGoal.Priority.SOIL.name(), false);
        addChip(priorities, string.priority_income, FarmGoal.Priority.INCOME.name(), false);
        addChip(priorities, string.priority_erosion, FarmGoal.Priority.EROSION.name(), false);
        addChip(priorities, string.priority_pest, FarmGoal.Priority.PEST.name(), false);
        bindMapTools(view);

        TextInputLayout nameLayout = view.findViewById(R.id.setup_name_layout);
        TextInputLayout areaLayout = view.findViewById(R.id.setup_area_layout);
        clearErrorOnEdit(name, nameLayout);
        clearErrorOnEdit(area, areaLayout);

        backGuard = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscard();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backGuard);

        view.findViewById(R.id.setup_back).setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.setup_locate).setOnClickListener(v -> {
            if (hasLocationPermission()) {
                findLocation();
            } else {
                permissions.launch(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });

        MaterialButton save = view.findViewById(R.id.setup_save);
        ZameenUi.polishPrimary(save);
        save.setOnClickListener(v -> save(nameLayout, areaLayout, summer, winter, water, soil, priorities));
        updateDirtyState();
    }

    private void bindMapTools(View view) {
        MaterialButton layer = view.findViewById(R.id.setup_layer);
        layer.setOnClickListener(v -> {
            satellite = !satellite;
            ZameenMap.useSatellite(map, satellite);
            layer.setText(satellite ? string.setup_street : string.setup_satellite);
        });
        view.findViewById(R.id.setup_quick_faisalabad).setOnClickListener(
                v -> placeMarker(new GeoPoint(31.30, 73.00), true));
        view.findViewById(R.id.setup_quick_hyderabad).setOnClickListener(
                v -> placeMarker(new GeoPoint(25.40, 68.40), true));
        TextInputEditText search = view.findViewById(R.id.setup_search);
        search.setOnEditorActionListener((input, action, event) -> {
            String query = text(search).strip();
            if (query.length() < 2) {
                return true;
            }
            hideKeyboard(search);
            ((AppServices) requireContext().getApplicationContext()).onlineServices().searchPlaces(query,
                    places -> {
                        if (getView() == null) {
                            return;
                        }
                        if (places.isEmpty()) {
                            message(string.setup_search_none);
                        } else {
                            placeMarker(new GeoPoint(places.get(0).latitude(), places.get(0).longitude()), true);
                        }
                    }, () -> message(string.setup_search_failed));
            return true;
        });
    }

    private void hideKeyboard(View input) {
        ((InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void addCropChip(ChipGroup group, String crop, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_choice_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setText(EngineText.cropName(requireContext(), crop));
        chip.setTag(crop);
        chip.setChecked(checked);
        group.addView(chip);
    }

    private static FarmGoal selectedGoal(ChipGroup group) {
        List<FarmGoal.Priority> priorities = new ArrayList<>();
        for (int id : group.getCheckedChipIds()) {
            priorities.add(FarmGoal.Priority.valueOf((String) group.findViewById(id).getTag()));
        }
        return FarmGoal.of(priorities);
    }

    private void save(TextInputLayout nameLayout, TextInputLayout areaLayout, ChipGroup summer,
            ChipGroup winter, ChipGroup water, ChipGroup soil, ChipGroup priorities) {
        nameLayout.setError(null);
        areaLayout.setError(null);
        if (marker == null) {
            hint.setText(string.error_field_location);
            message(string.error_field_location);
            return;
        }
        GeoPoint point = marker.getPosition();
        AppServices services = (AppServices) requireContext().getApplicationContext();
        Result<FarmField> result = services.fieldRepository().add(text(name), parseArea(text(area)),
                point.getLatitude(), point.getLongitude(),
                RotationPlan.customKey(selected(summer), Crop.YEAR_ROUND.equals(selected(summer))
                        ? Crop.YEAR_ROUND : selected(winter)),
                WaterAccess.parse(selected(water)), selectedGoal(priorities),
                SoilType.parse(selected(soil)));
        if (result.isSuccess()) {
            backGuard.setEnabled(false);
            services.analysisRepository().load(result.value(), false);
            ((AppNavigator) requireActivity()).openAdvisor(result.value().id());
        } else if (FarmField.ERROR_NAME.equals(result.errorCode())) {
            nameLayout.setError(getString(string.error_field_name));
            name.requestFocus();
        } else if (FarmField.ERROR_AREA.equals(result.errorCode())) {
            areaLayout.setError(getString(string.error_field_area));
            area.requestFocus();
        } else {
            hint.setText(string.error_field_location);
            message(string.error_field_location);
        }
    }

    private void placeMarker(GeoPoint point, boolean moveCamera) {
        if (marker == null) {
            marker = ZameenMap.pin(map, point);
            map.getOverlays().add(marker);
        } else {
            marker.setPosition(point);
        }
        boolean supported = FarmField.isSupportedLocation(point.getLatitude(), point.getLongitude());
        hint.setText(supported
                ? getString(string.setup_location_marked)
                : getString(string.error_field_location));
        if (moveCamera) {
            map.getController().animateTo(point, FIELD_ZOOM, 500L);
        }
        map.invalidate();
        updateDirtyState();
    }

    private boolean hasLocationPermission() {
        Context context = requireContext();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void findLocation() {
        stopLocation();
        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = locationManager.getProviders(true);
        Location best = null;
        for (String provider : providers) {
            Location last = locationManager.getLastKnownLocation(provider);
            if (last != null && (best == null || last.getTime() > best.getTime())) {
                best = last;
            }
        }
        if (best != null) {
            placeMarker(new GeoPoint(best.getLatitude(), best.getLongitude()), true);
        }
        String provider = providers.contains(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER
                : providers.contains(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
        if (provider == null) {
            if (best == null) {
                message(string.setup_location_unavailable);
            }
            return;
        }
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                stopLocation();
                if (getView() != null) {
                    placeMarker(new GeoPoint(location.getLatitude(), location.getLongitude()), true);
                }
            }

            @Override
            public void onProviderEnabled(@NonNull String ignored) {
            }

            @Override
            public void onProviderDisabled(@NonNull String ignored) {
            }

            @Override
            public void onStatusChanged(String ignored, int status, Bundle extras) {
            }
        };
        locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener, Looper.getMainLooper());
        handler.postDelayed(locationTimeout, LOCATION_TIMEOUT_MILLIS);
    }

    private void stopLocation() {
        handler.removeCallbacks(locationTimeout);
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        locationListener = null;
    }

    private void addChip(ChipGroup group, int label, String tag, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_choice_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(tag);
        chip.setChecked(checked);
        group.addView(chip);
    }

    private static String selected(ChipGroup group) {
        View chip = group.findViewById(group.getCheckedChipId());
        return chip == null ? null : (String) chip.getTag();
    }

    private void clearErrorOnEdit(TextInputEditText input, TextInputLayout layout) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateDirtyState();
            }
        });
    }

    private void updateDirtyState() {
        if (backGuard != null) {
            backGuard.setEnabled(marker != null || !text(name).isBlank() || !text(area).isBlank());
        }
    }

    private void confirmDiscard() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(string.discard_title)
                .setMessage(string.discard_body)
                .setNegativeButton(string.action_keep_editing, null)
                .setPositiveButton(string.action_discard, (dialog, which) -> {
                    backGuard.setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                })
                .show();
    }

    private void message(int text) {
        View view = getView();
        if (view != null) {
            Snackbar.make(view, text, Snackbar.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String text(TextInputEditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString();
    }

    public static double parseArea(String raw) {
        StringBuilder normalized = new StringBuilder();
        for (char c : raw.strip().toCharArray()) {
            int digit = Character.digit(c, 10);
            if (digit >= 0) {
                normalized.append(digit);
            } else if (c == '.' || c == '٫') {
                normalized.append('.');
            } else {
                return Double.NaN;
            }
        }
        try {
            return Double.parseDouble(normalized.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        stopLocation();
        map.onPause();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (marker != null) {
            out.putDouble(KEY_LAT, marker.getPosition().getLatitude());
            out.putDouble(KEY_LON, marker.getPosition().getLongitude());
        }
    }

    @Override
    public void onDestroyView() {
        stopLocation();
        map.onDetach();
        map = null;
        marker = null;
        hint = null;
        name = null;
        area = null;
        backGuard = null;
        super.onDestroyView();
    }
}
