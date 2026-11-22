package com.agroshift.core.domain.scene;

import com.agroshift.core.domain.water.CanalTurn;
import com.agroshift.core.domain.water.SkyState;
import java.time.LocalDateTime;

/** Immutable visual state for a living photo. Every value is from 0 to 1 and comes from farm data. */
public record LivingScene(float flow, float turnProgress, float rain, float wind, float cloud, float daylight,
                          float golden, float heat) {
    private static final float CALM_FLOW = 0.2f;
    private static final float NEAR_FLOW = 0.55f;
    private static final int NEAR_MINUTES = 60;

    public static LivingScene forCanal(CanalTurn turn, LocalDateTime now, SkyState sky) {
        float flow = CALM_FLOW;
        float progress = 0f;
        if (turn != null) {
            if (turn.isActive(now)) {
                flow = 1f;
                progress = turn.progress(now);
            } else if (turn.untilStart(now).toMinutes() < Math.max(NEAR_MINUTES, turn.remindMinutes())) {
                flow = NEAR_FLOW;
            }
        }
        float hour = now.getHour() + now.getMinute() / 60f;
        float daylight = smooth(5.5f, 7.5f, hour) * (1f - smooth(17.5f, 19.5f, hour));
        float golden = Math.max(bump(hour, 6.5f), bump(hour, 18f));
        float rain = 0f;
        float wind = 0f;
        float cloud = 0f;
        float heat = 0f;
        if (sky != null) {
            if (sky.rainMmPerHour() >= 0.1) {
                rain = clamp(Math.max(0.25f, (float) sky.rainMmPerHour() / 6f));
            }
            wind = clamp((float) sky.windKmh() / 45f);
            cloud = clamp(sky.cloudPercent() / 100f);
            heat = clamp(((float) sky.tempC() - 34f) / 10f);
            if (!sky.day()) {
                daylight = 0f;
                golden = 0f;
            }
        }
        return new LivingScene(flow, progress, rain, wind, cloud, daylight, golden, heat);
    }

    private static float bump(float hour, float center) {
        return Math.max(0f, 1f - Math.abs(hour - center) / 1.5f);
    }

    private static float smooth(float from, float to, float value) {
        float t = clamp((value - from) / (to - from));
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
