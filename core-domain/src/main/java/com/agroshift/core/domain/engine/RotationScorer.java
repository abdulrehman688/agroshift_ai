package com.agroshift.core.domain.engine;

import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import java.util.ArrayList;
import java.util.List;

/** Rotation suitability scoring, with severity ratios bounded to 0 through 1. */
public final class RotationScorer {
    private static final int HEAT_TOLERANT_MIN = 4;
    private static final double STANDARD_EQUIPMENT_PENALTY = 10;

    private RotationScorer() {
    }

    public static RotationScores score(RotationPlan plan, ClimateProfile profile, WaterAccess water,
            FarmGoal goal) {
        return score(plan, profile, water, goal, SoilType.UNKNOWN);
    }

    public static RotationScores score(RotationPlan plan, ClimateProfile profile, WaterAccess water,
            FarmGoal goal, SoilType soilType) {
        double waterFit = clamp100(waterFit(plan, profile, water));
        double climate = clamp100(climateRisk(plan, profile));
        double soil = clamp100(soilHealth(plan));
        double erosion = clamp100(erosion(plan, soilType));
        double pest = clamp100(pestBreak(plan));
        double income = clamp100(income(plan));

        double climateWeight = (goal.water + goal.soil) / 2;
        double totalWeight = goal.water + climateWeight + goal.soil + goal.erosion + goal.pest + goal.income;
        double overall = (waterFit * goal.water + climate * climateWeight + soil * goal.soil
                + erosion * goal.erosion + pest * goal.pest + income * goal.income) / totalWeight;
        overall -= equipmentPenalty(plan);
        return new RotationScores(round(clamp100(overall)), round(waterFit), round(climate),
                round(soil), round(erosion), round(pest), round(income));
    }

    private static double waterFit(RotationPlan plan, ClimateProfile profile, WaterAccess water) {
        List<Double> stress = new ArrayList<>();
        List<Double> tolerance = new ArrayList<>();
        for (RotationStep step : plan.steps()) {
            tolerance.add(scale(step.crop().droughtTolerance()));
            for (int month : step.months()) {
                stress.add(droughtSeverity(profile, month));
            }
        }
        double supply = 100 * (1 - average(stress) * water.stressFactor);
        double demand = 100 - clamp100(plan.annualWaterNeedMm() / 800 * 100);
        return 0.40 * supply + 0.30 * average(tolerance) + 0.30 * demand;
    }

    private static double droughtSeverity(ClimateProfile profile, int month) {
        double et0 = nanToZero(profile.value(Metric.ET0_MM, month));
        double deficit = nanToZero(profile.deficitMm(month));
        double ratio = et0 > 0 ? clamp(deficit / et0, 0, 1) : 0;
        double dry = clamp(nanToZero(profile.value(Metric.DRY_SPELL_PROB, month)), 0, 1);
        return 0.65 * ratio + 0.35 * dry;
    }

    private static double climateRisk(RotationPlan plan, ClimateProfile profile) {
        List<Double> risks = new ArrayList<>();
        for (RotationStep step : plan.steps()) {
            Crop crop = step.crop();
            List<Integer> months = step.months();
            for (int index = 0; index < months.size(); index++) {
                int month = months.get(index);
                GrowthStage stage = crop.calendar().stageFor(step, index);
                List<Double> monthRisks = new ArrayList<>();
                if (stage.sensitive().contains(Hazard.HEAT)) {
                    Metric metric = crop.heatTolerance() >= HEAT_TOLERANT_MIN
                            ? Metric.SEVERE_HEAT_DAYS : Metric.HEAT_DAYS;
                    double severity = clamp(nanToZero(profile.value(metric, month)) / 7, 0, 1);
                    monthRisks.add(severity * (6 - crop.heatTolerance()) / 5);
                }
                if (stage.sensitive().contains(Hazard.DROUGHT)) {
                    monthRisks.add(droughtSeverity(profile, month) * (6 - crop.droughtTolerance()) / 5);
                }
                if (stage.sensitive().contains(Hazard.FROST)) {
                    double severity = clamp(nanToZero(profile.value(Metric.FROST_DAYS, month)) / 7, 0, 1);
                    monthRisks.add(severity * (6 - crop.frostTolerance()) / 5);
                }
                if (!monthRisks.isEmpty()) {
                    risks.add(average(monthRisks));
                }
            }
        }
        return 100 * (1 - average(risks));
    }

    private static double soilHealth(RotationPlan plan) {
        List<Double> scores = new ArrayList<>();
        for (String slug : plan.uniqueCropSlugs()) {
            Crop crop = Crop.bySlug(slug);
            double root = switch (crop.rootDepth()) {
                case "shallow" -> 33;
                case "medium" -> 67;
                case "deep" -> 100;
                default -> 50;
            };
            scores.add(0.30 * scale(crop.groundCover()) + 0.30 * scale(crop.residue())
                    + 0.25 * (crop.nitrogenFixing() ? 100 : 0) + 0.15 * root);
        }
        return average(scores);
    }

    private static double erosion(RotationPlan plan, SoilType soilType) {
        List<Double> protection = new ArrayList<>();
        List<Double> cover = new ArrayList<>();
        for (String slug : plan.uniqueCropSlugs()) {
            Crop crop = Crop.bySlug(slug);
            protection.add(scale(crop.erosionProtection()));
            cover.add(scale(crop.groundCover()));
        }
        double bare = 0;
        for (double fraction : plan.bareFractionByMonth()) {
            bare += fraction;
        }
        double bareScore = clamp100(100 - 100 * (bare / 12) * soilType.erosionFactor);
        return 0.40 * average(protection) + 0.30 * average(cover) + 0.30 * bareScore;
    }

    private static double pestBreak(RotationPlan plan) {
        double diversity = clamp100(plan.families().size() / 4.0 * 100);
        List<RotationStep> ordered = new ArrayList<>(plan.steps());
        ordered.sort((a, b) -> a.year() != b.year()
                ? Integer.compare(a.year(), b.year()) : Integer.compare(a.sowMonth(), b.sowMonth()));
        double transitions = 0;
        if (ordered.size() > 1) {
            for (int i = 0; i < ordered.size(); i++) {
                RotationStep next = ordered.get((i + 1) % ordered.size());
                if (!ordered.get(i).crop().family().equals(next.crop().family())) {
                    transitions++;
                }
            }
            transitions = transitions / ordered.size() * 100;
        }
        return 0.65 * diversity + 0.35 * transitions;
    }

    private static double income(RotationPlan plan) {
        List<Double> scores = new ArrayList<>();
        for (String slug : plan.uniqueCropSlugs()) {
            Crop crop = Crop.bySlug(slug);
            double market = "cash".equals(crop.role()) || "forage".equals(crop.role()) ? 100 : 75;
            scores.add(0.75 * scale(crop.incomeLevel()) + 0.25 * market);
        }
        return average(scores);
    }

    private static double equipmentPenalty(RotationPlan plan) {
        int special = 0;
        for (RotationStep step : plan.steps()) {
            special += step.crop().specialEquipment() ? 1 : 0;
        }
        return (double) special / plan.steps().size() * STANDARD_EQUIPMENT_PENALTY;
    }

    private static double scale(int oneToFive) {
        return clamp100((oneToFive - 1) * 25.0);
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.size();
    }

    private static double nanToZero(double value) {
        return Double.isNaN(value) ? 0 : value;
    }

    private static double clamp100(double value) {
        return clamp(value, 0, 100);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
