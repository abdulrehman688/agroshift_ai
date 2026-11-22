package com.agroshift.core.data;

import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.agroshift.core.domain.concurrent.ChunkPipeline;
import com.agroshift.core.domain.concurrent.OrderedLocks;
import com.agroshift.core.domain.concurrent.StoppableWorker;
import com.agroshift.core.domain.data.DataAccessException;
import com.agroshift.core.domain.engine.ClimateEngine;
import com.agroshift.core.domain.engine.ClimateProfile;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.DailyClimate;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.farm.FarmField;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONException;

/** Loads 20 years of NASA data once per grid cell, then computes advice on the phone. */
public final class AnalysisRepository {
    private static final String KEY_SELECTED_FIELD = "selected_field";
    private static final int YEARS = 20;
    private static final int YEARS_PER_BLOCK = 4;
    private static final int DOWNLOAD_WORKERS = 2;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 1_500;
    private static final long PROGRESS_INTERVAL_MILLIS = 200;
    private static final long LOCK_TIMEOUT_SECONDS = 180;

    private final FarmDao dao;
    private final NasaPowerClient client = new NasaPowerClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final OrderedLocks<String> locks = new OrderedLocks<>();
    private final Map<String, MutableLiveData<AnalysisState>> states = new ConcurrentHashMap<>();
    private final Map<String, String> signatures = new ConcurrentHashMap<>();
    private final MutableLiveData<String> selectedFieldId = new MutableLiveData<>();
    private final SharedPreferences selection;

    public AnalysisRepository(FarmDao dao, SharedPreferences selection) {
        this.dao = dao;
        this.selection = selection;
        selectedFieldId.setValue(selection.getString(KEY_SELECTED_FIELD, null));
    }

    public LiveData<String> selectedFieldId() {
        return selectedFieldId;
    }

    public void selectField(String id) {
        selection.edit().putString(KEY_SELECTED_FIELD, id).apply();
        selectedFieldId.setValue(id);
    }

    public LiveData<AnalysisState> observe(String fieldId) {
        return live(fieldId);
    }

    private MutableLiveData<AnalysisState> live(String fieldId) {
        return states.computeIfAbsent(fieldId, id -> new MutableLiveData<>());
    }

    /** Safe to call often. Unchanged, loading or ready fields are not reloaded. */
    public void load(FarmField field, boolean retry) {
        MutableLiveData<AnalysisState> state = live(field.id());
        if (!field.hasLocation()) {
            state.postValue(AnalysisState.needsLocation());
            return;
        }
        String signature = cellKey(field) + "|" + field.rotationKey() + "|" + field.water() + "|"
                + field.goal().name() + "|" + field.soil();
        String previous = signatures.put(field.id(), signature);
        AnalysisState current = state.getValue();
        boolean failed = current != null && current.status() == AnalysisState.Status.FAILED;
        if (signature.equals(previous) && !(failed && retry)) {
            return;
        }
        if (current == null || current.status() != AnalysisState.Status.READY) {
            state.postValue(AnalysisState.loading(0, blockCount()));
        }
        executor.execute(() -> analyze(field, signature, state));
    }

