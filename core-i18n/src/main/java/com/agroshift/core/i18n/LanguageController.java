package com.agroshift.core.i18n;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.agroshift.core.domain.language.AppLanguage;

public final class LanguageController {
    private static final String PREFS = "agroshift_language";
    private static final String KEY_TAG = "selected_tag";
    private static final String KEY_ONBOARDING = "onboarding_seen";

    private final SharedPreferences prefs;

    public LanguageController(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasChosenLanguage() {
        return prefs.contains(KEY_TAG);
    }

    public boolean hasSeenOnboarding() {
        return prefs.getBoolean(KEY_ONBOARDING, false);
    }

    public void markOnboardingSeen() {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply();
    }

    public String selectedTag() {
        return prefs.getString(KEY_TAG, "ur-PK");
    }

    public void choose(AppLanguage language) {
        prefs.edit().putString(KEY_TAG, language.tag()).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag()));
    }
}
