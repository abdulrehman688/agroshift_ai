package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.engine.ClimateEngine;
import com.agroshift.core.domain.engine.ClimateProfile;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.domain.engine.DailyClimate;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.PlanStudio;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.RotationScorer;
import com.agroshift.core.domain.engine.RotationStep;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

public class PlanStudioTest {
    private static Map<ClimateScenario, ClimateProfile> profiles;
    private static ClimateProfile normal;

    @BeforeClass
    public static void buildClimate() {
        List<DailyClimate> days = new ArrayList<>();
        LocalDate date = LocalDate.of(2006, 1, 1);
        int index = 0;
        while (date.isBefore(LocalDate.of(2026, 1, 1))) {
            int month = date.getMonthValue();
            double high = month >= 5 && month <= 8 ? 41 : 25;
            double rain = index++ % 6 == 0 ? (month == 7 ? 12 : 1.5) : 0;
            days.add(new DailyClimate(date, high - 8, high, high - 15, rain, 0.3));
            date = date.plusDays(1);
        }
        profiles = new EnumMap<>(ClimateScenario.class);
        for (ClimateScenario scenario : ClimateScenario.values()) {
            profiles.put(scenario, ClimateEngine.buildScenario(days, 31.0, scenario));
        }
        normal = profiles.get(ClimateScenario.NORMAL);
    }

    private static List<PlanStudio.Entry> rank(PlanStudio.Lens lens) {
        return PlanStudio.rank(normal, WaterAccess.values()[0], FarmGoal.BALANCED, SoilType.UNKNOWN, lens);
    }

    @Test
    public void catalogHasEveryCropPairWithoutDuplicates() {
        List<RotationPlan> catalog = PlanStudio.catalog();
        assertEquals(Crop.KHARIF.size() * Crop.RABI.size() + 3, catalog.size());
        Set<String> crops = new HashSet<>();
        for (RotationPlan plan : catalog) {
            assertTrue(plan.key(), crops.add(plan.cycleYears() + ":" + plan.uniqueCropSlugs()));
        }
    }

    @Test
    public void rankingUsesTheSameScoresAsAdvisor() {
        for (PlanStudio.Entry entry : rank(PlanStudio.Lens.BEST)) {
            assertEquals(RotationScorer.score(entry.plan(), normal, WaterAccess.values()[0], FarmGoal.BALANCED,
                    SoilType.UNKNOWN), entry.scores());
        }
    }

    @Test
    public void eachLensSortsByItsOwnScore() {
        for (PlanStudio.Lens lens : PlanStudio.Lens.values()) {
            List<PlanStudio.Entry> ranked = rank(lens);
            assertEquals(PlanStudio.catalog().size(), ranked.size());
            for (int i = 1; i < ranked.size(); i++) {
                assertTrue(lens.name(), lens.value(ranked.get(i - 1).scores()) >= lens.value(ranked.get(i).scores()));
            }
        }
    }

    @Test
    public void favoritesComeFirstAndKeepRankOrder() {
        List<PlanStudio.Entry> ranked = rank(PlanStudio.Lens.BEST);
        String low = ranked.get(ranked.size() - 1).plan().key();
        String middle = ranked.get(ranked.size() / 2).plan().key();
        List<PlanStudio.Entry> sorted = PlanStudio.favoritesFirst(ranked, Set.of(low, middle));
        assertEquals(middle, sorted.get(0).plan().key());
        assertEquals(low, sorted.get(1).plan().key());
        assertEquals(ranked.size(), sorted.size());
    }

    @Test
    public void builtPlanMatchesTheCatalogPlanForThosePairs() {
        assertEquals(RotationPlan.COTTON_WHEAT, PlanStudio.build("cotton", "wheat").key());
        assertEquals(RotationPlan.RICE_WHEAT, PlanStudio.build("rice", "wheat").key());
        assertEquals(RotationPlan.customKey("maize", "mustard"), PlanStudio.build("maize", "mustard").key());
        assertThrows(IllegalArgumentException.class, () -> PlanStudio.build("wheat", "cotton"));
    }

    @Test
    public void samePlanRecognisesCustomKeyForTemplateCrops() {
        assertTrue(PlanStudio.isSame(RotationPlan.template(RotationPlan.COTTON_WHEAT),
                RotationPlan.customKey("cotton", "wheat")));
        assertFalse(PlanStudio.isSame(RotationPlan.template(RotationPlan.RICE_WHEAT),
                RotationPlan.customKey("cotton", "wheat")));
    }

    @Test
    public void yearBlocksCoverCropMonthsAndSplitAcrossNewYear() {
        RotationPlan plan = RotationPlan.template(RotationPlan.COTTON_WHEAT);
        List<PlanStudio.Block> blocks = PlanStudio.yearBlocks(plan);
        int wheatMonths = 0;
        int cottonMonths = 0;
        for (PlanStudio.Block block : blocks) {
            assertEquals(1, block.year());
            assertTrue(block.startMonth() >= 1 && block.startMonth() + block.length() <= 13);
            if (block.cropSlug().equals("wheat")) {
                wheatMonths += block.length();
            } else {
                cottonMonths += block.length();
            }
        }
        assertEquals(new RotationStep(1, "wheat", 11, 4).months().size(), wheatMonths);
        assertEquals(new RotationStep(1, "cotton", 5, 11).months().size(), cottonMonths);
        assertEquals(3, blocks.size());
    }

    @Test
    public void twoYearPlanWrapsLastCropIntoFirstYear() {
        List<PlanStudio.Block> blocks = PlanStudio.yearBlocks(RotationPlan.template(RotationPlan.WATER_SAVER));
        boolean wheatInYearOne = false;
        boolean wheatInYearTwo = false;
        for (PlanStudio.Block block : blocks) {
            if (block.cropSlug().equals("wheat")) {
                wheatInYearOne |= block.year() == 1 && block.startMonth() == 1;
                wheatInYearTwo |= block.year() == 2 && block.startMonth() == 11;
            }
        }
        assertTrue(wheatInYearOne);
        assertTrue(wheatInYearTwo);
    }

    @Test
    public void stressTestScoresEveryWeatherFuture() {
        RotationPlan plan = RotationPlan.template(RotationPlan.WATER_SAVER);
        PlanStudio.Stress stress = PlanStudio.stress(plan, profiles, WaterAccess.values()[0], FarmGoal.BALANCED,
                SoilType.UNKNOWN);
        for (ClimateScenario scenario : ClimateScenario.values()) {
            assertNotNull(stress.scores().get(scenario));
            assertEquals(RotationScorer.score(plan, profiles.get(scenario), WaterAccess.values()[0], FarmGoal.BALANCED,
                    SoilType.UNKNOWN).overall(), stress.scores().get(scenario), 0.001);
        }
        assertTrue(stress.worstDrop() >= 0);
    }

    @Test
    public void compareNamesTheBetterPlanForEachMeasure() {
        List<PlanStudio.Entry> ranked = rank(PlanStudio.Lens.LESS_WATER);
        PlanStudio.Entry first = ranked.get(0);
        PlanStudio.Entry last = ranked.get(ranked.size() - 1);
        assertEquals(PlanStudio.Winner.FIRST, PlanStudio.winner(PlanStudio.Lens.LESS_WATER, first, last));
        assertEquals(PlanStudio.Winner.SECOND, PlanStudio.winner(PlanStudio.Lens.LESS_WATER, last, first));
        assertEquals(PlanStudio.Winner.TIE, PlanStudio.winner(PlanStudio.Lens.LESS_WATER, first, first));
    }
}