    private void analyze(FarmField field, String signature, MutableLiveData<AnalysisState> state) {
        String cell = cellKey(field);
        AnalysisState[] outcome = new AnalysisState[1];
        try {
            boolean acquired = locks.runLocked(List.of("cell:" + cell, "field:" + field.id()),
                    LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> outcome[0] = compute(field, state));
            if (!acquired) {
                outcome[0] = AnalysisState.failed(DataAccessException.Kind.SERVER);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        if (signature.equals(signatures.get(field.id()))) {
            state.postValue(outcome[0]);
        }
    }

    private AnalysisState compute(FarmField field, MutableLiveData<AnalysisState> state) {
        int endYear = LocalDate.now().getYear() - 1;
        Map<ClimateScenario, ClimateProfile> cached = loadSaved(field, endYear, false);
        if (cached.size() == ClimateScenario.values().length) {
            return AnalysisState.ready(advise(field, cached), false);
        }
        try {
            Map<ClimateScenario, ClimateProfile> fresh = download(field, endYear, state);
            saveProfiles(field, fresh);
            return AnalysisState.ready(advise(field, fresh), false);
        } catch (DataAccessException error) {
            return fallback(field, endYear, error.kind());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback(field, endYear, DataAccessException.Kind.NETWORK);
        }
    }

    private AnalysisState fallback(FarmField field, int endYear, DataAccessException.Kind kind) {
        Map<ClimateScenario, ClimateProfile> saved = loadSaved(field, endYear, true);
        return saved.containsKey(ClimateScenario.NORMAL)
                ? AnalysisState.ready(advise(field, saved), true) : AnalysisState.failed(kind);
    }

    private Map<ClimateScenario, ClimateProfile> loadSaved(FarmField field, int endYear, boolean allowOld) {
        Map<ClimateScenario, ClimateProfile> profiles = new EnumMap<>(ClimateScenario.class);
        for (ClimateScenario scenario : ClimateScenario.values()) {
            ClimateProfileEntity entity = dao.findProfile(profileKey(field, scenario));
            ClimateProfile profile = decode(entity);
            if (profile != null && (allowOld || entity.endYear >= endYear)) {
                profiles.put(scenario, profile);
            }
        }
        return profiles;
    }

    /** Refreshes saved locations whose climate record is missing a completed year. */
    public void refreshSavedProfiles() throws DataAccessException, InterruptedException {
        int endYear = LocalDate.now().getYear() - 1;
        for (FieldEntity entity : dao.activeFields()) {
            FarmField field = FieldRepository.toDomain(entity);
            if (field.hasLocation()
                    && loadSaved(field, endYear, false).size() < ClimateScenario.values().length) {
                saveProfiles(field, download(field, endYear, null));
            }
        }
    }

    private Map<ClimateScenario, ClimateProfile> download(FarmField field, int endYear,
            MutableLiveData<AnalysisState> state) throws DataAccessException, InterruptedException {
        double latitude = cellCoordinate(field.latitude());
        double longitude = cellCoordinate(field.longitude());
        List<NasaPowerClient.YearRange> blocks = new ArrayList<>();
        for (int start = endYear - YEARS + 1; start <= endYear; start += YEARS_PER_BLOCK) {
            blocks.add(new NasaPowerClient.YearRange(start, Math.min(start + YEARS_PER_BLOCK - 1, endYear)));
        }
        AtomicInteger completed = new AtomicInteger();
        StoppableWorker progress = new StoppableWorker(() -> {
            if (state != null) {
                state.postValue(AnalysisState.loading(completed.get(), blocks.size()));
            }
        }, PROGRESS_INTERVAL_MILLIS);
        progress.start();
        try {
            List<List<DailyClimate>> results = new ChunkPipeline<NasaPowerClient.YearRange, List<DailyClimate>>(
                    DOWNLOAD_WORKERS, MAX_ATTEMPTS, RETRY_DELAY_MILLIS)
                    .run(blocks, block -> client.fetch(latitude, longitude, block), completed::set);
            List<DailyClimate> days = new ArrayList<>(YEARS * 366);
            for (List<DailyClimate> block : results) {
                days.addAll(block);
            }
            Map<ClimateScenario, ClimateProfile> profiles = new EnumMap<>(ClimateScenario.class);
            for (ClimateScenario scenario : ClimateScenario.values()) {
                profiles.put(scenario, ClimateEngine.buildScenario(days, latitude, scenario));
            }
            return profiles;
        } finally {
            progress.stop();
        }
    }

    private void saveProfiles(FarmField field, Map<ClimateScenario, ClimateProfile> profiles) {
        for (Map.Entry<ClimateScenario, ClimateProfile> item : profiles.entrySet()) {
            ClimateProfileEntity entity = new ClimateProfileEntity();
            entity.cellKey = profileKey(field, item.getKey());
            entity.latitude = cellCoordinate(field.latitude());
            entity.longitude = cellCoordinate(field.longitude());
            entity.startYear = item.getValue().startYear();
            entity.endYear = item.getValue().endYear();
            entity.fetchedAtMillis = System.currentTimeMillis();
            entity.profileJson = ClimateProfileCodec.encode(item.getValue());
            dao.putProfile(entity);
        }
    }

    private static ClimateProfile decode(ClimateProfileEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return ClimateProfileCodec.decode(entity.profileJson);
        } catch (JSONException | IllegalArgumentException corrupted) {
            return null;
        }
    }

    private static Map<ClimateScenario, RotationAdvisor.Advice> advise(FarmField field,
            Map<ClimateScenario, ClimateProfile> profiles) {
        Map<ClimateScenario, RotationAdvisor.Advice> outlooks = new EnumMap<>(ClimateScenario.class);
        for (Map.Entry<ClimateScenario, ClimateProfile> item : profiles.entrySet()) {
            outlooks.put(item.getKey(), RotationAdvisor.advise(item.getValue(), field.rotationKey(),
                    field.water(), field.goal(), field.soil()));
        }
        return outlooks;
    }

    private static int blockCount() {
        return (YEARS + YEARS_PER_BLOCK - 1) / YEARS_PER_BLOCK;
    }

    /** NASA POWER is coarse, so nearby fields share a half-degree cell. */
    private static double cellCoordinate(double value) {
        return Math.round(value * 2) / 2.0;
    }

    private static String cellKey(FarmField field) {
        return String.format(Locale.ROOT, "%.1f,%.1f", cellCoordinate(field.latitude()),
                cellCoordinate(field.longitude()));
    }

    private static String profileKey(FarmField field, ClimateScenario scenario) {
        return scenario == ClimateScenario.NORMAL ? cellKey(field) : cellKey(field) + "|" + scenario.name();
    }
}
