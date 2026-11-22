package com.agroshift.core.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class FarmDao {
    @Query("SELECT * FROM fields WHERE deletedAtMillis IS NULL ORDER BY updatedAtMillis DESC")
    public abstract LiveData<List<FieldEntity>> observeActiveFields();

    @Query("SELECT * FROM fields WHERE id = :id")
    public abstract FieldEntity findField(String id);

    @Query("SELECT * FROM fields WHERE deletedAtMillis IS NULL")
    public abstract List<FieldEntity> activeFields();

    @Query("SELECT * FROM climate_profiles WHERE cellKey = :cellKey")
    public abstract ClimateProfileEntity findProfile(String cellKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void putProfile(ClimateProfileEntity profile);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void putField(FieldEntity field);

    @Query("SELECT r.* FROM farm_records r JOIN fields f ON f.id = r.fieldId "
            + "WHERE r.deletedAtMillis IS NULL AND f.deletedAtMillis IS NULL ORDER BY r.dateMillis DESC")
    public abstract LiveData<List<FarmRecordEntity>> observeAllRecords();

    @Query("SELECT * FROM farm_records WHERE fieldId = :fieldId AND deletedAtMillis IS NULL "
            + "ORDER BY done ASC, dateMillis DESC")
    public abstract LiveData<List<FarmRecordEntity>> observeRecords(String fieldId);

    @Query("SELECT * FROM farm_records WHERE id = :id")
    public abstract FarmRecordEntity findRecord(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void putRecord(FarmRecordEntity record);

    @Transaction
    public void saveRecord(FarmRecordEntity record, OutboxEntity entry) {
        putRecord(record);
        addOutbox(entry);
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract void addOutbox(OutboxEntity entry);

    @Query("UPDATE fields SET deletedAtMillis = :deletedAt, updatedAtMillis = :deletedAt WHERE id = :id")
    abstract int markDeleted(String id, long deletedAt);

    @Query("UPDATE fields SET deletedAtMillis = NULL, updatedAtMillis = :restoredAt WHERE id = :id")
    abstract int markRestored(String id, long restoredAt);

    @Query("DELETE FROM sync_outbox WHERE entityId = :id AND operation = 'DELETE'")
    abstract void removePendingDeletes(String id);

    @Query("SELECT * FROM sync_outbox ORDER BY sequence ASC LIMIT :limit")
    public abstract List<OutboxEntity> nextOutbox(int limit);

    @Query("DELETE FROM sync_outbox WHERE sequence = :sequence")
    public abstract void removeOutbox(long sequence);

    @Query("UPDATE sync_outbox SET attempts = attempts + 1 WHERE sequence = :sequence")
    public abstract void recordAttempt(long sequence);

    @Transaction
    public void saveField(FieldEntity field, OutboxEntity entry) {
        putField(field);
        addOutbox(entry);
    }

    @Transaction
    public void deleteField(String id, long deletedAt, OutboxEntity entry) {
        if (markDeleted(id, deletedAt) > 0) {
            addOutbox(entry);
        }
    }

    @Transaction
    public void restoreField(String id, long restoredAt, OutboxEntity entry) {
        if (markRestored(id, restoredAt) > 0) {
            removePendingDeletes(id);
            addOutbox(entry);
        }
    }
}
