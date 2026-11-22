package com.agroshift.app;

import android.app.Application;
import androidx.annotation.Nullable;
import com.agroshift.core.data.AgroShiftDatabase;
import com.agroshift.core.data.AnalysisRepository;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.FarmDao;
import com.agroshift.core.data.FieldRepository;
import com.agroshift.core.data.OnlineServices;
import com.agroshift.core.data.RecordRepository;
import com.agroshift.core.data.WaterRepository;
import com.agroshift.core.sync.CanalAlarms;
import com.agroshift.core.sync.DangerAlertWorker;
import com.agroshift.core.sync.ClimateRefreshWorker;
import com.agroshift.core.sync.OutboxSyncWorker;
import com.agroshift.core.sync.SyncGateway;
import com.agroshift.core.sync.SyncHost;
import com.agroshift.core.ui.ZameenMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AgroShiftApp extends Application implements SyncHost, AppServices {
    private AgroShiftDatabase database;
    private FieldRepository fieldRepository;
    private AnalysisRepository analysisRepository;
    private RecordRepository recordRepository;
    private OnlineServices onlineServices;
    private WaterRepository waterRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        ZameenMap.configure(this);
        database = AgroShiftDatabase.create(this);
        ExecutorService writes = Executors.newSingleThreadExecutor();
        Runnable sync = () -> OutboxSyncWorker.schedule(this);
        fieldRepository = new FieldRepository(database.farmDao(), writes, sync);
        recordRepository = new RecordRepository(database.farmDao(), writes, sync);
        analysisRepository = new AnalysisRepository(database.farmDao(),
                getSharedPreferences("agroshift_selection", MODE_PRIVATE));
        onlineServices = new OnlineServices(this);
        waterRepository = new WaterRepository(database.farmDao(), writes, sync,
                water -> CanalAlarms.sync(this, water));
        ClimateRefreshWorker.schedule(this);
        DangerAlertWorker.schedule(this);
    }

    @Override
    public FarmDao farmDao() {
        return database.farmDao();
    }

    @Nullable
    @Override
    public SyncGateway syncGateway() {
        return null;
    }

    @Override
    public FieldRepository fieldRepository() {
        return fieldRepository;
    }

    @Override
    public AnalysisRepository analysisRepository() {
        return analysisRepository;
    }

    @Override
    public RecordRepository recordRepository() {
        return recordRepository;
    }

    @Override
    public WaterRepository waterRepository() {
        return waterRepository;
    }

    @Override
    public OnlineServices onlineServices() {
        return onlineServices;
    }
}
