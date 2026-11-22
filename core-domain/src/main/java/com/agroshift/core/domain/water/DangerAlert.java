package com.agroshift.core.domain.water;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A short-range weather danger, using AgroShift thresholds rather than an official warning. */
public record DangerAlert(Type type, Severity severity, LocalDate date, double value) {
    public enum Type { FROST, HEAT, HEAVY_RAIN, WIND }

    public enum Severity { WATCH, HIGH }

    private static final int WINDOW_DAYS = 3;
    private static final int LIKELY_RAIN_PERCENT = 50;

    public static List<DangerAlert> find(List<DayForecast> days, LocalDate today) {
        Map<Type, DangerAlert> worst = new EnumMap<>(Type.class);
        LocalDate end = today.plusDays(WINDOW_DAYS);
        for (DayForecast day : days) {
            if (day.date().isBefore(today) || !day.date().isBefore(end)) {
                continue;
            }
            if (day.minC() <= 2) {
                keep(worst, new DangerAlert(Type.FROST, day.minC() <= 0 ? Severity.HIGH : Severity.WATCH,
                        day.date(), day.minC()));
            }
            if (day.maxC() >= 42) {
                keep(worst, new DangerAlert(Type.HEAT, day.maxC() >= 45 ? Severity.HIGH : Severity.WATCH,
                        day.date(), day.maxC()));
            }
            if (day.rainMm() >= 30 && day.rainChance() >= LIKELY_RAIN_PERCENT) {
                keep(worst, new DangerAlert(Type.HEAVY_RAIN, day.rainMm() >= 60 ? Severity.HIGH : Severity.WATCH,
                        day.date(), day.rainMm()));
            }
            if (day.windKmh() >= 40) {
                keep(worst, new DangerAlert(Type.WIND, day.windKmh() >= 60 ? Severity.HIGH : Severity.WATCH,
                        day.date(), day.windKmh()));
            }
        }
        List<DangerAlert> alerts = new ArrayList<>(worst.values());
        alerts.sort(Comparator.comparing(DangerAlert::severity).reversed()
                .thenComparing(DangerAlert::date));
        return List.copyOf(alerts);
    }

    private static void keep(Map<Type, DangerAlert> worst, DangerAlert next) {
        DangerAlert current = worst.get(next.type());
        if (current == null || next.severity().compareTo(current.severity()) > 0
                || (next.severity() == current.severity() && next.date().isBefore(current.date()))) {
            worst.put(next.type(), next);
        }
    }
}
