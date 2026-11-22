package com.agroshift.core.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_sessions")
public class ChatSessionEntity {
    @PrimaryKey @NonNull public String id = "";
    public String fieldId;
    @NonNull public String title = "";
    public long createdAtMillis;
    public long updatedAtMillis;
    public int messageCount;
}
