package com.agroshift.core.domain.season;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Where a date sits in Pakistan's agricultural year, from 1 October to 30 September. */
public record SeasonPosition(Season season, double yearFraction) {
    public enum Season { RABI, KHARIF }

    public static SeasonPosition of(LocalDate date) {
        int startYear = date.getMonthValue() >= 10 ? date.getYear() : date.getYear() - 1;
        LocalDate start = LocalDate.of(startYear, 10, 1);
        LocalDate end = start.plusYears(1);
        double fraction = (double) ChronoUnit.DAYS.between(start, date)
                / ChronoUnit.DAYS.between(start, end);
        int month = date.getMonthValue();
        Season season = month >= 10 || month <= 3 ? Season.RABI : Season.KHARIF;
        return new SeasonPosition(season, fraction);
    }
}
