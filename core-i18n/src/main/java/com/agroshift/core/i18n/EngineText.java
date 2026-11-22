package com.agroshift.core.i18n;

import android.content.Context;
import androidx.annotation.StringRes;
import com.agroshift.core.domain.engine.ClimateAlert;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.RotationStep;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Turns engine results into short, plain language. */
public final class EngineText {
    private EngineText() {
    }

    public static String rotationName(Context context, String key) {
        try {
            return cropSequence(context, RotationPlan.template(key));
        } catch (IllegalArgumentException unknown) {
            return context.getString(R.string.crop_unknown);
        }
    }

    public static String optionName(Context context, RotationAdvisor.Kind kind) {
        return context.getString(switch (kind) {
            case CURRENT -> R.string.option_current;
            case WATER_SAVER -> R.string.option_water_saver;
            case SOIL_BUILDER -> R.string.option_soil_builder;
        });
    }

    public static String cropSequence(Context context, RotationPlan plan) {
        Set<String> names = new LinkedHashSet<>();
        for (RotationStep step : plan.steps()) {
            names.add(cropName(context, step.cropSlug()));
        }
        return String.join(context.getString(R.string.list_separator), names);
    }

    public static String cropName(Context context, String slug) {
        return context.getString(switch (slug) {
            case "wheat" -> R.string.crop_wheat;
            case "cotton" -> R.string.crop_cotton;
            case "rice" -> R.string.crop_rice;
            case "sorghum" -> R.string.crop_sorghum;
            case "chickpea" -> R.string.crop_chickpea;
            case "mungbean" -> R.string.crop_mungbean;
            case "berseem" -> R.string.crop_berseem;
            case "maize" -> R.string.crop_maize;
            case "mustard" -> R.string.crop_mustard;
            case "oats" -> R.string.crop_oats;
            case "alfalfa" -> R.string.crop_alfalfa;
            default -> R.string.crop_unknown;
        });
    }

    public static String fit(Context context, RotationAdvisor.Fit fit) {
        return context.getString(switch (fit) {
            case GOOD -> R.string.fit_good;
            case OKAY -> R.string.fit_okay;
            case RISKY -> R.string.fit_risky;
        });
    }

    public static String benefit(Context context, RotationAdvisor.Benefit benefit) {
        return context.getString(switch (benefit) {
            case ALREADY_BEST -> R.string.benefit_already;
            case LESS_WATER -> R.string.benefit_water;
            case SAFER_CLIMATE -> R.string.benefit_climate;
            case BETTER_SOIL -> R.string.benefit_soil;
            case MORE_INCOME -> R.string.benefit_income;
            case BALANCED -> R.string.benefit_balanced;
        });
    }

    public static String alertLabel(Context context, ClimateAlert alert) {
        return context.getString(alert.severity().compareTo(ClimateAlert.Severity.HIGH) >= 0
                ? R.string.label_serious : R.string.label_watch);
    }

    public static String alertTitle(Context context, ClimateAlert alert) {
        @StringRes int text = switch (alert.type()) {
            case HEAT -> R.string.plain_heat;
            case DROUGHT -> R.string.plain_drought;
            case WATER_DEFICIT -> R.string.plain_water;
            case FROST -> R.string.plain_frost;
            case WATERLOGGING -> R.string.plain_flood;
            case SOIL_DRYING -> R.string.plain_soil;
        };
        return context.getString(text, month(context, alert.month()), crops(context, alert));
    }

    public static String alertTip(Context context, ClimateAlert alert) {
        return context.getString(switch (alert.type()) {
            case HEAT -> R.string.tip_heat;
            case DROUGHT -> R.string.tip_drought;
            case WATER_DEFICIT -> R.string.tip_water;
            case FROST -> R.string.tip_frost;
            case WATERLOGGING -> R.string.tip_flood;
            case SOIL_DRYING -> R.string.tip_soil;
        });
    }

