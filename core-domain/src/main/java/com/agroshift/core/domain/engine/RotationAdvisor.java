package com.agroshift.core.domain.engine;

import java.util.ArrayList;
import java.util.List;

/** Compares the farmer's current rotation with water-saving and soil-building options. */
public final class RotationAdvisor {
    public enum Kind { CURRENT, WATER_SAVER, SOIL_BUILDER }

    /** Plain-language strength of an option. */
    public enum Fit { GOOD, OKAY, RISKY }

    /** The main benefit of the recommended option compared with the current one. */
    public enum Benefit { ALREADY_BEST, LESS_WATER, SAFER_CLIMATE, BETTER_SOIL, MORE_INCOME, BALANCED }

    public record Option(Kind kind, RotationPlan plan, RotationScores scores,
                         List<ClimateAlert> alerts) {
        public Option {
            alerts = List.copyOf(alerts);
        }

        public Fit fit() {
            return fitFor(scores.overall());
        }
    }

    public static Fit fitFor(double overall) {
        if (overall >= 70) {
            return Fit.GOOD;
        }
        return overall >= 50 ? Fit.OKAY : Fit.RISKY;
    }

    public record Advice(ClimateProfile profile, List<Option> options, Option best) {
        public Advice {
            options = List.copyOf(options);
        }

        public Option current() {
            return options.get(0);
        }

        public Benefit benefit() {
            if (best.kind() == Kind.CURRENT) {
                return Benefit.ALREADY_BEST;
            }
            RotationScores now = current().scores();
            RotationScores next = best.scores();
            double water = next.waterFit() - now.waterFit();
            double climate = next.climateRisk() - now.climateRisk();
            double soil = next.soilHealth() - now.soilHealth();
            double income = next.income() - now.income();
            double top = Math.max(water, Math.max(climate, Math.max(soil, income)));
            if (top <= 0) {
                return Benefit.BALANCED;
            }
            if (top == water) {
                return Benefit.LESS_WATER;
            }
            if (top == soil) {
                return Benefit.BETTER_SOIL;
            }
            return top == climate ? Benefit.SAFER_CLIMATE : Benefit.MORE_INCOME;
        }
    }

    private RotationAdvisor() {
    }

    public static Advice advise(ClimateProfile profile, String currentRotationKey, WaterAccess water,
            FarmGoal goal) {
        return advise(profile, currentRotationKey, water, goal, SoilType.UNKNOWN);
    }

    public static Advice advise(ClimateProfile profile, String currentRotationKey, WaterAccess water,
            FarmGoal goal, SoilType soil) {
        List<Option> options = new ArrayList<>(3);
        options.add(option(Kind.CURRENT, RotationPlan.template(currentRotationKey), profile, water, goal,
                soil));
        options.add(option(Kind.WATER_SAVER, RotationPlan.template(RotationPlan.WATER_SAVER), profile,
                water, goal, soil));
        options.add(option(Kind.SOIL_BUILDER, RotationPlan.template(RotationPlan.SOIL_BUILDER), profile,
                water, goal, soil));
        Option best = options.get(0);
        for (Option option : options) {
            if (option.scores().overall() > best.scores().overall()) {
                best = option;
            }
        }
        return new Advice(profile, options, best);
    }

    private static Option option(Kind kind, RotationPlan plan, ClimateProfile profile, WaterAccess water,
            FarmGoal goal, SoilType soil) {
        return new Option(kind, plan, RotationScorer.score(plan, profile, water, goal, soil),
                AlertEngine.build(plan, profile, water, soil));
    }
}
