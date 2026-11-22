package com.agroshift.core.domain.farm;

import java.util.List;

/** Chooses which saved farm a screen should show. */
public final class FarmSelection {
    private FarmSelection() {
    }

    /** Uses the requested farm when it still exists, otherwise the first located farm. */
    public static FarmField choose(List<FarmField> fields, String requestedId) {
        FarmField firstLocated = null;
        for (FarmField field : fields) {
            if (field.id().equals(requestedId)) {
                return field;
            }
            if (firstLocated == null && field.hasLocation()) {
                firstLocated = field;
            }
        }
        if (firstLocated != null) {
            return firstLocated;
        }
        return fields.isEmpty() ? null : fields.get(0);
    }
}
