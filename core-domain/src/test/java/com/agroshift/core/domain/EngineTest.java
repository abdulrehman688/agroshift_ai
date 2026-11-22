package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.engine.AlertEngine;
import com.agroshift.core.domain.engine.AlertQueue;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.engine.ClimateAlert;
import com.agroshift.core.domain.engine.ClimateEngine;
import com.agroshift.core.domain.engine.ClimateProfile;
import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import com.agroshift.core.domain.engine.CropCalendar;
import com.agroshift.core.domain.engine.DailyClimate;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.RotationScorer;
import com.agroshift.core.domain.engine.RotationScores;
import com.agroshift.core.domain.engine.RotationStep;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.farm.FarmField;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class EngineTest {
    private static List<DailyClimate> climate(int years, double high, double low, double rainEveryFifthDay) {
        List<DailyClimate> days = new ArrayList<>();
        LocalDate date = LocalDate.of(2006, 1, 1);
        LocalDate end = date.plusYears(years);
        int index = 0;
        while (date.isBefore(end)) {
            double rain = index++ % 5 == 0 ? rainEveryFifthDay : 0;
            days.add(new DailyClimate(date, (high + low) / 2, high, low, rain, 0.3));
            date = date.plusDays(1);
        }
        return days;
    }

    @Test
    public void seasonLengthWrapsYear() {
        assertEquals(5, CropCalendar.seasonLength(11, 4));
        assertEquals(12, CropCalendar.seasonLength(4, 4));
    }

    @Test
    public void stepMonthsIncludeSowAndExcludeHarvest() {
        assertEquals(List.of(11, 12, 1, 2, 3), new RotationStep(1, "wheat", 11, 4).months());
    }

    @Test
    public void rotationRejectsStepOutsideCycle() {
        assertThrows(IllegalArgumentException.class, () -> new RotationPlan("bad", 1,
                List.of(new RotationStep(2, "wheat", 11, 4))));
    }

    @Test
    public void cottonWheatBareMonthIsApril() {
        double[] bare = RotationPlan.template(RotationPlan.COTTON_WHEAT).bareFractionByMonth();
        assertEquals(1.0, bare[3], 0);
        assertEquals(0.0, bare[6], 0);
    }

    @Test
    public void et0MatchesFaoRadiationReference() {
        DailyClimate day = new DailyClimate(LocalDate.of(2026, 6, 21), 30, 38, 22, 0, 0.3);
        double et0 = ClimateEngine.et0(day, 31.3);
        assertTrue("Expected plausible summer ET0, got " + et0, et0 > 6 && et0 < 10);
    }

    @Test
    public void profileCountsHeatFrostAndRain() {
        ClimateProfile hot = ClimateEngine.build(climate(2, 41, 25, 2), 31.3);
        assertEquals(2, hot.yearCount());
        assertEquals(31, hot.value(Metric.HEAT_DAYS, 7), 0);
        assertEquals(31, hot.value(Metric.SEVERE_HEAT_DAYS, 7), 0);
        assertEquals(0, hot.value(Metric.FROST_DAYS, 1), 0);
        ClimateProfile cold = ClimateEngine.build(climate(2, 10, -2, 2), 31.3);
        assertEquals(31, cold.value(Metric.FROST_DAYS, 1), 0);
    }

    @Test
    public void missingPowerValuesAreIgnored() {
        assertTrue(Double.isNaN(DailyClimate.clean(-999.0)));
        List<DailyClimate> days = climate(1, 30, 15, 2);
        days.set(0, new DailyClimate(days.get(0).date(), Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN));
        ClimateProfile profile = ClimateEngine.build(days, 31.3);
        assertFalse(Double.isNaN(profile.value(Metric.T_MAX, 1)));
        assertTrue(profile.missingFraction() > 0);
    }

    @Test
    public void extremeHeatDoesNotProduceInvalidScores() {
        ClimateProfile profile = ClimateEngine.build(climate(3, 46, 30, 0), 27);
        for (String key : List.of(RotationPlan.COTTON_WHEAT, RotationPlan.RICE_WHEAT,
                RotationPlan.WATER_SAVER, RotationPlan.SOIL_BUILDER)) {
            RotationScores scores = RotationScorer.score(RotationPlan.template(key), profile,
                    WaterAccess.RAINFED, FarmGoal.BALANCED);
            assertTrue(scores.overall() >= 0 && scores.overall() <= 100);
            assertTrue(scores.climateRisk() >= 0 && scores.climateRisk() <= 100);
        }
    }

    @Test
    public void irrigationImprovesWaterFit() {
        ClimateProfile profile = ClimateEngine.build(climate(3, 38, 22, 0.5), 30);
        RotationPlan plan = RotationPlan.template(RotationPlan.COTTON_WHEAT);
        assertTrue(RotationScorer.score(plan, profile, WaterAccess.IRRIGATED, FarmGoal.BALANCED).waterFit()
                > RotationScorer.score(plan, profile, WaterAccess.RAINFED, FarmGoal.BALANCED).waterFit());
    }

    @Test
    public void waterSaverNeedsLessWaterThanRiceWheat() {
        assertTrue(RotationPlan.template(RotationPlan.WATER_SAVER).annualWaterNeedMm()
                < RotationPlan.template(RotationPlan.RICE_WHEAT).annualWaterNeedMm());
    }

    @Test
    public void advisorReturnsThreeOptionsAndBest() {
        ClimateProfile profile = ClimateEngine.build(climate(5, 38, 20, 2), 31.3);
        RotationAdvisor.Advice advice = RotationAdvisor.advise(profile, RotationPlan.RICE_WHEAT,
                WaterAccess.IRRIGATED, FarmGoal.SAVE_WATER);
        assertEquals(3, advice.options().size());
        assertEquals(RotationAdvisor.Kind.CURRENT, advice.current().kind());
        assertNotNull(advice.best());
        for (RotationAdvisor.Option option : advice.options()) {
            assertTrue(advice.best().scores().overall() >= option.scores().overall());
        }
    }

    @Test
    public void heatAlertsAppearForHotSensitiveStage() {
        ClimateProfile profile = ClimateEngine.build(climate(3, 42, 26, 0), 27);
        RotationAdvisor.Advice advice = RotationAdvisor.advise(profile, RotationPlan.COTTON_WHEAT,
                WaterAccess.RAINFED, FarmGoal.BALANCED);
        assertTrue(advice.current().alerts().stream().anyMatch(a -> a.type() == ClimateAlert.Type.HEAT
                && a.severity() == ClimateAlert.Severity.CRITICAL));
    }

    @Test
    public void alertQueueOrdersBySeverityAndMergesCrops() {
        AlertQueue queue = new AlertQueue();
        queue.offer(new ClimateAlert(ClimateAlert.Type.HEAT, ClimateAlert.Severity.MODERATE, 6,
                List.of("cotton"), "flowering", 4, 3));
        queue.offer(new ClimateAlert(ClimateAlert.Type.HEAT, ClimateAlert.Severity.HIGH, 6,
                List.of("rice"), "flowering", 8, 3));
        queue.offer(new ClimateAlert(ClimateAlert.Type.FROST, ClimateAlert.Severity.CRITICAL, 1,
                List.of("wheat"), "tillering", 9, 1));
        List<ClimateAlert> ordered = queue.ordered();
        assertEquals(2, ordered.size());
        assertEquals(ClimateAlert.Type.FROST, ordered.get(0).type());
        assertEquals(ClimateAlert.Severity.HIGH, ordered.get(1).severity());
        assertEquals(List.of("cotton", "rice"), ordered.get(1).cropSlugs());
    }

    @Test
    public void customRotationAvoidsOverlapAndRejectsWrongSeason() {
        RotationPlan plan = RotationPlan.template(RotationPlan.customKey("cotton", "mustard"));
        assertEquals(11, plan.steps().get(1).sowMonth());
        assertThrows(IllegalArgumentException.class,
                () -> RotationPlan.template(RotationPlan.customKey("wheat", "cotton")));
        assertEquals(1, RotationPlan.template(RotationPlan.customKey("alfalfa", "alfalfa")).steps().size());
    }

    @Test
    public void goalsParseAndRoundTrip() {
        FarmGoal goal = FarmGoal.of(List.of(FarmGoal.Priority.WATER, FarmGoal.Priority.PEST));
        assertEquals(goal, FarmGoal.parse(goal.name()));
        assertEquals(FarmGoal.SAVE_WATER, FarmGoal.parse("SAVE_WATER"));
        assertEquals(FarmGoal.BALANCED, FarmGoal.parse("not-real"));
    }

    @Test
    public void hotterScenarioRaisesHeatAndDemandWithoutChangingBase() {
        List<DailyClimate> days = climate(3, 34, 22, 2);
        ClimateProfile base = ClimateEngine.build(days, 30);
        ClimateProfile hotter = ClimateEngine.buildScenario(days, 30, ClimateScenario.HOTTER);
        assertEquals(0, base.value(Metric.HEAT_DAYS, 6), 0);
        assertEquals(30, hotter.value(Metric.HEAT_DAYS, 6), 0);
        assertTrue(hotter.value(Metric.ET0_MM, 6) > base.value(Metric.ET0_MM, 6));
    }

    @Test
    public void lessRainScenarioCutsRainByThirtyPercent() {
        List<DailyClimate> days = climate(3, 34, 22, 10);
        ClimateProfile base = ClimateEngine.build(days, 30);
        ClimateProfile dry = ClimateEngine.buildScenario(days, 30, ClimateScenario.LESS_RAIN);
        assertEquals(base.annualRainMm() * 0.7, dry.annualRainMm(), 0.01);
    }

    @Test
    public void clayCanTriggerWaterloggingButLoamDoesNot() {
        List<DailyClimate> days = climate(3, 30, 18, 0);
        for (int i = 0; i < days.size(); i++) {
            DailyClimate day = days.get(i);
            double rain = day.date().getDayOfMonth() == 1 ? 80 : 2;
            days.set(i, new DailyClimate(day.date(), day.tMean(), day.tMax(), day.tMin(), rain, 0.3));
        }
        ClimateProfile profile = ClimateEngine.build(days, 30);
        RotationPlan plan = RotationPlan.template(RotationPlan.COTTON_WHEAT);
        assertTrue(AlertEngine.build(plan, profile, WaterAccess.IRRIGATED, SoilType.CLAY).stream()
                .anyMatch(alert -> alert.type() == ClimateAlert.Type.WATERLOGGING));
        assertFalse(AlertEngine.build(plan, profile, WaterAccess.IRRIGATED, SoilType.LOAM).stream()
                .anyMatch(alert -> alert.type() == ClimateAlert.Type.WATERLOGGING));
    }

    @Test
    public void recordsValidateAndTotalMoney() {
        assertEquals(FarmRecord.ERROR_TITLE,
                FarmRecord.create("1", "f", FarmRecord.Type.TASK, " ", 0, 1).errorCode());
        assertEquals(FarmRecord.ERROR_AMOUNT,
                FarmRecord.create("1", "f", FarmRecord.Type.EXPENSE, "Seed", 0, 1).errorCode());
        FarmRecord.Money money = FarmRecord.totals(List.of(
                FarmRecord.create("1", "f", FarmRecord.Type.EXPENSE, "Seed", 1000, 1).value(),
                FarmRecord.create("2", "f", FarmRecord.Type.SALE, "Wheat", 2500, 2).value(),
                FarmRecord.create("3", "f", FarmRecord.Type.NOTE, "Rain", 0, 3).value()));
        assertEquals(1500, money.profit(), 0);
    }

    @Test
    public void fitLabelsFollowScore() {
        ClimateProfile profile = ClimateEngine.build(climate(3, 34, 20, 3), 30);
        RotationAdvisor.Advice advice = RotationAdvisor.advise(profile, RotationPlan.RICE_WHEAT,
                WaterAccess.IRRIGATED, FarmGoal.BALANCED, SoilType.LOAM);
        assertNotNull(advice.benefit());
        assertNotNull(advice.best().fit());
    }

    @Test
    public void fieldRejectsLocationsOutsideSupportedArea() {
        assertTrue(FarmField.create("1", "North", 2, 31.3, 73.0, RotationPlan.COTTON_WHEAT,
                WaterAccess.IRRIGATED, FarmGoal.BALANCED, 1).isSuccess());
        assertEquals(FarmField.ERROR_LOCATION, FarmField.create("1", "North", 2, 42.0, -93.0,
                RotationPlan.COTTON_WHEAT, WaterAccess.IRRIGATED, FarmGoal.BALANCED, 1).errorCode());
    }
}
