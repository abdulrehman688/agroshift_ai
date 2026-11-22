package com.agroshift.core.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.agroshift.core.data.FarmDao;
import com.agroshift.core.data.OutboxEntity;
import com.agroshift.core.domain.data.SyncOutbox;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Sends queued changes in order and stops at the first failure to preserve ordering. */
public final class OutboxSyncWorker extends Worker {
    private static final String WORK_NAME = "agroshift-outbox-sync";
    private static final int BATCH_SIZE = 50;

    public OutboxSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OutboxSyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        SyncHost host = (SyncHost) getApplicationContext();
        SyncGateway gateway = host.syncGateway();
        if (gateway == null) {
            return Result.success();
        }
        FarmDao dao = host.farmDao();
        SyncOutbox<OutboxEntity> outbox = new SyncOutbox<>(new SyncOutbox.Store<>() {
            @Override
            public List<OutboxEntity> next(int limit) {
                return dao.nextOutbox(limit);
            }

            @Override
            public void remove(OutboxEntity entry) {
                dao.removeOutbox(entry.sequence);
            }

            @Override
            public void recordAttempt(OutboxEntity entry) {
                dao.recordAttempt(entry.sequence);
            }
        }, BATCH_SIZE, gateway::send);
        return outbox.drain(this::isStopped) == SyncOutbox.Outcome.COMPLETE ? Result.success() : Result.retry();
    }
}
