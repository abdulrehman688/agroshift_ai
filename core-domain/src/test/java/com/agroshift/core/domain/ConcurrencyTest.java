package com.agroshift.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.agroshift.core.domain.concurrent.BoundedBuffer;
import com.agroshift.core.domain.concurrent.OrderedLocks;
import com.agroshift.core.domain.concurrent.StoppableWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ConcurrencyTest {
    @Test(timeout = 10_000)
    public void bufferDeliversEveryItemInOrder() throws Exception {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(3);
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 1_000; i++) {
                    buffer.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();
        for (int i = 0; i < 1_000; i++) {
            assertEquals(Integer.valueOf(i), buffer.take());
        }
        producer.join();
        assertEquals(0, buffer.size());
    }

    @Test(timeout = 20_000)
    public void oppositeLockOrdersDoNotDeadlock() throws Exception {
        OrderedLocks<String> locks = new OrderedLocks<>();
        AtomicInteger completed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            List<String> keys = i % 2 == 0 ? List.of("farm", "stock") : List.of("stock", "farm");
            results.add(pool.submit(() ->
                    locks.runLocked(keys, 10, TimeUnit.SECONDS, completed::incrementAndGet)));
        }
        for (Future<Boolean> result : results) {
            assertTrue(result.get());
        }
        pool.shutdown();
        assertEquals(2_000, completed.get());
    }

    @Test(timeout = 5_000)
    public void lockTimesOutAndReleasesPartialLocks() throws Exception {
        OrderedLocks<String> locks = new OrderedLocks<>();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            try {
                locks.runLocked(List.of("b"), 1, TimeUnit.SECONDS, () -> {
                    holding.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        holder.start();
        holding.await();
        assertFalse(locks.runLocked(List.of("a", "b"), 50, TimeUnit.MILLISECONDS, () -> { }));
        assertTrue(locks.runLocked(List.of("a"), 50, TimeUnit.MILLISECONDS, () -> { }));
        release.countDown();
        holder.join();
    }

    @Test(timeout = 5_000)
    public void workerStopsDuringSleep() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        StoppableWorker worker = new StoppableWorker(ran::countDown, 60_000);
        worker.start();
        ran.await();
        worker.stop();
        assertFalse(worker.isRunning());
    }
}
