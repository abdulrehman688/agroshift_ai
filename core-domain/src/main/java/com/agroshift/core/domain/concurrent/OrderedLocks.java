package com.agroshift.core.domain.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Prevents lock-order deadlocks by always acquiring keys in sorted order, with a timeout. */
public final class OrderedLocks<K extends Comparable<K>> {
    private final ConcurrentHashMap<K, ReentrantLock> locks = new ConcurrentHashMap<>();

    public boolean runLocked(List<K> keys, long timeout, TimeUnit unit, Runnable action)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        List<ReentrantLock> held = new ArrayList<>();
        try {
            for (K key : new TreeSet<>(keys)) {
                ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
                long remaining = deadline - System.nanoTime();
                if (!lock.tryLock(Math.max(remaining, 0), TimeUnit.NANOSECONDS)) {
                    return false;
                }
                held.add(lock);
            }
            action.run();
            return true;
        } finally {
            for (int i = held.size() - 1; i >= 0; i--) {
                held.get(i).unlock();
            }
        }
    }
}
