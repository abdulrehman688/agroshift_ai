package com.agroshift.core.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A local change waiting to reach the server. The key makes retries safe. */
@Entity(tableName = "sync_outbox", indices = {@Index(value = "idempotencyKey", unique = true)})
public class OutboxEntity {
    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";

    @PrimaryKey(autoGenerate = true) public long sequence;
    @NonNull public String idempotencyKey = "";
    @NonNull public String entityType = "";
    @NonNull public String entityId = "";
    @NonNull public String operation = "";
    @NonNull public String payloadJson = "{}";
    public long createdAtMillis;
    public int attempts;
}
