package com.agroshift.core.sync;

import com.agroshift.core.data.OutboxEntity;

/** Sends one change to the backend using the entry's idempotency key. */
public interface SyncGateway {
    boolean send(OutboxEntity entry) throws Exception;
}
