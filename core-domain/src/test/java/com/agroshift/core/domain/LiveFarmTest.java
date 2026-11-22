package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmSelection;
import com.agroshift.core.domain.season.SeasonPosition;
import com.agroshift.core.domain.season.SeasonTimeline;
import com.agroshift.core.domain.water.CanalTurn;
import com.agroshift.core.domain.water.SkyState;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.Test;

public class LiveFarmTest {
    private static FarmField field(String id, Double latitude, Double longitude) {
        return new FarmField(id, id, 2, "wheat", 1L, latitude, longitude, RotationPlan.COTTON_WHEAT,
                WaterAccess.IRRIGATED, FarmGoal.of(List.of()), SoilType.LOAM);
    }

    @Test
    public void farmSelectionKeepsChosenFarm() {
        List<FarmField> fields = List.of(field("lahore", 31.5, 74.3), field("multan", 30.2, 71.5));
        assertEquals("multan", FarmSelection.choose(fields, "multan").id());
    }

    @Test
    public void farmSelectionFallsBackToLocatedFarm() {
        List<FarmField> fields = List.of(field("draft", null, null), field("multan", 30.2, 71.5));
        assertEquals("multan", FarmSelection.choose(fields, "deleted").id());
        assertEquals("draft", FarmSelection.choose(List.of(field("draft", null, null)), null).id());
        assertNull(FarmSelection.choose(List.of(), "any"));
    }

    @Test
    public void timelineStartsInOctoberAndEndsInSeptember() {
        LocalDate today = LocalDate.of(2026, 11, 22);
        assertEquals(1, SeasonTimeline.indexOf(today));
        assertEquals(YearMonth.of(2026, 10), SeasonTimeline.monthAt(today, 0));
        assertEquals(YearMonth.of(2027, 9), SeasonTimeline.monthAt(today, 11));
        assertEquals(YearMonth.of(2025, 10), SeasonTimeline.monthAt(LocalDate.of(2026, 3, 1), 0));
    }

    @Test
    public void timelineFindsRotationCropAndGrowth() {
        RotationPlan plan = RotationPlan.template(RotationPlan.COTTON_WHEAT);
        SeasonTimeline.Moment wheat = SeasonTimeline.at(plan, YearMonth.of(2027, 1));
        assertEquals("wheat", wheat.cropSlug());
        assertEquals(SeasonPosition.Season.RABI, wheat.season());
        assertTrue(wheat.growth() > 0 && wheat.growth() < 1);
        assertEquals("cotton", SeasonTimeline.at(plan, YearMonth.of(2027, 7)).cropSlug());
    }

    @Test
    public void timelineShowsBareMonthsHonestly() {
        SeasonTimeline.Moment bare = SeasonTimeline.at(RotationPlan.template(RotationPlan.RICE_WHEAT),
                YearMonth.of(2027, 5));
        assertFalse(bare.hasCrop());
        assertNull(bare.stageKey());
    }

    @Test
    public void multiYearRotationUsesCorrectCycleYear() {
        RotationPlan plan = RotationPlan.template(RotationPlan.WATER_SAVER);
        String first = SeasonTimeline.at(plan, YearMonth.of(2027, 8)).cropSlug();
        String second = SeasonTimeline.at(plan, YearMonth.of(2028, 8)).cropSlug();
        assertFalse(first == null && second == null);
        assertFalse(java.util.Objects.equals(first, second));
    }

    @Test
    public void skyConditionPrioritisesRain() {
        assertEquals(SkyState.Condition.HEAVY_RAIN, new SkyState(true, 100, 5, 40, 40).condition());
        assertEquals(SkyState.Condition.RAIN, new SkyState(true, 90, 0.5, 5, 30).condition());
        assertEquals(SkyState.Condition.HOT, new SkyState(true, 10, 0, 5, 41).condition());
        assertEquals(SkyState.Condition.WINDY, new SkyState(true, 10, 0, 30, 25).condition());
        assertEquals(SkyState.Condition.CLOUDY, new SkyState(false, 70, 0, 5, 20).condition());
        assertEquals(SkyState.Condition.CLEAR, new SkyState(false, 10, 0, 5, 20).condition());
    }

    @Test
    public void canalTurnReportsLiveProgress() {
        CanalTurn turn = new CanalTurn(DayOfWeek.WEDNESDAY, 6 * 60, 60, 30);
        LocalDateTime middle = LocalDateTime.of(2026, 6, 10, 6, 15);
        assertEquals(0.25f, turn.progress(middle), 0.001f);
        assertEquals(Duration.ofMinutes(45), turn.remaining(middle));
        LocalDateTime before = LocalDateTime.of(2026, 6, 10, 5, 0);
        assertEquals(0f, turn.progress(before), 0f);
        assertEquals(Duration.ZERO, turn.remaining(before));
        assertEquals(Duration.ofHours(1), turn.untilStart(before));
    }
}
