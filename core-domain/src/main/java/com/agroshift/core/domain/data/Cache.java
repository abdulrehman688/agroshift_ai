package com.agroshift.core.domain.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Thread-safe least-recently-used cache whose values expire.
 *
 * <p>Rep invariant: entries never exceed maxSize. Abstraction function: the newest unexpired value
 * for each key. All access to the map is guarded by this object's monitor.
 */
public final class Cache<K, V> {
    private record Timed<V>(V value, long expiresAt) {
    }

    private final long ttlMillis;
    private final LongSupplier clock;
    private final Map<K, Timed<V>> entries;

    public Cache(int maxSize, long ttlMillis, LongSupplier clock) {
        if (maxSize <= 0 || ttlMillis <= 0) {
            throw new IllegalArgumentException("Cache size and lifetime must be positive");
        }
        this.ttlMillis = ttlMillis;
        this.clock = clock;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Timed<V>> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized Optional<V> get(K key) {
        Timed<V> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (clock.getAsLong() >= entry.expiresAt()) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public synchronized void put(K key, V value) {
        entries.put(key, new Timed<>(value, clock.getAsLong() + ttlMillis));
    }

    public synchronized void invalidate(K key) {
        entries.remove(key);
    }

    public synchronized int size() {
        return entries.size();
    }
}
