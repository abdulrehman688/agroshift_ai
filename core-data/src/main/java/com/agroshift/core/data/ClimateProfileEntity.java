package com.agroshift.core.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Saved NASA climate summary for one coarse POWER grid cell. */
@Entity(tableName = "climate_profiles")
public class ClimateProfileEntity {
    @PrimaryKey @NonNull public String cellKey = "";
    public double latitude;
    public double longitude;
    public int startYear;
    public int endYear;
    public long fetchedAtMillis;
    @NonNull public String profileJson = "{}";
}
