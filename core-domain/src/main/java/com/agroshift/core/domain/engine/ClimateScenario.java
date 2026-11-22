package com.agroshift.core.domain.engine;

/** Simple What-If futures applied to the historical climate record. */
public enum ClimateScenario {
    NORMAL(0, 0),
    HOTTER(2, 0),
    LESS_RAIN(0, -30),
    HOTTER_AND_DRIER(2, -30);

    final double temperatureC;
    final double rainPercent;

    ClimateScenario(double temperatureC, double rainPercent) {
        this.temperatureC = temperatureC;
        this.rainPercent = rainPercent;
    }
}
