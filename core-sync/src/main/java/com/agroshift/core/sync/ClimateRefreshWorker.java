package com.agroshift.core.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.data.DataAccessException;
import java.util.concurrent.TimeUnit;

/** Weekly background refresh for saved field climate records. */
public final class ClimateRefreshWorker extends Worker {
    private static final String WORK_NAME = "agroshift-climate-refresh";

    public ClimateRefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ClimateRefreshWorker.class, 7, TimeUnit.DAYS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ((AppServices) getApplicationContext()).analysisRepository().refreshSavedProfiles();
            return Result.success();
        } catch (DataAccessException error) {
            return error.isRetryable() ? Result.retry() : Result.failure();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }
    }
}
