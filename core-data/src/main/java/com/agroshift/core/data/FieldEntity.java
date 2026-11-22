package com.agroshift.core.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fields")
public class FieldEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String name = "";
    public double areaAcres;
    @NonNull public String crop = "";
    public long updatedAtMillis;
    public Long deletedAtMillis;
    public Double latitude;
    public Double longitude;
    public String rotationKey;
    public String waterAccess;
    public String goal;
    public String soil;
}
