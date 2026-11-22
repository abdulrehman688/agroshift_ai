package com.agroshift.core.domain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.farm.RecordStats;
import com.agroshift.core.domain.water.DayForecast;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Test;

public class RecordStatsTest {
    private static final ZoneId ZONE = ZoneOffset.UTC;

    private static long at(int year, int month) {
        return LocalDate.of(year, month, 10).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    private static FarmRecord record(String field, FarmRecord.Type type, String title, double amount, long date,
            boolean done) {
        return new FarmRecord(title + date, field, type, title, amount, date, done);
    }

    private static final List<FarmRecord> RECORDS = List.of(
            record("a", FarmRecord.Type.TASK, "Plough", 0, at(2026, 10), false),
            record("a", FarmRecord.Type.TASK, "Sow", 0, at(2026, 10), true),
            record("a", FarmRecord.Type.EXPENSE, "Seed", 500, at(2026, 10), false),
            record("a", FarmRecord.Type.SALE, "Wheat", 1000, at(2026, 11), false),
            record("b", FarmRecord.Type.SALE, "wheat", 700, at(2026, 9), false),
            record("b", FarmRecord.Type.SALE, "Cotton", 1200, at(2026, 11), false),
            record("b", FarmRecord.Type.NOTE, "Check canal", 0, at(2026, 11), false));

    @Test
    public void totalsCombineAllFarms() {
        RecordStats.Summary total = RecordStats.total(RECORDS);
        assertEquals(1, total.openTasks());
        assertEquals(1, total.doneTasks());
        assertEquals(500, total.spent(), 0);
        assertEquals(2900, total.earned(), 0);
        assertEquals(2400, total.profit(), 0);
        assertEquals(3, total.sales());
    }

    @Test
    public void statsAreSeparatedByFarm() {
        assertEquals(500, RecordStats.byField(RECORDS).get("a").profit(), 0);
        assertEquals(1900, RecordStats.byField(RECORDS).get("b").profit(), 0);
    }

    @Test
    public void filterKeepsOnlyChosenType() {
        assertEquals(3, RecordStats.ofType(RECORDS, FarmRecord.Type.SALE).size());
        assertEquals(RECORDS.size(), RecordStats.ofType(RECORDS, null).size());
    }

    @Test
    public void monthlySalesFallIntoCorrectBars() {
        assertArrayEquals(new double[] {700, 0, 2200},
                RecordStats.monthlySales(RECORDS, YearMonth.of(2026, 11), 3, ZONE), 0);
    }

    @Test
    public void topSaleCombinesSameCropIgnoringCase() {
        assertEquals("Wheat", RecordStats.topSale(RECORDS));
        assertNull(RecordStats.topSale(List.of()));
    }

    @Test
    public void weatherCodesMapToIcons() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        assertEquals(DayForecast.Kind.SUN, new DayForecast(day, 0, 0, 1, 30, 20, 5, 0).kind());
        assertEquals(DayForecast.Kind.CLOUD, new DayForecast(day, 0, 0, 1, 30, 20, 5, 3).kind());
        assertEquals(DayForecast.Kind.RAIN, new DayForecast(day, 5, 80, 1, 30, 20, 5, 61).kind());
        assertEquals(DayForecast.Kind.STORM, new DayForecast(day, 5, 80, 1, 30, 20, 5, 95).kind());
        assertEquals(DayForecast.Kind.RAIN, new DayForecast(day, 5, 80, 1, 30, 20, 5).kind());
    }
}
