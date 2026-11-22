package com.agroshift.core.domain.farm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Rules for showing and restoring deleted farm data. */
public final class RecycleBin {
    private RecycleBin() {
    }

    public static List<BinItem> newestFirst(List<BinItem> items) {
        List<BinItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(BinItem::deletedAtMillis).reversed().thenComparing(BinItem::id));
        return List.copyOf(sorted);
    }

    /** A null kind keeps every item. */
    public static List<BinItem> filter(List<BinItem> items, BinItem.Kind kind) {
        if (kind == null) {
            return List.copyOf(items);
        }
        List<BinItem> kept = new ArrayList<>();
        for (BinItem item : items) {
            if (item.kind() == kind) {
                kept.add(item);
            }
        }
        return List.copyOf(kept);
    }

    /** A record cannot be seen while its farm is deleted, so restoring it brings the farm back too. */
    public static boolean restoresFarm(BinItem item) {
        return item.kind() != BinItem.Kind.FARM && item.farmInBin();
    }
}
