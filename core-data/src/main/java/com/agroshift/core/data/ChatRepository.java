package com.agroshift.core.data;

import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.agroshift.core.domain.chat.ChatSession;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Saves assistant conversations on the phone so they survive closing the app. */
public final class ChatRepository {
    private static final String GENERAL_KEY = "general";

    private final ChatDao dao;
    private final Executor executor;

    public ChatRepository(ChatDao dao, Executor executor) {
        this.dao = dao;
        this.executor = executor;
    }

    public LiveData<List<ChatSession>> observeSessions() {
        return Transformations.map(dao.observeSessions(), entities -> {
            List<ChatSession> sessions = new ArrayList<>(entities.size());
            for (ChatSessionEntity entity : entities) {
                sessions.add(new ChatSession(entity.id, entity.fieldId, entity.title, entity.createdAtMillis,
                        entity.updatedAtMillis, entity.messageCount));
            }
            return sessions;
        });
    }

    public void load(String sessionId, Consumer<List<OnlineServices.ChatMessage>> onLoaded) {
        executor.execute(() -> onLoaded.accept(readMessages(sessionId)));
    }

    /** Blocking read used by background work and tests. */
    public List<OnlineServices.ChatMessage> readMessages(String sessionId) {
        List<OnlineServices.ChatMessage> messages = new ArrayList<>();
        for (ChatMessageEntity entity : dao.messages(sessionId)) {
            List<String> actions = new ArrayList<>();
            for (JsonElement action : JsonParser.parseString(entity.actionsJson).getAsJsonArray()) {
                actions.add(action.getAsString());
            }
            messages.add(new OnlineServices.ChatMessage(entity.fromFarmer, entity.text, actions, entity.expert,
                    entity.usedFieldData, entity.languageCode));
        }
        return List.copyOf(messages);
    }

    public void save(String sessionId, @Nullable String fieldId, List<OnlineServices.ChatMessage> messages) {
        List<OnlineServices.ChatMessage> copy = List.copyOf(messages);
        executor.execute(() -> write(sessionId, fieldId, copy, System.currentTimeMillis()));
    }

    private void write(String sessionId, @Nullable String fieldId, List<OnlineServices.ChatMessage> messages,
            long now) {
        if (messages.isEmpty()) {
            return;
        }
        ChatSessionEntity existing = dao.findSession(sessionId);
        ChatSessionEntity session = new ChatSessionEntity();
        session.id = sessionId;
        session.fieldId = fieldId != null || existing == null ? fieldId : existing.fieldId;
        session.createdAtMillis = existing == null ? now : existing.createdAtMillis;
        session.updatedAtMillis = now;
        session.messageCount = messages.size();
        session.title = ChatSession.titleFrom(firstQuestion(messages));
        List<ChatMessageEntity> rows = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            OnlineServices.ChatMessage message = messages.get(i);
            ChatMessageEntity row = new ChatMessageEntity();
            row.sessionId = sessionId;
            row.position = i;
            row.fromFarmer = message.fromFarmer();
            row.text = message.text();
            JsonArray actions = new JsonArray();
            message.actions().forEach(actions::add);
            row.actionsJson = actions.toString();
            row.expert = message.expert();
            row.usedFieldData = message.usedFieldData();
            row.languageCode = message.languageCode();
            rows.add(row);
        }
        dao.replaceSession(session, rows);
    }

    private static String firstQuestion(List<OnlineServices.ChatMessage> messages) {
        for (OnlineServices.ChatMessage message : messages) {
            if (message.fromFarmer()) {
                return message.text();
            }
        }
        return messages.get(0).text();
    }

    public void delete(String sessionId) {
        executor.execute(() -> dao.deleteSession(sessionId));
    }

    /** Moves chats saved by earlier app versions into sessions, once. */
    public void importLegacy(SharedPreferences prefs, String reservedKey) {
        executor.execute(() -> {
            SharedPreferences.Editor editor = prefs.edit();
            boolean changed = false;
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (entry.getKey().equals(reservedKey) || !(entry.getValue() instanceof String saved)) {
                    continue;
                }
                List<OnlineServices.ChatMessage> messages = parseLegacy(saved);
                if (!messages.isEmpty()) {
                    write(UUID.randomUUID().toString(), GENERAL_KEY.equals(entry.getKey()) ? null : entry.getKey(),
                            messages, System.currentTimeMillis());
                }
                editor.remove(entry.getKey());
                changed = true;
            }
            if (changed) {
                editor.apply();
            }
        });
    }

    static List<OnlineServices.ChatMessage> parseLegacy(String saved) {
        try {
            List<OnlineServices.ChatMessage> messages = new ArrayList<>();
            for (JsonElement item : JsonParser.parseString(saved).getAsJsonArray()) {
                JsonObject row = item.getAsJsonObject();
                List<String> actions = new ArrayList<>();
                if (row.has("actions")) {
                    row.getAsJsonArray("actions").forEach(value -> actions.add(value.getAsString()));
                }
                messages.add(new OnlineServices.ChatMessage(row.get("farmer").getAsBoolean(),
                        row.get("text").getAsString(), actions,
                        row.has("expert") ? row.get("expert").getAsString() : null,
                        row.has("data") && row.get("data").getAsBoolean(),
                        row.has("lang") ? row.get("lang").getAsString() : "en"));
            }
            return List.copyOf(messages);
        } catch (RuntimeException corrupted) {
            return List.of();
        }
    }
}
