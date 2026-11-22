package com.agroshift.core.domain.water;

import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estimates whether a field needs water from a simple daily soil-water balance. Reference
 * evapotranspiration is used without a crop coefficient, so results are guidance only.
 */
public final class IrrigationAdvisor {
    public enum Decision { IRRIGATE, WAIT_FOR_RAIN, SOON, NOT_NEEDED, RAINFED, NEED_LAST_WATERING, NO_DATA }

    public record Answer(Decision decision, double usedMm, double limitMm, int daysUntilNeeded,
                         LocalDate rainDate) {
    }

    static final double EFFECTIVE_RAIN = 0.8;
    static final int LIKELY_RAIN_PERCENT = 60;
    static final int RAIN_WINDOW_DAYS = 3;
    static final int SOON_DAYS = 2;
    static final int MAX_HISTORY_DAYS = 30;
    static final double MIN_USEFUL_RAIN_MM = 15;

    private IrrigationAdvisor() {
    }

    public static Answer decide(List<DayForecast> days, LocalDate lastWatered, LocalDate today,
            WaterAccess access, SoilType soil) {
        double limit = limitMm(soil);
        if (access == WaterAccess.RAINFED) {
            return answer(Decision.RAINFED, 0, limit, -1, null);
        }
        if (lastWatered == null || lastWatered.isAfter(today)
                || lastWatered.isBefore(today.minusDays(MAX_HISTORY_DAYS))) {
            return answer(Decision.NEED_LAST_WATERING, 0, limit, -1, null);
        }
        Map<LocalDate, DayForecast> byDate = new HashMap<>();
        for (DayForecast day : days) {
            byDate.put(day.date(), day);
        }
        if (!byDate.containsKey(today)) {
            return answer(Decision.NO_DATA, 0, limit, -1, null);
        }
        double used = 0;
        for (LocalDate date = lastWatered.plusDays(1); date.isBefore(today); date = date.plusDays(1)) {
            DayForecast day = byDate.get(date);
            if (day == null) {
                return answer(Decision.NO_DATA, 0, limit, -1, null);
            }
            used = Math.max(0, used + day.et0Mm() - day.rainMm() * EFFECTIVE_RAIN);
        }

        double expectedRain = 0;
        LocalDate rainDate = null;
        for (int i = 0; i < RAIN_WINDOW_DAYS; i++) {
            DayForecast day = byDate.get(today.plusDays(i));
            if (day != null && day.rainChance() >= LIKELY_RAIN_PERCENT && day.rainMm() > 0) {
                expectedRain += day.rainMm() * EFFECTIVE_RAIN;
                if (rainDate == null) {
                    rainDate = day.date();
                }
            }
        }
        if (used >= limit) {
            boolean useful = expectedRain >= Math.min(used * 0.5, MIN_USEFUL_RAIN_MM);
            return answer(useful ? Decision.WAIT_FOR_RAIN : Decision.IRRIGATE, used, limit, 0,
                    useful ? rainDate : null);
        }
        int until = daysUntilLimit(byDate, today, used, limit);
        boolean soon = until > 0 && until <= SOON_DAYS;
        return answer(soon ? Decision.SOON : Decision.NOT_NEEDED, used, limit, until, null);
    }

    private static int daysUntilLimit(Map<LocalDate, DayForecast> byDate, LocalDate today, double used,
            double limit) {
        double projected = used;
        for (int i = 0; byDate.containsKey(today.plusDays(i)); i++) {
            DayForecast day = byDate.get(today.plusDays(i));
            double rain = day.rainChance() >= LIKELY_RAIN_PERCENT ? day.rainMm() * EFFECTIVE_RAIN : 0;
            projected = Math.max(0, projected + day.et0Mm() - rain);
            if (projected >= limit) {
                return i + 1;
            }
        }
        return -1;
    }

    static double limitMm(SoilType soil) {
        return switch (soil) {
            case SANDY -> 25;
            case LOAM -> 45;
            case SILT -> 50;
            case CLAY -> 55;
            case UNKNOWN -> 40;
        };
    }

    private static Answer answer(Decision decision, double used, double limit, int until, LocalDate rainDate) {
        return new Answer(decision, used, limit, until, rainDate);
    }
}
