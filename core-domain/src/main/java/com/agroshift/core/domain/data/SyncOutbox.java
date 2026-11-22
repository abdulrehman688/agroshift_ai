package com.agroshift.core.domain.data;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Abstract data type for local changes waiting to reach a server.
 *
 * <p>Rep invariant: an entry leaves the store only after it was sent. Abstraction function: an
 * ordered queue of changes. Sending stops at the first failure so later changes stay in order.
 */
public final class SyncOutbox<E> {
    public enum Outcome { COMPLETE, RETRY }

    public interface Store<E> {
        List<E> next(int limit);

        void remove(E entry);

        void recordAttempt(E entry);
    }

    @FunctionalInterface
    public interface Sender<E> {
        boolean send(E entry) throws Exception;
    }

    private final Store<E> store;
    private final int batchSize;
    private final Sender<E> sender;

    public SyncOutbox(Store<E> store, int batchSize, Sender<E> sender) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.store = store;
        this.batchSize = batchSize;
        this.sender = sender;
    }

    public Outcome drain(BooleanSupplier stopped) {
        List<E> batch;
        while (!(batch = store.next(batchSize)).isEmpty()) {
            for (E entry : batch) {
                if (stopped.getAsBoolean()) {
                    return Outcome.RETRY;
                }
                if (!trySend(entry)) {
                    store.recordAttempt(entry);
                    return Outcome.RETRY;
                }
                store.remove(entry);
            }
        }
        return Outcome.COMPLETE;
    }

    private boolean trySend(E entry) {
        try {
            return sender.send(entry);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception failure) {
            return false;
        }
    }
}
