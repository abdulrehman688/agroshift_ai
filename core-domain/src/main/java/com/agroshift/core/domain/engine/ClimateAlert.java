package com.agroshift.core.domain.engine;

import java.util.List;

/** Historical risk pattern for a crop stage. It is not a live weather forecast. */
public record ClimateAlert(Type type, Severity severity, int month, List<String> cropSlugs,
                           String stageKey, double value, double threshold) {
    public enum Type { HEAT, DROUGHT, WATER_DEFICIT, FROST, WATERLOGGING, SOIL_DRYING }

    public enum Severity { LOW, MODERATE, HIGH, CRITICAL }

    public ClimateAlert {
        cropSlugs = List.copyOf(cropSlugs);
    }
}
