package com.agroshift.core.i18n;

import android.content.Context;
import com.agroshift.core.domain.water.CanalTurn;
import com.agroshift.core.domain.water.DangerAlert;
import com.agroshift.core.domain.water.IrrigationAdvisor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;

/** Localized wording for irrigation answers, canal turns and danger alerts. */
public final class WaterText {
    private WaterText() {
    }

    public static String title(Context context, IrrigationAdvisor.Answer answer) {
        return context.getString(switch (answer.decision()) {
            case IRRIGATE -> R.string.water_irrigate;
            case WAIT_FOR_RAIN -> R.string.water_wait;
            case SOON -> R.string.water_soon;
            case NOT_NEEDED -> R.string.water_not_needed;
            case RAINFED -> R.string.water_answer_rainfed;
            case NEED_LAST_WATERING -> R.string.water_need_last;
            case NO_DATA -> R.string.water_no_data;
        });
    }

    public static String reason(Context context, IrrigationAdvisor.Answer answer) {
        return switch (answer.decision()) {
            case IRRIGATE -> context.getString(R.string.water_reason_irrigate, EngineText.number(answer.usedMm(), 0));
            case WAIT_FOR_RAIN -> context.getString(R.string.water_reason_wait, day(answer.rainDate()));
            case SOON -> context.getString(R.string.water_reason_soon, EngineText.number(answer.daysUntilNeeded(), 0));
            case NOT_NEEDED -> answer.daysUntilNeeded() > 0
                    ? context.getString(R.string.water_reason_ok, EngineText.number(answer.daysUntilNeeded(), 0))
                    : context.getString(R.string.water_reason_ok_week);
            case RAINFED -> context.getString(R.string.water_reason_rainfed);
            case NEED_LAST_WATERING -> context.getString(R.string.water_reason_need_last);
            case NO_DATA -> context.getString(R.string.water_reason_no_data);
        };
    }

    public static String nextTurn(Context context, CanalTurn turn) {
        LocalDateTime now = LocalDateTime.now();
        if (turn.isActive(now)) {
            return context.getString(R.string.turn_active);
        }
        LocalDateTime next = turn.nextStart(now);
        return context.getString(R.string.turn_next, day(next.toLocalDate()), time(next.toLocalTime()));
    }

    public static String time(LocalTime time) {
        return time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
    }

    public static String day(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    public static String dangerTitle(Context context, DangerAlert alert) {
        return context.getString(switch (alert.type()) {
            case FROST -> R.string.danger_frost;
            case HEAT -> R.string.danger_heat;
            case HEAVY_RAIN -> R.string.danger_rain;
            case WIND -> R.string.danger_wind;
        });
    }

    public static String dangerSeverity(Context context, DangerAlert alert) {
        return context.getString(alert.severity() == DangerAlert.Severity.HIGH
                ? R.string.danger_high : R.string.danger_watch);
    }

    public static String dangerDetail(Context context, DangerAlert alert) {
        String value = EngineText.number(alert.value(), 0);
        String day = day(alert.date());
        return context.getString(switch (alert.type()) {
            case FROST -> R.string.danger_frost_detail;
            case HEAT -> R.string.danger_heat_detail;
            case HEAVY_RAIN -> R.string.danger_rain_detail;
            case WIND -> R.string.danger_wind_detail;
        }, value, day);
    }
}
