package com.agroshift.core.domain.concurrent;

import java.util.ArrayDeque;
import java.util.Deque;

/** Producer-consumer buffer using intrinsic locks with wait and notifyAll. */
public final class BoundedBuffer<T> {
    private final Deque<T> items = new ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized void put(T item) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException("item");
        }
        while (items.size() == capacity) {
            wait();
        }
        items.addLast(item);
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();
        }
        T item = items.removeFirst();
        notifyAll();
        return item;
    }

    public synchronized int size() {
        return items.size();
    }
}
