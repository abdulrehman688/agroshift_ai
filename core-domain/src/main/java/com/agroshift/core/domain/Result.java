package com.agroshift.core.domain;

import java.util.Objects;
import java.util.function.Function;

/** Immutable outcome of an operation that can fail in an expected way. */
public final class Result<T> {
    private final T value;
    private final String errorCode;

    private Result(T value, String errorCode) {
        this.value = value;
        this.errorCode = errorCode;
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(Objects.requireNonNull(value, "value"), null);
    }

    public static <T> Result<T> failure(String errorCode) {
        return new Result<>(null, Objects.requireNonNull(errorCode, "errorCode"));
    }

    public boolean isSuccess() {
        return errorCode == null;
    }

    public T value() {
        if (!isSuccess()) {
            throw new IllegalStateException("No value for failed result: " + errorCode);
        }
        return value;
    }

    public String errorCode() {
        if (isSuccess()) {
            throw new IllegalStateException("Successful result has no error");
        }
        return errorCode;
    }

    public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        return isSuccess() ? Result.success(mapper.apply(value)) : Result.failure(errorCode);
    }
}
