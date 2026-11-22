package com.agroshift.core.domain.data;

/** Turns remote failures into typed errors the app can explain. */
public final class DataErrors {
    private DataErrors() {
    }

    public static DataAccessException.Kind fromHttp(int status) {
        if (status == 429) {
            return DataAccessException.Kind.RATE_LIMITED;
        }
        return status >= 500 ? DataAccessException.Kind.SERVER : DataAccessException.Kind.BAD_DATA;
    }

    public static boolean shouldRetry(DataAccessException.Kind kind) {
        return new DataAccessException(kind, "", null).isRetryable();
    }
}
