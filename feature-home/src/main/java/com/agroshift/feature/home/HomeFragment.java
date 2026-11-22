package com.agroshift.feature.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.agroshift.core.data.AnalysisRepository;
import com.agroshift.core.data.AnalysisState;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.OnlineServices;
import com.agroshift.core.domain.engine.ClimateAlert;
import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.season.SeasonPosition;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.PhotoView;
import com.agroshift.core.ui.SeasonThreadView;
import com.agroshift.core.ui.ZameenMap;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

/** Today's answer over a seasonal farm photo, followed by field data and quick actions. */
public final class HomeFragment extends Fragment {
    private static final long SKELETON_DELAY_MILLIS = 150;
    private static final float HERO_PARALLAX = 0.35f;
    private static final double FIELD_ZOOM = 14.5;

    private AnalysisRepository analysis;
    private OnlineServices online;
    private FarmField primary;
    private LiveData<AnalysisState> activeState;
    private LiveData<OnlineServices.Weather> activeWeather;
    private final Observer<AnalysisState> stateObserver = this::renderState;
    private final Observer<OnlineServices.Weather> weatherObserver = this::renderWeather;
    private MapView map;
    private View mapSection;
    private Marker marker;
    private View weatherCard;
    private View watchCard;
    private View stats;
    private TextView rain;
    private TextView heat;
    private TextView risks;
    private TextView title;
    private TextView body;
    private TextView weatherNow;
    private TextView weatherNext;
    private TextView watch;
    private TextView tasks;
    private MaterialButton primaryAction;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppServices services = (AppServices) requireContext().getApplicationContext();
        analysis = services.analysisRepository();
        online = services.onlineServices();
        AppNavigator navigator = (AppNavigator) requireActivity();
        bindSeason(view);
        bindTopBar(view, navigator);

