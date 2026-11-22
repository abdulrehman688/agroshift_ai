package com.agroshift.core.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages", indices = {@Index("sessionId")})
public class ChatMessageEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @NonNull public String sessionId = "";
    public int position;
    public boolean fromFarmer;
    @NonNull public String text = "";
    @NonNull public String actionsJson = "[]";
    public String expert;
    public boolean usedFieldData;
    @NonNull public String languageCode = "en";
}
