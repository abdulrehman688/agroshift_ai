package com.agroshift.core.sync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.agroshift.core.domain.water.FieldWater;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Schedules the next alarm for a field's weekly canal turn. */
public final class CanalAlarms {
    static final String EXTRA_FIELD_ID = "fieldId";
    private static final String ACTION = "com.agroshift.action.CANAL_TURN";

    private CanalAlarms() {
    }

    public static boolean canUseExactTime(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    public static void sync(Context context, FieldWater water) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent pending = pending(context, water.fieldId());
        alarms.cancel(pending);
        if (water.turn() == null) {
            return;
        }
        LocalDateTime next = water.turn().nextAlarm(LocalDateTime.now());
        long at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (canUseExactTime(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        }
    }

    private static PendingIntent pending(Context context, String fieldId) {
        Intent intent = new Intent(context, CanalAlarmReceiver.class).setAction(ACTION)
                .putExtra(EXTRA_FIELD_ID, fieldId);
        return PendingIntent.getBroadcast(context, fieldId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static boolean isTurnAction(Intent intent) {
        return ACTION.equals(intent.getAction());
    }
}
