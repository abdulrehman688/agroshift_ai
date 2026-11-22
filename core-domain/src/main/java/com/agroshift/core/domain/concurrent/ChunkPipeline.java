package com.agroshift.core.domain.concurrent;

import com.agroshift.core.domain.data.DataAccessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntConsumer;

/**
 * Downloads chunks with worker threads and hands results to the caller through a bounded buffer.
 * Workers sleep between retries and stop on failure or caller interruption.
 */
public final class ChunkPipeline<C, R> {
    @FunctionalInterface
    public interface Fetcher<C, R> {
        R fetch(C chunk) throws DataAccessException, InterruptedException;
    }

    private record Task<C>(int index, C chunk) {
    }

    private record Outcome<R>(int index, R value, DataAccessException error) {
    }

    private final int workers;
    private final int maxAttempts;
    private final long baseDelayMillis;

    public ChunkPipeline(int workers, int maxAttempts, long baseDelayMillis) {
        if (workers < 1 || maxAttempts < 1 || baseDelayMillis < 0) {
            throw new IllegalArgumentException("Invalid pipeline settings");
        }
        this.workers = workers;
        this.maxAttempts = maxAttempts;
        this.baseDelayMillis = baseDelayMillis;
    }

    public List<R> run(List<C> chunks, Fetcher<C, R> fetcher, IntConsumer completed)
            throws DataAccessException, InterruptedException {
        Queue<Task<C>> pending = new ConcurrentLinkedQueue<>();
        List<R> results = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            pending.add(new Task<>(i, chunks.get(i)));
            results.add(null);
        }
        BoundedBuffer<Outcome<R>> buffer = new BoundedBuffer<>(Math.max(1, workers));
        List<Thread> threads = new ArrayList<>(workers);
        int count = Math.min(workers, Math.max(1, chunks.size()));
        for (int i = 0; i < count; i++) {
            Thread thread = new Thread(() -> work(pending, buffer, fetcher), "climate-fetch-" + i);
            threads.add(thread);
            thread.start();
        }
        try {
            for (int done = 1; done <= chunks.size(); done++) {
                Outcome<R> outcome = buffer.take();
                if (outcome.error() != null) {
                    throw outcome.error();
                }
                results.set(outcome.index(), outcome.value());
                completed.accept(done);
            }
            return results;
        } finally {
            for (Thread thread : threads) {
                thread.interrupt();
            }
            for (Thread thread : threads) {
                thread.join();
            }
        }
    }

    private void work(Queue<Task<C>> pending, BoundedBuffer<Outcome<R>> buffer, Fetcher<C, R> fetcher) {
        try {
            Task<C> task;
            while (!Thread.currentThread().isInterrupted() && (task = pending.poll()) != null) {
                try {
                    buffer.put(new Outcome<>(task.index(), fetchWithRetry(task.chunk(), fetcher), null));
                } catch (DataAccessException error) {
                    buffer.put(new Outcome<>(task.index(), null, error));
                    return;
                } catch (RuntimeException error) {
                    buffer.put(new Outcome<>(task.index(), null, new DataAccessException(
                            DataAccessException.Kind.BAD_DATA, "Could not read data", error)));
                    return;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private R fetchWithRetry(C chunk, Fetcher<C, R> fetcher)
            throws DataAccessException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                return fetcher.fetch(chunk);
            } catch (DataAccessException error) {
                if (!error.isRetryable() || attempt >= maxAttempts) {
                    throw error;
                }
                Thread.sleep(baseDelayMillis * (1L << (attempt - 1)));
            }
        }
    }
}
