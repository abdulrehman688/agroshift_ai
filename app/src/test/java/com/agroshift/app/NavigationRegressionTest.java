package com.agroshift.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
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
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Opens the real screens and follows the navigation paths farmers use. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = TestAgroShiftApp.class)
public class NavigationRegressionTest {
    private MainActivity activity;
    private BottomNavigationView tabs;
    private NavController navigation;
    private AppServices services;

    @Before
    public void openApp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("agroshift_language", Context.MODE_PRIVATE).edit()
                .putString("selected_tag", "en-PK").putBoolean("onboarding_seen", true).commit();
        services = (AppServices) context;
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
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

    private FarmField addFarm() {
        FarmField field = services.fieldRepository().add("Lahore farm", 2, 31.5, 74.3, RotationPlan.COTTON_WHEAT,
                WaterAccess.IRRIGATED, FarmGoal.of(List.of()), SoilType.LOAM).value();
        services.recordRepository().add(field.id(), FarmRecord.Type.TASK, "Plough", 0);
        services.recordRepository().add(field.id(), FarmRecord.Type.EXPENSE, "Seed", 500);
        services.recordRepository().add(field.id(), FarmRecord.Type.SALE, "Wheat", 1200);
        services.recordRepository().add(field.id(), FarmRecord.Type.NOTE, "Check canal", 0);
        settle();
        return field;
    }

    private void openTab(int id) {
        tabs.setSelectedItemId(id);
        settle();
        assertEquals(id, navigation.getCurrentDestination().getId());
        assertEquals(id, tabs.getSelectedItemId());
    }

    @Test
    public void farmAndMarketOpenWithoutFarms() {
        openTab(R.id.farmFragment);
        openTab(R.id.marketFragment);
        openTab(R.id.homeFragment);
    }

    @Test
    public void farmAndMarketOpenWithRecords() {
        addFarm();
        openTab(R.id.farmFragment);
        openTab(R.id.marketFragment);
        openTab(R.id.farmFragment);
        openTab(R.id.marketFragment);
    }

    @Test
    public void fieldDetailsOpenWithRecords() {
        FarmField field = addFarm();
        activity.openField(field.id());
        settle();
        assertEquals(R.id.fieldDetailFragment, navigation.getCurrentDestination().getId());
    }

    @Test
    public void tabsWorkAfterCropPlanCard() {
        FarmField field = addFarm();
        for (int tab : new int[] {R.id.homeFragment, R.id.communityFragment, R.id.farmFragment, R.id.marketFragment}) {
            openTab(R.id.homeFragment);
            activity.openAdvisor(field.id());
            settle();
            assertEquals(R.id.advisorFragment, navigation.getCurrentDestination().getId());
            assertEquals(R.id.advisorFragment, tabs.getSelectedItemId());
            openTab(tab);
        }
    }

    @Test
    public void cropPlanFromFieldDetailsKeepsTabsWorking() {
        FarmField field = addFarm();
        openTab(R.id.farmFragment);
        activity.openField(field.id());
        settle();
        activity.openAdvisor(field.id());
        settle();
        assertEquals(R.id.advisorFragment, navigation.getCurrentDestination().getId());
        openTab(R.id.farmFragment);
        openTab(R.id.homeFragment);
    }

    @Test
    public void tabsWorkAfterVisitingAdvisorTabThenCropPlan() {
        FarmField field = addFarm();
        openTab(R.id.advisorFragment);
        openTab(R.id.homeFragment);
        activity.openAdvisor(field.id());
        settle();
        openTab(R.id.homeFragment);
        activity.openAdvisor(field.id());
        settle();
        openTab(R.id.communityFragment);
    }
}
