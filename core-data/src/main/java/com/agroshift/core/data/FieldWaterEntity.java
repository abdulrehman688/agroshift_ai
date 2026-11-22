package com.agroshift.core.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "field_water")
public class FieldWaterEntity {
    @PrimaryKey @NonNull public String fieldId = "";
    public Long lastWateredEpochDay;
    public Integer turnDay;
    public Integer turnStartMinute;
    public Integer turnDurationMinutes;
    public Integer turnRemindMinutes;
    public long updatedAtMillis;
}
