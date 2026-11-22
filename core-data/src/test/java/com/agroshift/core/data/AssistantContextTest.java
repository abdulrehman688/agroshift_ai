package com.agroshift.core.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.engine.ClimateEngine;
import com.agroshift.core.domain.engine.ClimateProfile;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.DailyClimate;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.farm.FarmField;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class AssistantContextTest {
    private static final double SECRET_LATITUDE = 31.123456;
    private static final double SECRET_LONGITUDE = 73.654321;

    private static JsonObject sample() {
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
        FarmField field = FarmField.create("field-1", "Private field name", 4.5, SECRET_LATITUDE,
                SECRET_LONGITUDE, RotationPlan.RICE_WHEAT, WaterAccess.LIMITED, FarmGoal.SAVE_WATER,
                SoilType.CLAY, 1).value();
        Map<ClimateScenario, RotationAdvisor.Advice> outlooks = new EnumMap<>(ClimateScenario.class);
        for (ClimateScenario scenario : ClimateScenario.values()) {
            ClimateProfile profile = ClimateEngine.buildScenario(days, 31.0, scenario);
            outlooks.put(scenario, RotationAdvisor.advise(profile, field.rotationKey(), field.water(),
                    field.goal(), field.soil()));
        }
        return AssistantContext.build(field, outlooks);
    }

    @Test
    public void includesNasaClimateScoresRisksAndWhatIf() {
        JsonObject context = sample();
        JsonObject baseline = context.getAsJsonObject("climate").getAsJsonObject("baseline");
        assertEquals(12, baseline.getAsJsonArray("monthly").size());
        assertEquals(3, context.getAsJsonArray("strategies").size());
        assertTrue(context.getAsJsonArray("alerts").size() > 0);
        assertEquals(3, context.getAsJsonObject("location").getAsJsonArray("what_if_results").size());
    }

    @Test
    public void neverSharesExactLocationOrFieldName() {
        String json = sample().toString();
        assertFalse(json.contains("31.123456"));
        assertFalse(json.contains("73.654321"));
        assertFalse(json.contains("Private field name"));
        assertFalse(json.contains("\"latitude\""));
        assertFalse(json.contains("\"longitude\""));
    }

    @Test
    public void writesSamplePayloadForServiceCheck() throws Exception {
        File output = new File("build/assistant-context-sample.json");
        output.getParentFile().mkdirs();
        Files.write(output.toPath(), sample().toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(output.length() < 20_000);
    }
}
