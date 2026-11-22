package com.agroshift.core.domain.engine;

import java.util.EnumMap;
import java.util.Map;

/** Immutable monthly climate summary. Month arguments are 1 to 12. */
public final class ClimateProfile {
    public enum Metric {
        T_MAX, T_MIN, PRECIP_MM, ET0_MM, HEAT_DAYS, SEVERE_HEAT_DAYS, FROST_DAYS, DRY_SPELL_PROB,
        EXTREME_RAIN_DAYS, SOIL_ANOMALY
    }

    private final int startYear;
    private final int endYear;
    private final int yearCount;
    private final double missingFraction;
    private final Map<Metric, double[]> values;

    public ClimateProfile(int startYear, int endYear, int yearCount, double missingFraction,
            Map<Metric, double[]> values) {
        EnumMap<Metric, double[]> copy = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            double[] monthly = values.get(metric);
            if (monthly == null || monthly.length != 12) {
                throw new IllegalArgumentException("Missing monthly values for " + metric);
            }
            copy.put(metric, monthly.clone());
        }
        this.startYear = startYear;
        this.endYear = endYear;
        this.yearCount = yearCount;
        this.missingFraction = missingFraction;
        this.values = copy;
    }

    public int startYear() {
        return startYear;
    }

    public int endYear() {
        return endYear;
    }

    public int yearCount() {
        return yearCount;
    }

    public double missingFraction() {
        return missingFraction;
    }

    public double value(Metric metric, int month) {
        return values.get(metric)[month - 1];
    }

    public double[] series(Metric metric) {
        return values.get(metric).clone();
    }

    public double deficitMm(int month) {
        double rain = value(Metric.PRECIP_MM, month);
        double et0 = value(Metric.ET0_MM, month);
        return Double.isNaN(rain) || Double.isNaN(et0) ? Double.NaN : Math.max(0, et0 - rain);
    }

    public double annualRainMm() {
        double total = 0;
        for (double rain : values.get(Metric.PRECIP_MM)) {
            if (Double.isNaN(rain)) {
                return Double.NaN;
            }
            total += rain;
        }
        return total;
    }
}
