package com.kset.common.monitor.reporter;

/**
 * 同步直调上报。
 */
public final class SyncAsyncReporter implements AsyncReporter {

    @Override
    public void report(Runnable task) {
        if (task != null) {
            task.run();
        }
    }

    @Override
    public void shutdown() {
    }
}
