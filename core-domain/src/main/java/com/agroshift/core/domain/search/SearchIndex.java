package com.agroshift.core.domain.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Ranks app actions, farms, records and crops for the global search. */
public final class SearchIndex {
    public enum Kind { ACTION, FARM, RECORD, CROP }

    public record Entry(Kind kind, String id, String title, String subtitle) {
        public Entry {
            title = title == null ? "" : title;
            subtitle = subtitle == null ? "" : subtitle;
        }
    }

    private record Ranked(Entry entry, int rank, int order) {
    }

    private static final int NO_MATCH = Integer.MAX_VALUE;

    private SearchIndex() {
    }

    /** Best matches first. A null kind searches everything. */
    public static List<Entry> find(List<Entry> entries, String query, Kind kind, int limit) {
        String needle = normalize(query);
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (kind != null && entry.kind() != kind) {
                continue;
            }
            int rank = needle.isEmpty() ? 0 : rank(entry, needle);
            if (rank != NO_MATCH) {
                ranked.add(new Ranked(entry, rank, i));
            }
        }
        ranked.sort(Comparator.comparingInt(Ranked::rank).thenComparingInt(Ranked::order));
        List<Entry> result = new ArrayList<>();
        for (Ranked item : ranked.subList(0, Math.min(limit, ranked.size()))) {
            result.add(item.entry());
        }
        return List.copyOf(result);
    }

    private static int rank(Entry entry, String needle) {
        String title = normalize(entry.title());
        if (title.equals(needle)) {
            return 0;
        }
        if (title.startsWith(needle)) {
            return 1;
        }
        if (title.contains(" " + needle)) {
            return 2;
        }
        if (title.contains(needle)) {
            return 3;
        }
        return normalize(entry.subtitle()).contains(needle) ? 4 : NO_MATCH;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
