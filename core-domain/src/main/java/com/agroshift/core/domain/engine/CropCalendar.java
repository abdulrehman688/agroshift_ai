package com.agroshift.core.domain.engine;

import java.util.List;

/**
 * Abstract data type for a crop's typical season.
 *
 * <p>Rep invariant: months are 1 to 12 and stages are non-empty. Abstraction function: the growth
 * stage a crop is in for each month after its typical sowing month.
 */
public record CropCalendar(int sowMonth, int harvestMonth, List<GrowthStage> stages) {
    public CropCalendar {
        if (sowMonth < 1 || sowMonth > 12 || harvestMonth < 1 || harvestMonth > 12) {
            throw new IllegalArgumentException("Months must be 1 to 12");
        }
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("A calendar needs stages");
        }
        stages = List.copyOf(stages);
    }

    public static int seasonLength(int sow, int harvest) {
        int months = Math.floorMod(harvest - sow, 12);
        return months == 0 ? 12 : months;
    }

    /** Stretches stages to the step's season. The most sensitive overlapping stage wins. */
    public GrowthStage stageFor(RotationStep step, int monthIndex) {
        double ratio = (double) seasonLength(sowMonth, harvestMonth)
                / seasonLength(step.sowMonth(), step.harvestMonth());
        double low = monthIndex * ratio;
        double high = (monthIndex + 1) * ratio;
        GrowthStage best = null;
        for (GrowthStage stage : stages) {
            if (stage.start() < high && stage.end() > low
                    && (best == null || stage.sensitive().size() > best.sensitive().size())) {
                best = stage;
            }
        }
        return best != null ? best : stages.get(stages.size() - 1);
    }
}
