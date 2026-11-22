package com.agroshift.core.domain.engine;

import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Turns daily NASA POWER records into monthly climate facts. */
public final class ClimateEngine {
    static final double HEAT_DAY_C = 35;
    static final double SEVERE_HEAT_DAY_C = 40;
    static final double FROST_DAY_C = 0;
    static final double WET_DAY_MM = 1;
    static final int DRY_SPELL_DAYS = 10;
    private static final int RECENT_YEARS = 5;
    private static final double WETNESS_PER_MM = 0.001;

    private ClimateEngine() {
    }

    /** Reference evapotranspiration in mm/day using Hargreaves-Samani (FAO-56 eq. 52). */
    public static double et0(DailyClimate day, double latitude) {
        double range = Math.max(0, day.tMax() - day.tMin());
        double value = 0.0023 * 0.408 * extraterrestrialRadiation(latitude, day.date().getDayOfYear())
                * (day.tMean() + 17.8) * Math.sqrt(range);
        return Double.isNaN(value) ? Double.NaN : Math.max(0, value);
    }

    static double extraterrestrialRadiation(double latitude, int dayOfYear) {
        double phi = Math.toRadians(latitude);
        double angle = 2 * Math.PI * dayOfYear / 365;
        double dr = 1 + 0.033 * Math.cos(angle);
        double delta = 0.409 * Math.sin(angle - 1.39);
        double ws = Math.acos(Math.max(-1, Math.min(1, -Math.tan(phi) * Math.tan(delta))));
        return (24 * 60 / Math.PI) * 0.0820 * dr
                * (ws * Math.sin(phi) * Math.sin(delta)
                + Math.cos(phi) * Math.cos(delta) * Math.sin(ws));
    }

    public static ClimateProfile build(List<DailyClimate> input, double latitude) {
        if (input.isEmpty()) {
            throw new IllegalArgumentException("No climate data");
        }
        List<DailyClimate> days = sorted(input);
        double threshold = extremeRainThreshold(days);
        Map<Integer, MonthBucket[]> years = buckets(days, latitude, threshold);
        return summarize(days, years, years);
    }

    /** Applies a What-If while keeping "unusual" thresholds tied to the real historical climate. */
    public static ClimateProfile buildScenario(List<DailyClimate> input, double latitude,
            ClimateScenario scenario) {
        if (scenario == ClimateScenario.NORMAL) {
            return build(input, latitude);
        }
        if (input.isEmpty()) {
            throw new IllegalArgumentException("No climate data");
        }
        List<DailyClimate> base = sorted(input);
        double threshold = extremeRainThreshold(base);
        Map<Integer, MonthBucket[]> reference = buckets(base, latitude, threshold);
        List<DailyClimate> shifted = applyScenario(base, latitude, scenario);
        return summarize(shifted, buckets(shifted, latitude, threshold), reference);
    }

    private static List<DailyClimate> applyScenario(List<DailyClimate> base, double latitude,
            ClimateScenario scenario) {
        double rainFactor = 1 + scenario.rainPercent / 100;
        List<DailyClimate> warmed = new ArrayList<>(base.size());
        Map<Long, Double> monthlyBalance = new HashMap<>();
        for (DailyClimate day : base) {
            DailyClimate next = new DailyClimate(day.date(), day.tMean() + scenario.temperatureC,
                    day.tMax() + scenario.temperatureC, day.tMin() + scenario.temperatureC,
                    day.precipMm() * rainFactor, day.rootWetness());
            warmed.add(next);
            double change = (next.precipMm() - day.precipMm()) - (et0(next, latitude) - et0(day, latitude));
            if (!Double.isNaN(change)) {
                monthlyBalance.merge(key(day.date().getYear(), day.date().getMonthValue()), change,
                        Double::sum);
            }
        }
        List<DailyClimate> result = new ArrayList<>(warmed.size());
        for (DailyClimate day : warmed) {
            double balance = monthlyBalance.getOrDefault(
                    key(day.date().getYear(), day.date().getMonthValue()), 0.0);
            double wetness = Math.max(0, Math.min(1, day.rootWetness() + balance * WETNESS_PER_MM));
            result.add(new DailyClimate(day.date(), day.tMean(), day.tMax(), day.tMin(), day.precipMm(),
                    Double.isNaN(day.rootWetness()) ? Double.NaN : wetness));
        }
        return result;
    }

