package com.agroshift.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ApplicationProvider;
import com.agroshift.core.data.AnalysisState;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.engine.ClimateEngine;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.DailyClimate;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.highlight.Highlight;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Opens every main screen and uses its key controls, in English and Urdu. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = TestAgroShiftApp.class)
public class AppSmokeTest {
    private MainActivity activity;
    private NavController navigation;
    private BottomNavigationView tabs;
    private AppServices services;

    private void open() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("agroshift_language", Context.MODE_PRIVATE).edit()
                .putString("selected_tag", "en-PK").putBoolean("onboarding_seen", true).commit();
        android.provider.Settings.Global.putFloat(context.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        services = (AppServices) context;
        activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        tabs = activity.findViewById(R.id.bottom_nav);
        NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host);
        assertNotNull(host);
        navigation = host.getNavController();
        settle();
    }

    private void settle() {
        for (int i = 0; i < 25; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    private FarmField farm(String name, double latitude, double longitude) {
        FarmField field = services.fieldRepository().add(name, 2, latitude, longitude, RotationPlan.RICE_WHEAT,
                WaterAccess.IRRIGATED, FarmGoal.of(List.of()), SoilType.CLAY).value();
        services.recordRepository().add(field.id(), FarmRecord.Type.TASK, "Plough", 0);
        services.recordRepository().add(field.id(), FarmRecord.Type.EXPENSE, "Seed", 500);
        services.recordRepository().add(field.id(), FarmRecord.Type.SALE, "Rice", 1500);
        services.recordRepository().add(field.id(), FarmRecord.Type.NOTE, "Check canal", 0);
        settle();
        return field;
    }

    private View current() {
        Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host);
        return host.getChildFragmentManager().getPrimaryNavigationFragment().requireView();
    }

    private Fragment currentFragment() {
        Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host);
        return host.getChildFragmentManager().getPrimaryNavigationFragment();
    }

    private void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2200);
        settle();
    }

    private void useAllScreens() {
        FarmField first = farm("Lahore farm", 31.5, 74.3);
        FarmField second = farm("Multan farm", 30.2, 71.5);
        layout(current());

        ViewGroup strip = current().findViewById(com.agroshift.feature.home.R.id.home_farms);
        assertEquals(3, strip.getChildCount());
        strip.getChildAt(1).performClick();
        settle();
        strip = current().findViewById(com.agroshift.feature.home.R.id.home_farms);
        strip.getChildAt(0).performClick();
        settle();

        for (int tab : new int[] {R.id.farmFragment, R.id.advisorFragment, R.id.marketFragment,
                R.id.communityFragment, R.id.homeFragment}) {
            tabs.setSelectedItemId(tab);
            settle();
            assertEquals(tab, navigation.getCurrentDestination().getId());
            layout(current());
        }

        activity.openWater(first.id());
        settle();
        DialogFragment water = (DialogFragment) activity.getSupportFragmentManager().findFragmentByTag("water_plan");
        assertNotNull(water);
        View sheet = water.requireView();
        sheet.findViewById(com.agroshift.feature.farm.R.id.water_today).performClick();
        settle();
        sheet.findViewById(com.agroshift.feature.farm.R.id.turn_enabled).performClick();
        settle();
        layout(sheet);
        water.dismiss();
        settle();

        activity.openField(second.id());
        settle();
        View detail = current();
        layout(detail);
        ChipGroup filters = detail.findViewById(com.agroshift.feature.farm.R.id.detail_filters);
        for (int i = filters.getChildCount() - 1; i >= 0; i--) {
            filters.getChildAt(i).performClick();
            settle();
        }
        ViewGroup actions = detail.findViewById(com.agroshift.feature.farm.R.id.detail_actions);
        for (int i = 0; i < actions.getChildCount(); i++) {
            actions.getChildAt(i).performClick();
            settle();
            List<Fragment> dialogs = currentFragment().getChildFragmentManager().getFragments();
            assertTrue(!dialogs.isEmpty());
            ((DialogFragment) dialogs.get(dialogs.size() - 1)).dismiss();
            settle();
        }
        activity.getOnBackPressedDispatcher().onBackPressed();
        settle();

        activity.openAssistant();
        settle();
        layout(current());
        activity.getOnBackPressedDispatcher().onBackPressed();
        settle();
        activity.openFieldSetup();
        settle();
        layout(current());
        activity.getOnBackPressedDispatcher().onBackPressed();
        settle();
        useAdvisor(first);
        useHelp();
        useSearch();
        activity.openLanguage();
        settle();
        layout(current());
    }

    /** The real analysis must finish first, or its late result can replace the test's state. */
    private void waitForBackgroundAnalysis(FarmField field) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            AnalysisState state = services.analysisRepository().observe(field.id()).getValue();
            if (state != null && state.status() != AnalysisState.Status.LOADING) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void useAdvisor(FarmField field) {
        tabs.setSelectedItemId(R.id.advisorFragment);
        settle();
        List<DailyClimate> days = new ArrayList<>();
        LocalDate date = LocalDate.of(2006, 1, 1);
        int index = 0;
        while (date.isBefore(LocalDate.of(2026, 1, 1))) {
            int month = date.getMonthValue();
            double high = month >= 5 && month <= 8 ? 41 : 25;
            double rain = index++ % 6 == 0 ? (month == 7 ? 12 : 1.5) : 0;
            days.add(new DailyClimate(date, high - 8, high, high - 15, rain, 0.3));
            date = date.plusDays(1);
        }
        Map<ClimateScenario, RotationAdvisor.Advice> outlooks = new EnumMap<>(ClimateScenario.class);
        for (ClimateScenario scenario : ClimateScenario.values()) {
            outlooks.put(scenario, RotationAdvisor.advise(ClimateEngine.buildScenario(days, 31.0, scenario),
                    field.rotationKey(), field.water(), field.goal(), field.soil()));
        }
        services.analysisRepository().selectField(field.id());
        settle();
        waitForBackgroundAnalysis(field);
        ((MutableLiveData<AnalysisState>) services.analysisRepository().observe(field.id())).setValue(
                new AnalysisState(AnalysisState.Status.READY, 0, 0, outlooks, null, false));
        shadowOf(Looper.getMainLooper()).idle();
        View advisor = current();
        layout(advisor);
        ((MutableLiveData<AnalysisState>) services.analysisRepository().observe(field.id())).setValue(
                new AnalysisState(AnalysisState.Status.READY, 0, 0, outlooks, null, false));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, advisor.findViewById(com.agroshift.feature.advisor.R.id.advisor_content).getVisibility());
        ChipGroup whatIf = advisor.findViewById(com.agroshift.feature.advisor.R.id.advisor_whatif);
        for (int i = whatIf.getChildCount() - 1; i >= 0; i--) {
            whatIf.getChildAt(i).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            draw(advisor);
        }
        ViewGroup options = advisor.findViewById(com.agroshift.feature.advisor.R.id.advisor_options);
        assertEquals(3, options.getChildCount());
        TextView caption = advisor.findViewById(com.agroshift.feature.advisor.R.id.advisor_scene_caption);
        String first = caption.getText().toString();
        boolean changed = false;
        for (int i = 0; i < options.getChildCount(); i++) {
            options.getChildAt(i).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            changed |= !first.equals(caption.getText().toString());
            draw(advisor);
        }
        assertTrue(changed);
        CombinedChart chart = advisor.findViewById(com.agroshift.feature.advisor.R.id.advisor_chart);
        assertNotNull(chart.getData());
        TextView month = advisor.findViewById(com.agroshift.feature.advisor.R.id.advisor_chart_month);
        String before = month.getText().toString();
        int slot = before.startsWith(new java.text.DateFormatSymbols().getMonths()[3]) ? 4 : 3;
        Highlight tapped = new Highlight(slot, Float.NaN, 0);
        tapped.setDataIndex(1);
        chart.highlightValue(tapped, true);
        assertNotEquals(before, month.getText().toString());
        draw(advisor);
    }

    private void draw(View view) {
        layout(view);
        view.draw(new Canvas(Bitmap.createBitmap(1080, 2200, Bitmap.Config.ARGB_8888)));
    }

    private void useHelp() {
        tabs.setSelectedItemId(R.id.communityFragment);
        settle();
        View help = current();
        layout(help);
        ChipGroup filters = help.findViewById(com.agroshift.feature.help.R.id.help_crop_filters);
        for (int i = filters.getChildCount() - 1; i >= 0; i--) {
            filters.getChildAt(i).performClick();
            settle();
        }
        ViewGroup grid = help.findViewById(com.agroshift.feature.help.R.id.help_crop_grid);
        assertEquals(6, grid.getChildCount());
        ((ViewGroup) grid.getChildAt(0)).getChildAt(0).performClick();
        settle();
        DialogFragment guide = (DialogFragment) currentFragment().getChildFragmentManager()
                .findFragmentByTag("crop_guide");
        assertNotNull(guide);
        Slider slider = guide.requireView().findViewById(com.agroshift.feature.help.R.id.guide_slider);
        slider.setValue(slider.getValueTo());
        settle();
        draw(guide.requireView());
        guide.dismiss();
        settle();
    }

    private void useSearch() {
        activity.openCommandMenu();
        settle();
        DialogFragment menu = (DialogFragment) activity.getSupportFragmentManager().findFragmentByTag("command_menu");
        assertNotNull(menu);
        View sheet = menu.requireView();
        EditText input = sheet.findViewById(R.id.command_search);
        ViewGroup rows = sheet.findViewById(R.id.command_rows);
        input.setText("Multan");
        settle();
        assertTrue(rows.getChildCount() >= 2);
        input.setText("zzzz");
        settle();
        assertEquals(View.VISIBLE, sheet.findViewById(R.id.command_empty).getVisibility());
        input.setText("Rice");
        settle();
        ChipGroup filters = sheet.findViewById(R.id.command_filters);
        for (int i = filters.getChildCount() - 1; i >= 0; i--) {
            filters.getChildAt(i).performClick();
            settle();
        }
        input.setText("Wheat");
        settle();
        draw(sheet);
        rows.getChildAt(rows.getChildCount() - 1).performClick();
        settle();
        Fragment opened = activity.getSupportFragmentManager().findFragmentByTag("crop_guide");
        if (opened instanceof DialogFragment guide) {
            guide.dismiss();
            settle();
        }
    }

    @Test
    public void everyScreenWorksInEnglish() {
        open();
        useAllScreens();
    }

    @Test
    @Config(qualifiers = "ur-rPK")
    public void everyScreenWorksInUrdu() {
        open();
        useAllScreens();
    }
}
