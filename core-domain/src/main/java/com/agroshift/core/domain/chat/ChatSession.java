package com.agroshift.core.domain.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A saved assistant conversation the farmer can reopen and continue. */
public record ChatSession(String id, String fieldId, String title, long createdAtMillis, long updatedAtMillis,
                          int messageCount) {
    public static final int MAX_TITLE = 48;

    /** Builds a short title from the farmer's first question. */
    public static String titleFrom(String question) {
        String clean = question == null ? "" : question.strip().replaceAll("\\s+", " ");
        if (clean.length() <= MAX_TITLE) {
            return clean;
        }
        return clean.substring(0, MAX_TITLE - 1).stripTrailing() + "…";
    }

    public static List<ChatSession> recent(List<ChatSession> sessions, String currentId, int limit) {
        List<ChatSession> result = new ArrayList<>();
        for (ChatSession session : sessions) {
            if (!session.id().equals(currentId) && result.size() < limit) {
                result.add(session);
            }
        }
        return List.copyOf(result);
    }

    public static List<ChatSession> search(List<ChatSession> sessions, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<ChatSession> result = new ArrayList<>();
        for (ChatSession session : sessions) {
            if (needle.isEmpty() || session.title().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(session);
            }
        }
        return List.copyOf(result);
    }
}
