package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.water.CanalTurn;
import com.agroshift.core.domain.water.DangerAlert;
import com.agroshift.core.domain.water.DayForecast;
import com.agroshift.core.domain.water.IrrigationAdvisor;
import com.agroshift.core.domain.water.IrrigationAdvisor.Decision;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class WaterTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);

    private static List<DayForecast> dry(double et0) {
        List<DayForecast> days = new ArrayList<>();
        for (int i = -20; i <= 6; i++) {
            days.add(new DayForecast(TODAY.plusDays(i), 0, 0, et0, 35, 22, 10));
        }
        return days;
    }

    private static void rainOn(List<DayForecast> days, LocalDate date, double mm, int chance) {
        days.replaceAll(day -> day.date().equals(date)
                ? new DayForecast(date, mm, chance, day.et0Mm(), day.maxC(), day.minC(), day.windKmh()) : day);
    }

    private static IrrigationAdvisor.Answer decide(List<DayForecast> days, LocalDate last, SoilType soil) {
        return IrrigationAdvisor.decide(days, last, TODAY, WaterAccess.IRRIGATED, soil);
    }

    @Test
    public void dryFieldPastItsLimitNeedsWater() {
        IrrigationAdvisor.Answer answer = decide(dry(6), TODAY.minusDays(9), SoilType.LOAM);
        assertEquals(Decision.IRRIGATE, answer.decision());
        assertEquals(48, answer.usedMm(), 0.01);
    }

    @Test
    public void likelyRainDelaysWatering() {
        List<DayForecast> days = dry(6);
        rainOn(days, TODAY.plusDays(1), 30, 80);
        IrrigationAdvisor.Answer answer = decide(days, TODAY.minusDays(9), SoilType.LOAM);
        assertEquals(Decision.WAIT_FOR_RAIN, answer.decision());
        assertEquals(TODAY.plusDays(1), answer.rainDate());
    }

    @Test
    public void unlikelyRainDoesNotDelayWatering() {
        List<DayForecast> days = dry(6);
        rainOn(days, TODAY.plusDays(1), 30, 20);
        assertEquals(Decision.IRRIGATE, decide(days, TODAY.minusDays(9), SoilType.LOAM).decision());
    }

    @Test
    public void sandySoilNeedsWaterSoonerThanClay() {
        LocalDate last = TODAY.minusDays(6);
        assertEquals(Decision.IRRIGATE, decide(dry(6), last, SoilType.SANDY).decision());
        assertEquals(Decision.NOT_NEEDED, decide(dry(6), last, SoilType.CLAY).decision());
    }

    @Test
    public void nearLimitSaysSoon() {
        IrrigationAdvisor.Answer answer = decide(dry(6), TODAY.minusDays(7), SoilType.LOAM);
        assertEquals(Decision.SOON, answer.decision());
        assertEquals(2, answer.daysUntilNeeded());
    }

    @Test
    public void pastRainRefillsTheSoil() {
        List<DayForecast> days = dry(6);
        rainOn(days, TODAY.minusDays(1), 60, 90);
        assertEquals(Decision.NOT_NEEDED, decide(days, TODAY.minusDays(9), SoilType.LOAM).decision());
    }

    @Test
    public void missingEvidenceIsNeverGuessed() {
        assertEquals(Decision.NEED_LAST_WATERING, decide(dry(6), null, SoilType.LOAM).decision());
        assertEquals(Decision.NEED_LAST_WATERING, decide(dry(6), TODAY.minusDays(40), SoilType.LOAM).decision());
        assertEquals(Decision.NEED_LAST_WATERING, decide(dry(6), TODAY.plusDays(1), SoilType.LOAM).decision());
        assertEquals(Decision.NO_DATA, decide(List.of(), TODAY.minusDays(3), SoilType.LOAM).decision());
        List<DayForecast> gap = dry(6);
        gap.removeIf(day -> day.date().equals(TODAY.minusDays(2)));
        assertEquals(Decision.NO_DATA, decide(gap, TODAY.minusDays(5), SoilType.LOAM).decision());
    }

    @Test
    public void rainfedFieldGetsNoIrrigationInstruction() {
        assertEquals(Decision.RAINFED, IrrigationAdvisor.decide(dry(6), TODAY.minusDays(9), TODAY,
                WaterAccess.RAINFED, SoilType.LOAM).decision());
    }

    @Test
    public void dangerAlertsUseThreeDayWindowAndWorstFirst() {
        List<DayForecast> days = List.of(
                new DayForecast(TODAY.minusDays(1), 0, 0, 5, 50, 22, 10),
                new DayForecast(TODAY, 0, 0, 5, 43, 22, 10),
                new DayForecast(TODAY.plusDays(1), 70, 90, 5, 35, 22, 10),
                new DayForecast(TODAY.plusDays(2), 0, 0, 5, 46, 22, 45),
                new DayForecast(TODAY.plusDays(3), 0, 0, 5, 35, -3, 90));
        List<DangerAlert> alerts = DangerAlert.find(days, TODAY);
        assertEquals(3, alerts.size());
        assertEquals(DangerAlert.Type.HEAVY_RAIN, alerts.get(0).type());
        assertEquals(DangerAlert.Type.HEAT, alerts.get(1).type());
        assertEquals(DangerAlert.Severity.HIGH, alerts.get(1).severity());
        assertEquals(TODAY.plusDays(2), alerts.get(1).date());
        assertEquals(DangerAlert.Type.WIND, alerts.get(2).type());
        assertEquals(DangerAlert.Severity.WATCH, alerts.get(2).severity());
    }

    @Test
    public void unlikelyRainIsNotADanger() {
        List<DayForecast> days = List.of(new DayForecast(TODAY, 80, 20, 5, 30, 20, 10));
        assertTrue(DangerAlert.find(days, TODAY).isEmpty());
    }

    @Test
    public void frostThresholds() {
        List<DangerAlert> watch = DangerAlert.find(List.of(new DayForecast(TODAY, 0, 0, 1, 12, 2, 5)), TODAY);
        List<DangerAlert> high = DangerAlert.find(List.of(new DayForecast(TODAY, 0, 0, 1, 12, -1, 5)), TODAY);
        assertEquals(DangerAlert.Severity.WATCH, watch.get(0).severity());
        assertEquals(DangerAlert.Severity.HIGH, high.get(0).severity());
    }

    @Test
    public void canalTurnFindsNextWeeklyStart() {
        CanalTurn turn = new CanalTurn(DayOfWeek.WEDNESDAY, 6 * 60, 90, 30);
        assertEquals(LocalDateTime.of(2026, 6, 10, 6, 0), turn.nextStart(LocalDateTime.of(2026, 6, 9, 12, 0)));
        assertEquals(LocalDateTime.of(2026, 6, 17, 6, 0), turn.nextStart(LocalDateTime.of(2026, 6, 10, 6, 0)));
    }

    @Test
    public void canalAlarmFiresBeforeThenAtStart() {
        CanalTurn turn = new CanalTurn(DayOfWeek.WEDNESDAY, 6 * 60, 90, 30);
        assertEquals(LocalDateTime.of(2026, 6, 10, 5, 30), turn.nextAlarm(LocalDateTime.of(2026, 6, 9, 12, 0)));
        assertEquals(LocalDateTime.of(2026, 6, 10, 6, 0), turn.nextAlarm(LocalDateTime.of(2026, 6, 10, 5, 30)));
        assertEquals(LocalDateTime.of(2026, 6, 17, 5, 30), turn.nextAlarm(LocalDateTime.of(2026, 6, 10, 6, 0)));
    }

    @Test
    public void canalTurnKnowsWhenActiveAcrossMidnight() {
        CanalTurn turn = new CanalTurn(DayOfWeek.TUESDAY, 23 * 60, 120, 15);
        assertTrue(turn.isActive(LocalDateTime.of(2026, 6, 10, 0, 30)));
        assertFalse(turn.isActive(LocalDateTime.of(2026, 6, 10, 1, 0)));
    }

    @Test
    public void canalTurnRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new CanalTurn(DayOfWeek.MONDAY, 1440, 60, 15));
        assertThrows(IllegalArgumentException.class, () -> new CanalTurn(DayOfWeek.MONDAY, 60, 0, 15));
        assertThrows(IllegalArgumentException.class, () -> new CanalTurn(DayOfWeek.MONDAY, 60, 60, -1));
    }
}
