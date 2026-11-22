package com.agroshift.core.domain.engine;

import com.agroshift.core.domain.engine.ClimateAlert.Severity;
import com.agroshift.core.domain.engine.ClimateAlert.Type;
import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import java.util.List;

/** Finds historical heat, drought, frost and water risks for a rotation's crop stages. */
public final class AlertEngine {
    private static final double[] DROUGHT = {0.30, 0.50, 0.70};
    private static final double[] DEFICIT = {30, 60, 100};
    private static final double[] HEAT = {3, 7, 12};
    private static final double[] FROST = {1, 3, 7};
    private static final double[] WATERLOG = {1, 1.5, 2.5};
    private static final double[] SOIL = {-1, -1.5, -2};
    private static final int HEAT_TOLERANT_MIN = 4;
    private static final int FROST_HARDY_MIN = 4;
    private static final int NON_SENSITIVE_DOWNGRADE = 2;

    private AlertEngine() {
    }

    public static List<ClimateAlert> build(RotationPlan plan, ClimateProfile profile, WaterAccess water) {
        return build(plan, profile, water, SoilType.UNKNOWN);
    }

    public static List<ClimateAlert> build(RotationPlan plan, ClimateProfile profile, WaterAccess water,
            SoilType soil) {
        AlertQueue queue = new AlertQueue();
        for (RotationStep step : plan.steps()) {
            Crop crop = step.crop();
            List<Integer> months = step.months();
            for (int index = 0; index < months.size(); index++) {
                evaluate(queue, crop, months.get(index), crop.calendar().stageFor(step, index),
                        profile, water, soil);
            }
        }
        return queue.ordered();
    }

    private static void evaluate(AlertQueue queue, Crop crop, int month, GrowthStage stage,
            ClimateProfile profile, WaterAccess water, SoilType soil) {
        boolean heatStage = stage.sensitive().contains(Hazard.HEAT);
        boolean droughtStage = stage.sensitive().contains(Hazard.DROUGHT);

        boolean tolerant = crop.heatTolerance() >= HEAT_TOLERANT_MIN;
        double heatDays = profile.value(tolerant ? Metric.SEVERE_HEAT_DAYS : Metric.HEAT_DAYS, month);
        if (!Double.isNaN(heatDays)) {
            double weighted = heatDays * (heatStage && !tolerant ? 2 : 1);
            Severity severity = above(weighted, HEAT);
            add(queue, Type.HEAT, heatStage ? severity : downgrade(severity, NON_SENSITIVE_DOWNGRADE),
                    month, crop, stage, heatDays,
                    tolerant ? ClimateEngine.SEVERE_HEAT_DAY_C : ClimateEngine.HEAT_DAY_C);
        }

        double dry = profile.value(Metric.DRY_SPELL_PROB, month);
        if (!Double.isNaN(dry) && droughtStage && water == WaterAccess.RAINFED) {
            add(queue, Type.DROUGHT, downgrade(above(dry, DROUGHT), water.alertDowngrade), month, crop,
                    stage, dry, DROUGHT[0]);
        }

        double deficit = profile.deficitMm(month);
        if (!Double.isNaN(deficit) && droughtStage) {
            add(queue, Type.WATER_DEFICIT, downgrade(above(deficit, DEFICIT), water.alertDowngrade),
                    month, crop, stage, deficit, DEFICIT[0]);
        }

        double frost = profile.value(Metric.FROST_DAYS, month);
        int hardiness = crop.frostTolerance();
        if (!Double.isNaN(frost) && hardiness < FROST_HARDY_MIN
                && (hardiness <= 2 || stage.sensitive().contains(Hazard.FROST))) {
            add(queue, Type.FROST, above(frost, FROST), month, crop, stage, frost, FROST[0]);
        }

        double extreme = profile.value(Metric.EXTREME_RAIN_DAYS, month);
        boolean unknownSoil = soil == SoilType.UNKNOWN;
        if (!Double.isNaN(extreme) && (soil.slowDraining
                || (unknownSoil && stage.sensitive().contains(Hazard.WATERLOGGING)))) {
            Severity severity = above(extreme, WATERLOG);
            add(queue, Type.WATERLOGGING, soil.slowDraining ? severity : downgrade(severity, 1), month,
                    crop, stage, extreme, WATERLOG[0]);
        }

        double soilAnomaly = profile.value(Metric.SOIL_ANOMALY, month);
        if (!Double.isNaN(soilAnomaly)) {
            add(queue, Type.SOIL_DRYING, below(soilAnomaly, SOIL), month, crop, stage, soilAnomaly, SOIL[0]);
        }
    }

    private static void add(AlertQueue queue, Type type, Severity severity, int month, Crop crop,
            GrowthStage stage, double value, double threshold) {
        if (severity != Severity.LOW) {
            queue.offer(new ClimateAlert(type, severity, month, List.of(crop.slug()), stage.key(), value,
                    threshold));
        }
    }

    static Severity above(double value, double[] levels) {
        Severity severity = Severity.LOW;
        for (int i = 0; i < levels.length; i++) {
            if (value >= levels[i]) {
                severity = Severity.values()[i + 1];
            }
        }
        return severity;
    }

    static Severity below(double value, double[] levels) {
        Severity severity = Severity.LOW;
        for (int i = 0; i < levels.length; i++) {
            if (value <= levels[i]) {
                severity = Severity.values()[i + 1];
            }
        }
        return severity;
    }

    static Severity downgrade(Severity severity, int steps) {
        return Severity.values()[Math.max(0, severity.ordinal() - steps)];
    }
}
