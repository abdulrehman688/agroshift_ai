package com.agroshift.core.domain.engine;

import java.util.ArrayList;
import java.util.List;

/** A crop's typical season, month by month, from the AgroShift crop table. */
public final class CropJourney {
    public record Step(int month, String stageKey, double growth) {
    }

    private CropJourney() {
    }

    public static List<Step> of(Crop crop) {
        CropCalendar calendar = crop.calendar();
        RotationStep season = new RotationStep(1, crop.slug(), calendar.sowMonth(), calendar.harvestMonth());
        List<Integer> months = season.months();
        List<Step> steps = new ArrayList<>(months.size());
        for (int i = 0; i < months.size(); i++) {
            steps.add(new Step(months.get(i), calendar.stageFor(season, i).key(), (i + 1.0) / months.size()));
        }
        return List.copyOf(steps);
    }
}
