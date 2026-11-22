package com.agroshift.core.domain.water;

import java.time.LocalDate;

/** Immutable daily weather values for one field. A weather code of -1 means it is unknown. */
public record DayForecast(LocalDate date, double rainMm, int rainChance, double et0Mm, double maxC,
                          double minC, double windKmh, int weatherCode) {
    public enum Kind { SUN, CLOUD, RAIN, STORM }

    public DayForecast(LocalDate date, double rainMm, int rainChance, double et0Mm, double maxC,
            double minC, double windKmh) {
        this(date, rainMm, rainChance, et0Mm, maxC, minC, windKmh, -1);
    }

    /** Groups WMO weather codes for icons. */
    public Kind kind() {
        if (weatherCode >= 95) {
            return Kind.STORM;
        }
        if ((weatherCode >= 51 && weatherCode <= 67) || (weatherCode >= 80 && weatherCode <= 82)) {
            return Kind.RAIN;
        }
        if (weatherCode == 0 || weatherCode == 1) {
            return Kind.SUN;
        }
        return weatherCode < 0 && rainChance >= 50 ? Kind.RAIN : Kind.CLOUD;
    }
}
