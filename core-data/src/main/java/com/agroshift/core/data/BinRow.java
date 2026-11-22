package com.agroshift.core.data;

import androidx.annotation.NonNull;

/** One deleted farm or record read from Room for the recycle bin. */
public class BinRow {
    @NonNull public String kind = "";
    @NonNull public String id = "";
    @NonNull public String fieldId = "";
    @NonNull public String title = "";
    @NonNull public String farmName = "";
    public double amountRupees;
    public long deletedAtMillis;
    public boolean farmInBin;
    public int recordCount;
}
