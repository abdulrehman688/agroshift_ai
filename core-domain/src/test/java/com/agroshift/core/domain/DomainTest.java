package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.language.AppLanguage;
import com.agroshift.core.domain.language.ChatLanguage;
import com.agroshift.core.domain.season.SeasonPosition;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.Test;

public class DomainTest {
    @Test
    public void resultMapsSuccessAndKeepsFailure() {
        assertEquals(Integer.valueOf(4), Result.success("four").map(String::length).value());
        assertEquals("bad", Result.<String>failure("bad").map(String::length).errorCode());
    }

    @Test
    public void failedResultHasNoValue() {
        assertThrows(IllegalStateException.class, () -> Result.failure("bad").value());
    }

    @Test
    public void fieldTrimsValidInput() {
        Result<FarmField> result = FarmField.create("1", "  North  ", 4.5, " Wheat ", 10);
        assertTrue(result.isSuccess());
        assertEquals("North", result.value().name());
        assertEquals("Wheat", result.value().crop());
    }

    @Test
    public void fieldRejectsBlankNameAndBadAreas() {
        assertEquals(FarmField.ERROR_NAME, FarmField.create("1", " ", 1, "", 0).errorCode());
        for (double area : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 100_001}) {
            assertEquals(FarmField.ERROR_AREA, FarmField.create("1", "A", area, "", 0).errorCode());
        }
    }

    @Test
    public void seasonBoundaries() {
        assertEquals(SeasonPosition.Season.RABI, SeasonPosition.of(LocalDate.of(2026, 10, 1)).season());
        assertEquals(SeasonPosition.Season.RABI, SeasonPosition.of(LocalDate.of(2027, 3, 31)).season());
        assertEquals(SeasonPosition.Season.KHARIF, SeasonPosition.of(LocalDate.of(2027, 4, 1)).season());
        assertEquals(SeasonPosition.Season.KHARIF, SeasonPosition.of(LocalDate.of(2027, 9, 30)).season());
    }

    @Test
    public void seasonFractionStaysInYearIncludingLeapYear() {
        assertEquals(0.0, SeasonPosition.of(LocalDate.of(2026, 10, 1)).yearFraction(), 0.0);
        assertEquals(51.0 / 365, SeasonPosition.of(LocalDate.of(2026, 11, 21)).yearFraction(), 1e-9);
        assertTrue(SeasonPosition.of(LocalDate.of(2028, 9, 30)).yearFraction() < 1);
        assertEquals(151.0 / 366, SeasonPosition.of(LocalDate.of(2028, 2, 29)).yearFraction(), 1e-9);
    }

    @Test
    public void chatLanguagesOnlySelectWorkingOptions() {
        assertEquals(ChatLanguage.URDU, ChatLanguage.forLocale(Locale.forLanguageTag("ur-PK")));
        assertEquals(ChatLanguage.PUNJABI, ChatLanguage.forLocale(Locale.forLanguageTag("pa-Arab-PK")));
        assertEquals(ChatLanguage.ENGLISH, ChatLanguage.forLocale(Locale.forLanguageTag("ps-PK")));
        assertEquals(null, ChatLanguage.fromCode("ps"));
        assertTrue(ChatLanguage.SARAIKI.beta());
    }

    @Test
    public void waveOneLanguagesAreUniqueAndComplete() {
        assertEquals(7, AppLanguage.WAVE_ONE.size());
        assertEquals(7, new HashSet<>(AppLanguage.WAVE_ONE.stream()
                .map(AppLanguage::tag).collect(Collectors.toList())).size());
        assertTrue(AppLanguage.find("ur-PK").orElseThrow().rtl());
        assertFalse(AppLanguage.find("en-PK").orElseThrow().rtl());
        assertTrue(AppLanguage.find("xx").isEmpty());
    }
}
