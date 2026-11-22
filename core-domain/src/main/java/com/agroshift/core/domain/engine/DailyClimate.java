package com.agroshift.core.domain.engine;

import java.time.LocalDate;

/** One day of NASA POWER data. Missing values are NaN. */
public record DailyClimate(LocalDate date, double tMean, double tMax, double tMin, double precipMm,
                           double rootWetness) {
    public static double clean(Double value) {
        return value == null || value <= -998.5 ? Double.NaN : value;
    }
}
