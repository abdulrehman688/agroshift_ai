package com.agroshift.core.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class ChatDao {
    @Query("SELECT * FROM chat_sessions WHERE messageCount > 0 ORDER BY updatedAtMillis DESC")
    public abstract LiveData<List<ChatSessionEntity>> observeSessions();

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    public abstract ChatSessionEntity findSession(String id);

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY position ASC")
    public abstract List<ChatMessageEntity> messages(String sessionId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void putSession(ChatSessionEntity session);

    @Insert
    abstract void insertMessages(List<ChatMessageEntity> messages);

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    abstract void deleteMessages(String sessionId);

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    abstract void removeSession(String id);

    @Transaction
    public void replaceSession(ChatSessionEntity session, List<ChatMessageEntity> messages) {
        putSession(session);
        deleteMessages(session.id);
        insertMessages(messages);
    }

    @Transaction
    public void deleteSession(String id) {
        deleteMessages(id);
        removeSession(id);
    }
}
