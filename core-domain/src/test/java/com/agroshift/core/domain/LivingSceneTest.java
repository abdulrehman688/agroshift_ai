package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.scene.LivingScene;
import com.agroshift.core.domain.water.CanalTurn;
import com.agroshift.core.domain.water.SkyState;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import org.junit.Test;

/** Real farm data must decide what the living photo shows. */
public class LivingSceneTest {
    private static final CanalTurn TURN = new CanalTurn(DayOfWeek.WEDNESDAY, 12 * 60, 60, 30);
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 6, 10, 12, 30);

    @Test
    public void runningTurnFlowsFastAndShowsProgress() {
        LivingScene scene = LivingScene.forCanal(TURN, NOON, null);
        assertEquals(1f, scene.flow(), 0.001f);
        assertEquals(0.5f, scene.turnProgress(), 0.01f);
    }

    @Test
    public void waterIsCalmBeforeTurnAndBuildsWhenItIsNear() {
        LivingScene farAway = LivingScene.forCanal(TURN, LocalDateTime.of(2026, 6, 8, 12, 0), null);
        LivingScene soon = LivingScene.forCanal(TURN, LocalDateTime.of(2026, 6, 10, 11, 45), null);
        LivingScene none = LivingScene.forCanal(null, NOON, null);
        assertTrue(farAway.flow() < soon.flow());
        assertTrue(soon.flow() < 1f);
        assertEquals(farAway.flow(), none.flow(), 0.001f);
        assertEquals(0f, soon.turnProgress(), 0f);
    }

    @Test
    public void weatherControlsRainWindCloudAndHeat() {
        LivingScene wet = LivingScene.forCanal(null, NOON, new SkyState(true, 80, 3, 45, 20));
        LivingScene hot = LivingScene.forCanal(null, NOON, new SkyState(true, 0, 0, 0, 44));
        assertTrue(wet.rain() > 0.4f);
        assertEquals(1f, wet.wind(), 0.001f);
        assertEquals(0.8f, wet.cloud(), 0.001f);
        assertEquals(0f, wet.heat(), 0f);
        assertEquals(1f, hot.heat(), 0.001f);
        assertEquals(0f, hot.rain(), 0f);
    }

    @Test
    public void lightRainIsStillVisible() {
        LivingScene drizzle = LivingScene.forCanal(null, NOON, new SkyState(true, 90, 0.1, 5, 20));
        assertTrue(drizzle.rain() >= 0.25f);
    }

    @Test
    public void clockControlsDayGoldenHourAndNight() {
        LivingScene day = LivingScene.forCanal(null, NOON, null);
        LivingScene evening = LivingScene.forCanal(null, LocalDateTime.of(2026, 6, 10, 18, 0), null);
        LivingScene night = LivingScene.forCanal(null, LocalDateTime.of(2026, 6, 10, 23, 0), null);
        assertEquals(1f, day.daylight(), 0.001f);
        assertEquals(0f, day.golden(), 0.001f);
        assertTrue(evening.golden() > 0.5f);
        assertEquals(0f, night.daylight(), 0.001f);
    }

    @Test
    public void weatherServiceNightOverridesClock() {
        LivingScene scene = LivingScene.forCanal(null, NOON, new SkyState(false, 0, 0, 0, 20));
        assertEquals(0f, scene.daylight(), 0f);
    }

    @Test
    public void fieldSceneUsesWeatherWithoutCanalFlow() {
        LivingScene scene = LivingScene.forField(NOON, new SkyState(true, 40, 0, 30, 25));
        assertEquals(0f, scene.flow(), 0f);
        assertEquals(30f / 45f, scene.wind(), 0.001f);
        assertEquals(0f, scene.dryness(), 0f);
        assertEquals(1f, scene.health(), 0f);
    }

    @Test
    public void whatIfMakesFieldDrierHotterAndLessHealthy() {
        LivingScene base = LivingScene.forField(NOON, null);
        LivingScene stressed = base.withWhatIf(true, true, 0.3f);
        assertEquals(1f, stressed.dryness(), 0f);
        assertEquals(0.3f, stressed.health(), 0.001f);
        assertTrue(stressed.heat() >= 0.8f);
        assertEquals(0f, stressed.rain(), 0f);
        assertEquals(0f, base.withWhatIf(false, false, 2f).dryness(), 0f);
        assertEquals(1f, base.withWhatIf(false, false, 2f).health(), 0f);
    }
}
