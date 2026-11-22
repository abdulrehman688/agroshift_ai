package com.agroshift.core.domain.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable abstract data type for a multi-year crop rotation.
 *
 * <p>Rep invariant: cycleYears is at least 1, steps is non-empty, and each step year is within the
 * cycle. Abstraction function: the crops occupying a field through a repeating cycle.
 */
public final class RotationPlan {
    public static final String COTTON_WHEAT = "cotton_wheat";
    public static final String RICE_WHEAT = "rice_wheat";
    public static final String WATER_SAVER = "water_saver";
    public static final String SOIL_BUILDER = "soil_builder";
    private static final String CUSTOM_PREFIX = "custom:";

    private static final Map<String, RotationPlan> TEMPLATES = Map.of(
            COTTON_WHEAT, new RotationPlan(COTTON_WHEAT, 1, List.of(
                    new RotationStep(1, "cotton", 5, 11),
                    new RotationStep(1, "wheat", 11, 4))),
            RICE_WHEAT, new RotationPlan(RICE_WHEAT, 1, List.of(
                    new RotationStep(1, "rice", 7, 11),
                    new RotationStep(1, "wheat", 11, 4))),
            WATER_SAVER, new RotationPlan(WATER_SAVER, 2, List.of(
                    new RotationStep(1, "sorghum", 7, 10),
                    new RotationStep(1, "chickpea", 11, 4),
                    new RotationStep(2, "cotton", 5, 11),
                    new RotationStep(2, "wheat", 11, 4))),
            SOIL_BUILDER, new RotationPlan(SOIL_BUILDER, 2, List.of(
                    new RotationStep(1, "cotton", 5, 11),
                    new RotationStep(1, "berseem", 11, 4),
                    new RotationStep(2, "mungbean", 7, 10),
                    new RotationStep(2, "wheat", 11, 4))));

    private final String key;
    private final int cycleYears;
    private final List<RotationStep> steps;

    public RotationPlan(String key, int cycleYears, List<RotationStep> steps) {
        if (cycleYears < 1 || steps.isEmpty()) {
            throw new IllegalArgumentException("A rotation needs a cycle and steps");
        }
        for (RotationStep step : steps) {
            if (step.year() > cycleYears) {
                throw new IllegalArgumentException("Step outside rotation cycle");
            }
        }
        this.key = key;
        this.cycleYears = cycleYears;
        this.steps = List.copyOf(steps);
    }

    public static String customKey(String kharifCrop, String rabiCrop) {
        return CUSTOM_PREFIX + kharifCrop + ":" + rabiCrop;
    }

    /** Builds a one-year plan from typical calendars, keeping the two crops from overlapping. */
    private static RotationPlan custom(String key) {
        String[] parts = key.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Unknown rotation: " + key);
        }
        if (Crop.YEAR_ROUND.equals(parts[1])) {
            CropCalendar calendar = Crop.bySlug(Crop.YEAR_ROUND).calendar();
            return new RotationPlan(key, 1, List.of(new RotationStep(1, Crop.YEAR_ROUND,
                    calendar.sowMonth(), calendar.harvestMonth())));
        }
        if (!Crop.KHARIF.contains(parts[1]) || !Crop.RABI.contains(parts[2])) {
            throw new IllegalArgumentException("Unknown rotation: " + key);
        }
        CropCalendar kharif = Crop.bySlug(parts[1]).calendar();
        CropCalendar rabi = Crop.bySlug(parts[2]).calendar();
        int rabiSow = Math.max(rabi.sowMonth(), kharif.harvestMonth());
        return new RotationPlan(key, 1, List.of(
                new RotationStep(1, parts[1], kharif.sowMonth(), kharif.harvestMonth()),
                new RotationStep(1, parts[2], rabiSow, rabi.harvestMonth())));
    }

    public static RotationPlan template(String key) {
        if (key != null && key.startsWith(CUSTOM_PREFIX)) {
            return custom(key);
        }
        RotationPlan plan = TEMPLATES.get(key);
        if (plan == null) {
            throw new IllegalArgumentException("Unknown rotation: " + key);
        }
        return plan;
    }

    public String key() {
        return key;
    }

    public int cycleYears() {
        return cycleYears;
    }

    public List<RotationStep> steps() {
        return steps;
    }

    public double annualWaterNeedMm() {
        double total = 0;
        for (RotationStep step : steps) {
            total += step.crop().waterNeedMm();
        }
        return total / cycleYears;
    }

    public Set<String> families() {
        Set<String> families = new TreeSet<>();
        for (RotationStep step : steps) {
            families.add(step.crop().family());
        }
        return families;
    }

    public List<String> uniqueCropSlugs() {
        List<String> slugs = new ArrayList<>();
        for (RotationStep step : steps) {
            if (!slugs.contains(step.cropSlug())) {
                slugs.add(step.cropSlug());
            }
        }
        return slugs;
    }

    /** Share of cycle years each calendar month is bare. Index 0 is January. */
    public double[] bareFractionByMonth() {
        int total = 12 * cycleYears;
        boolean[] occupied = new boolean[total];
        for (RotationStep step : steps) {
            int start = (step.year() - 1) * 12 + step.sowMonth() - 1;
            int length = CropCalendar.seasonLength(step.sowMonth(), step.harvestMonth());
            for (int k = 0; k < length; k++) {
                occupied[(start + k) % total] = true;
            }
        }
        double[] fractions = new double[12];
        for (int month = 0; month < 12; month++) {
            int bare = 0;
            for (int year = 0; year < cycleYears; year++) {
                if (!occupied[year * 12 + month]) {
                    bare++;
                }
            }
            fractions[month] = (double) bare / cycleYears;
        }
        return fractions;
    }
}
