package com.agroshift.core.domain.water;

/** Current weather used to draw the live sky. */
public record SkyState(boolean day, int cloudPercent, double rainMmPerHour, double windKmh, double tempC) {
    public enum Condition { HEAVY_RAIN, RAIN, HOT, WINDY, CLOUDY, CLEAR }

    public Condition condition() {
        if (rainMmPerHour >= 4) {
            return Condition.HEAVY_RAIN;
        }
        if (rainMmPerHour >= 0.1) {
            return Condition.RAIN;
        }
        if (tempC >= 38) {
            return Condition.HOT;
        }
        if (windKmh >= 25) {
            return Condition.WINDY;
        }
        return cloudPercent >= 60 ? Condition.CLOUDY : Condition.CLEAR;
    }
}
