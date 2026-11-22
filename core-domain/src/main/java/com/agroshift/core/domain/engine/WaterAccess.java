package com.agroshift.core.domain.engine;

public enum WaterAccess {
    RAINFED(1.0, 0),
    LIMITED(0.65, 1),
    IRRIGATED(0.35, 2);

    final double stressFactor;
    final int alertDowngrade;

    WaterAccess(double stressFactor, int alertDowngrade) {
        this.stressFactor = stressFactor;
        this.alertDowngrade = alertDowngrade;
    }

    public static WaterAccess parse(String value) {
        for (WaterAccess access : values()) {
            if (access.name().equals(value)) {
                return access;
            }
        }
        return IRRIGATED;
    }
}