        map = view.findViewById(R.id.home_map);
        ZameenMap.prepare(map, false);
        ZameenMap.useSatellite(map, true);
        mapSection = view.findViewById(R.id.home_map_section);
        View mapCard = view.findViewById(R.id.home_map_card);
        ZameenUi.applySurface(mapCard, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        mapCard.setClipToOutline(true);
        view.findViewById(R.id.home_map_open).setOnClickListener(v -> {
            if (primary != null) {
                navigator.openField(primary.id());
            }
        });
        bindHero(view);
        bindTiles(view, navigator);
        title = view.findViewById(R.id.home_title);
        body = view.findViewById(R.id.home_body);
        weatherCard = view.findViewById(R.id.home_weather_card);
        watchCard = view.findViewById(R.id.home_watch_card);
        stats = view.findViewById(R.id.home_stats);
        rain = view.findViewById(R.id.home_rain_value);
        heat = view.findViewById(R.id.home_heat_value);
        risks = view.findViewById(R.id.home_risk_value);
        for (int id : new int[] {R.id.home_rain_card, R.id.home_heat_card, R.id.home_risk_card}) {
            ZameenUi.applySurface(view.findViewById(id), com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
        weatherNow = view.findViewById(R.id.home_weather_now);
        weatherNext = view.findViewById(R.id.home_weather_next);
        watch = view.findViewById(R.id.home_watch);
        tasks = view.findViewById(R.id.home_tasks);
        for (View card : new View[] {weatherCard, watchCard, tasks}) {
            ZameenUi.applySurface(card, com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }

        primaryAction = view.findViewById(R.id.home_primary);
        ZameenUi.polishPrimary(primaryAction);
        primaryAction.setOnClickListener(v -> {
            if (primary != null && primary.hasLocation()) {
                navigator.openAdvisor(primary.id());
            } else {
                navigator.openFieldSetup();
            }
        });
        tasks.setOnClickListener(v -> {
            if (primary != null) {
                navigator.openField(primary.id());
            }
        });

        View skeleton = view.findViewById(R.id.home_skeleton);
        Runnable showSkeleton = () -> skeleton.setVisibility(View.VISIBLE);
        view.postDelayed(showSkeleton, SKELETON_DELAY_MILLIS);

        services.fieldRepository().observeFields().observe(getViewLifecycleOwner(), fields -> {
            view.removeCallbacks(showSkeleton);
            skeleton.setVisibility(View.GONE);
            FarmField next = null;
            for (FarmField field : fields) {
                if (next == null || (!next.hasLocation() && field.hasLocation())) {
                    next = field;
                }
            }
            bindPrimary(next);
        });
        services.recordRepository().observeAll().observe(getViewLifecycleOwner(), this::renderTasks);
    }

    private void renderTasks(List<FarmRecord> records) {
        int open = 0;
        for (FarmRecord record : records) {
            if (record.type() == FarmRecord.Type.TASK && !record.done()) {
                open++;
            }
        }
        tasks.setText(open == 0 ? getString(string.home_no_tasks) : getString(string.home_tasks, open));
    }

    private void bindPrimary(@Nullable FarmField field) {
        boolean same = primary != null && field != null && primary.equals(field);
        primary = field;
        if (same) {
            return;
        }
        detach();
        weatherCard.setVisibility(View.GONE);
        watchCard.setVisibility(View.GONE);
        stats.setVisibility(View.GONE);
        tasks.setVisibility(field == null ? View.GONE : View.VISIBLE);
        if (field == null) {
            mapSection.setVisibility(View.GONE);
            title.setText(string.home_empty_title);
            body.setVisibility(View.VISIBLE);
            body.setText(string.home_empty_body);
            primaryAction.setText(string.action_add_field);
            return;
        }
        title.setText(field.name());
        body.setVisibility(View.VISIBLE);
        if (!field.hasLocation()) {
            mapSection.setVisibility(View.GONE);
            body.setText(string.home_add_location);
            primaryAction.setText(string.action_add_field);
            return;
        }
        primaryAction.setText(string.home_view_plan);
        body.setText(string.home_loading);
        showFieldOnMap(field);
        activeState = analysis.observe(field.id());
        activeState.observe(getViewLifecycleOwner(), stateObserver);
        analysis.load(field, false);
        activeWeather = online.observeWeather(field);
        activeWeather.observe(getViewLifecycleOwner(), weatherObserver);
    }

    private void detach() {
        if (activeState != null) {
            activeState.removeObserver(stateObserver);
            activeState = null;
        }
        if (activeWeather != null) {
            activeWeather.removeObserver(weatherObserver);
            activeWeather = null;
        }
    }

    private void showFieldOnMap(FarmField field) {
        GeoPoint point = new GeoPoint(field.latitude(), field.longitude());
        mapSection.setVisibility(View.VISIBLE);
        map.getController().setZoom(FIELD_ZOOM);
        map.getController().setCenter(point);
        if (marker == null) {
            marker = ZameenMap.pin(map, point);
            map.getOverlays().add(marker);
        } else {
            marker.setPosition(point);
        }
        map.invalidate();
    }

    private void renderWeather(@Nullable OnlineServices.Weather weather) {
        if (weather == null || getView() == null) {
            return;
        }
        weatherNow.setText(getString(string.weather_now, EngineText.number(weather.currentC(), 0)));
        if (weather.rainDate() == null) {
            weatherNext.setText(string.weather_dry);
        } else {
            weatherNext.setText(getString(string.weather_rain,
                    weather.rainDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())));
        }
        weatherCard.setVisibility(View.VISIBLE);
    }

    private void renderState(@Nullable AnalysisState state) {
        if (state == null || primary == null || getView() == null) {
            return;
        }
        if (state.status() == AnalysisState.Status.LOADING) {
            body.setVisibility(View.VISIBLE);
            body.setText(string.home_loading);
        } else if (state.status() == AnalysisState.Status.FAILED) {
            body.setVisibility(View.VISIBLE);
            body.setText(string.advisor_error_network);
        } else if (state.status() == AnalysisState.Status.READY) {
            RotationAdvisor.Advice advice = state.advice();
            boolean keep = advice.best().kind() == RotationAdvisor.Kind.CURRENT;
            title.setText(keep ? getString(string.plan_keep)
                    : EngineText.cropSequence(requireContext(), advice.best().plan()));
            body.setVisibility(View.VISIBLE);
            body.setText(EngineText.benefit(requireContext(), advice.benefit()));
            rain.setText(EngineText.number(advice.profile().annualRainMm(), 0) + " mm");
            heat.setText(EngineText.number(advice.profile().value(Metric.HEAT_DAYS, 6), 0));
            int high = 0;
            for (ClimateAlert alert : advice.current().alerts()) {
                if (alert.severity().compareTo(ClimateAlert.Severity.HIGH) >= 0) {
                    high++;
                }
            }
            risks.setText(EngineText.number(high, 0));
            stats.setVisibility(View.VISIBLE);
            List<ClimateAlert> top = EngineText.topAlerts(advice.current().alerts(), 1);
            if (top.isEmpty()) {
                watchCard.setVisibility(View.GONE);
            } else {
                watch.setText(EngineText.alertTitle(requireContext(), top.get(0)));
                watchCard.setVisibility(View.VISIBLE);
            }
        }
    }

    private void bindSeason(View view) {
        SeasonPosition position = SeasonPosition.of(LocalDate.now());
        SeasonThreadView thread = view.findViewById(R.id.home_season_thread);
        thread.setFraction((float) position.yearFraction());
        String season = getString(position.season() == SeasonPosition.Season.RABI
                ? string.season_rabi : string.season_kharif);
        thread.setContentDescription(getString(string.season_thread_description, season,
                Math.round(position.yearFraction() * 100)));
    }

    private void bindTopBar(View view, AppNavigator navigator) {
        View topBar = view.findViewById(R.id.home_top_bar);
        int gap = Math.round(8 * view.getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPaddingRelative(v.getPaddingStart(), safe.top + gap, v.getPaddingEnd(), 0);
            return insets;
        });
        View command = view.findViewById(R.id.home_command);
        ZameenUi.applySurface(command, com.agroshift.core.ui.R.color.zameen_background,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.attachPressSpring(command);
        command.setOnClickListener(v -> navigator.openCommandMenu());
        View language = view.findViewById(R.id.home_language);
        ZameenUi.applySurface(language, com.agroshift.core.ui.R.color.zameen_background,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.attachPressSpring(language);
        language.setOnClickListener(v -> navigator.openLanguage());
    }

    private void bindHero(View view) {
        SeasonPosition position = SeasonPosition.of(LocalDate.now());
        boolean rabi = position.season() == SeasonPosition.Season.RABI;
        PhotoView photo = view.findViewById(R.id.home_photo);
        photo.setPhoto(rabi ? com.agroshift.core.ui.R.drawable.photo_home_rabi
                : com.agroshift.core.ui.R.drawable.photo_home_kharif);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()));
        ((TextView) view.findViewById(R.id.home_date)).setText(getString(string.home_date,
                getString(rabi ? string.season_rabi : string.season_kharif), date));
        if (ZameenUi.motionEnabled(requireContext())) {
            NestedScrollView scroll = view.findViewById(R.id.home_scroll);
            scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                    (v, x, y, oldX, oldY) -> photo.setTranslationY(y * HERO_PARALLAX));
        }
    }

