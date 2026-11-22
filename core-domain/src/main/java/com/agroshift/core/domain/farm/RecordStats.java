package com.agroshift.core.domain.farm;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Totals calculated only from the farmer's own saved records. */
public final class RecordStats {
    public record Summary(int openTasks, int doneTasks, double spent, double earned, int sales) {
        public static final Summary EMPTY = new Summary(0, 0, 0, 0, 0);

        public double profit() {
            return earned - spent;
        }

        public int totalTasks() {
            return openTasks + doneTasks;
        }

        Summary plus(FarmRecord record) {
            return switch (record.type()) {
                case TASK -> new Summary(openTasks + (record.done() ? 0 : 1), doneTasks + (record.done() ? 1 : 0),
                        spent, earned, sales);
                case EXPENSE -> new Summary(openTasks, doneTasks, spent + record.amountRupees(), earned, sales);
                case SALE -> new Summary(openTasks, doneTasks, spent, earned + record.amountRupees(), sales + 1);
                case NOTE -> this;
            };
        }
    }

    private RecordStats() {
    }

    public static Summary total(List<FarmRecord> records) {
        Summary summary = Summary.EMPTY;
        for (FarmRecord record : records) {
            summary = summary.plus(record);
        }
        return summary;
    }

    public static Map<String, Summary> byField(List<FarmRecord> records) {
        Map<String, Summary> result = new HashMap<>();
        for (FarmRecord record : records) {
            result.put(record.fieldId(), result.getOrDefault(record.fieldId(), Summary.EMPTY).plus(record));
        }
        return result;
    }

    public static List<FarmRecord> ofType(List<FarmRecord> records, FarmRecord.Type type) {
        if (type == null) {
            return List.copyOf(records);
        }
        List<FarmRecord> result = new ArrayList<>();
        for (FarmRecord record : records) {
            if (record.type() == type) {
                result.add(record);
            }
        }
        return result;
    }

    /** Sales per month, oldest first, ending with {@code last}. */
    public static double[] monthlySales(List<FarmRecord> records, YearMonth last, int months, ZoneId zone) {
        double[] totals = new double[months];
        YearMonth first = last.minusMonths(months - 1L);
        for (FarmRecord record : records) {
            if (record.type() != FarmRecord.Type.SALE) {
                continue;
            }
            YearMonth month = YearMonth.from(Instant.ofEpochMilli(record.dateMillis()).atZone(zone));
            if (!month.isBefore(first) && !month.isAfter(last)) {
                totals[(int) java.time.temporal.ChronoUnit.MONTHS.between(first, month)] += record.amountRupees();
            }
        }
        return totals;
    }

    /** Sale title with the highest total, ignoring letter case, or null when nothing is sold. */
    public static String topSale(List<FarmRecord> records) {
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        for (FarmRecord record : records) {
            if (record.type() == FarmRecord.Type.SALE) {
                String key = record.title().toLowerCase(Locale.ROOT);
                names.putIfAbsent(key, record.title());
                totals.merge(key, record.amountRupees(), Double::sum);
            }
        }
        String best = null;
        double highest = -1;
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            if (entry.getValue() > highest) {
                highest = entry.getValue();
                best = entry.getKey();
            }
        }
        return best == null ? null : names.get(best);
    }
}
