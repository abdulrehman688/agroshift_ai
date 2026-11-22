package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.chat.ChatSession;
import java.util.List;
import org.junit.Test;

public class ChatSessionTest {
    private static ChatSession session(String id, String title) {
        return new ChatSession(id, null, title, 1, 2, 2);
    }

    @Test
    public void titleIsCleanedAndShortened() {
        assertEquals("When to sow wheat?", ChatSession.titleFrom("  When   to sow\nwheat? "));
        String longTitle = ChatSession.titleFrom("a".repeat(100));
        assertEquals(ChatSession.MAX_TITLE, longTitle.length());
        assertTrue(longTitle.endsWith("…"));
    }

    @Test
    public void recentSkipsOpenChatAndRespectsLimit() {
        List<ChatSession> all = List.of(session("a", "One"), session("b", "Two"), session("c", "Three"),
                session("d", "Four"));
        List<ChatSession> recent = ChatSession.recent(all, "b", 2);
        assertEquals(2, recent.size());
        assertEquals("a", recent.get(0).id());
        assertEquals("c", recent.get(1).id());
    }

    @Test
    public void searchMatchesTitleIgnoringCase() {
        List<ChatSession> all = List.of(session("a", "Wheat water"), session("b", "Cotton pests"));
        assertEquals("a", ChatSession.search(all, " WHEAT ").get(0).id());
        assertEquals(2, ChatSession.search(all, "").size());
        assertTrue(ChatSession.search(all, "rice").isEmpty());
    }
}
