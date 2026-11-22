package com.agroshift.core.domain.engine;

import java.util.ArrayList;
import java.util.List;

public record RotationStep(int year, String cropSlug, int sowMonth, int harvestMonth) {
    public RotationStep {
        if (year < 1 || sowMonth < 1 || sowMonth > 12 || harvestMonth < 1 || harvestMonth > 12) {
            throw new IllegalArgumentException("Invalid rotation step");
        }
        Crop.bySlug(cropSlug);
    }

    public Crop crop() {
        return Crop.bySlug(cropSlug);
    }

    /** Calendar months in the field, including sowing and excluding harvest. */
    public List<Integer> months() {
        int length = CropCalendar.seasonLength(sowMonth, harvestMonth);
        List<Integer> months = new ArrayList<>(length);
        for (int k = 0; k < length; k++) {
            months.add(Math.floorMod(sowMonth - 1 + k, 12) + 1);
        }
        return months;
    }
}
