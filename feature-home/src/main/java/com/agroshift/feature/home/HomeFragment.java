package com.agroshift.feature.home;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.agroshift.core.data.WaterRepository;
import com.agroshift.core.domain.engine.ClimateAlert;
import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.farm.FarmSelection;
import com.agroshift.core.domain.scene.LivingScene;
import com.agroshift.core.domain.season.SeasonPosition;
import com.agroshift.core.domain.season.SeasonTimeline;
import com.agroshift.core.domain.water.DangerAlert;
import com.agroshift.core.domain.water.DayForecast;
import com.agroshift.core.domain.water.FieldWater;
import com.agroshift.core.domain.water.IrrigationAdvisor;
import com.agroshift.core.domain.water.SkyState;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.i18n.WaterText;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.CropPhotos;
import com.agroshift.core.ui.LivingPhotoView;
import com.agroshift.core.ui.PhotoView;
import com.agroshift.core.ui.SeasonTimelineView;
import com.agroshift.core.ui.SplitBarView;
import com.agroshift.core.ui.ZameenMap;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private static final int FORECAST_DAYS = 7;
    private static final long SKY_FRESH_MILLIS = 3 * 60 * 60 * 1000L;

    private AnalysisRepository analysis;
    private OnlineServices online;
    private FarmField primary;
    private LiveData<AnalysisState> activeState;
    private LiveData<OnlineServices.Weather> activeWeather;
    private final Observer<AnalysisState> stateObserver = this::renderState;
    private final Observer<OnlineServices.Weather> weatherObserver = this::renderWeather;
    private final Observer<FieldWater> waterObserver = next -> {
        fieldWater = next;
        renderWater();
    };
    private WaterRepository water;
    private LiveData<FieldWater> activeWater;
    private FieldWater fieldWater;
    private OnlineServices.Weather latestWeather;
    private View waterCard;
    private TextView waterTitle;
    private TextView waterReason;
    private TextView waterTurn;
    private TextView waterSource;
    private LinearLayout dangers;
    private FarmStrip farmStrip;
    private List<FarmField> allFields;
    private View farmsSection;
    private TextView farmPill;
    private LivingPhotoView skyView;
    private TextView heroDate;
    private SeasonTimelineView timeline;
    private SplitBarView growth;
    private TextView timelineMonth;
    private TextView timelineCrop;
    private TextView timelineStage;
    private View timelineToday;
    private int timelineIndex;
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
        bindFarms(view, services, navigator);
        bindTimeline(view);
        bindTiles(view, navigator);
        water = services.waterRepository();
        waterCard = view.findViewById(R.id.home_water_card);
        ZameenUi.applySurface(waterCard, com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        waterTitle = view.findViewById(R.id.home_water_title);
        waterReason = view.findViewById(R.id.home_water_reason);
        waterTurn = view.findViewById(R.id.home_water_turn);
        waterSource = view.findViewById(R.id.home_water_source);
        dangers = view.findViewById(R.id.home_dangers);
        view.findViewById(R.id.home_water_done).setOnClickListener(v -> markWatered());
        view.findViewById(R.id.home_water_plan).setOnClickListener(v -> {
            if (primary != null) {
                navigator.openWater(primary.id());
            }
        });
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
            allFields = fields;
            refreshFarm();
        });
        analysis.selectedFieldId().observe(getViewLifecycleOwner(), id -> refreshFarm());
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

    private void refreshFarm() {
        if (allFields == null || getView() == null) {
            return;
        }
        FarmField next = FarmSelection.choose(allFields, analysis.selectedFieldId().getValue());
        farmsSection.setVisibility(allFields.isEmpty() ? View.GONE : View.VISIBLE);
        farmStrip.submit(allFields, next == null ? null : next.id());
        farmPill.setText(next == null ? getString(string.app_name) : next.name());
        bindPrimary(next);
        renderTimeline();
    }

    private void bindFarms(View view, AppServices services, AppNavigator navigator) {
        farmsSection = view.findViewById(R.id.home_farms_section);
        farmPill = view.findViewById(R.id.home_farm);
        ZameenUi.applySurface(farmPill, com.agroshift.core.ui.R.color.zameen_background,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.attachPressSpring(farmPill);
        farmStrip = new FarmStrip(this, view.findViewById(R.id.home_farms), services,
                field -> analysis.selectField(field.id()), navigator::openFieldSetup);
        farmPill.setOnClickListener(v -> chooseFarm(navigator));
    }

    private void chooseFarm(AppNavigator navigator) {
        if (allFields == null || allFields.isEmpty()) {
            navigator.openFieldSetup();
            return;
        }
        String[] names = new String[allFields.size()];
        int checked = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = allFields.get(i).name();
            if (primary != null && allFields.get(i).id().equals(primary.id())) {
                checked = i;
            }
        }
        List<FarmField> options = allFields;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(string.farm_pick_title)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    analysis.selectField(options.get(which).id());
                    dialog.dismiss();
                })
                .setPositiveButton(string.farm_add, (dialog, which) -> navigator.openFieldSetup())
                .setNegativeButton(string.action_close, null)
                .show();
    }

    private void bindTimeline(View view) {
        timeline = view.findViewById(R.id.home_timeline);
        growth = view.findViewById(R.id.home_growth);
        timelineMonth = view.findViewById(R.id.home_timeline_month);
        timelineCrop = view.findViewById(R.id.home_timeline_crop);
        timelineStage = view.findViewById(R.id.home_timeline_stage);
        timelineToday = view.findViewById(R.id.home_timeline_today);
        ZameenUi.applySurface(view.findViewById(R.id.home_timeline_card), com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        timelineIndex = SeasonTimeline.indexOf(LocalDate.now());
        timeline.setToday(timelineIndex);
        timeline.setOnMonthChanged(index -> {
            timelineIndex = index;
            renderTimeline();
        });
        timelineToday.setOnClickListener(v -> timeline.select(SeasonTimeline.indexOf(LocalDate.now())));
        renderTimeline();
    }

    private void showSeasonPhoto(SeasonPosition.Season season) {
        if (season == SeasonPosition.Season.RABI) {
            skyView.setPhoto(com.agroshift.core.ui.R.drawable.photo_home_rabi,
                    com.agroshift.core.ui.R.drawable.photo_home_rabi_mask);
        } else {
            skyView.setPhoto(com.agroshift.core.ui.R.drawable.photo_home_kharif,
                    com.agroshift.core.ui.R.drawable.photo_home_kharif_mask);
        }
    }

    private void renderTimeline() {
        if (timeline == null || getView() == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        RotationPlan plan = null;
        if (primary != null && primary.rotationKey() != null) {
            try {
                plan = RotationPlan.template(primary.rotationKey());
            } catch (IllegalArgumentException unknown) {
                plan = null;
            }
        }
        SeasonTimeline.Moment moment = SeasonTimeline.at(plan, SeasonTimeline.monthAt(today, timelineIndex));
        boolean isToday = timelineIndex == SeasonTimeline.indexOf(today);
        String month = moment.month().getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        String season = getString(moment.season() == SeasonPosition.Season.RABI
                ? string.season_rabi : string.season_kharif);
        timelineMonth.setText(getString(isToday ? string.timeline_now : string.timeline_month, month, season));
        if (primary == null) {
            timelineCrop.setText(string.timeline_no_farm);
            timelineStage.setText(string.timeline_hint);
            growth.setValues(0, 1);
            showSeasonPhoto(moment.season());
        } else if (moment.hasCrop()) {
            timelineCrop.setText(EngineText.cropName(requireContext(), moment.cropSlug()));
            timelineStage.setText(getString(string.timeline_stage, EngineText.stage(requireContext(), moment.stageKey())));
            growth.setValues(moment.growth(), 1 - moment.growth());
            CropPhotos.show(skyView, moment.cropSlug(), (float) moment.growth());
        } else {
            timelineCrop.setText(string.timeline_bare);
            timelineStage.setText(string.timeline_hint);
            growth.setValues(0, 1);
            showSeasonPhoto(moment.season());
        }
        timelineToday.setVisibility(isToday ? View.GONE : View.VISIBLE);
        timeline.setContentDescription(getString(string.timeline_description, month, season,
                timelineCrop.getText()));
    }

    private void bindPrimary(@Nullable FarmField field) {
        boolean same = primary != null && field != null && primary.equals(field);
        primary = field;
        if (same) {
            return;
        }
        skyView.setScene(LivingScene.forField(LocalDateTime.now(), null));
        renderHeroDate(null);
        detach();
        fieldWater = null;
        latestWeather = null;
        dangers.removeAllViews();
        waterCard.setVisibility(View.GONE);
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
        activeWater = water.observe(field.id());
        activeWater.observe(getViewLifecycleOwner(), waterObserver);
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
        if (activeWater != null) {
            activeWater.removeObserver(waterObserver);
            activeWater = null;
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
        weatherNow.setText(EngineText.number(weather.currentC(), 0) + "°");
        renderForecast(weather);
        if (weather.rainDate() == null) {
            weatherNext.setText(string.weather_dry);
        } else {
            weatherNext.setText(getString(string.weather_rain,
                    weather.rainDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())));
        }
        weatherCard.setVisibility(View.VISIBLE);
        latestWeather = weather;
        boolean fresh = weather.fetchedMillis() > 0
                && System.currentTimeMillis() - weather.fetchedMillis() < SKY_FRESH_MILLIS;
        SkyState sky = fresh ? weather.sky() : null;
        skyView.setScene(LivingScene.forField(LocalDateTime.now(), sky));
        renderHeroDate(sky);
        renderDangers(weather);
        renderWater();
    }

    private void renderForecast(OnlineServices.Weather weather) {
        View root = requireView();
        LocalDate today = LocalDate.now();
        boolean fresh = weather.fetchedMillis() > 0
                && System.currentTimeMillis() - weather.fetchedMillis() < SKY_FRESH_MILLIS;
        SkyState sky = fresh ? weather.sky() : null;
        LinearLayout forecast = root.findViewById(R.id.home_forecast);
        forecast.removeAllViews();
        DayForecast todayForecast = null;
        LayoutInflater inflater = getLayoutInflater();
        for (DayForecast day : weather.days()) {
            if (day.date().isBefore(today) || forecast.getChildCount() >= FORECAST_DAYS) {
                continue;
            }
            if (day.date().equals(today)) {
                todayForecast = day;
            }
            View item = inflater.inflate(R.layout.item_home_day, forecast, false);
            ((TextView) item.findViewById(R.id.day_name)).setText(day.date().equals(today)
                    ? getString(string.weather_today)
                    : day.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            ((ImageView) item.findViewById(R.id.day_icon)).setImageResource(iconFor(day.kind()));
            ((TextView) item.findViewById(R.id.day_high)).setText(EngineText.number(day.maxC(), 0) + "°");
            ((TextView) item.findViewById(R.id.day_low)).setText(EngineText.number(day.minC(), 0) + "°");
            item.setContentDescription(getString(string.weather_day_description,
                    day.date().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    EngineText.number(day.maxC(), 0), EngineText.number(day.minC(), 0), day.rainChance()));
            forecast.addView(item);
        }
        ImageView icon = root.findViewById(R.id.home_weather_icon);
        TextView condition = root.findViewById(R.id.home_weather_condition);
        if (sky != null) {
            condition.setText(WaterText.sky(requireContext(), sky));
            icon.setImageResource(iconFor(sky));
        } else if (todayForecast != null) {
            condition.setText(switch (todayForecast.kind()) {
                case SUN -> string.sky_clear;
                case CLOUD -> string.sky_cloudy;
                case RAIN -> string.sky_rain;
                case STORM -> string.sky_storm;
            });
            icon.setImageResource(iconFor(todayForecast.kind()));
        }
        TextView high = root.findViewById(R.id.home_weather_high);
        TextView low = root.findViewById(R.id.home_weather_low);
        TextView wind = root.findViewById(R.id.home_weather_wind);
        TextView chance = root.findViewById(R.id.home_weather_chance);
        if (todayForecast != null) {
            high.setText(getString(string.weather_high, EngineText.number(todayForecast.maxC(), 0)));
            low.setText(getString(string.weather_low, EngineText.number(todayForecast.minC(), 0)));
            chance.setText(getString(string.weather_rain_chance, todayForecast.rainChance()));
        }
        double windValue = sky != null ? sky.windKmh() : todayForecast != null ? todayForecast.windKmh() : 0;
        wind.setText(getString(string.weather_wind, EngineText.number(windValue, 0)));
    }

    private static int iconFor(DayForecast.Kind kind) {
        return switch (kind) {
            case SUN -> com.agroshift.core.ui.R.drawable.ic_ui_sun;
            case CLOUD -> com.agroshift.core.ui.R.drawable.ic_ui_cloud;
            case RAIN -> com.agroshift.core.ui.R.drawable.ic_ui_rain;
            case STORM -> com.agroshift.core.ui.R.drawable.ic_ui_storm;
        };
    }

    private static int iconFor(SkyState sky) {
        return switch (sky.condition()) {
            case HEAVY_RAIN, RAIN -> com.agroshift.core.ui.R.drawable.ic_ui_rain;
            case HOT -> com.agroshift.core.ui.R.drawable.ic_ui_heat;
            case WINDY -> com.agroshift.core.ui.R.drawable.ic_ui_wind;
            case CLOUDY -> com.agroshift.core.ui.R.drawable.ic_ui_cloud;
            case CLEAR -> sky.day() ? com.agroshift.core.ui.R.drawable.ic_ui_sun
                    : com.agroshift.core.ui.R.drawable.ic_ui_moon;
        };
    }

    private void renderDangers(OnlineServices.Weather weather) {
        dangers.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (DangerAlert alert : DangerAlert.find(weather.days(), LocalDate.now())) {
            View row = inflater.inflate(R.layout.item_home_danger, dangers, false);
            ZameenUi.applySurface(row, com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
            ((TextView) row.findViewById(R.id.danger_severity)).setText(WaterText.dangerSeverity(requireContext(), alert));
            ((TextView) row.findViewById(R.id.danger_title)).setText(WaterText.dangerTitle(requireContext(), alert));
            ((TextView) row.findViewById(R.id.danger_detail)).setText(WaterText.dangerDetail(requireContext(), alert));
            dangers.addView(row);
        }
    }

    private void renderWater() {
        if (getView() == null || primary == null || !primary.hasLocation() || fieldWater == null) {
            return;
        }
        List<DayForecast> days = latestWeather == null ? List.of() : latestWeather.days();
        IrrigationAdvisor.Answer answer = IrrigationAdvisor.decide(days, fieldWater.lastWatered(), LocalDate.now(),
                primary.water(), primary.soil());
        waterTitle.setText(WaterText.title(requireContext(), answer));
        waterReason.setText(WaterText.reason(requireContext(), answer));
        waterTurn.setText(fieldWater.turn() == null ? getString(string.turn_none)
                : WaterText.nextTurn(requireContext(), fieldWater.turn()));
        if (latestWeather != null && latestWeather.fetchedMillis() > 0) {
            waterSource.setText(getString(string.water_estimate, DateUtils.getRelativeTimeSpanString(
                    latestWeather.fetchedMillis(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)));
            waterSource.setVisibility(View.VISIBLE);
        } else {
            waterSource.setVisibility(View.GONE);
        }
        waterCard.setVisibility(View.VISIBLE);
    }

    private void markWatered() {
        if (primary == null || fieldWater == null) {
            return;
        }
        String id = primary.id();
        LocalDate previous = fieldWater.lastWatered();
        water.setLastWatered(id, LocalDate.now());
        Snackbar.make(requireView(), string.water_marked, Snackbar.LENGTH_LONG)
                .setAction(string.action_undo, v -> water.setLastWatered(id, previous)).show();
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
        LivingPhotoView photo = view.findViewById(R.id.home_photo);
        skyView = photo;
        photo.setFieldMode(true);
        photo.setCropFocus(0.5f, 0.6f);
        photo.setScene(LivingScene.forField(LocalDateTime.now(), null));
        showSeasonPhoto(position.season());
        heroDate = view.findViewById(R.id.home_date);
        renderHeroDate(null);
        if (ZameenUi.motionEnabled(requireContext())) {
            NestedScrollView scroll = view.findViewById(R.id.home_scroll);
            scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                    (v, x, y, oldX, oldY) -> photo.setTranslationY(y * HERO_PARALLAX));
        }
    }

    private void renderHeroDate(@Nullable SkyState sky) {
        SeasonPosition position = SeasonPosition.of(LocalDate.now());
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()));
        String line = getString(string.home_date, getString(position.season() == SeasonPosition.Season.RABI
                ? string.season_rabi : string.season_kharif), date);
        heroDate.setText(sky == null ? line : getString(string.home_date, line, WaterText.sky(requireContext(), sky)));
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
        skyView.setPaused(false);
    }

    @Override
    public void onPause() {
        skyView.setPaused(true);
        map.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        detach();
        farmStrip.clear();
        farmStrip = null;
        allFields = null;
        farmsSection = null;
        farmPill = null;
        skyView = null;
        heroDate = null;
        timeline = null;
        growth = null;
        timelineMonth = null;
        timelineCrop = null;
        timelineStage = null;
        timelineToday = null;
        map.onDetach();
        map = null;
        mapSection = null;
        waterCard = null;
        waterTitle = null;
        waterReason = null;
        waterTurn = null;
        waterSource = null;
        dangers = null;
        fieldWater = null;
        latestWeather = null;
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
