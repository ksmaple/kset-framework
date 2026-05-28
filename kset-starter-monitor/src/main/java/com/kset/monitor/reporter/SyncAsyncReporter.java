package com.kset.monitor.reporter;

/**
 * 同步直调上报（关闭 async 时使用）。
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
