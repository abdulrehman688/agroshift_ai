package com.agroshift.core.data;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.agroshift.core.domain.water.CanalTurn;
import com.agroshift.core.domain.water.FieldWater;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.json.JSONException;
import org.json.JSONObject;

/** Offline watering dates and canal turns. */
public final class WaterRepository {
    private final FarmDao dao;
    private final Executor executor;
    private final Runnable onLocalChange;
    private final Consumer<FieldWater> onTurnChange;

    public WaterRepository(FarmDao dao, Executor executor, Runnable onLocalChange,
            Consumer<FieldWater> onTurnChange) {
        this.dao = dao;
        this.executor = executor;
        this.onLocalChange = onLocalChange;
        this.onTurnChange = onTurnChange;
    }

    public LiveData<FieldWater> observe(String fieldId) {
        return Transformations.map(dao.observeWater(fieldId),
                entity -> entity == null ? new FieldWater(fieldId, null, null) : toDomain(entity));
    }

    /** Blocking read for alarm receivers. */
    public List<FieldWater> allWithTurns() {
        List<FieldWater> result = new ArrayList<>();
        for (FieldWaterEntity entity : dao.waterWithTurns()) {
            FieldWater water = toDomain(entity);
            if (water.turn() != null) {
                result.add(water);
            }
        }
        return result;
    }

    @Nullable
    public FieldWater find(String fieldId) {
        FieldWaterEntity entity = dao.findWater(fieldId);
        return entity == null ? null : toDomain(entity);
    }

    public void setLastWatered(String fieldId, @Nullable LocalDate date) {
        executor.execute(() -> {
            FieldWaterEntity entity = load(fieldId);
            entity.lastWateredEpochDay = date == null ? null : date.toEpochDay();
            save(entity);
        });
    }

    public void setTurn(String fieldId, @Nullable CanalTurn turn) {
        executor.execute(() -> {
            FieldWaterEntity entity = load(fieldId);
            entity.turnDay = turn == null ? null : turn.day().getValue();
            entity.turnStartMinute = turn == null ? null : turn.startMinute();
            entity.turnDurationMinutes = turn == null ? null : turn.durationMinutes();
            entity.turnRemindMinutes = turn == null ? null : turn.remindMinutes();
            save(entity);
            onTurnChange.accept(toDomain(entity));
        });
    }

    private FieldWaterEntity load(String fieldId) {
        FieldWaterEntity entity = dao.findWater(fieldId);
        if (entity == null) {
            entity = new FieldWaterEntity();
            entity.fieldId = fieldId;
        }
        return entity;
    }

    private void save(FieldWaterEntity entity) {
        entity.updatedAtMillis = System.currentTimeMillis();
        dao.saveWater(entity, outbox(entity));
        onLocalChange.run();
    }

    private static FieldWater toDomain(FieldWaterEntity entity) {
        LocalDate last = entity.lastWateredEpochDay == null ? null : LocalDate.ofEpochDay(entity.lastWateredEpochDay);
        CanalTurn turn = null;
        if (entity.turnDay != null && entity.turnStartMinute != null && entity.turnDurationMinutes != null
                && entity.turnRemindMinutes != null) {
            try {
                turn = new CanalTurn(DayOfWeek.of(entity.turnDay), entity.turnStartMinute,
                        entity.turnDurationMinutes, entity.turnRemindMinutes);
            } catch (RuntimeException invalid) {
                turn = null;
            }
        }
        return new FieldWater(entity.fieldId, last, turn);
    }

    private static OutboxEntity outbox(FieldWaterEntity entity) {
        OutboxEntity entry = new OutboxEntity();
        entry.idempotencyKey = UUID.randomUUID().toString();
        entry.entityType = "field_water";
        entry.entityId = entity.fieldId;
        entry.operation = OutboxEntity.UPSERT;
        entry.createdAtMillis = System.currentTimeMillis();
        try {
            entry.payloadJson = new JSONObject().put("fieldId", entity.fieldId)
                    .put("lastWateredEpochDay", entity.lastWateredEpochDay == null
                            ? JSONObject.NULL : entity.lastWateredEpochDay)
                    .put("turnDay", entity.turnDay == null ? JSONObject.NULL : entity.turnDay)
                    .put("turnStartMinute", entity.turnStartMinute == null ? JSONObject.NULL : entity.turnStartMinute)
                    .put("turnDurationMinutes", entity.turnDurationMinutes == null
                            ? JSONObject.NULL : entity.turnDurationMinutes)
                    .put("turnRemindMinutes", entity.turnRemindMinutes == null
                            ? JSONObject.NULL : entity.turnRemindMinutes)
                    .put("updatedAtMillis", entity.updatedAtMillis).toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Could not encode water state", e);
        }
        return entry;
    }
}