    /** The most important risks, at most one per kind of problem. */
    public static List<ClimateAlert> topAlerts(List<ClimateAlert> alerts, int limit) {
        List<ClimateAlert> top = new ArrayList<>();
        Set<ClimateAlert.Type> seen = new LinkedHashSet<>();
        for (ClimateAlert alert : alerts) {
            if (seen.add(alert.type())) {
                top.add(alert);
                if (top.size() == limit) {
                    break;
                }
            }
        }
        return top;
    }

    public static String severity(Context context, ClimateAlert.Severity severity) {
        return context.getString(switch (severity) {
            case LOW -> R.string.severity_low;
            case MODERATE -> R.string.severity_moderate;
            case HIGH -> R.string.severity_high;
            case CRITICAL -> R.string.severity_critical;
        });
    }

    public static String alertName(Context context, ClimateAlert alert) {
        return context.getString(switch (alert.type()) {
            case HEAT -> R.string.alert_heat;
            case DROUGHT -> R.string.alert_drought;
            case WATER_DEFICIT -> R.string.alert_water_deficit;
            case FROST -> R.string.alert_frost;
            case WATERLOGGING -> R.string.alert_waterlogging;
            case SOIL_DRYING -> R.string.alert_soil_drying;
        });
    }

    public static String alertReason(Context context, ClimateAlert alert) {
        @StringRes int text = switch (alert.type()) {
            case HEAT -> R.string.reason_heat;
            case DROUGHT -> R.string.reason_drought;
            case WATER_DEFICIT -> R.string.reason_water_deficit;
            case FROST -> R.string.reason_frost;
            case WATERLOGGING -> R.string.reason_waterlogging;
            case SOIL_DRYING -> R.string.reason_soil_drying;
        };
        return alertText(context, alert, text);
    }

    public static String alertAction(Context context, ClimateAlert alert) {
        @StringRes int text = switch (alert.type()) {
            case HEAT -> R.string.action_heat;
            case DROUGHT -> R.string.action_drought;
            case WATER_DEFICIT -> R.string.action_water_deficit;
            case FROST -> R.string.action_frost;
            case WATERLOGGING -> R.string.action_waterlogging;
            case SOIL_DRYING -> R.string.action_soil_drying;
        };
        return alertText(context, alert, text);
    }

    private static String alertText(Context context, ClimateAlert alert, @StringRes int text) {
        String value = switch (alert.type()) {
            case DROUGHT -> number(alert.value() * 100, 0);
            case SOIL_DRYING -> number(alert.value(), 1);
            default -> number(alert.value(), 0);
        };
        return context.getString(text, month(context, alert.month()), crops(context, alert),
                stage(context, alert.stageKey()), value, number(alert.threshold(), 0));
    }

    private static String stage(Context context, String key) {
        return context.getString(switch (key) {
            case "establishment" -> R.string.stage_establishment;
            case "transplant_establishment" -> R.string.stage_transplant;
            case "tillering" -> R.string.stage_tillering;
            case "heading" -> R.string.stage_heading;
            case "grain_fill" -> R.string.stage_grain_fill;
            case "vegetative" -> R.string.stage_vegetative;
            case "flowering" -> R.string.stage_flowering;
            case "flowering_boll_set" -> R.string.stage_flowering_boll;
            case "boll_opening" -> R.string.stage_boll_opening;
            case "pod_fill" -> R.string.stage_pod_fill;
            case "flowering_pod_fill" -> R.string.stage_flowering_pod;
            case "cutting_season" -> R.string.stage_cutting;
            case "growth_cuts" -> R.string.stage_growth_cuts;
            default -> R.string.stage_growing;
        });
    }

    public static String month(Context context, int month) {
        return context.getResources().getStringArray(R.array.months)[month - 1];
    }

    public static String number(double value, int decimals) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(decimals);
        format.setMinimumFractionDigits(decimals);
        return format.format(value);
    }

    public static String rupees(Context context, double value) {
        return context.getString(R.string.money_value, number(value, 0));
    }

    private static String crops(Context context, ClimateAlert alert) {
        List<String> names = new ArrayList<>();
        for (String slug : alert.cropSlugs()) {
            names.add(cropName(context, slug));
        }
        return String.join(context.getString(R.string.list_separator), names);
    }
}
