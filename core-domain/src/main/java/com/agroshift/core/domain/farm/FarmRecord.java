package com.agroshift.core.domain.farm;

import com.agroshift.core.domain.Result;
import java.util.List;

/** Immutable task, cost, sale or note for a field. */
public record FarmRecord(String id, String fieldId, Type type, String title, double amountRupees,
                         long dateMillis, boolean done) {
    public enum Type { TASK, EXPENSE, SALE, NOTE }

    public static final String ERROR_TITLE = "record_title_required";
    public static final String ERROR_AMOUNT = "record_amount_invalid";
    public static final double MAX_AMOUNT = 1_000_000_000;

    public FarmRecord {
        title = title == null ? "" : title.strip();
    }

    public static Result<FarmRecord> create(String id, String fieldId, Type type, String title,
            double amountRupees, long dateMillis) {
        FarmRecord record = new FarmRecord(id, fieldId, type, title, amountRupees, dateMillis, false);
        if (record.title().isEmpty()) {
            return Result.failure(ERROR_TITLE);
        }
        boolean money = type == Type.EXPENSE || type == Type.SALE;
        if (money && (!Double.isFinite(amountRupees) || amountRupees <= 0 || amountRupees > MAX_AMOUNT)) {
            return Result.failure(ERROR_AMOUNT);
        }
        return Result.success(record);
    }

    public record Money(double spent, double earned) {
        public double profit() {
            return earned - spent;
        }
    }

    public static Money totals(List<FarmRecord> records) {
        double spent = 0;
        double earned = 0;
        for (FarmRecord record : records) {
            if (record.type() == Type.EXPENSE) {
                spent += record.amountRupees();
            } else if (record.type() == Type.SALE) {
                earned += record.amountRupees();
            }
        }
        return new Money(spent, earned);
    }
}
