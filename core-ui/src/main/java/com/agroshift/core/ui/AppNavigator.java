package com.agroshift.core.ui;

/** Navigation actions shared by feature modules. */
public interface AppNavigator {
    void openCommandMenu();

    void openLanguage();

    void openFieldSetup();

    void openField(String fieldId);

    void openAdvisor(String fieldId);

    void openPlanStudio();

    void openAssistant();

    void openFarm();

    void openMarket();

    void openWater(String fieldId);
}
