package com.agroshift.core.sync;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.FieldEntity;
import com.agroshift.core.data.OnlineServices;
import com.agroshift.core.domain.water.DangerAlert;
import com.agroshift.core.i18n.WaterText;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Checks each located field twice a day and notifies once per high danger. */
public final class DangerAlertWorker extends Worker {
    private static final String WORK_NAME = "agroshift-danger-alerts";
    private static final String PREFS = "agroshift_danger";
    private static final String KEY_SENT = "sent";

    public DangerAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(DangerAlertWorker.class, 12, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        AppServices services = (AppServices) context;
        OnlineServices online = services.onlineServices();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LocalDate today = LocalDate.now();
        Set<String> sent = new HashSet<>();
        for (String key : prefs.getStringSet(KEY_SENT, Set.of())) {
            int split = key.lastIndexOf('|');
            if (split > 0 && !LocalDate.parse(key.substring(split + 1)).isBefore(today)) {
                sent.add(key);
            }
        }
        boolean failed = false;
        for (FieldEntity field : services.fieldRepository().activeBlocking()) {
            if (field.latitude == null || field.longitude == null) {
                continue;
            }
            OnlineServices.Weather weather = online.fetchWeather(field.latitude, field.longitude);
            if (weather == null) {
                failed = true;
                continue;
            }
            List<DangerAlert> alerts = DangerAlert.find(weather.days(), today);
            for (DangerAlert alert : alerts) {
                String key = field.id + "|" + alert.type() + "|" + alert.date();
                if (alert.severity() == DangerAlert.Severity.HIGH && sent.add(key)) {
                    FarmNotifications.postDanger(context, key.hashCode(),
                            field.name + ": " + WaterText.dangerTitle(context, alert),
                            WaterText.dangerDetail(context, alert));
                }
            }
        }
        prefs.edit().putStringSet(KEY_SENT, sent).apply();
        return failed ? Result.retry() : Result.success();
    }
}
