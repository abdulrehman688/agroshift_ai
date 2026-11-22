package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.concurrent.ChunkPipeline;
import com.agroshift.core.domain.data.DataAccessException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ChunkPipelineTest {
    @Test(timeout = 5_000)
    public void keepsResultOrderAcrossWorkers() throws Exception {
        ChunkPipeline<Integer, Integer> pipeline = new ChunkPipeline<>(3, 1, 0);
        AtomicInteger progress = new AtomicInteger();
        List<Integer> result = pipeline.run(List.of(1, 2, 3, 4, 5), value -> {
            Thread.sleep((6 - value) * 10L);
            return value * 2;
        }, progress::set);
        assertEquals(List.of(2, 4, 6, 8, 10), result);
        assertEquals(5, progress.get());
    }

    @Test(timeout = 5_000)
    public void retriesTemporaryFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<String> result = new ChunkPipeline<String, String>(1, 3, 1).run(List.of("a"), chunk -> {
            if (attempts.incrementAndGet() < 3) {
                throw new DataAccessException(DataAccessException.Kind.NETWORK, "offline", null);
            }
            return chunk;
        }, done -> { });
        assertEquals(List.of("a"), result);
        assertEquals(3, attempts.get());
    }

    @Test(timeout = 5_000)
    public void doesNotRetryBadData() {
        AtomicInteger attempts = new AtomicInteger();
        DataAccessException error = assertThrows(DataAccessException.class, () ->
                new ChunkPipeline<String, String>(1, 3, 1).run(List.of("a"), chunk -> {
                    attempts.incrementAndGet();
                    throw new DataAccessException(DataAccessException.Kind.BAD_DATA, "bad", null);
                }, done -> { }));
        assertEquals(DataAccessException.Kind.BAD_DATA, error.kind());
        assertEquals(1, attempts.get());
    }

    @Test(timeout = 5_000)
    public void unexpectedErrorDoesNotHangPipeline() {
        assertThrows(DataAccessException.class, () ->
                new ChunkPipeline<String, String>(2, 1, 0).run(List.of("a", "b"), chunk -> {
                    throw new IllegalStateException("boom");
                }, done -> { }));
    }

    @Test(timeout = 5_000)
    public void callerInterruptStopsWorkers() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                new ChunkPipeline<String, String>(1, 1, 0).run(List.of("a"), chunk -> {
                    started.countDown();
                    try {
                        Thread.sleep(60_000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        throw e;
                    }
                    return chunk;
                }, done -> { });
            } catch (InterruptedException expected) {
                Thread.currentThread().interrupt();
            } catch (DataAccessException unexpected) {
                throw new AssertionError(unexpected);
            }
        });
        caller.start();
        started.await();
        caller.interrupt();
        caller.join();
        assertTrue(interrupted.get());
    }
}
