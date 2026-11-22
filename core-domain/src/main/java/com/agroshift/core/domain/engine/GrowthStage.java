package com.agroshift.core.domain.engine;

import java.util.Set;

/** Stage in months after sowing, with the hazards that hurt most during it. */
public record GrowthStage(String key, int start, int end, Set<Hazard> sensitive) {
    public GrowthStage {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Invalid stage range");
        }
        sensitive = Set.copyOf(sensitive);
    }

    static GrowthStage of(String key, int start, int end, Hazard... hazards) {
        return new GrowthStage(key, start, end, Set.of(hazards));
    }
}
