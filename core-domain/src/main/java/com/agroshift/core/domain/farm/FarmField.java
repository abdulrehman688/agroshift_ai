package com.agroshift.core.domain.farm;

import com.agroshift.core.domain.Result;
import com.agroshift.core.domain.Validator;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;

/** Immutable farm field. Use {@link #create} for user-entered values. */
public record FarmField(String id, String name, double areaAcres, String crop, long updatedAtMillis,
                        Double latitude, Double longitude, String rotationKey, WaterAccess water,
                        FarmGoal goal, SoilType soil) {
    public static final String ERROR_NAME = "field_name_required";
    public static final String ERROR_AREA = "field_area_invalid";
    public static final String ERROR_LOCATION = "field_location_outside_pakistan_plains";
    public static final double MAX_AREA_ACRES = 100_000;
    public static final double MIN_LATITUDE = 23.5;
    public static final double MAX_LATITUDE = 35.0;
    public static final double MIN_LONGITUDE = 60.5;
    public static final double MAX_LONGITUDE = 77.9;

    private static final Validator<FarmField> VALIDATOR =
            ((Validator<FarmField>) field -> field.name().isEmpty()
                    ? Result.failure(ERROR_NAME)
                    : Result.success(field))
                    .and(field -> Double.isFinite(field.areaAcres())
                            && field.areaAcres() > 0
                            && field.areaAcres() <= MAX_AREA_ACRES
                            ? Result.success(field)
                            : Result.failure(ERROR_AREA))
                    .and(field -> !field.hasLocation() || isSupportedLocation(field.latitude(), field.longitude())
                            ? Result.success(field)
                            : Result.failure(ERROR_LOCATION));

    public FarmField {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        name = name == null ? "" : name.strip();
        crop = crop == null ? "" : crop.strip();
        rotationKey = rotationKey == null ? RotationPlan.COTTON_WHEAT : rotationKey;
        water = water == null ? WaterAccess.IRRIGATED : water;
        goal = goal == null ? FarmGoal.BALANCED : goal;
        soil = soil == null ? SoilType.UNKNOWN : soil;
    }

    public FarmField(String id, String name, double areaAcres, String crop, long updatedAtMillis) {
        this(id, name, areaAcres, crop, updatedAtMillis, null, null, null, null, null, null);
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public static boolean isSupportedLocation(double latitude, double longitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE
                && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    public static Result<FarmField> create(
            String id, String name, double areaAcres, String crop, long updatedAtMillis) {
        return VALIDATOR.validate(new FarmField(id, name, areaAcres, crop, updatedAtMillis));
    }

    public static Result<FarmField> create(String id, String name, double areaAcres, double latitude,
            double longitude, String rotationKey, WaterAccess water, FarmGoal goal, long updatedAtMillis) {
        return create(id, name, areaAcres, latitude, longitude, rotationKey, water, goal,
                SoilType.UNKNOWN, updatedAtMillis);
    }

    public static Result<FarmField> create(String id, String name, double areaAcres, double latitude,
            double longitude, String rotationKey, WaterAccess water, FarmGoal goal, SoilType soil,
            long updatedAtMillis) {
        return VALIDATOR.validate(new FarmField(id, name, areaAcres, rotationKey, updatedAtMillis, latitude,
                longitude, rotationKey, water, goal, soil));
    }
}
