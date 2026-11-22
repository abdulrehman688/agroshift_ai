package com.agroshift.core.domain.data;

/** Typed failure for fault-tolerant remote data access. */
public final class DataAccessException extends Exception {
    public enum Kind {
        NETWORK(true), RATE_LIMITED(true), SERVER(true), BAD_DATA(false), UNSUPPORTED_LOCATION(false);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private final Kind kind;

    public DataAccessException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isRetryable() {
        return kind.retryable;
    }
}