    private static List<DailyClimate> sorted(List<DailyClimate> input) {
        List<DailyClimate> days = new ArrayList<>(input);
        days.sort(Comparator.comparing(DailyClimate::date));
        return days;
    }

    private static Map<Integer, MonthBucket[]> buckets(List<DailyClimate> days, double latitude,
            double extremeThreshold) {
        Map<Integer, MonthBucket[]> years = new TreeMap<>();
        for (DailyClimate day : days) {
            MonthBucket[] buckets = years.computeIfAbsent(day.date().getYear(), y -> newBuckets());
            buckets[day.date().getMonthValue() - 1].add(day, et0(day, latitude), extremeThreshold);
        }
        return years;
    }

    private static ClimateProfile summarize(List<DailyClimate> days, Map<Integer, MonthBucket[]> years,
            Map<Integer, MonthBucket[]> reference) {
        int missing = 0;
        for (DailyClimate day : days) {
            missing += count(day.tMean()) + count(day.tMax()) + count(day.tMin()) + count(day.precipMm());
        }
        Set<Long> dryMonths = dryMonths(days);
        Map<Metric, double[]> values = new EnumMap<>(Metric.class);
        values.put(Metric.T_MAX, average(years, MonthBucket::meanTMax));
        values.put(Metric.T_MIN, average(years, MonthBucket::meanTMin));
        values.put(Metric.PRECIP_MM, average(years, MonthBucket::rain));
        values.put(Metric.ET0_MM, average(years, MonthBucket::et0));
        values.put(Metric.HEAT_DAYS, average(years, b -> b.present() ? b.heat : Double.NaN));
        values.put(Metric.SEVERE_HEAT_DAYS, average(years, b -> b.present() ? b.severeHeat : Double.NaN));
        values.put(Metric.FROST_DAYS, average(years, b -> b.present() ? b.frost : Double.NaN));
        values.put(Metric.EXTREME_RAIN_DAYS, average(years, b -> b.present() ? b.extreme : Double.NaN));
        double[] dry = new double[12];
        for (int month = 1; month <= 12; month++) {
            int hits = 0;
            for (int year : years.keySet()) {
                if (dryMonths.contains(key(year, month))) {
                    hits++;
                }
            }
            dry[month - 1] = (double) hits / years.size();
        }
        values.put(Metric.DRY_SPELL_PROB, dry);
        values.put(Metric.SOIL_ANOMALY, soilAnomaly(years, reference));

        int first = ((TreeMap<Integer, MonthBucket[]>) years).firstKey();
        int last = ((TreeMap<Integer, MonthBucket[]>) years).lastKey();
        return new ClimateProfile(first, last, years.size(), (double) missing / (days.size() * 4), values);
    }

    private static int count(double value) {
        return Double.isNaN(value) ? 1 : 0;
    }

    private static MonthBucket[] newBuckets() {
        MonthBucket[] buckets = new MonthBucket[12];
        for (int i = 0; i < 12; i++) {
            buckets[i] = new MonthBucket();
        }
        return buckets;
    }

    private static double extremeRainThreshold(List<DailyClimate> days) {
        double[] wet = days.stream().mapToDouble(DailyClimate::precipMm)
                .filter(rain -> rain >= WET_DAY_MM).sorted().toArray();
        return wet.length < 20 ? Double.POSITIVE_INFINITY : percentile(wet, 95);
    }

    /** Linear-interpolated percentile of a sorted array. */
    static double percentile(double[] sorted, double percent) {
        double position = (sorted.length - 1) * percent / 100;
        int low = (int) Math.floor(position);
        int high = Math.min(low + 1, sorted.length - 1);
        return sorted[low] + (sorted[high] - sorted[low]) * (position - low);
    }

    private static Set<Long> dryMonths(List<DailyClimate> days) {
        Set<Long> touched = new HashSet<>();
        int start = -1;
        for (int i = 0; i <= days.size(); i++) {
            boolean isDry = i < days.size() && days.get(i).precipMm() < WET_DAY_MM;
            if (isDry && start < 0) {
                start = i;
            } else if (!isDry && start >= 0) {
                if (i - start >= DRY_SPELL_DAYS) {
                    for (int d = start; d < i; d++) {
                        touched.add(key(days.get(d).date().getYear(), days.get(d).date().getMonthValue()));
                    }
                }
                start = -1;
            }
        }
        return touched;
    }

