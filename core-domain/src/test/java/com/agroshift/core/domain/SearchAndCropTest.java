package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.domain.engine.CropJourney;
import com.agroshift.core.domain.search.SearchIndex;
import com.agroshift.core.domain.search.SearchIndex.Entry;
import com.agroshift.core.domain.search.SearchIndex.Kind;
import java.util.List;
import org.junit.Test;

public class SearchAndCropTest {
    private static final List<Entry> ENTRIES = List.of(
            new Entry(Kind.ACTION, "market", "Market", "Open prices"),
            new Entry(Kind.FARM, "a", "Lahore wheat farm", "Cotton and wheat"),
            new Entry(Kind.RECORD, "r", "Buy wheat seed", "Cost, Lahore wheat farm"),
            new Entry(Kind.CROP, "wheat", "Wheat", "Rabi"),
            new Entry(Kind.RECORD, "n", "Check canal", "Note, Multan"));

    @Test
    public void exactMatchRanksFirst() {
        List<Entry> found = SearchIndex.find(ENTRIES, "  WHEAT ", null, 10);
        assertEquals("wheat", found.get(0).id());
        assertEquals(3, found.size());
    }

    @Test
    public void subtitleMatchesComeAfterTitles() {
        List<Entry> found = SearchIndex.find(ENTRIES, "lahore", null, 10);
        assertEquals("a", found.get(0).id());
        assertEquals("r", found.get(1).id());
    }

    @Test
    public void kindFilterAndLimitApply() {
        assertEquals(1, SearchIndex.find(ENTRIES, "wheat", Kind.CROP, 10).size());
        assertEquals(2, SearchIndex.find(ENTRIES, "", Kind.RECORD, 10).size());
        assertEquals(1, SearchIndex.find(ENTRIES, "", null, 1).size());
        assertTrue(SearchIndex.find(ENTRIES, "banana", null, 10).isEmpty());
    }

    @Test
    public void cropJourneyFollowsTheSeason() {
        List<CropJourney.Step> wheat = CropJourney.of(Crop.bySlug("wheat"));
        assertEquals(5, wheat.size());
        assertEquals(11, wheat.get(0).month());
        assertEquals(3, wheat.get(4).month());
        assertEquals(1.0, wheat.get(4).growth(), 0.001);
        assertEquals("establishment", wheat.get(0).stageKey());
    }

    @Test
    public void scenariosReportTheirWeatherChange() {
        assertTrue(ClimateScenario.HOTTER.hotter());
        assertTrue(ClimateScenario.LESS_RAIN.drier());
        assertTrue(ClimateScenario.HOTTER_AND_DRIER.hotter() && ClimateScenario.HOTTER_AND_DRIER.drier());
        assertTrue(!ClimateScenario.NORMAL.hotter() && !ClimateScenario.NORMAL.drier());
    }
}
