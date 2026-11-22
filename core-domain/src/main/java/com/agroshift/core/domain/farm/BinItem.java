package com.agroshift.core.domain.farm;

/** A deleted farm, task, cost, sale or note that can still be restored. */
public record BinItem(Kind kind, String id, String fieldId, String title, String farmName, double amountRupees,
                      long deletedAtMillis, boolean farmInBin, int recordCount) {
    public enum Kind {
        FARM, TASK, EXPENSE, SALE, NOTE;

        public static Kind parse(String value) {
            return valueOf(value);
        }
    }
}
