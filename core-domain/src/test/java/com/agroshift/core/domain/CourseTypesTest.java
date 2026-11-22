package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.data.Cache;
import com.agroshift.core.domain.data.DataAccessException;
import com.agroshift.core.domain.data.DataErrors;
import com.agroshift.core.domain.data.Repository;
import com.agroshift.core.domain.data.SyncOutbox;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/** Written before the classes they describe, for the course's test-driven development topic. */
public class CourseTypesTest {
    private static final class MemoryRepository implements Repository<String, Integer> {
        private final Map<Integer, String> rows = new LinkedHashMap<>();

        @Override
        public Optional<String> findById(Integer id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<String> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public void save(String value) {
            rows.put(value.length(), value);
        }

        @Override
        public void deleteById(Integer id) {
            rows.remove(id);
        }
    }

    private static final class MemoryStore implements SyncOutbox.Store<String> {
        final List<String> rows = new ArrayList<>();
        final Map<String, Integer> attempts = new LinkedHashMap<>();

        @Override
        public List<String> next(int limit) {
            return List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        }

        @Override
        public void remove(String entry) {
            rows.remove(entry);
        }

        @Override
        public void recordAttempt(String entry) {
            attempts.merge(entry, 1, Integer::sum);
        }
    }

    @Test
    public void repositoryContractWorksForAnyTypes() {
        Repository<String, Integer> repository = new MemoryRepository();
        repository.save("wheat");
        assertTrue(repository.existsById(5));
        assertEquals("wheat", repository.findById(5).orElseThrow());
        repository.deleteById(5);
        assertFalse(repository.existsById(5));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    public void cacheReturnsValuesUntilTheyExpire() {
        AtomicLong clock = new AtomicLong();
        Cache<String, Integer> cache = new Cache<>(3, 100, clock::get);
        cache.put("rain", 12);
        clock.set(99);
        assertEquals(Integer.valueOf(12), cache.get("rain").orElseThrow());
        clock.set(100);
        assertTrue(cache.get("rain").isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    public void cacheRemovesLeastRecentlyUsedValue() {
        Cache<String, Integer> cache = new Cache<>(2, 1000, () -> 0L);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");
        cache.put("c", 3);
        assertTrue(cache.get("a").isPresent());
        assertTrue(cache.get("b").isEmpty());
        assertTrue(cache.get("c").isPresent());
    }

    @Test
    public void cacheRejectsInvalidLimitsAndCanInvalidate() {
        assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(0, 10, () -> 0L));
        assertThrows(IllegalArgumentException.class, () -> new Cache<String, String>(1, 0, () -> 0L));
        Cache<String, Integer> cache = new Cache<>(2, 1000, () -> 0L);
        cache.put("a", 1);
        cache.invalidate("a");
        assertTrue(cache.get("a").isEmpty());
    }

    @Test
    public void cacheIsSafeAcrossThreads() throws Exception {
        Cache<Integer, Integer> cache = new Cache<>(50, 1000, () -> 0L);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch done = new CountDownLatch(6);
        for (int worker = 0; worker < 6; worker++) {
            int seed = worker;
            pool.execute(() -> {
                for (int i = 0; i < 2000; i++) {
                    cache.put((seed + i) % 80, i);
                    cache.get(i % 80);
                }
                done.countDown();
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertTrue(cache.size() <= 50);
    }

    @Test
    public void outboxSendsInOrderAndRemovesEntries() {
        MemoryStore store = new MemoryStore();
        store.rows.addAll(List.of("a", "b", "c"));
        List<String> sent = new ArrayList<>();
        SyncOutbox.Outcome outcome = new SyncOutbox<>(store, 2, value -> sent.add(value)).drain(() -> false);
        assertEquals(SyncOutbox.Outcome.COMPLETE, outcome);
        assertEquals(List.of("a", "b", "c"), sent);
        assertTrue(store.rows.isEmpty());
    }

    @Test
    public void outboxStopsAtFirstFailureToKeepOrder() {
        MemoryStore store = new MemoryStore();
        store.rows.addAll(List.of("a", "b", "c"));
        List<String> sent = new ArrayList<>();
        SyncOutbox.Outcome outcome = new SyncOutbox<>(store, 10, value -> {
            if ("b".equals(value)) {
                return false;
            }
            return sent.add(value);
        }).drain(() -> false);
        assertEquals(SyncOutbox.Outcome.RETRY, outcome);
        assertEquals(List.of("a"), sent);
        assertEquals(List.of("b", "c"), store.rows);
        assertEquals(Integer.valueOf(1), store.attempts.get("b"));
    }

    @Test
    public void outboxTreatsExceptionsAsRetryableFailures() {
        MemoryStore store = new MemoryStore();
        store.rows.add("a");
        SyncOutbox.Outcome outcome = new SyncOutbox<String>(store, 10, value -> {
            throw new java.io.IOException("offline");
        }).drain(() -> false);
        assertEquals(SyncOutbox.Outcome.RETRY, outcome);
        assertEquals(List.of("a"), store.rows);
    }

    @Test
    public void outboxStopsWhenWorkIsCancelled() {
        MemoryStore store = new MemoryStore();
        store.rows.add("a");
        assertEquals(SyncOutbox.Outcome.RETRY, new SyncOutbox<String>(store, 10, value -> true).drain(() -> true));
        assertEquals(List.of("a"), store.rows);
    }

    @Test
    public void httpErrorsMapToTypedFailures() {
        assertEquals(DataAccessException.Kind.RATE_LIMITED, DataErrors.fromHttp(429));
        assertEquals(DataAccessException.Kind.SERVER, DataErrors.fromHttp(503));
        assertEquals(DataAccessException.Kind.BAD_DATA, DataErrors.fromHttp(404));
    }

    @Test
    public void onlyTemporaryFailuresSuggestRetry() {
        assertTrue(DataErrors.shouldRetry(DataAccessException.Kind.NETWORK));
        assertTrue(DataErrors.shouldRetry(DataAccessException.Kind.RATE_LIMITED));
        assertFalse(DataErrors.shouldRetry(DataAccessException.Kind.BAD_DATA));
    }
}
