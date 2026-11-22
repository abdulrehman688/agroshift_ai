package com.agroshift.core.domain.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/** Ranks, filters and compares every crop plan with the same scoring engine used by Advisor. */
public final class PlanStudio {
    /** What the farmer wants plans sorted by. */
    public enum Lens {
        BEST(RotationScores::overall),
        LESS_WATER(RotationScores::waterFit),
        SAFE_CLIMATE(RotationScores::climateRisk),
        SOIL(RotationScores::soilHealth),
        EROSION(RotationScores::erosion),
        PEST(RotationScores::pestBreak),
        INCOME(RotationScores::income);

        private final ToDoubleFunction<RotationScores> reader;

        Lens(ToDoubleFunction<RotationScores> reader) {
            this.reader = reader;
        }

        public double value(RotationScores scores) {
            return reader.applyAsDouble(scores);
        }
    }

    public enum Winner { FIRST, SECOND, TIE }

    public record Entry(RotationPlan plan, RotationScores scores) {
        public RotationAdvisor.Fit fit() {
            return RotationAdvisor.fitFor(scores.overall());
        }
    }

    /** A continuous stretch of months for one crop, inside one calendar year of the cycle. */
    public record Block(int year, int startMonth, int length, String cropSlug) {
    }

    public record Stress(Map<ClimateScenario, Double> scores, double worstDrop) {
        public Stress {
            scores = Collections.unmodifiableMap(new EnumMap<>(scores));
        }
    }

    private static final List<RotationPlan> CATALOG = buildCatalog();

    private PlanStudio() {
    }

    private static List<RotationPlan> buildCatalog() {
        List<RotationPlan> plans = new ArrayList<>();
        for (String kharif : Crop.KHARIF) {
            for (String rabi : Crop.RABI) {
                plans.add(build(kharif, rabi));
            }
        }
        plans.add(RotationPlan.template(RotationPlan.customKey(Crop.YEAR_ROUND, Crop.YEAR_ROUND)));
        plans.add(RotationPlan.template(RotationPlan.WATER_SAVER));
        plans.add(RotationPlan.template(RotationPlan.SOIL_BUILDER));
        return List.copyOf(plans);
    }

    public static List<RotationPlan> catalog() {
        return CATALOG;
    }

    /** Builds a one-year plan from one Kharif crop and one Rabi crop. */
    public static RotationPlan build(String kharif, String rabi) {
        if (!Crop.KHARIF.contains(kharif) || !Crop.RABI.contains(rabi)) {
            throw new IllegalArgumentException("Choose one Kharif crop and one Rabi crop");
        }
        if ("wheat".equals(rabi) && "cotton".equals(kharif)) {
            return RotationPlan.template(RotationPlan.COTTON_WHEAT);
        }
        if ("wheat".equals(rabi) && "rice".equals(kharif)) {
            return RotationPlan.template(RotationPlan.RICE_WHEAT);
        }
        return RotationPlan.template(RotationPlan.customKey(kharif, rabi));
    }

    public static List<Entry> rank(ClimateProfile profile, WaterAccess water, FarmGoal goal, SoilType soil, Lens lens) {
        List<Entry> entries = new ArrayList<>(CATALOG.size());
        for (RotationPlan plan : CATALOG) {
            entries.add(new Entry(plan, RotationScorer.score(plan, profile, water, goal, soil)));
        }
        entries.sort(Comparator.<Entry>comparingDouble(entry -> lens.value(entry.scores())).reversed()
                .thenComparing(Comparator.<Entry>comparingDouble(entry -> entry.scores().overall()).reversed())
                .thenComparing(entry -> entry.plan().key()));
        return List.copyOf(entries);
    }

    /** Moves saved plans to the top without changing the order inside each group. */
    public static List<Entry> favoritesFirst(List<Entry> ranked, Set<String> favorites) {
        List<Entry> saved = new ArrayList<>();
        List<Entry> others = new ArrayList<>();
        for (Entry entry : ranked) {
            (favorites.contains(entry.plan().key()) ? saved : others).add(entry);
        }
        saved.addAll(others);
        return List.copyOf(saved);
    }

    /** True when a stored rotation key means the same crops, even if saved as a custom key. */
    public static boolean isSame(RotationPlan plan, String rotationKey) {
        try {
            RotationPlan other = RotationPlan.template(rotationKey);
            return plan.cycleYears() == other.cycleYears() && plan.uniqueCropSlugs().equals(other.uniqueCropSlugs());
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    public static List<Block> yearBlocks(RotationPlan plan) {
        int total = plan.cycleYears() * 12;
        List<Block> blocks = new ArrayList<>();
        for (RotationStep step : plan.steps()) {
            int start = (step.year() - 1) * 12 + step.sowMonth() - 1;
            int length = step.months().size();
            int done = 0;
            while (done < length) {
                int position = (start + done) % total;
                int inYear = Math.min(length - done, 12 - position % 12);
                blocks.add(new Block(position / 12 + 1, position % 12 + 1, inYear, step.cropSlug()));
                done += inYear;
            }
        }
        blocks.sort(Comparator.comparingInt(Block::year).thenComparingInt(Block::startMonth));
        return List.copyOf(blocks);
    }

    public static Stress stress(RotationPlan plan, Map<ClimateScenario, ClimateProfile> profiles, WaterAccess water,
            FarmGoal goal, SoilType soil) {
        Map<ClimateScenario, Double> scores = new EnumMap<>(ClimateScenario.class);
        for (Map.Entry<ClimateScenario, ClimateProfile> future : profiles.entrySet()) {
            scores.put(future.getKey(), RotationScorer.score(plan, future.getValue(), water, goal, soil).overall());
        }
        double normal = scores.getOrDefault(ClimateScenario.NORMAL, 0.0);
        double lowest = normal;
        for (double score : scores.values()) {
            lowest = Math.min(lowest, score);
        }
        return new Stress(scores, Math.max(0, normal - lowest));
    }

    public static Winner winner(Lens lens, Entry first, Entry second) {
        int order = Double.compare(lens.value(first.scores()), lens.value(second.scores()));
        return order == 0 ? Winner.TIE : order > 0 ? Winner.FIRST : Winner.SECOND;
    }
}
