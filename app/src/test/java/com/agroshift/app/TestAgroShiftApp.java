package com.agroshift.app;

import androidx.work.testing.WorkManagerTestInitHelper;

/** Starts the real app with a local background-work manager for screen tests. */
public class TestAgroShiftApp extends AgroShiftApp {
    @Override
    public void onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this);
        super.onCreate();
    }
}
