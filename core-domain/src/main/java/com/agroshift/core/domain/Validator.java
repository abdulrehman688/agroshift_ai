package com.agroshift.core.domain;

/** Checks a candidate value and returns it, or an error code. */
@FunctionalInterface
public interface Validator<T> {
    Result<T> validate(T candidate);

    default Validator<T> and(Validator<T> next) {
        return candidate -> {
            Result<T> first = validate(candidate);
            return first.isSuccess() ? next.validate(first.value()) : first;
        };
    }
}
