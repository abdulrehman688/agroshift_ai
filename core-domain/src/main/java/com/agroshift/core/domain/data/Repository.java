package com.agroshift.core.domain.data;

import java.util.List;
import java.util.Optional;

/** Generic storage contract. Implementations may block, so call them off the main thread. */
public interface Repository<T, ID> {
    Optional<T> findById(ID id);

    List<T> findAll();

    void save(T value);

    void deleteById(ID id);

    default boolean existsById(ID id) {
        return findById(id).isPresent();
    }
}
