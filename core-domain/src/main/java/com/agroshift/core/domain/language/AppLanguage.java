package com.agroshift.core.domain.language;

import java.util.List;
import java.util.Optional;

/** Registry entry. Adding a language here must not require feature code changes. */
public record AppLanguage(String tag, String nativeName, boolean rtl, ReviewStatus reviewStatus) {
    public enum ReviewStatus { SOURCE, MACHINE_DRAFT, NOT_TRANSLATED, NATIVE_REVIEWED }

    public static final List<AppLanguage> WAVE_ONE = List.of(
            new AppLanguage("ur-PK", "اردو", true, ReviewStatus.MACHINE_DRAFT),
            new AppLanguage("pa-Arab-PK", "پنجابی", true, ReviewStatus.NOT_TRANSLATED),
            new AppLanguage("sd-Arab-PK", "سنڌي", true, ReviewStatus.NOT_TRANSLATED),
            new AppLanguage("ps-PK", "پښتو", true, ReviewStatus.NOT_TRANSLATED),
            new AppLanguage("skr-Arab-PK", "سرائیکی", true, ReviewStatus.NOT_TRANSLATED),
            new AppLanguage("bal-Arab-PK", "بلوچی", true, ReviewStatus.NOT_TRANSLATED),
            new AppLanguage("en-PK", "English", false, ReviewStatus.SOURCE));

    public static Optional<AppLanguage> find(String tag) {
        return WAVE_ONE.stream().filter(language -> language.tag().equals(tag)).findFirst();
    }
}
