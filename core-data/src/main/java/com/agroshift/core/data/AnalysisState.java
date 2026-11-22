package com.agroshift.core.data;

import com.agroshift.core.domain.data.DataAccessException;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.RotationAdvisor;
import java.util.Map;

/** Immutable UI state for one field's rotation analysis. */
public record AnalysisState(Status status, int completedBlocks, int totalBlocks,
                            Map<ClimateScenario, RotationAdvisor.Advice> outlooks,
                            DataAccessException.Kind error, boolean stale) {
    public enum Status { LOADING, READY, FAILED, NEEDS_LOCATION }

    public AnalysisState {
        outlooks = outlooks == null ? Map.of() : Map.copyOf(outlooks);
    }

    public RotationAdvisor.Advice advice() {
        return outlooks.get(ClimateScenario.NORMAL);
    }

    static AnalysisState loading(int completed, int total) {
        return new AnalysisState(Status.LOADING, completed, total, null, null, false);
    }

    static AnalysisState ready(Map<ClimateScenario, RotationAdvisor.Advice> outlooks, boolean stale) {
        return new AnalysisState(Status.READY, 0, 0, outlooks, null, stale);
    }

    static AnalysisState failed(DataAccessException.Kind error) {
        return new AnalysisState(Status.FAILED, 0, 0, null, error, false);
    }

    static AnalysisState needsLocation() {
        return new AnalysisState(Status.NEEDS_LOCATION, 0, 0, null, null, false);
    }
}
