package com.agroshift.core.sync;

import androidx.annotation.Nullable;
import com.agroshift.core.data.FarmDao;

/** Implemented by the Application. A null gateway keeps changes safely queued. */
public interface SyncHost {
    FarmDao farmDao();

    @Nullable
    SyncGateway syncGateway();
}
