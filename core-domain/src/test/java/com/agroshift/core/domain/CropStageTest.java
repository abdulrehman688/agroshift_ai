package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;

import com.agroshift.core.domain.scene.CropStage;
import org.junit.Test;

public class CropStageTest {
    @Test
    public void startAndEndOfSeasonUseFirstAndLastPhoto() {
        assertEquals(0, CropStage.index(0f, 6));
        assertEquals(5, CropStage.index(1f, 6));
    }

    @Test
    public void growthMovesThroughEveryPhotoInOrder() {
        int previous = -1;
        for (int step = 0; step <= 100; step++) {
            int index = CropStage.index(step / 100f, 5);
            assertEquals(true, index == previous || index == previous + 1);
            previous = index;
        }
        assertEquals(4, previous);
    }

    @Test
    public void unusualValuesStayInRange() {
        assertEquals(0, CropStage.index(-2f, 5));
        assertEquals(4, CropStage.index(3f, 5));
        assertEquals(0, CropStage.index(0.7f, 1));
    }
}
