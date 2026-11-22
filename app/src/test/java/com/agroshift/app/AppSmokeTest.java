package com.agroshift.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ApplicationProvider;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import java.util.List;
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
        activity.openLanguage();
        settle();
        layout(current());
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
