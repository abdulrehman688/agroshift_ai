package com.agroshift.core.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.water.FieldWater;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Restores canal alarms after a restart, app update, time change or permission change. */
public final class CanalBootReceiver extends BroadcastReceiver {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIONS = Set.of(Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTIONS.contains(intent.getAction())) {
            return;
        }
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        WORK.execute(() -> {
            try {
                for (FieldWater water : ((AppServices) app).waterRepository().allWithTurns()) {
                    CanalAlarms.sync(app, water);
                }
            } finally {
                pending.finish();
            }
        });
    }
}
