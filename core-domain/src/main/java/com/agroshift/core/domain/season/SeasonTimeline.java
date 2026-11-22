package com.agroshift.core.domain.season;

import com.agroshift.core.domain.engine.CropCalendar;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.RotationStep;
import java.time.LocalDate;
import java.time.YearMonth;

/** Maps the 12 months of the farming year, October to September, to a field's rotation. */
public final class SeasonTimeline {
    public static final int MONTHS = 12;

    /** A month on the timeline. The crop fields are null when the field is between crops. */
    public record Moment(YearMonth month, SeasonPosition.Season season, String cropSlug, String stageKey,
                         double growth) {
        public boolean hasCrop() {
            return cropSlug != null;
        }
    }

    private SeasonTimeline() {
    }

    public static int indexOf(LocalDate date) {
        return Math.floorMod(date.getMonthValue() - 10, MONTHS);
    }

    public static YearMonth monthAt(LocalDate today, int index) {
        int startYear = today.getMonthValue() >= 10 ? today.getYear() : today.getYear() - 1;
        return YearMonth.of(startYear, 10).plusMonths(Math.floorMod(index, MONTHS));
    }

    public static Moment at(RotationPlan plan, YearMonth month) {
        SeasonPosition.Season season = SeasonPosition.of(month.atDay(15)).season();
        if (plan != null) {
            for (RotationStep step : plan.steps()) {
                int length = CropCalendar.seasonLength(step.sowMonth(), step.harvestMonth());
                int offset = Math.floorMod(month.getMonthValue() - step.sowMonth(), MONTHS);
                int sownYear = month.getMonthValue() >= step.sowMonth() ? month.getYear() : month.getYear() - 1;
                if (offset < length && Math.floorMod(sownYear, plan.cycleYears()) + 1 == step.year()) {
                    return new Moment(month, season, step.cropSlug(),
                            step.crop().calendar().stageFor(step, offset).key(), (offset + 0.5) / length);
                }
            }
        }
        return new Moment(month, season, null, null, 0);
    }
}
