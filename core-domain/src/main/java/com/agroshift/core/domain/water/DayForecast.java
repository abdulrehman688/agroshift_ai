package com.agroshift.core.domain.water;

import java.time.LocalDate;

/** Immutable daily weather values for one field. */
public record DayForecast(LocalDate date, double rainMm, int rainChance, double et0Mm, double maxC,
                          double minC, double windKmh) {
}
