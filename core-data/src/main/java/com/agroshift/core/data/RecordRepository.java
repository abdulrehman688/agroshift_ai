package com.agroshift.core.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.agroshift.core.domain.Result;
import com.agroshift.core.domain.farm.BinItem;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.farm.RecycleBin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.json.JSONException;
import org.json.JSONObject;

/** Offline-first tasks, costs, sales and notes. */
public final class RecordRepository {
    private final FarmDao dao;
    private final Executor executor;
    private final Runnable onLocalChange;

    public RecordRepository(FarmDao dao, Executor executor, Runnable onLocalChange) {
        this.dao = dao;
        this.executor = executor;
        this.onLocalChange = onLocalChange;
    }

    public LiveData<List<FarmRecord>> observe(String fieldId) {
        return Transformations.map(dao.observeRecords(fieldId), RecordRepository::toDomain);
    }

    public LiveData<List<FarmRecord>> observeAll() {
        return Transformations.map(dao.observeAllRecords(), RecordRepository::toDomain);
    }

    /** Deleted farms and records, newest first. */
    public LiveData<List<BinItem>> observeBin() {
        return Transformations.map(dao.observeBin(), rows -> {
            List<BinItem> items = new ArrayList<>(rows.size());
            for (BinRow row : rows) {
                items.add(new BinItem(BinItem.Kind.parse(row.kind), row.id, row.fieldId, row.title, row.farmName,
                        row.amountRupees, row.deletedAtMillis, row.farmInBin, row.recordCount));
            }
            return RecycleBin.newestFirst(items);
        });
    }

    public Result<FarmRecord> add(String fieldId, FarmRecord.Type type, String title, double amount) {
        Result<FarmRecord> result = FarmRecord.create(UUID.randomUUID().toString(), fieldId, type, title,
                amount, System.currentTimeMillis());
        if (result.isSuccess()) {
            FarmRecord record = result.value();
            FarmRecordEntity entity = new FarmRecordEntity();
            entity.id = record.id();
            entity.fieldId = record.fieldId();
            entity.type = record.type().name();
            entity.title = record.title();
            entity.amountRupees = record.amountRupees();
            entity.dateMillis = record.dateMillis();
            write(entity, OutboxEntity.UPSERT);
        }
        return result;
    }

    public void setDone(String id, boolean done) {
        update(id, entity -> entity.done = done, OutboxEntity.UPSERT);
    }

    public void delete(String id) {
        long now = System.currentTimeMillis();
        update(id, entity -> entity.deletedAtMillis = now, OutboxEntity.DELETE);
    }

    public void restore(String id) {
        update(id, entity -> entity.deletedAtMillis = null, OutboxEntity.UPSERT);
    }

    private interface Change {
        void apply(FarmRecordEntity entity);
    }

    private void update(String id, Change change, String operation) {
        executor.execute(() -> {
            FarmRecordEntity entity = dao.findRecord(id);
            if (entity != null) {
                change.apply(entity);
                dao.saveRecord(entity, outbox(entity, operation));
                onLocalChange.run();
            }
        });
    }

    private void write(FarmRecordEntity entity, String operation) {
        executor.execute(() -> {
            dao.saveRecord(entity, outbox(entity, operation));
            onLocalChange.run();
        });
    }

    private static List<FarmRecord> toDomain(List<FarmRecordEntity> entities) {
        List<FarmRecord> records = new ArrayList<>(entities.size());
        for (FarmRecordEntity entity : entities) {
            records.add(new FarmRecord(entity.id, entity.fieldId, FarmRecord.Type.valueOf(entity.type),
                    entity.title, entity.amountRupees, entity.dateMillis, entity.done));
        }
        return records;
    }

    private static OutboxEntity outbox(FarmRecordEntity entity, String operation) {
        OutboxEntity entry = new OutboxEntity();
        entry.idempotencyKey = UUID.randomUUID().toString();
        entry.entityType = "farm_record";
        entry.entityId = entity.id;
        entry.operation = operation;
        entry.createdAtMillis = System.currentTimeMillis();
        try {
            entry.payloadJson = new JSONObject()
                    .put("id", entity.id).put("fieldId", entity.fieldId).put("type", entity.type)
                    .put("title", entity.title).put("amountRupees", entity.amountRupees)
                    .put("dateMillis", entity.dateMillis).put("done", entity.done).toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Could not encode record", e);
        }
        return entry;
    }
}
