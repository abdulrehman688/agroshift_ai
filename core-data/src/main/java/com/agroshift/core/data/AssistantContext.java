package com.agroshift.core.data;

import com.agroshift.core.domain.engine.ClimateAlert;
import com.agroshift.core.domain.engine.ClimateProfile;
import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.engine.RotationScores;
import com.agroshift.core.domain.engine.RotationStep;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.farm.FarmField;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the facts the assistant may use. Exact field coordinates are never included. */
final class AssistantContext {
    private static final int MAX_ALERTS = 12;
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};

    private AssistantContext() {
    }

    static JsonObject build(FarmField field, Map<ClimateScenario, RotationAdvisor.Advice> outlooks) {
        RotationAdvisor.Advice advice = outlooks.get(ClimateScenario.NORMAL);
        if (advice == null) {
            throw new IllegalArgumentException("Normal climate advice is required");
        }
        ClimateProfile profile = advice.profile();
        JsonObject root = new JsonObject();
        root.add("location", location(field, advice, outlooks));
        root.add("data_quality", quality(profile));
        root.add("soil", soil(field.soil()));

        JsonObject climate = new JsonObject();
        climate.add("baseline", baseline(profile));
        climate.add("scenario", JsonNull.INSTANCE);
        root.add("climate", climate);

        JsonArray alerts = new JsonArray();
        List<ClimateAlert> found = advice.current().alerts();
        for (ClimateAlert alert : found.subList(0, Math.min(MAX_ALERTS, found.size()))) {
            alerts.add(alert(alert));
        }
        root.add("alerts", alerts);

        JsonArray strategies = new JsonArray();
        for (RotationAdvisor.Option option : advice.options()) {
            strategies.add(strategy(option, option == advice.best()));
        }
        root.add("strategies", strategies);
        return root;
    }

    private static JsonObject location(FarmField field, RotationAdvisor.Advice advice,
            Map<ClimateScenario, RotationAdvisor.Advice> outlooks) {
        JsonObject location = new JsonObject();
        location.addProperty("label", "Farmer's selected field");
        location.addProperty("data_resolution", "NASA POWER regional grid of about 50 km, not field-level");
        location.addProperty("area_acres", field.areaAcres());
        location.addProperty("water_access", field.water().name().toLowerCase(Locale.ROOT));
        JsonArray priorities = new JsonArray();
        for (FarmGoal.Priority priority : field.goal().priorities()) {
            priorities.add(priority.name().toLowerCase(Locale.ROOT));
        }
        location.add("farmer_priorities", priorities);
        JsonArray crops = new JsonArray();
        for (String crop : advice.current().plan().uniqueCropSlugs()) {
            crops.add(crop);
        }
        location.add("current_crops", crops);

        JsonArray temperatures = new JsonArray();
        for (int month = 1; month <= 12; month++) {
            JsonObject row = new JsonObject();
            row.addProperty("month", month);
            add(row, "average_high_c", advice.profile().value(Metric.T_MAX, month), 1);
            add(row, "average_low_c", advice.profile().value(Metric.T_MIN, month), 1);
            temperatures.add(row);
        }
        location.add("temperature_by_month", temperatures);

        JsonArray whatIf = new JsonArray();
        for (ClimateScenario scenario : ClimateScenario.values()) {
            RotationAdvisor.Advice outlook = outlooks.get(scenario);
            if (scenario == ClimateScenario.NORMAL || outlook == null) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("simulated_change", switch (scenario) {
                case HOTTER -> "2 C hotter";
                case LESS_RAIN -> "30 percent less rain";
                case HOTTER_AND_DRIER -> "2 C hotter and 30 percent less rain";
                case NORMAL -> "historical";
            });
            row.addProperty("basis", "simulated, not a forecast");
            row.addProperty("best_strategy", strategyName(outlook.best().kind()));
            row.addProperty("best_score", outlook.best().scores().overall());
            row.addProperty("current_rotation_score", outlook.current().scores().overall());
            whatIf.add(row);
        }
        location.add("what_if_results", whatIf);
        return location;
    }

    private static JsonObject quality(ClimateProfile profile) {
        JsonObject quality = new JsonObject();
        quality.addProperty("confidence", "low");
        quality.addProperty("tier", "standard");
        JsonArray sources = new JsonArray();
        sources.add("NASA POWER");
        quality.add("sources", sources);
        JsonArray warnings = new JsonArray();
        warnings.add("Climate is regional. Crop traits and calendars are approximate and need local review.");
        warnings.add("SMAP, NDVI and SoilGrids are not included in this analysis.");
        quality.add("warnings", warnings);
        JsonArray years = new JsonArray();
        years.add(profile.startYear());
        years.add(profile.endYear());
        quality.add("years_covered", years);
        return quality;
    }

    private static JsonObject soil(SoilType soil) {
        JsonObject result = new JsonObject();
        result.addProperty("available", soil != SoilType.UNKNOWN);
        result.addProperty("source", "Farmer selected");
        if (soil != SoilType.UNKNOWN) {
            result.addProperty("texture", soil.name().toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static JsonObject baseline(ClimateProfile profile) {
        JsonObject baseline = new JsonObject();
        add(baseline, "annual_rainfall_mm", profile.annualRainMm(), 1);
        JsonArray years = new JsonArray();
        years.add(profile.startYear());
        years.add(profile.endYear());
        baseline.add("years_covered", years);
        baseline.addProperty("basis", "historical");
        JsonArray monthly = new JsonArray();
        for (int month = 1; month <= 12; month++) {
            JsonObject row = new JsonObject();
            row.addProperty("month", month);
            add(row, "precip_mm", profile.value(Metric.PRECIP_MM, month), 1);
            add(row, "et0_mm", profile.value(Metric.ET0_MM, month), 1);
            add(row, "deficit_mm", profile.deficitMm(month), 1);
            add(row, "heat_days", profile.value(Metric.HEAT_DAYS, month), 1);
            add(row, "frost_days", profile.value(Metric.FROST_DAYS, month), 1);
            add(row, "dry_spell_prob", profile.value(Metric.DRY_SPELL_PROB, month), 2);
            monthly.add(row);
        }
        baseline.add("monthly", monthly);
        return baseline;
    }

    private static JsonObject alert(ClimateAlert alert) {
        JsonObject row = new JsonObject();
        row.addProperty("type", alert.type().name().toLowerCase(Locale.ROOT));
        row.addProperty("severity", alert.severity().name().toLowerCase(Locale.ROOT));
        row.addProperty("month", alert.month());
        JsonArray crops = new JsonArray();
        alert.cropSlugs().forEach(crops::add);
        row.add("crops", crops);
        row.addProperty("stage", alert.stageKey());
        row.addProperty("reason", reason(alert));
        row.addProperty("basis", "historical");
        return row;
    }

    private static String reason(ClimateAlert alert) {
        String month = MONTHS[alert.month() - 1];
        return switch (alert.type()) {
            case HEAT -> String.format(Locale.ROOT, "%s averages %.0f days above %.0f C", month, alert.value(),
                    alert.threshold());
            case DROUGHT -> String.format(Locale.ROOT, "%.0f%% of past years had a 10+ day dry spell in %s",
                    alert.value() * 100, month);
            case WATER_DEFICIT -> String.format(Locale.ROOT, "%s rain is about %.0f mm below crop water demand",
                    month, alert.value());
            case FROST -> String.format(Locale.ROOT, "%s averages %.1f frost days", month, alert.value());
            case WATERLOGGING -> String.format(Locale.ROOT, "%s averages %.1f heavy-rain days", month, alert.value());
            case SOIL_DRYING -> String.format(Locale.ROOT, "Recent %s soil moisture is below its usual level", month);
        };
    }

    private static JsonObject strategy(RotationAdvisor.Option option, boolean best) {
        JsonObject row = new JsonObject();
        row.addProperty("key", option.kind().name().toLowerCase(Locale.ROOT));
        row.addProperty("name", strategyName(option.kind()));
        row.addProperty("is_current", option.kind() == RotationAdvisor.Kind.CURRENT);
        row.addProperty("description", best ? "Highest overall score for this field" : "Compared option");
        JsonObject scores = new JsonObject();
        RotationScores values = option.scores();
        scores.addProperty("overall", values.overall());
        scores.addProperty("water_fit", values.waterFit());
        scores.addProperty("climate_risk", values.climateRisk());
        scores.addProperty("soil_health", values.soilHealth());
        scores.addProperty("erosion_infiltration", values.erosion());
        scores.addProperty("pest_break", values.pestBreak());
        scores.addProperty("income", values.income());
        row.add("scores", scores);
        JsonObject rotation = new JsonObject();
        rotation.addProperty("cycle_years", option.plan().cycleYears());
        rotation.addProperty("annual_water_need_mm", Math.round(option.plan().annualWaterNeedMm()));
        JsonArray steps = new JsonArray();
        for (RotationStep step : option.plan().steps()) {
            JsonObject item = new JsonObject();
            item.addProperty("year", step.year());
            item.addProperty("crop", step.cropSlug());
            item.addProperty("sow_month", step.sowMonth());
            item.addProperty("harvest_month", step.harvestMonth());
            steps.add(item);
        }
        rotation.add("steps", steps);
        row.add("rotation", rotation);
        return row;
    }

    private static String strategyName(RotationAdvisor.Kind kind) {
        return switch (kind) {
            case CURRENT -> "Current rotation";
            case WATER_SAVER -> "Water saver";
            case SOIL_BUILDER -> "Soil builder";
        };
    }

    private static void add(JsonObject target, String key, double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            target.add(key, JsonNull.INSTANCE);
            return;
        }
        double factor = Math.pow(10, decimals);
        target.addProperty(key, Math.round(value * factor) / factor);
    }
}
