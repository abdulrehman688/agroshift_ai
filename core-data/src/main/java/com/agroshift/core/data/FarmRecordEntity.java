package com.agroshift.core.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "farm_records", indices = {@Index("fieldId")})
public class FarmRecordEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String fieldId = "";
    @NonNull public String type = "";
    @NonNull public String title = "";
    public double amountRupees;
    public long dateMillis;
    public boolean done;
    public Long deletedAtMillis;
}