    private void bindTiles(View view, AppNavigator navigator) {
        tile(view, R.id.home_tile_plan, com.agroshift.core.ui.R.drawable.photo_tile_plan, string.home_tile_plan, () -> {
            if (primary != null && primary.hasLocation()) {
                navigator.openAdvisor(primary.id());
            } else {
                navigator.openFieldSetup();
            }
        });
        tile(view, R.id.home_tile_ask, com.agroshift.core.ui.R.drawable.photo_tile_ask, string.home_tile_ask,
                navigator::openAssistant);
        tile(view, R.id.home_tile_farm, com.agroshift.core.ui.R.drawable.photo_tile_water, string.home_tile_farm,
                navigator::openFarm);
        tile(view, R.id.home_tile_market, com.agroshift.core.ui.R.drawable.photo_tile_market, string.home_tile_market,
                navigator::openMarket);
    }

    private static void tile(View root, int id, int photo, int label, Runnable action) {
        View tile = root.findViewById(id);
        ((PhotoView) tile.findViewById(R.id.tile_photo)).setPhoto(photo);
        ((TextView) tile.findViewById(R.id.tile_label)).setText(label);
        ZameenUi.attachPressSpring(tile);
        tile.setOnClickListener(v -> action.run());
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        map.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        detach();
        map.onDetach();
        map = null;
        mapSection = null;
        marker = null;
        primary = null;
        weatherCard = null;
        watchCard = null;
        stats = null;
        rain = null;
        heat = null;
        risks = null;
        title = null;
        body = null;
        weatherNow = null;
        weatherNext = null;
        watch = null;
        tasks = null;
        primaryAction = null;
        super.onDestroyView();
    }
}
