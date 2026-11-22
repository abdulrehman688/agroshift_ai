package com.agroshift.core.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.FieldEntity;
import com.agroshift.core.data.WaterRepository;
import com.agroshift.core.domain.water.FieldWater;
import com.agroshift.core.i18n.R.string;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shows the canal alarm, then schedules the next one. */
public final class CanalAlarmReceiver extends BroadcastReceiver {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final long START_TOLERANCE_MINUTES = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        WORK.execute(() -> {
            try {
                AppServices services = (AppServices) app;
                WaterRepository water = services.waterRepository();
                if (CanalAlarms.isTurnAction(intent)) {
                    fire(app, services, water, intent.getStringExtra(CanalAlarms.EXTRA_FIELD_ID));
                }
            } finally {
                pending.finish();
            }
        });
    }

    private static void fire(Context app, AppServices services, WaterRepository repository, String fieldId) {
        if (fieldId == null) {
            return;
        }
        FieldWater water = repository.find(fieldId);
        if (water == null || water.turn() == null) {
            return;
        }
        FieldEntity field = services.fieldRepository().findBlocking(fieldId);
        if (field == null || field.deletedAtMillis != null) {
            CanalAlarms.sync(app, new FieldWater(fieldId, water.lastWatered(), null));
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = water.turn().nextStart(now);
        boolean started = water.turn().isActive(now)
                && Duration.between(next.minusWeeks(1), now).toMinutes() <= START_TOLERANCE_MINUTES;
        String body = started ? app.getString(string.turn_alarm_now)
                : app.getString(string.turn_alarm_soon,
                        next.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));
        FarmNotifications.postTurn(app, fieldId.hashCode(), app.getString(string.turn_alarm_title, field.name), body);
        CanalAlarms.sync(app, water);
    }
}