    private static long key(int year, int month) {
        return year * 100L + month;
    }

    private interface BucketValue {
        double of(MonthBucket bucket);
    }

    private static double[] average(Map<Integer, MonthBucket[]> years, BucketValue reader) {
        double[] result = new double[12];
        for (int month = 0; month < 12; month++) {
            double total = 0;
            int count = 0;
            for (MonthBucket[] buckets : years.values()) {
                double value = reader.of(buckets[month]);
                if (!Double.isNaN(value)) {
                    total += value;
                    count++;
                }
            }
            result[month] = count == 0 ? Double.NaN : total / count;
        }
        return result;
    }

    private static double[] soilAnomaly(Map<Integer, MonthBucket[]> years,
            Map<Integer, MonthBucket[]> reference) {
        TreeSet<Integer> ordered = new TreeSet<>(years.keySet());
        int recentCount = Math.max(1, Math.min(RECENT_YEARS, ordered.size() / 2));
        int cutoff = ordered.last() - recentCount;
        double[] anomaly = new double[12];
        Arrays.fill(anomaly, Double.NaN);
        for (int month = 0; month < 12; month++) {
            List<Double> all = new ArrayList<>();
            List<Double> recent = new ArrayList<>();
            for (MonthBucket[] buckets : reference.values()) {
                double value = buckets[month].meanWetness();
                if (!Double.isNaN(value)) {
                    all.add(value);
                }
            }
            for (Map.Entry<Integer, MonthBucket[]> entry : years.entrySet()) {
                double value = entry.getValue()[month].meanWetness();
                if (!Double.isNaN(value) && entry.getKey() > cutoff) {
                    recent.add(value);
                }
            }
            if (all.size() < 2 || recent.isEmpty()) {
                continue;
            }
            double mean = mean(all);
            double variance = 0;
            for (double value : all) {
                variance += (value - mean) * (value - mean);
            }
            double sd = Math.sqrt(variance / (all.size() - 1));
            if (sd > 0) {
                anomaly[month] = (mean(recent) - mean) / sd;
            }
        }
        return anomaly;
    }

    private static double mean(List<Double> values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.size();
    }

    /** Mutable accumulator confined to a single build call. */
    private static final class MonthBucket {
        private final Stat tMax = new Stat();
        private final Stat tMin = new Stat();
        private final Stat wetness = new Stat();
        private double rainTotal;
        private int rainCount;
        private double et0Total;
        private int et0Count;
        private int days;
        int heat;
        int severeHeat;
        int frost;
        int extreme;

        void add(DailyClimate day, double et0, double extremeThreshold) {
            days++;
            tMax.add(day.tMax());
            tMin.add(day.tMin());
            wetness.add(day.rootWetness());
            if (!Double.isNaN(day.precipMm())) {
                rainTotal += day.precipMm();
                rainCount++;
            }
            if (!Double.isNaN(et0)) {
                et0Total += et0;
                et0Count++;
            }
            heat += day.tMax() > HEAT_DAY_C ? 1 : 0;
            severeHeat += day.tMax() > SEVERE_HEAT_DAY_C ? 1 : 0;
            frost += day.tMin() <= FROST_DAY_C ? 1 : 0;
            extreme += day.precipMm() > extremeThreshold ? 1 : 0;
        }

        boolean present() {
            return days > 0;
        }

        double meanTMax() {
            return tMax.mean();
        }

        double meanTMin() {
            return tMin.mean();
        }

        double meanWetness() {
            return wetness.mean();
        }

        double rain() {
            return rainCount == 0 ? Double.NaN : rainTotal;
        }

        double et0() {
            return et0Count == 0 ? Double.NaN : et0Total;
        }
    }

    private static final class Stat {
        private double total;
        private int count;

        void add(double value) {
            if (!Double.isNaN(value)) {
                total += value;
                count++;
            }
        }

        double mean() {
            return count == 0 ? Double.NaN : total / count;
        }
    }
}
