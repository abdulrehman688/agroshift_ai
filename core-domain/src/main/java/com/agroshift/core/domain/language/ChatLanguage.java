package com.agroshift.core.domain.language;

import java.util.Locale;

/** Chatbot languages and how each one is requested and spoken. */
public enum ChatLanguage {
    ENGLISH("en", "English", "en", null, "en-US", true, false),
    URDU("ur", "اردو", "ur", null, "ur-PK", true, false),
    PUNJABI("pa", "پنجابی", "pa", null, "pa-PK", true, false),
    SINDHI("sd", "سنڌي", "sd", null, "sd-PK", true, false),
    SARAIKI("skr", "سرائیکی", "pa", "Saraiki in Perso-Arabic script", "skr-PK", true, true),
    PASHTO("ps", "پښتو", "en", null, "ps-AF", false, false),
    BALOCHI("bal", "بلوچی", "en", null, "bal-PK", false, false),
    HINDKO("hno", "ہندکو", "en", null, "hno-PK", false, false),
    BRAHUI("brh", "براہوی", "en", null, "brh-PK", false, false);

    private final String code;
    private final String nativeName;
    private final String serviceCode;
    private final String instruction;
    private final String speechTag;
    private final boolean available;
    private final boolean beta;

    ChatLanguage(String code, String nativeName, String serviceCode, String instruction, String speechTag,
            boolean available, boolean beta) {
        this.code = code;
        this.nativeName = nativeName;
        this.serviceCode = serviceCode;
        this.instruction = instruction;
        this.speechTag = speechTag;
        this.available = available;
        this.beta = beta;
    }

    public String code() {
        return code;
    }

    public String nativeName() {
        return nativeName;
    }

    public String serviceCode() {
        return serviceCode;
    }

    public String instruction() {
        return instruction;
    }

    public String speechTag() {
        return speechTag;
    }

    public boolean available() {
        return available;
    }

    public boolean beta() {
        return beta;
    }

    public boolean usesArabicScript() {
        return this != ENGLISH;
    }

    public static ChatLanguage fromCode(String code) {
        for (ChatLanguage language : values()) {
            if (language.code.equals(code) && language.available) {
                return language;
            }
        }
        return null;
    }

    public static ChatLanguage forLocale(Locale locale) {
        ChatLanguage language = fromCode(locale.getLanguage());
        return language == null ? ENGLISH : language;
    }
}
