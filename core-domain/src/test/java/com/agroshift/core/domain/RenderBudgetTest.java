package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.scene.CropVisual;
import com.agroshift.core.domain.scene.RenderBudget;
import com.agroshift.core.domain.scene.RenderBudget.Tier;
import org.junit.Test;

/** Living photos must suit every phone, from low-cost devices to flagships. */
public class RenderBudgetTest {
    private static RenderBudget budget(int memoryMb, boolean lowRam, int cores, boolean powerSave, boolean motion) {
        return RenderBudget.choose(new RenderBudget.Device(memoryMb, lowRam, cores, powerSave, motion));
    }

    @Test
    public void flagshipPhonesGetFullQuality() {
        RenderBudget high = budget(512, false, 8, false, true);
        assertEquals(Tier.HIGH, high.tier());
        assertTrue(high.animated());
        assertFalse(high.reducedEffects());
        assertEquals(33, high.frameMillis());
    }

    @Test
    public void midRangePhonesGetBalancedQuality() {
        RenderBudget balanced = budget(192, false, 4, false, true);
        assertEquals(Tier.BALANCED, balanced.tier());
        assertTrue(balanced.maxWidth() < budget(512, false, 8, false, true).maxWidth());
    }

    @Test
    public void lowCostPhonesGetLighterEffects() {
        for (RenderBudget lite : new RenderBudget[] {budget(96, false, 8, false, true),
                budget(512, true, 8, false, true), budget(512, false, 2, false, true)}) {
            assertEquals(Tier.LITE, lite.tier());
            assertTrue(lite.reducedEffects());
            assertTrue(lite.frameMillis() >= 50);
        }
    }

    @Test
    public void batterySaverAndReducedMotionUseStillPhoto() {
        assertEquals(Tier.STILL, budget(512, false, 8, true, true).tier());
        assertEquals(Tier.STILL, budget(512, false, 8, false, false).tier());
        assertFalse(budget(512, false, 8, true, true).animated());
    }

    @Test
    public void cropGrowthBlendsBetweenRealStages() {
        CropVisual.Blend start = CropVisual.blend(0f);
        assertEquals(0, start.from());
        assertEquals(0f, start.amount(), 0.001f);
        CropVisual.Blend quarter = CropVisual.blend(0.25f);
        assertEquals(0, quarter.from());
        assertEquals(1, quarter.to());
        assertEquals(0.5f, quarter.amount(), 0.001f);
        CropVisual.Blend late = CropVisual.blend(0.75f);
        assertEquals(1, late.from());
        assertEquals(2, late.to());
        CropVisual.Blend end = CropVisual.blend(1f);
        assertEquals(2, end.from());
        assertEquals(2, end.to());
        assertEquals(0f, end.amount(), 0.001f);
    }

    @Test
    public void growthOutsideRangeIsClamped() {
        assertEquals(0, CropVisual.blend(-1f).from());
        assertEquals(2, CropVisual.blend(4f).to());
    }
}
