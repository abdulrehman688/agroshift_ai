package com.agroshift.core.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.agroshift.core.domain.Result;
import com.agroshift.core.domain.engine.FarmGoal;
import com.agroshift.core.domain.engine.SoilType;
import com.agroshift.core.domain.engine.WaterAccess;
import com.agroshift.core.domain.farm.FarmField;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.json.JSONException;
import org.json.JSONObject;

/** Offline-first field store. Each change commits with its sync outbox entry. */
public final class FieldRepository {
    private static final String ENTITY_FIELD = "field";

    private final FarmDao dao;
    private final Executor executor;
    private final Runnable onLocalChange;

    public FieldRepository(FarmDao dao, Executor executor, Runnable onLocalChange) {
        this.dao = dao;
        this.executor = executor;
        this.onLocalChange = onLocalChange;
    }

    public LiveData<List<FarmField>> observeFields() {
        return Transformations.map(dao.observeActiveFields(), entities -> {
            List<FarmField> fields = new ArrayList<>(entities.size());
            for (FieldEntity entity : entities) {
                fields.add(toDomain(entity));
            }
            return fields;
        });
    }

    static FarmField toDomain(FieldEntity entity) {
        return new FarmField(entity.id, entity.name, entity.areaAcres, entity.crop,
                entity.updatedAtMillis, entity.latitude, entity.longitude, entity.rotationKey,
                WaterAccess.parse(entity.waterAccess), FarmGoal.parse(entity.goal),
                SoilType.parse(entity.soil));
    }

    public Result<FarmField> add(String name, double areaAcres, double latitude, double longitude,
            String rotationKey, WaterAccess water, FarmGoal goal, SoilType soil) {
        long now = System.currentTimeMillis();
        Result<FarmField> result = FarmField.create(UUID.randomUUID().toString(), name, areaAcres,
                latitude, longitude, rotationKey, water, goal, soil, now);
        if (result.isSuccess()) {
            FieldEntity entity = toEntity(result.value());
            OutboxEntity entry = outbox(entity.id, OutboxEntity.UPSERT, payload(entity), now);
            run(() -> dao.saveField(entity, entry));
        }
        return result;
    }

    public void updateGoal(String id, FarmGoal goal) {
        long now = System.currentTimeMillis();
        run(() -> {
            FieldEntity entity = dao.findField(id);
            if (entity != null && entity.deletedAtMillis == null) {
                entity.goal = goal.name();
                entity.updatedAtMillis = now;
                dao.saveField(entity, outbox(id, OutboxEntity.UPSERT, payload(entity), now));
            }
        });
    }

    public void delete(String id) {
        long now = System.currentTimeMillis();
        run(() -> dao.deleteField(id, now, outbox(id, OutboxEntity.DELETE, "{}", now)));
    }

    public void restore(String id) {
        long now = System.currentTimeMillis();
        run(() -> {
            FieldEntity entity = dao.findField(id);
            if (entity != null) {
                entity.updatedAtMillis = now;
                entity.deletedAtMillis = null;
                dao.restoreField(id, now, outbox(id, OutboxEntity.UPSERT, payload(entity), now));
            }
        });
    }

    private void run(Runnable write) {
        executor.execute(() -> {
            write.run();
            onLocalChange.run();
        });
    }

    private static FieldEntity toEntity(FarmField field) {
        FieldEntity entity = new FieldEntity();
        entity.id = field.id();
        entity.name = field.name();
        entity.areaAcres = field.areaAcres();
        entity.crop = field.crop();
        entity.updatedAtMillis = field.updatedAtMillis();
        entity.latitude = field.latitude();
        entity.longitude = field.longitude();
        entity.rotationKey = field.rotationKey();
        entity.waterAccess = field.water().name();
        entity.goal = field.goal().name();
        entity.soil = field.soil().name();
        return entity;
    }

    private static OutboxEntity outbox(String id, String operation, String payload, long now) {
        OutboxEntity entry = new OutboxEntity();
        entry.idempotencyKey = UUID.randomUUID().toString();
        entry.entityType = ENTITY_FIELD;
        entry.entityId = id;
        entry.operation = operation;
        entry.payloadJson = payload;
        entry.createdAtMillis = now;
        return entry;
    }

    private static String payload(FieldEntity entity) {
        try {
            return new JSONObject()
                    .put("id", entity.id)
                    .put("name", entity.name)
                    .put("areaAcres", entity.areaAcres)
                    .put("crop", entity.crop)
                    .put("latitude", entity.latitude == null ? JSONObject.NULL : entity.latitude)
                    .put("longitude", entity.longitude == null ? JSONObject.NULL : entity.longitude)
                    .put("rotationKey", entity.rotationKey == null ? JSONObject.NULL : entity.rotationKey)
                    .put("waterAccess", entity.waterAccess == null ? JSONObject.NULL : entity.waterAccess)
                    .put("goal", entity.goal == null ? JSONObject.NULL : entity.goal)
                    .put("soil", entity.soil == null ? JSONObject.NULL : entity.soil)
                    .put("updatedAtMillis", entity.updatedAtMillis)
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Could not encode field", e);
        }
    }

    /** Blocking read for background receivers and workers. */
    public FieldEntity findBlocking(String id) {
        return dao.findField(id);
    }

    /** Blocking read for background receivers and workers. */
    public java.util.List<FieldEntity> activeBlocking() {
        return dao.activeFields();
    }
}
