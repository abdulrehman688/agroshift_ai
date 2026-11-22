package com.agroshift.core.domain.concurrent;

/** Repeats a task on its own thread with sleep, clean stop, and interruption support. */
public final class StoppableWorker {
    private final Runnable task;
    private final long intervalMillis;
    private Thread thread;
    private volatile boolean running;

    public StoppableWorker(Runnable task, long intervalMillis) {
        if (intervalMillis < 0) {
            throw new IllegalArgumentException("interval must not be negative");
        }
        this.task = task;
        this.intervalMillis = intervalMillis;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "agroshift-worker");
        thread.start();
    }

    public void stop() throws InterruptedException {
        Thread current;
        synchronized (this) {
            running = false;
            current = thread;
            thread = null;
        }
        if (current != null) {
            current.interrupt();
            current.join();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            task.run();
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
