package com.agroshift.core.domain.engine;

/** Farmer-selected soil, using the same drainage and erosion groups as the original engine. */
public enum SoilType {
    UNKNOWN(1.0, false),
    SANDY(1.0, false),
    LOAM(1.0, false),
    CLAY(0.75, true),
    SILT(1.25, false);

    final double erosionFactor;
    final boolean slowDraining;

    SoilType(double erosionFactor, boolean slowDraining) {
        this.erosionFactor = erosionFactor;
        this.slowDraining = slowDraining;
    }

    public static SoilType parse(String value) {
        for (SoilType soil : values()) {
            if (soil.name().equals(value)) {
                return soil;
            }
        }
        return UNKNOWN;
    }
}
