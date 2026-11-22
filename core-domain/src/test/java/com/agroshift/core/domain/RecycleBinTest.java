package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.farm.BinItem;
import com.agroshift.core.domain.farm.RecycleBin;
import java.util.List;
import org.junit.Test;

public class RecycleBinTest {
    private static BinItem item(BinItem.Kind kind, String id, long deletedAt, boolean farmInBin) {
        return new BinItem(kind, id, "field", id, "Home farm", 0, deletedAt, farmInBin, 0);
    }

    @Test
    public void newestDeletedItemsComeFirst() {
        List<BinItem> sorted = RecycleBin.newestFirst(List.of(item(BinItem.Kind.NOTE, "old", 10, false),
                item(BinItem.Kind.FARM, "new", 30, true), item(BinItem.Kind.TASK, "middle", 20, false)));
        assertEquals("new", sorted.get(0).id());
        assertEquals("middle", sorted.get(1).id());
        assertEquals("old", sorted.get(2).id());
    }

    @Test
    public void filterKeepsOneKindOrEverything() {
        List<BinItem> items = List.of(item(BinItem.Kind.SALE, "sale", 1, false), item(BinItem.Kind.EXPENSE, "cost", 2, false));
        assertEquals(2, RecycleBin.filter(items, null).size());
        assertEquals("cost", RecycleBin.filter(items, BinItem.Kind.EXPENSE).get(0).id());
        assertTrue(RecycleBin.filter(items, BinItem.Kind.FARM).isEmpty());
    }

    @Test
    public void recordFromDeletedFarmBringsItsFarmBack() {
        assertTrue(RecycleBin.restoresFarm(item(BinItem.Kind.NOTE, "note", 1, true)));
        assertFalse(RecycleBin.restoresFarm(item(BinItem.Kind.NOTE, "note", 1, false)));
        assertFalse(RecycleBin.restoresFarm(item(BinItem.Kind.FARM, "field", 1, true)));
    }

    @Test
    public void storedRecordTypesMapToBinKinds() {
        assertEquals(BinItem.Kind.TASK, BinItem.Kind.parse("TASK"));
        assertEquals(BinItem.Kind.EXPENSE, BinItem.Kind.parse("EXPENSE"));
        assertEquals(BinItem.Kind.FARM, BinItem.Kind.parse("FARM"));
    }
}
