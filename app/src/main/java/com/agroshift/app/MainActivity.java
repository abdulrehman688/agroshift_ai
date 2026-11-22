package com.agroshift.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.Menu;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.language.AppLanguage;
import com.agroshift.core.i18n.LanguageController;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.feature.account.LanguageFragment;
import com.agroshift.feature.account.OnboardingFragment;
import com.agroshift.feature.farm.FieldDetailFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.List;
import java.util.Set;

public final class MainActivity extends AppCompatActivity
        implements LanguageFragment.Host, OnboardingFragment.Host, AppNavigator, CommandMenuSheet.Host {
    private static final String ACTION_ADD_FIELD = "com.agroshift.app.action.ADD_FIELD";
    private static final String TAG_COMMAND = "command_menu";
    private static final Set<Integer> FULL_SCREEN = Set.of(R.id.languageFragment, R.id.onboardingFragment,
            R.id.fieldSetupFragment, R.id.fieldDetailFragment, R.id.assistantFragment);

    private NavController navController;
    private LanguageController languages;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle state) {
        EdgeToEdge.enable(this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        languages = new LanguageController(this);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        navController = host.getNavController();
        bottomNav = findViewById(R.id.bottom_nav);
        NavigationUI.setupWithNavController(bottomNav, navController);
        bottomNav.setOnItemSelectedListener(item -> {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == item.getItemId()) {
                return true;
            }
            navController.navigate(item.getItemId(), null, motion()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(R.id.homeFragment, false, true)
                    .build());
            return true;
        });
        navController.addOnDestinationChangedListener((controller, destination, args) ->
                bottomNav.setVisibility(FULL_SCREEN.contains(destination.getId()) ? View.GONE : View.VISIBLE));

        if (state == null && !languages.hasChosenLanguage()) {
            navController.navigate(R.id.languageFragment, null, new NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true).build());
        } else if (state == null && !languages.hasSeenOnboarding()) {
            navController.navigate(R.id.onboardingFragment, null, new NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true).build());
        } else if (state == null) {
            handleShortcut(getIntent());
        }
    }

    private static NavOptions.Builder motion() {
        return new NavOptions.Builder()
                .setEnterAnim(R.anim.zameen_enter)
                .setExitAnim(R.anim.zameen_exit)
                .setPopEnterAnim(R.anim.zameen_enter)
                .setPopExitAnim(R.anim.zameen_exit);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (languages.hasChosenLanguage() && languages.hasSeenOnboarding()) {
            handleShortcut(intent);
        }
    }

    @Override
    public void onLanguageChosen(AppLanguage language) {
        if (!languages.hasSeenOnboarding()) {
            navController.navigate(R.id.onboardingFragment, null, motion()
                    .setPopUpTo(R.id.languageFragment, true).build());
        } else if (!navController.popBackStack(R.id.homeFragment, false)) {
            navController.navigate(R.id.homeFragment, null, motion()
                    .setPopUpTo(R.id.languageFragment, true).build());
        }
        languages.choose(language);
    }

    @Override
    public void onOnboardingDone() {
        languages.markOnboardingSeen();
        navController.navigate(R.id.homeFragment, null, motion()
                .setPopUpTo(R.id.onboardingFragment, true).build());
    }

    @Override
    public void openLanguage() {
        navController.navigate(R.id.languageFragment, null, motion().build());
    }

    @Override
    public void openFieldSetup() {
        if (navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() != R.id.fieldSetupFragment) {
            navController.navigate(R.id.fieldSetupFragment, null, motion().build());
        }
    }

    @Override
    public void openField(String fieldId) {
        Bundle args = new Bundle();
        args.putString(FieldDetailFragment.ARG_FIELD_ID, fieldId);
        navController.navigate(R.id.fieldDetailFragment, args, motion().build());
    }

    @Override
    public void openAdvisor(String fieldId) {
        ((AppServices) getApplication()).analysisRepository().selectField(fieldId);
        navController.navigate(R.id.advisorFragment, null, motion()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.homeFragment, false).build());
    }

    @Override
    public void openAssistant() {
        navController.navigate(R.id.assistantFragment, null, motion().build());
    }

    @Override
    public void openFarm() {
        bottomNav.setSelectedItemId(R.id.farmFragment);
    }

    @Override
    public void openMarket() {
        bottomNav.setSelectedItemId(R.id.marketFragment);
    }

    @Override
    public void openCommandMenu() {
        if (getSupportFragmentManager().findFragmentByTag(TAG_COMMAND) == null) {
            new CommandMenuSheet().show(getSupportFragmentManager(), TAG_COMMAND);
        }
    }

    @Override
    public void runCommand(CommandMenuSheet.Command command) {
        switch (command) {
            case ADD_FIELD -> openFieldSetup();
            case HOME -> bottomNav.setSelectedItemId(R.id.homeFragment);
            case FARM -> bottomNav.setSelectedItemId(R.id.farmFragment);
            case ADVISOR -> bottomNav.setSelectedItemId(R.id.advisorFragment);
            case MARKET -> bottomNav.setSelectedItemId(R.id.marketFragment);
            case COMMUNITY -> bottomNav.setSelectedItemId(R.id.communityFragment);
            case LANGUAGE -> openLanguage();
        }
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_K && event.isCtrlPressed()
                && navController.getCurrentDestination() != null
                && !FULL_SCREEN.contains(navController.getCurrentDestination().getId())) {
            openCommandMenu();
            return true;
        }
        return super.onKeyShortcut(keyCode, event);
    }

    @Override
    public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data, Menu menu,
            int deviceId) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId);
        data.add(new KeyboardShortcutGroup(
                getString(com.agroshift.core.i18n.R.string.app_name), List.of(
                new KeyboardShortcutInfo(getString(com.agroshift.core.i18n.R.string.action_search),
                        KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_ON))));
    }

    private void handleShortcut(Intent intent) {
        if (intent != null && ACTION_ADD_FIELD.equals(intent.getAction())) {
            intent.setAction(Intent.ACTION_MAIN);
            openFieldSetup();
        }
    }
}
